package com.fontainerepublic.client.gui.trade;

import com.fontainerepublic.client.gui.FrGuiUtil;
import com.fontainerepublic.client.trade.ClientTradeCache;
import com.fontainerepublic.client.trade.ClientTradeSender;
import com.fontainerepublic.common.network.display.TradeStateSyncPacket;
import com.fontainerepublic.common.trade.TradeMenu;
import com.fontainerepublic.common.trade.TradeOfferItemPacket;
import com.fontainerepublic.common.trade.TradeOfferMoneyPacket;
import com.fontainerepublic.common.trade.TradeOfferXpPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Container-backed trade screen (FR-TRADE-003-B).
 *
 * <p>Adapted from Secure Trade's {@code TradeScreen} (MIT, Copyright (c) 2026
 * Secure Trade Mod Authors; upstream commit
 * {@code add98b377ffc39e5d73789a874a08c5e369790ce}). It is an
 * {@link AbstractContainerScreen} over the shared 54-slot {@link TradeMenu}
 * container so the custom {@code MenuType} (registered per FR-TRADE-003-A
 * &sect;3) drives screen opening through Forge's {@code MenuScreens}. The two
 * 27-slot offer regions keep the Secure Trade layout; the FR intent model
 * carries {@link TradeOfferItemPacket#SLOT_COUNT} (4) live offer slots per
 * side, so the first four cells of each region show the authoritative
 * projection and the rest are inert.</p>
 *
 * <p><b>Authority:</b> every displayed value (phase, money, XP, agreement,
 * countdown, offer stacks) is read from the non-authoritative
 * {@link ClientTradeCache}, which only ever receives the server's per-viewer
 * {@link TradeStateSyncPacket}. Every action sends the bounded C2S intent;
 * the {@code TradeService} stays the single authority. The Secure Trade
 * texture is unavailable in FR, so the background and panels use
 * {@link FrGuiUtil} drawing instead.</p>
 */
public final class TradeScreen extends AbstractContainerScreen<TradeMenu> {

    private static final int IMAGE_WIDTH = 356;
    private static final int IMAGE_HEIGHT = 205;

    // Offer panel geometry (mirrors the container slot coordinates).
    private static final int LEFT_PANEL_X = 8;
    private static final int LEFT_PANEL_Y = 17;
    private static final int RIGHT_PANEL_X = 188;
    private static final int SLOT_COL = 18;
    private static final int SLOT_ROW = 18;

    private static final int LEFT_READY_X = 48;
    private static final int LEFT_READY_Y = 96;
    private static final int READY_W = 74;
    private static final int READY_H = 18;

    private EditBox moneyBox;
    private EditBox xpBox;
    private Button readyButton;
    private Button cancelButton;
    private Button acceptButton;

    private String statusMessage = "";
    private int statusColor = FrGuiUtil.COLOR_MUTED;

    public TradeScreen(TradeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;

        moneyBox = addRenderableWidget(new EditBox(
                this.font, x + 12, y + 150, 100, 14, Component.literal("Money offer")));
        moneyBox.setMaxLength(19);
        addRenderableWidget(Button.builder(
                        Component.literal("Money"),
                        button -> offerMoney())
                .bounds(x + 116, y + 149, 56, 16)
                .build());

        xpBox = addRenderableWidget(new EditBox(
                this.font, x + 212, y + 150, 100, 14, Component.literal("XP offer")));
        xpBox.setMaxLength(19);
        addRenderableWidget(Button.builder(
                        Component.literal("XP"),
                        button -> offerXp())
                .bounds(x + 316, y + 149, 40, 16)
                .build());

        readyButton = addRenderableWidget(Button.builder(
                        Component.literal("Ready"),
                        button -> toggleReady())
                .bounds(x + LEFT_READY_X, y + LEFT_READY_Y, READY_W, READY_H)
                .build());
        cancelButton = addRenderableWidget(Button.builder(
                        Component.literal("Cancel"),
                        button -> ClientTradeSender.sendCancel(sessionId()))
                .bounds(x + 284, y + LEFT_READY_Y, READY_W, READY_H)
                .build());
        acceptButton = addRenderableWidget(Button.builder(
                        Component.literal("Accept"),
                        button -> ClientTradeSender.sendRespond(sessionId(), true))
                .bounds(x + 130, y + LEFT_READY_Y, READY_W, READY_H)
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + IMAGE_WIDTH, y + IMAGE_HEIGHT, FrGuiUtil.PANEL_BACKGROUND);
        graphics.fill(x, y, x + IMAGE_WIDTH, y + 1, FrGuiUtil.PANEL_BORDER);
        graphics.fill(x, y + IMAGE_HEIGHT - 1, x + IMAGE_WIDTH, y + IMAGE_HEIGHT, FrGuiUtil.PANEL_BORDER);
        graphics.fill(x, y, x + 1, y + IMAGE_HEIGHT, FrGuiUtil.PANEL_BORDER);
        graphics.fill(x + IMAGE_WIDTH - 1, y, x + IMAGE_WIDTH, y + IMAGE_HEIGHT, FrGuiUtil.PANEL_BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        TradeStateSyncPacket snapshot = snapshot();
        int x = leftPos;
        int y = topPos;
        graphics.drawString(this.font, phaseTitle(snapshot), x + 8, y + 4, FrGuiUtil.COLOR_TITLE, false);
        graphics.drawString(this.font, "Your offer", x + LEFT_PANEL_X, y + 8, FrGuiUtil.COLOR_MUTED, false);
        graphics.drawString(this.font, "Their offer", x + RIGHT_PANEL_X, y + 8, FrGuiUtil.COLOR_MUTED, false);
        if (snapshot != null) {
            graphics.drawString(this.font, "Money: " + snapshot.ownMoney(), x + 12, y + 137, FrGuiUtil.COLOR_BODY, false);
            graphics.drawString(this.font, "Money: " + snapshot.otherMoney(), x + RIGHT_PANEL_X, y + 137, FrGuiUtil.COLOR_BODY, false);
            graphics.drawString(this.font, "XP: " + snapshot.ownXp(), x + 212, y + 137, FrGuiUtil.COLOR_BODY, false);
            graphics.drawString(this.font, "XP: " + snapshot.otherXp(), x + 304, y + 137, FrGuiUtil.COLOR_BODY, false);
        }
        graphics.drawString(this.font, statusMessage, x + 8, y + 118, statusColor, false);
    }

    /**
     * Draws the authoritative offer projection over the container slot grid:
     * the first {@link TradeOfferItemPacket#SLOT_COUNT} cells of each region.
     * The container player-inventory/hotbar slots render via {@code super}
     * (standard slot rendering); the offer regions are data-only here because
     * the intent model keeps items in the player's inventory.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawOfferProjection(graphics);
        renderTooltip(graphics, mouseX, mouseY);
        refreshControls(snapshot());
    }

    private void drawOfferProjection(GuiGraphics graphics) {
        TradeStateSyncPacket snapshot = snapshot();
        if (snapshot == null) {
            return;
        }
        drawOfferStacks(graphics, snapshot.ownItems(), leftPos + LEFT_PANEL_X, topPos + LEFT_PANEL_Y, snapshot.ownAgree());
        drawOfferStacks(graphics, snapshot.otherItems(), leftPos + RIGHT_PANEL_X, topPos + LEFT_PANEL_Y, snapshot.otherAgree());
    }

    private void drawOfferStacks(
            GuiGraphics graphics,
            List<ItemStack> items,
            int originX,
            int originY,
            boolean agreed
    ) {
        for (int i = 0; i < items.size() && i < TradeOfferItemPacket.SLOT_COUNT; i++) {
            int col = i % 9;
            int row = i / 9;
            int slotX = originX + col * SLOT_COL;
            int slotY = originY + row * SLOT_ROW;
            graphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, FrGuiUtil.PANEL_BORDER);
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, FrGuiUtil.PANEL_BACKGROUND);
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, slotX, slotY);
                graphics.renderItemDecorations(this.font, stack, slotX, slotY);
            }
        }
        if (agreed) {
            graphics.drawString(this.font, "READY", originX, originY + 3 * SLOT_ROW + 4, FrGuiUtil.COLOR_ACCENT, false);
        }
    }

    // ------------------------------------------------------------------
    // intent submission (server authoritative)
    // ------------------------------------------------------------------

    private TradeStateSyncPacket snapshot() {
        return ClientTradeCache.instance().snapshot();
    }

    private long sessionId() {
        TradeStateSyncPacket snapshot = snapshot();
        return snapshot == null ? 0L : snapshot.sessionId();
    }

    private void toggleReady() {
        TradeStateSyncPacket snapshot = snapshot();
        if (snapshot != null
                && (snapshot.phase() == TradeStateSyncPacket.PHASE_OPEN
                || snapshot.phase() == TradeStateSyncPacket.PHASE_LOCKED)) {
            ClientTradeSender.sendAgree(snapshot.sessionId(), !snapshot.ownAgree());
        }
    }

    private void offerMoney() {
        long amount;
        try {
            amount = Long.parseLong(moneyBox.getValue().trim());
        } catch (NumberFormatException invalid) {
            status("Enter a valid amount (0 clears).", FrGuiUtil.COLOR_ERROR);
            return;
        }
        if (amount < 0 || amount > TradeOfferMoneyPacket.MAX_OFFER_AMOUNT) {
            status("Amount out of range.", FrGuiUtil.COLOR_ERROR);
            return;
        }
        ClientTradeSender.sendOfferMoney(sessionId(), amount);
        moneyBox.setValue("");
        status("Money offer sent.", FrGuiUtil.COLOR_ACCENT);
    }

    private void offerXp() {
        long amount;
        try {
            amount = Long.parseLong(xpBox.getValue().trim());
        } catch (NumberFormatException invalid) {
            status("Enter a valid XP amount (0 clears).", FrGuiUtil.COLOR_ERROR);
            return;
        }
        if (amount < 0 || amount > TradeOfferXpPacket.MAX_OFFER_XP) {
            status("XP amount out of range.", FrGuiUtil.COLOR_ERROR);
            return;
        }
        ClientTradeSender.sendOfferXp(sessionId(), amount);
        xpBox.setValue("");
        status("XP offer sent.", FrGuiUtil.COLOR_ACCENT);
    }

    private void status(String message, int color) {
        statusMessage = message;
        statusColor = color;
    }

    private void refreshControls(TradeStateSyncPacket snapshot) {
        if (snapshot == null) {
            moneyBox.visible = false;
            moneyBox.setEditable(false);
            xpBox.visible = false;
            xpBox.setEditable(false);
            readyButton.visible = false;
            cancelButton.visible = false;
            acceptButton.visible = false;
            return;
        }
        int phase = snapshot.phase();
        boolean interactive = phase == TradeStateSyncPacket.PHASE_OPEN
                || phase == TradeStateSyncPacket.PHASE_LOCKED;
        boolean requested = phase == TradeStateSyncPacket.PHASE_REQUESTED;
        boolean terminal = phase == TradeStateSyncPacket.PHASE_COMPLETED
                || phase == TradeStateSyncPacket.PHASE_CANCELLED;

        moneyBox.visible = interactive;
        moneyBox.setEditable(interactive);
        xpBox.visible = interactive;
        xpBox.setEditable(interactive);
        readyButton.visible = interactive;
        readyButton.active = interactive;
        readyButton.setMessage(Component.literal(snapshot.ownAgree() ? "Cancel ready" : "Ready"));
        cancelButton.visible = interactive || requested;
        cancelButton.active = interactive || requested;
        acceptButton.visible = requested;
        acceptButton.active = requested;

        if (terminal) {
            status(phase == TradeStateSyncPacket.PHASE_COMPLETED
                    ? "Trade completed." : "Trade cancelled.",
                    phase == TradeStateSyncPacket.PHASE_COMPLETED
                            ? FrGuiUtil.COLOR_ACCENT : FrGuiUtil.COLOR_ERROR);
        }
    }

    private String phaseTitle(TradeStateSyncPacket snapshot) {
        if (snapshot == null) {
            return "No trade session.";
        }
        return switch (snapshot.phase()) {
            case TradeStateSyncPacket.PHASE_REQUESTED -> "Trade request pending — accept or refuse";
            case TradeStateSyncPacket.PHASE_OPEN -> "Trade in progress";
            case TradeStateSyncPacket.PHASE_LOCKED ->
                    "Both ready — executing in " + snapshot.countdownSeconds() + "s";
            case TradeStateSyncPacket.PHASE_EXECUTING -> "Executing…";
            case TradeStateSyncPacket.PHASE_COMPLETED -> "Trade completed";
            case TradeStateSyncPacket.PHASE_CANCELLED -> "Trade cancelled";
            default -> "Trade";
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
