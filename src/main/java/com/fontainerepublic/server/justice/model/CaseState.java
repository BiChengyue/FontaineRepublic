package com.fontainerepublic.server.justice.model;

/**
 * Closed judicial pipeline of a case (FR-JUS-001-A §5).
 *
 * <p>The main track is {@code DRAFT -> FILED -> ADMITTED -> HEARING ->
 * VERDICT_PENDING -> VERDICTED} with {@code VERDICT_PENDING -> REJECTED} on a
 * dismissal; a verdict may then be reviewed:
 * {@code VERDICTED -> REVIEW_REQUESTED -> REVIEWED -> FINAL}. Every transition
 * is recorded (actor/time/trigger/before/after/revision); illegal jumps are
 * rejected. {@code DRAFT} exists only as the initial birth state inside the
 * same committed snapshot that files the case — no case is ever persisted in
 * {@code DRAFT}.</p>
 */
public enum CaseState {
    DRAFT,
    FILED,
    ADMITTED,
    HEARING,
    VERDICT_PENDING,
    VERDICTED,
    REJECTED,
    REVIEW_REQUESTED,
    REVIEWED,
    FINAL
}
