package com.sam.realmfolk.society.housing;

import com.sam.realmfolk.society.HouseholdData;
import com.sam.realmfolk.society.FamilyManager;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.content.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class HousingManager {
    public static final int MAX_RESIDENCES = 32;
    private static final int VERTICAL_SEARCH = 16;
    private static final int ENCLOSURE_DISTANCE = 6;

    private HousingManager() {}

    /** Scans only loaded blocks inside the settlement and never force-loads chunks. */
    public static boolean refresh(ServerLevel level, Settlement settlement) {
        boolean changed = false;
        for (SettlementResidence existing : List.copyOf(settlement.residences())) {
            if (level.hasChunkAt(existing.bedPosition()) && !isValid(level, settlement, existing)) {
                settlement.removeResidence(existing.id());
                changed = true;
            }
        }
        if (settlement.residences().size() >= MAX_RESIDENCES) return changed;

        Set<BlockPos> registered = new HashSet<>();
        settlement.residences().forEach(home -> registered.add(home.bedPosition()));
        BlockPos center = settlement.center();
        List<SettlementResidence> found = new ArrayList<>();
        int radius = settlement.radius();
        search:
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if ((long) x * x + (long) z * z > (long) radius * radius) continue;
                for (int y = -VERTICAL_SEARCH; y <= VERTICAL_SEARCH; y++) {
                    BlockPos bed = center.offset(x, y, z);
                    if (registered.contains(bed) || !level.hasChunkAt(bed)) continue;
                    BlockState state = level.getBlockState(bed);
                    if (!(state.getBlock() instanceof BedBlock)
                            || state.getValue(BedBlock.PART) != BedPart.FOOT
                            || !hasMatchingHead(level, bed, state)) continue;
                    BlockPos interior = findInterior(level, bed);
                    if (interior == null || !isEnclosed(level, interior)) continue;
                    SettlementResidence residence = new SettlementResidence(UUID.randomUUID(), bed, interior,
                            SettlementResidence.DEFAULT_CAPACITY);
                    updateCapacity(level, residence);
                    found.add(residence);
                    registered.add(bed.immutable());
                    if (found.size() + settlement.residences().size() >= MAX_RESIDENCES) break search;
                }
            }
        }
        found.sort(Comparator.comparingDouble(home -> home.bedPosition().distSqr(center)));
        for (SettlementResidence residence : found) changed |= settlement.addResidence(residence);
        return changed;
    }

    public static boolean assignHouseholds(Settlement settlement, HumanSocietySavedData society) {
        boolean changed = false;
        Set<UUID> validHouseholds = new HashSet<>();
        for (HouseholdData household : society.getHouseholds()) {
            if (household.getSpouseAId() != null && household.getSpouseBId() != null
                    && settlement.memberIds().contains(household.getSpouseAId())
                    && settlement.memberIds().contains(household.getSpouseBId())) {
                validHouseholds.add(household.getHouseholdId());
            }
        }
        Set<UUID> assigned = new HashSet<>();
        for (SettlementResidence home : settlement.residences()) {
            if (home.householdId() != null && (!validHouseholds.contains(home.householdId())
                    || !assigned.add(home.householdId()))) {
                home.setHouseholdId(null);
                changed = true;
            }
        }
        List<UUID> waiting = validHouseholds.stream().filter(id -> !assigned.contains(id))
                .sorted(Comparator.comparing(UUID::toString)).toList();
        int index = 0;
        for (SettlementResidence home : settlement.residences()) {
            if (index >= waiting.size()) break;
            if (home.householdId() != null) continue;
            home.setHouseholdId(waiting.get(index++));
            changed = true;
        }
        FamilyManager families = new FamilyManager(society);
        for (HouseholdData household : society.getHouseholds()) {
            if (!validHouseholds.contains(household.getHouseholdId())) continue;
            SettlementResidence home = settlement.residenceForHousehold(household.getHouseholdId()).orElse(null);
            String label = home == null ? "Sem moradia fisica valida" : "Casa em X "
                    + home.interiorPosition().getX() + ", Y " + home.interiorPosition().getY()
                    + ", Z " + home.interiorPosition().getZ();
            for (UUID memberId : household.getMemberIds()) changed |= families.updateResidence(memberId, label);
        }
        return changed;
    }

    public static boolean assignResidence(Settlement settlement, HumanSocietySavedData society,
                                          UUID householdId, BlockPos clickedBed) {
        if (society.getHousehold(householdId).isEmpty()) return false;
        SettlementResidence selected = settlement.residences().stream()
                .filter(home -> home.bedPosition().closerThan(clickedBed, 2.0D)).findFirst().orElse(null);
        if (selected == null) return false;
        for (SettlementResidence home : settlement.residences()) {
            if (householdId.equals(home.householdId()) || home == selected) home.setHouseholdId(null);
        }
        selected.setHouseholdId(householdId);
        assignHouseholds(settlement, society);
        return householdId.equals(selected.householdId());
    }

    public static boolean isValid(ServerLevel level, Settlement settlement, SettlementResidence residence) {
        BlockPos bed = residence.bedPosition();
        if (!settlement.contains(bed) || !level.hasChunkAt(bed)) return false;
        BlockState state = level.getBlockState(bed);
        if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.FOOT
                || !hasMatchingHead(level, bed, state)) return false;
        BlockPos interior = findInterior(level, bed);
        if (interior == null || !isEnclosed(level, interior)) return false;
        residence.setInteriorPosition(interior);
        updateCapacity(level, residence);
        return true;
    }

    private static void updateCapacity(ServerLevel level, SettlementResidence residence) {
        int cradles = 0;
        BlockPos interior = residence.interiorPosition();
        for (BlockPos check : BlockPos.betweenClosed(interior.offset(-5, -1, -5), interior.offset(5, 3, 5))) {
            if (level.hasChunkAt(check) && level.getBlockState(check).is(ModBlocks.CRADLE.get())) cradles++;
        }
        residence.setCapacity(SettlementResidence.DEFAULT_CAPACITY + Math.min(2, cradles) * 2);
    }

    public static boolean isInside(SettlementResidence residence, BlockPos position) {
        return residence.interiorPosition().distSqr(position) <= 9.0D;
    }

    @Nullable
    private static BlockPos findInterior(ServerLevel level, BlockPos bed) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = bed.relative(direction);
            if (canStand(level, candidate)) return candidate.immutable();
        }
        return canStand(level, bed.above()) ? bed.above().immutable() : null;
    }

    private static boolean canStand(ServerLevel level, BlockPos position) {
        return level.hasChunkAt(position) && level.getBlockState(position).getCollisionShape(level, position).isEmpty()
                && level.getBlockState(position.above()).getCollisionShape(level, position.above()).isEmpty()
                && !level.getBlockState(position.below()).getCollisionShape(level, position.below()).isEmpty()
                && level.getFluidState(position).isEmpty() && level.getFluidState(position.above()).isEmpty();
    }

    private static boolean hasMatchingHead(ServerLevel level, BlockPos foot, BlockState footState) {
        BlockPos head = foot.relative(footState.getValue(BedBlock.FACING));
        if (!level.hasChunkAt(head)) return false;
        BlockState headState = level.getBlockState(head);
        return headState.getBlock() == footState.getBlock()
                && headState.getValue(BedBlock.PART) == BedPart.HEAD
                && headState.getValue(BedBlock.FACING) == footState.getValue(BedBlock.FACING);
    }

    /** Requires a roof and a boundary in all four horizontal directions around the birth point. */
    private static boolean isEnclosed(ServerLevel level, BlockPos interior) {
        boolean roof = false;
        for (int distance = 2; distance <= ENCLOSURE_DISTANCE; distance++) {
            BlockPos check = interior.above(distance);
            if (!level.hasChunkAt(check)) return false;
            if (boundary(level, check)) { roof = true; break; }
        }
        if (!roof) return false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            boolean wall = false;
            for (int distance = 1; distance <= ENCLOSURE_DISTANCE; distance++) {
                BlockPos check = interior.relative(direction, distance).above();
                if (!level.hasChunkAt(check)) return false;
                if (boundary(level, check)) { wall = true; break; }
            }
            if (!wall) return false;
        }
        return true;
    }

    private static boolean boundary(ServerLevel level, BlockPos position) {
        BlockState state = level.getBlockState(position);
        return state.getBlock() instanceof DoorBlock || (!state.getCollisionShape(level, position).isEmpty()
                && !(state.getBlock() instanceof BedBlock));
    }
}
