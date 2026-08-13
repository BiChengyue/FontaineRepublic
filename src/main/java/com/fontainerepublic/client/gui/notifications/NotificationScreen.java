package com.fontainerepublic.client.gui.notifications;

import com.fontainerepublic.client.gui.FrGuiUtil;
import com.fontainerepublic.client.net.ClientPresentationCache;
import com.fontainerepublic.client.view.ClientViewProjection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Notification view (FR-CLIENT-001-IMPL-B): a read-only, paginated projection
 * of {@link ClientPresentationCache#notificationEntries()}. The cache holds
 * the bounded pending-notification summary delivered on login; nothing here
 * acknowledges or mutates server state.
 */
public final class NotificationScreen extends Screen {

    private static final int PANEL_X = 30;
    private static final int PANEL_Y = 20;
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 200;
    private static final int PAGE_SIZE = 12;

    private int page;
    private Button previousPageButton;
    private Button nextPageButton;

    public NotificationScreen() {
        super(Component.literal("Notifications"));
    }

    @Override
    protected void init() {
        previousPageButton = addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> previousPage()
                )
                .bounds(PANEL_X + 4, PANEL_Y + PANEL_HEIGHT - 22, 30, 18)
                .build());
        nextPageButton = addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> nextPage()
                )
                .bounds(PANEL_X + PANEL_WIDTH - 34, PANEL_Y + PANEL_HEIGHT - 22, 30, 18)
                .build());
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
        List<String> lines = ClientViewProjection.notificationLines(
                ClientPresentationCache.instance());
        int totalPages = Math.max(1, (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= totalPages) {
            page = totalPages - 1;
        }
        int from = Math.min(page * PAGE_SIZE, lines.size());
        int to = Math.min(from + PAGE_SIZE, lines.size());
        FrGuiUtil.drawCard(
                graphics,
                this.font,
                PANEL_X,
                PANEL_Y,
                PANEL_WIDTH,
                PANEL_HEIGHT,
                "Notifications — page " + (page + 1) + "/" + totalPages,
                lines.isEmpty() ? List.of("No pending notifications.") : lines.subList(from, to),
                PAGE_SIZE
        );
        previousPageButton.active = page > 0;
        nextPageButton.active = page < totalPages - 1;
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void previousPage() {
        if (page > 0) {
            page--;
        }
    }

    private void nextPage() {
        List<String> lines = ClientViewProjection.notificationLines(
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
