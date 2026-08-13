package com.fontainerepublic.server.justice.api;

import com.fontainerepublic.server.justice.model.CaseId;
import com.fontainerepublic.server.justice.model.VerdictOutcome;

import java.util.Objects;

/**
 * Authoritative verdict draft (FR-JUS-001-A §3.3/§4).
 *
 * <p>The judging citizen is taken from the validated on-site official-duty
 * context at the final mutation boundary; the reasoning is bounded.
 * {@link VerdictOutcome#GUILTY}/{@link VerdictOutcome#NOT_GUILTY} advance the
 * case to {@code VERDICTED}, {@link VerdictOutcome#DISMISSED} rejects it.</p>
 *
 * @param caseId    the case being adjudicated
 * @param outcome   closed verdict outcome
 * @param reasoning bounded judicial reasoning
 */
public record VerdictDraft(
        CaseId caseId,
        VerdictOutcome outcome,
        String reasoning
) {

    public VerdictDraft {
        caseId = Objects.requireNonNull(caseId, "caseId");
        outcome = Objects.requireNonNull(outcome, "outcome");
        reasoning = Objects.requireNonNull(reasoning, "reasoning");
    }
}
