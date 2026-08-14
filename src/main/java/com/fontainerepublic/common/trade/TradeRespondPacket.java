package com.fontainerepublic.common.trade;

import com.fontainerepublic.common.network.NetworkPayloadException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * C2S response of the invited player (FR-TRADE-001-A §3, message ledger ID
 * 10). {@code accept = true} moves the session {@code REQUESTED -> OPEN};
 * {@code false} cancels it. The session id must be a positive server-issued
 * id; membership and phase are re-validated server-side.
 */
public record TradeRespondPacket(long sessionId, boolean accept) {

    public TradeRespondPacket {
        if (sessionId <= 0) {
            throw new NetworkPayloadException("Session id must be positive: " + sessionId);
        }
    }

    public static void encode(TradeRespondPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeLong(message.sessionId());
        buffer.writeBoolean(message.accept());
    }

    public static TradeRespondPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new TradeRespondPacket(buffer.readLong(), buffer.readBoolean());
    }
}
