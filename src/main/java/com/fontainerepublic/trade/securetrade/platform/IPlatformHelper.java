package com.fontainerepublic.trade.securetrade.platform;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Verbatim port of Navielon/SecureTrade's {@code IPlatformHelper} (MIT,
 * Copyright (c) 2026 Secure Trade Mod Authors; upstream commit
 * {@code add98b377ffc39e5d73789a874a08c5e369790ce}), extended by
 * FR-TRADE-004 with the money leg ({@link #sendMoneyChangePacket} and
 * {@link #sendStateSync} money fields).
 */
public interface IPlatformHelper {
    void sendLockPacket(boolean locked);

    void sendXPChangePacket(long xpPoints);

    void sendMoneyChangePacket(long moneyAmount);

    void sendStateSync(ServerPlayer player, boolean myLock, boolean otherLock, int countdownSeconds, long myXP, long otherXP, long otherTotalXP, String partnerName, long myMoney, long otherMoney);

    void sendBlacklistWarning(ServerPlayer player);

    boolean containsPlatformContainerItems(ItemStack stack, List<String> blacklist, int depth);

    double getMaxTradeDistance();
    int getRequestTimeoutSeconds();
    int getTradeCooldownSeconds();
    int getCountdownSeconds();
    boolean isLoggingEnabled();
    java.util.List<String> getBlacklistedItems();
    java.util.List<String> getAllowedDimensions();
    java.util.List<String> getBlockedDimensions();
    int getMaxHistoryEntries();
}
