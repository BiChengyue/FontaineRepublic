package com.fontainerepublic.server.government.api;

import com.fontainerepublic.server.government.model.GovernmentPosition;
import com.fontainerepublic.server.government.model.MinistryId;

/**
 * Immutable, bounded request for one position creation (FR-GOV-001-A §4).
 *
 * <p>Validation is fail-fast: the title is trimmed, must be non-blank and at
 * most {@link GovernmentPosition#MAX_TITLE_LENGTH} characters; the ministry
 * must exist at mutation time (checked by the service). The
 * {@code positionId} is server-assigned and therefore not part of the
 * request.</p>
 */
public record CreatePositionRequest(MinistryId ministryId, String title) {

    public CreatePositionRequest {
        if (ministryId == null) {
            throw new IllegalArgumentException("ministryId must not be null");
        }
        String normalized = title == null ? null : title.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (normalized.length() > GovernmentPosition.MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "title exceeds bound of "
                            + GovernmentPosition.MAX_TITLE_LENGTH + " characters"
            );
        }
        title = normalized;
    }
}
