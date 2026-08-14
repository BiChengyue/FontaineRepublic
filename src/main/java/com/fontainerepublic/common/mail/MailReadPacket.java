package com.fontainerepublic.common.mail;

import com.fontainerepublic.common.network.NetworkPayloadException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * C2S mail read/claim (FR-MAIL-001-A §3/§6.2, message ledger ID 18).
 * The sender is the recipient; the server marks the mail read and, for a
 * direct mail with unclaimed attachments, atomically claims them. {@code mailId}
 * must be positive.
 */
public record MailReadPacket(long mailId) {

    public MailReadPacket {
        if (mailId <= 0) {
            throw new NetworkPayloadException("mailId must be positive");
        }
    }

    public static void encode(MailReadPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeLong(message.mailId());
    }

    public static MailReadPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new MailReadPacket(buffer.readLong());
    }
}
