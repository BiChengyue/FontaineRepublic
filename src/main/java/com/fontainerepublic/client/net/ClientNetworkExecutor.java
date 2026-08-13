package com.fontainerepublic.client.net;

import com.fontainerepublic.common.network.display.BalanceSyncPacket;
import com.fontainerepublic.common.network.display.CitizenInfoPacket;
import com.fontainerepublic.common.network.display.GovernmentInfoPacket;
import com.fontainerepublic.common.network.display.JusticeInfoPacket;
import com.fontainerepublic.common.network.display.LandInfoPacket;
import com.fontainerepublic.common.network.display.NotificationPacket;
import com.fontainerepublic.common.network.display.ParliamentInfoPacket;
import com.fontainerepublic.common.network.display.TransactionHistorySyncPacket;
import com.fontainerepublic.common.network.display.TransactionNotifyPacket;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side sink of the S2C presentation ledger (FR-CLIENT-001-A §4.3).
 *
 * <p>This class lives in {@code client/} and is referenced only from the
 * {@code DistExecutor.safeRunWhenOn(Dist.CLIENT, ...)} supplier of the common
 * display handlers, so a dedicated server never loads it. Each {@code accept}
 * updates the non-authoritative in-memory cache; the logout listener is
 * registered lazily on first use and clears the cache on disconnect.</p>
 */
public final class ClientNetworkExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientNetworkExecutor.class);

    private static boolean logoutListenerRegistered;

    private ClientNetworkExecutor() {
    }

    public static void acceptBalanceSync(BalanceSyncPacket message) {
        ensureLogoutCleanup();
        ClientPresentationCache.instance().setBalance(message);
    }

    public static void acceptTransactionNotify(TransactionNotifyPacket message) {
        ensureLogoutCleanup();
        ClientPresentationCache.instance().appendTransaction(message);
    }

    public static void acceptNotification(NotificationPacket message) {
        ensureLogoutCleanup();
        ClientPresentationCache.instance().setNotifications(message.entries());
    }

    public static void acceptCitizenInfo(CitizenInfoPacket message) {
        ensureLogoutCleanup();
        ClientPresentationCache.instance().setCitizen(message);
    }

    public static void acceptTransactionHistorySync(TransactionHistorySyncPacket message) {
        ensureLogoutCleanup();
        ClientPresentationCache.instance().setHistory(message);
    }

    public static void acceptGovernmentInfo(GovernmentInfoPacket message) {
        ensureLogoutCleanup();
        ClientPresentationCache.instance().setGovernment(message);
    }

    public static void acceptParliamentInfo(ParliamentInfoPacket message) {
        ensureLogoutCleanup();
        ClientPresentationCache.instance().setParliament(message);
    }

    public static void acceptJusticeInfo(JusticeInfoPacket message) {
        ensureLogoutCleanup();
        ClientPresentationCache.instance().setJustice(message);
    }

    public static void acceptLandInfo(LandInfoPacket message) {
        ensureLogoutCleanup();
        ClientPresentationCache.instance().setLand(message);
    }

    private static void ensureLogoutCleanup() {
        if (logoutListenerRegistered) {
            return;
        }
        synchronized (ClientNetworkExecutor.class) {
            if (logoutListenerRegistered) {
                return;
            }
            MinecraftForge.EVENT_BUS.addListener(
                    (ClientPlayerNetworkEvent.LoggingOut event) ->
                            ClientPresentationCache.instance().clear()
            );
            logoutListenerRegistered = true;
            LOGGER.debug(
                    "[FR Client] Presentation cache logout cleanup registered"
            );
        }
    }
}
