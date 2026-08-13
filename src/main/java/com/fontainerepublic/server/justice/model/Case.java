package com.fontainerepublic.server.justice.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable, value-style case (FR-JUS-001-A §3.1).
 *
 * <p>{@code caseSeq} is a server-assigned monotonically increasing sequence
 * used only as a bounded paging cursor for {@code JusticeService.cases};
 * {@code caseId} is the permanent authoritative identity. {@code plaintiffRef}
 * is the canonical player UUID of the filing party (taken from the validated
 * on-site context, or from the Land violation reporter on intake);
 * {@code defendantRef} is optional. {@code sourceReportId} binds a case
 * created through the bounded Land violation-report intake to its source
 * report (at most one case per report). {@code recordRevision} increments
 * exactly once per state transition.</p>
 */
public record Case(
        int schemaVersion,
        CaseId caseId,
        long caseSeq,
        CaseType caseType,
        String title,
        String description,
        UUID plaintiffRef,
        Optional<UUID> defendantRef,
        Optional<Long> sourceReportId,
        CaseState state,
        CourtLevel courtLevel,
        long createdAt,
        long recordRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Bounded title length (enforced at construction and at decode). */
    public static final int MAX_TITLE_LENGTH = 120;

    /** Bounded case-description length (enforced at construction/decode). */
    public static final int MAX_DESCRIPTION_LENGTH = 2048;

    public Case {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported case schema version: " + schemaVersion
            );
        }
        caseId = Objects.requireNonNull(caseId, "caseId");
        caseType = Objects.requireNonNull(caseType, "caseType");
        title = Objects.requireNonNull(title, "title").trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "title exceeds bound of " + MAX_TITLE_LENGTH + " characters"
            );
        }
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
        plaintiffRef = Objects.requireNonNull(plaintiffRef, "plaintiffRef");
        defendantRef = defendantRef == null ? Optional.empty() : defendantRef;
        defendantRef.ifPresent(ref -> requireCanonical(ref, "defendantRef"));
        sourceReportId = sourceReportId == null ? Optional.empty() : sourceReportId;
        sourceReportId.ifPresent(id -> {
            if (id <= 0) {
                throw new IllegalArgumentException(
                        "sourceReportId must be a positive report id"
                );
            }
        });
        state = Objects.requireNonNull(state, "state");
        courtLevel = Objects.requireNonNull(courtLevel, "courtLevel");
        if (caseSeq <= 0) {
            throw new IllegalArgumentException("caseSeq must be positive");
        }
        if (createdAt <= 0) {
            throw new IllegalArgumentException(
                    "createdAt must be a positive epoch millisecond"
            );
        }
        if (recordRevision <= 0) {
            throw new IllegalArgumentException("recordRevision must be positive");
        }
        requireCanonical(plaintiffRef, "plaintiffRef");
    }

    /** Replacement case in a new state: revision incremented exactly once. */
    public Case withState(CaseState newState) {
        Objects.requireNonNull(newState, "newState");
        return new Case(
                schemaVersion,
                caseId,
                caseSeq,
                caseType,
                title,
                description,
                plaintiffRef,
                defendantRef,
                sourceReportId,
                newState,
                courtLevel,
                createdAt,
                recordRevision + 1
        );
    }

    private static void requireCanonical(UUID value, String field) {
        Objects.requireNonNull(value, field);
        if (!value.toString().equals(value.toString().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(field + " must be a canonical UUID");
        }
    }
}
