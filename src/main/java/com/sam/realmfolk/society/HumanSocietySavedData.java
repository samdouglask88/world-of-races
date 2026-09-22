package com.sam.realmfolk.society;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class HumanSocietySavedData extends SavedData {
    private static final String DATA_NAME = "realmfolk_human_society";

    private final Map<UUID, PersonData> people = new LinkedHashMap<>();
    private final Map<UUID, HouseholdData> households = new LinkedHashMap<>();

    public static HumanSocietySavedData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                HumanSocietySavedData::load,
                HumanSocietySavedData::new,
                DATA_NAME
        );
    }

    public Optional<PersonData> getPerson(UUID personId) {
        return Optional.ofNullable(people.get(personId));
    }

    public Optional<HouseholdData> getHousehold(UUID householdId) {
        return Optional.ofNullable(households.get(householdId));
    }

    public Collection<PersonData> getPeople() {
        return Collections.unmodifiableCollection(people.values());
    }

    public Collection<HouseholdData> getHouseholds() {
        return Collections.unmodifiableCollection(households.values());
    }

    void addPerson(PersonData person) {
        people.put(person.getPersonId(), person);
        setDirty();
    }

    void addHousehold(HouseholdData household) {
        households.put(household.getHouseholdId(), household);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag peopleTag = new ListTag();
        for (PersonData person : people.values()) peopleTag.add(person.save());
        tag.put("People", peopleTag);

        ListTag householdsTag = new ListTag();
        for (HouseholdData household : households.values()) householdsTag.add(household.save());
        tag.put("Households", householdsTag);
        return tag;
    }

    public static HumanSocietySavedData load(CompoundTag tag) {
        HumanSocietySavedData data = new HumanSocietySavedData();
        ListTag peopleTag = tag.getList("People", Tag.TAG_COMPOUND);
        for (int i = 0; i < peopleTag.size(); i++) {
            PersonData person = PersonData.load(peopleTag.getCompound(i));
            data.people.put(person.getPersonId(), person);
        }

        ListTag householdsTag = tag.getList("Households", Tag.TAG_COMPOUND);
        for (int i = 0; i < householdsTag.size(); i++) {
            HouseholdData household = HouseholdData.load(householdsTag.getCompound(i));
            data.households.put(household.getHouseholdId(), household);
        }
        return data;
    }
}
