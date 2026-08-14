package com.fontainerepublic.common.mail;

import com.fontainerepublic.common.network.NetworkPayloadException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * C2S mailbox list request (FR-MAIL-001-A §3, message ledger ID 17).
 *
 * <p>Returns the caller's bounded mailbox projection. {@code limit} is bounded
 * so the server always answers with a small, bounded page.</p>
 */
public record MailListRequestPacket(int limit) {

    public static final int MAX_LIMIT = 64;

    public MailListRequestPacket {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new NetworkPayloadException("limit must be within [1, " + MAX_LIMIT + "]");
        }
    }

    public static void encode(MailListRequestPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(message.limit());
    }

    public static MailListRequestPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new MailListRequestPacket(buffer.readVarInt());
    }
}
