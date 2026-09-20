package com.example.worldofraces.client.gui;

import com.example.worldofraces.client.menu.NpcMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class NpcScreen extends AbstractContainerScreen<NpcMenu> {
    private static final int PANEL = 0xF010151E;
    private static final int PANEL_LIGHT = 0xE01B2430;
    private static final int BORDER = 0xFFB68A45;
    private static final int TEXT_MUTED = 0xFF9FA8B7;

    public NpcScreen(NpcMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 340;
        this.imageHeight = 220;
        this.inventoryLabelY = 114;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 8;
        int y = topPos + 34;
        addMenuButton("Conversar", x, y, false);
        addMenuButton("Familia", x, y + 24, false);
        addMenuButton("Casa", x, y + 48, false);
        addMenuButton("Inventario", x, y + 72, true);
        addMenuButton("Seguir", x, y + 106, false);
        addMenuButton("Ficar aqui", x, y + 130, false);
    }

    private void addMenuButton(String label, int x, int y, boolean active) {
        Button button = Button.builder(Component.literal(label), pressed -> {
                    if (!active && minecraft != null && minecraft.player != null) {
                        minecraft.player.displayClientMessage(
                                Component.literal(label + ": recurso em desenvolvimento"), true);
                    }
                })
                .bounds(x, y, 72, 20)
                .build();
        button.active = !active;
        addRenderableWidget(button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        graphics.fill(left, top, left + imageWidth, top + imageHeight, 0xF0080B10);
        graphics.fill(left + 2, top + 2, left + imageWidth - 2, top + 29, PANEL_LIGHT);
        outline(graphics, left, top, imageWidth, imageHeight, BORDER);

        graphics.fill(left + 82, top + 34, left + 154, top + 113, PANEL);
        outline(graphics, left + 82, top + 34, 72, 79, 0xFF485362);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics, left + 118, top + 104, 32,
                left + 118 - mouseX, top + 66 - mouseY, menu.getNpc());

        graphics.fill(left + 158, top + 34, left + 332, top + 108, PANEL);
        outline(graphics, left + 158, top + 34, 174, 74, 0xFF485362);
        graphics.fill(left + 158, top + 118, left + 332, top + 210, PANEL);
        outline(graphics, left + 158, top + 118, 174, 92, 0xFF485362);

        for (int i = 0; i < 6; i++) {
            int slotX = switch (i) {
                case 4 -> left + 137;
                case 5 -> left + 93;
                default -> left + 115;
            };
            int slotY = switch (i) {
                case 0 -> top + 35;
                case 1 -> top + 55;
                case 2 -> top + 75;
                default -> top + 95;
            };
            outline(graphics, slotX, slotY, 18, 18, 0xFF6B7480);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, menu.getNpc().getName(), 10, 10, 0xFFF1D69A, false);
        graphics.drawString(font, "Painel do habitante", 164, 10, TEXT_MUTED, false);
        graphics.drawString(font, "Equipamento", 86, 24, 0xFFE3E7ED, false);
        graphics.drawString(font, "Pertences", 164, 40, 0xFFE3E7ED, false);
        graphics.drawString(font, "Seu inventario", 164, 114, 0xFFE3E7ED, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
