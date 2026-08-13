package com.fontainerepublic.server.parliament.api;

import com.fontainerepublic.server.parliament.model.NormLevel;
import com.fontainerepublic.server.parliament.model.Proposal;

/**
 * Immutable, bounded draft for one proposal submission (FR-PAR-001-A §4).
 *
 * <p>Validation is fail-fast: the title and full text are trimmed, must be
 * non-blank and bounded, and the normative level must be a closed enum value
 * (ADMINISTRATIVE rules are accepted for the administrative pipeline only;
 * the constitution-amendment pipeline is a deferred revision and therefore
 * {@link NormLevel#CONSTITUTION_BASIC} proposals are restricted at the
 * service layer, not here). The proposer and the on-site context are not
 * part of the draft — the proposer comes from the validated on-site context
 * at the final mutation boundary.</p>
 */
public record ProposalDraft(
        String title,
        NormLevel normLevel,
        String fullText
) {

    public ProposalDraft {
        if (normLevel == null) {
            throw new IllegalArgumentException("normLevel must not be null");
        }
        title = normalizeBounded(title, "title", Proposal.MAX_TITLE_LENGTH);
        fullText = normalizeBounded(fullText, "fullText", Proposal.MAX_FULL_TEXT_LENGTH);
    }

    private static String normalizeBounded(String value, String field, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " exceeds bound of " + maxLength + " characters"
            );
        }
        return normalized;
    }
}
