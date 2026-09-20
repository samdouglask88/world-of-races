package com.example.worldofraces.entity;

import com.example.worldofraces.family.FamilyData;
import com.example.worldofraces.family.FamilyRegistry;
import com.example.worldofraces.util.NameGenerator;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Mob;

public class RaceEntity extends PathfinderMob {

    private final FamilyData familyData = new FamilyData();
    private boolean isMale;
    private String noblesurname;

    public RaceEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);

        this.isMale = this.random.nextBoolean();
        boolean isNoble = this.random.nextInt(100) < 20;

        String firstName = NameGenerator.generateFirstName(this.isMale);
        String fullName;

        if (isNoble) {
            this.noblesurname = FamilyRegistry.getOrCreateNobleFamily().getSurname();
            fullName = firstName + " " + this.noblesurname;
        } else {
            this.noblesurname = null;
            fullName = firstName;
        }

        this.setCustomName(net.minecraft.network.chat.Component.literal(fullName));
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
    protected net.minecraft.world.InteractionResult mobInteract(
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {

        if (!this.level().isClientSide) {
            String fatherInfo = this.familyData.getFatherId() != null
                    ? this.familyData.getFatherId().toString() : "nenhum";
            String motherInfo = this.familyData.getMotherId() != null
                    ? this.familyData.getMotherId().toString() : "nenhuma";
            int childrenCount = this.familyData.getChildrenIds().size();

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[DEBUG] " + this.getName().getString() +
                    " | Pai: " + fatherInfo +
                    " | Mae: " + motherInfo +
                    " | Filhos: " + childrenCount
            ));
        } else {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                    new com.example.worldofraces.client.gui.DialogueScreen(this)
            );
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    public FamilyData getFamilyData() {
        return familyData;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (familyData.getFatherId() != null) {
            tag.putUUID("FatherId", familyData.getFatherId());
        }
        if (familyData.getMotherId() != null) {
            tag.putUUID("MotherId", familyData.getMotherId());
        }
        if (familyData.getSpouseId() != null) {
            tag.putUUID("SpouseId", familyData.getSpouseId());
        }
        ListTag childrenList = new ListTag();
        for (java.util.UUID childId : familyData.getChildrenIds()) {
            CompoundTag childTag = new CompoundTag();
            childTag.putUUID("Id", childId);
            childrenList.add(childTag);
        }
        tag.put("Children", childrenList);

        if (this.noblesurname != null) {
            tag.putString("NobleSurname", this.noblesurname);
        }
        tag.putBoolean("IsMale", this.isMale);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("FatherId")) {
            familyData.setFatherId(tag.getUUID("FatherId"));
        }
        if (tag.hasUUID("MotherId")) {
            familyData.setMotherId(tag.getUUID("MotherId"));
        }
        if (tag.hasUUID("SpouseId")) {
            familyData.setSpouseId(tag.getUUID("SpouseId"));
        }
        ListTag childrenList = tag.getList("Children", 10);
        for (int i = 0; i < childrenList.size(); i++) {
            CompoundTag childTag = childrenList.getCompound(i);
            familyData.addChild(childTag.getUUID("Id"));
        }

        if (tag.contains("NobleSurname")) {
            this.noblesurname = tag.getString("NobleSurname");
        } else {
            this.noblesurname = null;
        }
        if (tag.contains("IsMale")) {
            this.isMale = tag.getBoolean("IsMale");
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType spawnType,
            SpawnGroupData spawnData,
            CompoundTag dataTag) {

        if (this.noblesurname != null && level instanceof ServerLevel serverLevel) {
            java.util.List<RaceEntity> nearby = serverLevel.getEntitiesOfClass(
                    RaceEntity.class,
                    this.getBoundingBox().inflate(200.0D),
                    other -> other != this && this.noblesurname.equals(other.noblesurname)
            );

            if (!nearby.isEmpty()) {
                RaceEntity relative = nearby.get(0);
                if (this.isMale) {
                    if (!relative.isMale) {
                        this.familyData.setMotherId(relative.getUUID());
                        relative.familyData.addChild(this.getUUID());
                    }
                } else {
                    if (relative.isMale) {
                        this.familyData.setFatherId(relative.getUUID());
                        relative.familyData.addChild(this.getUUID());
                    }
                }
            }
        }

        return super.finalizeSpawn(level, difficulty, spawnType, spawnData, dataTag);
    }
}