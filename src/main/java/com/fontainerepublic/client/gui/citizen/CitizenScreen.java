package com.fontainerepublic.client.gui.citizen;

import com.fontainerepublic.client.gui.FrGuiUtil;
import com.fontainerepublic.client.net.ClientPresentationCache;
import com.fontainerepublic.client.view.ClientViewProjection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Citizen identity card screen (FR-CLIENT-001-IMPL-B2): renders the
 * receiver's own citizen snapshot from the non-authoritative
 * {@link ClientPresentationCache}. Purely read-only — nothing here asserts
 * presence, decides state, or submits anything; the authoritative card lives
 * on the server.
 */
public final class CitizenScreen extends Screen {

    private static final int CARD_X = 10;
    private static final int CARD_Y = 10;
    private static final int CARD_WIDTH = 260;
    private static final int CARD_HEIGHT = 96;

    public CitizenScreen() {
        super(Component.literal("Citizen Card"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        addRenderableWidget(Button.builder(
                        Component.literal("Back"),
                        button -> this.onClose()
                )
                .bounds(centerX - 60, this.height - 40, 120, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        List<String> lines = ClientViewProjection.citizenLines(
                ClientPresentationCache.instance());
        FrGuiUtil.drawCard(
                graphics,
                this.font,
                CARD_X,
                CARD_Y,
                CARD_WIDTH,
                CARD_HEIGHT,
                "Citizen card (server-issued)",
                lines,
                6
        );
        graphics.drawString(
                this.font,
                "This card is a read-only presentation of your server record.",
                CARD_X,
                CARD_Y + CARD_HEIGHT + 8,
                FrGuiUtil.COLOR_MUTED,
                false
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
