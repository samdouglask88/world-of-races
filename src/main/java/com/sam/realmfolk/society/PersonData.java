package com.sam.realmfolk.society;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final Map<UUID, Integer> playerAffinities = new LinkedHashMap<>();
    private int age;
    private String profession;
    private String personality;
    private String morality;
    private String origin;
    private String residence;
    private long lastInteractionTime = -1L;
    private int strength;
    private int intelligence;
    private int charisma;
    private int courage;

    public PersonData(UUID personId, String firstName, @Nullable ResourceLocation houseId,
                      Gender gender, LifeStage lifeStage) {
        this.personId = personId;
        this.firstName = firstName;
        this.houseId = houseId;
        this.gender = gender;
        this.lifeStage = lifeStage;
        this.status = PersonStatus.ALIVE;
        initializeProfile();
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
    public int getAffinity(UUID playerId) { return playerAffinities.getOrDefault(playerId, 0); }
    public int getAge() { return age; }
    public String getProfession() { return profession; }
    public String getPersonality() { return personality; }
    public String getMorality() { return morality; }
    public String getOrigin() { return origin; }
    public String getResidence() { return residence; }
    public long getLastInteractionTime() { return lastInteractionTime; }
    public int getStrength() { return strength; }
    public int getIntelligence() { return intelligence; }
    public int getCharisma() { return charisma; }
    public int getCourage() { return courage; }

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
    void setLastInteractionTime(long time) { this.lastInteractionTime = time; }
    void setInitialLocation(String origin, String residence) {
        if (this.origin.equals("Desconhecida")) this.origin = origin;
        if (this.residence.equals("Nao definida")) this.residence = residence;
    }

    int changeAffinity(UUID playerId, int amount) {
        int value = Math.max(-100, Math.min(100, getAffinity(playerId) + amount));
        playerAffinities.put(playerId, value);
        return value;
    }

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

        ListTag affinities = new ListTag();
        playerAffinities.forEach((playerId, value) -> {
            CompoundTag affinity = new CompoundTag();
            affinity.putUUID("PlayerId", playerId);
            affinity.putInt("Value", value);
            affinities.add(affinity);
        });
        tag.put("PlayerAffinities", affinities);
        tag.putInt("Age", age);
        tag.putString("Profession", profession);
        tag.putString("Personality", personality);
        tag.putString("Morality", morality);
        tag.putString("Origin", origin);
        tag.putString("Residence", residence);
        tag.putLong("LastInteractionTime", lastInteractionTime);
        tag.putInt("Strength", strength);
        tag.putInt("Intelligence", intelligence);
        tag.putInt("Charisma", charisma);
        tag.putInt("Courage", courage);
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
        ListTag affinities = tag.getList("PlayerAffinities", Tag.TAG_COMPOUND);
        for (int i = 0; i < affinities.size(); i++) {
            CompoundTag affinity = affinities.getCompound(i);
            if (affinity.hasUUID("PlayerId")) {
                person.playerAffinities.put(affinity.getUUID("PlayerId"), affinity.getInt("Value"));
            }
        }
        if (tag.contains("Age")) person.age = tag.getInt("Age");
        if (tag.contains("Profession")) person.profession = tag.getString("Profession");
        if (tag.contains("Personality")) person.personality = tag.getString("Personality");
        if (tag.contains("Morality")) person.morality = tag.getString("Morality");
        if (tag.contains("Origin")) person.origin = tag.getString("Origin");
        if (tag.contains("Residence")) person.residence = tag.getString("Residence");
        if (tag.contains("LastInteractionTime")) person.lastInteractionTime = tag.getLong("LastInteractionTime");
        if (tag.contains("Strength")) person.strength = tag.getInt("Strength");
        if (tag.contains("Intelligence")) person.intelligence = tag.getInt("Intelligence");
        if (tag.contains("Charisma")) person.charisma = tag.getInt("Charisma");
        if (tag.contains("Courage")) person.courage = tag.getInt("Courage");
        return person;
    }

    private void initializeProfile() {
        int seed = personId.hashCode() & Integer.MAX_VALUE;
        this.age = switch (lifeStage) {
            case BABY -> seed % 3;
            case CHILD -> 4 + seed % 8;
            case TEENAGER -> 12 + seed % 6;
            case ADULT -> 18 + seed % 43;
            case ELDER -> 61 + seed % 30;
        };
        String[] professions = {"Agricultor", "Ferreiro", "Cacador", "Mercador", "Artesao", "Guarda", "Curandeiro"};
        String[] traits = {"Leal", "Reservado", "Curioso", "Ambicioso", "Gentil", "Prudente", "Impulsivo", "Honrado"};
        this.profession = lifeStage == LifeStage.ADULT || lifeStage == LifeStage.ELDER
                ? professions[seed % professions.length] : "Sem profissao";
        this.personality = traits[seed % traits.length] + ", " + traits[(seed / 7 + 3) % traits.length];
        this.morality = new String[]{"Boa", "Neutra", "Questionavel"}[(seed / 13) % 3];
        this.origin = "Desconhecida";
        this.residence = "Nao definida";
        this.strength = 1 + seed % 10;
        this.intelligence = 1 + (seed / 11) % 10;
        this.charisma = 1 + (seed / 37) % 10;
        this.courage = 1 + (seed / 101) % 10;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
