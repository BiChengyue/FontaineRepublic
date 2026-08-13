package com.fontainerepublic.common.network;

import com.fontainerepublic.common.network.display.BalanceSyncPacket;
import com.fontainerepublic.common.network.display.CitizenInfoPacket;
import com.fontainerepublic.common.network.display.DisplayMessageHandlers;
import com.fontainerepublic.common.network.display.GovernmentInfoPacket;
import com.fontainerepublic.common.network.display.JusticeInfoPacket;
import com.fontainerepublic.common.network.display.LandInfoPacket;
import com.fontainerepublic.common.network.display.NotificationPacket;
import com.fontainerepublic.common.network.display.ParliamentInfoPacket;
import com.fontainerepublic.common.network.display.TransactionHistorySyncPacket;
import com.fontainerepublic.common.network.display.TransactionNotifyPacket;
import net.minecraftforge.network.NetworkDirection;

import java.util.Objects;
import java.util.Optional;

/**
 * Complete compiled production ledger for the current protocol
 * (FR-CLIENT-001-A §4.2). The ledger is append-only: message IDs start at 0
 * and are never reused or reordered; later stages append further display
 * messages (citizen card and transaction history after ID 2, institution
 * summaries after ID 4, court/land summaries after ID 6, per
 * FR-CLIENT-001-IMPL-B2 / FR-CLIENT-001-IMPL-B3a / FR-CLIENT-001-IMPL-B3b).
 */
public final class NetworkProductionMessageTable {

    /** Total number of messages expected by the current protocol revision. */
    public static final int EXPECTED_MESSAGE_COUNT = 9;

    private NetworkProductionMessageTable() {
    }

    public static void registerAll(NetworkMessageRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        registration.register(displaySpec(
                0,
                BalanceSyncPacket.class,
                BalanceSyncPacket::encode,
                BalanceSyncPacket::decode,
                DisplayMessageHandlers.balanceSync()
        ));
        registration.register(displaySpec(
                1,
                TransactionNotifyPacket.class,
                TransactionNotifyPacket::encode,
                TransactionNotifyPacket::decode,
                DisplayMessageHandlers.transactionNotify()
        ));
        registration.register(displaySpec(
                2,
                NotificationPacket.class,
                NotificationPacket::encode,
                NotificationPacket::decode,
                DisplayMessageHandlers.notification()
        ));
        registration.register(displaySpec(
                3,
                CitizenInfoPacket.class,
                CitizenInfoPacket::encode,
                CitizenInfoPacket::decode,
                DisplayMessageHandlers.citizenInfo()
        ));
        registration.register(displaySpec(
                4,
                TransactionHistorySyncPacket.class,
                TransactionHistorySyncPacket::encode,
                TransactionHistorySyncPacket::decode,
                DisplayMessageHandlers.transactionHistorySync()
        ));
        registration.register(displaySpec(
                5,
                GovernmentInfoPacket.class,
                GovernmentInfoPacket::encode,
                GovernmentInfoPacket::decode,
                DisplayMessageHandlers.governmentInfo()
        ));
        registration.register(displaySpec(
                6,
                ParliamentInfoPacket.class,
                ParliamentInfoPacket::encode,
                ParliamentInfoPacket::decode,
                DisplayMessageHandlers.parliamentInfo()
        ));
        registration.register(displaySpec(
                7,
                JusticeInfoPacket.class,
                JusticeInfoPacket::encode,
                JusticeInfoPacket::decode,
                DisplayMessageHandlers.justiceInfo()
        ));
        registration.register(displaySpec(
                8,
                LandInfoPacket.class,
                LandInfoPacket::encode,
                LandInfoPacket::decode,
                DisplayMessageHandlers.landInfo()
        ));
    }

    private static <MSG> NetworkMessageSpec<MSG> displaySpec(
            int id,
            Class<MSG> messageClass,
            java.util.function.BiConsumer<MSG, net.minecraft.network.FriendlyByteBuf> encoder,
            java.util.function.Function<net.minecraft.network.FriendlyByteBuf, MSG> decoder,
            NetworkMessageHandler<MSG> handler
    ) {
        return new NetworkMessageSpec<>(
                id,
                messageClass,
                NetworkDirection.PLAY_TO_CLIENT,
                encoder,
                decoder,
                handler,
                Optional.empty()
        );
    }
}
