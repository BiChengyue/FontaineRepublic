package com.fontainerepublic.common.network;

/**
 * Immutable transport quota for one future C2S message.
 */
public record RateLimitPolicy(
        int capacity,
        int refillTokens,
        long refillIntervalNanos,
        long minimumSpacingNanos
) {
    public RateLimitPolicy {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Rate-limit capacity must be positive");
        }
        if (refillTokens <= 0 || refillTokens > capacity) {
            throw new IllegalArgumentException(
                    "Refill tokens must be between 1 and capacity"
            );
        }
        if (refillIntervalNanos <= 0) {
            throw new IllegalArgumentException("Refill interval must be positive");
        }
        if (minimumSpacingNanos < 0) {
            throw new IllegalArgumentException("Minimum spacing must not be negative");
        }
    }
}
