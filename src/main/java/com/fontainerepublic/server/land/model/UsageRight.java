package com.fontainerepublic.server.land.model;

import com.fontainerepublic.server.registry.model.OwnerReference;

import java.util.Objects;

/**
 * Immutable, value-style usage right of one holder on one parcel
 * (FR-LAND-001-A §3.2).
 *
 * <p>{@code holder} is a typed {@link OwnerReference} ({@code PLAYER_UUID} in
 * Alpha; future kinds need a reviewed adapter). {@code grantedAt} is
 * server-assigned; {@code expiresAt} is {@code 0} for a grant without expiry
 * or a positive epoch millisecond after {@code grantedAt}. Renewal replaces
 * the right and increments {@code rightRevision} exactly once.</p>
 */
public record UsageRight(
        int schemaVersion,
        OwnerReference holder,
        UsageType usageType,
        long grantedAt,
        long expiresAt,
        long rightRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public UsageRight {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported usage right schema version: " + schemaVersion
            );
        }
        holder = Objects.requireNonNull(holder, "holder");
        usageType = Objects.requireNonNull(usageType, "usageType");
        if (grantedAt <= 0) {
            throw new IllegalArgumentException(
                    "grantedAt must be a positive epoch millisecond"
            );
        }
        if (expiresAt != 0 && expiresAt <= grantedAt) {
            throw new IllegalArgumentException(
                    "expiresAt must be 0 (no expiry) or after grantedAt"
            );
        }
        if (rightRevision <= 0) {
            throw new IllegalArgumentException("rightRevision must be positive");
        }
    }

    /** Whether the right is valid at the given epoch millisecond. */
    public boolean validAt(long timestamp) {
        return expiresAt == 0 || timestamp < expiresAt;
    }

    /**
     * Replacement right with a renewed expiry: the original {@code grantedAt}
     * is kept, {@code expiresAt} is replaced, and {@code rightRevision} is
     * incremented exactly once.
     */
    public UsageRight withRenewal(long newExpiresAt) {
        if (newExpiresAt != 0 && newExpiresAt <= grantedAt) {
            throw new IllegalArgumentException(
                    "newExpiresAt must be 0 (no expiry) or after the original grantedAt"
            );
        }
        return new UsageRight(
                schemaVersion,
                holder,
                usageType,
                grantedAt,
                newExpiresAt,
                rightRevision + 1
        );
    }
}
