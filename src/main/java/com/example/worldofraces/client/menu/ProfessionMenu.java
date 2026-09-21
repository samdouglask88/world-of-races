package com.example.worldofraces.client.menu;

import com.example.worldofraces.entity.RaceEntity;
import com.example.worldofraces.profession.NpcProfession;
import com.example.worldofraces.profession.ProfessionService;
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
    public static final int ASSIGN=100,REMOVE=101,EQUIPMENT=102;
    private final RaceEntity npc;private final Snapshot snapshot;
    public ProfessionMenu(int id,Inventory inv,FriendlyByteBuf b){super(ModMenuTypes.PROFESSION_MENU.get(),id);if(!(inv.player.level().getEntity(b.readVarInt())instanceof RaceEntity race))throw new IllegalStateException("NPC nao encontrado");npc=race;snapshot=Snapshot.read(b);}
    private ProfessionMenu(int id,Inventory inv,RaceEntity npc,Snapshot s){super(ModMenuTypes.PROFESSION_MENU.get(),id);this.npc=npc;snapshot=s;}
    public Snapshot snapshot(){return snapshot;}public RaceEntity npc(){return npc;}
    public static void open(ServerPlayer player,RaceEntity npc,NpcProfession selected){Snapshot s=Snapshot.create(npc,selected);NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inv,x)->new ProfessionMenu(id,inv,npc,s),Component.literal("Profissoes")),b->{b.writeVarInt(npc.getId());s.write(b);});}
    @Override public boolean clickMenuButton(Player p,int action){if(!(p instanceof ServerPlayer player)||!stillValid(p))return false;if(action>=0&&action<NpcProfession.values().length){open(player,npc,NpcProfession.values()[action]);return true;}if(action==ASSIGN){var result=ProfessionService.assign(player,npc,snapshot.selected);player.sendSystemMessage(Component.literal(result.message()));open(player,npc,snapshot.selected);return true;}if(action==REMOVE){ProfessionService.remove(player.serverLevel(),npc);open(player,npc,snapshot.selected);return true;}if(action==EQUIPMENT){NpcMenu.open(player,npc);return true;}return false;}
    @Override public boolean stillValid(Player p){return npc.isAlive()&&npc.distanceToSqr(p)<=64;}@Override public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}
    public record Snapshot(NpcProfession selected,NpcProfession current,int level,int experience,int needed,String aptitude,String station,boolean active,String blocked){
        static Snapshot create(RaceEntity npc,NpcProfession selected){var d=npc.getProfessionData();String station=d.workstation()==null?"Nenhuma":d.workstation().getX()+", "+d.workstation().getY()+", "+d.workstation().getZ();String blocked;if(!selected.implemented)blocked="Em breve";else if(d.profession()!=selected)blocked="Pronto para atribuicao";else if(d.workstation()==null)blocked="Procurando bigorna...";else if(!ProfessionService.hasHammer(npc))blocked="Aguardando Martelo de ferreiro";else blocked=d.active()?"Trabalhando":"Aguardando horario ou materiais";return new Snapshot(selected,d.profession(),d.level(),d.experience(),d.experienceNeeded(),d.aptitude().name(),station,d.active(),blocked);}
        void write(FriendlyByteBuf b){b.writeEnum(selected);b.writeEnum(current);b.writeVarInt(level);b.writeVarInt(experience);b.writeVarInt(needed);b.writeUtf(aptitude);b.writeUtf(station);b.writeBoolean(active);b.writeUtf(blocked);}
        static Snapshot read(FriendlyByteBuf b){return new Snapshot(b.readEnum(NpcProfession.class),b.readEnum(NpcProfession.class),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(),b.readUtf(),b.readBoolean(),b.readUtf());}
    }
}
