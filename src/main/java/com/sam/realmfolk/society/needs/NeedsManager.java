package com.sam.realmfolk.society.needs;

import com.sam.realmfolk.entity.ResidentEntity;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class NeedsManager {
    private NeedsManager() {}

    public static boolean hasFood(Container inventory) { return findFoodSlot(inventory) >= 0; }

    public static boolean eatFromInventory(ResidentEntity resident) {
        Container inventory = resident.getNpcInventory();
        int slot = findFoodSlot(inventory);
        if (slot < 0) return false;
        ItemStack food = inventory.getItem(slot);
        int nutrition = food.getFoodProperties(resident) == null ? 2 : food.getFoodProperties(resident).getNutrition();
        food.shrink(1);
        if (food.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
        inventory.setChanged();
        resident.getNeeds().change(NeedType.HUNGER, -Math.max(15, nutrition * 8));
        return true;
    }

    public static int findFoodSlot(Container inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.isEdible()) return i;
        }
        return -1;
    }
}
