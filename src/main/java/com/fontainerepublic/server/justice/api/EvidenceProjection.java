package com.fontainerepublic.server.justice.api;

import com.fontainerepublic.server.justice.model.Evidence;
import com.fontainerepublic.server.justice.model.EvidenceState;

import java.util.Objects;

/**
 * Bounded, ordered projection of one evidence entry for listing
 * (FR-JUS-001-A §4: reads are exact or bounded projections only — never an
 * enumeration API).
 *
 * @param evidenceSeq the per-case ascending paging cursor
 * @param evidenceId  the permanent evidence identity
 * @param caseId      the case the evidence belongs to
 * @param state       the current admissibility state
 * @param description the bounded evidence description
 */
public record EvidenceProjection(
        long evidenceSeq,
        String evidenceId,
        String caseId,
        EvidenceState state,
        String description
) {

    public static EvidenceProjection from(Evidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        return new EvidenceProjection(
                evidence.evidenceSeq(),
                evidence.evidenceId().canonicalKey(),
                evidence.caseId().canonicalKey(),
                evidence.state(),
                evidence.description()
        );
    }

    public EvidenceProjection {
        evidenceId = Objects.requireNonNull(evidenceId, "evidenceId");
        caseId = Objects.requireNonNull(caseId, "caseId");
        state = Objects.requireNonNull(state, "state");
        description = Objects.requireNonNull(description, "description");
    }
}
