package com.fontainerepublic.server.parliament.model;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, value-style legislative proposal (FR-PAR-001-A §3.1).
 *
 * <p>{@code proposalSeq} is a server-assigned monotonically increasing
 * sequence used only as a bounded paging cursor for
 * {@code ParliamentService.proposals}; {@code proposalId} is the permanent
 * authoritative identity. {@code proposerRef} is the canonical player UUID of
 * the submitting citizen (taken from the on-site context at the final
 * mutation boundary). {@code recordRevision} increments exactly once per
 * state transition.</p>
 */
public record Proposal(
        int schemaVersion,
        ProposalId proposalId,
        long proposalSeq,
        String title,
        NormLevel normLevel,
        String fullText,
        UUID proposerRef,
        BillState state,
        long createdAt,
        long recordRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Bounded title length (enforced at construction and at decode). */
    public static final int MAX_TITLE_LENGTH = 120;

    /** Bounded full-text length (enforced at construction and at decode). */
    public static final int MAX_FULL_TEXT_LENGTH = 4096;

    public Proposal {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported proposal schema version: " + schemaVersion
            );
        }
        proposalId = Objects.requireNonNull(proposalId, "proposalId");
        title = Objects.requireNonNull(title, "title").trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "title exceeds bound of " + MAX_TITLE_LENGTH + " characters"
            );
        }
        normLevel = Objects.requireNonNull(normLevel, "normLevel");
        fullText = Objects.requireNonNull(fullText, "fullText").trim();
        if (fullText.isEmpty()) {
            throw new IllegalArgumentException("fullText must not be blank");
        }
        if (fullText.length() > MAX_FULL_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "fullText exceeds bound of " + MAX_FULL_TEXT_LENGTH + " characters"
            );
        }
        proposerRef = Objects.requireNonNull(proposerRef, "proposerRef");
        state = Objects.requireNonNull(state, "state");
        if (proposalSeq <= 0) {
            throw new IllegalArgumentException("proposalSeq must be positive");
        }
        if (createdAt <= 0) {
            throw new IllegalArgumentException("createdAt must be a positive epoch millisecond");
        }
        if (recordRevision <= 0) {
            throw new IllegalArgumentException("recordRevision must be positive");
        }
        if (!proposerRef.toString().equals(proposerRef.toString().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("proposerRef must be a canonical UUID");
        }
    }

    /** Replacement proposal in a new state: revision incremented exactly once. */
    public Proposal withState(BillState newState) {
        Objects.requireNonNull(newState, "newState");
        return new Proposal(
                schemaVersion,
                proposalId,
                proposalSeq,
                title,
                normLevel,
                fullText,
                proposerRef,
                newState,
                createdAt,
                recordRevision + 1
        );
    }
}
