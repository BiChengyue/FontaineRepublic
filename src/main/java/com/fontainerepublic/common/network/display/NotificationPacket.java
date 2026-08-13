package com.fontainerepublic.common.network.display;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Objects;

/**
 * S2C presentation payload: the bounded summary of the receiver's pending
 * notifications delivered on login (FR-CLIENT-001-A §4.2, message ledger
 * ID 2). {@code entries} carries the notification id, amount and optional
 * display memo; the sender identity is intentionally not disclosed on the
 * wire. The count is bounded to {@value #MAX_ENTRIES} and the empty list is
 * legal (no pending notifications). All bounds are enforced at construction
 * and at decode.</p>
 */
public record NotificationPacket(List<NotificationEntry> entries) {

    public static final int MAX_ENTRIES = 128;
    public static final int MAX_MEMO = 128;

    public NotificationPacket {
        entries = Objects.requireNonNull(entries, "entries");
        if (entries.size() > MAX_ENTRIES) {
            throw new NetworkPayloadException(
                    "Notification entries exceed " + MAX_ENTRIES + ": " + entries.size()
            );
        }
        entries = List.copyOf(entries);
        for (NotificationEntry entry : entries) {
            Objects.requireNonNull(entry, "notification entry");
        }
    }

    public int count() {
        return entries.size();
    }

    public static void encode(NotificationPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        NetworkPayloadLimits.writeCollection(
                buffer,
                message.entries(),
                MAX_ENTRIES,
                (buf, entry) -> NotificationEntry.encode(entry, buf)
        );
    }

    public static NotificationPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new NotificationPacket(
                NetworkPayloadLimits.readList(buffer, MAX_ENTRIES, NotificationEntry::decode)
        );
    }

    /**
     * One pending-notification projection (id, amount, optional memo).
     */
    public record NotificationEntry(long notificationId, long amount, String memo) {

        public NotificationEntry {
            if (notificationId <= 0) {
                throw new NetworkPayloadException(
                        "notificationId must be positive: " + notificationId
                );
            }
            if (amount <= 0) {
                throw new NetworkPayloadException("Amount must be positive: " + amount);
            }
            if (memo != null && memo.length() > MAX_MEMO) {
                throw new NetworkPayloadException(
                        "memo exceeds " + MAX_MEMO + " characters"
                );
            }
        }

        public static void encode(NotificationEntry entry, FriendlyByteBuf buffer) {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(buffer, "buffer");
            buffer.writeLong(entry.notificationId());
            buffer.writeLong(entry.amount());
            buffer.writeBoolean(entry.memo() != null);
            if (entry.memo() != null) {
                NetworkPayloadLimits.writeUtf(buffer, entry.memo(), MAX_MEMO);
            }
        }

        public static NotificationEntry decode(FriendlyByteBuf buffer) {
            Objects.requireNonNull(buffer, "buffer");
            long notificationId = buffer.readLong();
            long amount = buffer.readLong();
            boolean hasMemo = buffer.readBoolean();
            String memo = hasMemo ? NetworkPayloadLimits.readUtf(buffer, MAX_MEMO) : null;
            return new NotificationEntry(notificationId, amount, memo);
        }
    }
}
