package com.fontainerepublic.server.government.model;

import com.fontainerepublic.server.registry.model.OwnerReference;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable office record: the appointment state of one position
 * (FR-GOV-001-A §3.3).
 *
 * <p>{@code officeId} is server-assigned and immutable; the office is keyed
 * by its {@code positionId} in the store (at most one office record per
 * position). {@code assignedAt} is the appointment timestamp; {@code revokedAt}
 * is empty while the office is current and set on dismissal.
 * {@code officeRevision} increments once per committed mutation. Appointment
 * and dismissal each produce one complete replacement snapshot.</p>
 */
public record Office(
        int schemaVersion,
        UUID officeId,
        PositionId positionId,
        OwnerReference holderRef,
        long assignedAt,
        Optional<Long> revokedAt,
        long officeRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public Office {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported office schema version: " + schemaVersion
            );
        }
        officeId = Objects.requireNonNull(officeId, "officeId");
        positionId = Objects.requireNonNull(positionId, "positionId");
        holderRef = Objects.requireNonNull(holderRef, "holderRef");
        revokedAt = revokedAt == null ? Optional.empty() : revokedAt;
        if (officeRevision <= 0) {
            throw new IllegalArgumentException("officeRevision must be positive");
        }
        if (assignedAt <= 0) {
            throw new IllegalArgumentException(
                    "assignedAt must be a positive epoch millisecond"
            );
        }
        revokedAt.ifPresent(revoked -> {
            if (revoked < assignedAt) {
                throw new IllegalArgumentException(
                        "revokedAt must not precede assignedAt"
                );
            }
        });
        if (!officeId.toString().equals(officeId.toString().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("officeId must be a canonical UUID");
        }
    }

    /** Whether this office is the current (not revoked) office of its position. */
    public boolean current() {
        return revokedAt.isEmpty();
    }

    /** Replacement office marked revoked at the given time: revision +1 exactly once. */
    public Office withRevoked(long atMillis) {
        if (atMillis < assignedAt) {
            throw new IllegalArgumentException(
                    "revocation time must not precede the assignment time"
            );
        }
        return new Office(
                schemaVersion,
                officeId,
                positionId,
                holderRef,
                assignedAt,
                Optional.of(atMillis),
                officeRevision + 1
        );
    }
}
