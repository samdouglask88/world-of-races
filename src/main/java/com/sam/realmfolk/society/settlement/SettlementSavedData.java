package com.sam.realmfolk.society.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SettlementSavedData extends SavedData {
    private static final String DATA_NAME = "realmfolk_settlements";
    private static final int DATA_VERSION = 1;
    private final Map<UUID, Settlement> settlements = new LinkedHashMap<>();

    public static SettlementSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                SettlementSavedData::load, SettlementSavedData::new, DATA_NAME);
    }

    public Optional<Settlement> get(UUID id) { return Optional.ofNullable(settlements.get(id)); }
    public Collection<Settlement> all() { return Collections.unmodifiableCollection(settlements.values()); }

    public Optional<Settlement> at(ResourceKey<Level> dimension, BlockPos position) {
        return settlements.values().stream().filter(settlement -> settlement.dimension().equals(dimension))
                .filter(settlement -> settlement.contains(position)).findFirst();
    }

    public boolean add(Settlement settlement) {
        if (settlements.containsKey(settlement.id())) return false;
        settlements.put(settlement.id(), settlement);
        setDirty();
        return true;
    }

    public Settlement remove(UUID id) {
        Settlement removed = settlements.remove(id);
        if (removed != null) setDirty();
        return removed;
    }

    public void changed() { setDirty(); }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("DataVersion", DATA_VERSION);
        ListTag list = new ListTag();
        settlements.values().forEach(settlement -> list.add(settlement.save()));
        tag.put("Settlements", list);
        return tag;
    }

    public static SettlementSavedData load(CompoundTag tag) {
        SettlementSavedData data = new SettlementSavedData();
        ListTag list = tag.getList("Settlements", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Settlement settlement = Settlement.load(list.getCompound(i));
            data.settlements.put(settlement.id(), settlement);
        }
        return data;
    }
}
