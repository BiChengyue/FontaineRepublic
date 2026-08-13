package com.fontainerepublic.server.parliament.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable, value-style referendum ballot (FR-PAR-002-A §5).
 *
 * <p>A referendum belongs to exactly one amendment proposal (keyed by
 * {@code proposalId}). At open the citizen roster is frozen — it becomes the
 * participation denominator and the eligible-voter set. Votes map each voting
 * citizen to their immutable choice — one vote per citizen per referendum,
 * never rewritten. The referendum passes when participation (for + against +
 * abstain) reaches 2/3 of the frozen roster <b>and</b> approval reaches 2/3
 * of the effective votes (for + against; abstentions count toward
 * participation but never toward the effective denominator). A closed
 * referendum carries {@code closedAt}; an open one does not.</p>
 */
public record Referendum(
        int schemaVersion,
        ProposalId proposalId,
        long frozenRosterCount,
        Set<UUID> frozenRoster,
        long votesFor,
        long votesAgainst,
        long votesAbstain,
        long openedAt,
        Optional<Long> closedAt,
        long referendumRevision,
        Map<UUID, VoteChoice> votes
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public Referendum {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported referendum schema version: " + schemaVersion
            );
        }
        proposalId = Objects.requireNonNull(proposalId, "proposalId");
        closedAt = closedAt == null ? Optional.empty() : closedAt;
        votes = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(votes, "votes"))
        );
        frozenRoster = Collections.unmodifiableSet(
                new LinkedHashSet<>(Objects.requireNonNull(frozenRoster, "frozenRoster"))
        );
        if (votesFor < 0 || votesAgainst < 0 || votesAbstain < 0) {
            throw new IllegalArgumentException("referendum tallies must not be negative");
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
            if (!citizen.toString().equals(citizen.toString().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "frozenRoster citizen must be a canonical UUID"
                );
            }
        }
        if (openedAt <= 0) {
            throw new IllegalArgumentException("openedAt must be a positive epoch millisecond");
        }
        if (referendumRevision <= 0) {
            throw new IllegalArgumentException("referendumRevision must be positive");
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
        for (UUID voter : votes.keySet()) {
            if (!voter.toString().equals(voter.toString().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("voter must be a canonical UUID");
            }
            if (!frozenRoster.contains(voter)) {
                throw new IllegalArgumentException(
                        "referendum voter " + voter + " is not on the frozen roster"
                );
            }
        }
    }

    /** Replacement referendum with a cast vote: tally and revision once. */
    public Referendum withCastVote(UUID voter, VoteChoice choice, long now) {
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(choice, "choice");
        if (votes.containsKey(voter)) {
            throw new IllegalArgumentException(
                    "citizen already voted on referendum " + proposalId
            );
        }
        if (closedAt.isPresent()) {
            throw new IllegalArgumentException(
                    "referendum " + proposalId + " is already closed"
            );
        }
        long nextFor = votesFor + (choice == VoteChoice.FOR ? 1 : 0);
        long nextAgainst = votesAgainst + (choice == VoteChoice.AGAINST ? 1 : 0);
        long nextAbstain = votesAbstain + (choice == VoteChoice.ABSTAIN ? 1 : 0);
        Map<UUID, VoteChoice> nextVotes = new LinkedHashMap<>(votes);
        nextVotes.put(voter, choice);
        return new Referendum(
                schemaVersion,
                proposalId,
                frozenRosterCount,
                frozenRoster,
                nextFor,
                nextAgainst,
                nextAbstain,
                openedAt,
                closedAt,
                referendumRevision + 1,
                nextVotes
        );
    }

    /** Replacement referendum closed at the given time. */
    public Referendum withClosed(long now) {
        return new Referendum(
                schemaVersion,
                proposalId,
                frozenRosterCount,
                frozenRoster,
                votesFor,
                votesAgainst,
                votesAbstain,
                openedAt,
                Optional.of(now),
                referendumRevision + 1,
                votes
        );
    }

    /** Total cast votes (for + against + abstain). */
    public long participated() {
        return votesFor + votesAgainst + votesAbstain;
    }

    /** Effective votes (for + against; abstentions are not effective). */
    public long effectiveVotes() {
        return votesFor + votesAgainst;
    }

    /**
     * Whether the referendum passed: participation reached 2/3 of the frozen
     * roster and approval reached 2/3 of the effective votes (FR-PAR-002-A
     * §5; ceilings).
     */
    public boolean passed() {
        long participationThreshold = ceilFraction(frozenRosterCount, 2, 3);
        if (participated() < participationThreshold) {
            return false;
        }
        long approvalThreshold = ceilFraction(effectiveVotes(), 2, 3);
        return votesFor >= approvalThreshold;
    }

    /** {@code ceil(size * numerator / denominator)} without floating point. */
    private static long ceilFraction(long size, long numerator, long denominator) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        return (size * numerator + denominator - 1) / denominator;
    }
}
