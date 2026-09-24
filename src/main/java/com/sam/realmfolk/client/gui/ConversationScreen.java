package com.sam.realmfolk.client.gui;

import com.sam.realmfolk.Realmfolk;
import com.sam.realmfolk.client.menu.ConversationMenu;
import com.sam.realmfolk.client.menu.ConversationSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class ConversationScreen extends AbstractContainerScreen<ConversationMenu> {
    private static final ResourceLocation FRAME = ResourceLocation.fromNamespaceAndPath(Realmfolk.MODID, "textures/gui/npc_menu_frame.png");
    private static final int PANEL_WIDTH = 640, PANEL_HEIGHT = 360, TEXTURE_WIDTH = 1672, TEXTURE_HEIGHT = 941;
    private static final int GOLD = 0xFFF0D08A, TEXT = 0xFFE8E2D8, MUTED = 0xFFAAA79F;
    private float panelScale = 1.0F;

    public ConversationScreen(ConversationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PANEL_WIDTH;
        imageHeight = PANEL_HEIGHT;
        titleLabelY = inventoryLabelY = -1000;
    }

    @Override protected void init() {
        super.init();
        leftPos = (width - imageWidth) / 2;
        topPos = (height - imageHeight) / 2;
        panelScale = Math.min(1.0F, Math.min((width - 16.0F) / imageWidth, (height - 16.0F) / imageHeight));

        int x = leftPos + 80;
        addNav(x, 100, "Conversar", Items.PAPER, -1, true);
        addNav(x, 129, "Familia", Items.PLAYER_HEAD, ConversationMenu.FAMILY, false);
        addNav(x, 158, "Casa", Items.OAK_DOOR, ConversationMenu.HOUSE, false);
        addNav(x, 187, "Profissões", Items.IRON_PICKAXE, ConversationMenu.PROFESSION, false);
        addNav(x, 216, "Equipamento", Items.IRON_CHESTPLATE, ConversationMenu.EQUIPMENT, false);
        addNav(x, 267, "Seguir", Items.LEAD, ConversationMenu.FOLLOW, false);
        addNav(x, 296, "Ficar aqui", Items.COMPASS, ConversationMenu.STAY, false);

        List<ConversationSnapshot.Response> responses = menu.getSnapshot().responses();
        for (int i = 0; i < responses.size(); i++) {
            ConversationSnapshot.Response response = responses.get(i);
            int action = i;
            String label = response.unlocked() ? response.text() : response.text() + " [" + response.requiredAffinity() + "]";
            DialogueButton button = new DialogueButton(leftPos + 194, topPos + 203 + i * 27, 252, 24,
                    Component.literal(label), ItemStack.EMPTY, ignored -> send(action), false);
            button.active = response.unlocked();
            addRenderableWidget(button);
        }

        addTopic("Vida pessoal", Items.BOOK, 0, 100);
        addTopic("Familia", Items.PLAYER_HEAD, 1, 126);
        addTopic("Trabalho", Items.IRON_PICKAXE, 2, 152);
        addTopic("Rumores", Items.MAP, 4, 178);
        addWidget(466, 224, 98, 23, "Elogiar", Items.RED_DYE, ConversationMenu.PRAISE, false);
        addWidget(466, 250, 98, 23, "Dar presente", Items.CHEST, ConversationMenu.GIFT, false);
        addWidget(466, 276, 98, 23, "Contar piada", Items.PAPER, ConversationMenu.JOKE, false);
        addRenderableWidget(new DialogueButton(leftPos + 545, topPos + 37, 26, 24,
                Component.literal("X"), ItemStack.EMPTY, ignored -> onClose(), false));
    }

    private void addNav(int x, int y, String label, Item icon, int action, boolean selected) {
        addRenderableWidget(new DialogueButton(x, topPos + y, 104, 25, Component.literal(label), icon.getDefaultInstance(),
                ignored -> { if (action >= 0) send(action); }, selected));
    }

    private void addTopic(String label, Item icon, int topic, int y) {
        int required = topic == 5 ? 20 : topic == 6 ? 50 : -100;
        String text = menu.getSnapshot().affinity() >= required ? label : label + " [" + required + "]";
        DialogueButton button = addWidget(466, y, 98, 23, text, icon, ConversationMenu.TOPIC_BASE + topic, false);
        button.active = menu.getSnapshot().affinity() >= required;
    }

    private DialogueButton addWidget(int x, int y, int w, int h, String label, Item icon, int action, boolean selected) {
        DialogueButton button = new DialogueButton(leftPos + x, topPos + y, w, h, Component.literal(label),
                icon.getDefaultInstance(), ignored -> send(action), selected);
        addRenderableWidget(button);
        return button;
    }

    private void send(int action) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.blit(FRAME, leftPos, topPos, imageWidth, imageHeight, 0, 0,
                TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        panel(graphics, leftPos + 194, topPos + 100, 252, 96);
        panel(graphics, leftPos + 466, topPos + 96, 98, 110);
        panel(graphics, leftPos + 466, topPos + 216, 98, 87);

        var npc = menu.getNpc();
        Component savedName = npc.getCustomName();
        boolean nameVisible = npc.isCustomNameVisible();
        npc.setCustomNameVisible(false);
        npc.setCustomName(null);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, leftPos + 226, topPos + 191, 31,
                leftPos + 226 - mouseX, topPos + 140 - mouseY, npc);
        npc.setCustomName(savedName);
        npc.setCustomNameVisible(nameVisible);

        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + 145, topPos + 40, 0);
        graphics.pose().scale(2.25F, 2.25F, 1);
        graphics.renderItem(Items.PLAYER_HEAD.getDefaultInstance(), 0, 0);
        graphics.pose().popPose();

        int affinity = menu.getSnapshot().affinity();
        int barWidth = Math.round(Math.max(0, Math.min(100, affinity + 50)) / 100.0F * 124);
        graphics.fill(leftPos + 421, topPos + 72, leftPos + 549, topPos + 79, 0xFF0D0F12);
        graphics.fill(leftPos + 423, topPos + 74, leftPos + 423 + barWidth, topPos + 77, affinityColor(affinity));
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        ConversationSnapshot snapshot = menu.getSnapshot();
        graphics.drawString(font, snapshot.speaker(), 190, 41, GOLD, true);
        graphics.drawString(font, "Humano  |  " + (snapshot.speaker().contains(" ") ? "Nobre" : "Plebeu"), 190, 59, TEXT, false);
        graphics.drawCenteredString(font, moodLabel(snapshot.affinity()), 367, 50, affinityColor(snapshot.affinity()));
        graphics.drawString(font, affinityLabel(snapshot.affinity()), 421, 57, GOLD, false);
        graphics.drawString(font, snapshot.speaker(), 240, 108, GOLD, false);
        graphics.drawWordWrap(font, Component.literal(snapshot.text()), 240, 124, 198, TEXT);
        graphics.drawCenteredString(font, "Escolha uma resposta", 320, 190, MUTED);
        graphics.drawCenteredString(font, "Assuntos", 515, 88, GOLD);
        graphics.drawCenteredString(font, "Interacao", 515, 208, GOLD);
        graphics.drawCenteredString(font, "Disponível agora", 515, 306, 0xFF55DD55);
        graphics.drawCenteredString(font, "Escolha uma resposta para continuar a conversa", imageWidth / 2, 326, MUTED);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xE8191B1E);
        outline(g, x, y, w, h, 0xFF8A6B32);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        int logicalX = (int) Math.round(toLogicalX(mouseX)), logicalY = (int) Math.round(toLogicalY(mouseY));
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, height / 2.0F, 0);
        graphics.pose().scale(panelScale, panelScale, 1);
        graphics.pose().translate(-width / 2.0F, -height / 2.0F, 0);
        super.render(graphics, logicalX, logicalY, partialTick);
        renderTooltip(graphics, logicalX, logicalY);
        graphics.pose().popPose();
    }

    private double toLogicalX(double value) { return width / 2.0D + (value - width / 2.0D) / panelScale; }
    private double toLogicalY(double value) { return height / 2.0D + (value - height / 2.0D) / panelScale; }
    @Override public boolean mouseClicked(double x, double y, int button) { return super.mouseClicked(toLogicalX(x), toLogicalY(y), button); }
    @Override public boolean mouseReleased(double x, double y, int button) { return super.mouseReleased(toLogicalX(x), toLogicalY(y), button); }

    private static String affinityLabel(int value) {
        if (value <= -60) return "Hostil"; if (value <= -20) return "Desconfiado";
        if (value < 20) return "Neutro"; if (value < 60) return "Amigavel";
        if (value < 85) return "Proximo"; return "Devoto";
    }
    private static String moodLabel(int value) {
        if (value <= -20) return "Irritado"; if (value >= 60) return "Muito feliz";
        if (value >= 20) return "Contente"; return "Calmo";
    }
    private static int affinityColor(int value) { return value < -19 ? 0xFFE05B5B : value > 19 ? 0xFF54D653 : 0xFFE0C45B; }

    private static final class DialogueButton extends AbstractButton {
        private final OnPress action;
        private final ItemStack icon;
        private final boolean selected;
        private DialogueButton(int x, int y, int w, int h, Component text, ItemStack icon, OnPress action, boolean selected) {
            super(x, y, w, h, text); this.icon = icon; this.action = action; this.selected = selected;
        }
        @Override public void onPress() { action.onPress(this); }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int bg = !active ? 0xE51A1A1A : selected ? 0xEE765115 : isHoveredOrFocused() ? 0xEE514124 : 0xE52A2B2D;
            int border = !active ? 0xFF4A4A4A : selected ? 0xFFFFC44F : isHoveredOrFocused() ? 0xFFFFC44F : 0xFF756F65;
            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            outline(g, getX(), getY(), width, height, border);
            if (!icon.isEmpty()) g.renderItem(icon, getX() + 4, getY() + (height - 16) / 2);
            var font = Minecraft.getInstance().font;
            int start = icon.isEmpty() ? 5 : 24, available = width - start - 4;
            float scale = Math.min(1, available / (float) Math.max(1, font.width(getMessage())));
            float renderedWidth = font.width(getMessage()) * scale;
            float textX = icon.isEmpty() ? getX() + (width - renderedWidth) / 2 : getX() + start;
            g.pose().pushPose();
            g.pose().translate(textX, getY() + (height - 8 * scale) / 2, 0);
            g.pose().scale(scale, scale, 1);
            g.drawString(font, getMessage(), 0, 0, active ? TEXT : 0xFF777777, false);
            g.pose().popPose();
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
        @FunctionalInterface private interface OnPress { void onPress(DialogueButton button); }
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color); g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color); g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
