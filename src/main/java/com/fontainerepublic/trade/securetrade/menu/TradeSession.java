package com.fontainerepublic.trade.securetrade.menu;

import com.fontainerepublic.trade.securetrade.platform.Services;
import com.fontainerepublic.trade.securetrade.TradeLogger;
import com.fontainerepublic.trade.securetrade.TradeHistoryManager;
import com.fontainerepublic.trade.securetrade.TradeItemValidator;
import com.fontainerepublic.trade.securetrade.TradeMessages;
import com.fontainerepublic.trade.securetrade.TradeRules;
import com.fontainerepublic.trade.securetrade.SecureTradeSounds;
import com.fontainerepublic.trade.securetrade.XPMath;
import com.fontainerepublic.trade.securetrade.FRSettlementBridge;
import com.fontainerepublic.server.economy.api.TradeSettlementReceipt;
import com.fontainerepublic.server.economy.persistence.EconomyUnavailableException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Verbatim port of Navielon/SecureTrade's {@code TradeSession} (MIT,
 * Copyright (c) 2026 Secure Trade Mod Authors; upstream commit
 * {@code add98b377ffc39e5d73789a874a08c5e369790ce}), extended by FR-TRADE-004
 * with the money leg: two-sided offers, an authoritative FR currency
 * settlement with 5% payer tax, subject identity and audit.
 */
public class TradeSession {
    public final ServerPlayer player1;
    public final ServerPlayer player2;
    public final SimpleContainer inventory1;
    public final SimpleContainer inventory2;

    public boolean player1Locked = false;
    public boolean player2Locked = false;
    public long player1XP = 0;
    public long player2XP = 0;
    public long player1Money = 0;
    public long player2Money = 0;
    private boolean isCancelled = false;
    private boolean isFinished = false;

    private int countdownTicks = -1;
    private long pendingPlayer1XP = -1;
    private long pendingPlayer2XP = -1;
    private long pendingPlayer1Money = -1;
    private long pendingPlayer2Money = -1;
    private final List<ItemStack> inventory1Snapshot = new ArrayList<>();
    private final List<ItemStack> inventory2Snapshot = new ArrayList<>();

    public TradeSession(ServerPlayer player1, ServerPlayer player2) {
        this.player1 = player1;
        this.player2 = player2;
        this.inventory1 = new SimpleContainer(27);
        this.inventory2 = new SimpleContainer(27);
        refreshSnapshot(inventory1, inventory1Snapshot);
        refreshSnapshot(inventory2, inventory2Snapshot);
        TradeSessionManager.register(this);
    }

    public void onItemsChanged() {
        if (!hasChanged(inventory1, inventory1Snapshot) && !hasChanged(inventory2, inventory2Snapshot)) {
            return;
        }

        refreshSnapshot(inventory1, inventory1Snapshot);
        refreshSnapshot(inventory2, inventory2Snapshot);
        onStateChanged();
    }

    public void onStateChanged() {
        if (player1Locked || player2Locked || countdownTicks > 0) {
            player1Locked = false;
            player2Locked = false;
            countdownTicks = -1;
            playAbortedSound();
        }
        syncState();
    }

    private static boolean hasChanged(SimpleContainer inventory, List<ItemStack> snapshot) {
        if (inventory.getContainerSize() != snapshot.size()) {
            return true;
        }

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (!ItemStack.matches(inventory.getItem(i), snapshot.get(i))) {
                return true;
            }
        }
        return false;
    }

    private static void refreshSnapshot(SimpleContainer inventory, List<ItemStack> snapshot) {
        snapshot.clear();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            snapshot.add(inventory.getItem(i).copy());
        }
    }

    public void setLocked(ServerPlayer player, boolean locked) {
        if (locked && (TradeItemValidator.containsBlacklistedItems(inventory1) || TradeItemValidator.containsBlacklistedItems(inventory2))) {
            Services.PLATFORM.sendBlacklistWarning(player);
            return;
        }

        if (player == player1) {
            if (player1Locked == locked) {
                return;
            }
            player1Locked = locked;
        } else if (player == player2) {
            if (player2Locked == locked) {
                return;
            }
            player2Locked = locked;
        } else {
            return;
        }

        if (player1Locked && player2Locked) {
            countdownTicks = Services.PLATFORM.getCountdownSeconds() * 20;
        } else {
            if (countdownTicks > 0) {
                countdownTicks = -1;
                playAbortedSound();
            }
        }

        syncState();
    }

    public void tick() {
        if (isCancelled || isFinished) return;

        if (!isPlayerOnline(player1) || !isPlayerOnline(player2)) {
            cancelTrade();
            return;
        }

        if (!TradeRules.isDimensionAllowed(player1.level().dimension().location().toString()) ||
            !TradeRules.isDimensionAllowed(player2.level().dimension().location().toString())) {
            cancelTrade();
            return;
        }

        double maxDist = Services.PLATFORM.getMaxTradeDistance();
        if (maxDist > 0) {
            if (!player1.level().dimension().equals(player2.level().dimension()) ||
                player1.distanceToSqr(player2) > maxDist * maxDist) {
                cancelTrade();
                return;
            }
        }

        boolean xpChanged = false;
        if (pendingPlayer1XP >= 0) {
            if (player1XP != pendingPlayer1XP) {
                player1XP = pendingPlayer1XP;
                xpChanged = true;
            }
            pendingPlayer1XP = -1;
        }
        if (pendingPlayer2XP >= 0) {
            if (player2XP != pendingPlayer2XP) {
                player2XP = pendingPlayer2XP;
                xpChanged = true;
            }
            pendingPlayer2XP = -1;
        }
        if (xpChanged) {
            onStateChanged();
        }

        boolean moneyChanged = false;
        if (pendingPlayer1Money >= 0) {
            if (player1Money != pendingPlayer1Money) {
                player1Money = pendingPlayer1Money;
                moneyChanged = true;
            }
            pendingPlayer1Money = -1;
        }
        if (pendingPlayer2Money >= 0) {
            if (player2Money != pendingPlayer2Money) {
                player2Money = pendingPlayer2Money;
                moneyChanged = true;
            }
            pendingPlayer2Money = -1;
        }
        if (moneyChanged) {
            onStateChanged();
        }

        if (countdownTicks > 0) {
            countdownTicks--;
            if (countdownTicks % 20 == 0) {
                int secsRemaining = countdownTicks / 20;
                if (secsRemaining > 0) {
                    playNotifySound(SecureTradeSounds.TRADE_COUNTDOWN_TICK, 1.6f, 1.0f);
                }
                syncState();
            }
            if (countdownTicks == 0) {
                executeTrade();
            }
        }
    }

    void syncState() {
        int secs = countdownTicks == -1 ? -1 : (countdownTicks + 19) / 20;
        if (player1.containerMenu instanceof TradeMenu menu1) {
            menu1.syncToClient(player1Locked, player2Locked, secs, player1XP, player2XP, XPMath.getPlayerXP(player2), player2.getScoreboardName(), player1Money, player2Money);
        }
        if (player2.containerMenu instanceof TradeMenu menu2) {
            menu2.syncToClient(player2Locked, player1Locked, secs, player2XP, player1XP, XPMath.getPlayerXP(player1), player1.getScoreboardName(), player2Money, player1Money);
        }
    }

    public void setOfferedXP(ServerPlayer player, long xp) {
        if (xp < 0 || isCancelled || isFinished) {
            return;
        }

        long maxXP = XPMath.getPlayerXP(player);
        long offeredXP = Math.max(0L, Math.min(maxXP, xp));

        if (player == player1) {
            pendingPlayer1XP = offeredXP;
        } else if (player == player2) {
            pendingPlayer2XP = offeredXP;
        }
    }

    public void setOfferedMoney(ServerPlayer player, long money) {
        if (money < 0 || isCancelled || isFinished) {
            return;
        }

        long offeredMoney = money;

        if (player == player1) {
            pendingPlayer1Money = offeredMoney;
        } else if (player == player2) {
            pendingPlayer2Money = offeredMoney;
        }
    }

    private void playNotifySound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        player1.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
        player2.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }

    private void playAbortedSound() {
        playNotifySound(SecureTradeSounds.TRADE_CANCEL, 0.9f, 1.0f);
    }

    private void executeTrade() {
        if (!isPlayerOnline(player1) || !isPlayerOnline(player2)) {
            cancelTrade();
            return;
        }

        if (TradeItemValidator.containsBlacklistedItems(inventory1) || TradeItemValidator.containsBlacklistedItems(inventory2)) {
            cancelTrade();
            return;
        }

        long p1Xp = XPMath.getPlayerXP(player1);
        long p2Xp = XPMath.getPlayerXP(player2);
        if (p1Xp < player1XP || p2Xp < player2XP) {
            cancelTrade();
            return;
        }

        // FR-TRADE-004: authoritative FR money settlement (items + XP do not
        // carry a tax; each paying money offer is charged floor(offer*rate/100).
        // A payer who cannot cover offer+tax fails the whole trade closed and
        // the escrow is refunded via cancelTrade()).
        boolean hadMoney = player1Money != 0L || player2Money != 0L;
        TradeSettlementReceipt receipt = null;
        if (hadMoney) {
            try {
                receipt = FRSettlementBridge.settleMoney(
                        player1.getUUID(), player2.getUUID(), player1Money, player2Money
                );
            } catch (EconomyUnavailableException failure) {
                TradeLogger.log("Trade settlement rejected (" + failure.failureCode() + "): "
                        + failure.getMessage());
                cancelTrade();
                return;
            } catch (RuntimeException failure) {
                TradeLogger.log("Trade settlement failed unexpectedly: " + failure.getMessage());
                cancelTrade();
                return;
            }
        }

        boolean hadItems = hasItems(inventory1) || hasItems(inventory2);
        boolean hadXp = player1XP != 0L || player2XP != 0L;

        TradeHistoryManager.recordTrade(player1, player2, inventory1, inventory2, player1XP, player2XP, player1Money, player2Money);

        StringBuilder logMsg = new StringBuilder();
        logMsg.append("Trade completed between ")
              .append(player1.getScoreboardName()).append(" (").append(player1.getUUID()).append(") and ")
              .append(player2.getScoreboardName()).append(" (").append(player2.getUUID()).append(").\n");

        logMsg.append("  ").append(player1.getScoreboardName()).append(" offered: ").append(player1XP).append(" XP, ")
              .append(player1Money).append(" money, ");
        appendInventoryItems(logMsg, inventory1);
        logMsg.append("\n  ").append(player2.getScoreboardName()).append(" offered: ").append(player2XP).append(" XP, ")
              .append(player2Money).append(" money, ");
        appendInventoryItems(logMsg, inventory2);

        if (receipt != null) {
            logMsg.append("\n  settlement: aOffered=").append(receipt.aOffered())
                  .append(", bOffered=").append(receipt.bOffered())
                  .append(", aTax=").append(receipt.aTax())
                  .append(", bTax=").append(receipt.bTax());
        }

        TradeLogger.log(logMsg.toString());

        transferItems(inventory2, player1);
        transferItems(inventory1, player2);

        TradeMessages.success(player1, Component.translatable("securetrade.trade_successful"));
        TradeMessages.success(player2, Component.translatable("securetrade.trade_successful"));

        player1.sendSystemMessage(
            Component.translatable("securetrade.trade_completed_overlay", player2.getScoreboardName())
                .withStyle(net.minecraft.ChatFormatting.GREEN),
            true
        );
        player2.sendSystemMessage(
            Component.translatable("securetrade.trade_completed_overlay", player1.getScoreboardName())
                .withStyle(net.minecraft.ChatFormatting.GREEN),
            true
        );

        playNotifySound(SecureTradeSounds.TRADE_SUCCESS, 1.0f, 1.0f);

        XPMath.setPlayerXP(player1, p1Xp - player1XP + player2XP);
        XPMath.setPlayerXP(player2, p2Xp - player2XP + player1XP);

        // FR-TRADE-004: authoritative audit (FINANCE trade-settled).
        FRSettlementBridge.recordAudit(player1.getUUID(), player2.getUUID(), hadMoney, hadItems, hadXp);

        isFinished = true;
        TradeSessionManager.unregister(this);
        player1.closeContainer();
        player2.closeContainer();
    }

    private static boolean hasItems(SimpleContainer container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Transfers items safely even if the recipient disconnected.
     */
    private void transferItems(SimpleContainer from, ServerPlayer to) {
        for (int i = 0; i < from.getContainerSize(); i++) {
            ItemStack stack = from.getItem(i);
            if (!stack.isEmpty()) {
                if (isPlayerOnline(to)) {
                    if (!to.getInventory().add(stack)) {
                        to.drop(stack, false);
                    }
                } else {
                    // Drop items at the last known position if the player disconnected.
                    to.level().addFreshEntity(
                        new net.minecraft.world.entity.item.ItemEntity(
                            to.level(), to.getX(), to.getY(), to.getZ(), stack
                        )
                    );
                }
                from.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    private static boolean isPlayerOnline(ServerPlayer player) {
        return player.connection != null && !player.hasDisconnected();
    }

    private void appendInventoryItems(StringBuilder sb, SimpleContainer container) {
        boolean first = true;
        sb.append("[");
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                if (!first) sb.append(", ");
                sb.append(stack.getCount()).append("x ").append(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                first = false;
            }
        }
        sb.append("]");
    }

    public void cancelTrade() {
        if (isCancelled || isFinished) return;
        isCancelled = true;

        transferItems(inventory1, player1);
        transferItems(inventory2, player2);

        if (isPlayerOnline(player1)) {
            player1.playNotifySound(SecureTradeSounds.TRADE_CANCEL, SoundSource.MASTER, 0.9f, 1.0f);
            TradeMessages.warning(player1, Component.translatable("securetrade.trade_cancelled"));
            if (player1.containerMenu instanceof TradeMenu) player1.closeContainer();
        }
        if (isPlayerOnline(player2)) {
            player2.playNotifySound(SecureTradeSounds.TRADE_CANCEL, SoundSource.MASTER, 0.9f, 1.0f);
            TradeMessages.warning(player2, Component.translatable("securetrade.trade_cancelled"));
            if (player2.containerMenu instanceof TradeMenu) player2.closeContainer();
        }

        TradeSessionManager.unregister(this);
    }
}
