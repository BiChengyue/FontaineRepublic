package com.fontainerepublic.common.trade;

import com.fontainerepublic.common.network.NetworkPayloadException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

/**
 * C2S trade initiation (FR-TRADE-001-A §3, message ledger ID 9).
 *
 * <p>Carries only the target player UUID: the sender is taken from the
 * connection context on the server, the target is re-resolved and every
 * authority rule (online, communicator gate, no active session) is enforced
 * server-side on the logical main thread. The UUID is bounded by its
 * canonical 36-char form.</p>
 */
public record TradeRequestPacket(UUID target) {

    public TradeRequestPacket {
        if (target == null) {
            throw new NetworkPayloadException("Trade target must not be null");
        }
    }

    public static void encode(TradeRequestPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeUUID(message.target());
    }

    public static TradeRequestPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new TradeRequestPacket(buffer.readUUID());
    }
}
