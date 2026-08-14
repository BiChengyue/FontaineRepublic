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

import java.util.ArrayList;
import java.util.List;

/**
 * Communicator mail screen (FR-MAIL-001-A §5): inbox list + read/delete +
 * compose (recipient / subject / body / money). Thin projection of the server
 * pushed {@link MailboxSyncPacket} and bound intention submission through the
 * C2S mail ledger; the server re-runs every authority rule.
 *
 * <p>FR-MAIL-001-FIX-01: the layout was reworked so the inbox card, the
 * selected-mail reader panel, and the compose form never overlap, and the
 * transient {@code status} feedback line (send/read/delete results) is now
 * actually rendered. Clicking {@code Read} opens the selected mail's content
 * (subject/from/body/attachment summary) so Read has an observable effect,
 * and always re-requests the server projection so the read/claimed state
 * stays in sync.</p>
 */
public final class MailScreen extends Screen {

    private static final int COL_X = 10;
    private static final int COL_Y = 24;
    private static final int COL_W = 320;
    private static final int ROW_H = 12;
    private static final int MAX_ROWS = 12;

    private static final int PANEL_X = COL_X + COL_W + 14;
    private static final int PANEL_W = 200;
    private static final int STATUS_Y = 4;

    private final List<MailboxSyncPacket.Entry> empty = List.of();
    private long selectedId = 0L;
    private boolean compose;
    private boolean reading;
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
    private Button backButton;

    public MailScreen() {
        super(Component.literal("Mail"));
    }

    @Override
    protected void init() {
        // Compose form controls (only visible in compose mode).
        toBox = addRenderableWidget(new EditBox(this.font,
                COL_X + 4, COL_Y + 22, COL_W - 8, 16, Component.literal("Recipient")));
        subjectBox = addRenderableWidget(new EditBox(this.font,
                COL_X + 4, COL_Y + 44, COL_W - 8, 16, Component.literal("Subject")));
        bodyBox = addRenderableWidget(new EditBox(this.font,
                COL_X + 4, COL_Y + 66, COL_W - 8, 40, Component.literal("Body")));
        moneyBox = addRenderableWidget(new EditBox(this.font,
                COL_X + 4, COL_Y + 112, 90, 16, Component.literal("Money")));
        sendButton = addRenderableWidget(Button.builder(Component.literal("Send"),
                        b -> send())
                .bounds(COL_X + COL_W - 120, COL_Y + 110, 56, 20).build());

        // Mode / action buttons on the right column.
        inboxButton = addRenderableWidget(Button.builder(Component.literal("Inbox"),
                        b -> { compose = false; reading = false;
                            ClientMailSender.requestList(); })
                .bounds(PANEL_X, 8, 56, 18).build());
        composeButton = addRenderableWidget(Button.builder(Component.literal("Compose"),
                        b -> { compose = true; reading = false; })
                .bounds(PANEL_X + 62, 8, 56, 18).build());
        readButton = addRenderableWidget(Button.builder(Component.literal("Read"),
                        b -> openRead())
                .bounds(PANEL_X, 32, 56, 18).build());
        deleteButton = addRenderableWidget(Button.builder(Component.literal("Delete"),
                        b -> deleteSelected())
                .bounds(PANEL_X + 62, 32, 56, 18).build());
        backButton = addRenderableWidget(Button.builder(Component.literal("Back"),
                        b -> { reading = false; })
                .bounds(PANEL_X + 124, 32, 56, 18).build());

        renderWidgets();
        ClientMailSender.requestList();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawString(this.font, "FR Mail — unread: "
                + ClientMailCache.instance().unread(), COL_X, 6, FrGuiUtil.COLOR_TITLE, false);
        if (compose) {
            renderCompose(graphics);
        } else if (reading) {
            renderReader(graphics);
        } else {
            renderList(graphics);
        }
        // Transient feedback line (send/read/delete results).
        if (!status.isEmpty()) {
            graphics.drawString(this.font, status, COL_X, this.height - 14,
                    statusColor, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderCompose(GuiGraphics graphics) {
        FrGuiUtil.drawCard(graphics, this.font, COL_X, COL_Y, COL_W, 142,
                "Compose mail", List.of(), 0);
    }

    private void renderList(GuiGraphics graphics) {
        FrGuiUtil.drawCard(graphics, this.font, COL_X, COL_Y, COL_W,
                MAX_ROWS * ROW_H + 10, "Inbox — click a row to select", List.of(), 0);
        MailboxSyncPacket sync = ClientMailCache.instance().sync();
        List<MailboxSyncPacket.Entry> entries = sync == null ? empty : sync.entries();
        if (entries.isEmpty()) {
            graphics.drawString(this.font, "No mail.", COL_X + 4, COL_Y + 18,
                    FrGuiUtil.COLOR_MUTED, false);
            return;
        }
        int rows = Math.min(entries.size(), MAX_ROWS);
        for (int index = 0; index < rows; index++) {
            MailboxSyncPacket.Entry entry = entries.get(index);
            int y = COL_Y + 16 + index * ROW_H;
            String subject = entry.subject() == null ? "" : entry.subject();
            if (subject.length() > 30) {
                subject = subject.substring(0, 30) + "…";
            }
            String line = (entry.read() ? "  " : "> ") + subject;
            int color = entry.mailId() == selectedId
                    ? FrGuiUtil.COLOR_ACCENT : FrGuiUtil.COLOR_BODY;
            graphics.drawString(this.font, line, COL_X + 4, y, color, false);
        }
    }

    private void renderReader(GuiGraphics graphics) {
        MailboxSyncPacket.Entry entry = selectedEntry();
        String subject = entry == null ? "?" : entry.subject();
        String from = entry == null ? "?" : entry.from();
        String body = entry == null ? "" : entry.body();
        long money = entry == null ? 0L : entry.moneyAttachment();
        boolean claimed = entry != null && entry.claimed();
        int itemSlots = entry == null ? 0 : entry.itemSlotCount();

        List<String> lines = new ArrayList<>();
        lines.add("From: " + from);
        if (money > 0L) {
            lines.add("Money attachment: " + money
                    + (claimed ? " (claimed)" : " (unclaimed)"));
        }
        if (itemSlots > 0) {
            lines.add("Item attachments: " + itemSlots + " slot(s)");
        }
        lines.add("");
        // Wrap the body into bounded display lines.
        if (body != null && !body.isEmpty()) {
            for (String wrapped : wrap(body, 42, 12)) {
                lines.add(wrapped);
            }
        } else {
            lines.add("(no body)");
        }
        FrGuiUtil.drawCard(graphics, this.font, COL_X, COL_Y, COL_W + PANEL_W + 4,
                180, subject, lines, 16);
    }

    private MailboxSyncPacket.Entry selectedEntry() {
        MailboxSyncPacket sync = ClientMailCache.instance().sync();
        if (sync == null) {
            return null;
        }
        for (MailboxSyncPacket.Entry entry : sync.entries()) {
            if (entry.mailId() == selectedId) {
                return entry;
            }
        }
        return null;
    }

    private void openRead() {
        if (selectedId <= 0) {
            status = "Select a mail first.";
            statusColor = FrGuiUtil.COLOR_MUTED;
            return;
        }
        ClientMailSender.read(selectedId);
        reading = true;
        status = "Read / claimed.";
        statusColor = FrGuiUtil.COLOR_ACCENT;
        renderWidgets();
        // Re-sync so the server's read/claim state is reflected here.
        ClientMailSender.requestList();
    }

    private void deleteSelected() {
        if (selectedId <= 0) {
            status = "Select a mail first.";
            statusColor = FrGuiUtil.COLOR_MUTED;
            return;
        }
        ClientMailSender.delete(selectedId);
        selectedId = 0L;
        reading = false;
        status = "Deleted.";
        statusColor = FrGuiUtil.COLOR_ACCENT;
        renderWidgets();
        ClientMailSender.requestList();
    }

    /** Shows the reader/action controls only when relevant (no overlap). */
    private void renderWidgets() {
        boolean reader = !compose && reading;
        readButton.visible = !compose;
        deleteButton.visible = !compose;
        inboxButton.visible = true;
        composeButton.visible = true;
        backButton.visible = reader;
        toBox.visible = compose;
        subjectBox.visible = compose;
        bodyBox.visible = compose;
        moneyBox.visible = compose;
        sendButton.visible = compose;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !compose && !reading
                && mouseX >= COL_X && mouseX <= COL_X + COL_W
                && mouseY >= COL_Y + 16
                && mouseY <= COL_Y + 16 + MAX_ROWS * ROW_H) {
            MailboxSyncPacket sync = ClientMailCache.instance().sync();
            if (sync != null) {
                List<MailboxSyncPacket.Entry> entries = sync.entries();
                int row = (int) ((mouseY - (COL_Y + 16)) / ROW_H);
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
        if (money < 0 || money > 1_000_000_000L) {
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

    /** Word-wrap a string to a bounded set of short lines (display bound). */
    private static List<String> wrap(String text, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        String remainder = text;
        while (!remainder.isEmpty() && lines.size() < maxLines) {
            if (remainder.length() <= width) {
                lines.add(remainder);
                break;
            }
            int cut = remainder.lastIndexOf(' ', width);
            if (cut <= 0) {
                cut = width;
            }
            lines.add(remainder.substring(0, cut).trim());
            remainder = remainder.substring(cut).trim();
        }
        return lines;
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
