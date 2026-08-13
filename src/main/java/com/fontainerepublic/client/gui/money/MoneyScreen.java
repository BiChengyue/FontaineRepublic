package com.fontainerepublic.client.gui.money;

import com.fontainerepublic.client.gui.FrGuiUtil;
import com.fontainerepublic.client.net.ClientPresentationCache;
import com.fontainerepublic.client.view.ClientViewProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Money screen (FR-CLIENT-001-IMPL-B): balance card + transfer form +
 * live transaction flow. Everything rendered here is a projection of the
 * non-authoritative {@link ClientPresentationCache}; the form submits through
 * the server command path ({@code /fr money pay ...}) and never applies any
 * optimistic ledger change. Server-side rejections surface through the server
 * chat feedback; local failures show a bounded inline message.
 */
public final class MoneyScreen extends Screen {

    private static final int CARD_X = 10;
    private static final int CARD_Y = 10;
    private static final int CARD_WIDTH = 220;
    private static final int CARD_HEIGHT = 46;

    private static final int FORM_X = 10;
    private static final int FORM_Y = 66;
    private static final int FORM_WIDTH = 220;

    private static final int FLOW_X = 250;
    private static final int FLOW_Y = 10;
    private static final int FLOW_WIDTH = 340;
    private static final int FLOW_HEIGHT = 200;
    private static final int FLOW_PAGE_SIZE = 10;

    private EditBox targetBox;
    private EditBox amountBox;
    private EditBox memoBox;
    private Button submitButton;
    private Button previousPageButton;
    private Button nextPageButton;

    private int flowPage;
    private String statusMessage = "";
    private int statusColor = FrGuiUtil.COLOR_MUTED;

    public MoneyScreen() {
        super(Component.literal("Money"));
    }

    @Override
    protected void init() {
        int fieldWidth = FORM_WIDTH - 8;
        targetBox = addRenderableWidget(new EditBox(
                this.font, FORM_X + 4, FORM_Y + 14, fieldWidth, 16,
                Component.literal("Target (name / UUID / registry number)")
        ));
        targetBox.setMaxLength(TransferFormComposer.MAX_TARGET);
        amountBox = addRenderableWidget(new EditBox(
                this.font, FORM_X + 4, FORM_Y + 36, fieldWidth, 16,
                Component.literal("Amount")
        ));
        amountBox.setMaxLength(19);
        memoBox = addRenderableWidget(new EditBox(
                this.font, FORM_X + 4, FORM_Y + 58, fieldWidth, 16,
                Component.literal("Memo (optional)")
        ));
        memoBox.setMaxLength(TransferFormComposer.MAX_MEMO);
        submitButton = addRenderableWidget(Button.builder(
                        Component.literal("Send payment"),
                        button -> submit()
                )
                .bounds(FORM_X + 4, FORM_Y + 80, 90, 20)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("Back"),
                        button -> this.onClose()
                )
                .bounds(FORM_X + 104, FORM_Y + 80, 60, 20)
                .build());

        previousPageButton = addRenderableWidget(Button.builder(
                        Component.literal("<"),
                        button -> previousFlowPage()
                )
                .bounds(FLOW_X + 4, FLOW_Y + FLOW_HEIGHT - 22, 30, 18)
                .build());
        nextPageButton = addRenderableWidget(Button.builder(
                        Component.literal(">"),
                        button -> nextFlowPage()
                )
                .bounds(FLOW_X + FLOW_WIDTH - 34, FLOW_Y + FLOW_HEIGHT - 22, 30, 18)
                .build());
        updatePaginationButtons();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        ClientPresentationCache cache = ClientPresentationCache.instance();

        // Balance card.
        FrGuiUtil.drawCard(
                graphics,
                this.font,
                CARD_X,
                CARD_Y,
                CARD_WIDTH,
                CARD_HEIGHT,
                "Balance — " + ClientViewProjection.currencyName(cache),
                List.of(ClientViewProjection.balanceLine(cache)),
                1
        );

        // Transfer form panel.
        FrGuiUtil.drawCard(
                graphics,
                this.font,
                FORM_X,
                FORM_Y,
                FORM_WIDTH,
                116,
                "Transfer (server-validated)",
                List.of(),
                0
        );
        graphics.drawString(
                this.font,
                "Target",
                FORM_X + 4,
                FORM_Y + 4,
                FrGuiUtil.COLOR_MUTED,
                false
        );
        graphics.drawString(
                this.font,
                "Amount",
                FORM_X + 4,
                FORM_Y + 26,
                FrGuiUtil.COLOR_MUTED,
                false
        );
        graphics.drawString(
                this.font,
                "Memo",
                FORM_X + 4,
                FORM_Y + 48,
                FrGuiUtil.COLOR_MUTED,
                false
        );
        graphics.drawString(
                this.font,
                statusMessage,
                FORM_X + 4,
                FORM_Y + 104,
                statusColor,
                false
        );

        // Live transaction flow (bounded projection, paginated).
        List<String> lines = ClientViewProjection.transactionLines(cache);
        int totalPages = Math.max(1, (lines.size() + FLOW_PAGE_SIZE - 1) / FLOW_PAGE_SIZE);
        if (flowPage >= totalPages) {
            flowPage = totalPages - 1;
        }
        int from = Math.min(flowPage * FLOW_PAGE_SIZE, lines.size());
        int to = Math.min(from + FLOW_PAGE_SIZE, lines.size());
        List<String> page = lines.subList(from, to);
        FrGuiUtil.drawCard(
                graphics,
                this.font,
                FLOW_X,
                FLOW_Y,
                FLOW_WIDTH,
                FLOW_HEIGHT,
                "Live flow — page " + (flowPage + 1) + "/" + totalPages,
                page.isEmpty() ? List.of("No transactions yet.") : page,
                FLOW_PAGE_SIZE
        );
        updatePaginationButtons();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submit() {
        String command;
        try {
            command = TransferFormComposer.compose(
                    targetBox.getValue(),
                    amountBox.getValue(),
                    memoBox.getValue()
            );
        } catch (IllegalArgumentException invalid) {
            statusMessage = invalid.getMessage();
            statusColor = FrGuiUtil.COLOR_ERROR;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.connection == null) {
            statusMessage = "Not connected to a server.";
            statusColor = FrGuiUtil.COLOR_ERROR;
            return;
        }
        minecraft.player.connection.sendCommand(command);
        amountBox.setValue("");
        memoBox.setValue("");
        statusMessage = "Payment request sent; the server will confirm in chat.";
        statusColor = FrGuiUtil.COLOR_ACCENT;
    }

    private void previousFlowPage() {
        if (flowPage > 0) {
            flowPage--;
        }
    }

    private void nextFlowPage() {
        int lines = ClientViewProjection.transactionLines(
                ClientPresentationCache.instance()).size();
        int totalPages = Math.max(1, (lines + FLOW_PAGE_SIZE - 1) / FLOW_PAGE_SIZE);
        if (flowPage < totalPages - 1) {
            flowPage++;
        }
    }

    private void updatePaginationButtons() {
        List<String> lines = ClientViewProjection.transactionLines(
                ClientPresentationCache.instance());
        int totalPages = Math.max(1, (lines.size() + FLOW_PAGE_SIZE - 1) / FLOW_PAGE_SIZE);
        if (previousPageButton != null) {
            previousPageButton.active = flowPage > 0;
            nextPageButton.active = flowPage < totalPages - 1;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
