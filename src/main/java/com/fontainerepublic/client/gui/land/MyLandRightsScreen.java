package com.fontainerepublic.client.gui.land;

import com.fontainerepublic.client.gui.FrGuiUtil;
import com.fontainerepublic.client.landrights.ClientMyLandRightsCache;
import com.fontainerepublic.client.landrights.ClientMyLandRightsSender;
import com.fontainerepublic.client.view.ClientViewProjection;
import com.fontainerepublic.common.landrights.MyLandRightsPagePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * "我的地块" personal land usage-rights view (FR-LAND-002-A §5): renders the
 * authenticated player's <em>own</em> current land rights as a bounded,
 * read-only paged list, queried on demand from the server.
 *
 * <p>The screen issues a first-page request on open and on refresh, and a
 * next-page request carrying the exclusive cursor + returned store revision.
 * Responses are accepted only when they match the current request id (stale /
 * lower-revision responses are discarded by {@link ClientMyLandRightsCache}).
 * All data is presentation-only; the server stays authoritative. Closing the
 * screen clears the temporary page state.</p>
 */
public final class MyLandRightsScreen extends Screen {

    private static final int CARD_X = 10;
    private static final int CARD_Y = 10;
    private static final int CARD_WIDTH = 380;
    private static final int CARD_HEIGHT = 220;
    private static final int LINE_HEIGHT = 10;

    /** Client default page size (matches the server default, FR-LAND-002-A §3.2). */
    private static final int DEFAULT_LIMIT = 12;

    public MyLandRightsScreen() {
        super(Component.literal("My Land Rights"));
    }

    @Override
    protected void init() {
        ClientMyLandRightsCache.instance().clear();
        ClientMyLandRightsSender.requestPage(null, 0L, DEFAULT_LIMIT);
        int centerX = this.width / 2;
        addRenderableWidget(Button.builder(
                        Component.literal("Refresh"),
                        button -> refresh()
                )
                .bounds(centerX - 130, this.height - 40, 60, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> previousPage()
                )
                .bounds(centerX - 60, this.height - 40, 40, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> nextPage()
                )
                .bounds(centerX - 10, this.height - 40, 40, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("Back"),
                        button -> this.onClose()
                )
                .bounds(centerX + 40, this.height - 40, 40, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        MyLandRightsPagePacket page = ClientMyLandRightsCache.instance().page();
        String title;
        List<String> lines;
        if (page == null) {
            title = "My land rights — waiting";
            lines = List.of("Requesting your current usage rights\u2026");
        } else {
            title = pageTitle(page);
            lines = ClientViewProjection.myLandRightsLines(
                    ClientMyLandRightsCache.instance());
        }
        FrGuiUtil.drawCard(
                graphics,
                this.font,
                CARD_X,
                CARD_Y,
                CARD_WIDTH,
                CARD_HEIGHT,
                title,
                lines,
                CARD_HEIGHT / LINE_HEIGHT
        );
        graphics.drawString(
                this.font,
                "Your active rights only; server-authoritative.",
                CARD_X,
                CARD_Y + CARD_HEIGHT + 8,
                FrGuiUtil.COLOR_MUTED,
                false
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void refresh() {
        // Fresh first page from store revision 0.
        ClientMyLandRightsCache.instance().clear();
        ClientMyLandRightsSender.requestPage(null, 0L, DEFAULT_LIMIT);
    }

    private void previousPage() {
        // Bounded first-page-only convenience view: no backward paging of the
        // singular accepted page in this release (re-open to go back).
        refresh();
    }

    private void nextPage() {
        MyLandRightsPagePacket page = ClientMyLandRightsCache.instance().page();
        if (page == null || !"OK".equals(page.status()) || !page.hasMore()) {
            return;
        }
        if (page.nextAfterParcelId().isEmpty()) {
            return;
        }
        ClientMyLandRightsSender.requestPage(
                page.nextAfterParcelId().get(),
                page.storeRevision(),
                DEFAULT_LIMIT
        );
    }

    private static String pageTitle(MyLandRightsPagePacket page) {
        return switch (page.status()) {
            case "RESET_REQUIRED" -> "My land rights — revision changed, refresh";
            case "INVALID_REQUEST" -> "My land rights — invalid request";
            case "UNAVAILABLE" -> "My land rights — unavailable";
            default -> "My land rights — page at revision " + page.storeRevision()
                    + (page.hasMore() ? " (more\u2026)" : "");
        };
    }

    @Override
    public void onClose() {
        // Clear the temporary page state whenever this screen closes (Back /
        // Esc / parent navigation) so a later re-open always starts from a fresh
        // first-page request at store revision 0. Disconnect cleanup is handled
        // separately by the logout listener; this only covers in-screen closes.
        ClientMyLandRightsCache.instance().clear();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
