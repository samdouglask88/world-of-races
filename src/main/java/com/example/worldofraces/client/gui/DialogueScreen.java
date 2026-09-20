package com.example.worldofraces.client.gui;

import com.example.worldofraces.entity.RaceEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DialogueScreen extends Screen {

    private final RaceEntity npc;
    private static final int WIDTH = 200;
    private static final int HEIGHT = 120;

    public DialogueScreen(RaceEntity npc) {
        super(Component.literal("Dialogo"));
        this.npc = npc;
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        this.addRenderableWidget(
            Button.builder(Component.literal("Fechar"), button -> this.onClose())
                .bounds(left + WIDTH / 2 - 40, top + HEIGHT - 30, 80, 20)
                .build()
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int left = (this.width - WIDTH) / 2;
        int top = (this.height - HEIGHT) / 2;

        guiGraphics.fill(left, top, left + WIDTH, top + HEIGHT, 0xCC000000);

        String npcName = this.npc.getName().getString();
        guiGraphics.drawCenteredString(this.font, npcName, this.width / 2, top + 15, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, "Ola, viajante!", this.width / 2, top + 35, 0xFFFFFF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}