package com.fontainerepublic.common.network.display;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * S2C presentation payload: one balance snapshot of the receiver's own
 * account (FR-CLIENT-001-A §4.2, message ledger ID 0).
 *
 * <p>Carries display data only — never an authority decision. {@code seq} is
 * the server-side account revision the snapshot was taken from; the client
 * may use it to detect stale presentation. All bounds are enforced at
 * construction and at decode so the wire never carries an out-of-range
 * value.</p>
 */
public record BalanceSyncPacket(
        long balance,
        String currencyName,
        String currencySymbol,
        long at,
        long seq
) {

    public static final int MAX_CURRENCY_NAME = 32;
    public static final int MAX_CURRENCY_SYMBOL = 8;

    public BalanceSyncPacket {
        if (balance < 0) {
            throw new NetworkPayloadException("Balance must not be negative: " + balance);
        }
        currencyName = Objects.requireNonNull(currencyName, "currencyName");
        if (currencyName.length() > MAX_CURRENCY_NAME) {
            throw new NetworkPayloadException(
                    "currencyName exceeds " + MAX_CURRENCY_NAME + " characters"
            );
        }
        currencySymbol = Objects.requireNonNull(currencySymbol, "currencySymbol");
        if (currencySymbol.length() > MAX_CURRENCY_SYMBOL) {
            throw new NetworkPayloadException(
                    "currencySymbol exceeds " + MAX_CURRENCY_SYMBOL + " characters"
            );
        }
        if (at <= 0) {
            throw new NetworkPayloadException("at must be a positive timestamp: " + at);
        }
        if (seq <= 0) {
            throw new NetworkPayloadException("seq must be positive: " + seq);
        }
    }

    public static void encode(BalanceSyncPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeLong(message.balance());
        NetworkPayloadLimits.writeUtf(buffer, message.currencyName(), MAX_CURRENCY_NAME);
        NetworkPayloadLimits.writeUtf(buffer, message.currencySymbol(), MAX_CURRENCY_SYMBOL);
        buffer.writeLong(message.at());
        buffer.writeLong(message.seq());
    }

    public static BalanceSyncPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new BalanceSyncPacket(
                buffer.readLong(),
                NetworkPayloadLimits.readUtf(buffer, MAX_CURRENCY_NAME),
                NetworkPayloadLimits.readUtf(buffer, MAX_CURRENCY_SYMBOL),
                buffer.readLong(),
                buffer.readLong()
        );
    }
}
