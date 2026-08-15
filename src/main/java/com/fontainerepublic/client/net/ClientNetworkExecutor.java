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

    /** FR-MAIL-001-A §6.6: server-pushed mailbox sync projection. */
    public static void acceptMailboxSync(
            com.fontainerepublic.common.network.display.MailboxSyncPacket message
    ) {
        ensureLogoutCleanup();
        com.fontainerepublic.client.mail.ClientMailCache.instance().setSync(message);
    }

    /** FR-MAIL-001-A §2.3: server-pushed new-mail alert (unread count). */
    public static void acceptMailAlert(
            com.fontainerepublic.common.network.display.MailAlertPacket message
    ) {
        ensureLogoutCleanup();
        com.fontainerepublic.client.mail.ClientMailCache.instance().setAlert(
                message.unreadCount(),
                message.summary()
        );
        // FR-MAIL-001-FIX-01 §2.3: the chat-reminder fallback — show the bounded
        // latest-mail summary as an action-bar line so the reminder is visible
        // even when the HUD badge is off-screen. Display-only; never authori-
        // tative. Only fires while the player carries the communicator (the
        // server already gates the send, but the client re-checks for parity).
        if (message.unreadCount() > 0) {
            net.minecraft.client.Minecraft minecraft =
                    net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player != null
                    && (com.fontainerepublic.client.CommunicatorGate
                            .inventoryContainsCommunicator(minecraft.player)
                            || com.fontainerepublic.client.CommunicatorGate
                            .holdsCommunicator(minecraft.player))) {
                minecraft.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                                "✉ " + message.unreadCount() + " unread mail — "
                                        + message.summary()),
                        true
                );
            }
        }
    }

    /**
     * FR-LAND-CLAIM-001-A §5: server-pushed inspection result for the
     * {@code LandLocationScreen}. Display-only; the server stays
     * authoritative.
     */
    public static void acceptLandInspectResult(
            com.fontainerepublic.common.landclaim.LandInspectResultPacket message
    ) {
        ensureLogoutCleanup();
        com.fontainerepublic.client.landclaim.ClientLandClaimCache.instance()
                .setInspectResult(message);
    }

    /**
     * FR-LAND-CLAIM-001-A §5: server-pushed claim result (success or stable
     * code). Display-only; the server stays authoritative.
     */
    public static void acceptLandClaimResult(
            com.fontainerepublic.common.landclaim.LandClaimResultPacket message
    ) {
        ensureLogoutCleanup();
        com.fontainerepublic.client.landclaim.ClientLandClaimCache.instance()
                .setClaimResult(message);
    }

    /**
     * FR-LAND-002-A §5: server-pushed self-only my-usage-rights page. The
     * cache accepts it only when it is the correlated response for the current
     * request (stale/lower responses are discarded); presentation-only, the
     * server stays authoritative.
     */
    public static void acceptMyLandRightsPage(
            com.fontainerepublic.common.landrights.MyLandRightsPagePacket message
    ) {
        ensureLogoutCleanup();
        com.fontainerepublic.client.landrights.ClientMyLandRightsCache.instance()
                .accept(message);
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
                    (ClientPlayerNetworkEvent.LoggingOut event) -> {
                        ClientPresentationCache.instance().clear();
                        com.fontainerepublic.client.mail.ClientMailCache.instance().clear();
                        com.fontainerepublic.client.landclaim.ClientLandClaimCache.instance()
                                .clear();
                        com.fontainerepublic.client.landrights.ClientMyLandRightsCache
                                .instance().clear();
                    }
            );
            logoutListenerRegistered = true;
            LOGGER.debug(
                    "[FR Client] Presentation cache logout cleanup registered"
            );
        }
    }
}
