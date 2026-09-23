package com.sam.realmfolk.society.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class ConstructionProject {
    private final UUID id;
    private final ResourceLocation blueprintId;
    private final BlockPos origin;
    private ConstructionStage stage;
    private int blockIndex;
    private String blockedReason = "";

    public ConstructionProject(UUID id, ResourceLocation blueprintId, BlockPos origin) {
        this.id = id;
        this.blueprintId = blueprintId;
        this.origin = origin.immutable();
        this.stage = ConstructionStage.PLANNED;
    }

    public UUID id() { return id; }
    public ResourceLocation blueprintId() { return blueprintId; }
    public BlockPos origin() { return origin; }
    public ConstructionStage stage() { return stage; }
    public int blockIndex() { return blockIndex; }
    public String blockedReason() { return blockedReason; }
    public void setStage(ConstructionStage value) { stage = value; }
    public void setBlockIndex(int value) { blockIndex = Math.max(0, value); }
    public void block(String reason) { stage = ConstructionStage.BLOCKED; blockedReason = reason == null ? "" : reason; }
    public void cancel() { stage = ConstructionStage.CANCELLED; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("BlueprintId", blueprintId.toString());
        tag.putLong("Origin", origin.asLong());
        tag.putString("Stage", stage.name());
        tag.putInt("BlockIndex", blockIndex);
        tag.putString("BlockedReason", blockedReason);
        return tag;
    }

    public static ConstructionProject load(CompoundTag tag) {
        ResourceLocation blueprint = ResourceLocation.tryParse(tag.getString("BlueprintId"));
        if (blueprint == null) blueprint = ResourceLocation.fromNamespaceAndPath("realmfolk", "small_storage_hut");
        ConstructionProject project = new ConstructionProject(tag.getUUID("Id"), blueprint, BlockPos.of(tag.getLong("Origin")));
        try { project.stage = ConstructionStage.valueOf(tag.getString("Stage")); }
        catch (IllegalArgumentException ignored) { project.stage = ConstructionStage.BLOCKED; }
        project.blockIndex = Math.max(0, tag.getInt("BlockIndex"));
        project.blockedReason = tag.getString("BlockedReason");
        return project;
    }
}
