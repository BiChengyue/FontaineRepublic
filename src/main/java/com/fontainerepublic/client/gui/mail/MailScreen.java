package com.fontainerepublic.client.gui.mail;

import com.fontainerepublic.client.gui.FrGuiUtil;
import com.fontainerepublic.client.mail.ClientMailCache;
import com.fontainerepublic.client.mail.ClientMailSender;
import com.fontainerepublic.common.network.display.MailboxSyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Communicator mail screen (FR-MAIL-001-A §5): inbox list + read/delete +
 * compose (recipient / subject / body / money). Thin projection of the server
 * pushed {@link MailboxSyncPacket} and bound intention submission through the
 * C2S mail ledger; the server re-runs every authority rule. All widgets are
 * created once in {@link #init()}; clicking an inbox row selects a mail and
 * the fixed Read/Delete actions act on the selection.
 */
public final class MailScreen extends Screen {

    private static final int COL_X = 10;
    private static final int COL_Y = 12;
    private static final int COL_W = 320;
    private static final int ROW_H = 16;
    private static final int MAX_ROWS = 9;

    private final List<MailboxSyncPacket.Entry> empty = List.of();
    private long selectedId = 0L;
    private boolean compose;
    private String status = "";
    private int statusColor = FrGuiUtil.COLOR_MUTED;

    private EditBox toBox;
    private EditBox subjectBox;
    private EditBox bodyBox;
    private EditBox moneyBox;
    private Button sendButton;
    private Button inboxButton;
    private Button composeButton;
    private Button readButton;
    private Button deleteButton;

    public MailScreen() {
        super(Component.literal("Mail"));
    }

    @Override
    protected void init() {
        toBox = addRenderableWidget(new EditBox(this.font,
                COL_X + 4, COL_Y + 24, COL_W - 8, 16, Component.literal("Recipient")));
        subjectBox = addRenderableWidget(new EditBox(this.font,
                COL_X + 4, COL_Y + 46, COL_W - 8, 16, Component.literal("Subject")));
        bodyBox = addRenderableWidget(new EditBox(this.font,
                COL_X + 4, COL_Y + 68, COL_W - 8, 44, Component.literal("Body")));
        moneyBox = addRenderableWidget(new EditBox(this.font,
                COL_X + 4, COL_Y + 118, 100, 16, Component.literal("Money")));
        sendButton = addRenderableWidget(Button.builder(Component.literal("Send"),
                        b -> send())
                .bounds(COL_X + COL_W - 130, COL_Y + 116, 60, 20).build());
        inboxButton = addRenderableWidget(Button.builder(Component.literal("Inbox"),
                        b -> { compose = false; ClientMailSender.requestList();
                            hideCompose(true); })
                .bounds(COL_X + COL_W + 12, COL_Y - 12, 60, 18).build());
        composeButton = addRenderableWidget(Button.builder(Component.literal("Compose"),
                        b -> { compose = true; hideCompose(false); })
                .bounds(COL_X + COL_W + 76, COL_Y - 12, 60, 18).build());
        readButton = addRenderableWidget(Button.builder(Component.literal("Read"),
                        b -> { if (selectedId > 0) { ClientMailSender.read(selectedId);
                            status = "Read / claimed."; statusColor = FrGuiUtil.COLOR_ACCENT; } })
                .bounds(COL_X + COL_W + 12, COL_Y + 140, 60, 18).build());
        deleteButton = addRenderableWidget(Button.builder(Component.literal("Delete"),
                        b -> { if (selectedId > 0) { ClientMailSender.delete(selectedId);
                            status = "Deleted."; statusColor = FrGuiUtil.COLOR_ACCENT; } })
                .bounds(COL_X + COL_W + 76, COL_Y + 140, 60, 18).build());
        hideCompose(true);
        ClientMailSender.requestList();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawString(this.font, "FR Mail — unread: "
                + ClientMailCache.instance().unread(), COL_X, 2, FrGuiUtil.COLOR_TITLE, false);
        if (compose) {
            renderCompose(graphics);
        } else {
            renderList(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCompose(GuiGraphics graphics) {
        FrGuiUtil.drawCard(graphics, this.font, COL_X, COL_Y, COL_W, 140,
                "Compose mail", List.of(), 0);
        toBox.visible = true;
        subjectBox.visible = true;
        bodyBox.visible = true;
        moneyBox.visible = true;
        sendButton.visible = true;
    }

    private void renderList(GuiGraphics graphics) {
        FrGuiUtil.drawCard(graphics, this.font, COL_X, COL_Y, COL_W,
                MAX_ROWS * ROW_H + 8, "Inbox — click a row to select", List.of(), 0);
        MailboxSyncPacket sync = ClientMailCache.instance().sync();
        List<MailboxSyncPacket.Entry> entries = sync == null ? empty : sync.entries();
        if (entries.isEmpty()) {
            graphics.drawString(this.font, "No mail.", COL_X + 4, COL_Y + 4,
                    FrGuiUtil.COLOR_MUTED, false);
            return;
        }
        int rows = Math.min(entries.size(), MAX_ROWS);
        for (int index = 0; index < rows; index++) {
            MailboxSyncPacket.Entry entry = entries.get(index);
            int y = COL_Y + 4 + index * ROW_H;
            String line = (entry.read() ? "  " : "> ")
                    + entry.subject() + " — from " + entry.from();
            int color = entry.mailId() == selectedId
                    ? FrGuiUtil.COLOR_ACCENT : FrGuiUtil.COLOR_BODY;
            graphics.drawString(this.font, line, COL_X + 4, y, color, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !compose && mouseX >= COL_X && mouseX <= COL_X + COL_W) {
            MailboxSyncPacket sync = ClientMailCache.instance().sync();
            if (sync != null) {
                List<MailboxSyncPacket.Entry> entries = sync.entries();
                int row = (int) ((mouseY - (COL_Y + 4)) / ROW_H);
                if (row >= 0 && row < Math.min(entries.size(), MAX_ROWS)) {
                    selectedId = entries.get(row).mailId();
                    status = "Selected mail #" + selectedId;
                    statusColor = FrGuiUtil.COLOR_MUTED;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void hideCompose(boolean listMode) {
        toBox.visible = !listMode;
        subjectBox.visible = !listMode;
        bodyBox.visible = !listMode;
        moneyBox.visible = !listMode;
        sendButton.visible = !listMode;
    }

    private void send() {
        long money;
        try {
            money = moneyBox.getValue().trim().isEmpty()
                    ? 0L : Long.parseLong(moneyBox.getValue().trim());
        } catch (NumberFormatException invalid) {
            status = "Invalid money amount.";
            statusColor = FrGuiUtil.COLOR_ERROR;
            return;
        }
        if (money < 0 || money > MailSendPacketMax.MONEY) {
            status = "Money out of range (0..1,000,000,000).";
            statusColor = FrGuiUtil.COLOR_ERROR;
            return;
        }
        ClientMailSender.send(
                toBox.getValue(),
                subjectBox.getValue(),
                bodyBox.getValue(),
                money,
                List.of()
        );
        subjectBox.setValue("");
        bodyBox.setValue("");
        moneyBox.setValue("");
        status = "Mail request sent; the server will confirm in chat.";
        statusColor = FrGuiUtil.COLOR_ACCENT;
    }

    private static final class MailSendPacketMax {
        static final long MONEY = 1_000_000_000L;
    }

    @Override
    public void onClose() {
        super.onClose();
        ClientMailCache.instance().clear();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
