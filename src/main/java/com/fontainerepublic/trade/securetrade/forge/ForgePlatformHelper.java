package com.fontainerepublic.trade.securetrade.forge;

import com.fontainerepublic.trade.securetrade.platform.IPlatformHelper;
import com.fontainerepublic.trade.securetrade.TradeItemValidator;
import com.fontainerepublic.trade.securetrade.network.TradeBlacklistWarningPacket;
import com.fontainerepublic.trade.securetrade.network.TradeLockPacket;
import com.fontainerepublic.trade.securetrade.network.TradeMoneyChangePacket;
import com.fontainerepublic.trade.securetrade.network.TradeNetwork;
import com.fontainerepublic.trade.securetrade.network.TradeStateSyncPacket;
import com.fontainerepublic.trade.securetrade.network.TradeXPChangePacket;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Verbatim port of Navielon/SecureTrade's {@code ForgePlatformHelper} (MIT,
 * Copyright (c) 2026 Secure Trade Mod Authors), extended by FR-TRADE-004 with
 * the money packet and the money fields on the state sync.
 */
public class ForgePlatformHelper implements IPlatformHelper {
    @Override
    public void sendLockPacket(boolean locked) {
        TradeNetwork.sendToServer(new TradeLockPacket(locked));
    }

    @Override
    public void sendXPChangePacket(long xpPoints) {
        TradeNetwork.sendToServer(new TradeXPChangePacket(xpPoints));
    }

    @Override
    public void sendMoneyChangePacket(long moneyAmount) {
        TradeNetwork.sendToServer(new TradeMoneyChangePacket(moneyAmount));
    }

    @Override
    public void sendStateSync(ServerPlayer player, boolean myLock, boolean otherLock, int countdownSeconds, long myXP, long otherXP, long otherTotalXP, String partnerName, long myMoney, long otherMoney) {
        TradeNetwork.sendToPlayer(player, new TradeStateSyncPacket(myLock, otherLock, countdownSeconds, myXP, otherXP, otherTotalXP, partnerName, myMoney, otherMoney));
    }

    @Override
    public void sendBlacklistWarning(ServerPlayer player) {
        TradeNetwork.sendToPlayer(player, new TradeBlacklistWarningPacket());
    }

    @Override
    public boolean containsPlatformContainerItems(ItemStack stack, List<String> blacklist, int depth) {
        try {
            Class<?> forgeCapabilitiesClass = Class.forName("net.minecraftforge.common.capabilities.ForgeCapabilities");
            Class<?> capabilityClass = Class.forName("net.minecraftforge.common.capabilities.Capability");
            Field itemHandlerField = forgeCapabilitiesClass.getField("ITEM_HANDLER");
            Object itemHandlerCapability = itemHandlerField.get(null);
            Method getCapability = stack.getClass().getMethod("getCapability", capabilityClass);
            Object lazyOptional = getCapability.invoke(stack, itemHandlerCapability);
            Method resolve = lazyOptional.getClass().getMethod("resolve");
            Object optional = resolve.invoke(lazyOptional);
            if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) {
                return false;
            }
            return TradeItemValidator.containsHandlerItems(opt.get(), blacklist, depth);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public double getMaxTradeDistance() {
        return TradeConfig.MAX_TRADE_DISTANCE.get();
    }

    @Override
    public int getRequestTimeoutSeconds() {
        return TradeConfig.REQUEST_TIMEOUT_SECONDS.get();
    }

    @Override
    public int getTradeCooldownSeconds() {
        return TradeConfig.TRADE_COOLDOWN_SECONDS.get();
    }

    @Override
    public int getCountdownSeconds() {
        return TradeConfig.COUNTDOWN_SECONDS.get();
    }

    @Override
    public boolean isLoggingEnabled() {
        return TradeConfig.ENABLE_TRADE_LOGGING.get();
    }

    @Override
    public java.util.List<String> getBlacklistedItems() {
        return (java.util.List<String>) TradeConfig.BLACKLISTED_ITEMS.get();
    }

    @Override
    public java.util.List<String> getAllowedDimensions() {
        return (java.util.List<String>) TradeConfig.ALLOWED_DIMENSIONS.get();
    }

    @Override
    public java.util.List<String> getBlockedDimensions() {
        return (java.util.List<String>) TradeConfig.BLOCKED_DIMENSIONS.get();
    }

    @Override
    public int getMaxHistoryEntries() {
        return TradeConfig.MAX_HISTORY_ENTRIES.get();
    }
}
