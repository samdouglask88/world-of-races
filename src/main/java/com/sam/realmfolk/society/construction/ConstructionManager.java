package com.sam.realmfolk.society.construction;

import com.sam.realmfolk.Realmfolk;
import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.settlement.SettlementSavedData;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.society.task.WorkOrder;
import com.sam.realmfolk.society.task.WorkOrderStatus;
import com.sam.realmfolk.society.task.WorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ConstructionManager {
    public static final ResourceLocation SMALL_STORAGE_HUT = ResourceLocation.fromNamespaceAndPath(Realmfolk.MODID, "small_storage_hut");
    public static final int BLOCKS_PER_WORK_CYCLE = 2;

    private ConstructionManager() {}

    public static void evaluateStorageNeed(ServerLevel level, Settlement settlement) {
        if (settlement.projects().stream().anyMatch(project -> project.blueprintId().equals(SMALL_STORAGE_HUT)
                && project.stage() != ConstructionStage.CANCELLED)) return;
        Blueprint blueprint = BlueprintLoader.INSTANCE.get(SMALL_STORAGE_HUT).orElse(null);
        if (blueprint == null || settlement.level().ordinal() < blueprint.minimumLevel().ordinal()) return;
        BlockPos origin = findSafePosition(level, settlement, blueprint);
        if (origin == null) return;
        ConstructionProject project = new ConstructionProject(UUID.randomUUID(), blueprint.id(), origin);
        project.setStage(ConstructionStage.WAITING_RESOURCES);
        settlement.addProject(project);
        createMaterialOrders(level, settlement, project, blueprint);
        SettlementSavedData.get(level.getServer()).changed();
    }

    public static WorkResult workProject(ServerLevel level, Settlement settlement, ResidentEntity resident) {
        ConstructionProject project = settlement.projects().stream()
                .filter(value -> value.stage() != ConstructionStage.COMPLETED && value.stage() != ConstructionStage.CANCELLED)
                .findFirst().orElse(null);
        if (project == null) return WorkResult.BLOCKED;
        Blueprint blueprint = BlueprintLoader.INSTANCE.get(project.blueprintId()).orElse(null);
        if (blueprint == null) { project.block("Blueprint não carregado"); return WorkResult.BLOCKED; }
        if (!allChunksLoaded(level, project.origin(), blueprint.size())) return WorkResult.UNLOADED;
        SettlementStorage storage = new SettlementStorage(level, settlement);
        if (project.stage() == ConstructionStage.WAITING_RESOURCES) {
            for (Map.Entry<Item, Integer> material : blueprint.materials().entrySet()) {
                if (storage.count(material.getKey()) < remainingRequired(blueprint, project.blockIndex(), material.getKey())) return WorkResult.WAITING_RESOURCES;
            }
            project.setStage(ConstructionStage.FOUNDATION);
        }

        int placed = 0;
        while (project.blockIndex() < blueprint.blocks().size() && placed < BLOCKS_PER_WORK_CYCLE) {
            Blueprint.BlockEntry entry = blueprint.blocks().get(project.blockIndex());
            BlockPos worldPosition = project.origin().offset(entry.relativePosition());
            if (!settlement.contains(worldPosition) || !level.hasChunkAt(worldPosition)) return WorkResult.UNLOADED;
            BlockState current = level.getBlockState(worldPosition);
            if (current.equals(entry.state())) { project.setBlockIndex(project.blockIndex() + 1); continue; }
            if (level.getBlockEntity(worldPosition) != null || !current.getFluidState().isEmpty()
                    || (!current.isAir() && !current.canBeReplaced())) {
                project.block("Bloco protegido ou ocupado em " + worldPosition.toShortString());
                return WorkResult.BLOCKED;
            }
            Item material = entry.state().getBlock().asItem();
            net.minecraft.world.item.ItemStack consumed = material == Items.AIR
                    ? net.minecraft.world.item.ItemStack.EMPTY : storage.extract(material, 1);
            if (consumed.isEmpty()) {
                project.setStage(ConstructionStage.WAITING_RESOURCES);
                return WorkResult.WAITING_RESOURCES;
            }
            if (!level.setBlock(worldPosition, entry.state(), 3)) {
                storage.insert(consumed);
                project.block("Não foi possível colocar bloco em " + worldPosition.toShortString());
                return WorkResult.BLOCKED;
            }
            project.setBlockIndex(project.blockIndex() + 1);
            placed++;
            updateStage(project, blueprint);
        }
        if (project.blockIndex() >= blueprint.blocks().size()) {
            project.setStage(ConstructionStage.COMPLETED);
            for (Blueprint.BlockEntry entry : blueprint.blocks()) {
                if (entry.state().is(Blocks.BARREL)) settlement.addStorage(project.origin().offset(entry.relativePosition()));
            }
            SettlementSavedData.get(level.getServer()).changed();
            return WorkResult.COMPLETED;
        }
        SettlementSavedData.get(level.getServer()).changed();
        return WorkResult.IN_PROGRESS;
    }

    public static Optional<ConstructionProject> activeProject(Settlement settlement) {
        return settlement.projects().stream().filter(project -> project.stage() != ConstructionStage.COMPLETED
                && project.stage() != ConstructionStage.CANCELLED).findFirst();
    }

    public static PlanResult planProjectAt(ServerLevel level, Settlement settlement,
                                           ResourceLocation blueprintId, BlockPos origin) {
        Blueprint blueprint = BlueprintLoader.INSTANCE.get(blueprintId).orElse(null);
        if (blueprint == null) return PlanResult.BLUEPRINT_MISSING;
        if (settlement.level().ordinal() < blueprint.minimumLevel().ordinal()) return PlanResult.LEVEL_TOO_LOW;
        if (!isSafe(level, settlement, blueprint, origin)) return PlanResult.UNSAFE_SITE;
        if (settlement.projects().stream().anyMatch(project -> project.stage() != ConstructionStage.CANCELLED
                && project.origin().closerThan(origin, Math.max(blueprint.size().getX(), blueprint.size().getZ()) + 3))) {
            return PlanResult.OVERLAPS_PROJECT;
        }
        ConstructionProject project = new ConstructionProject(UUID.randomUUID(), blueprint.id(), origin);
        project.setStage(ConstructionStage.WAITING_RESOURCES);
        if (!settlement.addProject(project)) return PlanResult.OVERLAPS_PROJECT;
        createMaterialOrders(level, settlement, project, blueprint);
        SettlementSavedData.get(level.getServer()).changed();
        return PlanResult.SUCCESS;
    }

    public static boolean canPlanAt(ServerLevel level, Settlement settlement,
                                    ResourceLocation blueprintId, BlockPos origin) {
        Blueprint blueprint = BlueprintLoader.INSTANCE.get(blueprintId).orElse(null);
        return blueprint != null && settlement.level().ordinal() >= blueprint.minimumLevel().ordinal()
                && isSafe(level, settlement, blueprint, origin);
    }

    private static void createMaterialOrders(ServerLevel level, Settlement settlement,
                                             ConstructionProject project, Blueprint blueprint) {
        long now = level.getGameTime();
        SettlementStorage storage = new SettlementStorage(level, settlement);
        for (Map.Entry<Item, Integer> material : blueprint.materials().entrySet()) {
            int missing = Math.max(0, material.getValue() - storage.count(material.getKey()));
            if (missing == 0) continue;
            WorkOrder order = new WorkOrder(UUID.randomUUID(), WorkOrderType.FETCH_RESOURCE, 70,
                    settlement.id(), project.origin(), level.dimension(), material.getKey(), missing,
                    Items.AIR, 0, now, now + 72000L);
            order.block("Coleta física de " + material.getKey() + " ainda não está integrada");
            settlement.taskBoard().add(order);
        }
        if (blueprint.materials().entrySet().stream().allMatch(entry -> storage.count(entry.getKey()) >= entry.getValue())) {
            createBuildOrder(level, settlement, project);
        }
    }

    public static void ensureBuildOrder(ServerLevel level, Settlement settlement) {
        ConstructionProject project = activeProject(settlement).orElse(null);
        if (project == null || project.stage() == ConstructionStage.BLOCKED) return;
        Blueprint blueprint = BlueprintLoader.INSTANCE.get(project.blueprintId()).orElse(null);
        if (blueprint == null) return;
        SettlementStorage storage = new SettlementStorage(level, settlement);
        boolean ready = blueprint.materials().entrySet().stream()
                .allMatch(entry -> storage.count(entry.getKey()) >= remainingRequired(blueprint, project.blockIndex(), entry.getKey()));
        if (!ready) return;
        boolean exists = settlement.taskBoard().orders().stream().anyMatch(order -> order.type() == WorkOrderType.BUILD
                && order.targetPosition() != null && order.targetPosition().equals(project.origin())
                && order.status() != WorkOrderStatus.COMPLETED && order.status() != WorkOrderStatus.CANCELLED);
        if (!exists) createBuildOrder(level, settlement, project);
    }

    private static void createBuildOrder(ServerLevel level, Settlement settlement, ConstructionProject project) {
        long now = level.getGameTime();
        if (settlement.taskBoard().add(new WorkOrder(UUID.randomUUID(), WorkOrderType.BUILD, 85, settlement.id(),
                project.origin(), level.dimension(), Items.AIR, 0, Items.BARREL, 1, now, now + 96000L))) {
            SettlementSavedData.get(level.getServer()).changed();
        }
    }

    @Nullable
    private static BlockPos findSafePosition(ServerLevel level, Settlement settlement, Blueprint blueprint) {
        for (int radius = 10; radius <= Math.min(settlement.radius() - 6, 40); radius += 5) {
            for (int x = -radius; x <= radius; x += 5) {
                for (int zSign : new int[]{-radius, radius}) {
                    BlockPos candidate = settlement.center().offset(x, 0, zSign);
                    if (!level.hasChunkAt(candidate)) continue;
                    BlockPos surface = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate);
                    if (isSafe(level, settlement, blueprint, surface)) return surface;
                }
            }
            for (int z = -radius + 5; z < radius; z += 5) {
                for (int xSign : new int[]{-radius, radius}) {
                    BlockPos candidate = settlement.center().offset(xSign, 0, z);
                    if (!level.hasChunkAt(candidate)) continue;
                    BlockPos surface = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, candidate);
                    if (isSafe(level, settlement, blueprint, surface)) return surface;
                }
            }
        }
        return null;
    }

    private static boolean isSafe(ServerLevel level, Settlement settlement, Blueprint blueprint, BlockPos origin) {
        BlockPos max = origin.offset(blueprint.size().getX() - 1, blueprint.size().getY() - 1, blueprint.size().getZ() - 1);
        if (!settlement.contains(origin) || !settlement.contains(max) || !allChunksLoaded(level, origin, blueprint.size())) return false;
        for (ConstructionProject existing : settlement.projects()) {
            if (existing.stage() == ConstructionStage.CANCELLED) continue;
            if (existing.origin().closerThan(origin, Math.max(blueprint.size().getX(), blueprint.size().getZ()) + 3)) return false;
        }
        int baseY = origin.getY() - 1;
        for (int x = 0; x < blueprint.size().getX(); x++) {
            for (int z = 0; z < blueprint.size().getZ(); z++) {
                BlockPos ground = new BlockPos(origin.getX() + x, baseY, origin.getZ() + z);
                if (!level.getBlockState(ground).isSolidRender(level, ground) || !level.getFluidState(ground).isEmpty()) return false;
            }
        }
        for (int x = 0; x < blueprint.size().getX(); x++) for (int y = 0; y < blueprint.size().getY(); y++)
            for (int z = 0; z < blueprint.size().getZ(); z++) {
                BlockPos pos = origin.offset(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (level.getBlockEntity(pos) != null || !state.getFluidState().isEmpty() || (!state.isAir() && !state.canBeReplaced())) return false;
            }
        return true;
    }

    private static boolean allChunksLoaded(ServerLevel level, BlockPos origin, BlockPos size) {
        int maxX = size.getX() - 1;
        int maxZ = size.getZ() - 1;
        return level.hasChunkAt(origin)
                && level.hasChunkAt(origin.offset(maxX, 0, 0))
                && level.hasChunkAt(origin.offset(0, 0, maxZ))
                && level.hasChunkAt(origin.offset(maxX, 0, maxZ));
    }

    private static int remainingRequired(Blueprint blueprint, int fromIndex, Item item) {
        int count = 0;
        for (int i = Math.max(0, fromIndex); i < blueprint.blocks().size(); i++)
            if (blueprint.blocks().get(i).state().getBlock().asItem() == item) count++;
        return count;
    }

    private static void updateStage(ConstructionProject project, Blueprint blueprint) {
        double progress = blueprint.blocks().isEmpty() ? 1.0D : (double) project.blockIndex() / blueprint.blocks().size();
        project.setStage(progress < 0.35D ? ConstructionStage.FOUNDATION
                : progress < 0.85D ? ConstructionStage.STRUCTURE : ConstructionStage.FINISHING);
    }

    public enum WorkResult { IN_PROGRESS, COMPLETED, WAITING_RESOURCES, UNLOADED, BLOCKED }
    public enum PlanResult { SUCCESS, BLUEPRINT_MISSING, LEVEL_TOO_LOW, UNSAFE_SITE, OVERLAPS_PROJECT }
}
