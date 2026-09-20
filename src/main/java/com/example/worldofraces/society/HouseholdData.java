package com.example.worldofraces.society;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class HouseholdData {
    private final UUID householdId;
    @Nullable private final ResourceLocation primaryHouseId;
    @Nullable private final UUID spouseAId;
    @Nullable private final UUID spouseBId;
    private final List<UUID> memberIds = new ArrayList<>();
    private final List<UUID> childrenIds = new ArrayList<>();

    public HouseholdData(UUID householdId, @Nullable ResourceLocation primaryHouseId,
                         @Nullable UUID spouseAId, @Nullable UUID spouseBId) {
        this.householdId = householdId;
        this.primaryHouseId = primaryHouseId;
        this.spouseAId = spouseAId;
        this.spouseBId = spouseBId;
    }

    public UUID getHouseholdId() { return householdId; }
    @Nullable public ResourceLocation getPrimaryHouseId() { return primaryHouseId; }
    @Nullable public UUID getSpouseAId() { return spouseAId; }
    @Nullable public UUID getSpouseBId() { return spouseBId; }
    public List<UUID> getMemberIds() { return Collections.unmodifiableList(memberIds); }
    public List<UUID> getChildrenIds() { return Collections.unmodifiableList(childrenIds); }

    public void addMember(UUID personId) {
        if (!memberIds.contains(personId)) memberIds.add(personId);
    }

    public void addChild(UUID personId) {
        if (!childrenIds.contains(personId)) childrenIds.add(personId);
        addMember(personId);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("HouseholdId", householdId);
        if (primaryHouseId != null) tag.putString("PrimaryHouseId", primaryHouseId.toString());
        if (spouseAId != null) tag.putUUID("SpouseAId", spouseAId);
        if (spouseBId != null) tag.putUUID("SpouseBId", spouseBId);
        ListTag members = new ListTag();
        for (UUID memberId : memberIds) {
            CompoundTag member = new CompoundTag();
            member.putUUID("Id", memberId);
            members.add(member);
        }
        tag.put("Members", members);
        ListTag children = new ListTag();
        for (UUID childId : childrenIds) {
            CompoundTag child = new CompoundTag();
            child.putUUID("Id", childId);
            children.add(child);
        }
        tag.put("Children", children);
        return tag;
    }

    public static HouseholdData load(CompoundTag tag) {
        String houseKey = tag.contains("PrimaryHouseId") ? "PrimaryHouseId" : "HouseId";
        ResourceLocation primaryHouseId = tag.contains(houseKey)
                ? new ResourceLocation(tag.getString(houseKey)) : null;
        HouseholdData household = new HouseholdData(
                tag.getUUID("HouseholdId"), primaryHouseId,
                tag.hasUUID("SpouseAId") ? tag.getUUID("SpouseAId") : null,
                tag.hasUUID("SpouseBId") ? tag.getUUID("SpouseBId") : null);
        ListTag members = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            CompoundTag member = members.getCompound(i);
            if (member.hasUUID("Id")) household.addMember(member.getUUID("Id"));
        }
        ListTag children = tag.getList("Children", Tag.TAG_COMPOUND);
        for (int i = 0; i < children.size(); i++) {
            CompoundTag child = children.getCompound(i);
            if (child.hasUUID("Id")) household.addChild(child.getUUID("Id"));
        }
        return household;
    }
}
