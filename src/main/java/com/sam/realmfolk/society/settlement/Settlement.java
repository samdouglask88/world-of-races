package com.sam.realmfolk.society.settlement;

import com.sam.realmfolk.society.construction.ConstructionProject;
import com.sam.realmfolk.society.economy.SettlementEconomy;
import com.sam.realmfolk.society.government.SettlementGovernment;
import com.sam.realmfolk.society.housing.SettlementResidence;
import com.sam.realmfolk.society.storage.StorageReservation;
import com.sam.realmfolk.society.task.TaskBoard;
import com.sam.realmfolk.society.task.WorkOrderStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Settlement {
    private final UUID id;
    private String name;
    private final BlockPos center;
    private final ResourceKey<Level> dimension;
    private int radius;
    @Nullable private UUID leaderId;
    private final Set<UUID> memberIds = new LinkedHashSet<>();
    private final Map<UUID, Long> memberJoinTimes = new LinkedHashMap<>();
    private SettlementLevel level;
    private SettlementGovernment government;
    private SettlementEconomy economy;
    private TaskBoard taskBoard;
    private final List<ConstructionProject> projects = new ArrayList<>();
    private final List<SettlementResidence> residences = new ArrayList<>();
    private final Set<BlockPos> storagePositions = new LinkedHashSet<>();
    private final Set<BlockPos> patrolPositions = new LinkedHashSet<>();
    private final Set<BlockPos> workPositions = new LinkedHashSet<>();
    private final List<StorageReservation> storageReservations = new ArrayList<>();
    @Nullable private BlockPos treasuryPosition;
    private final long createdGameTime;
    private long lastDailyUpdate;

    public Settlement(UUID id, String name, BlockPos center, ResourceKey<Level> dimension,
                      int radius, long createdGameTime) {
        this.id = id;
        this.name = name;
        this.center = center.immutable();
        this.dimension = dimension;
        this.radius = Math.max(16, radius);
        this.level = SettlementLevel.CAMP;
        this.government = new SettlementGovernment();
        this.economy = new SettlementEconomy();
        this.taskBoard = new TaskBoard();
        this.createdGameTime = createdGameTime;
        this.lastDailyUpdate = createdGameTime;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public BlockPos center() { return center; }
    public ResourceKey<Level> dimension() { return dimension; }
    public int radius() { return radius; }
    @Nullable public UUID leaderId() { return leaderId; }
    public Set<UUID> memberIds() { return Collections.unmodifiableSet(memberIds); }
    public long memberJoinTime(UUID personId) { return memberJoinTimes.getOrDefault(personId, createdGameTime); }
    public SettlementLevel level() { return level; }
    public SettlementGovernment government() { return government; }
    public SettlementEconomy economy() { return economy; }
    public TaskBoard taskBoard() { return taskBoard; }
    public List<ConstructionProject> projects() { return Collections.unmodifiableList(projects); }
    public List<SettlementResidence> residences() { return Collections.unmodifiableList(residences); }
    public Set<BlockPos> storagePositions() { return Collections.unmodifiableSet(storagePositions); }
    public Set<BlockPos> patrolPositions() { return Collections.unmodifiableSet(patrolPositions); }
    public Set<BlockPos> workPositions() { return Collections.unmodifiableSet(workPositions); }
    public List<StorageReservation> storageReservations() { return Collections.unmodifiableList(storageReservations); }
    @Nullable public BlockPos treasuryPosition() { return treasuryPosition; }
    public long createdGameTime() { return createdGameTime; }
    public long lastDailyUpdate() { return lastDailyUpdate; }

    public void setName(String value) { if (value != null && !value.isBlank()) name = value; }
    public void setRadius(int value) { radius = Math.max(16, value); }
    public void setLeaderId(@Nullable UUID value) { leaderId = value; }
    public void setLevel(SettlementLevel value) { level = value; }
    public void setLastDailyUpdate(long value) { lastDailyUpdate = value; }

    public boolean addMember(UUID personId, long joinedAt) {
        if (!memberIds.add(personId)) return false;
        memberJoinTimes.put(personId, joinedAt);
        return true;
    }

    public boolean removeMember(UUID personId) {
        memberJoinTimes.remove(personId);
        if (personId.equals(leaderId)) leaderId = null;
        return memberIds.remove(personId);
    }

    public boolean addStorage(BlockPos position) {
        boolean added = storagePositions.add(position.immutable());
        if (treasuryPosition == null) treasuryPosition = position.immutable();
        return added;
    }

    public boolean removeStorage(BlockPos position) {
        boolean removed = storagePositions.remove(position);
        if (position.equals(treasuryPosition)) treasuryPosition = storagePositions.stream().findFirst().orElse(null);
        return removed;
    }

    public boolean addPatrolPosition(BlockPos position) {
        return contains(position) && patrolPositions.size() < 64 && patrolPositions.add(position.immutable());
    }

    public boolean addWorkPosition(BlockPos position) {
        return contains(position) && workPositions.size() < 64 && workPositions.add(position.immutable());
    }

    public boolean removeMarkerPosition(BlockPos position) {
        return patrolPositions.remove(position) | workPositions.remove(position);
    }

    public void setTreasuryPosition(@Nullable BlockPos position) {
        treasuryPosition = position == null ? null : position.immutable();
        if (treasuryPosition != null) storagePositions.add(treasuryPosition);
    }

    public int reservedAmount(Item item, long now) {
        int total = 0;
        for (StorageReservation reservation : storageReservations) {
            if (reservation.expiresAt() > now && reservation.item() == item) total += reservation.amount();
        }
        return total;
    }

    public boolean reserveStorage(UUID orderId, Item item, int amount, long expiresAt) {
        if (amount <= 0 || storageReservations.stream().anyMatch(value ->
                value.orderId().equals(orderId) && value.item() == item)) return amount <= 0;
        return storageReservations.add(new StorageReservation(UUID.randomUUID(), orderId, item, amount, expiresAt));
    }

    public boolean releaseStorageReservations(UUID orderId) {
        return storageReservations.removeIf(value -> value.orderId().equals(orderId));
    }

    public boolean maintainStorageReservations(long now) {
        return storageReservations.removeIf(reservation -> reservation.expiresAt() <= now
                || taskBoard.get(reservation.orderId()).map(order ->
                order.status() != WorkOrderStatus.RESERVED && order.status() != WorkOrderStatus.IN_PROGRESS).orElse(true));
    }

    public boolean addProject(ConstructionProject project) {
        if (projects.stream().anyMatch(existing -> existing.id().equals(project.id()))) return false;
        projects.add(project);
        return true;
    }

    public boolean addResidence(SettlementResidence residence) {
        if (residences.stream().anyMatch(existing -> existing.id().equals(residence.id())
                || existing.bedPosition().equals(residence.bedPosition()))) return false;
        residences.add(residence);
        return true;
    }

    public boolean removeResidence(UUID residenceId) {
        return residences.removeIf(residence -> residence.id().equals(residenceId));
    }

    public java.util.Optional<SettlementResidence> residenceForHousehold(UUID householdId) {
        return residences.stream().filter(home -> householdId.equals(home.householdId())).findFirst();
    }

    public boolean contains(BlockPos position) {
        long dx = position.getX() - center.getX();
        long dz = position.getZ() - center.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putLong("Center", center.asLong());
        tag.putString("Dimension", dimension.location().toString());
        tag.putInt("Radius", radius);
        if (leaderId != null) tag.putUUID("LeaderId", leaderId);
        tag.putString("Level", level.name());
        tag.put("Government", government.save());
        tag.put("Economy", economy.save());
        tag.put("TaskBoard", taskBoard.save());
        tag.putLong("CreatedGameTime", createdGameTime);
        tag.putLong("LastDailyUpdate", lastDailyUpdate);
        if (treasuryPosition != null) tag.putLong("TreasuryPosition", treasuryPosition.asLong());

        ListTag members = new ListTag();
        for (UUID memberId : memberIds) {
            CompoundTag member = new CompoundTag();
            member.putUUID("Id", memberId);
            member.putLong("JoinedAt", memberJoinTimes.getOrDefault(memberId, createdGameTime));
            members.add(member);
        }
        tag.put("Members", members);

        ListTag storages = new ListTag();
        for (BlockPos storage : storagePositions) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", storage.asLong());
            storages.add(entry);
        }
        tag.put("Storages", storages);

        tag.put("PatrolPositions", savePositions(patrolPositions));
        tag.put("WorkPositions", savePositions(workPositions));

        ListTag reservations = new ListTag();
        storageReservations.forEach(reservation -> reservations.add(reservation.save()));
        tag.put("StorageReservations", reservations);

        ListTag projectList = new ListTag();
        projects.forEach(project -> projectList.add(project.save()));
        tag.put("Projects", projectList);

        ListTag residenceList = new ListTag();
        residences.forEach(residence -> residenceList.add(residence.save()));
        tag.put("Residences", residenceList);
        return tag;
    }

    public static Settlement load(CompoundTag tag) {
        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (dimensionId == null) dimensionId = Level.OVERWORLD.location();
        Settlement settlement = new Settlement(tag.getUUID("Id"), tag.getString("Name"),
                BlockPos.of(tag.getLong("Center")), ResourceKey.create(Registries.DIMENSION, dimensionId),
                tag.contains("Radius") ? tag.getInt("Radius") : 64,
                tag.getLong("CreatedGameTime"));
        if (tag.hasUUID("LeaderId")) settlement.leaderId = tag.getUUID("LeaderId");
        try { settlement.level = SettlementLevel.valueOf(tag.getString("Level")); }
        catch (IllegalArgumentException ignored) { settlement.level = SettlementLevel.CAMP; }
        if (tag.contains("Government", Tag.TAG_COMPOUND)) settlement.government = SettlementGovernment.load(tag.getCompound("Government"));
        if (tag.contains("Economy", Tag.TAG_COMPOUND)) settlement.economy = SettlementEconomy.load(tag.getCompound("Economy"));
        if (tag.contains("TaskBoard", Tag.TAG_COMPOUND)) settlement.taskBoard = TaskBoard.load(tag.getCompound("TaskBoard"));
        settlement.lastDailyUpdate = tag.getLong("LastDailyUpdate");
        if (tag.contains("TreasuryPosition")) settlement.treasuryPosition = BlockPos.of(tag.getLong("TreasuryPosition"));

        ListTag members = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            CompoundTag member = members.getCompound(i);
            if (member.hasUUID("Id")) settlement.addMember(member.getUUID("Id"), member.getLong("JoinedAt"));
        }
        ListTag storages = tag.getList("Storages", Tag.TAG_COMPOUND);
        for (int i = 0; i < storages.size(); i++) settlement.storagePositions.add(BlockPos.of(storages.getCompound(i).getLong("Pos")));
        loadPositions(tag.getList("PatrolPositions", Tag.TAG_COMPOUND), settlement.patrolPositions, 64);
        loadPositions(tag.getList("WorkPositions", Tag.TAG_COMPOUND), settlement.workPositions, 64);
        ListTag reservations = tag.getList("StorageReservations", Tag.TAG_COMPOUND);
        for (int i = 0; i < reservations.size(); i++) settlement.storageReservations.add(StorageReservation.load(reservations.getCompound(i)));
        ListTag projectList = tag.getList("Projects", Tag.TAG_COMPOUND);
        for (int i = 0; i < projectList.size(); i++) settlement.projects.add(ConstructionProject.load(projectList.getCompound(i)));
        ListTag residenceList = tag.getList("Residences", Tag.TAG_COMPOUND);
        for (int i = 0; i < residenceList.size(); i++) settlement.residences.add(SettlementResidence.load(residenceList.getCompound(i)));
        return settlement;
    }

    private static ListTag savePositions(Set<BlockPos> positions) {
        ListTag list = new ListTag();
        for (BlockPos position : positions) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", position.asLong());
            list.add(entry);
        }
        return list;
    }

    private static void loadPositions(ListTag list, Set<BlockPos> target, int limit) {
        for (int i = 0; i < list.size() && target.size() < limit; i++) {
            target.add(BlockPos.of(list.getCompound(i).getLong("Pos")));
        }
    }
}
