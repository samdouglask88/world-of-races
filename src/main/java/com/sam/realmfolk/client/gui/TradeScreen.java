package com.sam.realmfolk.client.gui;

import com.sam.realmfolk.Realmfolk;
import com.sam.realmfolk.client.menu.TradeMenu;
import com.sam.realmfolk.trade.TradePriceRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

public final class TradeScreen extends AbstractContainerScreen<TradeMenu> {
    private static final ResourceLocation FRAME=ResourceLocation.fromNamespaceAndPath(Realmfolk.MODID,"textures/gui/npc_menu_frame.png");
    private static final int W=640,H=360,TW=1672,TH=941,GOLD=0xFFF0D08A,TEXT=0xFFE8E2D8,MUTED=0xFFAAA79F;
    private float scale=1; private Filter filter=Filter.ALL; private TradeButton confirm;
    public TradeScreen(TradeMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=W;imageHeight=H;titleLabelY=inventoryLabelY=-1000;}

    @Override protected void init(){super.init();leftPos=(width-W)/2;topPos=(height-H)/2;scale=Math.min(1,Math.min((width-16F)/W,(height-16F)/H));
        nav("Conversar",100,Items.PAPER,TradeMenu.TALK);nav("Familia",129,Items.PLAYER_HEAD,TradeMenu.FAMILY);nav("Casa",158,Items.OAK_DOOR,TradeMenu.HOUSE);
        nav("Profissoes",187,Items.IRON_PICKAXE,TradeMenu.PROFESSION);nav("Equipamento",216,Items.IRON_CHESTPLATE,TradeMenu.EQUIPMENT);
        add(leftPos+80,topPos+245,104,25,"Comercio",Items.EMERALD.getDefaultInstance(),()->{},true);
        nav(menu.getNpc().getBehaviorMode()==com.sam.realmfolk.entity.NpcBehaviorMode.FOLLOW?"Parar de seguir":"Seguir",279,Items.LEAD,TradeMenu.FOLLOW);
        nav(menu.getNpc().getBehaviorMode()==com.sam.realmfolk.entity.NpcBehaviorMode.STAY?"Pode andar":"Ficar aqui",308,Items.COMPASS,TradeMenu.STAY);
        filter("Todos",194,112,Filter.ALL);filter("Comida",234,112,Filter.FOOD);filter("Materiais",274,112,Filter.MATERIAL);filter("Equip.",326,112,Filter.EQUIPMENT);
        add(leftPos+356,topPos+113,43,22,"Comprar",ItemStack.EMPTY,()->send(TradeMenu.BUY),false);
        add(leftPos+402,topPos+113,43,22,"Vender",ItemStack.EMPTY,()->send(TradeMenu.SELL),false);
        add(leftPos+366,topPos+159,22,22,"-",ItemStack.EMPTY,()->send(TradeMenu.MINUS),false);
        add(leftPos+413,topPos+159,22,22,"+",ItemStack.EMPTY,()->send(TradeMenu.PLUS),false);
        confirm=add(leftPos+466,topPos+175,98,27,"Confirmar troca",Items.EMERALD.getDefaultInstance(),()->send(TradeMenu.CONFIRM),false);
        add(leftPos+545,topPos+37,26,24,"X",ItemStack.EMPTY,this::onClose,false);
    }
    private void nav(String text,int y,net.minecraft.world.item.Item icon,int action){add(leftPos+80,topPos+y,104,25,text,icon.getDefaultInstance(),()->send(action),false);}
    private void filter(String text,int x,int y,Filter value){add(leftPos+x,topPos+y,Math.max(36,font.width(text)+8),16,text,ItemStack.EMPTY,()->{filter=value;rebuildWidgets();},filter==value);}
    private TradeButton add(int x,int y,int w,int h,String text,ItemStack icon,Runnable action,boolean selected){TradeButton b=new TradeButton(x,y,w,h,Component.literal(text),icon,action,selected);addRenderableWidget(b);return b;}
    private void send(int action){if(minecraft!=null&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    @Override protected void containerTick(){super.containerTick();ItemStack s=menu.getSelectedStack();int unit=menu.getUnitPrice();int total=unit*menu.getAmount();
        confirm.active=!s.isEmpty()&&unit>0&&menu.getAmount()<=s.getCount()&&(menu.getMode()==0?menu.playerEmeralds()>=total:menu.npcEmeralds()>=total);}

    @Override protected void renderBg(GuiGraphics g,float pt,int mx,int my){g.blit(FRAME,leftPos,topPos,W,H,0,0,TW,TH,TW,TH);
        panel(g,194,96,156,102);panel(g,356,96,90,102);panel(g,466,96,98,106);panel(g,194,214,370,100);
        for(int i=0;i<menu.slots.size();i++){var slot=menu.slots.get(i);int x=leftPos+slot.x-1,y=topPos+slot.y-1;g.fill(x,y,x+18,y+18,0xDD0B0D10);outline(g,x,y,18,18,0xFF615B51);}
        int selected=menu.getSelectedSlot();if(selected>=0){int menuIndex=menu.getMode()==0?selected:selected<9?45+selected:18+(selected-9);if(menuIndex>=0&&menuIndex<menu.slots.size()){var s=menu.slots.get(menuIndex);outline(g,leftPos+s.x-2,topPos+s.y-2,20,20,0xFFFFC44F);}}
        g.pose().pushPose();g.pose().translate(leftPos+145,topPos+40,0);g.pose().scale(2.25F,2.25F,1);g.renderItem(Items.PLAYER_HEAD.getDefaultInstance(),0,0);g.pose().popPose();
        int bar=Math.round((menu.getAffinity()+100)/200F*124);g.fill(leftPos+421,topPos+72,leftPos+549,topPos+79,0xFF0D0F12);g.fill(leftPos+423,topPos+74,leftPos+423+bar,topPos+77,0xFFD5A83B);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){String name=menu.getNpc().getName().getString();
        String profession=menu.getNpc().getProfessionData().profession().name;
        g.drawString(font,name,190,41,GOLD,true);g.drawString(font,"Humano | "+profession,190,59,TEXT,false);g.drawString(font,affinityLabel(menu.getAffinity()),421,57,GOLD,false);
        g.drawCenteredString(font,"Estoque de "+name,272,100,GOLD);g.drawCenteredString(font,"Operacao",401,100,GOLD);g.drawCenteredString(font,"Negociacao",515,100,GOLD);
        g.drawCenteredString(font,menu.getMode()==0?"Comprar":"Vender",401,141,menu.getMode()==0?0xFF55DD55:0xFFFFC44F);
        g.drawCenteredString(font,Integer.toString(menu.getAmount()),401,166,TEXT);
        ItemStack s=menu.getSelectedStack();int unit=menu.getUnitPrice(),total=unit*menu.getAmount();
        if(!s.isEmpty()){g.renderItem(s,476,116);g.drawString(font,font.plainSubstrByWidth(s.getHoverName().getString(),70),496,120,TEXT,false);
            g.drawString(font,"Quantidade: "+menu.getAmount(),472,140,TEXT,false);g.drawString(font,"Unitario: "+unit,472,152,TEXT,false);g.drawString(font,"Total: "+total,472,164,GOLD,false);
        }else g.drawCenteredString(font,"Selecione um item",515,128,MUTED);
        String warning=warning();if(!warning.isEmpty())g.drawCenteredString(font,warning,515,207,0xFFE05B5B);
        g.drawCenteredString(font,"Seu inventario",379,218,GOLD);g.drawCenteredString(font,"Selecione um item para comprar ou vender",W/2,326,MUTED);
        g.drawString(font,"Esmeraldas: "+menu.playerEmeralds(),198,296,GOLD,false);
        for(int i=0;i<18;i++){ItemStack stack=menu.getNpc().getTradeInventory().getItem(i);if(!visible(stack))g.fill(menu.slots.get(i).x-1,menu.slots.get(i).y-1,menu.slots.get(i).x+17,menu.slots.get(i).y+17,0xDD000000);}
    }
    private static String affinityLabel(int value){if(value<=-60)return"Hostil";if(value<=-20)return"Desconfiado";if(value<20)return"Neutro";if(value<60)return"Amigavel";if(value<85)return"Proximo";return"Devoto";}
    private String warning(){if(menu.getResult()>0)return switch(menu.getResult()-1){case 2->"Estoque insuficiente";case 3->"Esmeraldas insuficientes";case 4->"Itens insuficientes";case 5->"NPC sem esmeraldas";case 6->"Sem espaco no inventario";default->"";};
        ItemStack s=menu.getSelectedStack();int total=menu.getUnitPrice()*menu.getAmount();if(!s.isEmpty()&&menu.getUnitPrice()>0){if(menu.getMode()==0&&menu.playerEmeralds()<total)return"Esmeraldas insuficientes";if(menu.getMode()==1&&menu.npcEmeralds()<total)return"NPC sem esmeraldas";}return"";}
    private boolean visible(ItemStack s){if(s.isEmpty()||s.is(Items.EMERALD))return false;return switch(filter){case ALL->true;case FOOD->s.isEdible();case EQUIPMENT->s.isDamageableItem();case MATERIAL->!s.isEdible()&&!s.isDamageableItem();};}
    private void panel(GuiGraphics g,int x,int y,int w,int h){x+=leftPos;y+=topPos;g.fill(x,y,x+w,y+h,0xE8191B1E);outline(g,x,y,w,h,0xFF8A6B32);}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){g.fill(0,0,width,height,0x99000000);int lx=(int)Math.round(toX(mx)),ly=(int)Math.round(toY(my));g.pose().pushPose();g.pose().translate(width/2F,height/2F,0);g.pose().scale(scale,scale,1);g.pose().translate(-width/2F,-height/2F,0);super.render(g,lx,ly,pt);renderTradeTooltip(g,lx,ly);g.pose().popPose();}
    private void renderTradeTooltip(GuiGraphics g,int x,int y){
        if(hoveredSlot!=null&&hoveredSlot.index<18&&visible(hoveredSlot.getItem())){
            ItemStack stack=hoveredSlot.getItem();int price=TradePriceRegistry.get(stack.getItem()).buyPrice();
            g.renderTooltip(font,List.of(stack.getHoverName(),Component.literal("Quantidade: "+stack.getCount()),
                    Component.literal("Preco: "+price+" esmeralda"+(price==1?"":"s"))),Optional.empty(),x,y);
        }else renderTooltip(g,x,y);
    }
    @Override public boolean mouseClicked(double x,double y,int button){double lx=toX(x),ly=toY(y);for(int i=0;i<18;i++){var s=menu.slots.get(i);if(inside(lx,ly,leftPos+s.x,topPos+s.y,16,16)&&visible(s.getItem())){send(TradeMenu.SELECT_STOCK+i);return true;}}
        for(int i=18;i<54;i++){var s=menu.slots.get(i);if(inside(lx,ly,leftPos+s.x,topPos+s.y,16,16)){int inventoryIndex=i<45?9+(i-18):i-45;send(TradeMenu.SELECT_PLAYER+inventoryIndex);return true;}}return super.mouseClicked(lx,ly,button);}
    @Override public boolean mouseReleased(double x,double y,int b){return super.mouseReleased(toX(x),toY(y),b);}private double toX(double x){return width/2D+(x-width/2D)/scale;}private double toY(double y){return height/2D+(y-height/2D)/scale;}private static boolean inside(double x,double y,int bx,int by,int w,int h){return x>=bx&&x<bx+w&&y>=by&&y<by+h;}
    private enum Filter{ALL,FOOD,MATERIAL,EQUIPMENT}
    private static void outline(GuiGraphics g,int x,int y,int w,int h,int c){g.fill(x,y,x+w,y+1,c);g.fill(x,y+h-1,x+w,y+h,c);g.fill(x,y,x+1,y+h,c);g.fill(x+w-1,y,x+w,y+h,c);}
    private static final class TradeButton extends AbstractButton{private final ItemStack icon;private final Runnable action;private final boolean selected;private TradeButton(int x,int y,int w,int h,Component t,ItemStack i,Runnable a,boolean s){super(x,y,w,h,t);icon=i;action=a;selected=s;}@Override public void onPress(){action.run();}@Override protected void renderWidget(GuiGraphics g,int mx,int my,float pt){int bg=!active?0xDD171717:selected?0xEE765115:isHoveredOrFocused()?0xEE514124:0xE52A2B2D;g.fill(getX(),getY(),getX()+width,getY()+height,bg);outline(g,getX(),getY(),width,height,!active?0xFF444444:selected?0xFFFFC44F:0xFF756F65);if(!icon.isEmpty())g.renderItem(icon,getX()+3,getY()+(height-16)/2);var f=Minecraft.getInstance().font;int start=icon.isEmpty()?4:22;float s=Math.min(1,(width-start-3F)/Math.max(1,f.width(getMessage())));g.pose().pushPose();g.pose().translate(getX()+start,getY()+(height-8*s)/2,0);g.pose().scale(s,s,1);g.drawString(f,getMessage(),0,0,active?TEXT:0xFF777777,false);g.pose().popPose();}@Override protected void updateWidgetNarration(NarrationElementOutput o){defaultButtonNarrationText(o);}}
}
