package com.sam.realmfolk.society.reproduction;

import com.sam.realmfolk.entity.ModEntityTypes;
import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.society.FamilyManager;
import com.sam.realmfolk.society.Gender;
import com.sam.realmfolk.society.HouseholdData;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.LifeStage;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.PersonStatus;
import com.sam.realmfolk.society.housing.HousingManager;
import com.sam.realmfolk.society.housing.SettlementResidence;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.settlement.SettlementSavedData;
import com.sam.realmfolk.society.storage.SettlementStorage;
import com.sam.realmfolk.util.NameGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ReproductionManager {
    public static final long DAY_TICKS = 24000L;
    public static final long PREGNANCY_TICKS = DAY_TICKS * 3L;
    public static final long POST_BIRTH_COOLDOWN_TICKS = DAY_TICKS * 5L;
    public static final int CONCEPTION_CHANCE_PERCENT = 35;
    public static final int FOOD_PER_RESIDENT_FOR_CONCEPTION = 3;

    private ReproductionManager() {}

    /** Lightweight frequent update: it only examines already registered pregnancies. */
    public static boolean tick(ServerLevel level, Settlement settlement) {
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        boolean changed = false;
        for (UUID memberId : List.copyOf(settlement.memberIds())) {
            PersonData mother = society.getPerson(memberId).orElse(null);
            if (mother == null || !mother.isPregnant()) continue;
            if (mother.getStatus() != PersonStatus.ALIVE || mother.getHouseholdId() == null) {
                new FamilyManager(society).finishPregnancy(memberId,
                        level.getGameTime() + POST_BIRTH_COOLDOWN_TICKS);
                changed = true;
                continue;
            }
            if (level.getGameTime() < mother.getPregnancyDueGameTime()) continue;
            SettlementResidence residence = settlement.residenceForHousehold(mother.getHouseholdId()).orElse(null);
            ResidentEntity motherEntity = loaded(level, mother);
            if (residence == null || motherEntity == null || !HousingManager.isValid(level, settlement, residence)) {
                if (motherEntity != null) motherEntity.setBirthHomePosition(null);
                continue;
            }
            motherEntity.setBirthHomePosition(residence.interiorPosition());
            if (!HousingManager.isInside(residence, motherEntity.blockPosition())) continue;
            changed |= giveBirth(level, settlement, residence, mother, motherEntity);
        }
        if (changed) SettlementSavedData.get(level.getServer()).changed();
        return changed;
    }

    /** Daily population decisions. No residents or items are simulated in unloaded chunks. */
    public static void dailyUpdate(ServerLevel level, Settlement settlement) {
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        boolean changed = HousingManager.refresh(level, settlement);
        changed |= HousingManager.assignHouseholds(settlement, society);
        changed |= updateAges(level, settlement, society);
        if (formOneHousehold(level, settlement, society)) {
            changed = true;
            changed |= HousingManager.assignHouseholds(settlement, society);
        }

        int housingCapacity = settlement.residences().stream().mapToInt(SettlementResidence::capacity).sum();
        int food = new SettlementStorage(level, settlement).countFood();
        if (housingCapacity > settlement.memberIds().size()
                && food >= Math.max(6, settlement.memberIds().size() * FOOD_PER_RESIDENT_FOR_CONCEPTION)) {
            long day = level.getDayTime() / DAY_TICKS;
            List<HouseholdData> households = society.getHouseholds().stream()
                    .sorted(Comparator.comparing(household -> household.getHouseholdId().toString())).toList();
            for (HouseholdData household : households) {
                SettlementResidence home = settlement.residenceForHousehold(household.getHouseholdId()).orElse(null);
                if (home == null || household.getMemberIds().size() >= home.capacity()) continue;
                PersonData mother = parent(society, household, Gender.FEMALE);
                PersonData father = parent(society, household, Gender.MALE);
                if (!eligibleParents(level, settlement, mother, father, level.getGameTime())) continue;
                if (Math.floorMod(Objects.hash(settlement.id(), household.getHouseholdId(), day), 100)
                        >= CONCEPTION_CHANCE_PERCENT) continue;
                changed |= startPregnancy(society, mother, father, level.getGameTime());
            }
        }
        if (changed) SettlementSavedData.get(level.getServer()).changed();
    }

    public static boolean startPregnancy(HumanSocietySavedData society, PersonData mother,
                                         PersonData father, long now) {
        if (mother == null || father == null || mother.isPregnant()
                || mother.getNextPregnancyAllowedGameTime() > now) return false;
        try {
            new FamilyManager(society).startPregnancy(mother.getPersonId(), father.getPersonId(),
                    now, now + PREGNANCY_TICKS);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean giveBirth(ServerLevel level, Settlement settlement, SettlementResidence residence,
                                     PersonData mother, ResidentEntity motherEntity) {
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        if (mother.getHouseholdId() == null || !HousingManager.isInside(residence, motherEntity.blockPosition())) return false;
        ResidentEntity baby = ModEntityTypes.RESIDENT.get().create(level);
        if (baby == null) return false;
        Gender gender = level.random.nextBoolean() ? Gender.MALE : Gender.FEMALE;
        String name = NameGenerator.generateFirstName(gender == Gender.MALE);
        PersonData child;
        try {
            child = new FamilyManager(society).createChild(mother.getHouseholdId(), name, gender, level.getGameTime());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        baby.initializePerson(child);
        baby.setSettlementId(settlement.id());
        baby.moveTo(residence.interiorPosition().getX() + 0.5D, residence.interiorPosition().getY(),
                residence.interiorPosition().getZ() + 0.5D, motherEntity.getYRot(), 0.0F);
        if (!level.addFreshEntity(baby)) {
            new FamilyManager(society).rollbackCreatedChild(child.getPersonId());
            return false;
        }
        settlement.addMember(child.getPersonId(), level.getGameTime());
        new FamilyManager(society).finishPregnancy(mother.getPersonId(),
                level.getGameTime() + POST_BIRTH_COOLDOWN_TICKS);
        motherEntity.setBirthHomePosition(null);
        motherEntity.playSound(SoundEvents.VILLAGER_CELEBRATE, 0.8F, 1.15F);
        return true;
    }

    private static boolean formOneHousehold(ServerLevel level, Settlement settlement, HumanSocietySavedData society) {
        long freeHomes = settlement.residences().stream().filter(home -> home.householdId() == null).count();
        if (freeHomes <= 0) return false;
        List<PersonData> men = eligibleSingles(level, settlement, society, Gender.MALE);
        List<PersonData> women = eligibleSingles(level, settlement, society, Gender.FEMALE);
        FamilyManager families = new FamilyManager(society);
        for (PersonData woman : women) {
            for (PersonData man : men) {
                try {
                    families.marry(woman.getPersonId(), man.getPersonId(),
                            woman.getHouseId() != null ? woman.getHouseId() : man.getHouseId());
                    return true;
                } catch (IllegalArgumentException ignored) {
                    // Try the next deterministic pair when they are close relatives or otherwise incompatible.
                }
            }
        }
        return false;
    }

    private static List<PersonData> eligibleSingles(ServerLevel level, Settlement settlement,
                                                    HumanSocietySavedData society, Gender gender) {
        List<PersonData> result = new ArrayList<>();
        for (UUID memberId : settlement.memberIds()) {
            PersonData person = society.getPerson(memberId).orElse(null);
            if (person != null && person.getStatus() == PersonStatus.ALIVE && person.getGender() == gender
                    && person.getLifeStage() == LifeStage.ADULT && person.getSpouseId() == null
                    && loaded(level, person) != null) result.add(person);
        }
        result.sort(Comparator.comparing(person -> person.getPersonId().toString()));
        return result;
    }

    private static boolean eligibleParents(ServerLevel level, Settlement settlement, @Nullable PersonData mother,
                                           @Nullable PersonData father, long now) {
        ResidentEntity motherEntity = loaded(level, mother);
        ResidentEntity fatherEntity = loaded(level, father);
        return mother != null && father != null && motherEntity != null && fatherEntity != null
                && motherEntity.distanceToSqr(fatherEntity) <= 256.0D && mother.getStatus() == PersonStatus.ALIVE
                && father.getStatus() == PersonStatus.ALIVE && mother.getLifeStage() == LifeStage.ADULT
                && father.getLifeStage() == LifeStage.ADULT && !mother.isPregnant()
                && mother.getNextPregnancyAllowedGameTime() <= now
                && Objects.equals(mother.getSpouseId(), father.getPersonId())
                && settlement.memberIds().contains(mother.getPersonId())
                && settlement.memberIds().contains(father.getPersonId());
    }

    @Nullable
    private static PersonData parent(HumanSocietySavedData society, HouseholdData household, Gender gender) {
        PersonData first = household.getSpouseAId() == null ? null : society.getPerson(household.getSpouseAId()).orElse(null);
        PersonData second = household.getSpouseBId() == null ? null : society.getPerson(household.getSpouseBId()).orElse(null);
        if (first != null && first.getGender() == gender) return first;
        return second != null && second.getGender() == gender ? second : null;
    }

    private static boolean updateAges(ServerLevel level, Settlement settlement, HumanSocietySavedData society) {
        boolean changed = false;
        FamilyManager families = new FamilyManager(society);
        for (UUID memberId : settlement.memberIds()) {
            PersonData person = society.getPerson(memberId).orElse(null);
            if (person == null || person.getBirthGameTime() < 0L || person.getStatus() != PersonStatus.ALIVE) continue;
            long days = Math.max(0L, (level.getGameTime() - person.getBirthGameTime()) / DAY_TICKS);
            LifeStage stage = days < 2 ? LifeStage.BABY : days < 7 ? LifeStage.CHILD
                    : days < 14 ? LifeStage.TEENAGER : LifeStage.ADULT;
            int age = stage == LifeStage.BABY ? 0 : stage == LifeStage.CHILD ? (int) Math.min(11, 4 + days - 2)
                    : stage == LifeStage.TEENAGER ? (int) Math.min(17, 12 + days - 7)
                    : 18 + (int) ((days - 14) / 7);
            if (stage == person.getLifeStage() && age == person.getAge()) continue;
            families.updateLifeStage(memberId, stage, age);
            ResidentEntity entity = loaded(level, person);
            if (entity != null) entity.applyPersonData(person);
            changed = true;
        }
        return changed;
    }

    @Nullable
    private static ResidentEntity loaded(ServerLevel level, PersonData person) {
        if (person == null || person.getEntityId() == null) return null;
        Entity entity = level.getEntity(person.getEntityId());
        return entity instanceof ResidentEntity resident && resident.isAlive() ? resident : null;
    }
}
