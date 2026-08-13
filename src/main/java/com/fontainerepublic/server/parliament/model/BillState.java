package com.fontainerepublic.server.parliament.model;

/**
 * Closed legal state machine of proposals and bills (FR-BL-003 §12;
 * FR-PAR-001-A §5).
 *
 * <p>The main track is {@code DRAFT -> REVIEW -> VOTING -> APPROVED ->
 * PUBLISHED -> ACTIVE} with {@code VOTING -> REJECTED} on a failed ballot.
 * {@link #SUSPENDED}, {@link #INVALID} and {@link #EXPIRED} are reserved
 * terminal/derivative states of later revisions (adjudication/term logic)
 * and have no transition entry point in this module. A proposal lives in
 * {@code DRAFT/REVIEW/VOTING/APPROVED/REJECTED}; a bill is born at
 * {@code APPROVED} and may later move through {@code PUBLISHED/ACTIVE}.
 * Every transition is recorded (FR-BL-005 §12); illegal jumps are
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
    EXPIRED
}
