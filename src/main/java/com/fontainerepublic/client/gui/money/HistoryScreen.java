package com.fontainerepublic.client.gui.money;

import com.fontainerepublic.client.gui.FrGuiUtil;
import com.fontainerepublic.client.net.ClientPresentationCache;
import com.fontainerepublic.client.view.ClientViewProjection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Transaction history screen (FR-CLIENT-001-IMPL-B2): renders the receiver's
 * own history page (first page in this release) from the non-authoritative
 * {@link ClientPresentationCache}. Purely read-only — the authoritative
 * ledger lives on the server; the "more" marker simply reflects the
 * server-reported {@code hasMore} flag.
 */
public final class HistoryScreen extends Screen {

    private static final int CARD_X = 10;
    private static final int CARD_Y = 10;
    private static final int CARD_WIDTH = 380;
    private static final int CARD_HEIGHT = 220;
    private static final int PAGE_SIZE = 12;

    private int historyPage;

    public HistoryScreen() {
        super(Component.literal("Transaction History"));
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
        ClientPresentationCache cache = ClientPresentationCache.instance();
        List<String> lines = ClientViewProjection.historyLines(cache);
        int totalPages = Math.max(1, (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (historyPage >= totalPages) {
            historyPage = totalPages - 1;
        }
        int from = Math.min(historyPage * PAGE_SIZE, lines.size());
        int to = Math.min(from + PAGE_SIZE, lines.size());
        List<String> page = lines.subList(from, to);
        String hasMore = cache.historySnapshot() != null
                && cache.historySnapshot().hasMore()
                ? " (more pages on server)"
                : "";
        FrGuiUtil.drawCard(
                graphics,
                this.font,
                CARD_X,
                CARD_Y,
                CARD_WIDTH,
                CARD_HEIGHT,
                "History — page " + (historyPage + 1) + "/" + totalPages + hasMore,
                page.isEmpty() ? List.of("No history yet.") : page,
                PAGE_SIZE
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void previousPage() {
        if (historyPage > 0) {
            historyPage--;
        }
    }

    private void nextPage() {
        List<String> lines = ClientViewProjection.historyLines(
                ClientPresentationCache.instance());
        int totalPages = Math.max(1, (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (historyPage < totalPages - 1) {
            historyPage++;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
