package com.fontainerepublic.server.justice.model;

/**
 * Admissibility lifecycle of a piece of evidence (FR-JUS-001-A §3.2).
 *
 * <p>Evidence is append-only per case: it is born {@link #SUBMITTED} and may
 * be ruled {@link #ADMITTED} or {@link #REJECTED} by an official-duty
 * admission; a ruling is never silently removed after admission and there is
 * no deletion path. Only {@link #ADMITTED} evidence can support a verdict
 * (admissibility check at the final mutation boundary).</p>
 */
public enum EvidenceState {
    SUBMITTED,
    ADMITTED,
    REJECTED
}
