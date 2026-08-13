package com.fontainerepublic.server.parliament.model;

/**
 * Closed legal state machine of proposals and bills (FR-BL-003 §12;
 * FR-PAR-001-A §5; FR-PAR-002-A §3).
 *
 * <p>The main legislative track is {@code DRAFT -> REVIEW -> VOTING ->
 * APPROVED -> PUBLISHED -> ACTIVE} with {@code VOTING -> REJECTED} on a
 * failed ballot. FR-PAR-002 adds the constitution's higher-order stages:
 * the water-god guardian review ({@code GUARDIAN_REVIEW} /
 * {@code GUARDIAN_RETURNED}), the supreme-court constitutionality review
 * ({@code COURT_REVIEW}), the referendum stages
 * ({@code REFERENDUM_OPEN} / {@code REFERENDUM_CLOSED}), and the amendment
 * pipeline stages ({@code PROPOSED}, {@code PARLIAMENT_VOTE},
 * {@code GUARDIAN_CONSENT}). {@link #SUSPENDED}, {@link #INVALID} and
 * {@link #EXPIRED} are reserved terminal/derivative states of later
 * revisions (term/adjudication logic) and have no transition into them yet.
 * The set is closed: every transition is validated and illegal jumps are
 * rejected.</p>
 */
public enum BillState {
    DRAFT,
    REVIEW,
    VOTING,
    APPROVED,
    PUBLISHED,
    ACTIVE,
    REJECTED,
    SUSPENDED,
    INVALID,
    EXPIRED,

    /** Amendment proposal proposed, awaiting the supreme-court review. */
    PROPOSED,
    /** Under the water-god guardian review (ordinary 72h / basic 7d). */
    GUARDIAN_REVIEW,
    /** Returned once by the guardian; parliament may override-vote. */
    GUARDIAN_RETURNED,
    /** Under the supreme-court constitutionality review (14d). */
    COURT_REVIEW,
    /** Amendment pipeline: parliament vote phase (4/5). */
    PARLIAMENT_VOTE,
    /** Referendum open for citizen voting. */
    REFERENDUM_OPEN,
    /** Referendum tallied; the outcome is decided in the same snapshot. */
    REFERENDUM_CLOSED,
    /** Amendment pipeline: water-god constitutional consent phase (7d). */
    GUARDIAN_CONSENT
}
