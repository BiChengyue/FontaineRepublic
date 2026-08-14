package com.fontainerepublic.client.trade;

import com.fontainerepublic.common.network.NetworkBootstrap;
import com.fontainerepublic.common.trade.TradeAgreePacket;
import com.fontainerepublic.common.trade.TradeCancelPacket;
import com.fontainerepublic.common.trade.TradeOfferItemPacket;
import com.fontainerepublic.common.trade.TradeOfferMoneyPacket;
import com.fontainerepublic.common.trade.TradeOfferXpPacket;
import com.fontainerepublic.common.trade.TradeRequestPacket;
import com.fontainerepublic.common.trade.TradeRespondPacket;

import java.util.Objects;
import java.util.UUID;

/**
 * Client-side C2S sender of the trade ledger (FR-TRADE-001-A §3, message
 * ledger IDs 9-14).
 *
 * <p>Thin transport helpers: every call builds the bounded packet and hands
 * it to the frozen {@link NetworkBootstrap} C2S surface. The server re-runs
 * every authority rule; sending on a dedicated server is a no-op (no local
 * player). This class lives in {@code client/} and is never loaded by a
 * dedicated server.</p>
 */
public final class ClientTradeSender {

    private ClientTradeSender() {
    }

    public static void sendRequest(UUID target) {
        Objects.requireNonNull(target, "target");
        ClientTradeCache.instance().markOwnRequestInFlight();
        NetworkBootstrap.instance().sendToServer(new TradeRequestPacket(target));
    }

    public static void sendRespond(long sessionId, boolean accept) {
        NetworkBootstrap.instance().sendToServer(
                new TradeRespondPacket(sessionId, accept)
        );
    }

    public static void sendOfferMoney(long sessionId, long amount) {
        NetworkBootstrap.instance().sendToServer(
                new TradeOfferMoneyPacket(sessionId, amount)
        );
    }

    public static void sendOfferItem(long sessionId, int slot, int inventoryIndex) {
        NetworkBootstrap.instance().sendToServer(
                new TradeOfferItemPacket(sessionId, slot, inventoryIndex)
        );
    }

    public static void sendWithdrawItem(long sessionId, int slot) {
        sendOfferItem(sessionId, slot, TradeOfferItemPacket.WITHDRAW_MARKER);
    }

    public static void sendOfferXp(long sessionId, long xpPoints) {
        NetworkBootstrap.instance().sendToServer(
                new TradeOfferXpPacket(sessionId, xpPoints)
        );
    }

    public static void sendAgree(long sessionId, boolean agree) {
        NetworkBootstrap.instance().sendToServer(
                new TradeAgreePacket(sessionId, agree)
        );
    }

    public static void sendCancel(long sessionId) {
        NetworkBootstrap.instance().sendToServer(new TradeCancelPacket(sessionId));
    }
}
