package com.sam.realmfolk.society.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

public record StorageReservation(UUID id, UUID orderId, Item item, int amount, long expiresAt) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("OrderId", orderId);
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        if (itemId != null) tag.putString("Item", itemId.toString());
        tag.putInt("Amount", amount);
        tag.putLong("ExpiresAt", expiresAt);
        return tag;
    }

    public static StorageReservation load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Item"));
        Item item = id == null ? Items.AIR : ForgeRegistries.ITEMS.getValue(id);
        return new StorageReservation(tag.getUUID("Id"), tag.getUUID("OrderId"), item == null ? Items.AIR : item,
                Math.max(0, tag.getInt("Amount")), tag.getLong("ExpiresAt"));
    }
}
