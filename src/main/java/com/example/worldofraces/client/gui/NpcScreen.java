package com.example.worldofraces.client.gui;

import com.example.worldofraces.WorldOfRaces;
import com.example.worldofraces.client.menu.NpcMenu;
import com.example.worldofraces.entity.NpcBehaviorMode;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class NpcScreen extends AbstractContainerScreen<NpcMenu> {
    private static final ResourceLocation FRAME = new ResourceLocation(
            WorldOfRaces.MODID, "textures/gui/npc_menu_frame.png");
    private static final int TEXTURE_WIDTH = 1672;
    private static final int TEXTURE_HEIGHT = 941;
    private static final int PANEL_WIDTH = 640;
    private static final int PANEL_HEIGHT = 360;
    private static final int SCREEN_MARGIN = 8;
    private static final int GOLD = 0xFFF0D08A;
    private static final int MUTED = 0xFFAAA79F;
    private float panelScale = 1.0F;
    private MedievalButton followButton;
    private MedievalButton stayButton;

    public NpcScreen(NpcMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();

        // Every component uses this unscaled, centered coordinate system.
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        this.panelScale = Math.min(1.0F, Math.min(
                (this.width - SCREEN_MARGIN * 2.0F) / this.imageWidth,
                (this.height - SCREEN_MARGIN * 2.0F) / this.imageHeight));

        int x = leftPos + 80;
        int y = topPos + 100;
        addActionTab("Conversar", Items.PAPER.getDefaultInstance(), x, y, NpcMenu.TALK_ACTION);
        addRenderableWidget(new MedievalButton(x, y + 29, 104, 25, Component.literal("Familia"),
                Items.PLAYER_HEAD.getDefaultInstance(), button -> openFamilyTree(), false));
        addActionTab("Casa", Items.OAK_DOOR.getDefaultInstance(), x, y + 58, NpcMenu.HOUSE_TAB);
        addActionTab("Profissoes", Items.IRON_PICKAXE.getDefaultInstance(), x, y + 87, NpcMenu.PROFESSIONS_TAB);
        addTab("Equipamento", Items.IRON_CHESTPLATE.getDefaultInstance(), x, y + 116, true);
        addActionTab("Comercio", Items.EMERALD.getDefaultInstance(), x, y + 145, NpcMenu.TRADE_TAB);
        this.followButton = addActionTab("Seguir", Items.LEAD.getDefaultInstance(), x, y + 174, NpcMenu.FOLLOW_ACTION);
        this.stayButton = addActionTab("Ficar aqui", Items.COMPASS.getDefaultInstance(), x, y + 203, NpcMenu.STAY_ACTION);
        addRenderableWidget(new MedievalButton(leftPos + 545, topPos + 37, 26, 24,
                Component.literal("X"), ItemStack.EMPTY, button -> onClose(), false));
    }

    private void addTab(String label, ItemStack icon, int x, int y, boolean selected) {
        addRenderableWidget(new MedievalButton(x, y, 104, 25, Component.literal(label), icon, button -> {
            if (!selected && minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.literal(label + ": recurso em desenvolvimento"), true);
            }
        }, selected));
    }

    private MedievalButton addActionTab(String label, ItemStack icon, int x, int y, int action) {
        MedievalButton button = new MedievalButton(x, y, 104, 25, Component.literal(label), icon,
                ignored -> sendMenuAction(action), false);
        addRenderableWidget(button);
        return button;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (followButton != null) followButton.setMessage(Component.literal(
                menu.getBehaviorMode() == NpcBehaviorMode.FOLLOW ? "Parar" : "Seguir"));
        if (stayButton != null) stayButton.setMessage(Component.literal(
                menu.getBehaviorMode() == NpcBehaviorMode.STAY ? "Pode andar" : "Ficar aqui"));
    }

    private void sendMenuAction(int action) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
        }
    }

    private void openFamilyTree() {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, NpcMenu.FAMILY_TAB);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(FRAME, leftPos, topPos, imageWidth, imageHeight,
                0.0F, 0.0F, TEXTURE_WIDTH, TEXTURE_HEIGHT,
                TEXTURE_WIDTH, TEXTURE_HEIGHT);

        // Rebuild the lower right header so its separator never crosses the title.
        graphics.fill(leftPos + 386, topPos + 192, leftPos + 558, topPos + 218, 0xE817191C);
        graphics.fill(leftPos + 389, topPos + 213, leftPos + 555, topPos + 214, 0xFFD0A347);

        for (int index = 0; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, 0xCC090B0E);
            outline(graphics, x, y, 18, 18, 0xFF62666B);
            // Vanilla already draws the native empty armor/shield silhouettes.
            // Only the main-hand slot needs a custom hint because Minecraft has no empty sword sprite.
            if (index == 4 && !slot.hasItem()) {
                int iconX = leftPos + slot.x;
                int iconY = topPos + slot.y;
                drawEmptySwordIcon(graphics, iconX, iconY);
            }
        }

        Component savedName = menu.getNpc().getCustomName();
        boolean nameWasVisible = menu.getNpc().isCustomNameVisible();
        menu.getNpc().setCustomNameVisible(false);
        menu.getNpc().setCustomName(null);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics, leftPos + 276, topPos + 289, 55,
                leftPos + 276 - mouseX, topPos + 192 - mouseY, menu.getNpc());
        menu.getNpc().setCustomName(savedName);
        menu.getNpc().setCustomNameVisible(nameWasVisible);

        int affinity = menu.getAffinity();
        int affinityWidth = Math.round((affinity + 100) / 200.0F * 160.0F);
        int affinityColor = affinity < -19 ? 0xFFB83A3A : affinity > 19 ? 0xFF42B83F : 0xFFD5A83B;
        graphics.fill(leftPos + 388, topPos + 73, leftPos + 554, topPos + 78, 0xFF111419);
        graphics.fill(leftPos + 391, topPos + 75, leftPos + 391 + affinityWidth, topPos + 77, affinityColor);

        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + 145, topPos + 40, 0.0F);
        graphics.pose().scale(2.25F, 2.25F, 1.0F);
        graphics.renderItem(Items.PLAYER_HEAD.getDefaultInstance(), 0, 0);
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String npcName = menu.getNpc().getName().getString();
        graphics.drawString(font, npcName, 190, 41, GOLD, true);
        graphics.drawString(font, "Humano", 190, 59, 0xFFE5E0D7, false);
        graphics.drawString(font, npcName.contains(" ") ? "Nobre" : "Plebeu",
                264, 59, 0xFFE5E0D7, false);
        graphics.drawCenteredString(font, affinityLabel(menu.getAffinity()), 471, 57,
                menu.getAffinity() < -19 ? 0xFFE05B5B : menu.getAffinity() > 19 ? 0xFF54D653 : 0xFFE0C45B);

        graphics.drawCenteredString(font, "Pertences", 472, 101, GOLD);
        graphics.drawCenteredString(font, "Seu inventario", 472, 198, GOLD);
        graphics.drawCenteredString(font, npcName, 274, 102, GOLD);
        graphics.drawCenteredString(font, "Clique em um item para equipar ou remover",
                imageWidth / 2, 325, MUTED);
    }

    private static String affinityLabel(int affinity) {
        if (affinity <= -60) return "Hostil";
        if (affinity <= -20) return "Desconfiado";
        if (affinity < 20) return "Neutro";
        if (affinity < 60) return "Amigavel";
        if (affinity < 85) return "Proximo";
        return "Devoto";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);

        int logicalMouseX = (int) Math.round(toLogicalX(mouseX));
        int logicalMouseY = (int) Math.round(toLogicalY(mouseY));
        graphics.pose().pushPose();
        graphics.pose().translate(this.width / 2.0F, this.height / 2.0F, 0.0F);
        graphics.pose().scale(this.panelScale, this.panelScale, 1.0F);
        graphics.pose().translate(-this.width / 2.0F, -this.height / 2.0F, 0.0F);
        super.render(graphics, logicalMouseX, logicalMouseY, partialTick);
        renderTooltip(graphics, logicalMouseX, logicalMouseY);
        if (stayButton != null && stayButton.isHoveredOrFocused()
                && menu.getBehaviorMode() == NpcBehaviorMode.STAY) {
            graphics.renderTooltip(font,
                    Component.literal("Permite que o habitante ande livremente"),
                    logicalMouseX, logicalMouseY);
        }
        graphics.pose().popPose();
    }

    private double toLogicalX(double screenX) {
        return this.width / 2.0D + (screenX - this.width / 2.0D) / this.panelScale;
    }

    private double toLogicalY(double screenY) {
        return this.height / 2.0D + (screenY - this.height / 2.0D) / this.panelScale;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(toLogicalX(mouseX), toLogicalY(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(toLogicalX(mouseX), toLogicalY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(toLogicalX(mouseX), toLogicalY(mouseY), button,
                dragX / this.panelScale, dragY / this.panelScale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return super.mouseScrolled(toLogicalX(mouseX), toLogicalY(mouseY), delta);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static void drawEmptySwordIcon(GuiGraphics graphics, int x, int y) {
        int shadow = 0xFF3D4146;
        int outline = 0xFF777C82;
        int highlight = 0xFFA4A8AD;

        // Diagonal blade, crossguard and handle in the same muted style as vanilla empty slots.
        for (int step = 0; step < 8; step++) {
            int bladeX = x + 6 + step;
            int bladeY = y + 9 - step;
            graphics.fill(bladeX, bladeY, bladeX + 2, bladeY + 2, outline);
            graphics.fill(bladeX + 1, bladeY, bladeX + 2, bladeY + 1, highlight);
        }
        graphics.fill(x + 4, y + 9, x + 10, y + 11, outline);
        graphics.fill(x + 3, y + 10, x + 6, y + 13, shadow);
        graphics.fill(x + 2, y + 12, x + 5, y + 15, outline);
        graphics.fill(x + 1, y + 14, x + 4, y + 16, shadow);
    }

    private static final class MedievalButton extends AbstractButton {
        private final OnPress onPress;
        private final boolean selected;
        private final ItemStack icon;

        private MedievalButton(int x, int y, int width, int height, Component message,
                               ItemStack icon, OnPress onPress, boolean selected) {
            super(x, y, width, height, message);
            this.onPress = onPress;
            this.selected = selected;
            this.icon = icon;
        }

        @Override
        public void onPress() {
            onPress.onPress(this);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int background = selected ? 0xEE765115 : isHoveredOrFocused() ? 0xEE42403B : 0xDD292A2B;
            int border = selected ? 0xFFFFC44F : isHoveredOrFocused() ? 0xFFD2A34C : 0xFF69645B;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
            outline(graphics, getX(), getY(), width, height, border);
            if (!icon.isEmpty()) graphics.renderItem(icon, getX() + 3, getY() + 2);
            var font = Minecraft.getInstance().font;
            int availableWidth = icon.isEmpty() ? width - 8 : width - 27;
            float textScale = Math.min(1.0F,
                    availableWidth / (float) Math.max(1, font.width(getMessage())));
            float renderedWidth = font.width(getMessage()) * textScale;
            float textX = icon.isEmpty() ? getX() + (width - renderedWidth) / 2.0F : getX() + 23;
            float textY = getY() + (height - 8.0F * textScale) / 2.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(textX, textY, 0.0F);
            graphics.pose().scale(textScale, textScale, 1.0F);
            graphics.drawString(font, getMessage(), 0, 0, selected ? GOLD : 0xFFE8E2D8, false);
            graphics.pose().popPose();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }

        @FunctionalInterface
        private interface OnPress {
            void onPress(MedievalButton button);
        }
    }
}
