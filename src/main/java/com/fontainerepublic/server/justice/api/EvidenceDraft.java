package com.fontainerepublic.server.justice.api;

import com.fontainerepublic.server.justice.model.CaseId;

import java.util.Objects;
import java.util.Optional;

/**
 * Authoritative evidence-submission draft (FR-JUS-001-A §3.2/§4).
 *
 * <p>The submitting party is taken from the validated on-site context at the
 * final mutation boundary; the description is bounded and
 * {@code integrityDigest} is an optional bounded canonical digest of the
 * referenced material. Evidence is append-only once submitted.</p>
 *
 * @param caseId          the case the evidence belongs to
 * @param description     bounded human description of the evidence
 * @param integrityDigest optional bounded integrity digest
 */
public record EvidenceDraft(
        CaseId caseId,
        String description,
        Optional<String> integrityDigest
) {

    public EvidenceDraft {
        caseId = Objects.requireNonNull(caseId, "caseId");
        description = Objects.requireNonNull(description, "description");
        integrityDigest = integrityDigest == null ? Optional.empty() : integrityDigest;
    }
}
