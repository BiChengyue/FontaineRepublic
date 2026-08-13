package com.fontainerepublic.server.parliament.model;

/**
 * Ballot lifecycle state of one vote (FR-PAR-001-A §3.2).
 *
 * <p>{@link #OPEN} ballots accept citizen votes; {@link #CLOSED} ballots
 * have been tallied by {@code closeVoteAndAdvance} and accept nothing
 * further. A closed ballot is never reopened.</p>
 */
public enum VoteBallotState {
    OPEN,
    CLOSED
}
