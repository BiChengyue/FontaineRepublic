package com.fontainerepublic.core;

/**
 * Bounds of the durable commit gate (FR-CORE-002).
 *
 * @param minIntervalMillis minimum wall-clock interval between accepted
 *                          commits; commits that arrive sooner fail with
 *                          {@code RATE_GUARD}
 * @param maxBytesPerNamespace maximum serialized bytes accepted per committed
 *                          namespace snapshot; larger snapshots fail with
 *                          {@code BOUNDS_EXCEEDED}
 */
public record DurableCommitPolicy(
        int minIntervalMillis,
        int maxBytesPerNamespace
) {
    public static final DurableCommitPolicy DEFAULT =
            new DurableCommitPolicy(100, 8 * 1024 * 1024);
}
