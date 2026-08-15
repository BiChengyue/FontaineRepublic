package com.fontainerepublic.trade.securetrade.command;

import com.mojang.brigadier.CommandDispatcher;
import com.fontainerepublic.trade.securetrade.platform.Services;
import com.fontainerepublic.trade.securetrade.menu.TradeMenu;
import com.fontainerepublic.trade.securetrade.TradeHistoryManager;
import com.fontainerepublic.trade.securetrade.TradeMessages;
import com.fontainerepublic.trade.securetrade.TradeRules;
import com.fontainerepublic.trade.securetrade.SecureTradeSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Verbatim port of Navielon/SecureTrade's {@code TradeCommand} (MIT,
 * Copyright (c) 2026 Secure Trade Mod Authors).
 */
public class TradeCommand {

    private static class TradeRequest {
        public final UUID senderId;
        public final long expirationTime;

        public TradeRequest(UUID senderId, long expirationTime) {
            this.senderId = senderId;
            this.expirationTime = expirationTime;
        }
    }

    private static class CooldownKey {
        public final UUID sender;
        public final UUID target;

        public CooldownKey(UUID sender, UUID target) {
            this.sender = sender;
            this.target = target;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CooldownKey)) return false;
            CooldownKey that = (CooldownKey) o;
            return sender.equals(that.sender) && target.equals(that.target);
        }

        @Override
        public int hashCode() {
            return 31 * sender.hashCode() + target.hashCode();
        }
    }

    private static final Map<UUID, TradeRequest> pendingRequests = new HashMap<>();

    private static final Map<CooldownKey, Long> tradeCooldowns = new HashMap<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("trade")
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> requestTrade(context.getSource(), EntityArgument.getPlayer(context, "target"))))
                .then(Commands.literal("accept")
                        .executes(context -> acceptTrade(context.getSource())))
                .then(Commands.literal("deny")
                        .executes(context -> denyTrade(context.getSource())))
                .then(Commands.literal("history")
                        .executes(context -> showHistory(context.getSource())))
        );
    }

    private static int requestTrade(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer sender = source.getPlayer();
        if (sender == null) return 0;

        if (sender.getUUID().equals(target.getUUID())) {
            TradeMessages.error(sender, Component.translatable("securetrade.cannot_trade_self"));
            return 0;
        }

        if (sender.containerMenu instanceof TradeMenu) {
            TradeMessages.error(sender, Component.translatable("securetrade.error_already_trading"));
            return 0;
        }
        if (target.containerMenu instanceof TradeMenu) {
            TradeMessages.error(sender, Component.translatable("securetrade.error_target_already_trading", TradeMessages.playerName(target)));
            return 0;
        }

        String senderDim = sender.level().dimension().location().toString();
        String targetDim = target.level().dimension().location().toString();

        if (!TradeRules.isDimensionAllowed(senderDim)) {
            TradeMessages.error(sender, Component.translatable("securetrade.error_blocked_dimension_self"));
            return 0;
        }
        if (!TradeRules.isDimensionAllowed(targetDim)) {
            TradeMessages.error(sender, Component.translatable("securetrade.error_blocked_dimension_target"));
            return 0;
        }

        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        if (maxDist > 0) {
            if (!sender.level().dimension().equals(target.level().dimension())) {
                TradeMessages.error(sender, Component.translatable("securetrade.error_different_dimensions"));
                return 0;
            }
            double distSq = sender.distanceToSqr(target);
            if (distSq > maxDist * maxDist) {
                TradeMessages.error(sender, Component.translatable("securetrade.error_too_far"));
                return 0;
            }
        }

        long now = System.currentTimeMillis();

        tradeCooldowns.entrySet().removeIf(entry -> now > entry.getValue());

        TradeRequest targetPending = pendingRequests.get(target.getUUID());
        if (targetPending != null) {
            if (now > targetPending.expirationTime) {
                pendingRequests.remove(target.getUUID());
                applyExpiredRequestCooldown(target.getUUID(), targetPending);
                targetPending = null;
            }
        }

        if (targetPending != null) {
            if (targetPending.senderId.equals(sender.getUUID())) {
                TradeMessages.warning(sender, Component.translatable("securetrade.error_already_requested"));
            } else {
                TradeMessages.warning(sender, Component.translatable("securetrade.error_target_has_pending"));
            }
            return 0;
        }

        CooldownKey cooldownKey = new CooldownKey(sender.getUUID(), target.getUUID());
        Long cooldownExpire = tradeCooldowns.get(cooldownKey);
        if (cooldownExpire != null) {
            if (now < cooldownExpire) {
                int secsLeft = (int) Math.ceil((cooldownExpire - now) / 1000.0);
                TradeMessages.warning(sender, Component.translatable("securetrade.error_cooldown", secsLeft, TradeMessages.playerName(target)));
                return 0;
            } else {
                tradeCooldowns.remove(cooldownKey);
            }
        }

        TradeRequest senderPending = pendingRequests.get(sender.getUUID());
        if (senderPending != null && senderPending.senderId.equals(target.getUUID()) && now <= senderPending.expirationTime) {
            pendingRequests.remove(sender.getUUID());
            TradeMessages.success(target, Component.translatable("securetrade.trade_accepted"));
            TradeMessages.success(sender, Component.translatable("securetrade.target_accepted", TradeMessages.playerName(target)));
            TradeMenu.openTrade(sender, target);
            return 1;
        }

        long expireAt = now + (Services.PLATFORM.getRequestTimeoutSeconds() * 1000L);
        pendingRequests.put(target.getUUID(), new TradeRequest(sender.getUUID(), expireAt));

        TradeMessages.info(sender, Component.translatable("securetrade.request_sent", TradeMessages.playerName(target)));
        sender.playNotifySound(SecureTradeSounds.TRADE_REQUEST_SENT, SoundSource.MASTER, 0.8f, 1.0f);

        Component acceptText = Component.translatable("securetrade.accept_button")
                .withStyle(Style.EMPTY.withColor(0x55FF55).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("securetrade.accept_hover"))));

        Component denyText = Component.translatable("securetrade.deny_button")
                .withStyle(Style.EMPTY.withColor(0xFF5555).withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/trade deny"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("securetrade.deny_hover"))));

        target.sendSystemMessage(TradeMessages.format(
                Component.translatable("securetrade.wants_to_trade", TradeMessages.playerName(sender))
                        .append(" ").append(acceptText).append(" ").append(denyText),
                ChatFormatting.YELLOW));

        return 1;
    }

    private static int acceptTrade(CommandSourceStack source) {
        ServerPlayer target = source.getPlayer();
        if (target == null) return 0;

        if (target.containerMenu instanceof TradeMenu) {
            TradeMessages.error(target, Component.translatable("securetrade.error_already_trading"));
            return 0;
        }

        long now = System.currentTimeMillis();
        TradeRequest request = pendingRequests.get(target.getUUID());
        if (request == null || now > request.expirationTime) {
            if (request != null) {
                pendingRequests.remove(target.getUUID());
                applyExpiredRequestCooldown(target.getUUID(), request);
            }
            TradeMessages.warning(target, Component.translatable("securetrade.no_pending_requests"));
            return 0;
        }

        pendingRequests.remove(target.getUUID());

        ServerPlayer sender = target.server.getPlayerList().getPlayer(request.senderId);
        if (sender == null) {
            TradeMessages.error(target, Component.translatable("securetrade.sender_offline"));
            return 0;
        }

        if (sender.containerMenu instanceof TradeMenu) {
            TradeMessages.error(target, Component.translatable("securetrade.error_target_already_trading", TradeMessages.playerName(sender)));
            return 0;
        }

        String targetDim = target.level().dimension().location().toString();
        String senderDim = sender.level().dimension().location().toString();

        if (!TradeRules.isDimensionAllowed(targetDim)) {
            TradeMessages.error(target, Component.translatable("securetrade.error_blocked_dimension_self"));
            return 0;
        }
        if (!TradeRules.isDimensionAllowed(senderDim)) {
            TradeMessages.error(target, Component.translatable("securetrade.error_blocked_dimension_target"));
            return 0;
        }

        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        if (maxDist > 0) {
            if (!sender.level().dimension().equals(target.level().dimension()) || 
                sender.distanceToSqr(target) > maxDist * maxDist) {
                TradeMessages.error(target, Component.translatable("securetrade.error_too_far"));
                TradeMessages.error(sender, Component.translatable("securetrade.error_too_far"));
                return 0;
            }
        }

        TradeMessages.success(target, Component.translatable("securetrade.trade_accepted"));
        TradeMessages.success(sender, Component.translatable("securetrade.target_accepted", TradeMessages.playerName(target)));

        TradeMenu.openTrade(sender, target);

        return 1;
    }

    private static int denyTrade(CommandSourceStack source) {
        ServerPlayer target = source.getPlayer();
        if (target == null) return 0;

        if (target.containerMenu instanceof TradeMenu) {
            TradeMessages.error(target, Component.translatable("securetrade.error_already_trading"));
            return 0;
        }

        long now = System.currentTimeMillis();
        TradeRequest request = pendingRequests.get(target.getUUID());
        if (request == null || now > request.expirationTime) {
            if (request != null) {
                pendingRequests.remove(target.getUUID());
                applyExpiredRequestCooldown(target.getUUID(), request);
            }
            TradeMessages.warning(target, Component.translatable("securetrade.no_pending_requests"));
            return 0;
        }

        pendingRequests.remove(target.getUUID());

        long cooldownTime = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        if (cooldownTime > 0) {
            tradeCooldowns.put(new CooldownKey(request.senderId, target.getUUID()), now + cooldownTime);
        }

        ServerPlayer sender = target.server.getPlayerList().getPlayer(request.senderId);
        if (sender != null) {
            sender.playNotifySound(SecureTradeSounds.TRADE_CANCEL, SoundSource.MASTER, 0.9f, 1.0f);
            TradeMessages.warning(sender, Component.translatable("securetrade.target_denied", TradeMessages.playerName(target)));
        }
        target.playNotifySound(SecureTradeSounds.TRADE_CANCEL, SoundSource.MASTER, 0.9f, 1.0f);
        TradeMessages.warning(target, Component.translatable("securetrade.trade_denied"));

        return 1;
    }

    private static int showHistory(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        TradeHistoryManager.showHistory(player);
        return 1;
    }

    public static void clearAll() {
        pendingRequests.clear();
        tradeCooldowns.clear();
    }

    public static void pruneExpired() {
        long now = System.currentTimeMillis();
        pendingRequests.entrySet().removeIf(entry -> {
            TradeRequest request = entry.getValue();
            if (now <= request.expirationTime) {
                return false;
            }
            applyExpiredRequestCooldown(entry.getKey(), request);
            return true;
        });
        tradeCooldowns.entrySet().removeIf(entry -> now > entry.getValue());
    }

    private static void applyExpiredRequestCooldown(UUID targetId, TradeRequest request) {
        long cooldownTime = Services.PLATFORM.getTradeCooldownSeconds() * 1000L;
        if (cooldownTime > 0) {
            tradeCooldowns.put(new CooldownKey(request.senderId, targetId), request.expirationTime + cooldownTime);
        }
    }

    // ------------------------------------------------------------------
    // Public parity facade (FR-TRADE-004): /fr trade delegates here so a
    // client without the GUI can still trade by command.
    // ------------------------------------------------------------------

    public static int requestTradeCommand(CommandSourceStack source, ServerPlayer target) {
        return requestTrade(source, target);
    }

    public static int acceptTradeCommand(CommandSourceStack source) {
        return acceptTrade(source);
    }

    public static int denyTradeCommand(CommandSourceStack source) {
        return denyTrade(source);
    }

    public static int showHistoryCommand(CommandSourceStack source) {
        return showHistory(source);
    }
}
