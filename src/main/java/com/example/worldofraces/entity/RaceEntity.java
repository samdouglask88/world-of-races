package com.example.worldofraces.entity;

import com.example.worldofraces.client.menu.NpcMenu;
import com.example.worldofraces.society.FamilyManager;
import com.example.worldofraces.society.Gender;
import com.example.worldofraces.society.House;
import com.example.worldofraces.society.HouseRegistry;
import com.example.worldofraces.society.HumanSocietySavedData;
import com.example.worldofraces.society.LifeStage;
import com.example.worldofraces.society.PersonData;
import com.example.worldofraces.util.NameGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class RaceEntity extends PathfinderMob implements MenuProvider {
    @Nullable private UUID personId;
    private String firstName;
    private boolean isMale;
    @Nullable private ResourceLocation houseId;
    private final SimpleContainer npcInventory = new SimpleContainer(18);

    public RaceEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.isMale = this.random.nextBoolean();
        this.firstName = NameGenerator.generateFirstName(this.isMale);
        if (this.random.nextInt(100) < 20) this.houseId = HouseRegistry.randomHumanHouse().id();
        updateDisplayName();
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!(this.level() instanceof ServerLevel serverLevel)) return;

        HumanSocietySavedData society = HumanSocietySavedData.get(serverLevel);
        FamilyManager manager = new FamilyManager(society);
        PersonData person;
        if (this.personId == null) {
            person = manager.createPerson(this.firstName, this.houseId,
                    this.isMale ? Gender.MALE : Gender.FEMALE, LifeStage.ADULT);
            this.personId = person.getPersonId();
        } else {
            person = society.getPerson(this.personId).orElseGet(() -> manager.createPerson(
                    this.personId, this.firstName, this.houseId,
                    this.isMale ? Gender.MALE : Gender.FEMALE, LifeStage.ADULT));
        }
        manager.bindEntity(person.getPersonId(), this.getUUID());
        applyPerson(person);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            PersonData person = getPerson(serverLevel).orElse(null);
            if (person == null) {
                player.sendSystemMessage(Component.literal("[DEBUG] Pessoa ainda nao registrada"));
            } else {
                HumanSocietySavedData society = HumanSocietySavedData.get(serverLevel);
                player.sendSystemMessage(Component.literal(
                        "[DEBUG] " + person.getDisplayName()
                                + " | Casa: " + houseName(person)
                                + " | Pai: " + personName(society, person.getFatherId(), "nenhum")
                                + " | Mae: " + personName(society, person.getMotherId(), "nenhuma")
                                + " | Conjuge: " + personName(society, person.getSpouseId(), "nenhum")
                                + " | Ramo: " + householdName(society, person)
                                + " | Filhos: " + person.getChildrenIds().size()));
            }
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHooks.openScreen(serverPlayer, this, buffer -> buffer.writeVarInt(this.getId()));
            }
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel serverLevel && this.personId != null) {
            HumanSocietySavedData society = HumanSocietySavedData.get(serverLevel);
            if (society.getPerson(this.personId).isPresent()) new FamilyManager(society).markDeceased(this.personId);
        }
        super.die(source);
    }

    public Optional<PersonData> getPerson(ServerLevel level) {
        return personId == null ? Optional.empty() : HumanSocietySavedData.get(level).getPerson(personId);
    }

    @Nullable
    public UUID getPersonId() {
        return personId;
    }

    public SimpleContainer getNpcInventory() {
        return npcInventory;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new NpcMenu(containerId, playerInventory, this);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (personId != null) tag.putUUID("PersonId", personId);
        tag.putString("FirstName", firstName);
        tag.putBoolean("IsMale", isMale);
        if (houseId != null) tag.putString("HouseId", houseId.toString());
        tag.put("NpcInventory", npcInventory.createTag());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("PersonId")) this.personId = tag.getUUID("PersonId");
        if (tag.contains("IsMale")) this.isMale = tag.getBoolean("IsMale");
        if (tag.contains("HouseId")) {
            this.houseId = new ResourceLocation(tag.getString("HouseId"));
        } else if (tag.contains("NobleSurname")) {
            this.houseId = HouseRegistry.getBySurname(tag.getString("NobleSurname")).map(House::id).orElse(null);
        } else {
            this.houseId = null;
        }
        if (tag.contains("NpcInventory", Tag.TAG_LIST)) {
            this.npcInventory.fromTag(tag.getList("NpcInventory", Tag.TAG_COMPOUND));
        }
        if (tag.contains("FirstName")) {
            this.firstName = tag.getString("FirstName");
        } else if (this.getCustomName() != null) {
            String oldName = this.getCustomName().getString();
            String surname = this.houseId == null ? null
                    : HouseRegistry.get(this.houseId).map(House::surname).orElse(null);
            this.firstName = surname != null && oldName.endsWith(" " + surname)
                    ? oldName.substring(0, oldName.length() - surname.length() - 1)
                    : oldName;
        }
        updateDisplayName();
    }

    private void applyPerson(PersonData person) {
        this.firstName = person.getFirstName();
        this.houseId = person.getHouseId();
        this.isMale = person.getGender() == Gender.MALE;
        this.setCustomName(Component.literal(person.getDisplayName()));
    }

    private void updateDisplayName() {
        String displayName = houseId == null ? firstName : HouseRegistry.get(houseId)
                .map(house -> firstName + " " + house.surname()).orElse(firstName);
        this.setCustomName(Component.literal(displayName));
    }

    private static String houseName(PersonData person) {
        if (person.getHouseId() == null) return "nenhuma";
        return HouseRegistry.get(person.getHouseId())
                .map(house -> "Casa " + house.surname()).orElse("desconhecida");
    }

    private static String personName(HumanSocietySavedData society, @Nullable UUID id, String emptyName) {
        if (id == null) return emptyName;
        return society.getPerson(id).map(PersonData::getDisplayName).orElse("desconhecido");
    }

    private static String householdName(HumanSocietySavedData society, PersonData person) {
        if (person.getHouseholdId() == null) return "nenhum";
        return society.getHousehold(person.getHouseholdId())
                .map(household -> household.getPrimaryHouseId() == null
                        ? "sem Casa principal"
                        : HouseRegistry.get(household.getPrimaryHouseId())
                                .map(house -> "Casa " + house.surname()).orElse("Casa desconhecida"))
                .orElse("desconhecido");
    }
}
