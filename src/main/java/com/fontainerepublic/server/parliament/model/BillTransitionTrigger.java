package com.fontainerepublic.server.parliament.model;

/**
 * Closed set of transition triggers of the legal state machine (FR-PAR-001-A
 * §5: every transition records its trigger).
 *
 * <p>Triggers for the later revisions (publication, activation, suspension,
 * invalidation, expiry) exist in the closed set for full coverage of the
 * constitution's machine but have no entry point in this module yet.</p>
 */
public enum BillTransitionTrigger {
    SUBMITTED,
    REVIEW_STARTED,
    VOTE_OPENED,
    VOTE_PASSED,
    VOTE_REJECTED,
    PUBLISHED,
    ACTIVATED,
    SUSPENDED,
    INVALIDATED,
    EXPIRED
}
