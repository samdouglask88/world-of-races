package com.example.worldofraces.society;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FamilyManager {
    private final HumanSocietySavedData data;

    public FamilyManager(HumanSocietySavedData data) {
        this.data = data;
    }

    public PersonData createPerson(String firstName, @Nullable ResourceLocation houseId,
                                   Gender gender, LifeStage lifeStage) {
        return createPerson(UUID.randomUUID(), firstName, houseId, gender, lifeStage);
    }

    public PersonData createPerson(UUID personId, String firstName, @Nullable ResourceLocation houseId,
                                   Gender gender, LifeStage lifeStage) {
        if (data.getPerson(personId).isPresent()) {
            throw new IllegalArgumentException("Pessoa ja registrada: " + personId);
        }
        PersonData person = new PersonData(personId, firstName, houseId, gender, lifeStage);
        data.addPerson(person);
        return person;
    }

    public Optional<PersonData> getPerson(UUID personId) {
        return data.getPerson(personId);
    }

    public void bindEntity(UUID personId, UUID entityId) {
        requirePerson(personId).setEntityId(entityId);
        data.setDirty();
    }

    public void setParents(UUID childId, @Nullable UUID fatherId, @Nullable UUID motherId) {
        PersonData child = requirePerson(childId);
        validateParent(childId, fatherId);
        validateParent(childId, motherId);

        removeFromOldParent(child.getFatherId(), childId);
        removeFromOldParent(child.getMotherId(), childId);
        child.setFatherId(fatherId);
        child.setMotherId(motherId);
        if (fatherId != null) requirePerson(fatherId).addChild(childId);
        if (motherId != null) requirePerson(motherId).addChild(childId);
        data.setDirty();
    }

    public HouseholdData marry(UUID firstId, UUID secondId, @Nullable ResourceLocation primaryHouseId) {
        if (firstId.equals(secondId)) throw new IllegalArgumentException("Uma pessoa nao pode casar consigo mesma");
        PersonData first = requirePerson(firstId);
        PersonData second = requirePerson(secondId);
        if (first.getStatus() != PersonStatus.ALIVE || second.getStatus() != PersonStatus.ALIVE) {
            throw new IllegalArgumentException("Somente pessoas vivas podem se casar");
        }
        if (isCloseRelative(firstId, secondId)) {
            throw new IllegalArgumentException("Casamento entre parentes proximos nao e permitido");
        }
        if (primaryHouseId != null
                && !Objects.equals(primaryHouseId, first.getHouseId())
                && !Objects.equals(primaryHouseId, second.getHouseId())) {
            throw new IllegalArgumentException("A Casa principal deve pertencer a um dos conjuges");
        }
        if (primaryHouseId != null && HouseRegistry.get(primaryHouseId).isEmpty()) {
            throw new IllegalArgumentException("Casa principal desconhecida: " + primaryHouseId);
        }
        divorceCurrentSpouse(first);
        divorceCurrentSpouse(second);
        first.setSpouseId(secondId);
        second.setSpouseId(firstId);

        HouseholdData household = new HouseholdData(
                UUID.randomUUID(), primaryHouseId, firstId, secondId);
        household.addMember(firstId);
        household.addMember(secondId);
        data.addHousehold(household);
        first.setHouseholdId(household.getHouseholdId());
        second.setHouseholdId(household.getHouseholdId());
        data.setDirty();
        return household;
    }

    public PersonData createChild(UUID householdId, String firstName, Gender gender) {
        HouseholdData household = requireHousehold(householdId);
        UUID spouseAId = household.getSpouseAId();
        UUID spouseBId = household.getSpouseBId();
        if (spouseAId == null || spouseBId == null) {
            throw new IllegalArgumentException("O nucleo familiar nao possui dois conjuges registrados");
        }

        PersonData spouseA = requirePerson(spouseAId);
        PersonData spouseB = requirePerson(spouseBId);
        UUID fatherId = parentWithGender(spouseA, spouseB, Gender.MALE);
        UUID motherId = parentWithGender(spouseA, spouseB, Gender.FEMALE);
        if (fatherId == null || motherId == null) {
            throw new IllegalArgumentException("O modelo atual exige um pai e uma mae para criar o filho");
        }

        PersonData child = createPerson(firstName, household.getPrimaryHouseId(), gender, LifeStage.BABY);
        setParents(child.getPersonId(), fatherId, motherId);
        child.setHouseholdId(householdId);
        household.addChild(child.getPersonId());
        data.setDirty();
        return child;
    }

    public void markDeceased(UUID personId) {
        requirePerson(personId).setStatus(PersonStatus.DECEASED);
        data.setDirty();
    }

    public void setLifeStage(UUID personId, LifeStage stage) {
        requirePerson(personId).setLifeStage(stage);
        data.setDirty();
    }

    public int changeAffinity(UUID personId, UUID playerId, int amount) {
        int value = requirePerson(personId).changeAffinity(playerId, amount);
        data.setDirty();
        return value;
    }

    public void recordInteraction(UUID personId, long gameTime) {
        requirePerson(personId).setLastInteractionTime(gameTime);
        data.setDirty();
    }

    public void setInitialLocation(UUID personId, String origin, String residence) {
        requirePerson(personId).setInitialLocation(origin, residence);
        data.setDirty();
    }

    private void validateParent(UUID childId, @Nullable UUID parentId) {
        if (parentId == null) return;
        requirePerson(parentId);
        if (childId.equals(parentId)) throw new IllegalArgumentException("Uma pessoa nao pode ser seu proprio pai ou mae");
        if (isDescendant(parentId, childId, new HashSet<>())) {
            throw new IllegalArgumentException("O parentesco criaria um ciclo genealogico");
        }
    }

    private boolean isDescendant(UUID possibleDescendant, UUID ancestor, Set<UUID> visited) {
        if (!visited.add(ancestor)) return false;
        PersonData person = requirePerson(ancestor);
        for (UUID childId : person.getChildrenIds()) {
            if (childId.equals(possibleDescendant) || isDescendant(possibleDescendant, childId, visited)) return true;
        }
        return false;
    }

    private boolean isCloseRelative(UUID firstId, UUID secondId) {
        PersonData first = requirePerson(firstId);
        PersonData second = requirePerson(secondId);
        if (first.getFatherId() != null && first.getFatherId().equals(secondId)) return true;
        if (first.getMotherId() != null && first.getMotherId().equals(secondId)) return true;
        if (second.getFatherId() != null && second.getFatherId().equals(firstId)) return true;
        if (second.getMotherId() != null && second.getMotherId().equals(firstId)) return true;
        return sharesParent(first, second);
    }

    private boolean sharesParent(PersonData first, PersonData second) {
        return first.getFatherId() != null && first.getFatherId().equals(second.getFatherId())
                || first.getMotherId() != null && first.getMotherId().equals(second.getMotherId());
    }

    private void divorceCurrentSpouse(PersonData person) {
        UUID spouseId = person.getSpouseId();
        if (spouseId != null) data.getPerson(spouseId).ifPresent(spouse -> spouse.setSpouseId(null));
        person.setSpouseId(null);
    }

    private void removeFromOldParent(@Nullable UUID parentId, UUID childId) {
        if (parentId != null) data.getPerson(parentId).ifPresent(parent -> parent.removeChild(childId));
    }

    @Nullable
    private UUID parentWithGender(PersonData first, PersonData second, Gender gender) {
        if (first.getGender() == gender) return first.getPersonId();
        if (second.getGender() == gender) return second.getPersonId();
        return null;
    }

    private HouseholdData requireHousehold(UUID householdId) {
        return data.getHousehold(householdId)
                .orElseThrow(() -> new IllegalArgumentException("Nucleo familiar desconhecido: " + householdId));
    }

    private PersonData requirePerson(UUID personId) {
        return data.getPerson(personId)
                .orElseThrow(() -> new IllegalArgumentException("Pessoa desconhecida: " + personId));
    }
}
