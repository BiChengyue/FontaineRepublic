package com.fontainerepublic.server.parliament.api;

import com.fontainerepublic.server.parliament.model.Proposal;

/**
 * Immutable, bounded draft for one constitutional amendment proposal
 * (FR-PAR-002-A §2/§3).
 *
 * <p>Validation is fail-fast and mirrors {@link ProposalDraft}: bounded
 * title/full text. The amendment always carries the
 * {@code CONSTITUTION_BASIC} norm level (a first-layer amendment changes the
 * constitution itself) and enters the amendment pipeline
 * ({@code PROPOSED -> COURT_REVIEW -> PARLIAMENT_VOTE -> REFERENDUM ->
 * GUARDIAN_CONSENT -> APPROVED -> PUBLISHED}). The proposer and the on-site
 * context are not part of the draft — the proposer comes from the validated
 * on-site context at the final mutation boundary.</p>
 */
public record AmendmentDraft(
        String title,
        String fullText
) {

    public AmendmentDraft {
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
