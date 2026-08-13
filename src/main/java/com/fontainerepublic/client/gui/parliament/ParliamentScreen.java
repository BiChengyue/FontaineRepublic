package com.fontainerepublic.client.gui.parliament;

import com.fontainerepublic.client.gui.FrGuiUtil;
import com.fontainerepublic.client.net.ClientPresentationCache;
import com.fontainerepublic.client.view.ClientViewProjection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Parliament summary screen (FR-CLIENT-001-IMPL-B3a): renders the public
 * proposal summary from the non-authoritative {@link ClientPresentationCache}.
 * Purely read-only — the authoritative legislative pipeline lives on the
 * server; this screen decides nothing and submits nothing.
 */
public final class ParliamentScreen extends Screen {

    private static final int CARD_X = 10;
    private static final int CARD_Y = 10;
    private static final int CARD_WIDTH = 380;
    private static final int CARD_HEIGHT = 220;
    private static final int PAGE_SIZE = 12;

    private int page;

    public ParliamentScreen() {
        super(Component.literal("Parliament"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> previousPage()
                )
                .bounds(centerX - 80, this.height - 40, 40, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> nextPage()
                )
                .bounds(centerX + 40, this.height - 40, 40, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("Back"),
                        button -> this.onClose()
                )
                .bounds(centerX - 20, this.height - 40, 40, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        List<String> lines = ClientViewProjection.parliamentLines(
                ClientPresentationCache.instance());
        int totalPages = Math.max(1, (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= totalPages) {
            page = totalPages - 1;
        }
        int from = Math.min(page * PAGE_SIZE, lines.size());
        int to = Math.min(from + PAGE_SIZE, lines.size());
        List<String> pageLines = lines.subList(from, to);
        FrGuiUtil.drawCard(
                graphics,
                this.font,
                CARD_X,
                CARD_Y,
                CARD_WIDTH,
                CARD_HEIGHT,
                "Parliament — proposals, page " + (page + 1) + "/" + totalPages,
                pageLines.isEmpty() ? List.of("No parliament info yet.") : pageLines,
                PAGE_SIZE
        );
        graphics.drawString(
                this.font,
                "Read-only summary of your server's public proposals.",
                CARD_X,
                CARD_Y + CARD_HEIGHT + 8,
                FrGuiUtil.COLOR_MUTED,
                false
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void previousPage() {
        if (page > 0) {
            page--;
        }
    }

    private void nextPage() {
        List<String> lines = ClientViewProjection.parliamentLines(
                ClientPresentationCache.instance());
        int totalPages = Math.max(1, (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page < totalPages - 1) {
            page++;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
