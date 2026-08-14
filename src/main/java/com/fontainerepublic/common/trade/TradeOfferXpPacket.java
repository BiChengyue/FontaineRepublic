package com.fontainerepublic.common.trade;

import com.fontainerepublic.common.network.NetworkPayloadException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * C2S experience offer (FR-TRADE-003-A, intent model), adapted from
 * SecureTrade's {@code TradeXPChangePacket} (MIT, Copyright (c) 2026 Secure
 * Trade Mod Authors) and bound to the FR authoritative trade state machine.
 *
 * <p>{@code xpPoints} is the total experience-point offer ({@code 0} clears
 * the offer; a positive value replaces the current offer). The server clamps
 * the value to the player's current total and the configured
 * {@code tradeMaxOfferXp} ceiling, so the wire never decides a held amount.
 * The sender is taken from the connection context; membership and phase are
 * re-validated server-side. No XP is ever pre-escrowed.</p>
 */
public record TradeOfferXpPacket(long sessionId, long xpPoints) {

    /** Hard cap mirrored by the server-side {@code ConfigManager}. */
    public static final long MAX_OFFER_XP = 2_000_000_000_000_000L;

    public TradeOfferXpPacket {
        if (sessionId <= 0) {
            throw new NetworkPayloadException("Session id must be positive: " + sessionId);
        }
        if (xpPoints < 0 || xpPoints > MAX_OFFER_XP) {
            throw new NetworkPayloadException(
                    "XP offer must be within [0, " + MAX_OFFER_XP + "]: " + xpPoints
            );
        }
    }

    public static void encode(TradeOfferXpPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeLong(message.sessionId());
        buffer.writeLong(message.xpPoints());
    }

    public static TradeOfferXpPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new TradeOfferXpPacket(buffer.readLong(), buffer.readLong());
    }
}
