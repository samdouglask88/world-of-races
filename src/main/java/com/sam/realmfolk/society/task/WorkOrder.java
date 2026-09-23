package com.sam.realmfolk.society.task;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.UUID;

public final class WorkOrder {
    public static final long DEFAULT_RESERVATION_TICKS = 1200L;

    private final UUID id;
    private final WorkOrderType type;
    private final int priority;
    private WorkOrderStatus status;
    private final UUID settlementId;
    @Nullable private UUID assignedNpcId;
    @Nullable private BlockPos targetPosition;
    private final ResourceKey<Level> dimension;
    private final Item requirement;
    private final int requiredAmount;
    private final Item result;
    private final int resultAmount;
    private final long createdGameTime;
    private final long expiresAt;
    private long reservationExpiresAt;
    private String blockedReason = "";

    public WorkOrder(UUID id, WorkOrderType type, int priority, UUID settlementId,
                     @Nullable BlockPos targetPosition, ResourceKey<Level> dimension,
                     Item requirement, int requiredAmount, Item result, int resultAmount,
                     long createdGameTime, long expiresAt) {
        this.id = id;
        this.type = type;
        this.priority = Math.max(0, Math.min(100, priority));
        this.status = WorkOrderStatus.AVAILABLE;
        this.settlementId = settlementId;
        this.targetPosition = targetPosition == null ? null : targetPosition.immutable();
        this.dimension = dimension;
        this.requirement = requirement;
        this.requiredAmount = Math.max(0, requiredAmount);
        this.result = result;
        this.resultAmount = Math.max(0, resultAmount);
        this.createdGameTime = createdGameTime;
        this.expiresAt = expiresAt;
    }

    public UUID id() { return id; }
    public WorkOrderType type() { return type; }
    public int priority() { return priority; }
    public WorkOrderStatus status() { return status; }
    public UUID settlementId() { return settlementId; }
    @Nullable public UUID assignedNpcId() { return assignedNpcId; }
    @Nullable public BlockPos targetPosition() { return targetPosition; }
    public ResourceKey<Level> dimension() { return dimension; }
    public Item requirement() { return requirement; }
    public int requiredAmount() { return requiredAmount; }
    public Item result() { return result; }
    public int resultAmount() { return resultAmount; }
    public long createdGameTime() { return createdGameTime; }
    public long expiresAt() { return expiresAt; }
    public long reservationExpiresAt() { return reservationExpiresAt; }
    public String blockedReason() { return blockedReason; }

    public boolean reserve(UUID npcId, long now) {
        if (status != WorkOrderStatus.AVAILABLE || assignedNpcId != null) return false;
        assignedNpcId = npcId;
        reservationExpiresAt = now + DEFAULT_RESERVATION_TICKS;
        status = WorkOrderStatus.RESERVED;
        return true;
    }

    public boolean begin(UUID npcId, long now) {
        if (!npcId.equals(assignedNpcId) || (status != WorkOrderStatus.RESERVED && status != WorkOrderStatus.IN_PROGRESS)) return false;
        status = WorkOrderStatus.IN_PROGRESS;
        reservationExpiresAt = now + DEFAULT_RESERVATION_TICKS;
        return true;
    }

    public void heartbeat(UUID npcId, long now) {
        if (npcId.equals(assignedNpcId) && (status == WorkOrderStatus.RESERVED || status == WorkOrderStatus.IN_PROGRESS)) {
            reservationExpiresAt = now + DEFAULT_RESERVATION_TICKS;
        }
    }

    public void release() {
        assignedNpcId = null;
        reservationExpiresAt = 0L;
        if (status != WorkOrderStatus.COMPLETED && status != WorkOrderStatus.CANCELLED) status = WorkOrderStatus.AVAILABLE;
    }

    public void complete() { status = WorkOrderStatus.COMPLETED; reservationExpiresAt = 0L; }
    public void cancel() { status = WorkOrderStatus.CANCELLED; assignedNpcId = null; reservationExpiresAt = 0L; }
    public void block(String reason) { status = WorkOrderStatus.BLOCKED; blockedReason = reason == null ? "" : reason; assignedNpcId = null; }

    public boolean isExpired(long now) { return expiresAt > 0L && now >= expiresAt; }
    public boolean reservationExpired(long now) { return reservationExpiresAt > 0L && now >= reservationExpiresAt; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Type", type.name());
        tag.putInt("Priority", priority);
        tag.putString("Status", status.name());
        tag.putUUID("SettlementId", settlementId);
        if (assignedNpcId != null) tag.putUUID("AssignedNpcId", assignedNpcId);
        if (targetPosition != null) tag.putLong("TargetPosition", targetPosition.asLong());
        tag.putString("Dimension", dimension.location().toString());
        ResourceLocation requirementId = ForgeRegistries.ITEMS.getKey(requirement);
        ResourceLocation resultId = ForgeRegistries.ITEMS.getKey(result);
        if (requirementId != null) tag.putString("Requirement", requirementId.toString());
        if (resultId != null) tag.putString("Result", resultId.toString());
        tag.putInt("RequiredAmount", requiredAmount);
        tag.putInt("ResultAmount", resultAmount);
        tag.putLong("CreatedGameTime", createdGameTime);
        tag.putLong("ExpiresAt", expiresAt);
        tag.putLong("ReservationExpiresAt", reservationExpiresAt);
        tag.putString("BlockedReason", blockedReason);
        return tag;
    }

    public static WorkOrder load(CompoundTag tag) {
        WorkOrder order = new WorkOrder(
                tag.getUUID("Id"), enumValue(WorkOrderType.class, tag.getString("Type"), WorkOrderType.FETCH_RESOURCE),
                tag.getInt("Priority"), tag.getUUID("SettlementId"),
                tag.contains("TargetPosition") ? BlockPos.of(tag.getLong("TargetPosition")) : null,
                ResourceKey.create(Registries.DIMENSION, location(tag.getString("Dimension"), Level.OVERWORLD.location())),
                item(tag.getString("Requirement")), tag.getInt("RequiredAmount"),
                item(tag.getString("Result")), tag.getInt("ResultAmount"),
                tag.getLong("CreatedGameTime"), tag.getLong("ExpiresAt"));
        order.status = enumValue(WorkOrderStatus.class, tag.getString("Status"), WorkOrderStatus.AVAILABLE);
        if (tag.hasUUID("AssignedNpcId")) order.assignedNpcId = tag.getUUID("AssignedNpcId");
        order.reservationExpiresAt = tag.getLong("ReservationExpiresAt");
        order.blockedReason = tag.getString("BlockedReason");
        return order;
    }

    private static Item item(String id) {
        if (id == null || id.isBlank()) return Items.AIR;
        Item value = ForgeRegistries.ITEMS.getValue(location(id,
                ResourceLocation.fromNamespaceAndPath("minecraft", "air")));
        return value == null ? Items.AIR : value;
    }

    private static ResourceLocation location(String value, ResourceLocation fallback) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed == null ? fallback : parsed;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }
}
