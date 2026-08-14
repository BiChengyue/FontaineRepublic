package com.fontainerepublic.common.trade;

import com.fontainerepublic.common.network.NetworkPayloadException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * C2S agreement toggle (FR-TRADE-001-A §3, message ledger ID 13).
 *
 * <p>{@code agree = true} marks the sender ready; when both sides agree the
 * session enters the 5-second {@code LOCKED} confirmation window.
 * {@code agree = false} (or any agreement toggle during the window) reverts
 * the session to {@code OPEN} and restarts the countdown. Membership and
 * phase are re-validated server-side.</p>
 */
public record TradeAgreePacket(long sessionId, boolean agree) {

    public TradeAgreePacket {
        if (sessionId <= 0) {
            throw new NetworkPayloadException("Session id must be positive: " + sessionId);
        }
    }

    public static void encode(TradeAgreePacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeLong(message.sessionId());
        buffer.writeBoolean(message.agree());
    }

    public static TradeAgreePacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new TradeAgreePacket(buffer.readLong(), buffer.readBoolean());
    }
}
