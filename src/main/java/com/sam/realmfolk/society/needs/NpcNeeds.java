package com.sam.realmfolk.society.needs;

import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;

public final class NpcNeeds {
    private final Map<NeedType, Integer> values = new EnumMap<>(NeedType.class);

    public NpcNeeds() {
        for (NeedType type : NeedType.values()) values.put(type, 0);
    }

    public int get(NeedType type) { return values.getOrDefault(type, 0); }
    public int hunger() { return get(NeedType.HUNGER); }
    public boolean needsFood() { return hunger() >= 60; }
    public boolean hungerEmergency() { return hunger() >= 85; }

    public void set(NeedType type, int value) { values.put(type, clamp(value)); }
    public void change(NeedType type, int amount) { set(type, get(type) + amount); }
    public void update(long gameTime) {
        change(NeedType.HUNGER, 1);
        int dayTime = (int) (gameTime % 24000L);
        change(NeedType.REST, dayTime >= 13000 && dayTime <= 23000 ? 2 : -1);
    }

    public NeedType mostUrgent() {
        NeedType result = NeedType.HUNGER;
        int highest = -1;
        for (NeedType type : NeedType.values()) {
            int value = get(type);
            if (value > highest) { highest = value; result = type; }
        }
        return result;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        values.forEach((type, value) -> tag.putInt(type.name(), value));
        return tag;
    }

    public static NpcNeeds load(CompoundTag tag) {
        NpcNeeds needs = new NpcNeeds();
        for (NeedType type : NeedType.values()) if (tag.contains(type.name())) needs.set(type, tag.getInt(type.name()));
        return needs;
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
}
