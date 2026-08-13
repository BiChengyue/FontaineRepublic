package com.fontainerepublic.server.parliament.model;

/**
 * Closed set of transition triggers of the legal state machine (FR-PAR-001-A
 * §5: every transition records its trigger; FR-PAR-002-A §3: every extension
 * transition records its trigger too).
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
    EXPIRED,

    /** Amendment proposal entered the pipeline ({@code -> PROPOSED}). */
    AMENDMENT_PROPOSED,
    /** A proposal entered the supreme-court review ({@code -> COURT_REVIEW}). */
    COURT_REVIEW_STARTED,
    /** The court review passed (COURT_REVIEW -> PARLIAMENT_VOTE/GUARDIAN_REVIEW/APPROVED). */
    COURT_REVIEW_PASSED,
    /** The court review returned the proposal (COURT_REVIEW -> VOTING/REJECTED). */
    COURT_REVIEW_RETURNED,
    /** A proposal entered the guardian review ({@code -> GUARDIAN_REVIEW}). */
    GUARDIAN_REVIEW_STARTED,
    /** The guardian approved (GUARDIAN_REVIEW -> APPROVED). */
    GUARDIAN_APPROVED,
    /** The guardian review timed out and counts as approval. */
    GUARDIAN_TIMED_OUT,
    /** The guardian returned the proposal once (GUARDIAN_REVIEW -> GUARDIAN_RETURNED). */
    GUARDIAN_RETURNED,
    /** The guardian recused; the supreme court reviews instead. */
    GUARDIAN_RECUSED,
    /** An override ballot opened (GUARDIAN_RETURNED -> VOTING). */
    GUARDIAN_OVERRIDE_OPENED,
    /** An override ballot passed (VOTING -> APPROVED). */
    GUARDIAN_OVERRIDE_PASSED,
    /** A court-returned re-vote passed (VOTING -> APPROVED). */
    COURT_RETURN_PASSED,
    /** Amendment parliament vote passed (PARLIAMENT_VOTE -> REFERENDUM_OPEN). */
    PARLIAMENT_VOTE_PASSED,
    /** Amendment parliament vote failed (PARLIAMENT_VOTE -> REJECTED). */
    PARLIAMENT_VOTE_FAILED,
    /** A referendum was tallied (REFERENDUM_OPEN -> REFERENDUM_CLOSED). */
    REFERENDUM_CLOSED,
    /** A referendum passed (REFERENDUM_CLOSED -> GUARDIAN_CONSENT). */
    REFERENDUM_PASSED,
    /** A referendum failed (REFERENDUM_CLOSED -> REJECTED). */
    REFERENDUM_FAILED,
    /** The guardian consented to the amendment (GUARDIAN_CONSENT -> APPROVED). */
    GUARDIAN_CONSENT_APPROVED,
    /** The guardian explicitly rejected the amendment (GUARDIAN_CONSENT -> REJECTED). */
    GUARDIAN_CONSENT_REJECTED,
    /** The constitutional-consent deadline passed and counts as consent. */
    GUARDIAN_CONSENT_TIMED_OUT
}
