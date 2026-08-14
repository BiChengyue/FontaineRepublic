package com.fontainerepublic.trade.securetrade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Trade chat formatting (FR-TRADE-003-A).
 *
 * <p>Adapted from Secure Trade's {@code TradeMessages} (MIT, Copyright (c)
 * 2026 Secure Trade Mod Authors). The prefix was changed to the FR
 * communicator branding; the send helpers and colour conventions are
 * preserved.</p>
 */
public final class TradeMessages {
    private TradeMessages() {
    }

    public static Component playerName(ServerPlayer player) {
        return Component.literal(player.getScoreboardName()).withStyle(ChatFormatting.AQUA);
    }

    public static void info(ServerPlayer player, Component message) {
        send(player, message, ChatFormatting.GRAY);
    }

    public static void success(ServerPlayer player, Component message) {
        send(player, message, ChatFormatting.GREEN);
    }

    public static void warning(ServerPlayer player, Component message) {
        send(player, message, ChatFormatting.YELLOW);
    }

    public static void error(ServerPlayer player, Component message) {
        send(player, message, ChatFormatting.RED);
    }

    public static MutableComponent format(Component message, ChatFormatting color) {
        return Component.empty()
                .append(Component.literal("Trade").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(message.copy().withStyle(color));
    }

    private static void send(ServerPlayer player, Component message, ChatFormatting color) {
        player.sendSystemMessage(format(message, color));
    }
}
