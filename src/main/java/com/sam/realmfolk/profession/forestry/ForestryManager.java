package com.sam.realmfolk.profession.forestry;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.profession.ProfessionService;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.society.task.WorkOrder;
import com.sam.realmfolk.society.task.WorkOrderStatus;
import com.sam.realmfolk.society.task.WorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Physical, bounded forestry used by lumberjack work orders. */
public final class ForestryManager {
    public static final int MIN_LOGS = 3;
    public static final int MAX_LOGS = 48;
    public static final int MIN_LEAVES = 4;
    public static final int MAX_OPEN_ORDERS = 2;
    public static final int WORK_INTERVAL_TICKS = 20;
    private static final int CANOPY_RADIUS = 3;
    private static final int SEARCH_STEP = 2;

    private ForestryManager() {}

    public static boolean ensureWorkOrder(ServerLevel level, Settlement settlement) {
        if (!level.hasChunkAt(settlement.center()) || !needsWood(level, settlement)
                || !hasReadyLumberjack(level, settlement) || openOrders(settlement) >= MAX_OPEN_ORDERS) return false;
        Tree tree = findTree(level, settlement);
        if (tree == null || hasOrderFor(settlement, tree.base())) return false;
        long now = level.getGameTime();
        WorkOrder order = new WorkOrder(UUID.randomUUID(), WorkOrderType.PRODUCE_RESOURCE,
                Math.max(55, settlement.government().priority(com.sam.realmfolk.society.government.SettlementPolicy.PRODUCTION)),
                settlement.id(), tree.base(), level.dimension(), Items.AIR, 0,
                tree.logItem(), tree.logs().size(), now,
                now + com.sam.realmfolk.society.economy.DailyEconomyManager.DAY_TICKS * 2L);
        return settlement.taskBoard().add(order);
    }

    public static boolean canPerform(ServerLevel level, Settlement settlement,
                                     ResidentEntity resident, WorkOrder order) {
        return order.type() == WorkOrderType.PRODUCE_RESOURCE
                && resident.getProfessionData().profession() == NpcProfession.LUMBERJACK
                && resident.getProfessionData().workstation() != null
                && level.hasChunkAt(resident.getProfessionData().workstation())
                && ProfessionService.validateOrFindStation(level, resident)
                && ProfessionService.hasRequiredTool(resident)
                && order.targetPosition() != null && settlement.contains(order.targetPosition())
                && inspectTreeForOrder(level, settlement, order) != null;
    }

    public static WorkResult workTree(ServerLevel level, Settlement settlement,
                                      ResidentEntity resident, WorkOrder order) {
        BlockPos base = order.targetPosition();
        if (base == null || !settlement.contains(base)) return WorkResult.INVALID_TREE;
        if (!level.hasChunkAt(base)) return WorkResult.UNLOADED;
        if (!ProfessionService.hasRequiredTool(resident)) return WorkResult.NO_TOOL;
        Tree tree = inspectTreeForOrder(level, settlement, order);
        if (tree == null) return WorkResult.INVALID_TREE;

        BlockPos next = tree.logs().stream()
                .filter(position -> !position.equals(tree.base()))
                .max(Comparator.<BlockPos>comparingInt(position -> position.getY())
                        .thenComparingInt(position -> position.distManhattan(tree.base())))
                .orElse(tree.base());
        BlockState state = level.getBlockState(next);
        ItemStack tool = ProfessionService.requiredToolStack(resident);
        List<ItemStack> drops = Block.getDrops(state, level, next,
                level.getBlockEntity(next), resident, tool);
        if (!canFit(resident.getNpcInventory(), drops)) return WorkResult.INVENTORY_FULL;
        if (!level.setBlock(next, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) return WorkResult.BLOCKED;
        for (ItemStack drop : drops) add(resident.getNpcInventory(), drop);
        resident.getNpcInventory().setChanged();
        ProfessionService.damageRequiredTool(resident);
        resident.getProfessionData().addExperience(4);
        level.levelEvent(2001, next, Block.getId(state));

        if (!next.equals(tree.base())) return WorkResult.IN_PROGRESS;
        replant(level, settlement, resident, base, tree.logItem());
        return WorkResult.COMPLETED;
    }

    @Nullable
    public static Tree inspectTree(ServerLevel level, Settlement settlement, BlockPos requestedBase) {
        return inspectTree(level, settlement, requestedBase, MIN_LOGS, true);
    }

    @Nullable
    private static Tree inspectTree(ServerLevel level, Settlement settlement,
                                    BlockPos requestedBase, int minimumLogs, boolean requireCanopy) {
        if (!level.hasChunkAt(requestedBase) || !settlement.contains(requestedBase)
                || !level.getBlockState(requestedBase).is(BlockTags.LOGS)
                || !level.getBlockState(requestedBase.below()).is(BlockTags.DIRT)) return null;
        Set<BlockPos> logs = connectedLogs(level, settlement, requestedBase);
        if (logs.size() < minimumLogs || logs.size() > MAX_LOGS) return null;
        BlockPos canonicalBase = logs.stream().min(Comparator.<BlockPos>comparingInt(position -> position.getY())
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ)).orElse(requestedBase);
        if (!canonicalBase.equals(requestedBase) || !level.getBlockState(canonicalBase.below()).is(BlockTags.DIRT)) return null;
        if (hasSuspiciousAttachment(level, logs) || (requireCanopy && countLeaves(level, logs) < MIN_LEAVES)) return null;
        Item item = level.getBlockState(canonicalBase).getBlock().asItem();
        if (item == Items.AIR) return null;
        return new Tree(canonicalBase.immutable(), Set.copyOf(logs), item);
    }

    @Nullable
    private static Tree inspectTreeForOrder(ServerLevel level, Settlement settlement, WorkOrder order) {
        BlockPos base = order.targetPosition();
        if (base == null) return null;
        Tree strict = inspectTree(level, settlement, base);
        if (strict != null) return strict;

        // A reserved tree can legitimately lose its canopy proximity and fall below
        // MIN_LOGS as the lumberjack works from the top down. The persisted original
        // amount proves that this is a partially completed physical order. Structural
        // attachments and the exact log species remain validated on every block.
        Tree remaining = inspectTree(level, settlement, base, 1, false);
        return remaining != null && remaining.logs().size() < order.resultAmount()
                && remaining.logItem() == order.result() ? remaining : null;
    }

    @Nullable
    public static WorkOrder createOrderAt(ServerLevel level, Settlement settlement, BlockPos base) {
        Tree tree = inspectTree(level, settlement, base);
        if (tree == null || hasOrderFor(settlement, tree.base())) return null;
        long now = level.getGameTime();
        WorkOrder order = new WorkOrder(UUID.randomUUID(), WorkOrderType.PRODUCE_RESOURCE, 70,
                settlement.id(), tree.base(), level.dimension(), Items.AIR, 0,
                tree.logItem(), tree.logs().size(), now, now + 48000L);
        return settlement.taskBoard().add(order) ? order : null;
    }

    private static boolean needsWood(ServerLevel level, Settlement settlement) {
        int target = Math.max(32, settlement.memberIds().size() * 16);
        return new SettlementStorage(level, settlement).count(stack -> stack.is(ItemTags.LOGS)) < target;
    }

    private static boolean hasReadyLumberjack(ServerLevel level, Settlement settlement) {
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        for (UUID memberId : settlement.memberIds()) {
            PersonData person = society.getPerson(memberId).orElse(null);
            if (person == null || person.getEntityId() == null) continue;
            Entity entity = level.getEntity(person.getEntityId());
            if (entity instanceof ResidentEntity resident && resident.isAlive() && resident.isWorkingAge()
                    && resident.getProfessionData().profession() == NpcProfession.LUMBERJACK
                    && ProfessionService.validateOrFindStation(level, resident)
                    && ProfessionService.hasRequiredTool(resident)) return true;
        }
        return false;
    }

    private static int openOrders(Settlement settlement) {
        int count = 0;
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isForestryOrder(order) && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) count++;
        }
        return count;
    }

    private static boolean hasOrderFor(Settlement settlement, BlockPos base) {
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isForestryOrder(order) && base.equals(order.targetPosition())
                    && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) return true;
        }
        return false;
    }

    public static boolean isForestryOrder(WorkOrder order) {
        return order.type() == WorkOrderType.PRODUCE_RESOURCE && order.targetPosition() != null
                && new ItemStack(order.result()).is(ItemTags.LOGS);
    }

    @Nullable
    private static Tree findTree(ServerLevel level, Settlement settlement) {
        int radius = Math.min(64, settlement.radius());
        int offset = (int) ((level.getGameTime() / 600L) & 1L);
        for (int dx = -radius + offset; dx <= radius; dx += SEARCH_STEP) {
            for (int dz = -radius + offset; dz <= radius; dz += SEARCH_STEP) {
                BlockPos column = settlement.center().offset(dx, 0, dz);
                if (!settlement.contains(column) || !level.hasChunkAt(column)) continue;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ());
                for (int y = top; y >= Math.max(level.getMinBuildHeight(), top - 18); y--) {
                    BlockPos candidate = new BlockPos(column.getX(), y, column.getZ());
                    if (!level.getBlockState(candidate).is(BlockTags.LOGS)
                            || level.getBlockState(candidate.below()).is(BlockTags.LOGS)) continue;
                    Tree tree = inspectTree(level, settlement, candidate);
                    if (tree != null && !hasOrderFor(settlement, tree.base())) return tree;
                }
            }
        }
        return null;
    }

    private static Set<BlockPos> connectedLogs(ServerLevel level, Settlement settlement, BlockPos start) {
        Set<BlockPos> found = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        while (!queue.isEmpty() && found.size() <= MAX_LOGS) {
            BlockPos position = queue.removeFirst();
            if (found.contains(position) || !settlement.contains(position) || !level.hasChunkAt(position)
                    || !level.getBlockState(position).is(BlockTags.LOGS)) continue;
            found.add(position.immutable());
            for (Direction direction : Direction.values()) queue.add(position.relative(direction));
        }
        return found;
    }

    private static int countLeaves(ServerLevel level, Set<BlockPos> logs) {
        Set<BlockPos> leaves = new HashSet<>();
        for (BlockPos log : logs) {
            for (BlockPos position : BlockPos.betweenClosed(log.offset(-CANOPY_RADIUS, -1, -CANOPY_RADIUS),
                    log.offset(CANOPY_RADIUS, CANOPY_RADIUS, CANOPY_RADIUS))) {
                if (level.hasChunkAt(position) && level.getBlockState(position).is(BlockTags.LEAVES)) {
                    leaves.add(position.immutable());
                    if (leaves.size() >= MIN_LEAVES) return leaves.size();
                }
            }
        }
        return leaves.size();
    }

    private static boolean hasSuspiciousAttachment(ServerLevel level, Set<BlockPos> logs) {
        for (BlockPos log : logs) {
            if (level.getBlockEntity(log) != null) return true;
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = log.relative(direction);
                if (logs.contains(neighborPos)) continue;
                BlockState neighbor = level.getBlockState(neighborPos);
                if (neighbor.isAir() || neighbor.is(BlockTags.LEAVES) || neighbor.is(BlockTags.LOGS)
                        || neighbor.is(BlockTags.DIRT) || neighbor.canBeReplaced()
                        || !neighbor.isCollisionShapeFullBlock(level, neighborPos)) continue;
                return true;
            }
        }
        return false;
    }

    private static void replant(ServerLevel level, Settlement settlement, ResidentEntity resident,
                                BlockPos base, Item log) {
        Item sapling = saplingFor(log);
        if (sapling == Items.AIR || !level.getBlockState(base).isAir()) return;
        if (!removeOne(resident.getNpcInventory(), sapling)) {
            SettlementStorage storage = new SettlementStorage(level, settlement);
            if (!storage.moveItemTo(sapling, 1, resident.getNpcInventory())
                    || !removeOne(resident.getNpcInventory(), sapling)) return;
        }
        BlockState saplingState = Block.byItem(sapling).defaultBlockState();
        if (saplingState.isAir() || !saplingState.canSurvive(level, base)
                || !level.setBlock(base, saplingState, Block.UPDATE_ALL)) {
            add(resident.getNpcInventory(), new ItemStack(sapling));
        }
    }

    private static Item saplingFor(Item log) {
        if (log == Items.OAK_LOG || log == Items.OAK_WOOD || log == Items.STRIPPED_OAK_LOG || log == Items.STRIPPED_OAK_WOOD) return Items.OAK_SAPLING;
        if (log == Items.BIRCH_LOG || log == Items.BIRCH_WOOD || log == Items.STRIPPED_BIRCH_LOG || log == Items.STRIPPED_BIRCH_WOOD) return Items.BIRCH_SAPLING;
        if (log == Items.SPRUCE_LOG || log == Items.SPRUCE_WOOD || log == Items.STRIPPED_SPRUCE_LOG || log == Items.STRIPPED_SPRUCE_WOOD) return Items.SPRUCE_SAPLING;
        if (log == Items.JUNGLE_LOG || log == Items.JUNGLE_WOOD || log == Items.STRIPPED_JUNGLE_LOG || log == Items.STRIPPED_JUNGLE_WOOD) return Items.JUNGLE_SAPLING;
        if (log == Items.ACACIA_LOG || log == Items.ACACIA_WOOD || log == Items.STRIPPED_ACACIA_LOG || log == Items.STRIPPED_ACACIA_WOOD) return Items.ACACIA_SAPLING;
        if (log == Items.DARK_OAK_LOG || log == Items.DARK_OAK_WOOD || log == Items.STRIPPED_DARK_OAK_LOG || log == Items.STRIPPED_DARK_OAK_WOOD) return Items.DARK_OAK_SAPLING;
        if (log == Items.CHERRY_LOG || log == Items.CHERRY_WOOD || log == Items.STRIPPED_CHERRY_LOG || log == Items.STRIPPED_CHERRY_WOOD) return Items.CHERRY_SAPLING;
        if (log == Items.MANGROVE_LOG || log == Items.MANGROVE_WOOD || log == Items.STRIPPED_MANGROVE_LOG || log == Items.STRIPPED_MANGROVE_WOOD) return Items.MANGROVE_PROPAGULE;
        return Items.AIR;
    }

    private static boolean canFit(SimpleContainer inventory, List<ItemStack> drops) {
        SimpleContainer simulation = new SimpleContainer(inventory.getContainerSize());
        for (int i = 0; i < inventory.getContainerSize(); i++) simulation.setItem(i, inventory.getItem(i).copy());
        for (ItemStack drop : drops) if (!simulation.addItem(drop.copy()).isEmpty()) return false;
        return true;
    }

    private static void add(SimpleContainer inventory, ItemStack stack) {
        ItemStack remaining = inventory.addItem(stack.copy());
        if (!remaining.isEmpty()) throw new IllegalStateException("Forestry inventory simulation diverged");
    }

    private static boolean removeOne(SimpleContainer inventory, Item item) {
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

    public record Tree(BlockPos base, Set<BlockPos> logs, Item logItem) {}
    public enum WorkResult { IN_PROGRESS, COMPLETED, NO_TOOL, INVENTORY_FULL, UNLOADED, INVALID_TREE, BLOCKED }
}
