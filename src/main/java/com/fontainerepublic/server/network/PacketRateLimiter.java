package com.fontainerepublic.server.network;

import com.fontainerepublic.common.network.RateLimitPolicy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Runtime-only token buckets. Mutations are restricted to one owning thread.
 */
public final class PacketRateLimiter {
    private final Map<BucketKey, Bucket> buckets = new HashMap<>();
    private Thread ownerThread;

    public RateLimitDecision tryAcquire(
            UUID playerId,
            int messageId,
            RateLimitPolicy policy,
            long nowNanos
    ) {
        requireOwnerThread();
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(policy, "policy");
        if (messageId < 0) {
            throw new IllegalArgumentException("Message ID must not be negative");
        }
        BucketKey key = new BucketKey(playerId, messageId);
        Bucket bucket = buckets.computeIfAbsent(
                key,
                ignored -> new Bucket(policy.capacity(), nowNanos)
        );
        bucket.verifyClock(nowNanos);
        bucket.refill(policy, nowNanos);

        if (bucket.hasAccepted
                && nowNanos - bucket.lastAcceptedNanos < policy.minimumSpacingNanos()) {
            return RateLimitDecision.MINIMUM_SPACING;
        }
        if (bucket.tokens <= 0) {
            return RateLimitDecision.RATE_EXCEEDED;
        }

        bucket.tokens--;
        bucket.lastAcceptedNanos = nowNanos;
        bucket.hasAccepted = true;
        return RateLimitDecision.ALLOWED;
    }

    public void removePlayer(UUID playerId) {
        requireOwnerThread();
        Objects.requireNonNull(playerId, "playerId");
        Iterator<BucketKey> iterator = buckets.keySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().playerId().equals(playerId)) {
                iterator.remove();
            }
        }
    }

    public void clear() {
        requireOwnerThread();
        buckets.clear();
    }

    public int size() {
        return buckets.size();
    }

    private void requireOwnerThread() {
        Thread current = Thread.currentThread();
        if (ownerThread == null) {
            ownerThread = current;
            return;
        }
        if (ownerThread != current) {
            throw new IllegalStateException(
                    "PacketRateLimiter may only be mutated on its owning server thread"
            );
        }
    }

    public enum RateLimitDecision {
        ALLOWED,
        RATE_EXCEEDED,
        MINIMUM_SPACING
    }

    private record BucketKey(UUID playerId, int messageId) {
        private BucketKey {
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    private static final class Bucket {
        private int tokens;
        private long lastRefillNanos;
        private long lastAcceptedNanos;
        private boolean hasAccepted;

        private Bucket(int tokens, long nowNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = nowNanos;
        }

        private void verifyClock(long nowNanos) {
            if (nowNanos < lastRefillNanos) {
                throw new IllegalArgumentException("Rate-limit clock moved backwards");
            }
        }

        private void refill(RateLimitPolicy policy, long nowNanos) {
            long elapsed = nowNanos - lastRefillNanos;
            long intervals = elapsed / policy.refillIntervalNanos();
            if (intervals <= 0) {
                return;
            }
            long intervalsToCapacity = Math.max(
                    1L,
                    (policy.capacity() - (long) tokens + policy.refillTokens() - 1L)
                            / policy.refillTokens()
            );
            if (intervals >= intervalsToCapacity) {
                tokens = policy.capacity();
            } else {
                tokens += (int) (intervals * policy.refillTokens());
            }
            lastRefillNanos += intervals * policy.refillIntervalNanos();
        }
    }
}
