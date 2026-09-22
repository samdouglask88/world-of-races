package com.sam.realmfolk.client.menu;

import com.sam.realmfolk.entity.NpcBehaviorMode;
import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.profession.NpcProfession;
import com.sam.realmfolk.profession.ProfessionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

public final class ProfessionMenu extends AbstractContainerMenu {
    public static final int ASSIGN = 100, REMOVE = 101, EQUIPMENT = 102,
            TALK = 103, FAMILY = 104, HOUSE = 105, TRADE = 106, FOLLOW = 107, STAY = 108;

    private final ResidentEntity npc;
    private final Snapshot snapshot;

    public ProfessionMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        super(ModMenuTypes.PROFESSION_MENU.get(), id);
        if (!(inventory.player.level().getEntity(buffer.readVarInt()) instanceof ResidentEntity resident)) {
            throw new IllegalStateException("NPC não encontrado");
        }
        npc = resident;
        snapshot = Snapshot.read(buffer);
    }

    private ProfessionMenu(int id, Inventory inventory, ResidentEntity npc, Snapshot snapshot) {
        super(ModMenuTypes.PROFESSION_MENU.get(), id);
        this.npc = npc;
        this.snapshot = snapshot;
    }

    public Snapshot snapshot() { return snapshot; }
    public ResidentEntity npc() { return npc; }

    public static void open(ServerPlayer player, ResidentEntity npc, NpcProfession selected) {
        Snapshot snapshot = Snapshot.create(npc, selected);
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, ignored) -> new ProfessionMenu(id, inventory, npc, snapshot),
                        Component.literal("Profissões")),
                buffer -> {
                    buffer.writeVarInt(npc.getId());
                    snapshot.write(buffer);
                });
    }

    @Override
    public boolean clickMenuButton(Player player, int action) {
        if (!(player instanceof ServerPlayer server) || !stillValid(player)) return false;
        if (action >= 0 && action < NpcProfession.values().length) {
            open(server, npc, NpcProfession.values()[action]);
            return true;
        }
        if (action == ASSIGN) {
            ProfessionService.Result result = ProfessionService.assign(server, npc, snapshot.selected);
            server.sendSystemMessage(Component.literal(result.message()));
            open(server, npc, snapshot.selected);
            return true;
        }
        if (action == REMOVE) {
            ProfessionService.remove(server.serverLevel(), npc);
            open(server, npc, snapshot.selected);
            return true;
        }
        if (action == EQUIPMENT) { NpcMenu.open(server, npc); return true; }
        if (action == TALK) { ConversationMenu.open(server, npc, "greeting"); return true; }
        if (action == FAMILY && npc.getPersonId() != null) { FamilyTreeMenu.open(server, npc, npc.getPersonId(), 0); return true; }
        if (action == HOUSE) {
            var person = npc.getPerson(server.serverLevel()).orElse(null);
            String house = person == null || person.getHouseId() == null ? "não pertence a uma Casa nobre"
                    : com.sam.realmfolk.society.HouseRegistry.get(person.getHouseId())
                    .map(value -> "pertence à Casa " + value.surname()).orElse("pertence a uma Casa desconhecida");
            server.sendSystemMessage(Component.literal(npc.getName().getString() + " " + house + "."));
            return true;
        }
        if (action == TRADE) { TradeMenu.open(server, npc); return true; }
        if (action == FOLLOW) {
            if (npc.getBehaviorMode() == NpcBehaviorMode.FOLLOW) npc.wander();
            else npc.follow(server);
            open(server, npc, snapshot.selected);
            return true;
        }
        if (action == STAY) {
            if (npc.getBehaviorMode() == NpcBehaviorMode.STAY) npc.wander();
            else npc.stayHere();
            open(server, npc, snapshot.selected);
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) { return npc.isAlive() && npc.distanceToSqr(player) <= 64; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    public record Snapshot(NpcProfession selected, NpcProfession current, int level, int experience,
                           int needed, String aptitude, String station, boolean active,
                           String status, NpcBehaviorMode behavior) {
        static Snapshot create(ResidentEntity npc, NpcProfession selected) {
            var data = npc.getProfessionData();
            String station = data.workstation() == null ? "Nenhuma"
                    : data.workstation().getX() + ", " + data.workstation().getY() + ", " + data.workstation().getZ();
            return new Snapshot(selected, data.profession(), data.level(), data.experience(),
                    data.experienceNeeded(), data.aptitude().name(), station, data.active(),
                    ProfessionService.status(npc, selected), npc.getBehaviorMode());
        }

        void write(FriendlyByteBuf buffer) {
            buffer.writeEnum(selected);
            buffer.writeEnum(current);
            buffer.writeVarInt(level);
            buffer.writeVarInt(experience);
            buffer.writeVarInt(needed);
            buffer.writeUtf(aptitude);
            buffer.writeUtf(station);
            buffer.writeBoolean(active);
            buffer.writeUtf(status);
            buffer.writeEnum(behavior);
        }

        static Snapshot read(FriendlyByteBuf buffer) {
            return new Snapshot(buffer.readEnum(NpcProfession.class), buffer.readEnum(NpcProfession.class),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(),
                    buffer.readUtf(), buffer.readBoolean(), buffer.readUtf(), buffer.readEnum(NpcBehaviorMode.class));
        }
    }
}
