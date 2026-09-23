package com.sam.realmfolk.society.housing;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

/** A physical home anchored to a real bed. Noble House membership is stored separately. */
public final class SettlementResidence {
    public static final int DEFAULT_CAPACITY = 6;

    private final UUID id;
    private final BlockPos bedPosition;
    private BlockPos interiorPosition;
    private int capacity;
    @Nullable private UUID householdId;

    public SettlementResidence(UUID id, BlockPos bedPosition, BlockPos interiorPosition, int capacity) {
        this.id = id;
        this.bedPosition = bedPosition.immutable();
        this.interiorPosition = interiorPosition.immutable();
        this.capacity = Math.max(2, capacity);
    }

    public UUID id() { return id; }
    public BlockPos bedPosition() { return bedPosition; }
    public BlockPos interiorPosition() { return interiorPosition; }
    public int capacity() { return capacity; }
    public void setCapacity(int value) { capacity = Math.max(2, Math.min(10, value)); }
    @Nullable public UUID householdId() { return householdId; }
    public void setHouseholdId(@Nullable UUID value) { householdId = value; }
    public void setInteriorPosition(BlockPos value) { interiorPosition = value.immutable(); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putLong("Bed", bedPosition.asLong());
        tag.putLong("Interior", interiorPosition.asLong());
        tag.putInt("Capacity", capacity);
        if (householdId != null) tag.putUUID("HouseholdId", householdId);
        return tag;
    }

    public static SettlementResidence load(CompoundTag tag) {
        SettlementResidence residence = new SettlementResidence(
                tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID(),
                BlockPos.of(tag.getLong("Bed")), BlockPos.of(tag.getLong("Interior")),
                tag.contains("Capacity") ? tag.getInt("Capacity") : DEFAULT_CAPACITY);
        if (tag.hasUUID("HouseholdId")) residence.householdId = tag.getUUID("HouseholdId");
        return residence;
    }
}
