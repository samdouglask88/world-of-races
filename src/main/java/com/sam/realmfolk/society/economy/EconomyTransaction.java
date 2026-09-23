package com.sam.realmfolk.society.economy;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

public record EconomyTransaction(EconomyTransactionType type, int amount, long gameTime,
                                 @Nullable UUID personId, String note) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.name());
        tag.putInt("Amount", amount);
        tag.putLong("GameTime", gameTime);
        if (personId != null) tag.putUUID("PersonId", personId);
        tag.putString("Note", note == null ? "" : note);
        return tag;
    }

    public static EconomyTransaction load(CompoundTag tag) {
        EconomyTransactionType type;
        try { type = EconomyTransactionType.valueOf(tag.getString("Type")); }
        catch (IllegalArgumentException ignored) { type = EconomyTransactionType.DEPOSIT; }
        return new EconomyTransaction(type, Math.max(0, tag.getInt("Amount")), tag.getLong("GameTime"),
                tag.hasUUID("PersonId") ? tag.getUUID("PersonId") : null, tag.getString("Note"));
    }
}
