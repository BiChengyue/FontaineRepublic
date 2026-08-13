package com.fontainerepublic.server.justice.model;

/**
 * Closed set of court levels (FR-JUS-001-A §3.1/§3.3: {@code courtLevel} of
 * a case, {@code level} of a verdict).
 *
 * <p>Alpha adjudicates at {@link #FIRST_INSTANCE}; {@link #APPEAL} is
 * reserved for the review revision and has no entry point in this module
 * (an appeal never changes the level of the original verdict record).</p>
 */
public enum CourtLevel {
    FIRST_INSTANCE,
    APPEAL
}
