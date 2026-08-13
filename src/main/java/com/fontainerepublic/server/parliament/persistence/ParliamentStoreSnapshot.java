package com.fontainerepublic.server.parliament.persistence;

import com.fontainerepublic.server.parliament.model.Bill;
import com.fontainerepublic.server.parliament.model.BillId;
import com.fontainerepublic.server.parliament.model.Proposal;
import com.fontainerepublic.server.parliament.model.ProposalId;
import com.fontainerepublic.server.parliament.model.ProposalStage;
import com.fontainerepublic.server.parliament.model.Referendum;
import com.fontainerepublic.server.parliament.model.TransitionRecord;
import com.fontainerepublic.server.parliament.model.Vote;
import com.fontainerepublic.server.parliament.model.VoteId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Immutable, fully validated representation of the complete
 * {@code "parliament"} namespace (FR-PAR-001-A §3.4; FR-PAR-002-A §3 adds
 * the per-proposal stage metadata and the amendment referendums).
 *
 * <p>The constructor enforces the authoritative invariants — no partially
 * consistent snapshot can exist:</p>
 * <ul>
 *   <li>every map key matches its record's canonical id;</li>
 *   <li>every vote references an existing proposal;</li>
 *   <li>every bill references an existing proposal;</li>
 *   <li>every stage references an existing proposal;</li>
 *   <li>every referendum references an existing proposal;</li>
 *   <li>every transition references an existing proposal;</li>
 *   <li>transitions are an ordered, immutable ledger (append-only within a
 *       snapshot).</li>
 * </ul>
 * <p>Duplicates, mismatches, or dangling references reject the whole
 * snapshot (fail closed).</p>
 */
public record ParliamentStoreSnapshot(
        int storeVersion,
        long storeRevision,
        Map<ProposalId, Proposal> proposals,
        Map<VoteId, Vote> votes,
        Map<BillId, Bill> bills,
        List<TransitionRecord> transitions,
        Set<UUID> citizenRoster,
        Map<ProposalId, ProposalStage> stages,
        Map<ProposalId, Referendum> referendums
) {

    public static final int CURRENT_STORE_VERSION = 1;

    public ParliamentStoreSnapshot {
        if (storeVersion != CURRENT_STORE_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported parliament store version: " + storeVersion
            );
        }
        if (storeRevision < 0) {
            throw new IllegalArgumentException("storeRevision must not be negative");
        }
        proposals = Map.copyOf(Objects.requireNonNull(proposals, "proposals"));
        votes = Map.copyOf(Objects.requireNonNull(votes, "votes"));
        bills = Map.copyOf(Objects.requireNonNull(bills, "bills"));
        transitions = List.copyOf(Objects.requireNonNull(transitions, "transitions"));
        citizenRoster = Collections.unmodifiableSet(
                new TreeSet<>(Objects.requireNonNull(citizenRoster, "citizenRoster"))
        );
        stages = Map.copyOf(Objects.requireNonNull(stages, "stages"));
        referendums = Map.copyOf(Objects.requireNonNull(referendums, "referendums"));

        for (UUID citizen : citizenRoster) {
            if (!citizen.toString().equals(
                    citizen.toString().toLowerCase(java.util.Locale.ROOT))) {
                throw invalid("Citizen roster contains a non-canonical UUID: " + citizen);
            }
        }
        for (Map.Entry<ProposalId, Proposal> entry : proposals.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().proposalId())) {
                throw invalid(
                        "Proposals key " + entry.getKey()
                                + " does not match record proposalId "
                                + entry.getValue().proposalId()
                );
            }
        }
        for (Map.Entry<VoteId, Vote> entry : votes.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().voteId())) {
                throw invalid(
                        "Votes key " + entry.getKey()
                                + " does not match record voteId "
                                + entry.getValue().voteId()
                );
            }
            if (!proposals.containsKey(entry.getValue().proposalId())) {
                throw invalid(
                        "Vote " + entry.getKey() + " references missing proposal "
                                + entry.getValue().proposalId()
                );
            }
        }
        for (Map.Entry<BillId, Bill> entry : bills.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().billId())) {
                throw invalid(
                        "Bills key " + entry.getKey()
                                + " does not match record billId "
                                + entry.getValue().billId()
                );
            }
            if (!proposals.containsKey(entry.getValue().proposalId())) {
                throw invalid(
                        "Bill " + entry.getKey() + " references missing proposal "
                                + entry.getValue().proposalId()
                );
            }
        }
        for (Map.Entry<ProposalId, ProposalStage> entry : stages.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().proposalId())) {
                throw invalid(
                        "Stages key " + entry.getKey()
                                + " does not match record proposalId "
                                + entry.getValue().proposalId()
                );
            }
            if (!proposals.containsKey(entry.getKey())) {
                throw invalid(
                        "Stage for " + entry.getKey()
                                + " references missing proposal"
                );
            }
        }
        for (Map.Entry<ProposalId, Referendum> entry : referendums.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().proposalId())) {
                throw invalid(
                        "Referendums key " + entry.getKey()
                                + " does not match record proposalId "
                                + entry.getValue().proposalId()
                );
            }
            if (!proposals.containsKey(entry.getKey())) {
                throw invalid(
                        "Referendum for " + entry.getKey()
                                + " references missing proposal"
                );
            }
        }
        for (TransitionRecord transition : transitions) {
            if (!proposals.containsKey(transition.proposalId())) {
                throw invalid(
                        "Transition for " + transition.proposalId()
                                + " references missing proposal"
                );
            }
        }
    }

    private static ParliamentNbtException invalid(String message) {
        return new ParliamentNbtException(message);
    }
}
