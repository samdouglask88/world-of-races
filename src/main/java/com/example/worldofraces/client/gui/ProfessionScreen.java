package com.example.worldofraces.client.gui;

import com.example.worldofraces.WorldOfRaces;
import com.example.worldofraces.client.menu.ProfessionMenu;
import com.example.worldofraces.entity.NpcBehaviorMode;
import com.example.worldofraces.profession.NpcProfession;
import com.example.worldofraces.profession.ProfessionCategory;
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

import java.util.Arrays;
import java.util.List;

public final class ProfessionScreen extends AbstractContainerScreen<ProfessionMenu> {
    private static final ResourceLocation FRAME = new ResourceLocation(WorldOfRaces.MODID, "textures/gui/npc_menu_frame.png");
    private static final int W = 640, H = 360, TW = 1672, TH = 941;
    private static final int GOLD = 0xFFF0D08A, TEXT = 0xFFE8E2D8, MUTED = 0xFFAAA79F;
    private static final List<NpcProfession> PROFESSIONS = Arrays.stream(NpcProfession.values())
            .filter(value -> value != NpcProfession.NONE).toList();

    private float scale = 1;
    private int firstIndex;

    public ProfessionScreen(ProfessionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = W;
        imageHeight = H;
        titleLabelY = inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - W) / 2;
        topPos = (height - H) / 2;
        scale = Math.min(1, Math.min((width - 16F) / W, (height - 16F) / H));

        nav("Conversar", 100, Items.PAPER.getDefaultInstance(), ProfessionMenu.TALK, false);
        nav("Família", 129, Items.PLAYER_HEAD.getDefaultInstance(), ProfessionMenu.FAMILY, false);
        nav("Casa", 158, Items.OAK_DOOR.getDefaultInstance(), ProfessionMenu.HOUSE, false);
        nav("Profissões", 187, Items.IRON_PICKAXE.getDefaultInstance(), -1, true);
        nav("Equipamento", 216, Items.IRON_CHESTPLATE.getDefaultInstance(), ProfessionMenu.EQUIPMENT, false);
        nav("Comércio", 245, Items.EMERALD.getDefaultInstance(), ProfessionMenu.TRADE, false);

        String follow = menu.snapshot().behavior() == NpcBehaviorMode.FOLLOW ? "Parar de seguir" : "Seguir";
        String stay = menu.snapshot().behavior() == NpcBehaviorMode.STAY ? "Pode andar" : "Ficar aqui";
        nav(follow, 279, Items.LEAD.getDefaultInstance(), ProfessionMenu.FOLLOW, false);
        nav(stay, 308, Items.COMPASS.getDefaultInstance(), ProfessionMenu.STAY, false);

        int categoryX = 194;
        for (ProfessionCategory category : ProfessionCategory.values()) {
            int width = switch (category) { case PRODUCTION -> 62; case GATHERING -> 52; case SERVICES -> 58; case MILITARY -> 51; };
            add(leftPos + categoryX, topPos + 96, width, 22, category.displayName, ItemStack.EMPTY,
                    () -> jumpTo(category), menu.snapshot().selected().category == category, false);
            categoryX += width + 4;
        }

        for (int visible = 0; visible < 9; visible++) {
            int index = firstIndex + visible;
            if (index >= PROFESSIONS.size()) break;
            NpcProfession profession = PROFESSIONS.get(index);
            int column = visible % 3, row = visible / 3;
            add(leftPos + 198 + column * 82, topPos + 125 + row * 58, 76, 53,
                    profession.name, profession.icon(), () -> send(profession.ordinal()),
                    profession == menu.snapshot().selected(), true);
        }

        var snapshot = menu.snapshot();
        if (snapshot.current() == NpcProfession.NONE) {
            ProfessionButton assign = add(leftPos + 454, topPos + 281, 110, 25,
                    "Atribuir profissão", ItemStack.EMPTY, () -> send(ProfessionMenu.ASSIGN), false, false);
            assign.active = snapshot.selected().implemented;
        } else {
            add(leftPos + 454, topPos + 281, 110, 25, "Remover profissão", ItemStack.EMPTY,
                    () -> send(ProfessionMenu.REMOVE), false, false);
        }
        add(leftPos + 545, topPos + 37, 26, 24, "X", ItemStack.EMPTY, this::onClose, false, false);
    }

    private void nav(String text, int y, ItemStack icon, int action, boolean selected) {
        add(leftPos + 80, topPos + y, 104, 25, text, icon,
                () -> { if (action >= 0) send(action); }, selected, false);
    }

    private void jumpTo(ProfessionCategory category) {
        for (int i = 0; i < PROFESSIONS.size(); i++) {
            if (PROFESSIONS.get(i).category == category) {
                firstIndex = Math.min(i, Math.max(0, PROFESSIONS.size() - 9));
                rebuildWidgets();
                return;
            }
        }
    }

    private ProfessionButton add(int x, int y, int width, int height, String text, ItemStack icon,
                                 Runnable action, boolean selected, boolean card) {
        ProfessionButton button = new ProfessionButton(x, y, width, height, Component.literal(text),
                icon, action, selected, card);
        addRenderableWidget(button);
        return button;
    }

    private void send(int action) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(FRAME, leftPos, topPos, W, H, 0, 0, TW, TH, TW, TH);
        panel(graphics, 194, 92, 252, 218);
        panel(graphics, 454, 92, 110, 218);

        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + 145, topPos + 40, 0);
        graphics.pose().scale(2.25F, 2.25F, 1);
        graphics.renderItem(Items.PLAYER_HEAD.getDefaultInstance(), 0, 0);
        graphics.pose().popPose();

        ItemStack icon = menu.snapshot().selected().icon();
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos + 485, topPos + 115, 0);
        graphics.pose().scale(3F, 3F, 1);
        graphics.renderItem(icon, 0, 0);
        graphics.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var snapshot = menu.snapshot();
        NpcProfession selected = snapshot.selected();
        String name = menu.npc().getName().getString();
        graphics.drawString(font, name, 190, 41, GOLD, true);
        graphics.drawString(font, "Humano  |  " + (snapshot.current() == NpcProfession.NONE
                ? "Sem profissão" : snapshot.current().name), 190, 59, TEXT, false);

        graphics.drawCenteredString(font, selected.name, 509, 98, GOLD);
        graphics.drawString(font, "Nível " + (snapshot.current() == selected ? snapshot.level() : 1), 462, 169, GOLD, false);
        int experience = snapshot.current() == selected ? snapshot.experience() : 0;
        int needed = snapshot.current() == selected ? snapshot.needed() : 100;
        graphics.drawString(font, "Experiência " + experience + " / " + needed, 462, 183, TEXT, false);
        graphics.fill(462, 195, 556, 201, 0xFF0B0D10);
        int progress = needed <= 0 ? 94 : Math.round(94 * Math.min(1F, experience / (float) needed));
        graphics.fill(463, 196, 463 + progress, 200, 0xFFD5A83B);
        graphics.drawWordWrap(font, Component.literal(selected.description), 462, 207, 94, TEXT);
        drawFitted(graphics, "Estação: " + selected.station(), 462, 238, 94, MUTED, false);
        drawFitted(graphics, "Ferramenta: " + selected.tool(), 462, 250, 94, MUTED, false);
        drawFitted(graphics, "Aptidão: " + translateAptitude(snapshot.aptitude()), 462, 262, 94,
                aptitudeColor(snapshot.aptitude()), false);
        drawFitted(graphics, snapshot.status(), 460, 271, 98,
                snapshot.active() ? 0xFF55DD55 : GOLD, true);
        graphics.drawCenteredString(font, "Escolha uma profissão compatível com as habilidades do habitante",
                W / 2, 326, MUTED);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double logicalX = toX(mouseX), logicalY = toY(mouseY);
        if (logicalX >= leftPos + 194 && logicalX <= leftPos + 446
                && logicalY >= topPos + 120 && logicalY <= topPos + 310) {
            int previous = firstIndex;
            firstIndex = Math.max(0, Math.min(Math.max(0, PROFESSIONS.size() - 9),
                    firstIndex + (delta < 0 ? 3 : -3)));
            if (previous != firstIndex) rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(logicalX, logicalY, delta);
    }

    private static String translateAptitude(String value) {
        return switch (value) { case "LOW" -> "Baixa"; case "HIGH" -> "Alta"; default -> "Média"; };
    }

    private static int aptitudeColor(String value) {
        return switch (value) { case "LOW" -> 0xFFE05B5B; case "HIGH" -> 0xFF55DD55; default -> GOLD; };
    }

    private void drawFitted(GuiGraphics graphics, String text, int x, int y, int available,
                            int color, boolean centered) {
        float fitted = Math.min(1F, available / (float) Math.max(1, font.width(text)));
        float offset = centered ? (available - font.width(text) * fitted) / 2F : 0;
        graphics.pose().pushPose();
        graphics.pose().translate(x + offset, y + (8 - 8 * fitted) / 2F, 0);
        graphics.pose().scale(fitted, fitted, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        x += leftPos; y += topPos;
        graphics.fill(x, y, x + width, y + height, 0xE8191B1E);
        outline(graphics, x, y, width, height, 0xFF8A6B32);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x99000000);
        int logicalX = (int) Math.round(toX(mouseX)), logicalY = (int) Math.round(toY(mouseY));
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2F, height / 2F, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(-width / 2F, -height / 2F, 0);
        super.render(graphics, logicalX, logicalY, partialTick);
        renderTooltip(graphics, logicalX, logicalY);
        graphics.pose().popPose();
    }

    private double toX(double value) { return width / 2D + (value - width / 2D) / scale; }
    private double toY(double value) { return height / 2D + (value - height / 2D) / scale; }
    @Override public boolean mouseClicked(double x, double y, int button) { return super.mouseClicked(toX(x), toY(y), button); }
    @Override public boolean mouseReleased(double x, double y, int button) { return super.mouseReleased(toX(x), toY(y), button); }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    private static final class ProfessionButton extends AbstractButton {
        private final ItemStack icon;
        private final Runnable action;
        private final boolean selected;
        private final boolean card;

        private ProfessionButton(int x, int y, int width, int height, Component text, ItemStack icon,
                                 Runnable action, boolean selected, boolean card) {
            super(x, y, width, height, text);
            this.icon = icon;
            this.action = action;
            this.selected = selected;
            this.card = card;
        }

        @Override public void onPress() { action.run(); }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int background = !active ? 0xE51A1A1A : selected ? 0xEE765115
                    : isHoveredOrFocused() ? 0xEE514124 : 0xE52A2B2D;
            int border = !active ? 0xFF4A4A4A : selected ? 0xFFFFC44F
                    : isHoveredOrFocused() ? 0xFFD2A34C : 0xFF756F65;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
            outline(graphics, getX(), getY(), width, height, border);
            var font = Minecraft.getInstance().font;
            if (card) {
                graphics.pose().pushPose();
                graphics.pose().translate(getX() + width / 2F - 12, getY() + 4, 0);
                graphics.pose().scale(1.5F, 1.5F, 1);
                graphics.renderItem(icon, 0, 0);
                graphics.pose().popPose();
                drawFitted(graphics, font, getMessage(), getX() + 3, getY() + 39, width - 6,
                        selected ? GOLD : TEXT);
                return;
            }
            if (!icon.isEmpty()) graphics.renderItem(icon, getX() + 4, getY() + (height - 16) / 2);
            int start = icon.isEmpty() ? 4 : 24;
            drawFitted(graphics, font, getMessage(), getX() + start, getY() + (height - 8) / 2,
                    width - start - 4, active ? (selected ? GOLD : TEXT) : 0xFF777777);
        }

        private static void drawFitted(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                       Component text, int x, int y, int available, int color) {
            float textScale = Math.min(1F, available / (float) Math.max(1, font.width(text)));
            graphics.pose().pushPose();
            graphics.pose().translate(x, y + (8 - 8 * textScale) / 2, 0);
            graphics.pose().scale(textScale, textScale, 1);
            graphics.drawString(font, text, 0, 0, color, false);
            graphics.pose().popPose();
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
