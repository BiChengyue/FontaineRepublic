package com.fontainerepublic.server.justice.model;

/**
 * Closed set of verdict outcomes (FR-JUS-001-A §3.3).
 *
 * <p>{@link #GUILTY} and {@link #NOT_GUILTY} advance the case to
 * {@link CaseState#VERDICTED}; {@link #DISMISSED} rejects the case
 * ({@link CaseState#REJECTED}). Verdicts are immutable records; remedies are
 * separate records (appeal/new-trial links of later revisions).</p>
 */
public enum VerdictOutcome {
    GUILTY,
    NOT_GUILTY,
    DISMISSED
}
