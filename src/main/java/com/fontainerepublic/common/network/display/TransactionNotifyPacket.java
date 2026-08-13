package com.fontainerepublic.common.network.display;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * S2C presentation payload: one transaction notice for a participant of an
 * ordinary transfer (FR-CLIENT-001-A §4.2, message ledger ID 1). Sent only
 * to the two participants themselves.
 *
 * <p>{@code direction} is {@code 0 = incoming} and {@code 1 = outgoing}
 * relative to the receiver. {@code counterpartyDigest} is a bounded hex
 * digest of the counterparty subject identity (display only; never a routing
 * key). {@code memo} is optional and display-only. All bounds are enforced
 * at construction and at decode.</p>
 */
public record TransactionNotifyPacket(
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

    public TransactionNotifyPacket {
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
        if (counterpartyDigest.isEmpty() || counterpartyDigest.length() > MAX_COUNTERPARTY_DIGEST) {
            throw new NetworkPayloadException(
                    "counterpartyDigest must be 1.." + MAX_COUNTERPARTY_DIGEST + " hex characters"
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

    public static void encode(TransactionNotifyPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeLong(message.transactionId());
        buffer.writeByte(message.direction());
        buffer.writeLong(message.amount());
        NetworkPayloadLimits.writeUtf(buffer, message.counterpartyDigest(), MAX_COUNTERPARTY_DIGEST);
        buffer.writeBoolean(message.memo() != null);
        if (message.memo() != null) {
            NetworkPayloadLimits.writeUtf(buffer, message.memo(), MAX_MEMO);
        }
        buffer.writeLong(message.at());
    }

    public static TransactionNotifyPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        long transactionId = buffer.readLong();
        byte direction = buffer.readByte();
        long amount = buffer.readLong();
        String digest = NetworkPayloadLimits.readUtf(buffer, MAX_COUNTERPARTY_DIGEST);
        boolean hasMemo = buffer.readBoolean();
        String memo = hasMemo ? NetworkPayloadLimits.readUtf(buffer, MAX_MEMO) : null;
        long at = buffer.readLong();
        return new TransactionNotifyPacket(transactionId, direction, amount, digest, memo, at);
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
