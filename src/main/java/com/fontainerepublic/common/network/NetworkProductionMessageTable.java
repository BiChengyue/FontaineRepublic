package com.fontainerepublic.common.network;

import com.fontainerepublic.common.landclaim.LandClaimMessageHandlers;
import com.fontainerepublic.common.landclaim.LandClaimPacket;
import com.fontainerepublic.common.landclaim.LandInspectPacket;
import com.fontainerepublic.common.landclaim.LandInspectResultPacket;
import com.fontainerepublic.common.landclaim.LandClaimResultPacket;
import com.fontainerepublic.common.landrights.LandRightsMessageHandlers;
import com.fontainerepublic.common.landrights.MyLandRightsPagePacket;
import com.fontainerepublic.common.landrights.MyLandRightsRequestPacket;
import com.fontainerepublic.common.mail.MailBroadcastPacket;
import com.fontainerepublic.common.mail.MailDeletePacket;
import com.fontainerepublic.common.mail.MailListRequestPacket;
import com.fontainerepublic.common.mail.MailMessageHandlers;
import com.fontainerepublic.common.mail.MailReadPacket;
import com.fontainerepublic.common.mail.MailSendPacket;
import com.fontainerepublic.common.network.display.BalanceSyncPacket;
import com.fontainerepublic.common.network.display.CitizenInfoPacket;
import com.fontainerepublic.common.network.display.DisplayMessageHandlers;
import com.fontainerepublic.common.network.display.GovernmentInfoPacket;
import com.fontainerepublic.common.network.display.JusticeInfoPacket;
import com.fontainerepublic.common.network.display.LandInfoPacket;
import com.fontainerepublic.common.network.display.MailAlertPacket;
import com.fontainerepublic.common.network.display.MailboxSyncPacket;
import com.fontainerepublic.common.network.display.NotificationPacket;
import com.fontainerepublic.common.network.display.ParliamentInfoPacket;
import com.fontainerepublic.common.network.display.TransactionHistorySyncPacket;
import com.fontainerepublic.common.network.display.TransactionNotifyPacket;
import com.fontainerepublic.common.network.display.TradeStateSyncPacket;
import com.fontainerepublic.common.trade.TradeAgreePacket;
import com.fontainerepublic.common.trade.TradeCancelPacket;
import com.fontainerepublic.common.trade.TradeMessageHandlers;
import com.fontainerepublic.common.trade.TradeOfferItemPacket;
import com.fontainerepublic.common.trade.TradeOfferMoneyPacket;
import com.fontainerepublic.common.trade.TradeRequestPacket;
import com.fontainerepublic.common.trade.TradeRespondPacket;
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
 * FR-TRADE-001-A appends the first C2S entries (IDs 9-14) and the trade
 * snapshot (ID 15); every C2S entry carries an explicit rate policy.
 */
public final class NetworkProductionMessageTable {

    /** Total number of messages expected by the current protocol revision. */
    public static final int EXPECTED_MESSAGE_COUNT = 29;

    // Per-message C2S rate policies (FR-NET-001 rate limiting; bounded
    // interactive abuse control, not business authority).
    private static final RateLimitPolicy TRADE_REQUEST_POLICY =
            new RateLimitPolicy(5, 1, 2_000_000_000L, 500_000_000L);
    private static final RateLimitPolicy TRADE_RESPOND_POLICY =
            new RateLimitPolicy(5, 1, 2_000_000_000L, 500_000_000L);
    private static final RateLimitPolicy TRADE_OFFER_POLICY =
            new RateLimitPolicy(10, 2, 1_000_000_000L, 200_000_000L);
    private static final RateLimitPolicy TRADE_AGREE_POLICY =
            new RateLimitPolicy(10, 2, 1_000_000_000L, 200_000_000L);
    private static final RateLimitPolicy TRADE_CANCEL_POLICY =
            new RateLimitPolicy(10, 2, 1_000_000_000L, 200_000_000L);
    // FR-MAIL-001-A: mail C2S rate policies (bounded interactive abuse
    // control; never business authority).
    private static final RateLimitPolicy MAIL_SEND_POLICY =
            new RateLimitPolicy(6, 2, 3_000_000_000L, 500_000_000L);
    private static final RateLimitPolicy MAIL_LIST_POLICY =
            new RateLimitPolicy(20, 4, 1_000_000_000L, 100_000_000L);
    private static final RateLimitPolicy MAIL_READ_POLICY =
            new RateLimitPolicy(20, 4, 1_000_000_000L, 100_000_000L);
    private static final RateLimitPolicy MAIL_DELETE_POLICY =
            new RateLimitPolicy(20, 4, 1_000_000_000L, 100_000_000L);
    private static final RateLimitPolicy MAIL_BROADCAST_POLICY =
            new RateLimitPolicy(2, 1, 5_000_000_000L, 1_000_000_000L);
    // FR-LAND-CLAIM-001-A: land-claim C2S rate policies (bounded interactive
    // abuse control; never business authority).
    private static final RateLimitPolicy LAND_INSPECT_POLICY =
            new RateLimitPolicy(10, 2, 1_000_000_000L, 100_000_000L);
    private static final RateLimitPolicy LAND_CLAIM_POLICY =
            new RateLimitPolicy(4, 1, 2_000_000_000L, 500_000_000L);
    // FR-LAND-002-A §7: self-only my-usage-rights page query C2S rate policy
    // (bounded interactive abuse control; never business authority). Short
    // burst 2, sustained ~10 requests/second (one token refill per 100ms).
    private static final RateLimitPolicy MY_LAND_QUERY_POLICY =
            new RateLimitPolicy(2, 1, 100_000_000L, 100_000_000L);

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
        // FR-TRADE-001-A: C2S trade ledger (IDs 9-14, each with a rate
        // policy) and the per-viewer S2C snapshot (ID 15).
        registration.register(c2sSpec(
                9,
                TradeRequestPacket.class,
                TradeRequestPacket::encode,
                TradeRequestPacket::decode,
                TradeMessageHandlers.request(),
                TRADE_REQUEST_POLICY
        ));
        registration.register(c2sSpec(
                10,
                TradeRespondPacket.class,
                TradeRespondPacket::encode,
                TradeRespondPacket::decode,
                TradeMessageHandlers.respond(),
                TRADE_RESPOND_POLICY
        ));
        registration.register(c2sSpec(
                11,
                TradeOfferMoneyPacket.class,
                TradeOfferMoneyPacket::encode,
                TradeOfferMoneyPacket::decode,
                TradeMessageHandlers.offerMoney(),
                TRADE_OFFER_POLICY
        ));
        registration.register(c2sSpec(
                12,
                TradeOfferItemPacket.class,
                TradeOfferItemPacket::encode,
                TradeOfferItemPacket::decode,
                TradeMessageHandlers.offerItem(),
                TRADE_OFFER_POLICY
        ));
        registration.register(c2sSpec(
                13,
                TradeAgreePacket.class,
                TradeAgreePacket::encode,
                TradeAgreePacket::decode,
                TradeMessageHandlers.agree(),
                TRADE_AGREE_POLICY
        ));
        registration.register(c2sSpec(
                14,
                TradeCancelPacket.class,
                TradeCancelPacket::encode,
                TradeCancelPacket::decode,
                TradeMessageHandlers.cancel(),
                TRADE_CANCEL_POLICY
        ));
        registration.register(displaySpec(
                15,
                TradeStateSyncPacket.class,
                TradeStateSyncPacket::encode,
                TradeStateSyncPacket::decode,
                DisplayMessageHandlers.tradeStateSync()
        ));
        // FR-MAIL-001-A: mail ledger (IDs 16-22). C2S send/list/read/delete/
        // broadcast carry explicit rate policies; sync/alert are S2C (ID 20,
        // 21).
        registration.register(c2sSpec(
                16,
                MailSendPacket.class,
                MailSendPacket::encode,
                MailSendPacket::decode,
                MailMessageHandlers.send(),
                MAIL_SEND_POLICY
        ));
        registration.register(c2sSpec(
                17,
                MailListRequestPacket.class,
                MailListRequestPacket::encode,
                MailListRequestPacket::decode,
                MailMessageHandlers.list(),
                MAIL_LIST_POLICY
        ));
        registration.register(c2sSpec(
                18,
                MailReadPacket.class,
                MailReadPacket::encode,
                MailReadPacket::decode,
                MailMessageHandlers.read(),
                MAIL_READ_POLICY
        ));
        registration.register(c2sSpec(
                19,
                MailDeletePacket.class,
                MailDeletePacket::encode,
                MailDeletePacket::decode,
                MailMessageHandlers.delete(),
                MAIL_DELETE_POLICY
        ));
        registration.register(displaySpec(
                20,
                MailboxSyncPacket.class,
                MailboxSyncPacket::encode,
                MailboxSyncPacket::decode,
                DisplayMessageHandlers.mailboxSync()
        ));
        registration.register(displaySpec(
                21,
                MailAlertPacket.class,
                MailAlertPacket::encode,
                MailAlertPacket::decode,
                DisplayMessageHandlers.mailAlert()
        ));
        registration.register(c2sSpec(
                22,
                MailBroadcastPacket.class,
                MailBroadcastPacket::encode,
                MailBroadcastPacket::decode,
                MailMessageHandlers.broadcast(),
                MAIL_BROADCAST_POLICY
        ));
        // FR-LAND-CLAIM-001-A: land-claim ledger (IDs 23-26). C2S
        // inspect/claim carry explicit rate policies; S2C results (24, 26)
        // are display-only.
        registration.register(c2sSpec(
                23,
                LandInspectPacket.class,
                LandInspectPacket::encode,
                LandInspectPacket::decode,
                LandClaimMessageHandlers.inspect(),
                LAND_INSPECT_POLICY
        ));
        registration.register(displaySpec(
                24,
                LandInspectResultPacket.class,
                LandInspectResultPacket::encode,
                LandInspectResultPacket::decode,
                DisplayMessageHandlers.landInspectResult()
        ));
        registration.register(c2sSpec(
                25,
                LandClaimPacket.class,
                LandClaimPacket::encode,
                LandClaimPacket::decode,
                LandClaimMessageHandlers.claim(),
                LAND_CLAIM_POLICY
        ));
        registration.register(displaySpec(
                26,
                LandClaimResultPacket.class,
                LandClaimResultPacket::encode,
                LandClaimResultPacket::decode,
                DisplayMessageHandlers.landClaimResult()
        ));
        // FR-LAND-002-A: self-only my-usage-rights page (IDs 27/28). C2S query
        // carries an explicit rate policy; the S2C page is display-only.
        registration.register(c2sSpec(
                27,
                MyLandRightsRequestPacket.class,
                MyLandRightsRequestPacket::encode,
                MyLandRightsRequestPacket::decode,
                LandRightsMessageHandlers.request(),
                MY_LAND_QUERY_POLICY
        ));
        registration.register(displaySpec(
                28,
                MyLandRightsPagePacket.class,
                MyLandRightsPagePacket::encode,
                MyLandRightsPagePacket::decode,
                DisplayMessageHandlers.myLandRightsPage()
        ));
    }

    private static <MSG> NetworkMessageSpec<MSG> c2sSpec(
            int id,
            Class<MSG> messageClass,
            java.util.function.BiConsumer<MSG, net.minecraft.network.FriendlyByteBuf> encoder,
            java.util.function.Function<net.minecraft.network.FriendlyByteBuf, MSG> decoder,
            NetworkMessageHandler<MSG> handler,
            RateLimitPolicy rateLimitPolicy
    ) {
        return new NetworkMessageSpec<>(
                id,
                messageClass,
                NetworkDirection.PLAY_TO_SERVER,
                encoder,
                decoder,
                handler,
                Optional.of(rateLimitPolicy)
        );
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
