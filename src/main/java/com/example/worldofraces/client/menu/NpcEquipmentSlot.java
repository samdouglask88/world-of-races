package com.example.worldofraces.client.menu;

import com.mojang.datafixers.util.Pair;
import com.example.worldofraces.entity.RaceEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

final class NpcEquipmentSlot extends Slot {
    private static final SimpleContainer EMPTY_CONTAINER = new SimpleContainer(0);
    private final RaceEntity npc;
    private final EquipmentSlot equipmentSlot;

    private static final ResourceLocation EMPTY_HELMET = new ResourceLocation("item/empty_armor_slot_helmet");
    private static final ResourceLocation EMPTY_CHESTPLATE = new ResourceLocation("item/empty_armor_slot_chestplate");
    private static final ResourceLocation EMPTY_LEGGINGS = new ResourceLocation("item/empty_armor_slot_leggings");
    private static final ResourceLocation EMPTY_BOOTS = new ResourceLocation("item/empty_armor_slot_boots");
    private static final ResourceLocation EMPTY_SHIELD = new ResourceLocation("item/empty_armor_slot_shield");

    NpcEquipmentSlot(RaceEntity npc, EquipmentSlot equipmentSlot, int x, int y) {
        super(EMPTY_CONTAINER, 0, x, y);
        this.npc = npc;
        this.equipmentSlot = equipmentSlot;
    }

    @Override
    public ItemStack getItem() {
        return npc.getItemBySlot(equipmentSlot);
    }

    @Override
    public boolean hasItem() {
        return !getItem().isEmpty();
    }

    @Override
    public void set(ItemStack stack) {
        npc.setItemSlot(equipmentSlot, stack);
        setChanged();
    }

    @Override
    public ItemStack remove(int amount) {
        ItemStack current = getItem();
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = current.split(amount);
        if (current.isEmpty()) npc.setItemSlot(equipmentSlot, ItemStack.EMPTY);
        setChanged();
        return removed;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return equipmentSlot.getType() == EquipmentSlot.Type.HAND
                || RaceEntity.getEquipmentSlotForItem(stack) == equipmentSlot;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Nullable
    @Override
    public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        ResourceLocation icon = switch (equipmentSlot) {
            case HEAD -> EMPTY_HELMET;
            case CHEST -> EMPTY_CHESTPLATE;
            case LEGS -> EMPTY_LEGGINGS;
            case FEET -> EMPTY_BOOTS;
            case OFFHAND -> EMPTY_SHIELD;
            default -> null;
        };
        return icon == null ? null : Pair.of(InventoryMenu.BLOCK_ATLAS, icon);
    }

    @Override
    public void setChanged() {
        npc.setPersistenceRequired();
    }
}
