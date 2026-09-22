package com.sam.realmfolk.client.menu;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.HouseRegistry;
import com.sam.realmfolk.society.PersonData;
import com.sam.realmfolk.society.PersonStatus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

public final class FamilyTreeMenu extends AbstractContainerMenu {
    public static final int PREVIOUS_PAGE = 100;
    public static final int NEXT_PAGE = 101;
    public static final int EQUIPMENT_TAB = 200;
    public static final int TALK_ACTION = 201;
    public static final int HOUSE_ACTION = 202;
    public static final int TRADE_ACTION = 203;
    public static final int PROFILE_ACTION = 204;
    public static final int PROFESSION_ACTION = 205;
    public static final int FOLLOW_ACTION = 206;
    public static final int STAY_ACTION = 207;

    private final ResidentEntity npc;
    private final FamilyTreeSnapshot snapshot;

    public FamilyTreeMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        super(ModMenuTypes.FAMILY_TREE_MENU.get(), containerId);
        int entityId = buffer.readVarInt();
        if (!(inventory.player.level().getEntity(entityId) instanceof ResidentEntity foundNpc)) {
            throw new IllegalStateException("NPC da arvore nao foi encontrado: " + entityId);
        }
        this.npc = foundNpc;
        this.snapshot = FamilyTreeSnapshot.read(buffer);
    }

    private FamilyTreeMenu(int containerId, Inventory inventory, ResidentEntity npc,
                           UUID focusId, int childPage) {
        super(ModMenuTypes.FAMILY_TREE_MENU.get(), containerId);
        this.npc = npc;
        this.snapshot = FamilyTreeSnapshot.create(
                HumanSocietySavedData.get((ServerLevel) inventory.player.level()), focusId, childPage);
    }

    public FamilyTreeSnapshot getSnapshot() {
        return snapshot;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        if (buttonId == PREVIOUS_PAGE && snapshot.childPage() > 0) {
            open(serverPlayer, npc, snapshot.focus().id(), snapshot.childPage() - 1);
            return true;
        }
        if (buttonId == NEXT_PAGE && snapshot.childPage() + 1 < snapshot.childPageCount()) {
            open(serverPlayer, npc, snapshot.focus().id(), snapshot.childPage() + 1);
            return true;
        }
        if (buttonId == EQUIPMENT_TAB) {
            NetworkHooks.openScreen(serverPlayer, npc, buffer -> buffer.writeVarInt(npc.getId()));
            return true;
        }
        if (buttonId == TALK_ACTION) {
            ConversationMenu.open(serverPlayer, npc, "greeting");
            return true;
        }
        if (buttonId == TRADE_ACTION) {
            TradeMenu.open(serverPlayer, npc);
            return true;
        }
        if (buttonId == HOUSE_ACTION) {
            showHouse(serverPlayer);
            return true;
        }
        if (buttonId == PROFILE_ACTION) {
            ProfileMenu.open(serverPlayer, npc, snapshot.focus().id());
            return true;
        }
        if (buttonId == PROFESSION_ACTION) {
            ProfessionMenu.open(serverPlayer, npc,
                    npc.getProfessionData().profession() == com.sam.realmfolk.profession.NpcProfession.NONE
                            ? com.sam.realmfolk.profession.NpcProfession.FARMER
                            : npc.getProfessionData().profession());
            return true;
        }
        if (buttonId == FOLLOW_ACTION) {
            if (npc.getBehaviorMode() == com.sam.realmfolk.entity.NpcBehaviorMode.FOLLOW) npc.wander();
            else npc.follow(serverPlayer);
            open(serverPlayer, npc, snapshot.focus().id(), snapshot.childPage());
            return true;
        }
        if (buttonId == STAY_ACTION) {
            if (npc.getBehaviorMode() == com.sam.realmfolk.entity.NpcBehaviorMode.STAY) npc.wander();
            else npc.stayHere();
            open(serverPlayer, npc, snapshot.focus().id(), snapshot.childPage());
            return true;
        }
        FamilyTreeSnapshot.Node selected = snapshot.selectable(buttonId);
        if (selected == null) return false;
        open(serverPlayer, npc, selected.id(), 0);
        return true;
    }

    private void showHouse(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        PersonData person = society.getPerson(snapshot.focus().id()).orElse(null);
        if (person == null || person.getHouseId() == null) {
            player.sendSystemMessage(Component.literal(snapshot.focus().name() + " nao pertence a uma Casa nobre."));
            return;
        }
        long members = society.getPeople().stream()
                .filter(value -> person.getHouseId().equals(value.getHouseId())).count();
        long living = society.getPeople().stream()
                .filter(value -> person.getHouseId().equals(value.getHouseId()))
                .filter(value -> value.getStatus() == PersonStatus.ALIVE).count();
        String house = HouseRegistry.get(person.getHouseId()).map(value -> "Casa " + value.surname())
                .orElse("Casa desconhecida");
        player.sendSystemMessage(Component.literal(house + " | Membros: " + members
                + " | Vivos: " + living + " | Falecidos: " + (members - living)));
    }

    private static String translateStage(String stage) {
        return switch (stage) {
            case "BABY" -> "Bebe";
            case "CHILD" -> "Crianca";
            case "TEENAGER" -> "Adolescente";
            case "ELDER" -> "Idoso";
            default -> "Adulto";
        };
    }

    public static void open(ServerPlayer player, ResidentEntity npc, UUID focusId, int childPage) {
        if (!(player.level() instanceof ServerLevel level)) return;
        FamilyTreeSnapshot snapshot = FamilyTreeSnapshot.create(
                HumanSocietySavedData.get(level), focusId, childPage);
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((containerId, inventory, ignored) ->
                        new FamilyTreeMenu(containerId, inventory, npc, focusId, childPage),
                        Component.literal("Arvore genealogica")),
                buffer -> {
                    buffer.writeVarInt(npc.getId());
                    snapshot.write(buffer);
                });
    }

    @Override
    public boolean stillValid(Player player) {
        return npc.isAlive() && npc.distanceToSqr(player) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
