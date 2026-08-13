package com.fontainerepublic.client.gui.guide;

import com.fontainerepublic.client.gui.FrGuiUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * In-client guidance page (FR-CLIENT-001-IMPL-B): static display text linking
 * the player to the server command surface ({@code /fr help}) and the player
 * guide. Purely informational — no state, no mutations.
 */
public final class GuideScreen extends Screen {

    private static final int PANEL_X = 30;
    private static final int PANEL_Y = 20;
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 200;

    private static final List<String> GUIDE_LINES = List.of(
            "FontaineRepublic — FR Client guide",
            "",
            "Commands:",
            "  /fr help            module overview",
            "  /fr money balance   your balance",
            "  /fr money pay <target> <amount> [memo]",
            "  /fr money history   your transactions",
            "  /fr citizen info    your citizen status",
            "",
            "Balances and flows shown here are display",
            "snapshots from the server; the server always",
            "re-validates every action you submit."
    );

    public GuideScreen() {
        super(Component.literal("Guide"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                        Component.literal("Back"),
                        button -> this.onClose()
                )
                .bounds(PANEL_X + PANEL_WIDTH - 70, PANEL_Y + PANEL_HEIGHT + 6, 70, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        FrGuiUtil.drawCard(
                graphics,
                this.font,
                PANEL_X,
                PANEL_Y,
                PANEL_WIDTH,
                PANEL_HEIGHT,
                "Guide",
                GUIDE_LINES,
                18
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
