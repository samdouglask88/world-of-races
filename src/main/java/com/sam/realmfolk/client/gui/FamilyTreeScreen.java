package com.sam.realmfolk.client.gui;

import com.sam.realmfolk.Realmfolk;
import com.sam.realmfolk.client.menu.FamilyTreeMenu;
import com.sam.realmfolk.client.menu.FamilyTreeSnapshot;
import com.mojang.blaze3d.systems.RenderSystem;
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

public final class FamilyTreeScreen extends AbstractContainerScreen<FamilyTreeMenu> {
    private static final ResourceLocation FRAME = new ResourceLocation(
            Realmfolk.MODID, "textures/gui/family_tree_frame.png");
    private static final int TEXTURE_WIDTH = 1671;
    private static final int TEXTURE_HEIGHT = 941;
    private static final int PANEL_WIDTH = 640;
    private static final int PANEL_HEIGHT = 360;
    private static final int SCREEN_MARGIN = 8;
    private static final int GOLD = 0xFFF0C66B;
    private static final int TEXT = 0xFFE8E2D8;
    private float panelScale = 1.0F;

    public FamilyTreeScreen(FamilyTreeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.titleLabelY = -1000;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
        this.panelScale = Math.min(1.0F, Math.min(
                (this.width - SCREEN_MARGIN * 2.0F) / this.imageWidth,
                (this.height - SCREEN_MARGIN * 2.0F) / this.imageHeight));

        // family_tree_frame has a narrower sidebar than npc_menu_frame.
        int x = leftPos + 76;
        int y = topPos + 92;
        addSideButton("Conversar", x, y, false, FamilyTreeMenu.TALK_ACTION, 27);
        addSideButton("Família", x, y + 32, true, -1, 27);
        addSideButton("Casa", x, y + 64, false, FamilyTreeMenu.HOUSE_ACTION, 27);
        addSideButton("Profissões", x, y + 96, false, FamilyTreeMenu.PROFESSION_ACTION, 27);
        addSideButton("Equipamento", x, y + 128, false, FamilyTreeMenu.EQUIPMENT_TAB, 27);
        addSideButton("Seguir", x, topPos + 266, false, FamilyTreeMenu.FOLLOW_ACTION, 22);
        addSideButton("Ficar aqui", x, topPos + 290, false, FamilyTreeMenu.STAY_ACTION, 22);

        FamilyTreeSnapshot tree = menu.getSnapshot();
        addPerson(tree.father(), "Pai", 0, 194, 87, 116, 54, false);
        addPerson(tree.mother(), "Mae", 1, 330, 87, 116, 54, false);
        addPerson(tree.focus(), "Em foco", -1, 200, 166, 125, 58, true);
        addPerson(tree.spouse(), "Conjuge", 2, 330, 166, 116, 58, false);
        List<FamilyTreeSnapshot.Node> children = tree.children();
        for (int i = 0; i < children.size(); i++) {
            String relation = children.get(i).gender().equals("MALE") ? "Filho" : "Filha";
            addPerson(children.get(i), relation, 3 + i, 194 + i * 63, 246, 58, 54, false);
        }

        if (tree.childPage() > 0) addSmallButton("<", 270, 301, FamilyTreeMenu.PREVIOUS_PAGE);
        if (tree.childPage() + 1 < tree.childPageCount()) {
            addSmallButton(">", 352, 301, FamilyTreeMenu.NEXT_PAGE);
        }
        addRenderableWidget(new TreeButton(leftPos + 466, topPos + 281, 98, 28,
                Component.literal("Ver perfil"), Items.PLAYER_HEAD.getDefaultInstance(), button -> {
                    sendButton(FamilyTreeMenu.PROFILE_ACTION);
                }, false));
        addRenderableWidget(new TreeButton(leftPos + 535, topPos + 35, 26, 24,
                Component.literal("X"), button -> onClose(), false));
    }

    private void addSideButton(String label, int x, int y, boolean selected, int action, int height) {
        ItemStack icon = switch (label) {
            case "Conversar" -> Items.PAPER.getDefaultInstance();
            case "Família" -> Items.PLAYER_HEAD.getDefaultInstance();
            case "Casa" -> Items.OAK_DOOR.getDefaultInstance();
            case "Profissões" -> Items.IRON_PICKAXE.getDefaultInstance();
            case "Equipamento" -> Items.IRON_CHESTPLATE.getDefaultInstance();
            case "Seguir" -> Items.LEAD.getDefaultInstance();
            default -> Items.COMPASS.getDefaultInstance();
        };
        addRenderableWidget(new TreeButton(x, y, 78, height, Component.literal(label), icon, button -> {
            if (action >= 0) sendButton(action);
            else if (!selected && minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(label + ": recurso em desenvolvimento"), true);
            }
        }, selected));
    }

    private void addPerson(FamilyTreeSnapshot.Node node, String relation, int action, int x, int y,
                           int width, int height, boolean selected) {
        if (node == null) return;
        addRenderableWidget(new PersonCard(leftPos + x, topPos + y, width, height, node, relation,
                button -> { if (action >= 0) sendButton(action); }, selected));
    }

    private void addSmallButton(String text, int x, int y, int action) {
        addRenderableWidget(new TreeButton(leftPos + x, topPos + y, 18, 13,
                Component.literal(text), button -> sendButton(action), false));
    }

    private void sendButton(int action) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(FRAME, leftPos, topPos, imageWidth, imageHeight,
                0.0F, 0.0F, TEXTURE_WIDTH, TEXTURE_HEIGHT, TEXTURE_WIDTH, TEXTURE_HEIGHT);
        // Keep each content area opaque and clipped to its own frame while the menu refreshes.
        graphics.fill(leftPos + 166, topPos + 89, leftPos + 459, topPos + 313, 0xE8181A1D);
        graphics.fill(leftPos + 464, topPos + 89, leftPos + 566, topPos + 313, 0xE8181A1D);
        outline(graphics, leftPos + 165, topPos + 88, 295, 226, 0xFF8A6B32);
        outline(graphics, leftPos + 463, topPos + 88, 104, 226, 0xFF8A6B32);
        FamilyTreeSnapshot tree = menu.getSnapshot();
        int gold = 0xFFD0A347;

        if (tree.father() != null && tree.mother() != null) {
            line(graphics, leftPos + 252, topPos + 150, leftPos + 388, topPos + 150, gold);
        }
        if (tree.father() != null) {
            line(graphics, leftPos + 252, topPos + 141, leftPos + 252, topPos + 150, gold);
            line(graphics, leftPos + 252, topPos + 150, leftPos + 320, topPos + 150, gold);
        }
        if (tree.mother() != null) {
            line(graphics, leftPos + 388, topPos + 141, leftPos + 388, topPos + 150, gold);
            line(graphics, leftPos + 320, topPos + 150, leftPos + 388, topPos + 150, gold);
        }
        if (tree.father() != null || tree.mother() != null) {
            line(graphics, leftPos + 320, topPos + 150, leftPos + 320, topPos + 165, gold);
        }
        if (tree.spouse() != null) {
            line(graphics, leftPos + 325, topPos + 195, leftPos + 330, topPos + 195, gold);
        }
        if (!tree.children().isEmpty()) {
            int descentX = tree.spouse() == null ? 262 : 327;
            line(graphics, leftPos + descentX, topPos + 225, leftPos + descentX, topPos + 237, gold);
            int firstCenter = 223;
            int lastCenter = 223 + (tree.children().size() - 1) * 63;
            line(graphics, leftPos + Math.min(firstCenter, descentX), topPos + 237,
                    leftPos + Math.max(lastCenter, descentX), topPos + 237, gold);
            for (int i = 0; i < tree.children().size(); i++) {
                int childX = firstCenter + i * 63;
                line(graphics, leftPos + childX, topPos + 237, leftPos + childX, topPos + 245, gold);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        FamilyTreeSnapshot tree = menu.getSnapshot();
        String heading = tree.focus().house().equals("Sem Casa")
                ? "Família de " + tree.focus().name()
                : tree.focus().house();
        graphics.drawCenteredString(font, heading, imageWidth / 2, 36, GOLD);
        graphics.drawCenteredString(font, "Linhagem humana", imageWidth / 2, 52, TEXT);

        int detailX = 467;
        graphics.drawString(font, trim(tree.focus().name(), 88), detailX, 87, GOLD, false);
        graphics.drawString(font, tree.focus().house(), detailX, 102, 0xFFB7B1A7, false);

        graphics.pose().pushPose();
        graphics.pose().translate(490, 121, 0);
        graphics.pose().scale(3.0F, 3.0F, 1.0F);
        graphics.renderItem(Items.PLAYER_HEAD.getDefaultInstance(), 0, 0);
        graphics.pose().popPose();

        graphics.drawString(font, translateStage(tree.focus().lifeStage()), detailX, 199, TEXT, false);
        graphics.drawString(font, tree.focus().alive() ? "Vivo" : "Falecido", detailX, 214,
                tree.focus().alive() ? 0xFF65C466 : 0xFFAAAAAA, false);
        graphics.drawString(font, tree.focus().gender().equals("MALE") ? "Masculino" : "Feminino",
                detailX, 229, TEXT, false);
        graphics.drawString(font, "Filhos: " + tree.totalChildren(), detailX, 244, TEXT, false);

        boolean hasNoRelatives = tree.father() == null && tree.mother() == null
                && tree.spouse() == null && tree.totalChildren() == 0;
        if (hasNoRelatives) {
            graphics.drawCenteredString(font, "Nenhum parente conhecido", 302, 235, 0xFFAAA79F);
        }

        String page = tree.totalChildren() == 0 ? "Filhos: 0"
                : "Filhos: " + (tree.childPage() + 1) + "/" + tree.childPageCount();
        graphics.drawCenteredString(font, page, 320, 303, 0xFFB7B1A7);
        graphics.drawCenteredString(font, "Clique em um parente para navegar", imageWidth / 2, 329, 0xFFAAA79F);
    }

    private String trim(String text, int width) {
        return font.plainSubstrByWidth(text, width);
    }

    private static String translateStage(String stage) {
        return switch (stage) {
            case "BABY" -> "Bebe";
            case "CHILD" -> "Crianca";
            case "TEENAGER" -> "Adolescente";
            case "ELDER" -> "Idoso";
            default -> "Adulto";
        };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int logicalX = (int) Math.round(toLogicalX(mouseX));
        int logicalY = (int) Math.round(toLogicalY(mouseY));
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2.0F, height / 2.0F, 0.0F);
        graphics.pose().scale(panelScale, panelScale, 1.0F);
        graphics.pose().translate(-width / 2.0F, -height / 2.0F, 0.0F);
        super.render(graphics, logicalX, logicalY, partialTick);
        renderTooltip(graphics, logicalX, logicalY);
        graphics.pose().popPose();
    }

    private double toLogicalX(double value) {
        return width / 2.0D + (value - width / 2.0D) / panelScale;
    }

    private double toLogicalY(double value) {
        return height / 2.0D + (value - height / 2.0D) / panelScale;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return super.mouseClicked(toLogicalX(x), toLogicalY(y), button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(toLogicalX(x), toLogicalY(y), button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        return super.mouseDragged(toLogicalX(x), toLogicalY(y), button,
                dragX / panelScale, dragY / panelScale);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        return super.mouseScrolled(toLogicalX(x), toLogicalY(y), delta);
    }

    private static void line(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
    }

    private static class TreeButton extends AbstractButton {
        private final OnPress action;
        protected final boolean selected;
        private final ItemStack icon;

        private TreeButton(int x, int y, int width, int height, Component text,
                           OnPress action, boolean selected) {
            this(x, y, width, height, text, ItemStack.EMPTY, action, selected);
        }

        private TreeButton(int x, int y, int width, int height, Component text, ItemStack icon,
                           OnPress action, boolean selected) {
            super(x, y, width, height, text);
            this.action = action;
            this.selected = selected;
            this.icon = icon;
        }

        @Override
        public void onPress() { action.onPress(this); }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int background = selected ? 0xEE765115 : isHoveredOrFocused() ? 0xEE42403B : 0xDD292A2B;
            int border = selected ? 0xFFFFC44F : isHoveredOrFocused() ? 0xFFD2A34C : 0xFF69645B;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
            outline(graphics, getX(), getY(), width, height, border);
            if (!icon.isEmpty()) graphics.renderItem(icon, getX() + 4, getY() + (height - 16) / 2);
            var font = Minecraft.getInstance().font;
            int availableWidth = icon.isEmpty() ? width - 8 : width - 27;
            float textScale = Math.min(1.0F, availableWidth / (float) Math.max(1, font.width(getMessage())));
            float renderedWidth = font.width(getMessage()) * textScale;
            float textX = icon.isEmpty() ? getX() + (width - renderedWidth) / 2.0F : getX() + 23;
            float textY = getY() + (height - 8.0F * textScale) / 2.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(textX, textY, 0.0F);
            graphics.pose().scale(textScale, textScale, 1.0F);
            graphics.drawString(font, getMessage(), 0, 0, selected ? GOLD : TEXT, false);
            graphics.pose().popPose();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }

        @FunctionalInterface
        protected interface OnPress {
            void onPress(TreeButton button);
        }
    }

    private static final class PersonCard extends TreeButton {
        private final FamilyTreeSnapshot.Node person;
        private final String relation;

        private PersonCard(int x, int y, int width, int height, FamilyTreeSnapshot.Node person,
                           String relation, OnPress action, boolean selected) {
            super(x, y, width, height, Component.literal(person.name()), action, selected);
            this.person = person;
            this.relation = relation;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int background = selected ? 0xF0342A18 : isHoveredOrFocused() ? 0xF0444038 : 0xEE17191C;
            int border = selected || isHoveredOrFocused() ? 0xFFFFC44F : 0xFF9A7434;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, background);
            outline(graphics, getX(), getY(), width, height, border);
            if (selected) outline(graphics, getX() + 2, getY() + 2, width - 4, height - 4, 0xFFD69A2D);
            var font = Minecraft.getInstance().font;
            int portraitSize = width < 90 ? 20 : Math.min(26, height - 12);
            int portraitX = getX() + 5;
            int portraitY = getY() + (height - portraitSize) / 2;
            graphics.fill(portraitX - 1, portraitY - 1, portraitX + portraitSize + 1,
                    portraitY + portraitSize + 1, 0xFF8F682B);
            graphics.fill(portraitX, portraitY, portraitX + portraitSize, portraitY + portraitSize, 0xFF111317);
            graphics.pose().pushPose();
            graphics.pose().translate(portraitX + 1, portraitY + 1, 0.0F);
            graphics.pose().scale(1.5F, 1.5F, 1.0F);
            graphics.renderItem(Items.PLAYER_HEAD.getDefaultInstance(), 0, 0);
            graphics.pose().popPose();

            int textX = portraitX + portraitSize + 5;
            int textWidth = getX() + width - textX - 4;
            graphics.drawString(font, font.plainSubstrByWidth(person.name(), textWidth),
                    textX, getY() + 5, GOLD, false);
            graphics.drawString(font, font.plainSubstrByWidth(relation, textWidth),
                    textX, getY() + 17, TEXT, false);
            graphics.drawString(font, font.plainSubstrByWidth(translateStage(person.lifeStage()), textWidth),
                    textX, getY() + 29, 0xFFAAA79F, false);
            graphics.drawString(font, person.alive() ? "Vivo" : "Falecido",
                    textX, getY() + 41, person.alive() ? 0xFF65C466 : 0xFF999999, false);
        }
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }
}
