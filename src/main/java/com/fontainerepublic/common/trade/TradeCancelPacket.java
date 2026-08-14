package com.fontainerepublic.common.trade;

import com.fontainerepublic.common.network.NetworkPayloadException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * C2S cancellation (FR-TRADE-001-A §3, message ledger ID 14, intent model).
 *
 * <p>Either party may cancel at any moment before execution; the session
 * moves to {@code CANCELLED} and is simply dropped. Because the session
 * holds only intentions — money is never pre-escrowed and offered items
 * never leave the owners' inventories — there is nothing to refund.
 * Membership and phase are re-validated server-side; the session id must be
 * positive.</p>
 */
public record TradeCancelPacket(long sessionId) {

    public TradeCancelPacket {
        if (sessionId <= 0) {
            throw new NetworkPayloadException("Session id must be positive: " + sessionId);
        }
    }

    public static void encode(TradeCancelPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeLong(message.sessionId());
    }

    public static TradeCancelPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new TradeCancelPacket(buffer.readLong());
    }
}
