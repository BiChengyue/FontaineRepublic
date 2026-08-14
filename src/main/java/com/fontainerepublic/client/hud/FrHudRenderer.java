package com.fontainerepublic.client.hud;

import com.fontainerepublic.client.gui.FrGuiUtil;
import com.fontainerepublic.client.net.ClientPresentationCache;
import com.fontainerepublic.client.view.ClientViewProjection;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Balance + notification HUD (FR-CLIENT-001-IMPL-B). Renders a compact card
 * in the top-left corner while in-game: the balance line from the
 * non-authoritative presentation cache and, when present, a bounded pending
 * notification summary. Display-only; the cache is never authoritative.
 */
public final class FrHudRenderer {

    private static final int HUD_X = 4;
    private static final int HUD_Y = 4;
    private static final int HUD_WIDTH = 170;
    private static final int HUD_HEIGHT = 34;

    private FrHudRenderer() {
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        // In-game only: our screens render their own content and the vanilla
        // HUD elements we overlay are not relevant while a screen is open.
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        ClientPresentationCache cache = ClientPresentationCache.instance();
        List<String> lines = new ArrayList<>(3);
        lines.add(ClientViewProjection.balanceLine(cache));
        int pending = ClientViewProjection.notificationLines(cache).size();
        if (pending > 0) {
            lines.add(pending + " pending notification"
                    + (pending == 1 ? "" : "s"));
        }
        // FR-MAIL-001-A §6.5: new-mail HUD badge, shown only while the player's
        // inventory contains the communicator. No client / no communicator = no
        // mail badge (zero impact).
        net.minecraft.world.entity.player.Player player = Minecraft.getInstance().player;
        if (player != null
                && com.fontainerepublic.client.CommunicatorGate
                .inventoryContainsCommunicator(player)) {
            int unread = com.fontainerepublic.client.mail.ClientMailCache.instance().unread();
            if (unread > 0) {
                lines.add("✉ " + unread + " unread mail"
                        + (unread == 1 ? "" : "s"));
            }
        }
        FrGuiUtil.drawCard(
                event.getGuiGraphics(),
                Minecraft.getInstance().font,
                HUD_X,
                HUD_Y,
                HUD_WIDTH,
                lines.size() > 1 ? HUD_HEIGHT + 10 * (lines.size() - 1) : HUD_HEIGHT,
                "FR — " + ClientViewProjection.currencyName(cache),
                lines,
                2
        );
    }
}
