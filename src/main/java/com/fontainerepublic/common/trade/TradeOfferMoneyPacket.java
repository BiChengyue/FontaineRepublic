package com.fontainerepublic.common.trade;

import com.fontainerepublic.common.network.NetworkPayloadException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * C2S covering money offer (FR-TRADE-001-A §3, message ledger ID 11,
 * intent model — nothing is escrowed).
 *
 * <p>{@code amount = 0} clears the offer; a positive amount replaces the
 * current offer covering the value. The session holds only the offered
 * amount as an <em>intention</em>: no money is pre-escrowed or deducted
 * from the account before the atomic execution (cancel / disconnect / server
 * shutdown simply drop the session with nothing to refund). The amount is
 * bounded at construction and at decode so the wire never carries an
 * out-of-range value; the server re-validates funds and every authority rule.</p>
 */
public record TradeOfferMoneyPacket(long sessionId, long amount) {

    /** Display/session ceiling of one offer (mirrors the transfer form cap). */
    public static final long MAX_OFFER_AMOUNT = 9_000_000_000_000_000L;

    public TradeOfferMoneyPacket {
        if (sessionId <= 0) {
            throw new NetworkPayloadException("Session id must be positive: " + sessionId);
        }
        if (amount < 0 || amount > MAX_OFFER_AMOUNT) {
            throw new NetworkPayloadException(
                    "Offer amount must be within [0, " + MAX_OFFER_AMOUNT + "]: " + amount
            );
        }
    }

    public static void encode(TradeOfferMoneyPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeLong(message.sessionId());
        buffer.writeLong(message.amount());
    }

    public static TradeOfferMoneyPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new TradeOfferMoneyPacket(buffer.readLong(), buffer.readLong());
    }
}
