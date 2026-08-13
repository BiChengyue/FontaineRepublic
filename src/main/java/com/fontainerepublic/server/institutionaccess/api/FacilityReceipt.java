package com.fontainerepublic.server.institutionaccess.api;

import com.fontainerepublic.server.institutionaccess.model.Facility;

import java.util.Objects;

/**
 * Result of one facility mutation (FR-INST-002-A §7).
 *
 * @param kind    what was committed (or attempted idempotently)
 * @param facility the facility after the mutation
 * @param applied true when the state actually changed (a no-op request
 *                commits nothing and reports {@code false})
 * @param timestamp server-assigned mutation timestamp
 */
public record FacilityReceipt(
        FacilityChangeKind kind,
        Facility facility,
        boolean applied,
        long timestamp
) {

    public FacilityReceipt {
        kind = Objects.requireNonNull(kind, "kind");
        facility = Objects.requireNonNull(facility, "facility");
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must not be negative");
        }
    }
}
