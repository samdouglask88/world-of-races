package com.sam.realmfolk.entity;

import com.sam.realmfolk.client.menu.NpcMenu;
import com.sam.realmfolk.society.FamilyManager;
import com.sam.realmfolk.society.Gender;
import com.sam.realmfolk.society.House;
import com.sam.realmfolk.society.HouseRegistry;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.LifeStage;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.settlement.Settlement;
import com.sam.realmfolk.society.settlement.SettlementManager;
import com.sam.realmfolk.society.settlement.SettlementSavedData;
import com.sam.realmfolk.util.NameGenerator;
import com.sam.realmfolk.profession.ProfessionAptitude;
import com.sam.realmfolk.profession.ProfessionData;
import com.sam.realmfolk.profession.blacksmith.BlacksmithWorkGoal;
import com.sam.realmfolk.society.ai.NpcBrain;
import com.sam.realmfolk.society.ai.SleepAtHomeGoal;
import com.sam.realmfolk.society.ai.WorkOrderGoal;
import com.sam.realmfolk.society.needs.NpcNeeds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ResidentEntity extends PathfinderMob implements MenuProvider, Merchant {
    private static final EntityDataAccessor<Integer> DATA_LIFE_STAGE =
            SynchedEntityData.defineId(ResidentEntity.class, EntityDataSerializers.INT);
    @Nullable private UUID personId;
    @Nullable private UUID settlementId;
    private String firstName;
    private boolean isMale;
    @Nullable private ResourceLocation houseId;
    private final SimpleContainer npcInventory = new SimpleContainer(18);
    private final SimpleContainer tradeInventory = new SimpleContainer(18);
    private boolean tradeStockInitialized;
    private NpcBehaviorMode behaviorMode = NpcBehaviorMode.WANDER;
    @Nullable private UUID followingPlayerId;
    @Nullable private BlockPos stayPosition;
    private final Map<UUID, Long> lastConversationTimes = new HashMap<>();
    @Nullable private Player tradingPlayer;
    private MerchantOffers merchantOffers;
    private int merchantXp;
    private ProfessionData professionData;
    private NpcNeeds needs = new NpcNeeds();
    private final NpcBrain npcBrain = new NpcBrain();
    @Nullable private UUID currentWorkOrderId;
    private int nextBrainTick;
    private int nextNeedsTick;
    @Nullable private BlockPos birthHomePosition;

    public ResidentEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        ProfessionAptitude[] aptitudes=ProfessionAptitude.values();
        this.professionData=new ProfessionData(aptitudes[this.random.nextInt(aptitudes.length)]);
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
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_LIFE_STAGE, LifeStage.ADULT.ordinal());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ReturnHomeForBirthGoal());
        this.goalSelector.addGoal(2, new SleepAtHomeGoal(this));
        this.goalSelector.addGoal(3, new WorkOrderGoal(this));
        this.goalSelector.addGoal(4, new FollowAssignedPlayerGoal());
        this.goalSelector.addGoal(5, new ReturnToStayPositionGoal());
        this.goalSelector.addGoal(6, new MeleeAttackGoal(this, 1.15D, true));
        this.goalSelector.addGoal(7, new BlacksmithWorkGoal(this));
        this.goalSelector.addGoal(8, new WanderWhenFreeGoal());
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Monster.class, true) {
            @Override
            public boolean canUse() {
                return behaviorMode == NpcBehaviorMode.WANDER
                        && professionData.profession() == com.sam.realmfolk.profession.NpcProfession.GUARD
                        && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return behaviorMode == NpcBehaviorMode.WANDER
                        && professionData.profession() == com.sam.realmfolk.profession.NpcProfession.GUARD
                        && super.canContinueToUse();
            }
        });
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
        String biome = serverLevel.getBiome(this.blockPosition()).unwrapKey()
                .map(key -> key.location().getPath().replace('_', ' ')).orElse("regiao desconhecida");
        manager.setInitialLocation(person.getPersonId(), biome,
                "X " + this.blockPosition().getX() + ", Z " + this.blockPosition().getZ());
        applyPersonData(person);
        if (settlementId != null) SettlementManager.validateMembership(serverLevel, this);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            PersonData person = getPerson(serverLevel).orElse(null);
            if (person != null) {
                HumanSocietySavedData society = HumanSocietySavedData.get(serverLevel);
                new FamilyManager(society).recordInteraction(person.getPersonId(), serverLevel.getGameTime());
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
            PersonData deceased = society.getPerson(this.personId).orElse(null);
            if (deceased != null) {
                FamilyManager families = new FamilyManager(society);
                if (deceased.isPregnant()) families.finishPregnancy(this.personId, Long.MAX_VALUE);
                families.markDeceased(this.personId);
            }
            com.sam.realmfolk.profession.ProfessionService.remove(serverLevel,this);
            if (settlementId != null) {
                SettlementSavedData settlements = SettlementSavedData.get(serverLevel.getServer());
                Settlement settlement = settlements.get(settlementId).orElse(null);
                if (settlement != null) {
                    if (currentWorkOrderId != null) {
                        settlement.taskBoard().get(currentWorkOrderId).ifPresent(com.sam.realmfolk.society.task.WorkOrder::release);
                        settlement.releaseStorageReservations(currentWorkOrderId);
                        currentWorkOrderId = null;
                    }
                    boolean wasLeader = this.personId.equals(settlement.leaderId());
                    settlement.removeMember(this.personId);
                    if (wasLeader) SettlementManager.selectLeader(serverLevel, settlement);
                    settlements.changed();
                }
            }
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

    @Nullable
    public UUID getSettlementId() { return settlementId; }

    public void setSettlementId(@Nullable UUID value) {
        settlementId = value;
        setPersistenceRequired();
    }

    public SimpleContainer getNpcInventory() {
        return npcInventory;
    }

    public ProfessionData getProfessionData(){return professionData;}

    public NpcNeeds getNeeds() { return needs; }
    public NpcBrain getNpcBrain() { return npcBrain; }
    @Nullable public UUID getCurrentWorkOrderId() { return currentWorkOrderId; }
    public void setCurrentWorkOrderId(@Nullable UUID value) { currentWorkOrderId = value; }
    @Nullable public BlockPos getBirthHomePosition() { return birthHomePosition; }
    public void setBirthHomePosition(@Nullable BlockPos value) {
        birthHomePosition = value == null ? null : value.immutable();
        if (birthHomePosition != null && currentWorkOrderId != null && level() instanceof ServerLevel level
                && settlementId != null) {
            SettlementSavedData.get(level.getServer()).get(settlementId)
                    .ifPresent(settlement -> com.sam.realmfolk.society.task.TaskManager.releaseCurrent(settlement, this));
        }
    }

    public LifeStage getLifeStage() {
        int ordinal = this.entityData.get(DATA_LIFE_STAGE);
        LifeStage[] values = LifeStage.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : LifeStage.ADULT;
    }

    public boolean isMale() { return isMale; }

    @Override
    public boolean isBaby() { return getLifeStage() == LifeStage.BABY; }

    public boolean isWorkingAge() {
        LifeStage stage = getLifeStage();
        return stage == LifeStage.ADULT || stage == LifeStage.ELDER;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        EntityDimensions dimensions = super.getDimensions(pose);
        return isBaby() ? dimensions.scale(0.5F) : getLifeStage() == LifeStage.CHILD
                ? dimensions.scale(0.7F) : getLifeStage() == LifeStage.TEENAGER ? dimensions.scale(0.88F) : dimensions;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_LIFE_STAGE.equals(key)) refreshDimensions();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel serverLevel)) return;
        if (tickCount >= nextNeedsTick) {
            needs.update(serverLevel.getGameTime());
            nextNeedsTick = tickCount + 100 + random.nextInt(101);
        }
        if (tickCount >= nextBrainTick) {
            npcBrain.evaluate(serverLevel, this);
            nextBrainTick = tickCount + 20 + random.nextInt(21);
        }
    }

    public SimpleContainer getTradeInventory() {
        ensureTradeStock();
        return tradeInventory;
    }

    private void ensureTradeStock() {
        if (tradeStockInitialized) return;
        tradeInventory.setItem(0, new ItemStack(Items.EMERALD, 48));
        tradeInventory.setItem(1, new ItemStack(Items.BREAD, 24));
        tradeInventory.setItem(2, new ItemStack(Items.WHEAT, 32));
        tradeInventory.setItem(3, new ItemStack(Items.COOKED_BEEF, 16));
        tradeInventory.setItem(4, new ItemStack(Items.IRON_INGOT, 12));
        tradeInventory.setItem(5, new ItemStack(Items.COAL, 24));
        tradeInventory.setItem(6, new ItemStack(Items.ARROW, 32));
        tradeInventory.setItem(7, new ItemStack(Items.IRON_SWORD, 2));
        tradeInventory.setItem(8, new ItemStack(Items.LEATHER_CHESTPLATE, 2));
        tradeStockInitialized = true;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new NpcMenu(containerId, playerInventory, this);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (personId != null) tag.putUUID("PersonId", personId);
        if (settlementId != null) tag.putUUID("SettlementId", settlementId);
        tag.putString("FirstName", firstName);
        tag.putBoolean("IsMale", isMale);
        if (houseId != null) tag.putString("HouseId", houseId.toString());
        tag.put("NpcInventory", npcInventory.createTag());
        tag.put("TradeInventory", tradeInventory.createTag());
        tag.putBoolean("TradeStockInitialized", tradeStockInitialized);
        tag.putString("BehaviorMode", behaviorMode.name());
        if (followingPlayerId != null) tag.putUUID("FollowingPlayerId", followingPlayerId);
        if (stayPosition != null) tag.putLong("StayPosition", stayPosition.asLong());
        if (merchantOffers != null) tag.put("MerchantOffers", merchantOffers.createTag());
        tag.putInt("MerchantXp", merchantXp);
        tag.put("ProfessionData",professionData.save());
        tag.put("Needs", needs.save());
        if (currentWorkOrderId != null) tag.putUUID("CurrentWorkOrderId", currentWorkOrderId);
        if (birthHomePosition != null) tag.putLong("BirthHomePosition", birthHomePosition.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("PersonId")) this.personId = tag.getUUID("PersonId");
        this.settlementId = tag.hasUUID("SettlementId") ? tag.getUUID("SettlementId") : null;
        boolean forcedGender = tag.getBoolean(com.sam.realmfolk.content.item.GenderedResidentSpawnEggItem.FORCED_GENDER_TAG);
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
        if (tag.contains("TradeInventory", Tag.TAG_LIST)) {
            this.tradeInventory.fromTag(tag.getList("TradeInventory", Tag.TAG_COMPOUND));
        }
        this.tradeStockInitialized = tag.getBoolean("TradeStockInitialized");
        if (tag.contains("BehaviorMode")) {
            try {
                this.behaviorMode = NpcBehaviorMode.valueOf(tag.getString("BehaviorMode"));
            } catch (IllegalArgumentException ignored) {
                this.behaviorMode = NpcBehaviorMode.WANDER;
            }
        }
        this.followingPlayerId = tag.hasUUID("FollowingPlayerId") ? tag.getUUID("FollowingPlayerId") : null;
        this.stayPosition = tag.contains("StayPosition") ? BlockPos.of(tag.getLong("StayPosition")) : null;
        if (tag.contains("MerchantOffers", Tag.TAG_COMPOUND)) {
            this.merchantOffers = new MerchantOffers(tag.getCompound("MerchantOffers"));
        }
        this.merchantXp = tag.getInt("MerchantXp");
        if(tag.contains("ProfessionData",Tag.TAG_COMPOUND))this.professionData=ProfessionData.load(tag.getCompound("ProfessionData"),professionData.aptitude());
        if (tag.contains("Needs", Tag.TAG_COMPOUND)) this.needs = NpcNeeds.load(tag.getCompound("Needs"));
        this.currentWorkOrderId = tag.hasUUID("CurrentWorkOrderId") ? tag.getUUID("CurrentWorkOrderId") : null;
        this.birthHomePosition = tag.contains("BirthHomePosition") ? BlockPos.of(tag.getLong("BirthHomePosition")) : null;
        if (tag.contains("FirstName")) {
            this.firstName = tag.getString("FirstName");
        } else if (forcedGender && this.personId == null) {
            this.firstName = NameGenerator.generateFirstName(this.isMale);
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

    public void initializePerson(PersonData person) {
        this.personId = person.getPersonId();
        applyPersonData(person);
        this.setPersistenceRequired();
    }

    public void applyPersonData(PersonData person) {
        this.firstName = person.getFirstName();
        this.houseId = person.getHouseId();
        this.isMale = person.getGender() == Gender.MALE;
        this.entityData.set(DATA_LIFE_STAGE, person.getLifeStage().ordinal());
        this.refreshDimensions();
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

    public NpcBehaviorMode getBehaviorMode() {
        return behaviorMode;
    }

    public void follow(Player player) {
        this.behaviorMode = NpcBehaviorMode.FOLLOW;
        this.followingPlayerId = player.getUUID();
        this.stayPosition = null;
        this.navigation.stop();
        this.setPersistenceRequired();
    }

    public void stayHere() {
        this.behaviorMode = NpcBehaviorMode.STAY;
        this.followingPlayerId = null;
        this.stayPosition = this.blockPosition();
        this.navigation.stop();
        this.setPersistenceRequired();
    }

    public void wander() {
        this.behaviorMode = NpcBehaviorMode.WANDER;
        this.followingPlayerId = null;
        this.stayPosition = null;
        this.navigation.stop();
        this.setPersistenceRequired();
    }

    public int converse(ServerPlayer player) {
        if (personId == null || !(level() instanceof ServerLevel serverLevel)) return 0;
        long now = serverLevel.getGameTime();
        long previous = lastConversationTimes.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
        PersonData person = HumanSocietySavedData.get(serverLevel).getPerson(personId).orElse(null);
        if (person == null) return 0;
        if (now - previous < 1200L) return person.getAffinity(player.getUUID());
        lastConversationTimes.put(player.getUUID(), now);
        return new FamilyManager(HumanSocietySavedData.get(serverLevel))
                .changeAffinity(personId, player.getUUID(), 1);
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        this.tradingPlayer = player;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        if (merchantOffers == null) {
            merchantOffers = new MerchantOffers();
            merchantOffers.add(new MerchantOffer(
                    new ItemStack(Items.WHEAT, 10), new ItemStack(Items.EMERALD), 16, 2, 0.05F));
            merchantOffers.add(new MerchantOffer(
                    new ItemStack(Items.EMERALD), new ItemStack(Items.BREAD, 6), 16, 2, 0.05F));
            merchantOffers.add(new MerchantOffer(
                    new ItemStack(Items.EMERALD, 3), new ItemStack(Items.IRON_INGOT), 12, 4, 0.05F));
        }
        return merchantOffers;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
        this.merchantOffers = offers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        this.merchantXp += offer.getXp();
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return merchantXp;
    }

    @Override
    public void overrideXp(int xp) {
        this.merchantXp = xp;
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide;
    }

    private final class WanderWhenFreeGoal extends WaterAvoidingRandomStrollGoal {
        private WanderWhenFreeGoal() { super(ResidentEntity.this, 1.0D); }

        @Override
        public boolean canUse() {
            return birthHomePosition == null && behaviorMode == NpcBehaviorMode.WANDER && super.canUse();
        }
    }

    private final class ReturnHomeForBirthGoal extends Goal {
        private ReturnHomeForBirthGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            com.sam.realmfolk.society.ai.NpcAction action = npcBrain.currentAction();
            return birthHomePosition != null && action != com.sam.realmfolk.society.ai.NpcAction.FLEE
                    && action != com.sam.realmfolk.society.ai.NpcAction.DEFEND
                    && birthHomePosition.distSqr(blockPosition()) > 4.0D;
        }

        @Override
        public boolean canContinueToUse() { return canUse(); }

        @Override
        public void tick() {
            if (birthHomePosition == null) return;
            getNavigation().moveTo(birthHomePosition.getX() + 0.5D, birthHomePosition.getY(),
                    birthHomePosition.getZ() + 0.5D, 1.0D);
            getLookControl().setLookAt(birthHomePosition.getX() + 0.5D,
                    birthHomePosition.getY() + 0.5D, birthHomePosition.getZ() + 0.5D);
        }
    }

    private final class FollowAssignedPlayerGoal extends Goal {
        @Nullable private Player target;

        private FollowAssignedPlayerGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (behaviorMode != NpcBehaviorMode.FOLLOW || followingPlayerId == null
                    || !(level() instanceof ServerLevel serverLevel)) return false;
            target = serverLevel.getPlayerByUUID(followingPlayerId);
            return target != null && target.isAlive() && distanceToSqr(target) > 9.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return behaviorMode == NpcBehaviorMode.FOLLOW && target != null && target.isAlive()
                    && distanceToSqr(target) > 4.0D;
        }

        @Override
        public void tick() {
            if (target == null) return;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            getNavigation().moveTo(target, 1.1D);
            if (distanceToSqr(target) > 1024.0D) {
                BlockPos destination = target.blockPosition().offset(1, 0, 1);
                if (level().noCollision(ResidentEntity.this, getBoundingBox().move(
                        destination.getX() + 0.5D - getX(), destination.getY() - getY(),
                        destination.getZ() + 0.5D - getZ()))) {
                    teleportTo(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D);
                }
            }
        }

        @Override
        public void stop() { target = null; }
    }

    private final class ReturnToStayPositionGoal extends Goal {
        private ReturnToStayPositionGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            return behaviorMode == NpcBehaviorMode.STAY && stayPosition != null
                    && stayPosition.distSqr(blockPosition()) > 9.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return behaviorMode == NpcBehaviorMode.STAY && stayPosition != null
                    && stayPosition.distSqr(blockPosition()) > 4.0D;
        }

        @Override
        public void start() {
            if (stayPosition != null) getNavigation().moveTo(
                    stayPosition.getX() + 0.5D, stayPosition.getY(), stayPosition.getZ() + 0.5D, 1.0D);
        }
    }
}
