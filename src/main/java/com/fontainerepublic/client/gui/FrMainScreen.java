package com.fontainerepublic.client.gui;

import com.fontainerepublic.client.gui.guide.GuideScreen;
import com.fontainerepublic.client.gui.money.MoneyScreen;
import com.fontainerepublic.client.gui.notifications.NotificationScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * FR client main menu (FR-CLIENT-001-IMPL-B): entry point opened by the
 * {@code /frclient} client command. Pure navigation — every screen below is a
 * thin view over the non-authoritative presentation cache or a static guide.
 */
public final class FrMainScreen extends Screen {

    private static final int BUTTON_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 24;

    public FrMainScreen() {
        super(Component.literal("FontaineRepublic"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 100;
        addRenderableWidget(Button.builder(
                        Component.literal("Money — balance / transfer / flow"),
                        button -> open(new MoneyScreen())
                )
                .bounds(centerX - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("Notifications"),
                        button -> open(new NotificationScreen())
                )
                .bounds(centerX - BUTTON_WIDTH / 2, startY + SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("Guide"),
                        button -> open(new GuideScreen())
                )
                .bounds(centerX - BUTTON_WIDTH / 2, startY + 2 * SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("Government — ministries / positions"),
                        button -> open(new com.fontainerepublic.client.gui.government.GovernmentScreen())
                )
                .bounds(centerX - BUTTON_WIDTH / 2, startY + 3 * SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("Parliament — proposals"),
                        button -> open(new com.fontainerepublic.client.gui.parliament.ParliamentScreen())
                )
                .bounds(centerX - BUTTON_WIDTH / 2, startY + 4 * SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("Court — cases"),
                        button -> open(new com.fontainerepublic.client.gui.court.CourtScreen())
                )
                .bounds(centerX - BUTTON_WIDTH / 2, startY + 5 * SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("Land — overview"),
                        button -> open(new com.fontainerepublic.client.gui.land.LandScreen())
                )
                .bounds(centerX - BUTTON_WIDTH / 2, startY + 6 * SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(
                        Component.literal("Close"),
                        button -> this.onClose()
                )
                .bounds(centerX - BUTTON_WIDTH / 2, startY + 7 * SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(
                this.font,
                "FontaineRepublic — FR Client",
                this.width / 2,
                this.height / 2 - 90,
                FrGuiUtil.COLOR_TITLE
        );
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void open(Screen screen) {
        net.minecraft.client.player.LocalPlayer player =
                net.minecraft.client.Minecraft.getInstance().player;
        if (player != null
                && !com.fontainerepublic.client.CommunicatorGate
                .holdsCommunicator(player)) {
            player.displayClientMessage(
                    Component.literal(
                            com.fontainerepublic.client.CommunicatorGate
                                    .GATE_MESSAGE
                    ),
                    false
            );
            return;
        }
        net.minecraft.client.Minecraft.getInstance().setScreen(screen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
