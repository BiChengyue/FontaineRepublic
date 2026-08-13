package com.fontainerepublic.server.government.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable, value-style authoritative ministry record (FR-GOV-001-A §3.2).
 *
 * <p>{@code ministryId} is server-assigned and immutable; {@code name} is a
 * bounded display string normalized at construction (trimmed, non-blank, at
 * most {@value #MAX_NAME_LENGTH} characters). {@code ministryRevision}
 * increments once per committed mutation. The current contract creates
 * ministries only in {@link MinistryState#ACTIVE}.</p>
 */
public record Ministry(
        int schemaVersion,
        MinistryId ministryId,
        String name,
        MinistryState state,
        long ministryRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_NAME_LENGTH = 64;

    public Ministry {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported ministry schema version: " + schemaVersion
            );
        }
        ministryId = Objects.requireNonNull(ministryId, "ministryId");
        state = Objects.requireNonNull(state, "state");
        name = requireBoundedName(name);
        if (ministryRevision <= 0) {
            throw new IllegalArgumentException("ministryRevision must be positive");
        }
        if (!ministryId.canonicalKey().equals(
                ministryId.canonicalKey().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("ministryId must be a canonical UUID");
        }
    }

    private static String requireBoundedName(String value) {
        Objects.requireNonNull(value, "name");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "name exceeds bound of " + MAX_NAME_LENGTH + " characters"
            );
        }
        return normalized;
    }
}
