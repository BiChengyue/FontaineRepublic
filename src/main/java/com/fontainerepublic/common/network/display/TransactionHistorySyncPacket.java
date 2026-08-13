package com.fontainerepublic.common.network.display;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Objects;

/**
 * S2C presentation payload: one bounded page of the receiver's own
 * transaction history (FR-CLIENT-001-A §4.2, message ledger ID 4;
 * FR-CLIENT-001-IMPL-B2). Sent on login/account-ready and replaced on every
 * resync; the first release sends only the first page ({@code afterId = 0}).
 *
 * <p>{@code direction} is {@code 0 = incoming} and {@code 1 = outgoing}
 * relative to the receiver. {@code counterpartyDigest} is a bounded hex
 * digest of the counterparty subject identity (display only; a fixed system
 * digest is used for official treasury transactions). {@code nextAfterId} is
 * the cursor for the following page and {@code hasMore} whether more records
 * exist; {@code at} is the snapshot time. All bounds are enforced at
 * construction and at decode.</p>
 */
public record TransactionHistorySyncPacket(
        List<HistoryEntry> entries,
        long nextAfterId,
        boolean hasMore,
        long at
) {

    public static final int MAX_ENTRIES = 128;

    public TransactionHistorySyncPacket {
        entries = Objects.requireNonNull(entries, "entries");
        if (entries.size() > MAX_ENTRIES) {
            throw new NetworkPayloadException(
                    "History entries exceed " + MAX_ENTRIES + ": " + entries.size()
            );
        }
        entries = List.copyOf(entries);
        for (HistoryEntry entry : entries) {
            Objects.requireNonNull(entry, "history entry");
        }
        if (nextAfterId < 0) {
            throw new NetworkPayloadException(
                    "nextAfterId must not be negative: " + nextAfterId
            );
        }
        if (at <= 0) {
            throw new NetworkPayloadException("at must be a positive timestamp: " + at);
        }
    }

    public int count() {
        return entries.size();
    }

    public static void encode(TransactionHistorySyncPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        NetworkPayloadLimits.writeCollection(
                buffer,
                message.entries(),
                MAX_ENTRIES,
                (buf, entry) -> HistoryEntry.encode(entry, buf)
        );
        buffer.writeLong(message.nextAfterId());
        buffer.writeBoolean(message.hasMore());
        buffer.writeLong(message.at());
    }

    public static TransactionHistorySyncPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        List<HistoryEntry> entries =
                NetworkPayloadLimits.readList(buffer, MAX_ENTRIES, HistoryEntry::decode);
        long nextAfterId = buffer.readLong();
        boolean hasMore = buffer.readBoolean();
        long at = buffer.readLong();
        return new TransactionHistorySyncPacket(entries, nextAfterId, hasMore, at);
    }

    /**
     * One history-page projection (transaction id, receiver-relative
     * direction, amount, counterparty digest, optional memo, timestamp).
     */
    public record HistoryEntry(
            long transactionId,
            byte direction,
            long amount,
            String counterpartyDigest,
            String memo,
            long at
    ) {

        public static final byte DIRECTION_IN = 0;
        public static final byte DIRECTION_OUT = 1;
        public static final int MAX_COUNTERPARTY_DIGEST = 64;
        public static final int MAX_MEMO = 128;

        public HistoryEntry {
            if (transactionId <= 0) {
                throw new NetworkPayloadException(
                        "transactionId must be positive: " + transactionId
                );
            }
            if (direction != DIRECTION_IN && direction != DIRECTION_OUT) {
                throw new NetworkPayloadException("Invalid direction: " + direction);
            }
            if (amount <= 0) {
                throw new NetworkPayloadException("Amount must be positive: " + amount);
            }
            counterpartyDigest = Objects.requireNonNull(
                    counterpartyDigest, "counterpartyDigest"
            );
            if (counterpartyDigest.isEmpty()
                    || counterpartyDigest.length() > MAX_COUNTERPARTY_DIGEST) {
                throw new NetworkPayloadException(
                        "counterpartyDigest must be 1.." + MAX_COUNTERPARTY_DIGEST
                                + " hex characters"
                );
            }
            requireHex(counterpartyDigest);
            if (memo != null && memo.length() > MAX_MEMO) {
                throw new NetworkPayloadException(
                        "memo exceeds " + MAX_MEMO + " characters"
                );
            }
            if (at <= 0) {
                throw new NetworkPayloadException("at must be a positive timestamp: " + at);
            }
        }

        public static void encode(HistoryEntry entry, FriendlyByteBuf buffer) {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(buffer, "buffer");
            buffer.writeLong(entry.transactionId());
            buffer.writeByte(entry.direction());
            buffer.writeLong(entry.amount());
            NetworkPayloadLimits.writeUtf(buffer, entry.counterpartyDigest(), MAX_COUNTERPARTY_DIGEST);
            buffer.writeBoolean(entry.memo() != null);
            if (entry.memo() != null) {
                NetworkPayloadLimits.writeUtf(buffer, entry.memo(), MAX_MEMO);
            }
            buffer.writeLong(entry.at());
        }

        public static HistoryEntry decode(FriendlyByteBuf buffer) {
            Objects.requireNonNull(buffer, "buffer");
            long transactionId = buffer.readLong();
            byte direction = buffer.readByte();
            long amount = buffer.readLong();
            String digest = NetworkPayloadLimits.readUtf(buffer, MAX_COUNTERPARTY_DIGEST);
            boolean hasMemo = buffer.readBoolean();
            String memo = hasMemo ? NetworkPayloadLimits.readUtf(buffer, MAX_MEMO) : null;
            long at = buffer.readLong();
            return new HistoryEntry(transactionId, direction, amount, digest, memo, at);
        }

        private static void requireHex(String value) {
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                boolean hexDigit = (current >= '0' && current <= '9')
                        || (current >= 'a' && current <= 'f')
                        || (current >= 'A' && current <= 'F');
                if (!hexDigit) {
                    throw new NetworkPayloadException(
                            "counterpartyDigest is not hex: " + value
                    );
                }
            }
        }
    }
}
