package com.fontainerepublic.server.justice.model;

/**
 * Closed set of transition triggers of the judicial pipeline (FR-JUS-001-A
 * §5: every transition records its trigger).
 *
 * <p>Each trigger has exactly one legal entry point in this module:
 * {@link #FILED} (filing/intake), {@link #ADMITTED} (acceptance),
 * {@link #HEARING_STARTED} and {@link #VERDICT_PENDING} (court advancement),
 * {@link #VERDICTED} and {@link #CASE_REJECTED} (verdict issue), and
 * {@link #REVIEW_REQUESTED}, {@link #REVIEW_COMPLETED}, {@link #FINALIZED}
 * (review path).</p>
 */
public enum CaseTransitionTrigger {
    FILED,
    ADMITTED,
    HEARING_STARTED,
    VERDICT_PENDING,
    VERDICTED,
    CASE_REJECTED,
    REVIEW_REQUESTED,
    REVIEW_COMPLETED,
    FINALIZED
}
