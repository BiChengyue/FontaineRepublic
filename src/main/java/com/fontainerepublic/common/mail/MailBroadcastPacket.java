package com.fontainerepublic.common.mail;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * C2S institution broadcast (FR-MAIL-001-A §6.6, message ledger ID 22).
 * The sender must be an authorized institution actor; the server resolves the
 * acting institution, enforces the cooldown and derives the citizen range. The
 * sender is taken from the connection context.
 */
public record MailBroadcastPacket(String subject, String body) {

    public static final int MAX_SUBJECT = 64;
    public static final int MAX_BODY = 2000;

    public MailBroadcastPacket {
        subject = Objects.requireNonNull(subject, "subject");
        if (subject.length() > MAX_SUBJECT) {
            throw new NetworkPayloadException("subject exceeds " + MAX_SUBJECT + " characters");
        }
        body = Objects.requireNonNull(body, "body");
        if (body.length() > MAX_BODY) {
            throw new NetworkPayloadException("body exceeds " + MAX_BODY + " characters");
        }
    }

    public static void encode(MailBroadcastPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        NetworkPayloadLimits.writeUtf(buffer, message.subject(), MAX_SUBJECT);
        NetworkPayloadLimits.writeUtf(buffer, message.body(), MAX_BODY);
    }

    public static MailBroadcastPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new MailBroadcastPacket(
                NetworkPayloadLimits.readUtf(buffer, MAX_SUBJECT),
                NetworkPayloadLimits.readUtf(buffer, MAX_BODY)
        );
    }
}
