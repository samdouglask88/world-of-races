package com.sam.realmfolk.society.ai;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.integration.HostileMobIntegration;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.housing.SettlementResidence;
import com.sam.realmfolk.society.needs.NeedType;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.settlement.SettlementSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Moves a resident to a real, unoccupied bed and uses Minecraft's sleeping pose until morning. */
public final class SleepAtHomeGoal extends Goal {
    private static final int HOME_BED_RADIUS = 7;
    private static final int HOME_BED_VERTICAL = 3;
    private static final int HOMELESS_BED_RADIUS = 8;
    private static final double ARRIVAL_DISTANCE_SQR = 4.0D;
    private final ResidentEntity resident;
    @Nullable private BlockPos bed;
    private int travelTicks;
    private int sleepingTicks;

    public SleepAtHomeGoal(ResidentEntity resident) {
        this.resident = resident;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    public static boolean isSleepTime(ServerLevel level) {
        long time = Math.floorMod(level.getDayTime(), 24000L);
        return time >= 12500L && time < 23500L;
    }

    @Override
    public boolean canUse() {
        if (!(resident.level() instanceof ServerLevel level) || !resident.isAlive() || resident.isPassenger()
                || resident.isSleeping() || resident.getNpcBrain().currentAction() != NpcAction.REST
                || !isSleepTime(level) || dangerNearby(level)) return false;
        bed = findBed(level, resident).orElse(null);
        return bed != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (!(resident.level() instanceof ServerLevel level) || bed == null || !resident.isAlive()
                || resident.getNpcBrain().currentAction() != NpcAction.REST || !isSleepTime(level)
                || dangerNearby(level) || !level.hasChunkAt(bed)) return false;
        return resident.isSleeping() || travelTicks < 400;
    }

    @Override
    public void start() {
        travelTicks = 0;
        sleepingTicks = 0;
        moveToBed();
    }

    @Override
    public void tick() {
        if (!(resident.level() instanceof ServerLevel level) || bed == null) return;
        if (resident.isSleeping()) {
            sleepingTicks++;
            if (sleepingTicks % 20 == 0) resident.getNeeds().change(NeedType.REST, -5);
            return;
        }
        travelTicks++;
        double distance = resident.distanceToSqr(bed.getX() + 0.5D, bed.getY() + 0.5D, bed.getZ() + 0.5D);
        if (distance > ARRIVAL_DISTANCE_SQR) {
            if (travelTicks % 20 == 1 || resident.getNavigation().isDone()) moveToBed();
            return;
        }
        BlockState state = level.getBlockState(bed);
        if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.OCCUPIED)) {
            bed = findBed(level, resident).orElse(null);
            if (bed != null) moveToBed();
            return;
        }
        resident.getNavigation().stop();
        setOccupied(level, bed, true);
        resident.startSleeping(bed);
    }

    @Override
    public void stop() {
        if (resident.level() instanceof ServerLevel level && bed != null) {
            if (resident.isSleeping()) resident.stopSleeping();
            setOccupied(level, bed, false);
        }
        resident.getNavigation().stop();
        if (resident.level() instanceof ServerLevel level
                && resident.getNpcBrain().currentAction() == NpcAction.REST) {
            resident.getNpcBrain().clearAction(isSleepTime(level)
                    ? "Cama residencial indisponivel" : "Acordou ao amanhecer");
        }
        bed = null;
        travelTicks = 0;
        sleepingTicks = 0;
    }

    private void moveToBed() {
        if (bed != null) resident.getNavigation().moveTo(
                bed.getX() + 0.5D, bed.getY(), bed.getZ() + 0.5D, 1.05D);
    }

    private boolean dangerNearby(ServerLevel level) {
        return !level.getEntitiesOfClass(Mob.class, resident.getBoundingBox().inflate(8.0D),
                mob -> mob.isAlive() && HostileMobIntegration.isHostileToResidents(mob)).isEmpty();
    }

    public static Optional<BlockPos> findBed(ServerLevel level, ResidentEntity resident) {
        Settlement settlement = resident.getSettlementId() == null ? null
                : SettlementSavedData.get(level.getServer()).get(resident.getSettlementId()).orElse(null);
        PersonData person = resident.getPerson(level).orElse(null);
        List<BlockPos> candidates = new ArrayList<>();
        Set<BlockPos> unique = new HashSet<>();

        SettlementResidence familyHome = null;
        if (settlement != null && person != null && person.getHouseholdId() != null) {
            familyHome = settlement.residenceForHousehold(person.getHouseholdId()).orElse(null);
        }
        if (familyHome != null) {
            collectBeds(level, familyHome.interiorPosition(), HOME_BED_RADIUS, HOME_BED_VERTICAL, candidates, unique);
        } else if (settlement != null) {
            for (SettlementResidence residence : settlement.residences()) {
                if (residence.householdId() == null) addBed(level, residence.bedPosition(), candidates, unique);
            }
        }
        if (candidates.isEmpty()) {
            collectBeds(level, resident.blockPosition(), HOMELESS_BED_RADIUS, HOME_BED_VERTICAL, candidates, unique);
        }
        candidates.removeIf(position -> !available(level, position));
        candidates.sort(Comparator.comparingDouble(position -> position.distSqr(resident.blockPosition())));
        return candidates.stream().findFirst();
    }

    private static void collectBeds(ServerLevel level, BlockPos center, int radius, int vertical,
                                    List<BlockPos> output, Set<BlockPos> unique) {
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if ((long) x * x + (long) z * z > (long) radius * radius) continue;
            for (int y = -vertical; y <= vertical; y++) addBed(level, center.offset(x, y, z), output, unique);
        }
    }

    private static void addBed(ServerLevel level, BlockPos position, List<BlockPos> output, Set<BlockPos> unique) {
        if (!level.hasChunkAt(position)) return;
        BlockState state = level.getBlockState(position);
        if (!(state.getBlock() instanceof BedBlock)) return;
        BlockPos foot = state.getValue(BedBlock.PART) == BedPart.FOOT ? position
                : position.relative(state.getValue(BedBlock.FACING).getOpposite());
        foot = foot.immutable();
        if (unique.add(foot)) output.add(foot);
    }

    private static boolean available(ServerLevel level, BlockPos position) {
        if (!level.hasChunkAt(position)) return false;
        BlockState state = level.getBlockState(position);
        return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT
                && !state.getValue(BedBlock.OCCUPIED);
    }

    private static void setOccupied(ServerLevel level, BlockPos position, boolean occupied) {
        if (!level.hasChunkAt(position)) return;
        BlockState state = level.getBlockState(position);
        if (!(state.getBlock() instanceof BedBlock)) return;
        level.setBlock(position, state.setValue(BedBlock.OCCUPIED, occupied), 3);
        BlockPos other = state.getValue(BedBlock.PART) == BedPart.FOOT
                ? position.relative(state.getValue(BedBlock.FACING))
                : position.relative(state.getValue(BedBlock.FACING).getOpposite());
        if (!level.hasChunkAt(other)) return;
        BlockState otherState = level.getBlockState(other);
        if (otherState.getBlock() == state.getBlock()) {
            level.setBlock(other, otherState.setValue(BedBlock.OCCUPIED, occupied), 3);
        }
    }
}
