package com.fontainerepublic.client.gui.land;

import com.fontainerepublic.client.gui.FrGuiUtil;
import com.fontainerepublic.client.landclaim.ClientLandClaimCache;
import com.fontainerepublic.client.landclaim.ClientLandClaimSender;
import com.fontainerepublic.client.landclaim.LandClaimTarget;
import com.fontainerepublic.common.landclaim.LandClaimResultPacket;
import com.fontainerepublic.common.landclaim.LandInspectResultPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parcel location view (FR-LAND-CLAIM-001-A §4/§5): opened when the communicator
 * is right-clicked on a block. Opens by sending a {@code LandInspectPacket} for
 * that exact position and renders the server's answer — either the existing
 * parcel summary (read-only) or a "claimable" state with a button that sends a
 * {@code LandClaimPacket}. Purely non-authoritative: the server re-runs every
 * gate and grants the usage right atomically.
 */
public final class LandLocationScreen extends Screen {

    private static final int CARD_X = 10;
    private static final int CARD_Y = 10;
    private static final int CARD_WIDTH = 380;
    private static final int CARD_HEIGHT = 150;

    private final BlockPos pos;
    private final String dimension;
    private Button claimButton;
    private boolean sentInspect;
    private boolean claimed;

    public LandLocationScreen(BlockPos pos, String dimension) {
        super(Component.literal("Land location"));
        this.pos = pos;
        this.dimension = dimension;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        ClientLandClaimCache cache = ClientLandClaimCache.instance();
        cache.clear();
        claimed = false;
        sentInspect = false;
        addRenderableWidget(Button.builder(
                        Component.literal("Back"),
                        button -> this.onClose()
                )
                .bounds(centerX - 40, this.height - 40, 80, 20)
                .build());
        claimButton = addRenderableWidget(Button.builder(
                        Component.literal("Apply to create parcel"),
                        button -> claim()
                )
                .bounds(centerX - 90, this.height - 70, 180, 20)
                .build());
        claimButton.visible = false;
    }

    /** On the first render, ask the server whether the position is claimable. */
    private void ensureInspectSent() {
        if (sentInspect) {
            return;
        }
        sentInspect = true;
        ClientLandClaimSender.inspect(dimension, pos.getX(), pos.getY(), pos.getZ());
    }

    private void claim() {
        if (claimed) {
            return;
        }
        claimed = true;
        ClientLandClaimCache.instance().clear();
        ClientLandClaimSender.claim(dimension, pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        ensureInspectSent();
        ClientLandClaimCache cache = ClientLandClaimCache.instance();
        LandInspectResultPacket inspect = cache.inspectResult();
        LandClaimResultPacket claimResult = cache.claimResult();

        // FR-LAND-CLAIM-001-FIX-01 F3: a late S2C result whose echoed canonical
        // target does not equal this screen's own target is stale (the player
        // opened a different location and a previous response arrived late).
        // It must never change title/button state of the current screen, so it
        // is ignored here rather than cached.
        if (inspect != null && !matchesTarget(inspect.dimension(),
                inspect.x(), inspect.y(), inspect.z())) {
            inspect = null;
        }
        if (claimResult != null && !matchesTarget(claimResult.dimension(),
                claimResult.x(), claimResult.y(), claimResult.z())) {
            claimResult = null;
        }

        List<String> lines = new ArrayList<>();
        lines.add("X " + pos.getX() + "  Y " + pos.getY() + "  Z " + pos.getZ());
        lines.add("Dimension: " + dimension);
        String title;
        if (claimResult != null) {
            if (claimResult.success()) {
                title = "Parcel claimed successfully";
                lines.add("Parcel: " + claimResult.parcelId());
                lines.add("Region x" + regionLine(claimResult.minX(), claimResult.maxX())
                        + " y" + regionLine(claimResult.minY(), claimResult.maxY())
                        + " z" + regionLine(claimResult.minZ(), claimResult.maxZ()));
                lines.add("Zone: " + claimResult.zoneType()
                        + "  Access: " + claimResult.access());
                claimButton.visible = false;
            } else {
                title = "Claim failed";
                lines.add("Code: " + claimResult.code());
                claimButton.visible = false;
            }
        } else if (inspect == null) {
            title = "Inspecting…";
            lines.add("Waiting for the server.");
        } else if (inspect.claimable()) {
            title = "Unowned land";
            lines.add("This position is claimable.");
            claimButton.visible = true;
        } else if (inspect.parcelId() == null) {
            title = "Inspect unavailable";
            lines.add("Code: " + inspect.code());
            claimButton.visible = false;
        } else {
            title = "Already owned";
            lines.add("Parcel: " + inspect.parcelId());
            lines.add("Zone: " + (inspect.zoneType() == null ? "?" : inspect.zoneType())
                    + "  Access: " + (inspect.access() == null ? "?" : inspect.access()));
            claimButton.visible = false;
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
                6
        );
        graphics.drawString(
                this.font,
                "Server-authoritative; ownership stays with the Republic.",
                CARD_X,
                CARD_Y + CARD_HEIGHT + 8,
                FrGuiUtil.COLOR_MUTED,
                false
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static String regionLine(int min, int max) {
        return "[" + min + ".." + max + "]";
    }

    /**
     * FR-LAND-CLAIM-001-FIX-01 F3: whether a result echoing
     * {@code resultDimension + x + y + z} belongs to this screen's canonical
     * block position (delegates to the pure {@link LandClaimTarget}). A stale
     * S2C result for a previously opened location is rejected so it can never
     * change this screen's title or button state.
     */
    private boolean matchesTarget(String resultDimension, int x, int y, int z) {
        return LandClaimTarget.matches(
                dimension, pos.getX(), pos.getY(), pos.getZ(),
                resultDimension, x, y, z
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
