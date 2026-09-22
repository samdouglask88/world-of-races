package com.sam.realmfolk.client.menu;

import com.sam.realmfolk.entity.ResidentEntity;
import com.sam.realmfolk.trade.TradePriceRegistry;
import com.sam.realmfolk.trade.TradeService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

public final class TradeMenu extends AbstractContainerMenu {
    public static final int SELECT_STOCK = 0, SELECT_PLAYER = 100, BUY = 200, SELL = 201,
            MINUS = 202, PLUS = 203, CONFIRM = 204;
    public static final int TALK=300,FAMILY=301,HOUSE=302,PROFESSION=303,EQUIPMENT=304,
            FOLLOW=305,STAY=306;
    private final ResidentEntity npc;
    private final Inventory playerInventory;
    private int selectedSlot = -1, mode, amount = 1, result, affinity;

    public TradeMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, findNpc(inventory, buffer.readVarInt()));
    }
    private TradeMenu(int id, Inventory inventory, ResidentEntity npc) {
        super(ModMenuTypes.TRADE_MENU.get(), id); this.npc=npc; this.playerInventory=inventory;
        Container stock=npc.getTradeInventory(); stock.startOpen(inventory.player);
        for(int row=0;row<3;row++) for(int col=0;col<6;col++) addSlot(new ReadOnlySlot(stock,col+row*6,205+col*18,132+row*18));
        for(int row=0;row<3;row++) for(int col=0;col<9;col++) addSlot(new ReadOnlySlot(inventory,col+row*9+9,298+col*18,231+row*18));
        for(int col=0;col<9;col++) addSlot(new ReadOnlySlot(inventory,col,298+col*18,290));
        addDataSlot(sync(() -> selectedSlot, value -> selectedSlot=value));
        addDataSlot(sync(() -> mode, value -> mode=value));
        addDataSlot(sync(() -> amount, value -> amount=value));
        addDataSlot(sync(() -> result, value -> result=value));
        addDataSlot(sync(() -> {
            if(inventory.player.level() instanceof net.minecraft.server.level.ServerLevel level&&npc.getPersonId()!=null)
                return com.sam.realmfolk.society.HumanSocietySavedData.get(level).getPerson(npc.getPersonId())
                        .map(p->p.getAffinity(inventory.player.getUUID())).orElse(0);
            return affinity;
        },value->affinity=value));
    }
    private static DataSlot sync(java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
        return new DataSlot(){public int get(){return get.getAsInt();}public void set(int value){set.accept(value);}};
    }
    public static void open(ServerPlayer player, ResidentEntity npc) {
        if (npc.getProfessionData().profession() != com.sam.realmfolk.profession.NpcProfession.MERCHANT) {
            player.sendSystemMessage(Component.literal(npc.getName().getString()
                    + " precisa trabalhar como comerciante para negociar."));
            return;
        }
        NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inv,ignored)->new TradeMenu(id,inv,npc),
                Component.literal("Comercio - "+npc.getName().getString())),b->b.writeVarInt(npc.getId()));
    }
    @Override public boolean clickMenuButton(Player player,int action) {
        if(!(player instanceof ServerPlayer server)||!stillValid(player)) return false;
        if(action>=SELECT_STOCK&&action<SELECT_STOCK+18){selectedSlot=action;mode=0;amount=1;result=0;return true;}
        if(action>=SELECT_PLAYER&&action<SELECT_PLAYER+36){selectedSlot=action-SELECT_PLAYER;mode=1;amount=1;result=0;return true;}
        if(action==BUY){mode=0;selectedSlot=-1;amount=1;result=0;return true;}
        if(action==SELL){mode=1;selectedSlot=-1;amount=1;result=0;return true;}
        if(action==MINUS){amount=Math.max(1,amount-1);return true;}
        if(action==PLUS){amount=Math.min(64,amount+1);return true;}
        if(action==CONFIRM){
            TradeService.Result tradeResult=mode==0?TradeService.buy(server,npc,selectedSlot,amount)
                    :TradeService.sell(server,npc,selectedSlot,amount);
            result=tradeResult.ordinal()+1;
            if(tradeResult==TradeService.Result.SUCCESS){amount=1;}
            broadcastChanges(); return true;
        }
        if(action==TALK){ConversationMenu.open(server,npc,"greeting");return true;}
        if(action==FAMILY&&npc.getPersonId()!=null){FamilyTreeMenu.open(server,npc,npc.getPersonId(),0);return true;}
        if(action==EQUIPMENT){NpcMenu.open(server,npc);return true;}
        if(action==PROFESSION){
            ProfessionMenu.open(server,npc,npc.getProfessionData().profession()==com.sam.realmfolk.profession.NpcProfession.NONE
                    ?com.sam.realmfolk.profession.NpcProfession.FARMER:npc.getProfessionData().profession());
            return true;
        }
        if(action==FOLLOW){if(npc.getBehaviorMode()==com.sam.realmfolk.entity.NpcBehaviorMode.FOLLOW)npc.wander();else npc.follow(server);TradeMenu.open(server,npc);return true;}
        if(action==STAY){if(npc.getBehaviorMode()==com.sam.realmfolk.entity.NpcBehaviorMode.STAY)npc.wander();else npc.stayHere();TradeMenu.open(server,npc);return true;}
        if(action==HOUSE){
            var person=npc.getPerson(server.serverLevel()).orElse(null);
            if(person==null)return true;
            String house=person.getHouseId()==null?"nao pertence a uma Casa nobre":com.sam.realmfolk.society.HouseRegistry.get(person.getHouseId()).map(h->"pertence a Casa "+h.surname()).orElse("pertence a uma Casa desconhecida");server.sendSystemMessage(Component.literal(person.getDisplayName()+" "+house+"."));
            return true;
        }
        return false;
    }
    public ResidentEntity getNpc(){return npc;} public int getMode(){return mode;} public int getAmount(){return amount;}
    public int getResult(){return result;} public int getSelectedSlot(){return selectedSlot;}
    public int getAffinity(){return affinity;}
    public ItemStack getSelectedStack(){
        if(selectedSlot<0)return ItemStack.EMPTY;
        return mode==0?npc.getTradeInventory().getItem(selectedSlot):playerInventory.getItem(selectedSlot);
    }
    public int getUnitPrice(){ItemStack s=getSelectedStack();if(s.isEmpty())return 0;var p=TradePriceRegistry.get(s.getItem());return mode==0?p.buyPrice():p.sellPrice();}
    public int playerEmeralds(){return TradeService.count(playerInventory,net.minecraft.world.item.Items.EMERALD);}
    public int npcEmeralds(){return TradeService.count(npc.getTradeInventory(),net.minecraft.world.item.Items.EMERALD);}
    @Override public boolean stillValid(Player player){return npc.isAlive()&&npc.distanceToSqr(player)<=64;}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    @Override public void removed(Player player){super.removed(player);npc.getTradeInventory().stopOpen(player);}
    private static ResidentEntity findNpc(Inventory inv,int id){if(inv.player.level().getEntity(id)instanceof ResidentEntity npc)return npc;throw new IllegalStateException("NPC do comercio nao encontrado");}
    private static final class ReadOnlySlot extends Slot {
        private ReadOnlySlot(Container c,int i,int x,int y){super(c,i,x,y);}
        @Override public boolean mayPickup(Player player){return false;}
        @Override public boolean mayPlace(ItemStack stack){return false;}
    }
}
