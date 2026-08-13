package com.fontainerepublic.server.parliament.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, value-style per-proposal stage metadata of the legislative
 * extensions (FR-PAR-002-A §3/§4): the guardian-review and court-review
 * deadlines, the guardian-return basis, and the provenance needed to keep
 * the extended state machine closed.
 *
 * <p>Only one deadline is active at a time (each extension stage starts when
 * the proposal enters it): {@code stageStartedAt} / {@code stageDeadlineAt}
 * describe the current timed stage — guardian review (ordinary 72h / basic
 * 7d), court review (14d, extendable once by 7d), or constitutional consent
 * (7d). {@code returnBasis} carries the bounded constitutional-basis or
 * procedural-error text of a guardian return. {@code courtReviewEnteredFrom}
 * and {@code votingEnteredFrom} record the source states so a later
 * transition can pick the correct target (court review of an ordinary law
 * passes into guardian review, a recusal replacement passes straight to
 * APPROVED; an override ballot passes with the guardian threshold).
 * {@code stageRevision} increments exactly once per stage change.</p>
 */
public record ProposalStage(
        int schemaVersion,
        ProposalId proposalId,
        Optional<Long> stageStartedAt,
        Optional<Long> stageDeadlineAt,
        Optional<String> returnBasis,
        Optional<BillState> courtReviewEnteredFrom,
        Optional<BillState> votingEnteredFrom,
        int courtExtensionCount,
        long stageRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Bounded guardian-return basis (constitution clause / procedural error). */
    public static final int MAX_RETURN_BASIS_LENGTH = 200;

    public ProposalStage {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported proposal-stage schema version: " + schemaVersion
            );
        }
        proposalId = Objects.requireNonNull(proposalId, "proposalId");
        stageStartedAt = stageStartedAt == null ? Optional.empty() : stageStartedAt;
        stageDeadlineAt = stageDeadlineAt == null ? Optional.empty() : stageDeadlineAt;
        returnBasis = returnBasis == null ? Optional.empty() : returnBasis;
        courtReviewEnteredFrom =
                courtReviewEnteredFrom == null ? Optional.empty() : courtReviewEnteredFrom;
        votingEnteredFrom = votingEnteredFrom == null ? Optional.empty() : votingEnteredFrom;
        stageStartedAt.ifPresent(at -> {
            if (at <= 0) {
                throw new IllegalArgumentException(
                        "stageStartedAt must be a positive epoch millisecond"
                );
            }
        });
        stageDeadlineAt.ifPresent(at -> {
            if (at <= 0) {
                throw new IllegalArgumentException(
                        "stageDeadlineAt must be a positive epoch millisecond"
                );
            }
        });
        if (stageStartedAt.isPresent() && stageDeadlineAt.isPresent()
                && stageDeadlineAt.get() < stageStartedAt.get()) {
            throw new IllegalArgumentException(
                    "stageDeadlineAt must not precede stageStartedAt"
            );
        }
        returnBasis.ifPresent(basis -> {
            String normalized = basis.trim();
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("returnBasis must not be blank");
            }
            if (normalized.length() > MAX_RETURN_BASIS_LENGTH) {
                throw new IllegalArgumentException(
                        "returnBasis exceeds bound of " + MAX_RETURN_BASIS_LENGTH
                                + " characters"
                );
            }
        });
        if (courtExtensionCount < 0 || courtExtensionCount > 1) {
            throw new IllegalArgumentException(
                    "courtExtensionCount must be 0 or 1 (extendable once)"
            );
        }
        if (stageRevision <= 0) {
            throw new IllegalArgumentException("stageRevision must be positive");
        }
        if (stageDeadlineAt.isEmpty() && courtExtensionCount > 0) {
            throw new IllegalArgumentException(
                    "a court extension requires an active deadline"
            );
        }
    }

    /**
     * Replacement stage for a proposal that just entered a timed stage
     * (guardian review / court review / constitutional consent): revision
     * incremented exactly once, the guard/court provenance fields kept only
     * where they still apply.
     */
    public ProposalStage withTimedStage(
            long startedAt,
            long deadlineAt,
            Optional<BillState> courtEnteredFrom,
            Optional<BillState> votingFrom
    ) {
        return new ProposalStage(
                schemaVersion,
                proposalId,
                Optional.of(startedAt),
                Optional.of(deadlineAt),
                Optional.empty(),
                courtEnteredFrom,
                votingFrom,
                0,
                stageRevision + 1
        );
    }

    /** Replacement stage recording a guardian return with its basis. */
    public ProposalStage withGuardianReturn(String basis, long now) {
        Objects.requireNonNull(basis, "basis");
        return new ProposalStage(
                schemaVersion,
                proposalId,
                Optional.of(now),
                Optional.empty(),
                Optional.of(basis.trim()),
                courtReviewEnteredFrom,
                votingEnteredFrom,
                0,
                stageRevision + 1
        );
    }

    /** Replacement stage after a timed stage resolved (no active deadline). */
    public ProposalStage withStageResolved(long now) {
        return new ProposalStage(
                schemaVersion,
                proposalId,
                Optional.of(now),
                Optional.empty(),
                Optional.empty(),
                courtReviewEnteredFrom,
                votingEnteredFrom,
                0,
                stageRevision + 1
        );
    }

    /** Replacement stage for a vote-open: records where the vote came from. */
    public ProposalStage withVotingOrigin(BillState enteredFrom, long now) {
        Objects.requireNonNull(enteredFrom, "enteredFrom");
        return new ProposalStage(
                schemaVersion,
                proposalId,
                Optional.of(now),
                Optional.empty(),
                returnBasis,
                courtReviewEnteredFrom,
                Optional.of(enteredFrom),
                0,
                stageRevision + 1
        );
    }

    /** Replacement stage extending the active court-review deadline by 7 days. */
    public ProposalStage withCourtExtension(long extendedDeadlineAt) {
        if (courtExtensionCount != 0) {
            throw new IllegalStateException("court review is already extended once");
        }
        return new ProposalStage(
                schemaVersion,
                proposalId,
                stageStartedAt,
                Optional.of(extendedDeadlineAt),
                returnBasis,
                courtReviewEnteredFrom,
                votingEnteredFrom,
                1,
                stageRevision + 1
        );
    }
}
