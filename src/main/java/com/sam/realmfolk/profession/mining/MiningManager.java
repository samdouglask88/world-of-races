package com.sam.realmfolk.profession.mining;

import com.sam.realmfolk.content.ModBlocks;
import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.profession.ProfessionService;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.economy.DailyEconomyManager;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.task.WorkOrder;
import com.sam.realmfolk.society.task.WorkOrderStatus;
import com.sam.realmfolk.society.task.WorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Physical mining constrained to player-authorized settlement work markers. */
public final class MiningManager {
    public static final int WORK_INTERVAL_TICKS = 30;
    public static final int MAX_OPEN_ORDERS = 3;
    public static final int MARKER_RADIUS = 8;
    private static final int MARKERS_PER_SCAN = 4;

    private MiningManager() {}

    public static boolean ensureWorkOrder(ServerLevel level, Settlement settlement) {
        if (!level.hasChunkAt(settlement.center()) || openOrders(settlement) >= MAX_OPEN_ORDERS
                || readyMiner(level, settlement) == null) return false;
        MiningTarget target = findTarget(level, settlement);
        return target != null && createOrderAt(level, settlement, target.position()) != null;
    }

    public static boolean canPerform(ServerLevel level, Settlement settlement,
                                     ResidentEntity resident, WorkOrder order) {
        return isMiningOrder(order)
                && resident.getProfessionData().profession() == NpcProfession.MINER
                && resident.getProfessionData().workstation() != null
                && level.hasChunkAt(resident.getProfessionData().workstation())
                && ProfessionService.validateOrFindStation(level, resident)
                && ProfessionService.hasRequiredTool(resident)
                && order.targetPosition() != null
                && validTarget(level, settlement, order.targetPosition());
    }

    public static WorkResult workBlock(ServerLevel level, Settlement settlement,
                                       ResidentEntity resident, WorkOrder order) {
        BlockPos target = order.targetPosition();
        if (target == null || !settlement.contains(target) || !nearRegisteredMarker(level, settlement, target)) {
            return WorkResult.INVALID_TARGET;
        }
        if (!level.hasChunkAt(target)) return WorkResult.UNLOADED;
        if (!ProfessionService.hasRequiredTool(resident)) return WorkResult.NO_TOOL;
        if (!validTarget(level, settlement, target)) return WorkResult.INVALID_TARGET;

        BlockState state = level.getBlockState(target);
        ItemStack tool = ProfessionService.requiredToolStack(resident);
        List<ItemStack> drops = Block.getDrops(state, level, target,
                level.getBlockEntity(target), resident, tool);
        if (!canFit(resident.getNpcInventory(), drops)) return WorkResult.INVENTORY_FULL;
        if (!level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) return WorkResult.BLOCKED;
        for (ItemStack drop : drops) add(resident.getNpcInventory(), drop);
        resident.getNpcInventory().setChanged();
        ProfessionService.damageRequiredTool(resident);
        resident.getProfessionData().addExperience(isOre(state) ? 16 : 7);
        level.levelEvent(2001, target, Block.getId(state));
        return WorkResult.COMPLETED;
    }

    @Nullable
    public static WorkOrder createOrderAt(ServerLevel level, Settlement settlement, BlockPos target) {
        if (!validTarget(level, settlement, target) || hasOrderFor(settlement, target)) return null;
        List<ItemStack> expectedDrops = Block.getDrops(level.getBlockState(target), level, target,
                level.getBlockEntity(target), null, new ItemStack(Items.IRON_PICKAXE));
        Item result = expectedDrops.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::getItem)
                .findFirst().orElse(Items.AIR);
        int amount = expectedDrops.stream().filter(stack -> stack.is(result)).mapToInt(ItemStack::getCount).sum();
        if (result == Items.AIR || amount <= 0) return null;
        long now = level.getGameTime();
        WorkOrder order = new WorkOrder(UUID.randomUUID(), WorkOrderType.PRODUCE_RESOURCE,
                Math.max(60, settlement.government().priority(
                        com.sam.realmfolk.society.government.SettlementPolicy.PRODUCTION)),
                settlement.id(), target, level.dimension(), Items.IRON_PICKAXE, 1,
                result, amount, now, now + DailyEconomyManager.DAY_TICKS * 2L);
        return settlement.taskBoard().add(order) ? order : null;
    }

    public static boolean isMiningOrder(WorkOrder order) {
        return order.type() == WorkOrderType.PRODUCE_RESOURCE && order.targetPosition() != null
                && order.requirement() == Items.IRON_PICKAXE;
    }

    @Nullable
    private static ResidentEntity readyMiner(ServerLevel level, Settlement settlement) {
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        for (UUID memberId : settlement.memberIds()) {
            PersonData person = society.getPerson(memberId).orElse(null);
            if (person == null || person.getEntityId() == null) continue;
            Entity entity = level.getEntity(person.getEntityId());
            if (entity instanceof ResidentEntity resident && resident.isAlive() && resident.isWorkingAge()
                    && resident.getProfessionData().profession() == NpcProfession.MINER
                    && ProfessionService.validateOrFindStation(level, resident)
                    && ProfessionService.hasRequiredTool(resident)) return resident;
        }
        return null;
    }

    @Nullable
    private static MiningTarget findTarget(ServerLevel level, Settlement settlement) {
        MiningTarget best = null;
        List<BlockPos> markers = new ArrayList<>(settlement.workPositions());
        if (markers.isEmpty()) return null;
        int checked = Math.min(MARKERS_PER_SCAN, markers.size());
        int start = (int) ((level.getGameTime() / 600L) % markers.size());
        for (int markerIndex = 0; markerIndex < checked; markerIndex++) {
            BlockPos marker = markers.get((start + markerIndex) % markers.size());
            if (!validMarker(level, settlement, marker)) continue;
            for (BlockPos candidate : BlockPos.betweenClosed(marker.offset(-MARKER_RADIUS, -MARKER_RADIUS, -MARKER_RADIUS),
                    marker.offset(MARKER_RADIUS, MARKER_RADIUS, MARKER_RADIUS))) {
                if (!validTarget(level, settlement, candidate) || hasOrderFor(settlement, candidate)) continue;
                BlockState state = level.getBlockState(candidate);
                int score = (isOre(state) ? 10_000 : 0) - candidate.distManhattan(marker);
                if (best == null || score > best.score()) best = new MiningTarget(candidate.immutable(), score);
            }
        }
        return best;
    }

    private static boolean validTarget(ServerLevel level, Settlement settlement, BlockPos target) {
        if (!settlement.contains(target) || !level.hasChunkAt(target)
                || !nearRegisteredMarker(level, settlement, target)) return false;
        BlockState state = level.getBlockState(target);
        if (!isMineableResource(state) || state.getDestroySpeed(level, target) < 0.0F
                || level.getBlockEntity(target) != null || level.getBlockState(target.above()).getBlock() instanceof FallingBlock) {
            return false;
        }
        boolean accessible = false;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = target.relative(direction);
            if (!level.hasChunkAt(neighbor) || !level.getFluidState(neighbor).isEmpty()) return false;
            if (direction.getAxis().isHorizontal() && level.getBlockState(neighbor).isAir()
                    && level.getBlockState(neighbor.below()).isCollisionShapeFullBlock(level, neighbor.below())) {
                accessible = true;
            }
        }
        return accessible;
    }

    private static boolean nearRegisteredMarker(ServerLevel level, Settlement settlement, BlockPos target) {
        for (BlockPos marker : settlement.workPositions()) {
            if (validMarker(level, settlement, marker)
                    && Math.abs(target.getX() - marker.getX()) <= MARKER_RADIUS
                    && Math.abs(target.getY() - marker.getY()) <= MARKER_RADIUS
                    && Math.abs(target.getZ() - marker.getZ()) <= MARKER_RADIUS) return true;
        }
        return false;
    }

    private static boolean validMarker(ServerLevel level, Settlement settlement, BlockPos marker) {
        return settlement.contains(marker) && level.hasChunkAt(marker)
                && level.getBlockState(marker).is(ModBlocks.WORK_MARKER.get());
    }

    private static boolean isMineableResource(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || isOre(state);
    }

    private static boolean isOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES);
    }

    private static int openOrders(Settlement settlement) {
        int count = 0;
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isMiningOrder(order) && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) count++;
        }
        return count;
    }

    private static boolean hasOrderFor(Settlement settlement, BlockPos target) {
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isMiningOrder(order) && target.equals(order.targetPosition())
                    && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) return true;
        }
        return false;
    }

    private static boolean canFit(SimpleContainer inventory, List<ItemStack> drops) {
        SimpleContainer simulation = new SimpleContainer(inventory.getContainerSize());
        for (int i = 0; i < inventory.getContainerSize(); i++) simulation.setItem(i, inventory.getItem(i).copy());
        for (ItemStack drop : drops) if (!drop.isEmpty() && !simulation.addItem(drop.copy()).isEmpty()) return false;
        return true;
    }

    private static void add(SimpleContainer inventory, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack remaining = inventory.addItem(stack.copy());
        if (!remaining.isEmpty()) throw new IllegalStateException("Mining inventory simulation diverged");
    }

    private record MiningTarget(BlockPos position, int score) {}
    public enum WorkResult { COMPLETED, NO_TOOL, INVENTORY_FULL, UNLOADED, INVALID_TARGET, BLOCKED }
}
