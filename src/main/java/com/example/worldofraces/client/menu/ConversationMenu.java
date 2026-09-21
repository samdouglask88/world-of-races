package com.example.worldofraces.client.menu;

import com.example.worldofraces.dialogue.DialogueRegistry;
import com.example.worldofraces.entity.RaceEntity;
import com.example.worldofraces.society.FamilyManager;
import com.example.worldofraces.society.HumanSocietySavedData;
import com.example.worldofraces.society.PersonData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkHooks;

import java.util.List;

public final class ConversationMenu extends AbstractContainerMenu {
    public static final int TOPIC_BASE = 100;
    public static final int GOODBYE = 200;
    public static final int GIFT = 201;
    public static final int FAMILY = 202;
    public static final int HOUSE = 203;
    public static final int TRADE = 204;
    public static final int EQUIPMENT = 205;
    public static final int FOLLOW = 206;
    public static final int STAY = 207;
    public static final int PROFESSION = 208;
    public static final int PRAISE = 209;
    public static final int JOKE = 210;
    public static final List<String> TOPICS = List.of(
            "personal_intro", "family_intro", "work_intro", "region_intro",
            "rumors_intro", "help_intro", "romance_intro");

    private final RaceEntity npc;
    private final ConversationSnapshot snapshot;

    public ConversationMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        super(ModMenuTypes.CONVERSATION_MENU.get(), containerId);
        int entityId = buffer.readVarInt();
        if (!(inventory.player.level().getEntity(entityId) instanceof RaceEntity foundNpc)) {
            throw new IllegalStateException("NPC da conversa nao foi encontrado: " + entityId);
        }
        this.npc = foundNpc;
        this.snapshot = ConversationSnapshot.read(buffer);
    }

    private ConversationMenu(int containerId, Inventory inventory, RaceEntity npc,
                             ConversationSnapshot snapshot) {
        super(ModMenuTypes.CONVERSATION_MENU.get(), containerId);
        this.npc = npc;
        this.snapshot = snapshot;
    }

    public ConversationSnapshot getSnapshot() { return snapshot; }
    public RaceEntity getNpc() { return npc; }

    public static void open(ServerPlayer player, RaceEntity npc, String requestedNode) {
        if (!(player.level() instanceof ServerLevel level) || npc.getPersonId() == null) return;
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        PersonData person = society.getPerson(npc.getPersonId()).orElse(null);
        if (person == null) return;
        new FamilyManager(society).recordInteraction(person.getPersonId(), level.getGameTime());
        String nodeId = requestedNode.equals("greeting") ? greetingFor(level.getDayTime()) : requestedNode;
        DialogueRegistry.DialogueNode node = DialogueRegistry.INSTANCE.get(nodeId)
                .or(() -> DialogueRegistry.INSTANCE.get("greeting_day")).orElse(null);
        if (node == null) {
            player.sendSystemMessage(Component.literal("Nenhum dialogo foi carregado."));
            return;
        }
        int affinity = person.getAffinity(player.getUUID());
        ConversationSnapshot snapshot = ConversationSnapshot.create(node, person, society, affinity);
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, ignored) ->
                        new ConversationMenu(id, inventory, npc, snapshot),
                        Component.literal("Conversa com " + person.getDisplayName())),
                buffer -> {
                    buffer.writeVarInt(npc.getId());
                    snapshot.write(buffer);
                });
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer) || npc.getPersonId() == null) return false;
        if (buttonId == GOODBYE) {
            serverPlayer.closeContainer();
            return true;
        }
        if (buttonId == GIFT) {
            ItemStack held = serverPlayer.getMainHandItem();
            if (held.isEmpty()) {
                serverPlayer.sendSystemMessage(Component.literal("Segure o presente na mao principal."));
                return true;
            }
            ItemStack gift = held.copyWithCount(1);
            ItemStack remainder = npc.getNpcInventory().addItem(gift);
            if (!remainder.isEmpty()) {
                serverPlayer.sendSystemMessage(Component.literal("O inventario do habitante esta cheio."));
                return true;
            }
            held.shrink(1);
            HumanSocietySavedData society = HumanSocietySavedData.get(serverPlayer.serverLevel());
            int gain = gift.isEdible() ? 2 : gift.is(Items.EMERALD) || gift.is(Items.DIAMOND) ? 4 : 1;
            int affinity = new FamilyManager(society)
                    .changeAffinity(npc.getPersonId(), serverPlayer.getUUID(), gain);
            serverPlayer.sendSystemMessage(Component.literal(
                    npc.getName().getString() + " aceitou o presente. Afinidade: " + affinity));
            open(serverPlayer, npc, snapshot.nodeId());
            return true;
        }
        if (buttonId == FAMILY) {
            FamilyTreeMenu.open(serverPlayer, npc, npc.getPersonId(), 0);
            return true;
        }
        if (buttonId == HOUSE) {
            HumanSocietySavedData society = HumanSocietySavedData.get(serverPlayer.serverLevel());
            PersonData person = society.getPerson(npc.getPersonId()).orElse(null);
            String house = person == null || person.getHouseId() == null ? null
                    : com.example.worldofraces.society.HouseRegistry.get(person.getHouseId())
                    .map(value -> value.surname()).orElse(null);
            serverPlayer.sendSystemMessage(Component.literal(npc.getName().getString()
                    + (house == null ? " nao pertence a uma Casa nobre."
                    : " pertence a Casa " + house + ".")));
            return true;
        }
        if (buttonId == TRADE) {
            TradeMenu.open(serverPlayer, npc);
            return true;
        }
        if (buttonId == EQUIPMENT) {
            NpcMenu.open(serverPlayer, npc);
            return true;
        }
        if (buttonId == PROFESSION) {
            ProfessionMenu.open(serverPlayer, npc, npc.getProfessionData().profession() == com.example.worldofraces.profession.NpcProfession.NONE
                    ? com.example.worldofraces.profession.NpcProfession.FARMER : npc.getProfessionData().profession());
            return true;
        }
        if (buttonId == PRAISE) {
            int affinity = npc.converse(serverPlayer);
            serverPlayer.sendSystemMessage(Component.literal(npc.getName().getString()
                    + " recebeu o elogio. Afinidade: " + affinity));
            open(serverPlayer, npc, snapshot.nodeId());
            return true;
        }
        if (buttonId == JOKE) {
            HumanSocietySavedData society = HumanSocietySavedData.get(serverPlayer.serverLevel());
            int change = npc.getRandom().nextInt(4) == 0 ? -1 : 2;
            int affinity = new FamilyManager(society)
                    .changeAffinity(npc.getPersonId(), serverPlayer.getUUID(), change);
            serverPlayer.sendSystemMessage(Component.literal(change > 0
                    ? npc.getName().getString() + " riu da piada. Afinidade: " + affinity
                    : npc.getName().getString() + " não achou graça. Afinidade: " + affinity));
            open(serverPlayer, npc, snapshot.nodeId());
            return true;
        }
        if (buttonId == FOLLOW) {
            if (npc.getBehaviorMode() == com.example.worldofraces.entity.NpcBehaviorMode.FOLLOW) npc.wander();
            else npc.follow(serverPlayer);
            open(serverPlayer, npc, snapshot.nodeId());
            return true;
        }
        if (buttonId == STAY) {
            if (npc.getBehaviorMode() == com.example.worldofraces.entity.NpcBehaviorMode.STAY) npc.wander();
            else npc.stayHere();
            open(serverPlayer, npc, snapshot.nodeId());
            return true;
        }
        if (buttonId >= TOPIC_BASE && buttonId < TOPIC_BASE + TOPICS.size()) {
            open(serverPlayer, npc, TOPICS.get(buttonId - TOPIC_BASE));
            return true;
        }
        DialogueRegistry.DialogueNode node = DialogueRegistry.INSTANCE.get(snapshot.nodeId()).orElse(null);
        if (node == null || buttonId < 0 || buttonId >= node.responses().size() || buttonId >= 4) return false;
        DialogueRegistry.DialogueResponse response = node.responses().get(buttonId);
        ServerLevel level = serverPlayer.serverLevel();
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        PersonData person = society.getPerson(npc.getPersonId()).orElse(null);
        if (person == null || person.getAffinity(player.getUUID()) < response.requiresAffinity()) return false;
        if (response.affinityChange() != 0) {
            new FamilyManager(society).changeAffinity(person.getPersonId(), player.getUUID(), response.affinityChange());
        }
        if (response.endsConversation()) serverPlayer.closeContainer();
        else open(serverPlayer, npc, response.nextDialogue());
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return npc.isAlive() && npc.distanceToSqr(player) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    private static String greetingFor(long dayTime) {
        long time = dayTime % 24000L;
        if (time < 6000L) return "greeting_morning";
        if (time < 13000L) return "greeting_day";
        return "greeting_night";
    }
}
