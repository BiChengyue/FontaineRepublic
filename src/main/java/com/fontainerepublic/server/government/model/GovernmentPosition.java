package com.fontainerepublic.server.government.model;

import com.fontainerepublic.server.registry.model.OwnerReference;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, value-style authoritative government position record
 * (FR-GOV-001-A §3.1).
 *
 * <p>{@code positionId} and {@code ministryId} are server-assigned and
 * immutable; {@code title} is a bounded display string normalized at
 * construction (at most {@value #MAX_TITLE_LENGTH} characters). The holder is
 * an {@link OwnerReference} resolved through services — a game name is never
 * stored. {@code holderRef} is present if and only if the position is
 * {@link PositionState#FILLED}. {@code positionRevision} increments once per
 * committed mutation.</p>
 */
public record GovernmentPosition(
        int schemaVersion,
        PositionId positionId,
        MinistryId ministryId,
        String title,
        PositionState state,
        Optional<OwnerReference> holderRef,
        long positionRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_TITLE_LENGTH = 64;

    public GovernmentPosition {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported position schema version: " + schemaVersion
            );
        }
        positionId = Objects.requireNonNull(positionId, "positionId");
        ministryId = Objects.requireNonNull(ministryId, "ministryId");
        state = Objects.requireNonNull(state, "state");
        title = requireBoundedTitle(title);
        holderRef = holderRef == null ? Optional.empty() : holderRef;
        if (positionRevision <= 0) {
            throw new IllegalArgumentException("positionRevision must be positive");
        }
        if (state == PositionState.FILLED && holderRef.isEmpty()) {
            throw new IllegalArgumentException(
                    "a FILLED position must carry a holder"
            );
        }
        if (state != PositionState.FILLED && holderRef.isPresent()) {
            throw new IllegalArgumentException(
                    "a non-FILLED position must not carry a holder"
            );
        }
        if (!positionId.canonicalKey().equals(
                positionId.canonicalKey().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("positionId must be a canonical UUID");
        }
        if (!ministryId.canonicalKey().equals(
                ministryId.canonicalKey().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("ministryId must be a canonical UUID");
        }
    }

    /** Replacement position filled by the given holder: revision +1 exactly once. */
    public GovernmentPosition withHolder(OwnerReference holder) {
        Objects.requireNonNull(holder, "holder");
        return new GovernmentPosition(
                schemaVersion,
                positionId,
                ministryId,
                title,
                PositionState.FILLED,
                Optional.of(holder),
                positionRevision + 1
        );
    }

    /** Replacement position vacated: revision +1 exactly once. */
    public GovernmentPosition withVacated() {
        return new GovernmentPosition(
                schemaVersion,
                positionId,
                ministryId,
                title,
                PositionState.VACANT,
                Optional.empty(),
                positionRevision + 1
        );
    }

    private static String requireBoundedTitle(String value) {
        Objects.requireNonNull(value, "title");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "title exceeds bound of " + MAX_TITLE_LENGTH + " characters"
            );
        }
        return normalized;
    }
}
