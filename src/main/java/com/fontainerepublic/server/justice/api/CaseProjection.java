package com.fontainerepublic.server.justice.api;

import com.fontainerepublic.server.justice.model.Case;
import com.fontainerepublic.server.justice.model.CaseState;
import com.fontainerepublic.server.justice.model.CaseType;

import java.util.Objects;

/**
 * Bounded, ordered projection of a case for listing (FR-JUS-001-A §4: reads
 * are exact or bounded projections only — never an enumeration API).
 *
 * @param caseSeq the ascending paging cursor
 * @param caseId  the permanent case identity
 * @param caseType closed case type
 * @param state   the current pipeline state
 * @param title   the bounded case title
 */
public record CaseProjection(
        long caseSeq,
        String caseId,
        CaseType caseType,
        CaseState state,
        String title
) {

    public static CaseProjection from(Case aCase) {
        Objects.requireNonNull(aCase, "aCase");
        return new CaseProjection(
                aCase.caseSeq(),
                aCase.caseId().canonicalKey(),
                aCase.caseType(),
                aCase.state(),
                aCase.title()
        );
    }

    public CaseProjection {
        caseId = Objects.requireNonNull(caseId, "caseId");
        caseType = Objects.requireNonNull(caseType, "caseType");
        state = Objects.requireNonNull(state, "state");
        title = Objects.requireNonNull(title, "title");
    }
}
