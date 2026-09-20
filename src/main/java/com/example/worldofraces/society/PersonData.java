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

public final class PersonData {
    private final UUID personId;
    private String firstName;
    @Nullable private ResourceLocation houseId;
    private Gender gender;
    private LifeStage lifeStage;
    private PersonStatus status;
    @Nullable private UUID entityId;
    @Nullable private UUID fatherId;
    @Nullable private UUID motherId;
    @Nullable private UUID spouseId;
    @Nullable private UUID householdId;
    private final List<UUID> childrenIds = new ArrayList<>();

    public PersonData(UUID personId, String firstName, @Nullable ResourceLocation houseId,
                      Gender gender, LifeStage lifeStage) {
        this.personId = personId;
        this.firstName = firstName;
        this.houseId = houseId;
        this.gender = gender;
        this.lifeStage = lifeStage;
        this.status = PersonStatus.ALIVE;
    }

    public UUID getPersonId() { return personId; }
    public String getFirstName() { return firstName; }
    @Nullable public ResourceLocation getHouseId() { return houseId; }
    public Gender getGender() { return gender; }
    public LifeStage getLifeStage() { return lifeStage; }
    public PersonStatus getStatus() { return status; }
    @Nullable public UUID getEntityId() { return entityId; }
    @Nullable public UUID getFatherId() { return fatherId; }
    @Nullable public UUID getMotherId() { return motherId; }
    @Nullable public UUID getSpouseId() { return spouseId; }
    @Nullable public UUID getHouseholdId() { return householdId; }
    public List<UUID> getChildrenIds() { return Collections.unmodifiableList(childrenIds); }

    public String getDisplayName() {
        if (houseId == null) return firstName;
        return HouseRegistry.get(houseId)
                .map(house -> firstName + " " + house.surname())
                .orElse(firstName);
    }

    void setEntityId(@Nullable UUID entityId) { this.entityId = entityId; }
    void setFatherId(@Nullable UUID fatherId) { this.fatherId = fatherId; }
    void setMotherId(@Nullable UUID motherId) { this.motherId = motherId; }
    void setSpouseId(@Nullable UUID spouseId) { this.spouseId = spouseId; }
    void setHouseholdId(@Nullable UUID householdId) { this.householdId = householdId; }
    void setStatus(PersonStatus status) { this.status = status; }
    void setLifeStage(LifeStage lifeStage) { this.lifeStage = lifeStage; }

    void addChild(UUID childId) {
        if (!childrenIds.contains(childId)) childrenIds.add(childId);
    }

    void removeChild(UUID childId) {
        childrenIds.remove(childId);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("PersonId", personId);
        tag.putString("FirstName", firstName);
        if (houseId != null) tag.putString("HouseId", houseId.toString());
        tag.putString("Gender", gender.name());
        tag.putString("LifeStage", lifeStage.name());
        tag.putString("Status", status.name());
        if (entityId != null) tag.putUUID("EntityId", entityId);
        if (fatherId != null) tag.putUUID("FatherId", fatherId);
        if (motherId != null) tag.putUUID("MotherId", motherId);
        if (spouseId != null) tag.putUUID("SpouseId", spouseId);
        if (householdId != null) tag.putUUID("HouseholdId", householdId);

        ListTag children = new ListTag();
        for (UUID childId : childrenIds) {
            CompoundTag child = new CompoundTag();
            child.putUUID("Id", childId);
            children.add(child);
        }
        tag.put("Children", children);
        return tag;
    }

    public static PersonData load(CompoundTag tag) {
        ResourceLocation houseId = tag.contains("HouseId")
                ? new ResourceLocation(tag.getString("HouseId")) : null;
        PersonData person = new PersonData(
                tag.getUUID("PersonId"),
                tag.getString("FirstName"),
                houseId,
                enumValue(Gender.class, tag.getString("Gender"), Gender.MALE),
                enumValue(LifeStage.class, tag.getString("LifeStage"), LifeStage.ADULT)
        );
        person.status = enumValue(PersonStatus.class, tag.getString("Status"), PersonStatus.ALIVE);
        if (tag.hasUUID("EntityId")) person.entityId = tag.getUUID("EntityId");
        if (tag.hasUUID("FatherId")) person.fatherId = tag.getUUID("FatherId");
        if (tag.hasUUID("MotherId")) person.motherId = tag.getUUID("MotherId");
        if (tag.hasUUID("SpouseId")) person.spouseId = tag.getUUID("SpouseId");
        if (tag.hasUUID("HouseholdId")) person.householdId = tag.getUUID("HouseholdId");
        ListTag children = tag.getList("Children", Tag.TAG_COMPOUND);
        for (int i = 0; i < children.size(); i++) {
            CompoundTag child = children.getCompound(i);
            if (child.hasUUID("Id")) person.addChild(child.getUUID("Id"));
        }
        return person;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
