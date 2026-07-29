package com.fontainerepublic.server.network;

import com.fontainerepublic.common.network.RateLimitPolicy;

import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Mutable transport state owned by one server runtime.
 */
public final class NetworkRuntimeService {
    private final PacketRateLimiter rateLimiter;
    private final LongSupplier nanoClock;
    private boolean active = true;

    NetworkRuntimeService(PacketRateLimiter rateLimiter, LongSupplier nanoClock) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    public PacketRateLimiter.RateLimitDecision tryAcquire(
            UUID playerId,
            int messageId,
            RateLimitPolicy policy
    ) {
        requireActive();
        return rateLimiter.tryAcquire(
                playerId,
                messageId,
                policy,
                nanoClock.getAsLong()
        );
    }

    public void removePlayer(UUID playerId) {
        requireActive();
        rateLimiter.removePlayer(playerId);
    }

    public boolean isActive() {
        return active;
    }

    public int bucketCount() {
        return rateLimiter.size();
    }

    void shutdown() {
        if (!active) {
            return;
        }
        rateLimiter.clear();
        active = false;
    }

    private void requireActive() {
        if (!active) {
            throw new IllegalStateException("Network runtime service is not active");
        }
    }
}
