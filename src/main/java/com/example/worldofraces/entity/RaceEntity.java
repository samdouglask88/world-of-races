package com.example.worldofraces.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.client.Minecraft;
import com.example.worldofraces.client.gui.DialogueScreen;
import com.example.worldofraces.util.NameGenerator;
import com.example.worldofraces.family.FamilyData;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public class RaceEntity extends PathfinderMob {

    private final FamilyData familyData = new FamilyData();

    public RaceEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        String randomName = NameGenerator.generateRandomFullName();
        this.setCustomName(Component.literal(randomName));
        this.setCustomNameVisible(true);
    }

    public com.example.worldofraces.family.FamilyData getFamilyData() {
        return familyData;
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
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide) {
            Minecraft.getInstance().setScreen(new DialogueScreen(this));
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }
}