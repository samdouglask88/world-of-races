package com.sam.realmfolk.client.menu;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.entity.NpcBehaviorMode;
import com.sam.realmfolk.society.HouseRegistry;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.PersonStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

public final class NpcMenu extends AbstractContainerMenu {
    public static final int FAMILY_TAB = 0;
    public static final int TALK_ACTION = 1;
    public static final int HOUSE_TAB = 2;
    public static final int TRADE_TAB = 3;
    public static final int FOLLOW_ACTION = 4;
    public static final int STAY_ACTION = 5;
    public static final int PROFESSIONS_TAB = 6;
    private static final int NPC_EQUIPMENT_SLOTS = 6;
    private static final int NPC_STORAGE_SLOTS = 18;
    private static final int NPC_SLOT_COUNT = NPC_EQUIPMENT_SLOTS + NPC_STORAGE_SLOTS;
    private final ResidentEntity npc;
    private final Container npcInventory;
    private final Player viewer;
    private int syncedAffinity;
    private int syncedBehaviorMode;

    public NpcMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, getNpc(playerInventory, buffer.readVarInt()));
    }

    public NpcMenu(int containerId, Inventory playerInventory, ResidentEntity npc) {
        super(ModMenuTypes.NPC_MENU.get(), containerId);
        this.npc = npc;
        this.viewer = playerInventory.player;
        this.npcInventory = npc.getNpcInventory();
        this.npcInventory.startOpen(playerInventory.player);

        addSlot(new NpcEquipmentSlot(npc, EquipmentSlot.HEAD, 202, 128));
        addSlot(new NpcEquipmentSlot(npc, EquipmentSlot.CHEST, 202, 169));
        addSlot(new NpcEquipmentSlot(npc, EquipmentSlot.LEGS, 202, 210));
        addSlot(new NpcEquipmentSlot(npc, EquipmentSlot.FEET, 202, 251));
        addSlot(new NpcEquipmentSlot(npc, EquipmentSlot.MAINHAND, 346, 169));
        addSlot(new NpcEquipmentSlot(npc, EquipmentSlot.OFFHAND, 346, 210));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 6; column++) {
                addSlot(new Slot(npcInventory, column + row * 6, 410 + column * 18, 119 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        390 + column * 18, 224 + row * 17));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 390 + column * 18, 281));
        }

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (!(viewer.level() instanceof ServerLevel level) || npc.getPersonId() == null) {
                    return syncedAffinity;
                }
                return HumanSocietySavedData.get(level).getPerson(npc.getPersonId())
                        .map(person -> person.getAffinity(viewer.getUUID())).orElse(0);
            }

            @Override
            public void set(int value) { syncedAffinity = value; }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() { return viewer.level().isClientSide ? syncedBehaviorMode : npc.getBehaviorMode().ordinal(); }

            @Override
            public void set(int value) { syncedBehaviorMode = value; }
        });
    }

    public ResidentEntity getNpc() {
        return npc;
    }

    public static void open(ServerPlayer player, ResidentEntity npc) {
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, ignored) -> new NpcMenu(id, inventory, npc),
                        Component.literal(npc.getName().getString())),
                buffer -> buffer.writeVarInt(npc.getId()));
    }

    public int getAffinity() { return syncedAffinity; }

    public NpcBehaviorMode getBehaviorMode() {
        NpcBehaviorMode[] values = NpcBehaviorMode.values();
        return values[Math.max(0, Math.min(syncedBehaviorMode, values.length - 1))];
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        if (buttonId == FAMILY_TAB && npc.getPersonId() != null) {
            FamilyTreeMenu.open(serverPlayer, npc, npc.getPersonId(), 0);
            return true;
        }
        if (buttonId == TALK_ACTION) {
            ConversationMenu.open(serverPlayer, npc, "greeting");
            return true;
        }
        if (buttonId == FOLLOW_ACTION) {
            if (npc.getBehaviorMode() == NpcBehaviorMode.FOLLOW) {
                npc.wander();
                serverPlayer.sendSystemMessage(Component.literal(npc.getName().getString() + " parou de seguir voce."));
            } else {
                npc.follow(serverPlayer);
                serverPlayer.sendSystemMessage(Component.literal(npc.getName().getString() + " agora esta seguindo voce."));
            }
            return true;
        }
        if (buttonId == STAY_ACTION) {
            if (npc.getBehaviorMode() == NpcBehaviorMode.STAY) {
                npc.wander();
                serverPlayer.sendSystemMessage(Component.literal(npc.getName().getString() + " pode andar novamente."));
            } else {
                npc.stayHere();
                serverPlayer.sendSystemMessage(Component.literal(npc.getName().getString() + " ficara neste local."));
            }
            return true;
        }
        if (buttonId == HOUSE_TAB) {
            showHouseInformation(serverPlayer);
            return true;
        }
        if (buttonId == TRADE_TAB) {
            TradeMenu.open(serverPlayer, npc);
            return true;
        }
        if(buttonId==PROFESSIONS_TAB){ProfessionMenu.open(serverPlayer,npc,com.sam.realmfolk.profession.NpcProfession.BLACKSMITH);return true;}
        return false;
    }

    private void showHouseInformation(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || npc.getPersonId() == null) return;
        PersonData person = HumanSocietySavedData.get(level).getPerson(npc.getPersonId()).orElse(null);
        if (person == null || person.getHouseId() == null) {
            player.sendSystemMessage(Component.literal(npc.getName().getString() + " nao pertence a uma Casa nobre."));
            return;
        }
        String house = HouseRegistry.get(person.getHouseId()).map(value -> "Casa " + value.surname())
                .orElse("Casa desconhecida");
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        long members = society.getPeople().stream()
                .filter(value -> person.getHouseId().equals(value.getHouseId())).count();
        long living = society.getPeople().stream()
                .filter(value -> person.getHouseId().equals(value.getHouseId()))
                .filter(value -> value.getStatus() == PersonStatus.ALIVE).count();
        player.sendSystemMessage(Component.literal(person.getDisplayName() + " pertence a " + house
                + ". Membros: " + members + " | Vivos: " + living
                + " | Falecidos: " + (members - living)));
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

    private static ResidentEntity getNpc(Inventory inventory, int entityId) {
        if (inventory.player.level().getEntity(entityId) instanceof ResidentEntity npc) return npc;
        throw new IllegalStateException("NPC do menu nao foi encontrado: " + entityId);
    }
}
