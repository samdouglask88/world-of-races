package com.sam.realmfolk.client.menu;

import com.sam.realmfolk.entity.NpcBehaviorMode;
import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.society.HumanSocietySavedData;
import com.sam.realmfolk.society.PersonData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

public final class ProfileMenu extends AbstractContainerMenu {
    public static final int BACK = 0, TALK = 1, FOLLOW = 2, LOCATE = 3;
    private final ResidentEntity sourceNpc;
    private final ProfileSnapshot snapshot;

    public ProfileMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        super(ModMenuTypes.PROFILE_MENU.get(), id);
        Entity entity = inventory.player.level().getEntity(buffer.readVarInt());
        if (!(entity instanceof ResidentEntity npc)) throw new IllegalStateException("NPC do perfil nao encontrado");
        sourceNpc = npc;
        snapshot = ProfileSnapshot.read(buffer);
    }

    private ProfileMenu(int id, Inventory inventory, ResidentEntity npc, ProfileSnapshot snapshot) {
        super(ModMenuTypes.PROFILE_MENU.get(), id); sourceNpc = npc; this.snapshot = snapshot;
    }
    public ProfileSnapshot getSnapshot() { return snapshot; }
    public ResidentEntity getSourceNpc() { return sourceNpc; }

    public static void open(ServerPlayer player, ResidentEntity sourceNpc, UUID personId) {
        ServerLevel level = player.serverLevel();
        HumanSocietySavedData society = HumanSocietySavedData.get(level);
        PersonData person = society.getPerson(personId).orElse(null);
        if (person == null) return;
        Entity loaded = person.getEntityId() == null ? null : level.getEntity(person.getEntityId());
        boolean recruited = loaded instanceof ResidentEntity resident && resident.getBehaviorMode() == NpcBehaviorMode.FOLLOW;
        String profession = loaded instanceof ResidentEntity resident ? resident.getProfessionData().profession().name : "Desconhecida";
        ProfileSnapshot data = ProfileSnapshot.create(person, society, player.getUUID(), loaded != null,
                recruited, level.getGameTime(), profession);
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, ignored) ->
                new ProfileMenu(id, inv, sourceNpc, data), Component.literal("Perfil de " + person.getDisplayName())), b -> {
            b.writeVarInt(sourceNpc.getId()); data.write(b);
        });
    }

    @Override public boolean clickMenuButton(Player player, int action) {
        if (!(player instanceof ServerPlayer server)) return false;
        HumanSocietySavedData society = HumanSocietySavedData.get(server.serverLevel());
        PersonData person = society.getPerson(snapshot.personId()).orElse(null);
        Entity entity = person == null || person.getEntityId() == null ? null : server.serverLevel().getEntity(person.getEntityId());
        ResidentEntity target = entity instanceof ResidentEntity resident ? resident : null;
        if (action == BACK) { FamilyTreeMenu.open(server, sourceNpc, snapshot.personId(), 0); return true; }
        if (action == TALK && target != null) { ConversationMenu.open(server, target, "greeting"); return true; }
        if (action == FOLLOW && target != null) {
            if (target.getBehaviorMode() == NpcBehaviorMode.FOLLOW) target.wander(); else target.follow(server);
            open(server, sourceNpc, snapshot.personId()); return true;
        }
        if (action == LOCATE) {
            boolean recruited = target != null && target.getBehaviorMode() == NpcBehaviorMode.FOLLOW;
            int affinity = person == null ? 0 : person.getAffinity(server.getUUID());
            if (target == null || (affinity < 60 && !recruited)) {
                server.sendSystemMessage(Component.literal("Voce ainda nao pode localizar este habitante.")); return true;
            }
            server.sendSystemMessage(Component.literal(snapshot.name() + " esta em X: " + target.blockPosition().getX()
                    + " Y: " + target.blockPosition().getY() + " Z: " + target.blockPosition().getZ()));
            return true;
        }
        return false;
    }
    @Override public boolean stillValid(Player player) { return sourceNpc.isAlive() && sourceNpc.distanceToSqr(player) <= 64; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
