package com.sam.realmfolk.society.government;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SettlementGovernment {
    private LeaderType leaderType = LeaderType.APPOINTED;
    @Nullable private UUID playerLeaderId;
    private boolean playerControlled;
    private final Map<SettlementPolicy, Integer> priorities = new EnumMap<>(SettlementPolicy.class);

    public SettlementGovernment() {
        for (SettlementPolicy policy : SettlementPolicy.values()) priorities.put(policy, 50);
    }

    public LeaderType leaderType() { return leaderType; }
    public Optional<UUID> playerLeaderId() { return Optional.ofNullable(playerLeaderId); }
    public boolean playerControlled() { return playerControlled; }
    public int priority(SettlementPolicy policy) { return priorities.getOrDefault(policy, 50); }

    public void setLeaderType(LeaderType value) { leaderType = value; }
    public void setPlayerLeader(@Nullable UUID id, boolean controlled) {
        playerLeaderId = id;
        playerControlled = controlled && id != null;
    }
    public void setPriority(SettlementPolicy policy, int value) {
        priorities.put(policy, Math.max(0, Math.min(100, value)));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("LeaderType", leaderType.name());
        if (playerLeaderId != null) tag.putUUID("PlayerLeaderId", playerLeaderId);
        tag.putBoolean("PlayerControlled", playerControlled);
        CompoundTag prioritiesTag = new CompoundTag();
        priorities.forEach((policy, value) -> prioritiesTag.putInt(policy.name(), value));
        tag.put("Priorities", prioritiesTag);
        return tag;
    }

    public static SettlementGovernment load(CompoundTag tag) {
        SettlementGovernment government = new SettlementGovernment();
        try { government.leaderType = LeaderType.valueOf(tag.getString("LeaderType")); }
        catch (IllegalArgumentException ignored) { government.leaderType = LeaderType.APPOINTED; }
        if (tag.hasUUID("PlayerLeaderId")) government.playerLeaderId = tag.getUUID("PlayerLeaderId");
        government.playerControlled = tag.getBoolean("PlayerControlled") && government.playerLeaderId != null;
        CompoundTag prioritiesTag = tag.getCompound("Priorities");
        for (SettlementPolicy policy : SettlementPolicy.values()) {
            if (prioritiesTag.contains(policy.name())) government.setPriority(policy, prioritiesTag.getInt(policy.name()));
        }
        return government;
    }
}
