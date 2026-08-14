package com.fontainerepublic.client.gui.trade;

import com.fontainerepublic.client.gui.FrGuiUtil;
import com.fontainerepublic.client.trade.ClientTradeCache;
import com.fontainerepublic.client.trade.ClientTradeSender;
import com.fontainerepublic.common.network.display.TradeStateSyncPacket;
import com.fontainerepublic.common.trade.TradeOfferItemPacket;
import com.fontainerepublic.common.trade.TradeOfferMoneyPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Communicator trade screen (FR-TRADE-001-A §5).
 *
 * <p>A thin, read-only projection of the latest server snapshot plus
 * intention submission: the own and counterparty offer slots (4 + 4), money
 * offers, agreement flags, the LOCKED countdown, the local main inventory
 * for picking items, and agree / cancel controls. Every action sends the
 * bounded C2S intention packet (ledger IDs 9-14); the server re-runs every
 * authority rule, and a REQUESTED snapshot opens this screen so the invited
 * player can accept or refuse. No optimistic state is ever applied.</p>
 */
public final class TradeScreen extends Screen {

    private static final int PANEL_WIDTH = 180;
    private static final int PANEL_HEIGHT = 118;
    private static final int OWN_X = 12;
    private static final int OTHER_X = 210;
    private static final int PANEL_Y = 30;
    private static final int SLOT_SIZE = 36;
    private static final int SLOT_GAP = 6;

    private static final int INV_X = 12;
    private static final int INV_Y = 196;
    private static final int INV_SLOT = 18;
    private static final int INV_ROWS = 4;
    private static final int INV_COLS = 9;

    private EditBox amountBox;
    private Button agreeButton;
    private Button cancelAgreeButton;
    private Button cancelTradeButton;
    private Button acceptButton;
    private Button refuseButton;
    private Button closeButton;

    private String statusMessage = "";
    private int statusColor = FrGuiUtil.COLOR_MUTED;

    public TradeScreen() {
        super(Component.literal("Trade"));
    }

    @Override
    protected void init() {
        amountBox = addRenderableWidget(new EditBox(
                this.font,
                OWN_X + 4,
                158,
                110,
                16,
                Component.literal("Money offer")
        ));
        amountBox.setMaxLength(19);
        addRenderableWidget(Button.builder(
                        Component.literal("Offer money"),
                        button -> offerMoney()
                )
                .bounds(OWN_X + 120, 157, 72, 18)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("Back"),
                        button -> this.onClose()
                )
                .bounds(OTHER_X + 110, 157, 60, 18)
                .build());

        agreeButton = addRenderableWidget(Button.builder(
                        Component.literal("Agree"),
                        button -> ClientTradeSender.sendAgree(currentSessionId(), true)
                )
                .bounds(OWN_X, 292, 90, 20)
                .build());
        cancelAgreeButton = addRenderableWidget(Button.builder(
                        Component.literal("Cancel agree"),
                        button -> ClientTradeSender.sendAgree(currentSessionId(), false)
                )
                .bounds(OWN_X + 96, 292, 96, 20)
                .build());
        cancelTradeButton = addRenderableWidget(Button.builder(
                        Component.literal("Cancel trade"),
                        button -> ClientTradeSender.sendCancel(currentSessionId())
                )
                .bounds(OTHER_X, 292, 90, 20)
                .build());
        acceptButton = addRenderableWidget(Button.builder(
                        Component.literal("Accept invitation"),
                        button -> ClientTradeSender.sendRespond(currentSessionId(), true)
                )
                .bounds(OWN_X, 292, 120, 20)
                .build());
        refuseButton = addRenderableWidget(Button.builder(
                        Component.literal("Refuse"),
                        button -> ClientTradeSender.sendRespond(currentSessionId(), false)
                )
                .bounds(OWN_X + 126, 292, 70, 20)
                .build());
        closeButton = addRenderableWidget(Button.builder(
                        Component.literal("Close"),
                        button -> this.onClose()
                )
                .bounds(OWN_X + 96, 292, 80, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        TradeStateSyncPacket snapshot = ClientTradeCache.instance().snapshot();
        if (snapshot == null) {
            graphics.drawString(
                    this.font,
                    "No trade session.",
                    OWN_X,
                    12,
                    FrGuiUtil.COLOR_MUTED,
                    false
            );
            updateControls(snapshot);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        graphics.drawString(
                this.font,
                phaseTitle(snapshot),
                OWN_X,
                10,
                FrGuiUtil.COLOR_TITLE,
                false
        );
        graphics.drawString(
                this.font,
                "Your offer",
                OWN_X,
                24,
                FrGuiUtil.COLOR_MUTED,
                false
        );
        graphics.drawString(
                this.font,
                "Their offer",
                OTHER_X,
                24,
                FrGuiUtil.COLOR_MUTED,
                false
        );

        drawPanel(graphics, OWN_X, "You");
        drawSlots(graphics, OWN_X, snapshot.ownItems(), snapshot.ownAgree());
        graphics.drawString(
                this.font,
                "Money: " + snapshot.ownMoney(),
                OWN_X + 4,
                PANEL_Y + PANEL_HEIGHT - 16,
                FrGuiUtil.COLOR_BODY,
                false
        );

        drawPanel(graphics, OTHER_X, "Other");
        drawSlots(graphics, OTHER_X, snapshot.otherItems(), snapshot.otherAgree());
        graphics.drawString(
                this.font,
                "Money: " + snapshot.otherMoney(),
                OTHER_X + 4,
                PANEL_Y + PANEL_HEIGHT - 16,
                FrGuiUtil.COLOR_BODY,
                false
        );

        drawInventory(graphics);
        graphics.drawString(
                this.font,
                statusMessage,
                OWN_X,
                276,
                statusColor,
                false
        );
        updateControls(snapshot);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphics graphics, int x, String title) {
        FrGuiUtil.drawCard(
                graphics,
                this.font,
                x,
                PANEL_Y,
                PANEL_WIDTH,
                PANEL_HEIGHT,
                title,
                List.of(),
                0
        );
    }

    private void drawSlots(
            GuiGraphics graphics,
            int x,
            List<ItemStack> items,
            boolean agree
    ) {
        for (int index = 0; index < TradeOfferItemPacket.SLOT_COUNT; index++) {
            int slotX = x + 8 + index * (SLOT_SIZE + SLOT_GAP);
            int slotY = PANEL_Y + 26;
            graphics.fill(
                    slotX - 1,
                    slotY - 1,
                    slotX + SLOT_SIZE + 1,
                    slotY + SLOT_SIZE + 1,
                    FrGuiUtil.PANEL_BORDER
            );
            graphics.fill(
                    slotX,
                    slotY,
                    slotX + SLOT_SIZE,
                    slotY + SLOT_SIZE,
                    FrGuiUtil.PANEL_BACKGROUND
            );
            ItemStack stack = items.get(index);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, slotX + 10, slotY + 10);
                graphics.renderItemDecorations(this.font, stack, slotX + 10, slotY + 10);
            }
            if (agree) {
                graphics.drawString(
                        this.font,
                        "AGREED",
                        x + 4,
                        PANEL_Y + PANEL_HEIGHT - 36,
                        FrGuiUtil.COLOR_ACCENT,
                        false
                );
            }
        }
    }

    private void drawInventory(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int index = row * INV_COLS + col;
                int slotX = INV_X + col * INV_SLOT;
                int slotY = INV_Y + row * INV_SLOT;
                ItemStack stack = minecraft.player.getInventory().getItem(index);
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, slotX, slotY);
                    graphics.renderItemDecorations(this.font, stack, slotX, slotY);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && handleSlotClick(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Slot picking: clicking an own offer slot withdraws it; clicking a main
     * inventory cell offers it into the first free own slot. Pure intention
     * submission — the server re-validates the source slot and authority.
     */
    private boolean handleSlotClick(double mouseX, double mouseY) {
        TradeStateSyncPacket snapshot = ClientTradeCache.instance().snapshot();
        if (snapshot == null) {
            return false;
        }
        int phase = snapshot.phase();
        for (int index = 0; index < TradeOfferItemPacket.SLOT_COUNT; index++) {
            int slotX = OWN_X + 8 + index * (SLOT_SIZE + SLOT_GAP);
            int slotY = PANEL_Y + 26;
            if (within(mouseX, mouseY, slotX, slotY, SLOT_SIZE, SLOT_SIZE)) {
                if (phase == TradeStateSyncPacket.PHASE_OPEN
                        || phase == TradeStateSyncPacket.PHASE_LOCKED) {
                    ClientTradeSender.sendWithdrawItem(currentSessionId(), index);
                }
                return true;
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int index = row * INV_COLS + col;
                int slotX = INV_X + col * INV_SLOT;
                int slotY = INV_Y + row * INV_SLOT;
                if (within(mouseX, mouseY, slotX, slotY, INV_SLOT, INV_SLOT)) {
                    if ((phase == TradeStateSyncPacket.PHASE_OPEN
                            || phase == TradeStateSyncPacket.PHASE_LOCKED)
                            && !minecraft.player.getInventory().getItem(index).isEmpty()) {
                        int targetSlot = firstFreeOwnSlot(snapshot.ownItems());
                        if (targetSlot < 0) {
                            statusMessage = "All four offer slots are full; "
                                    + "withdraw one first.";
                            statusColor = FrGuiUtil.COLOR_ERROR;
                        } else {
                            ClientTradeSender.sendOfferItem(
                                    currentSessionId(),
                                    targetSlot,
                                    index
                            );
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static int firstFreeOwnSlot(List<ItemStack> ownItems) {
        for (int index = 0; index < ownItems.size(); index++) {
            if (ownItems.get(index).isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    private void offerMoney() {
        TradeStateSyncPacket snapshot = ClientTradeCache.instance().snapshot();
        if (snapshot == null) {
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(amountBox.getValue().trim());
        } catch (NumberFormatException invalid) {
            statusMessage = "Enter a valid amount (0 clears the offer).";
            statusColor = FrGuiUtil.COLOR_ERROR;
            return;
        }
        if (amount < 0 || amount > TradeOfferMoneyPacket.MAX_OFFER_AMOUNT) {
            statusMessage = "Amount out of range.";
            statusColor = FrGuiUtil.COLOR_ERROR;
            return;
        }
        ClientTradeSender.sendOfferMoney(currentSessionId(), amount);
        amountBox.setValue("");
        statusMessage = "Money offer sent.";
        statusColor = FrGuiUtil.COLOR_ACCENT;
    }

    /** Bounded session id from the latest snapshot (0 when absent — the C2S
     *  packet validates positive ids, so a stale send is a server no-op). */
    private static long currentSessionId() {
        TradeStateSyncPacket snapshot = ClientTradeCache.instance().snapshot();
        return snapshot == null ? 0L : snapshot.sessionId();
    }

    private void updateControls(TradeStateSyncPacket snapshot) {
        if (snapshot == null) {
            setVisible(amountBox, false);
            hide(agreeButton, cancelAgreeButton, cancelTradeButton,
                    acceptButton, refuseButton, closeButton);
            return;
        }
        int phase = snapshot.phase();
        boolean interactive = phase == TradeStateSyncPacket.PHASE_OPEN
                || phase == TradeStateSyncPacket.PHASE_LOCKED;
        boolean requested = phase == TradeStateSyncPacket.PHASE_REQUESTED;
        boolean terminal = phase == TradeStateSyncPacket.PHASE_COMPLETED
                || phase == TradeStateSyncPacket.PHASE_CANCELLED;

        setVisible(amountBox, interactive);
        setActive(agreeButton, interactive && !snapshot.ownAgree());
        setActive(cancelAgreeButton, interactive && snapshot.ownAgree());
        setActive(cancelTradeButton, interactive || requested);
        setActive(acceptButton, requested);
        setActive(refuseButton, requested);
        setActive(closeButton, terminal);
        if (terminal) {
            statusMessage = phase == TradeStateSyncPacket.PHASE_COMPLETED
                    ? "Trade completed."
                    : "Trade cancelled.";
            statusColor = phase == TradeStateSyncPacket.PHASE_COMPLETED
                    ? FrGuiUtil.COLOR_ACCENT
                    : FrGuiUtil.COLOR_ERROR;
        }
    }

    private static void setActive(Button button, boolean active) {
        if (button != null) {
            button.visible = true;
            button.active = active;
        }
    }

    private static void setVisible(EditBox box, boolean visible) {
        if (box != null) {
            box.visible = visible;
            box.setEditable(visible);
        }
    }

    private static void hide(Button... buttons) {
        for (Button button : buttons) {
            if (button != null) {
                button.visible = false;
            }
        }
    }

    private static boolean within(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height
    ) {
        return mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height;
    }

    private static String phaseTitle(TradeStateSyncPacket snapshot) {
        String base = switch (snapshot.phase()) {
            case TradeStateSyncPacket.PHASE_REQUESTED -> "Trade request pending";
            case TradeStateSyncPacket.PHASE_OPEN -> "Trade in progress";
            case TradeStateSyncPacket.PHASE_LOCKED ->
                    "Both agreed — executing in " + snapshot.countdownSeconds() + "s";
            case TradeStateSyncPacket.PHASE_EXECUTING -> "Executing…";
            case TradeStateSyncPacket.PHASE_COMPLETED -> "Trade completed";
            case TradeStateSyncPacket.PHASE_CANCELLED -> "Trade cancelled";
            default -> "Trade";
        };
        if (snapshot.phase() == TradeStateSyncPacket.PHASE_REQUESTED) {
            base = base + " — accept or refuse";
        }
        return base;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
