package com.fontainerepublic.common.mail;

import com.fontainerepublic.common.network.NetworkPayloadException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * C2S mail delete (FR-MAIL-001-A §3, message ledger ID 19). Removes a direct
 * mail from the sender-recipient's mailbox or suppresses a broadcast for them.
 * {@code mailId} must be positive.
 */
public record MailDeletePacket(long mailId) {

    public MailDeletePacket {
        if (mailId <= 0) {
            throw new NetworkPayloadException("mailId must be positive");
        }
    }

    public static void encode(MailDeletePacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeLong(message.mailId());
    }

    public static MailDeletePacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new MailDeletePacket(buffer.readLong());
    }
}
