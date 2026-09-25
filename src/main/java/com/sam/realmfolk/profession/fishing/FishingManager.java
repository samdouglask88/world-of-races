package com.sam.realmfolk.profession.fishing;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.profession.ProfessionService;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.economy.DailyEconomyManager;
import com.sam.realmfolk.society.government.SettlementPolicy;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.society.task.WorkOrder;
import com.sam.realmfolk.society.task.WorkOrderStatus;
import com.sam.realmfolk.society.task.WorkOrderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Physical fishing performed from a safe bank beside a real body of source water. */
public final class FishingManager {
    public static final int WORK_INTERVAL_TICKS = 100;
    public static final int SEARCH_RADIUS = 24;
    public static final int MAX_OPEN_ORDERS = 2;
    private static final int MIN_WATER_SOURCES = 6;

    private FishingManager() {}

    public static boolean ensureWorkOrder(ServerLevel level, Settlement settlement) {
        if (!level.hasChunkAt(settlement.center()) || openOrders(settlement) >= MAX_OPEN_ORDERS) return false;
        SettlementStorage storage = new SettlementStorage(level, settlement);
        int foodTarget = Math.max(16, settlement.memberIds().size() * 6);
        if (storage.countFood() >= foodTarget) return false;
        ResidentEntity fisherman = readyFisherman(level, settlement);
        if (fisherman == null || fisherman.getProfessionData().workstation() == null) return false;
        Optional<BlockPos> bank = findFishingBank(level, settlement, fisherman.getProfessionData().workstation());
        return bank.isPresent() && createOrderAt(level, settlement, bank.get()) != null;
    }

    public static boolean canPerform(ServerLevel level, Settlement settlement,
                                     ResidentEntity resident, WorkOrder order) {
        BlockPos bank = order.targetPosition();
        return isFishingOrder(order)
                && resident.getProfessionData().profession() == NpcProfession.FISHERMAN
                && bank != null && settlement.contains(bank) && level.hasChunkAt(bank)
                && ProfessionService.validateOrFindStation(level, resident)
                && ProfessionService.hasRequiredTool(resident)
                && waterBeside(level, bank) != null;
    }

    public static WorkResult fish(ServerLevel level, Settlement settlement,
                                  ResidentEntity resident, WorkOrder order) {
        BlockPos bank = order.targetPosition();
        if (bank == null || !settlement.contains(bank)) return WorkResult.INVALID_SPOT;
        if (!level.hasChunkAt(bank)) return WorkResult.UNLOADED;
        BlockPos water = waterBeside(level, bank);
        if (water == null) return WorkResult.INVALID_SPOT;
        ItemStack rod = ProfessionService.requiredToolStack(resident);
        if (rod.isEmpty()) return WorkResult.NO_TOOL;

        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(water))
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, resident)
                .withOptionalParameter(LootContextParams.KILLER_ENTITY, resident)
                .withLuck(0.0F)
                .create(LootContextParamSets.FISHING);
        LootTable table = level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING);
        List<ItemStack> catchItems = table.getRandomItems(params);
        if (catchItems.isEmpty()) return WorkResult.NO_CATCH;
        if (!canFitAll(resident.getNpcInventory(), catchItems)) return WorkResult.INVENTORY_FULL;
        for (ItemStack stack : catchItems) SettlementStorage.add(resident.getNpcInventory(), stack.copy());
        resident.getNpcInventory().setChanged();
        ProfessionService.damageRequiredTool(resident);
        resident.getProfessionData().addExperience(12);
        level.playSound(null, water, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.5F, 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, water.getX() + 0.5D, water.getY() + 0.9D,
                water.getZ() + 0.5D, 6, 0.25D, 0.05D, 0.25D, 0.05D);
        return WorkResult.COMPLETED;
    }

    @Nullable
    public static WorkOrder createOrderAt(ServerLevel level, Settlement settlement, BlockPos bank) {
        if (!settlement.contains(bank) || !level.hasChunkAt(bank) || waterBeside(level, bank) == null
                || hasOrderFor(settlement, bank)) return null;
        long now = level.getGameTime();
        WorkOrder order = new WorkOrder(UUID.randomUUID(), WorkOrderType.PRODUCE_RESOURCE,
                Math.max(70, settlement.government().priority(SettlementPolicy.FOOD)),
                settlement.id(), bank, level.dimension(), Items.FISHING_ROD, 1,
                Items.COD, 1, now, now + DailyEconomyManager.DAY_TICKS);
        return settlement.taskBoard().add(order) ? order : null;
    }

    public static boolean isFishingOrder(WorkOrder order) {
        return order.type() == WorkOrderType.PRODUCE_RESOURCE && order.targetPosition() != null
                && order.requirement() == Items.FISHING_ROD && order.requiredAmount() == 1
                && order.result() == Items.COD;
    }

    public static Optional<BlockPos> findFishingBank(ServerLevel level, Settlement settlement, BlockPos center) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = center.getY() - 4; y <= center.getY() + 4; y++) {
            for (int x = center.getX() - SEARCH_RADIUS; x <= center.getX() + SEARCH_RADIUS; x++) {
                for (int z = center.getZ() - SEARCH_RADIUS; z <= center.getZ() + SEARCH_RADIUS; z++) {
                    cursor.set(x, y, z);
                    if (!settlement.contains(cursor) || !level.hasChunkAt(cursor) || waterBeside(level, cursor) == null) continue;
                    double distance = cursor.distSqr(center);
                    if (distance < bestDistance) {
                        best = cursor.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    @Nullable
    private static BlockPos waterBeside(ServerLevel level, BlockPos bank) {
        if (!level.getBlockState(bank).isAir() || !level.getBlockState(bank.above()).isAir()
                || !level.getBlockState(bank.below()).isFaceSturdy(level, bank.below(), Direction.UP)) return null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos water = bank.relative(direction);
            if (!level.hasChunkAt(water) || !isOpenSourceWater(level, water)) continue;
            int sources = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos sample = water.offset(dx, 0, dz);
                    if (level.hasChunkAt(sample) && isOpenSourceWater(level, sample)) sources++;
                }
            }
            if (sources >= MIN_WATER_SOURCES) return water.immutable();
        }
        return null;
    }

    private static boolean isOpenSourceWater(ServerLevel level, BlockPos position) {
        return level.getFluidState(position).is(FluidTags.WATER)
                && level.getFluidState(position).isSource()
                && level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty();
    }

    @Nullable
    private static ResidentEntity readyFisherman(ServerLevel level, Settlement settlement) {
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        for (UUID memberId : settlement.memberIds()) {
            PersonData person = society.getPerson(memberId).orElse(null);
            if (person == null || person.getEntityId() == null) continue;
            Entity entity = level.getEntity(person.getEntityId());
            if (entity instanceof ResidentEntity resident && resident.isAlive() && resident.isWorkingAge()
                    && resident.getProfessionData().profession() == NpcProfession.FISHERMAN
                    && ProfessionService.validateOrFindStation(level, resident)
                    && ProfessionService.hasRequiredTool(resident)) return resident;
        }
        return null;
    }

    private static int openOrders(Settlement settlement) {
        int count = 0;
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isFishingOrder(order) && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) count++;
        }
        return count;
    }

    private static boolean hasOrderFor(Settlement settlement, BlockPos bank) {
        for (WorkOrder order : settlement.taskBoard().orders()) {
            if (isFishingOrder(order) && bank.equals(order.targetPosition())
                    && order.status() != WorkOrderStatus.COMPLETED
                    && order.status() != WorkOrderStatus.CANCELLED) return true;
        }
        return false;
    }

    private static boolean canFitAll(SimpleContainer inventory, List<ItemStack> incoming) {
        SimpleContainer simulation = new SimpleContainer(inventory.getContainerSize());
        for (int i = 0; i < inventory.getContainerSize(); i++) simulation.setItem(i, inventory.getItem(i).copy());
        for (ItemStack stack : incoming) if (!simulation.addItem(stack.copy()).isEmpty()) return false;
        return true;
    }

    public enum WorkResult {
        COMPLETED, NO_TOOL, INVENTORY_FULL, NO_CATCH, UNLOADED, INVALID_SPOT
    }
}
