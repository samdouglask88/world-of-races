package com.example.worldofraces.client.menu;

import com.example.worldofraces.entity.RaceEntity;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

final class NpcEquipmentSlot extends Slot {
    private static final SimpleContainer EMPTY_CONTAINER = new SimpleContainer(0);
    private final RaceEntity npc;
    private final EquipmentSlot equipmentSlot;

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

    @Override
    public void setChanged() {
        npc.setPersistenceRequired();
    }
}
