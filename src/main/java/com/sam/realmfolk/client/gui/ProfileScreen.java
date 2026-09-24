package com.sam.realmfolk.client.gui;

import com.sam.realmfolk.Realmfolk;
import com.sam.realmfolk.client.menu.ProfileMenu;
import com.sam.realmfolk.client.menu.ProfileSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ProfileScreen extends AbstractContainerScreen<ProfileMenu> {
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(Realmfolk.MODID, "textures/gui/family_tree_frame.png");
    private static final int W = 640, H = 360, TW = 1671, TH = 941;
    private static final int GOLD = 0xFFF0D08A, TEXT = 0xFFE8E2D8, MUTED = 0xFFAAA79F;
    private float scale = 1;

    public ProfileScreen(ProfileMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = W; imageHeight = H; titleLabelY = inventoryLabelY = -1000;
    }

    @Override protected void init() {
        super.init(); leftPos = (width - W) / 2; topPos = (height - H) / 2;
        scale = Math.min(1, Math.min((width - 16F) / W, (height - 16F) / H));
        button(82, 39, 32, 25, "<", ItemStack.EMPTY, ProfileMenu.BACK);
        button(545, 39, 26, 25, "X", ItemStack.EMPTY, -1);
        button(378, 286, 60, 37, "Conversar", Items.PAPER.getDefaultInstance(), ProfileMenu.TALK);
        button(440, 286, 60, 37, "Seguir", Items.LEAD.getDefaultInstance(), ProfileMenu.FOLLOW);
        ProfileButton locate = button(502, 286, 60, 37, "Localizar", Items.COMPASS.getDefaultInstance(), ProfileMenu.LOCATE);
        locate.active = menu.getSnapshot().canLocate();
        button(198, 286, 172, 25, "Ver arvore genealogica", Items.OAK_SAPLING.getDefaultInstance(), ProfileMenu.BACK);
    }

    private ProfileButton button(int x, int y, int w, int h, String label, ItemStack icon, int action) {
        ProfileButton b = new ProfileButton(leftPos + x, topPos + y, w, h, Component.literal(label), icon,
                ignored -> { if (action < 0) onClose(); else send(action); }); addRenderableWidget(b); return b;
    }
    private void send(int action) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action); }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(FRAME, leftPos, topPos, W, H, 0, 0, TW, TH, TW, TH);
        panel(g, 78, 86, 112, 236); panel(g, 198, 86, 172, 126); panel(g, 198, 218, 172, 64);
        panel(g, 378, 86, 184, 126); panel(g, 378, 218, 184, 64);
        if (menu.getSourceNpc().getPersonId() != null
                && menu.getSourceNpc().getPersonId().equals(menu.getSnapshot().personId())) {
            Component savedName = menu.getSourceNpc().getCustomName();
            boolean nameVisible = menu.getSourceNpc().isCustomNameVisible();
            menu.getSourceNpc().setCustomNameVisible(false);
            menu.getSourceNpc().setCustomName(null);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, leftPos + 134, topPos + 155, 42,
                    leftPos + 134 - mouseX, topPos + 112 - mouseY, menu.getSourceNpc());
            menu.getSourceNpc().setCustomName(savedName);
            menu.getSourceNpc().setCustomNameVisible(nameVisible);
        } else {
            g.pose().pushPose(); g.pose().translate(leftPos + 108, topPos + 99, 0); g.pose().scale(3.25F, 3.25F, 1);
            g.renderItem(Items.PLAYER_HEAD.getDefaultInstance(), 0, 0); g.pose().popPose();
        }
        int affinity = menu.getSnapshot().affinity();
        g.fill(leftPos + 88, topPos + 286, leftPos + 180, topPos + 294, 0xFF0B0D10);
        g.fill(leftPos + 90, topPos + 288, leftPos + 90 + Math.round((affinity + 100) / 200F * 88), topPos + 292,
                affinity < -19 ? 0xFFB83A3A : affinity > 19 ? 0xFF42B83F : 0xFFD5A83B);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        ProfileSnapshot p = menu.getSnapshot();
        g.drawCenteredString(font, "Perfil do habitante", W / 2, 39, GOLD);
        g.drawCenteredString(font, "Informações pessoais e familiares", W / 2, 53, MUTED);
        drawFitted(g, p.name(), 88, 158, 92, GOLD);
        drawFitted(g, "Humano | " + (p.house().equals("Sem Casa") ? "Plebeu" : "Nobre"), 88, 174, 92, TEXT);
        g.drawString(font, p.alive() ? "Vivo" : "Falecido", 88, 192, p.alive() ? 0xFF55DD55 : 0xFFE05B5B, false);
        g.drawString(font, stage(p.lifeStage()), 88, 211, TEXT, false);
        g.drawString(font, p.gender().equals("MALE") ? "Masculino" : "Feminino", 88, 228, TEXT, false);
        g.drawString(font, affinity(p.affinity()), 88, 267, GOLD, false);
        g.drawCenteredString(font, p.affinity() + " / 100", 134, 299, MUTED);

        title(g, "Informações", 204, 94);
        line(g, "Idade", reveal(p, 2, p.age() + " anos"), 204, 116);
        line(g, "Profissão", reveal(p, 2, p.profession()), 204, 132);
        line(g, "Moradia", reveal(p, 2, p.residence()), 204, 148);
        line(g, "Personalidade", reveal(p, 2, p.personality()), 204, 164);
        line(g, "Moral", reveal(p, 3, p.morality()), 204, 180);

        title(g, "Familia", 204, 226);
        line(g, "Pai", reveal(p, 2, p.father()), 204, 242);
        line(g, "Mãe", reveal(p, 2, p.mother()), 204, 253);
        line(g, "Cônjuge", reveal(p, 2, p.spouse()), 204, 264);
        String family = Integer.toString(p.children()) + (p.familyState().isBlank() ? "" : " | " + p.familyState());
        line(g, "Filhos", reveal(p, 2, family), 204, 275);

        title(g, "Histórico", 384, 94);
        line(g, "Casa", reveal(p, 2, p.house()), 384, 116);
        line(g, "Origem", reveal(p, 3, p.origin()), 384, 136);
        line(g, "Reputação", reveal(p, 2, affinity(p.affinity())), 384, 156);
        line(g, "Última interação", reveal(p, 3, p.lastInteraction()), 384, 176);

        title(g, "Características", 384, 226);
        if (p.knowledge() >= 3) {
            stat(g, "Forca", p.strength(), 384, 242); stat(g, "Inteligencia", p.intelligence(), 384, 252);
            stat(g, "Carisma", p.charisma(), 384, 262); stat(g, "Coragem", p.courage(), 384, 272);
        } else g.drawString(font, "Aumente a afinidade para descobrir", 384, 246, MUTED, false);
        g.drawCenteredString(font, knowledgeText(p.knowledge()), W / 2, 328, MUTED);
    }

    private static String reveal(ProfileSnapshot p, int level, String value) {
        return p.knowledge() >= level ? value : "Não descoberto";
    }
    private static String stage(String value) { return switch (value) { case "BABY" -> "Bebe"; case "CHILD" -> "Crianca"; case "TEENAGER" -> "Adolescente"; case "ELDER" -> "Idoso"; default -> "Adulto"; }; }
    private static String affinity(int v) { return v < -19 ? "Desconfiado" : v < 20 ? "Neutro" : v < 60 ? "Amigavel" : "Aliado"; }
    private static String knowledgeText(int k) { return switch (k) { case 0 -> "Converse para conhecer este habitante"; case 1 -> "Informacoes basicas descobertas"; case 2 -> "Informacoes pessoais descobertas"; default -> "Perfil conhecido"; }; }
    private void title(GuiGraphics g, String text, int x, int y) { g.drawString(font, text, x, y, GOLD, true); }
    private void line(GuiGraphics g, String key, String value, int x, int y) {
        String text = key + ": " + value;
        int available = x < 378 ? 160 : 172;
        float textScale = Math.min(1.0F, available / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(textScale, textScale, 1);
        g.drawString(font, text, 0, 0, TEXT, false);
        g.pose().popPose();
    }
    private void stat(GuiGraphics g, String name, int value, int x, int y) {
        g.drawString(font, name, x, y, TEXT, false); g.fill(x + 72, y + 2, x + 142, y + 8, 0xFF0B0D10);
        g.fill(x + 74, y + 4, x + 74 + value * 6, y + 6, 0xFFD5A83B); g.drawString(font, Integer.toString(value), x + 148, y, GOLD, false);
    }
    private void drawFitted(GuiGraphics g, String text, int x, int y, int available, int color) {
        float fitted = Math.min(1.0F, available / (float) Math.max(1, font.width(text)));
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(fitted, fitted, 1);
        g.drawString(font, text, 0, 0, color, false); g.pose().popPose();
    }
    private void panel(GuiGraphics g, int x, int y, int w, int h) { x += leftPos; y += topPos; g.fill(x, y, x+w, y+h, 0xE8191B1E); outline(g,x,y,w,h,0xFF8A6B32); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0,0,width,height,0x99000000); int lx=(int)Math.round(toX(mouseX)), ly=(int)Math.round(toY(mouseY));
        g.pose().pushPose(); g.pose().translate(width/2F,height/2F,0); g.pose().scale(scale,scale,1); g.pose().translate(-width/2F,-height/2F,0);
        super.render(g,lx,ly,partialTick); renderTooltip(g,lx,ly); g.pose().popPose();
    }
    private double toX(double x){return width/2D+(x-width/2D)/scale;} private double toY(double y){return height/2D+(y-height/2D)/scale;}
    @Override public boolean mouseClicked(double x,double y,int b){return super.mouseClicked(toX(x),toY(y),b);}
    @Override public boolean mouseReleased(double x,double y,int b){return super.mouseReleased(toX(x),toY(y),b);}
    private static void outline(GuiGraphics g,int x,int y,int w,int h,int c){g.fill(x,y,x+w,y+1,c);g.fill(x,y+h-1,x+w,y+h,c);g.fill(x,y,x+1,y+h,c);g.fill(x+w-1,y,x+w,y+h,c);}

    private static final class ProfileButton extends AbstractButton {
        private final ItemStack icon; private final OnPress action;
        private ProfileButton(int x,int y,int w,int h,Component text,ItemStack icon,OnPress action){super(x,y,w,h,text);this.icon=icon;this.action=action;}
        @Override public void onPress(){action.press(this);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float pt){int bg=!active?0xDD171717:isHoveredOrFocused()?0xEE514124:0xE52A2B2D;g.fill(getX(),getY(),getX()+width,getY()+height,bg);outline(g,getX(),getY(),width,height,active?0xFF8A6B32:0xFF444444);if(!icon.isEmpty())g.renderItem(icon,getX()+4,getY()+3);var f=Minecraft.getInstance().font;float s=Math.min(1,(width-8F)/Math.max(1,f.width(getMessage())));g.pose().pushPose();g.pose().translate(getX()+(width-f.width(getMessage())*s)/2F,getY()+height-11,0);g.pose().scale(s,s,1);g.drawString(f,getMessage(),0,0,active?TEXT:0xFF777777,false);g.pose().popPose();}
        @Override protected void updateWidgetNarration(NarrationElementOutput o){defaultButtonNarrationText(o);}
        @FunctionalInterface private interface OnPress{void press(ProfileButton b);}
    }
}
