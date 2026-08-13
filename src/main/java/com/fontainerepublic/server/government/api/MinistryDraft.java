package com.fontainerepublic.server.government.api;

import com.fontainerepublic.server.government.model.Ministry;

/**
 * Immutable, bounded draft for one ministry creation (FR-GOV-001-A §4).
 *
 * <p>Validation is fail-fast: the name is trimmed, must be non-blank and at
 * most {@link Ministry#MAX_NAME_LENGTH} characters. The {@code ministryId} is
 * server-assigned and therefore not part of the draft.</p>
 */
public record MinistryDraft(String name) {

    public MinistryDraft {
        String normalized = name == null ? null : name.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (normalized.length() > Ministry.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "name exceeds bound of " + Ministry.MAX_NAME_LENGTH + " characters"
            );
        }
        name = normalized;
    }
}
