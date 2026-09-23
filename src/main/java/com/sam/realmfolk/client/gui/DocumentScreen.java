package com.sam.realmfolk.client.gui;

import com.sam.realmfolk.client.menu.DocumentMenu;
import com.sam.realmfolk.client.menu.DocumentPage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** A compact two-page medieval book for Realmfolk's data-backed documents. */
public final class DocumentScreen extends AbstractContainerScreen<DocumentMenu> {
    private static final int WIDTH = 390;
    private static final int HEIGHT = 230;
    private static final int PAGE_WIDTH = 166;
    private static final int INK = 0xFF2A2118;
    private static final int MUTED_INK = 0xFF665544;
    private static final int GOLD = 0xFFD1A74C;
    private int spread;
    private Button previous;
    private Button next;

    public DocumentScreen(DocumentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        titleLabelY = inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-2))
                .bounds(leftPos + 20, topPos + 198, 28, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(2))
                .bounds(leftPos + WIDTH - 48, topPos + 198, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fechar"), button -> onClose())
                .bounds(leftPos + WIDTH / 2 - 30, topPos + 198, 60, 20).build());
        updateButtons();
    }

    private void changePage(int amount) {
        spread = Math.max(0, Math.min(lastSpread(), spread + amount));
        updateButtons();
    }

    private int lastSpread() {
        int lastPage = Math.max(0, menu.pages().size() - 1);
        return lastPage - lastPage % 2;
    }

    private void updateButtons() {
        if (previous != null) previous.active = spread > 0;
        if (next != null) next.active = spread + 2 < menu.pages().size();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x + 4, y + 11, x + WIDTH - 4, y + HEIGHT - 6, 0xFF3A2418);
        graphics.fill(x + 9, y + 16, x + WIDTH / 2 - 4, y + 193, 0xFFF1DFB5);
        graphics.fill(x + WIDTH / 2 + 4, y + 16, x + WIDTH - 9, y + 193, 0xFFF1DFB5);
        graphics.fill(x + 13, y + 20, x + WIDTH / 2 - 8, y + 189, 0xFFE8D39F);
        graphics.fill(x + WIDTH / 2 + 8, y + 20, x + WIDTH - 13, y + 189, 0xFFE8D39F);
        graphics.fill(x + WIDTH / 2 - 4, y + 16, x + WIDTH / 2 + 4, y + 193, 0xFF5B3822);
        graphics.fill(x + WIDTH / 2 - 1, y + 18, x + WIDTH / 2 + 1, y + 191, 0xFFB58B54);
        outline(graphics, x + 9, y + 16, WIDTH / 2 - 13, 177, 0xFF8A683E);
        outline(graphics, x + WIDTH / 2 + 4, y + 16, WIDTH / 2 - 13, 177, 0xFF8A683E);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawCenteredString(font, menu.documentTitle(), WIDTH / 2, 1, GOLD);
        graphics.drawCenteredString(font, menu.subtitle(), WIDTH / 2, 10, 0xFFDEC78E);
        drawPage(graphics, spread, 20);
        drawPage(graphics, spread + 1, WIDTH / 2 + 12);
        graphics.drawCenteredString(font, (spread + 1) + " / " + menu.pages().size(), 97, 178, MUTED_INK);
        if (spread + 1 < menu.pages().size()) {
            graphics.drawCenteredString(font, (spread + 2) + " / " + menu.pages().size(), WIDTH - 97, 178, MUTED_INK);
        }
    }

    private void drawPage(GuiGraphics graphics, int index, int x) {
        if (index >= menu.pages().size()) return;
        DocumentPage page = menu.pages().get(index);
        graphics.drawCenteredString(font, page.heading(), x + PAGE_WIDTH / 2, 28, 0xFF704315);
        graphics.fill(x + 11, 40, x + PAGE_WIDTH - 11, 41, 0xFFB8955C);
        int y = 47;
        for (String raw : page.lines()) {
            if (raw.isBlank()) {
                y += 6;
                continue;
            }
            List<FormattedCharSequence> wrapped = font.split(Component.literal(raw), PAGE_WIDTH - 20);
            for (FormattedCharSequence line : wrapped) {
                if (y > 166) return;
                graphics.drawString(font, line, x + 10, y, color(raw), false);
                y += 10;
            }
            y += 2;
        }
    }

    private static int color(String line) {
        if (line.startsWith("!")) return 0xFF9B2D24;
        if (line.startsWith("+")) return 0xFF2E6B32;
        return INK;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xA0000000);
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
