package com.fontainerepublic.server.parliament.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable, value-style ballot record (FR-PAR-001-A §3.2).
 *
 * <p>{@code requiredThreshold} is computed once at vote open from the
 * normative level and the frozen citizen roster size (ceiling; roster is the
 * denominator) and is persisted so a restart never re-derives a different
 * threshold. {@code frozenRoster} is the immutable, ordered citizen list
 * frozen at vote open (FR-BL-005 §2): it fixes both the denominator and the
 * eligible voters — a citizen outside the frozen roster cannot cast a vote
 * on this ballot even if they become a citizen later. {@code votes} maps
 * each voting citizen to their immutable choice — one vote per citizen per
 * ballot, never rewritten. A closed ballot carries {@code closedAt}; an open
 * ballot does not.</p>
 */
public record Vote(
        int schemaVersion,
        VoteId voteId,
        ProposalId proposalId,
        VoteBallotState ballotState,
        long votesFor,
        long votesAgainst,
        long votesAbstain,
        long requiredThreshold,
        long frozenRosterCount,
        Set<UUID> frozenRoster,
        long openedAt,
        Optional<Long> closedAt,
        long voteRevision,
        Map<UUID, VoteChoice> votes
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public Vote {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported vote schema version: " + schemaVersion
            );
        }
        voteId = Objects.requireNonNull(voteId, "voteId");
        proposalId = Objects.requireNonNull(proposalId, "proposalId");
        ballotState = Objects.requireNonNull(ballotState, "ballotState");
        closedAt = closedAt == null ? Optional.empty() : closedAt;
        votes = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(votes, "votes"))
        );
        frozenRoster = Collections.unmodifiableSet(
                new LinkedHashSet<>(Objects.requireNonNull(frozenRoster, "frozenRoster"))
        );
        if (votesFor < 0 || votesAgainst < 0 || votesAbstain < 0) {
            throw new IllegalArgumentException("vote tallies must not be negative");
        }
        if (requiredThreshold <= 0) {
            throw new IllegalArgumentException("requiredThreshold must be positive");
        }
        if (frozenRosterCount <= 0) {
            throw new IllegalArgumentException("frozenRosterCount must be positive");
        }
        if (frozenRoster.size() != frozenRosterCount) {
            throw new IllegalArgumentException(
                    "frozenRoster size " + frozenRoster.size()
                            + " must equal frozenRosterCount " + frozenRosterCount
            );
        }
        for (UUID citizen : frozenRoster) {
            if (!citizen.toString().equals(citizen.toString().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("frozenRoster citizen must be a canonical UUID");
            }
        }
        if (openedAt <= 0) {
            throw new IllegalArgumentException("openedAt must be a positive epoch millisecond");
        }
        if (voteRevision <= 0) {
            throw new IllegalArgumentException("voteRevision must be positive");
        }
        closedAt.ifPresent(at -> {
            if (at <= 0) {
                throw new IllegalArgumentException(
                        "closedAt must be a positive epoch millisecond"
                );
            }
            if (at < openedAt) {
                throw new IllegalArgumentException("closedAt must not precede openedAt");
            }
        });
        if (ballotState == VoteBallotState.CLOSED && closedAt.isEmpty()) {
            throw new IllegalArgumentException(
                    "a CLOSED ballot must carry closedAt"
            );
        }
        if (ballotState == VoteBallotState.OPEN && closedAt.isPresent()) {
            throw new IllegalArgumentException(
                    "an OPEN ballot must not carry closedAt"
            );
        }
        for (UUID voter : votes.keySet()) {
            if (!voter.toString().equals(voter.toString().toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("voter must be a canonical UUID");
            }
        }
    }

    /** Replacement ballot with a cast vote: tally and revision incremented once. */
    public Vote withCastVote(UUID voter, VoteChoice choice, long now) {
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(choice, "choice");
        if (votes.containsKey(voter)) {
            throw new IllegalArgumentException(
                    "citizen already voted on ballot " + voteId
            );
        }
        if (ballotState != VoteBallotState.OPEN) {
            throw new IllegalArgumentException(
                    "ballot " + voteId + " is not open for voting"
            );
        }
        if (!frozenRoster.contains(voter)) {
            throw new IllegalArgumentException(
                    "citizen " + voter + " is not on the frozen roster of ballot "
                            + voteId
            );
        }
        Map<UUID, VoteChoice> next = new LinkedHashMap<>(votes);
        next.put(voter, choice);
        return new Vote(
                schemaVersion,
                voteId,
                proposalId,
                ballotState,
                votesFor + (choice == VoteChoice.FOR ? 1 : 0),
                votesAgainst + (choice == VoteChoice.AGAINST ? 1 : 0),
                votesAbstain + (choice == VoteChoice.ABSTAIN ? 1 : 0),
                requiredThreshold,
                frozenRosterCount,
                frozenRoster,
                openedAt,
                Optional.empty(),
                voteRevision + 1,
                next
        );
    }

    /** Replacement ballot closed at {@code now}: revision incremented once. */
    public Vote withClosed(long now) {
        if (ballotState != VoteBallotState.OPEN) {
            throw new IllegalArgumentException(
                    "ballot " + voteId + " is already closed"
            );
        }
        if (now < openedAt) {
            throw new IllegalArgumentException(
                    "close time must not precede openedAt"
            );
        }
        return new Vote(
                schemaVersion,
                voteId,
                proposalId,
                VoteBallotState.CLOSED,
                votesFor,
                votesAgainst,
                votesAbstain,
                requiredThreshold,
                frozenRosterCount,
                frozenRoster,
                openedAt,
                Optional.of(now),
                voteRevision + 1,
                votes
        );
    }

    /** Whether the ballot passed its frozen-roster threshold. */
    public boolean passed() {
        return votesFor >= requiredThreshold;
    }

    /** Total number of cast votes (for + against + abstain). */
    public long castTotal() {
        return votesFor + votesAgainst + votesAbstain;
    }
}
