package com.fontainerepublic.server.justice.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable, value-style evidence entry (FR-JUS-001-A §3.2).
 *
 * <p>{@code evidenceSeq} is the per-case monotonically increasing sequence
 * used as the bounded paging cursor of {@code JusticeService.evidenceFor}.
 * {@code submittedByRef} is the canonical player UUID of the submitting party
 * (from the validated on-site context). {@code integrityDigest} is an
 * optional bounded canonical digest of the referenced material.
 * {@code state} is the admissibility ruling; {@code admittedAt} is set when
 * a ruling is made. Evidence is append-only per case — no silent removal
 * after admission, no deletion path. {@code recordRevision} increments
 * exactly once per admissibility transition.</p>
 */
public record Evidence(
        int schemaVersion,
        EvidenceId evidenceId,
        CaseId caseId,
        long evidenceSeq,
        UUID submittedByRef,
        String description,
        Optional<String> integrityDigest,
        EvidenceState state,
        long submittedAt,
        Optional<Long> admittedAt,
        long recordRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Bounded description length (enforced at construction and at decode). */
    public static final int MAX_DESCRIPTION_LENGTH = 2048;

    /** Bounded integrity-digest length (enforced at construction/decode). */
    public static final int MAX_DIGEST_LENGTH = 128;

    public Evidence {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported evidence schema version: " + schemaVersion
            );
        }
        evidenceId = Objects.requireNonNull(evidenceId, "evidenceId");
        caseId = Objects.requireNonNull(caseId, "caseId");
        submittedByRef = Objects.requireNonNull(submittedByRef, "submittedByRef");
        description = Objects.requireNonNull(description, "description").trim();
        if (description.isEmpty()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "description exceeds bound of " + MAX_DESCRIPTION_LENGTH
                            + " characters"
            );
        }
        integrityDigest = integrityDigest == null ? Optional.empty() : integrityDigest;
        integrityDigest.ifPresent(digest -> {
            if (digest.isEmpty()) {
                throw new IllegalArgumentException(
                        "integrityDigest must not be blank when present"
                );
            }
            if (digest.length() > MAX_DIGEST_LENGTH) {
                throw new IllegalArgumentException(
                        "integrityDigest exceeds bound of " + MAX_DIGEST_LENGTH
                                + " characters"
                );
            }
        });
        state = Objects.requireNonNull(state, "state");
        admittedAt = admittedAt == null ? Optional.empty() : admittedAt;
        admittedAt.ifPresent(at -> {
            if (at <= 0) {
                throw new IllegalArgumentException(
                        "admittedAt must be a positive epoch millisecond"
                );
            }
        });
        if (evidenceSeq <= 0) {
            throw new IllegalArgumentException("evidenceSeq must be positive");
        }
        if (submittedAt <= 0) {
            throw new IllegalArgumentException(
                    "submittedAt must be a positive epoch millisecond"
            );
        }
        if (recordRevision <= 0) {
            throw new IllegalArgumentException("recordRevision must be positive");
        }
        if (!submittedByRef.toString().equals(
                submittedByRef.toString().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "submittedByRef must be a canonical UUID"
            );
        }
    }

    /**
     * Replacement evidence with a new admissibility state (revision +1).
     *
     * <p>Rejection is not final: previously {@code REJECTED} evidence may be
     * re-ruled {@code ADMITTED}. Admission is final — an {@code ADMITTED}
     * piece can never be re-ruled (FR-JUS-001-A §3.2).</p>
     */
    public Evidence withState(EvidenceState newState, long ruledAt) {
        Objects.requireNonNull(newState, "newState");
        if (state == EvidenceState.ADMITTED) {
            throw new IllegalArgumentException(
                    "admitted evidence is final and cannot be re-ruled; "
                            + "current state is " + state
            );
        }
        if (newState == state) {
            throw new IllegalArgumentException(
                    "a ruling must change the state (" + state
                            + " == " + newState + ")"
            );
        }
        if (newState == EvidenceState.SUBMITTED) {
            throw new IllegalArgumentException(
                    "a ruling cannot return evidence to SUBMITTED"
            );
        }
        if (ruledAt <= 0) {
            throw new IllegalArgumentException(
                    "ruledAt must be a positive epoch millisecond"
            );
        }
        return new Evidence(
                schemaVersion,
                evidenceId,
                caseId,
                evidenceSeq,
                submittedByRef,
                description,
                integrityDigest,
                newState,
                submittedAt,
                Optional.of(ruledAt),
                recordRevision + 1
        );
    }
}
