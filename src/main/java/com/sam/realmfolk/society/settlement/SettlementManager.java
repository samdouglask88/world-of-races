package com.sam.realmfolk.society.settlement;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.LifeStage;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.PersonStatus;
import com.sam.realmfolk.society.government.LeaderType;
import com.sam.realmfolk.society.economy.DailyEconomyManager;
import com.sam.realmfolk.society.housing.HousingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SettlementManager {
    public static final int INITIAL_RADIUS = 64;
    public static final int BELL_SEARCH_RADIUS = 16;
    public static final int MAX_REGISTERED_STORAGES = 32;

    private SettlementManager() {}

    public static Optional<BlockPos> findNearestBell(ServerLevel level, BlockPos origin) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -BELL_SEARCH_RADIUS; x <= BELL_SEARCH_RADIUS; x++) {
            for (int y = -8; y <= 8; y++) {
                for (int z = -BELL_SEARCH_RADIUS; z <= BELL_SEARCH_RADIUS; z++) {
                    BlockPos position = origin.offset(x, y, z);
                    if (!level.hasChunkAt(position) || !level.getBlockState(position).is(Blocks.BELL)) continue;
                    double distance = position.distSqr(origin);
                    if (distance < bestDistance) { bestDistance = distance; best = position.immutable(); }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static Settlement create(ServerLevel level, String name, BlockPos bell) {
        SettlementSavedData data = SettlementSavedData.get(level.getServer());
        Settlement settlement = new Settlement(UUID.randomUUID(), name, bell, level.dimension(), INITIAL_RADIUS, level.getGameTime());
        settlement.setLastDailyUpdate(level.getGameTime() - DailyEconomyManager.DAY_TICKS);
        registerNearbyBarrels(level, settlement);
        linkNearbyResidents(level, settlement);
        HousingManager.refresh(level, settlement);
        HousingManager.assignHouseholds(settlement, HumanSocietySavedData.get(level));
        selectLeader(level, settlement);
        data.add(settlement);
        return settlement;
    }

    public static int registerNearbyBarrels(ServerLevel level, Settlement settlement) {
        int before = settlement.storagePositions().size();
        BlockPos center = settlement.center();
        int radius = settlement.radius();
        List<BlockPos> found = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if ((long) x * x + (long) z * z > (long) radius * radius) continue;
                for (int y = -16; y <= 16; y++) {
                    BlockPos position = center.offset(x, y, z);
                    if (level.hasChunkAt(position) && level.getBlockState(position).is(Blocks.BARREL)) {
                        found.add(position.immutable());
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(position -> position.distSqr(center)));
        for (int i = 0; i < found.size() && settlement.storagePositions().size() < MAX_REGISTERED_STORAGES; i++) {
            settlement.addStorage(found.get(i));
        }
        return settlement.storagePositions().size() - before;
    }

    public static int linkNearbyResidents(ServerLevel level, Settlement settlement) {
        int linked = 0;
        double size = settlement.radius() * 2.0D;
        AABB area = AABB.ofSize(settlement.center().getCenter(), size, 64.0D, size);
        for (ResidentEntity resident : level.getEntitiesOfClass(ResidentEntity.class, area, Entity::isAlive)) {
            UUID personId = resident.getPersonId();
            if (personId == null || resident.getSettlementId() != null) continue;
            resident.setSettlementId(settlement.id());
            if (settlement.addMember(personId, level.getGameTime())) linked++;
        }
        return linked;
    }

    public static Optional<UUID> selectLeader(ServerLevel level, Settlement settlement) {
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        List<PersonData> eligible = new ArrayList<>();
        for (UUID memberId : settlement.memberIds()) {
            PersonData person = society.getPerson(memberId).orElse(null);
            if (person == null || person.getStatus() != PersonStatus.ALIVE) continue;
            if (person.getLifeStage() != LifeStage.ADULT && person.getLifeStage() != LifeStage.ELDER) continue;
            if (person.getEntityId() == null) continue;
            Entity entity = level.getEntity(person.getEntityId());
            if (!(entity instanceof ResidentEntity resident) || !resident.isAlive()
                    || !settlement.id().equals(resident.getSettlementId())) continue;
            eligible.add(person);
        }
        eligible.sort(Comparator.comparingInt((PersonData person) -> leadershipScore(person, settlement)).reversed()
                .thenComparing(person -> person.getPersonId().toString()));
        UUID leader = eligible.isEmpty() ? null : eligible.get(0).getPersonId();
        settlement.setLeaderId(leader);
        PersonData leaderData = leader == null ? null : society.getPerson(leader).orElse(null);
        settlement.government().setLeaderType(leaderData != null && leaderData.getHouseId() != null
                ? LeaderType.NOBLE : LeaderType.APPOINTED);
        SettlementSavedData.get(level.getServer()).changed();
        return Optional.ofNullable(leader);
    }

    private static int leadershipScore(PersonData person, Settlement settlement) {
        int nobility = person.getHouseId() == null ? 0 : 1000;
        int adultExperience = Math.max(0, person.getAge() - 18);
        long tenure = Math.max(0L, settlement.lastDailyUpdate() - settlement.memberJoinTime(person.getPersonId()));
        return nobility + person.getCharisma() * 20 + adultExperience + (int) Math.min(100L, tenure / 24000L);
    }

    public static boolean remove(ServerLevel level, Settlement settlement) {
        com.sam.realmfolk.society.FamilyManager families =
                new com.sam.realmfolk.society.FamilyManager(HumanSocietySavedData.get(level));
        for (UUID personId : settlement.memberIds()) {
            HumanSocietySavedData.get(level).getPerson(personId).filter(PersonData::isPregnant)
                    .ifPresent(person -> families.finishPregnancy(personId, level.getGameTime()));
            HumanSocietySavedData.get(level).getPerson(personId).map(PersonData::getEntityId).ifPresent(entityId -> {
                Entity entity = level.getEntity(entityId);
                if (entity instanceof ResidentEntity resident && settlement.id().equals(resident.getSettlementId())) resident.setSettlementId(null);
            });
        }
        return SettlementSavedData.get(level.getServer()).remove(settlement.id()) != null;
    }

    public static boolean validateMembership(ServerLevel level, ResidentEntity resident) {
        UUID settlementId = resident.getSettlementId();
        if (settlementId == null) return false;
        Settlement settlement = SettlementSavedData.get(level.getServer()).get(settlementId).orElse(null);
        if (settlement == null || resident.getPersonId() == null || !settlement.memberIds().contains(resident.getPersonId())) {
            resident.setSettlementId(null);
            return false;
        }
        return true;
    }
}
