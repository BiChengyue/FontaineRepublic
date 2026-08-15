package com.fontainerepublic.common.trade;

import com.fontainerepublic.common.network.NetworkMessageHandler;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * FR-TRADE-004: retired receiving-side handlers of the old intent-model
 * trade C2S ledger (protocol IDs 9-14, 29).
 *
 * <p>The intent-model trade service (the broken item/XP legs) was removed and
 * replaced by the copied Navielon/SecureTrade escrow surface (which runs on
 * its own {@code fontainerepublic:trade} sub-channel). These ledger slots are
 * protocol-frozen (append-only, never reused) and must remain registered, so
 * each handler now logs and drops the message: no authority, no side effect.</p>
 */
public final class TradeMessageHandlers {

    private static final Logger LOGGER = LogUtils.getLogger();

    private TradeMessageHandlers() {
    }

    public static NetworkMessageHandler<TradeRequestPacket> request() {
        return (message, context) -> dropped("request");
    }

    public static NetworkMessageHandler<TradeRespondPacket> respond() {
        return (message, context) -> dropped("respond");
    }

    public static NetworkMessageHandler<TradeOfferMoneyPacket> offerMoney() {
        return (message, context) -> dropped("offer-money");
    }

    public static NetworkMessageHandler<TradeOfferItemPacket> offerItem() {
        return (message, context) -> dropped("offer-item");
    }

    public static NetworkMessageHandler<TradeOfferXpPacket> offerXp() {
        return (message, context) -> dropped("offer-xp");
    }

    public static NetworkMessageHandler<TradeAgreePacket> agree() {
        return (message, context) -> dropped("agree");
    }

    public static NetworkMessageHandler<TradeCancelPacket> cancel() {
        return (message, context) -> dropped("cancel");
    }

    private static void dropped(String action) {
        LOGGER.warn(
                "[Trade] Retired intent-model C2S message ({}) dropped; use the "
                        + "Secure Trade escrow UI (/trade) instead",
                action
        );
    }
}
