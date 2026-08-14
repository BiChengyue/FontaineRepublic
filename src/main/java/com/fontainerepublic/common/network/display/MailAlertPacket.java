package com.fontainerepublic.common.network.display;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * S2C new-mail alert (FR-MAIL-001-A §2.3/§6.5, message ledger ID 21).
 *
 * <p>Carries the receiver's exact unread count and a bounded latest-mail
 * summary. The client shows a HUD unread badge and an optional chat line only
 * while the player's inventory contains the communicator (the server gates the
 * send of this packet on that inventory scan; the client, if it ever receives
 * it without the item, is a display-only no-op).</p>
 */
public record MailAlertPacket(int unreadCount, String summary) {

    public static final int MAX_SUMMARY = 128;

    public MailAlertPacket {
        if (unreadCount < 0) {
            throw new NetworkPayloadException(
                    "unreadCount must not be negative: " + unreadCount
            );
        }
        summary = Objects.requireNonNull(summary, "summary");
        if (summary.length() > MAX_SUMMARY) {
            throw new NetworkPayloadException(
                    "summary exceeds " + MAX_SUMMARY + " characters"
            );
        }
    }

    public static void encode(MailAlertPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeInt(message.unreadCount());
        NetworkPayloadLimits.writeUtf(buffer, message.summary(), MAX_SUMMARY);
    }

    public static MailAlertPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new MailAlertPacket(
                buffer.readInt(),
                NetworkPayloadLimits.readUtf(buffer, MAX_SUMMARY)
        );
    }
}
