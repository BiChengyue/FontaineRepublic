package com.fontainerepublic.server.government.api;

import com.fontainerepublic.server.government.model.MinistryId;
import com.fontainerepublic.server.government.model.MinistryState;

/**
 * Bounded read projection of a ministry (FR-GOV-001-A §4; no NBT, no
 * repository, no mutation surface).
 */
public record MinistryProjection(
        MinistryId ministryId,
        String name,
        MinistryState state,
        long ministryRevision
) {

    public MinistryProjection {
        if (ministryId == null) {
            throw new IllegalArgumentException("ministryId must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (ministryRevision <= 0) {
            throw new IllegalArgumentException("ministryRevision must be positive");
        }
    }
}
