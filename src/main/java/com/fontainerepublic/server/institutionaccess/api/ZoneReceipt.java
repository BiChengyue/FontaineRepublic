package com.fontainerepublic.server.institutionaccess.api;

import com.fontainerepublic.server.institutionaccess.model.Zone;

import java.util.Objects;

/**
 * Result of one zone mutation (FR-INST-002-B §7).
 *
 * @param kind     what was committed (or attempted idempotently)
 * @param zone     the zone after the mutation ({@code null} when removed)
 * @param applied  true when the state actually changed (a no-op request
 *                 commits nothing and reports {@code false})
 * @param timestamp server-assigned mutation timestamp
 */
public record ZoneReceipt(
        ZoneChangeKind kind,
        Zone zone,
        boolean applied,
        long timestamp
) {

    public ZoneReceipt {
        kind = Objects.requireNonNull(kind, "kind");
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must not be negative");
        }
    }
}
