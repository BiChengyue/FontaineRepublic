package com.fontainerepublic.server.justice.model;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, value-style verdict (FR-JUS-001-A §3.3).
 *
 * <p>A verdict is created when a case reaches {@code VERDICT_PENDING} and the
 * judge issues the ruling: it carries the closed outcome, the bounded
 * reasoning, the judging citizen ({@code judgeRef}, from the validated
 * on-site official-duty context), the court level of the case, and the issue
 * time. Verdicts are immutable — no {@code withState} replacement exists —
 * and binding records; execution is separate and human-directed
 * (Justice never executes a verdict).</p>
 */
public record Verdict(
        int schemaVersion,
        VerdictId verdictId,
        CaseId caseId,
        VerdictOutcome outcome,
        String reasoning,
        UUID judgeRef,
        CourtLevel level,
        long issuedAt,
        long recordRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Bounded reasoning length (enforced at construction and at decode). */
    public static final int MAX_REASONING_LENGTH = 4096;

    public Verdict {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported verdict schema version: " + schemaVersion
            );
        }
        verdictId = Objects.requireNonNull(verdictId, "verdictId");
        caseId = Objects.requireNonNull(caseId, "caseId");
        outcome = Objects.requireNonNull(outcome, "outcome");
        reasoning = Objects.requireNonNull(reasoning, "reasoning").trim();
        if (reasoning.isEmpty()) {
            throw new IllegalArgumentException("reasoning must not be blank");
        }
        if (reasoning.length() > MAX_REASONING_LENGTH) {
            throw new IllegalArgumentException(
                    "reasoning exceeds bound of " + MAX_REASONING_LENGTH
                            + " characters"
            );
        }
        judgeRef = Objects.requireNonNull(judgeRef, "judgeRef");
        level = Objects.requireNonNull(level, "level");
        if (issuedAt <= 0) {
            throw new IllegalArgumentException(
                    "issuedAt must be a positive epoch millisecond"
            );
        }
        if (recordRevision <= 0) {
            throw new IllegalArgumentException("recordRevision must be positive");
        }
        if (!judgeRef.toString().equals(judgeRef.toString().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("judgeRef must be a canonical UUID");
        }
    }
}
