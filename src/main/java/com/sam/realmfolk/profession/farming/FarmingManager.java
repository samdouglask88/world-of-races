package com.sam.realmfolk.profession.farming;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.profession.ProfessionService;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.economy.DailyEconomyManager;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.society.task.WorkOrder;
import com.sam.realmfolk.society.task.WorkOrderStatus;
import com.sam.realmfolk.society.task.WorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Physical harvesting and replanting for vanilla field crops. */
public final class FarmingManager {
    public static final int WORK_INTERVAL_TICKS = 20;
    public static final int MAX_OPEN_ORDERS = 4;
    private static final int FIELD_RADIUS = 32;
    private static final List<CropSpec> CROPS = List.of(
            new CropSpec(Blocks.WHEAT, Items.WHEAT, Items.WHEAT_SEEDS),
            new CropSpec(Blocks.CARROTS, Items.CARROT, Items.CARROT),
            new CropSpec(Blocks.POTATOES, Items.POTATO, Items.POTATO),
            new CropSpec(Blocks.BEETROOTS, Items.BEETROOT, Items.BEETROOT_SEEDS));

    private FarmingManager() {}

    public static boolean ensureWorkOrder(ServerLevel level, Settlement settlement) {
        if (!level.hasChunkAt(settlement.center()) || openOrders(settlement) >= MAX_OPEN_ORDERS) return false;
        ResidentEntity farmer = readyFarmer(level, settlement);
        if (farmer == null || farmer.getProfessionData().workstation() == null) return false;
        BlockPos crop = findMatureCrop(level, settlement, farmer.getProfessionData().workstation());
        return crop != null && createOrderAt(level, settlement, crop) != null;
    }

    public static boolean canPerform(ServerLevel level, Settlement settlement,
                                     ResidentEntity resident, WorkOrder order) {
        BlockPos target = order.targetPosition();
        BlockPos station = resident.getProfessionData().workstation();
        return isFarmingOrder(order)
                && resident.getProfessionData().profession() == NpcProfession.FARMER
                && station != null && level.hasChunkAt(station)
                && ProfessionService.validateOrFindStation(level, resident)
                && ProfessionService.hasRequiredTool(resident)
                && target != null && settlement.contains(target)
                && target.distSqr(resident.getProfessionData().workstation()) <= FIELD_RADIUS * FIELD_RADIUS
                && matureCrop(level, target);
    }

    public static WorkResult workCrop(ServerLevel level, Settlement settlement,
                                      ResidentEntity resident, WorkOrder order) {
        BlockPos target = order.targetPosition();
        if (target == null || !settlement.contains(target)) return WorkResult.INVALID_CROP;
        if (!level.hasChunkAt(target)) return WorkResult.UNLOADED;
        if (!ProfessionService.hasRequiredTool(resident)) return WorkResult.NO_TOOL;
        BlockState state = level.getBlockState(target);
        CropSpec spec = cropSpec(state);
        if (spec == null || !(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
            return WorkResult.INVALID_CROP;
        }

        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, target,
                level.getBlockEntity(target), resident, ProfessionService.requiredToolStack(resident)));
        SeedSource seedSource;
        if (removeOne(drops, spec.seed())) {
            seedSource = SeedSource.DROPS;
        } else if (removeOne(resident.getNpcInventory(), spec.seed())) {
            seedSource = SeedSource.INVENTORY;
        } else {
            ItemStack storedSeed = new SettlementStorage(level, settlement).extract(spec.seed(), 1);
            if (storedSeed.isEmpty()) return WorkResult.NO_SEED;
            seedSource = SeedSource.STORAGE;
        }
        if (!canFit(resident.getNpcInventory(), drops)) {
            restoreSeed(level, settlement, resident, seedSource, spec.seed());
            return WorkResult.INVENTORY_FULL;
        }

        BlockState replanted = crop.getStateForAge(0);
        if (!replanted.canSurvive(level, target)
                || !level.setBlock(target, replanted, Block.UPDATE_ALL)) {
            restoreSeed(level, settlement, resident, seedSource, spec.seed());
            return WorkResult.BLOCKED;
        }
        for (ItemStack drop : drops) add(resident.getNpcInventory(), drop);
        resident.getNpcInventory().setChanged();
        ProfessionService.damageRequiredTool(resident);
        resident.getProfessionData().addExperience(8);
        level.levelEvent(2001, target, Block.getId(state));
        return WorkResult.COMPLETED;
    }

    @Nullable
    public static WorkOrder createOrderAt(ServerLevel level, Settlement settlement, BlockPos crop) {
        CropSpec spec = level.hasChunkAt(crop) ? cropSpec(level.getBlockState(crop)) : null;
        if (!settlement.contains(crop) || spec == null || !matureCrop(level, crop)
                || hasOrderFor(settlement, crop)) return null;
        long now = level.getGameTime();
        WorkOrder order = new WorkOrder(UUID.randomUUID(), WorkOrderType.PRODUCE_RESOURCE,
                Math.max(65, settlement.government().priority(
                        com.sam.realmfolk.society.government.SettlementPolicy.FOOD)),
                settlement.id(), crop, level.dimension(), spec.seed(), 1,
                spec.produce(), 1, now, now + DailyEconomyManager.DAY_TICKS);
        return settlement.taskBoard().add(order) ? order : null;
    }

    public static boolean isFarmingOrder(WorkOrder order) {
        return order.type() == WorkOrderType.PRODUCE_RESOURCE
                && order.targetPosition() != null && cropSpec(order.result()) != null;
    }

    @Nullable
    private static ResidentEntity readyFarmer(ServerLevel level, Settlement settlement) {
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        for (UUID memberId : settlement.memberIds()) {
            PersonData person = society.getPerson(memberId).orElse(null);
            if (person == null || person.getEntityId() == null) continue;
            Entity entity = level.getEntity(person.getEntityId());
            if (entity instanceof ResidentEntity resident && resident.isAlive() && resident.isWorkingAge()
                    && resident.getProfessionData().profession() == NpcProfession.FARMER
                    && ProfessionService.validateOrFindStation(level, resident)
                    && ProfessionService.hasRequiredTool(resident)) return resident;
        }
        return null;
    }

    @Nullable
    private static BlockPos findMatureCrop(ServerLevel level, Settlement settlement, BlockPos station) {
        for (int radius = 0; radius <= FIELD_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                BlockPos found = matureInColumn(level, settlement, station.getX() + dx, station.getZ() - radius);
                if (found != null) return found;
                if (radius > 0) {
                    found = matureInColumn(level, settlement, station.getX() + dx, station.getZ() + radius);
                    if (found != null) return found;
                }
            }
            for (int dz = -radius + 1; dz < radius; dz++) {
                BlockPos found = matureInColumn(level, settlement, station.getX() - radius, station.getZ() + dz);
                if (found != null) return found;
                if (radius > 0) {
                    found = matureInColumn(level, settlement, station.getX() + radius, station.getZ() + dz);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos matureInColumn(ServerLevel level, Settlement settlement, int x, int z) {
        BlockPos column = new BlockPos(x, settlement.center().getY(), z);
        if (!settlement.contains(column) || !level.hasChunkAt(column)) return null;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        for (int y = top; y >= Math.max(level.getMinBuildHeight(), top - 6); y--) {
            BlockPos candidate = new BlockPos(x, y, z);
            if (matureCrop(level, candidate) && !hasOrderFor(settlement, candidate)) return candidate;
        }
        return null;
    }

    private static boolean matureCrop(ServerLevel level, BlockPos position) {
        if (!level.hasChunkAt(position)) return false;
        BlockState state = level.getBlockState(position);
        return cropSpec(state) != null && state.getBlock() instanceof CropBlock crop
                && crop.isMaxAge(state) && level.getBlockState(position.below()).is(Blocks.FARMLAND);
    }

    @Nullable
    private static CropSpec cropSpec(BlockState state) {
        for (CropSpec spec : CROPS) if (state.is(spec.block())) return spec;
        return null;
    }

    @Nullable
    private static CropSpec cropSpec(Item produce) {
        for (CropSpec spec : CROPS) if (spec.produce() == produce) return spec;
        return null;
    }

    private static int openOrders(Settlement settlement) {
        int count = 0;
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isFarmingOrder(order) && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) count++;
        }
        return count;
    }

    private static boolean hasOrderFor(Settlement settlement, BlockPos position) {
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isFarmingOrder(order) && position.equals(order.targetPosition())
                    && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) return true;
        }
        return false;
    }

    private static boolean removeOne(List<ItemStack> drops, net.minecraft.world.item.Item item) {
        for (ItemStack stack : drops) {
            if (!stack.is(item)) continue;
            stack.shrink(1);
            return true;
        }
        return false;
    }

    private static boolean removeOne(SimpleContainer inventory, net.minecraft.world.item.Item item) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(item)) continue;
            stack.shrink(1);
            if (stack.isEmpty()) inventory.setItem(i, ItemStack.EMPTY);
            inventory.setChanged();
            return true;
        }
        return false;
    }

    private static void restoreSeed(ServerLevel level, Settlement settlement,
                                    ResidentEntity resident, SeedSource source, Item seedItem) {
        if (source == SeedSource.DROPS) return;
        ItemStack seed = new ItemStack(seedItem);
        if (source == SeedSource.STORAGE && new SettlementStorage(level, settlement).insert(seed)) return;
        add(resident.getNpcInventory(), seed);
        resident.getNpcInventory().setChanged();
    }

    private static boolean canFit(SimpleContainer inventory, List<ItemStack> drops) {
        SimpleContainer simulation = new SimpleContainer(inventory.getContainerSize());
        for (int i = 0; i < inventory.getContainerSize(); i++) simulation.setItem(i, inventory.getItem(i).copy());
        for (ItemStack drop : drops) {
            if (!drop.isEmpty() && !simulation.addItem(drop.copy()).isEmpty()) return false;
        }
        return true;
    }

    private static void add(SimpleContainer inventory, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack remaining = inventory.addItem(stack.copy());
        if (!remaining.isEmpty()) throw new IllegalStateException("Farming inventory simulation diverged");
    }

    public enum WorkResult {
        COMPLETED, NO_TOOL, NO_SEED, INVENTORY_FULL, UNLOADED, INVALID_CROP, BLOCKED
    }

    private enum SeedSource { DROPS, INVENTORY, STORAGE }
    private record CropSpec(Block block, Item produce, Item seed) {}
}
