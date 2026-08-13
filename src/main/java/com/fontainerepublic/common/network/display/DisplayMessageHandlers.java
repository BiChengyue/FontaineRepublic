package com.fontainerepublic.common.network.display;

import com.fontainerepublic.common.network.NetworkMessageHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * Receiving-side handlers of the S2C presentation ledger (FR-CLIENT-001-A
 * §4.3). The handlers live in the common package and never import a client
 * class: the client executor is referenced by fully qualified name inside the
 * {@link DistExecutor#safeRunWhenOn} supplier only, so a dedicated server
 * never evaluates the supplier and never loads any {@code client/} class.
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
        return (message, context) -> DistExecutor.safeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptBalanceSync(message)
        );
    }

    public static NetworkMessageHandler<TransactionNotifyPacket> transactionNotify() {
        return (message, context) -> DistExecutor.safeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptTransactionNotify(message)
        );
    }

    public static NetworkMessageHandler<NotificationPacket> notification() {
        return (message, context) -> DistExecutor.safeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptNotification(message)
        );
    }

    public static NetworkMessageHandler<CitizenInfoPacket> citizenInfo() {
        return (message, context) -> DistExecutor.safeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptCitizenInfo(message)
        );
    }

    public static NetworkMessageHandler<TransactionHistorySyncPacket> transactionHistorySync() {
        return (message, context) -> DistExecutor.safeRunWhenOn(
                Dist.CLIENT,
                () -> () -> com.fontainerepublic.client.net.ClientNetworkExecutor
                        .acceptTransactionHistorySync(message)
        );
    }
}
