package com.example.worldofraces.client.menu;

import com.example.worldofraces.entity.RaceEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class NpcMenu extends AbstractContainerMenu {
    private static final int NPC_EQUIPMENT_SLOTS = 6;
    private static final int NPC_STORAGE_SLOTS = 18;
    private static final int NPC_SLOT_COUNT = NPC_EQUIPMENT_SLOTS + NPC_STORAGE_SLOTS;
    private final RaceEntity npc;
    private final Container npcInventory;

    public NpcMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, getNpc(playerInventory, buffer.readVarInt()));
    }

    public NpcMenu(int containerId, Inventory playerInventory, RaceEntity npc) {
        super(ModMenuTypes.NPC_MENU.get(), containerId);
        this.npc = npc;
        this.npcInventory = npc.getNpcInventory();
        this.npcInventory.startOpen(playerInventory.player);

        addSlot(new NpcEquipmentSlot(npc, EquipmentSlot.HEAD, 116, 36));
        addSlot(new NpcEquipmentSlot(npc, EquipmentSlot.CHEST, 116, 56));
        addSlot(new NpcEquipmentSlot(npc, EquipmentSlot.LEGS, 116, 76));
        addSlot(new NpcEquipmentSlot(npc, EquipmentSlot.FEET, 116, 96));
        addSlot(new NpcEquipmentSlot(npc, EquipmentSlot.MAINHAND, 138, 96));
        addSlot(new NpcEquipmentSlot(npc, EquipmentSlot.OFFHAND, 94, 96));

        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(npcInventory, column + row * 9, 164 + column * 18, 56 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        164 + column * 18, 126 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 164 + column * 18, 184));
        }
    }

    public RaceEntity getNpc() {
        return npc;
    }

    @Override
    public boolean stillValid(Player player) {
        return npc.isAlive() && npc.distanceToSqr(player) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < NPC_SLOT_COUNT) {
            if (!moveItemStackTo(stack, NPC_SLOT_COUNT, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, NPC_EQUIPMENT_SLOTS, NPC_SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        npcInventory.stopOpen(player);
    }

    private static RaceEntity getNpc(Inventory inventory, int entityId) {
        if (inventory.player.level().getEntity(entityId) instanceof RaceEntity npc) return npc;
        throw new IllegalStateException("NPC do menu nao foi encontrado: " + entityId);
    }
}
