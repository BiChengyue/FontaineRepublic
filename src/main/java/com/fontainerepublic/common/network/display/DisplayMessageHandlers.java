package com.fontainerepublic.common.network.display;

import com.fontainerepublic.common.network.NetworkMessageHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Receiving-side handlers of the S2C presentation ledger (FR-CLIENT-001-A
 * §4.3). The handlers live in the common package and never import a client
 * class: the client executor is referenced by fully qualified name inside the
 * {@link DistExecutor#unsafeRunWhenOn} supplier only.
 *
 * <p><b>Why {@code unsafeRunWhenOn}:</b> Forge's {@code safeRunWhenOn}
 * validates the referent class and only accepts Minecraft/client packages,
 * so a mod-owned client executor throws "Unsafe Referent usage" when a
 * display packet actually arrives on the client. The unsafe variant skips
 * that validation; it is safe here because these handlers run only on the
 * receiving side of a {@code PLAY_TO_CLIENT} packet — a dedicated server
 * never receives such packets, so the {@code client/} classes are never
 * loaded there. The mod-entry client initialization uses
 * {@code FMLClientSetupEvent} instead (see FontaineRepublic).</p>
 *
 * <p>Transport ordering is already guaranteed by
 * {@code ServerNetworkDispatcher}: the handler runs on the receiving logical
 * main thread, so the client executor may update the in-memory presentation
 * cache directly.</p>
 */
public final class DisplayMessageHandlers {

    private DisplayMessageHandlers() {
    }

    public static NetworkMessageHandler<BalanceSyncPacket> balanceSync() {
        return (message, context) -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptBalanceSync(message)
        );
    }

    public static NetworkMessageHandler<TransactionNotifyPacket> transactionNotify() {
        return (message, context) -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptTransactionNotify(message)
        );
    }

    public static NetworkMessageHandler<NotificationPacket> notification() {
        return (message, context) -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptNotification(message)
        );
    }

    public static NetworkMessageHandler<CitizenInfoPacket> citizenInfo() {
        return (message, context) -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptCitizenInfo(message)
        );
    }

    public static NetworkMessageHandler<TransactionHistorySyncPacket> transactionHistorySync() {
        return (message, context) -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptTransactionHistorySync(message)
        );
    }

    public static NetworkMessageHandler<GovernmentInfoPacket> governmentInfo() {
        return (message, context) -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptGovernmentInfo(message)
        );
    }

    public static NetworkMessageHandler<ParliamentInfoPacket> parliamentInfo() {
        return (message, context) -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptParliamentInfo(message)
        );
    }

    public static NetworkMessageHandler<JusticeInfoPacket> justiceInfo() {
        return (message, context) -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptJusticeInfo(message)
        );
    }

    public static NetworkMessageHandler<LandInfoPacket> landInfo() {
        return (message, context) -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptLandInfo(message)
        );
    }

    public static NetworkMessageHandler<MailboxSyncPacket> mailboxSync() {
        return (message, context) -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptMailboxSync(message)
        );
    }

    public static NetworkMessageHandler<MailAlertPacket> mailAlert() {
        return (message, context) -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptMailAlert(message)
        );
    }

    public static NetworkMessageHandler<com.fontainerepublic.common.landclaim.LandInspectResultPacket>
            landInspectResult() {
        return (message, context) -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptLandInspectResult(message)
        );
    }

    public static NetworkMessageHandler<com.fontainerepublic.common.landclaim.LandClaimResultPacket>
            landClaimResult() {
        return (message, context) -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptLandClaimResult(message)
        );
    }

    public static NetworkMessageHandler<com.fontainerepublic.common.landrights.MyLandRightsPagePacket>
            myLandRightsPage() {
        return (message, context) -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptMyLandRightsPage(message)
        );
    }
}
