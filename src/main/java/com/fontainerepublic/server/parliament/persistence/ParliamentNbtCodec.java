package com.fontainerepublic.server.parliament.persistence;

import com.fontainerepublic.server.parliament.model.Bill;
import com.fontainerepublic.server.parliament.model.BillId;
import com.fontainerepublic.server.parliament.model.BillState;
import com.fontainerepublic.server.parliament.model.BillTransitionTrigger;
import com.fontainerepublic.server.parliament.model.NormLevel;
import com.fontainerepublic.server.parliament.model.Proposal;
import com.fontainerepublic.server.parliament.model.ProposalId;
import com.fontainerepublic.server.parliament.model.TransitionRecord;
import com.fontainerepublic.server.parliament.model.Vote;
import com.fontainerepublic.server.parliament.model.VoteBallotState;
import com.fontainerepublic.server.parliament.model.VoteChoice;
import com.fontainerepublic.server.parliament.model.VoteId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Strict, versioned, deterministic NBT codec for the {@code "parliament"}
 * namespace (FR-PAR-001-A §3.4).
 *
 * <p>Encoding writes every index in deterministic lexical key order and the
 * transition ledger in append order, so the same immutable snapshot always
 * produces an equivalent ordered NBT. Decoding accepts only declared fields
 * with exact NBT types, canonical UUIDs, valid enums, bounded text, positive
 * timestamps and revisions, and constructs the validating
 * {@link ParliamentStoreSnapshot} which enforces key/record identity and
 * referential integrity. Unknown newer versions are rejected; nothing is
 * ever auto-repaired.</p>
 */
public final class ParliamentNbtCodec {

    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String CITIZEN_ROSTER = "CitizenRoster";
    private static final String PROPOSALS = "Proposals";
    private static final String VOTES = "Votes";
    private static final String BILLS = "Bills";
    private static final String TRANSITIONS = "Transitions";

    private static final String PROPOSAL_VERSION = "ProposalVersion";
    private static final String PROPOSAL_ID = "ProposalId";
    private static final String PROPOSAL_SEQ = "ProposalSeq";
    private static final String TITLE = "Title";
    private static final String NORM_LEVEL = "NormLevel";
    private static final String FULL_TEXT = "FullText";
    private static final String PROPOSER_REF = "ProposerRef";
    private static final String STATE = "State";
    private static final String CREATED_AT = "CreatedAt";
    private static final String RECORD_REVISION = "RecordRevision";

    private static final String VOTE_VERSION = "VoteVersion";
    private static final String VOTE_ID = "VoteId";
    private static final String BALLOT_STATE = "BallotState";
    private static final String VOTES_FOR = "VotesFor";
    private static final String VOTES_AGAINST = "VotesAgainst";
    private static final String VOTES_ABSTAIN = "VotesAbstain";
    private static final String REQUIRED_THRESHOLD = "RequiredThreshold";
    private static final String FROZEN_ROSTER_COUNT = "FrozenRosterCount";
    private static final String FROZEN_ROSTER = "FrozenRoster";
    private static final String OPENED_AT = "OpenedAt";
    private static final String CLOSED_AT = "ClosedAt";
    private static final String VOTE_REVISION = "VoteRevision";
    private static final String BALLOT_VOTES = "Votes";

    private static final String BILL_VERSION = "BillVersion";
    private static final String BILL_ID = "BillId";
    private static final String PASSED_AT = "PassedAt";

    private static final String TRANSITION_VERSION = "TransitionVersion";
    private static final String ACTOR = "Actor";
    private static final String AT_MILLIS = "AtMillis";
    private static final String TRIGGER = "Trigger";
    private static final String BEFORE = "Before";
    private static final String AFTER = "After";

    /** Hard structural caps, independent of the configurable budget. */
    private static final int HARD_MAX_PROPOSALS = 100_000;
    private static final int HARD_MAX_VOTES = 100_000;
    private static final int HARD_MAX_BILLS = 100_000;
    private static final int HARD_MAX_TRANSITIONS = 1_000_000;
    private static final int HARD_MAX_VOTES_PER_BALLOT = 100_000;

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION,
            STORE_REVISION,
            CITIZEN_ROSTER,
            PROPOSALS,
            VOTES,
            BILLS,
            TRANSITIONS
    );
    private static final Set<String> PROPOSAL_KEYS = Set.of(
            PROPOSAL_VERSION,
            PROPOSAL_ID,
            PROPOSAL_SEQ,
            TITLE,
            NORM_LEVEL,
            FULL_TEXT,
            PROPOSER_REF,
            STATE,
            CREATED_AT,
            RECORD_REVISION
    );
    private static final Set<String> VOTE_KEYS = Set.of(
            VOTE_VERSION,
            VOTE_ID,
            PROPOSAL_ID,
            BALLOT_STATE,
            VOTES_FOR,
            VOTES_AGAINST,
            VOTES_ABSTAIN,
            REQUIRED_THRESHOLD,
            FROZEN_ROSTER_COUNT,
            FROZEN_ROSTER,
            OPENED_AT,
            CLOSED_AT,
            VOTE_REVISION,
            BALLOT_VOTES
    );
    private static final Set<String> BILL_KEYS = Set.of(
            BILL_VERSION,
            BILL_ID,
            PROPOSAL_ID,
            STATE,
            PASSED_AT,
            RECORD_REVISION
    );
    private static final Set<String> TRANSITION_KEYS = Set.of(
            TRANSITION_VERSION,
            PROPOSAL_ID,
            ACTOR,
            AT_MILLIS,
            TRIGGER,
            BEFORE,
            AFTER,
            RECORD_REVISION
    );

    // ------------------------------------------------------------------
    // decode
    // ------------------------------------------------------------------

    /**
     * Decodes and fully validates a namespace snapshot. Empty input is
     * rejected: a present parliament namespace must be a valid, initialized
     * store.
     */
    public ParliamentStoreSnapshot decode(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            throw invalid(
                    "parliament namespace is empty; it must be a valid initialized store"
            );
        }

        requireOnlyKeys(root, STORE_KEYS, "parliament");
        requireType(root, STORE_VERSION, Tag.TAG_INT, "parliament");
        requireType(root, STORE_REVISION, Tag.TAG_LONG, "parliament");
        requireType(root, CITIZEN_ROSTER, Tag.TAG_LIST, "parliament");
        requireType(root, PROPOSALS, Tag.TAG_COMPOUND, "parliament");
        requireType(root, VOTES, Tag.TAG_COMPOUND, "parliament");
        requireType(root, BILLS, Tag.TAG_COMPOUND, "parliament");
        requireType(root, TRANSITIONS, Tag.TAG_LIST, "parliament");

        int storeVersion = root.getInt(STORE_VERSION);
        if (storeVersion != ParliamentStoreSnapshot.CURRENT_STORE_VERSION) {
            throw invalid("Unsupported parliament store version: " + storeVersion);
        }
        long storeRevision = root.getLong(STORE_REVISION);
        if (storeRevision < 0) {
            throw invalid("StoreRevision must not be negative");
        }

        Map<ProposalId, Proposal> proposals = decodeProposals(
                root.getCompound(PROPOSALS)
        );
        Map<VoteId, Vote> votes = decodeVotes(
                root.getCompound(VOTES),
                proposals
        );
        Map<BillId, Bill> bills = decodeBills(
                root.getCompound(BILLS),
                proposals
        );
        List<TransitionRecord> transitions = decodeTransitions(
                root.getList(TRANSITIONS, Tag.TAG_COMPOUND),
                proposals
        );
        Set<UUID> citizenRoster = decodeCitizenRoster(
                root.getList(CITIZEN_ROSTER, Tag.TAG_INT_ARRAY)
        );
        return new ParliamentStoreSnapshot(
                storeVersion,
                storeRevision,
                proposals,
                votes,
                bills,
                transitions,
                citizenRoster
        );
    }

    private Map<ProposalId, Proposal> decodeProposals(CompoundTag tag) {
        if (tag.getAllKeys().size() > HARD_MAX_PROPOSALS) {
            throw invalid("Proposal count exceeds " + HARD_MAX_PROPOSALS);
        }
        Map<ProposalId, Proposal> proposals = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            ProposalId proposalId = parseCanonicalId(key, PROPOSALS);
            requireType(tag, key, Tag.TAG_COMPOUND, PROPOSALS);
            Proposal proposal = decodeProposal(tag.getCompound(key), proposalId);
            if (proposals.put(proposalId, proposal) != null) {
                throw invalid("Duplicate proposal id: " + proposalId);
            }
        }
        return proposals;
    }

    private Proposal decodeProposal(CompoundTag tag, ProposalId expectedId) {
        requireOnlyKeys(tag, PROPOSAL_KEYS, "proposal " + expectedId);
        requireType(tag, PROPOSAL_VERSION, Tag.TAG_INT, "proposal " + expectedId);
        requireType(tag, PROPOSAL_ID, Tag.TAG_INT_ARRAY, "proposal " + expectedId);
        requireType(tag, PROPOSAL_SEQ, Tag.TAG_LONG, "proposal " + expectedId);
        requireType(tag, TITLE, Tag.TAG_STRING, "proposal " + expectedId);
        requireType(tag, NORM_LEVEL, Tag.TAG_STRING, "proposal " + expectedId);
        requireType(tag, FULL_TEXT, Tag.TAG_STRING, "proposal " + expectedId);
        requireType(tag, PROPOSER_REF, Tag.TAG_INT_ARRAY, "proposal " + expectedId);
        requireType(tag, STATE, Tag.TAG_STRING, "proposal " + expectedId);
        requireType(tag, CREATED_AT, Tag.TAG_LONG, "proposal " + expectedId);
        requireType(tag, RECORD_REVISION, Tag.TAG_LONG, "proposal " + expectedId);

        int version = tag.getInt(PROPOSAL_VERSION);
        if (version != Proposal.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported proposal schema version for " + expectedId + ": " + version
            );
        }
        UUID storedId = tag.getUUID(PROPOSAL_ID);
        if (!expectedId.value().equals(storedId)) {
            throw invalid(
                    "Proposals key " + expectedId
                            + " does not match record proposalId " + storedId
            );
        }
        try {
            return new Proposal(
                    version,
                    expectedId,
                    tag.getLong(PROPOSAL_SEQ),
                    tag.getString(TITLE),
                    enumValue(NormLevel.class, tag.getString(NORM_LEVEL),
                            "NormLevel for " + expectedId),
                    tag.getString(FULL_TEXT),
                    tag.getUUID(PROPOSER_REF),
                    enumValue(BillState.class, tag.getString(STATE),
                            "State for " + expectedId),
                    tag.getLong(CREATED_AT),
                    tag.getLong(RECORD_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid proposal " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private Map<VoteId, Vote> decodeVotes(
            CompoundTag tag,
            Map<ProposalId, Proposal> proposals
    ) {
        if (tag.getAllKeys().size() > HARD_MAX_VOTES) {
            throw invalid("Vote count exceeds " + HARD_MAX_VOTES);
        }
        Map<VoteId, Vote> votes = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            VoteId voteId = parseVoteId(key);
            requireType(tag, key, Tag.TAG_COMPOUND, VOTES);
            Vote vote = decodeVote(tag.getCompound(key), voteId, proposals);
            if (votes.put(voteId, vote) != null) {
                throw invalid("Duplicate vote id: " + voteId);
            }
        }
        return votes;
    }

    private Vote decodeVote(
            CompoundTag tag,
            VoteId expectedId,
            Map<ProposalId, Proposal> proposals
    ) {
        requireOnlyKeys(tag, VOTE_KEYS, "vote " + expectedId);
        requireType(tag, VOTE_VERSION, Tag.TAG_INT, "vote " + expectedId);
        requireType(tag, VOTE_ID, Tag.TAG_INT_ARRAY, "vote " + expectedId);
        requireType(tag, PROPOSAL_ID, Tag.TAG_INT_ARRAY, "vote " + expectedId);
        requireType(tag, BALLOT_STATE, Tag.TAG_STRING, "vote " + expectedId);
        requireType(tag, VOTES_FOR, Tag.TAG_LONG, "vote " + expectedId);
        requireType(tag, VOTES_AGAINST, Tag.TAG_LONG, "vote " + expectedId);
        requireType(tag, VOTES_ABSTAIN, Tag.TAG_LONG, "vote " + expectedId);
        requireType(tag, REQUIRED_THRESHOLD, Tag.TAG_LONG, "vote " + expectedId);
        requireType(tag, FROZEN_ROSTER_COUNT, Tag.TAG_LONG, "vote " + expectedId);
        requireType(tag, FROZEN_ROSTER, Tag.TAG_LIST, "vote " + expectedId);
        requireType(tag, OPENED_AT, Tag.TAG_LONG, "vote " + expectedId);
        requireType(tag, VOTE_REVISION, Tag.TAG_LONG, "vote " + expectedId);
        requireType(tag, BALLOT_VOTES, Tag.TAG_COMPOUND, "vote " + expectedId);

        int version = tag.getInt(VOTE_VERSION);
        if (version != Vote.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported vote schema version for " + expectedId + ": " + version
            );
        }
        UUID storedId = tag.getUUID(VOTE_ID);
        if (!expectedId.value().equals(storedId)) {
            throw invalid(
                    "Votes key " + expectedId
                            + " does not match record voteId " + storedId
            );
        }
        ProposalId proposalId = parseProposalId(tag, PROPOSAL_ID, "vote " + expectedId);
        if (!proposals.containsKey(proposalId)) {
            throw invalid(
                    "Vote " + expectedId + " references missing proposal " + proposalId
            );
        }
        Optional<Long> closedAt = tag.contains(CLOSED_AT, Tag.TAG_LONG)
                ? Optional.of(tag.getLong(CLOSED_AT))
                : Optional.empty();
        try {
            return new Vote(
                    version,
                    expectedId,
                    proposalId,
                    enumValue(VoteBallotState.class, tag.getString(BALLOT_STATE),
                            "BallotState for " + expectedId),
                    tag.getLong(VOTES_FOR),
                    tag.getLong(VOTES_AGAINST),
                    tag.getLong(VOTES_ABSTAIN),
                    tag.getLong(REQUIRED_THRESHOLD),
                    tag.getLong(FROZEN_ROSTER_COUNT),
                    decodeFrozenRoster(tag.getList(FROZEN_ROSTER, Tag.TAG_INT_ARRAY),
                            expectedId),
                    tag.getLong(OPENED_AT),
                    closedAt,
                    tag.getLong(VOTE_REVISION),
                    decodeBallotVotes(tag.getCompound(BALLOT_VOTES), expectedId)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid vote " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private Set<UUID> decodeCitizenRoster(ListTag list) {
        if (list.size() > HARD_MAX_VOTES_PER_BALLOT) {
            throw invalid(
                    "Citizen roster exceeds " + HARD_MAX_VOTES_PER_BALLOT
            );
        }
        TreeSet<UUID> roster = new TreeSet<>(Comparator.comparing(UUID::toString));
        for (int index = 0; index < list.size(); index++) {
            int[] raw = list.getIntArray(index);
            if (raw.length != 4) {
                throw invalid(
                        "Citizen roster entry at " + index
                                + " is not a canonical UUID"
                );
            }
            UUID citizen = new UUID(
                    (long) raw[0] << 32 | raw[1] & 0xFFFFFFFFL,
                    (long) raw[2] << 32 | raw[3] & 0xFFFFFFFFL
            );
            if (!roster.add(citizen)) {
                throw invalid("Citizen roster contains duplicate " + citizen);
            }
        }
        return roster;
    }

    private Set<UUID> decodeFrozenRoster(ListTag list, VoteId voteId) {
        if (list.size() > HARD_MAX_VOTES_PER_BALLOT) {
            throw invalid(
                    "Ballot " + voteId + " frozen roster exceeds "
                            + HARD_MAX_VOTES_PER_BALLOT
            );
        }
        LinkedHashSet<UUID> roster = new LinkedHashSet<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            int[] raw = list.getIntArray(index);
            if (raw.length != 4) {
                throw invalid(
                        "Ballot " + voteId + " frozen roster entry at " + index
                                + " is not a canonical UUID"
                );
            }
            UUID citizen = new UUID(
                    (long) raw[0] << 32 | raw[1] & 0xFFFFFFFFL,
                    (long) raw[2] << 32 | raw[3] & 0xFFFFFFFFL
            );
            if (!roster.add(citizen)) {
                throw invalid(
                        "Ballot " + voteId + " frozen roster contains duplicate "
                                + citizen
                );
            }
        }
        return roster;
    }

    private Map<UUID, VoteChoice> decodeBallotVotes(CompoundTag tag, VoteId voteId) {
        if (tag.getAllKeys().size() > HARD_MAX_VOTES_PER_BALLOT) {
            throw invalid(
                    "Ballot " + voteId + " vote count exceeds "
                            + HARD_MAX_VOTES_PER_BALLOT
            );
        }
        Map<UUID, VoteChoice> votes = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            UUID voter = parseCanonicalUuid(key, "Votes of " + voteId);
            requireType(tag, key, Tag.TAG_STRING, "Votes of " + voteId);
            VoteChoice choice = enumValue(
                    VoteChoice.class,
                    tag.getString(key),
                    "Votes of " + voteId
            );
            if (votes.put(voter, choice) != null) {
                throw invalid("Duplicate voter on ballot " + voteId + ": " + voter);
            }
        }
        return votes;
    }

    private Map<BillId, Bill> decodeBills(
            CompoundTag tag,
            Map<ProposalId, Proposal> proposals
    ) {
        if (tag.getAllKeys().size() > HARD_MAX_BILLS) {
            throw invalid("Bill count exceeds " + HARD_MAX_BILLS);
        }
        Map<BillId, Bill> bills = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            BillId billId = parseBillId(key);
            requireType(tag, key, Tag.TAG_COMPOUND, BILLS);
            Bill bill = decodeBill(tag.getCompound(key), billId, proposals);
            if (bills.put(billId, bill) != null) {
                throw invalid("Duplicate bill id: " + billId);
            }
        }
        return bills;
    }

    private Bill decodeBill(
            CompoundTag tag,
            BillId expectedId,
            Map<ProposalId, Proposal> proposals
    ) {
        requireOnlyKeys(tag, BILL_KEYS, "bill " + expectedId);
        requireType(tag, BILL_VERSION, Tag.TAG_INT, "bill " + expectedId);
        requireType(tag, BILL_ID, Tag.TAG_INT_ARRAY, "bill " + expectedId);
        requireType(tag, PROPOSAL_ID, Tag.TAG_INT_ARRAY, "bill " + expectedId);
        requireType(tag, STATE, Tag.TAG_STRING, "bill " + expectedId);
        requireType(tag, PASSED_AT, Tag.TAG_LONG, "bill " + expectedId);
        requireType(tag, RECORD_REVISION, Tag.TAG_LONG, "bill " + expectedId);

        int version = tag.getInt(BILL_VERSION);
        if (version != Bill.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported bill schema version for " + expectedId + ": " + version
            );
        }
        UUID storedId = tag.getUUID(BILL_ID);
        if (!expectedId.value().equals(storedId)) {
            throw invalid(
                    "Bills key " + expectedId
                            + " does not match record billId " + storedId
            );
        }
        ProposalId proposalId = parseProposalId(tag, PROPOSAL_ID, "bill " + expectedId);
        if (!proposals.containsKey(proposalId)) {
            throw invalid(
                    "Bill " + expectedId + " references missing proposal " + proposalId
            );
        }
        try {
            return new Bill(
                    version,
                    expectedId,
                    proposalId,
                    enumValue(BillState.class, tag.getString(STATE),
                            "State for " + expectedId),
                    tag.getLong(PASSED_AT),
                    tag.getLong(RECORD_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid bill " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private List<TransitionRecord> decodeTransitions(
            ListTag list,
            Map<ProposalId, Proposal> proposals
    ) {
        if (list.size() > HARD_MAX_TRANSITIONS) {
            throw invalid("Transition count exceeds " + HARD_MAX_TRANSITIONS);
        }
        List<TransitionRecord> transitions = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = list.getCompound(index);
            requireOnlyKeys(tag, TRANSITION_KEYS, "transition " + index);
            requireType(tag, TRANSITION_VERSION, Tag.TAG_INT, "transition " + index);
            requireType(tag, PROPOSAL_ID, Tag.TAG_INT_ARRAY, "transition " + index);
            requireType(tag, ACTOR, Tag.TAG_INT_ARRAY, "transition " + index);
            requireType(tag, AT_MILLIS, Tag.TAG_LONG, "transition " + index);
            requireType(tag, TRIGGER, Tag.TAG_STRING, "transition " + index);
            requireType(tag, AFTER, Tag.TAG_STRING, "transition " + index);
            requireType(tag, RECORD_REVISION, Tag.TAG_LONG, "transition " + index);

            int version = tag.getInt(TRANSITION_VERSION);
            if (version != TransitionRecord.CURRENT_SCHEMA_VERSION) {
                throw invalid(
                        "Unsupported transition schema version at " + index + ": " + version
                );
            }
            ProposalId proposalId = parseProposalId(
                    tag, PROPOSAL_ID, "transition " + index
            );
            if (!proposals.containsKey(proposalId)) {
                throw invalid(
                        "Transition at " + index
                                + " references missing proposal " + proposalId
                );
            }
            Optional<BillState> before = tag.contains(BEFORE, Tag.TAG_STRING)
                    ? Optional.of(enumValue(BillState.class, tag.getString(BEFORE),
                            "Before at " + index))
                    : Optional.empty();
            try {
                transitions.add(new TransitionRecord(
                        version,
                        proposalId,
                        tag.getUUID(ACTOR),
                        tag.getLong(AT_MILLIS),
                        enumValue(BillTransitionTrigger.class, tag.getString(TRIGGER),
                                "Trigger at " + index),
                        before,
                        enumValue(BillState.class, tag.getString(AFTER),
                                "After at " + index),
                        tag.getLong(RECORD_REVISION)
                ));
            } catch (IllegalArgumentException failure) {
                throw invalid(
                        "Invalid transition at " + index + ": " + failure.getMessage(),
                        failure
                );
            }
        }
        return transitions;
    }

    // ------------------------------------------------------------------
    // encode
    // ------------------------------------------------------------------

    /**
     * Encodes a validated snapshot deterministically: every index is written
     * in sorted lexical key order; the transition ledger keeps its order.
     */
    public CompoundTag encode(ParliamentStoreSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(STORE_VERSION, snapshot.storeVersion());
        root.putLong(STORE_REVISION, snapshot.storeRevision());

        ListTag citizenRoster = new ListTag();
        snapshot.citizenRoster().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(citizen -> citizenRoster.add(encodeUuid(citizen)));
        root.put(CITIZEN_ROSTER, citizenRoster);

        CompoundTag proposals = new CompoundTag();
        TreeMap<String, Proposal> orderedProposals = new TreeMap<>();
        snapshot.proposals().forEach(
                (id, proposal) -> orderedProposals.put(id.canonicalKey(), proposal)
        );
        orderedProposals.forEach(
                (key, proposal) -> proposals.put(key, encodeProposal(proposal))
        );
        root.put(PROPOSALS, proposals);

        CompoundTag votes = new CompoundTag();
        TreeMap<String, Vote> orderedVotes = new TreeMap<>();
        snapshot.votes().forEach(
                (id, vote) -> orderedVotes.put(id.canonicalKey(), vote)
        );
        orderedVotes.forEach(
                (key, vote) -> votes.put(key, encodeVote(vote))
        );
        root.put(VOTES, votes);

        CompoundTag bills = new CompoundTag();
        TreeMap<String, Bill> orderedBills = new TreeMap<>();
        snapshot.bills().forEach(
                (id, bill) -> orderedBills.put(id.canonicalKey(), bill)
        );
        orderedBills.forEach(
                (key, bill) -> bills.put(key, encodeBill(bill))
        );
        root.put(BILLS, bills);

        ListTag transitions = new ListTag();
        for (TransitionRecord transition : snapshot.transitions()) {
            transitions.add(encodeTransition(transition));
        }
        root.put(TRANSITIONS, transitions);
        return root;
    }

    private CompoundTag encodeProposal(Proposal proposal) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(PROPOSAL_VERSION, proposal.schemaVersion());
        tag.putUUID(PROPOSAL_ID, proposal.proposalId().value());
        tag.putLong(PROPOSAL_SEQ, proposal.proposalSeq());
        tag.putString(TITLE, proposal.title());
        tag.putString(NORM_LEVEL, proposal.normLevel().name());
        tag.putString(FULL_TEXT, proposal.fullText());
        tag.putUUID(PROPOSER_REF, proposal.proposerRef());
        tag.putString(STATE, proposal.state().name());
        tag.putLong(CREATED_AT, proposal.createdAt());
        tag.putLong(RECORD_REVISION, proposal.recordRevision());
        return tag;
    }

    private CompoundTag encodeVote(Vote vote) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VOTE_VERSION, vote.schemaVersion());
        tag.putUUID(VOTE_ID, vote.voteId().value());
        tag.putUUID(PROPOSAL_ID, vote.proposalId().value());
        tag.putString(BALLOT_STATE, vote.ballotState().name());
        tag.putLong(VOTES_FOR, vote.votesFor());
        tag.putLong(VOTES_AGAINST, vote.votesAgainst());
        tag.putLong(VOTES_ABSTAIN, vote.votesAbstain());
        tag.putLong(REQUIRED_THRESHOLD, vote.requiredThreshold());
        tag.putLong(FROZEN_ROSTER_COUNT, vote.frozenRosterCount());
        ListTag frozenRoster = new ListTag();
        vote.frozenRoster().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(citizen -> frozenRoster.add(encodeUuid(citizen)));
        tag.put(FROZEN_ROSTER, frozenRoster);
        tag.putLong(OPENED_AT, vote.openedAt());
        vote.closedAt().ifPresent(at -> tag.putLong(CLOSED_AT, at));
        tag.putLong(VOTE_REVISION, vote.voteRevision());
        CompoundTag votes = new CompoundTag();
        TreeMap<String, VoteChoice> orderedVotes = new TreeMap<>();
        vote.votes().forEach(
                (voter, choice) -> orderedVotes.put(voter.toString(), choice)
        );
        orderedVotes.forEach(
                (key, choice) -> votes.putString(key, choice.name())
        );
        tag.put(BALLOT_VOTES, votes);
        return tag;
    }

    private CompoundTag encodeBill(Bill bill) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(BILL_VERSION, bill.schemaVersion());
        tag.putUUID(BILL_ID, bill.billId().value());
        tag.putUUID(PROPOSAL_ID, bill.proposalId().value());
        tag.putString(STATE, bill.state().name());
        tag.putLong(PASSED_AT, bill.passedAt());
        tag.putLong(RECORD_REVISION, bill.recordRevision());
        return tag;
    }

    private CompoundTag encodeTransition(TransitionRecord transition) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TRANSITION_VERSION, transition.schemaVersion());
        tag.putUUID(PROPOSAL_ID, transition.proposalId().value());
        tag.putUUID(ACTOR, transition.actor());
        tag.putLong(AT_MILLIS, transition.atMillis());
        tag.putString(TRIGGER, transition.trigger().name());
        transition.before().ifPresent(state -> tag.putString(BEFORE, state.name()));
        tag.putString(AFTER, transition.after().name());
        tag.putLong(RECORD_REVISION, transition.recordRevision());
        return tag;
    }

    // ------------------------------------------------------------------
    // size
    // ------------------------------------------------------------------

    /** Serialized (uncompressed) size of an encoded namespace snapshot. */
    public int encodedSize(CompoundTag snapshot) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeUnnamedTag(snapshot, new DataOutputStream(out));
            return out.size();
        } catch (IOException failure) {
            return Integer.MAX_VALUE;
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private IntArrayTag encodeUuid(UUID uuid) {
        return new IntArrayTag(new int[]{
                (int) (uuid.getMostSignificantBits() >> 32),
                (int) uuid.getMostSignificantBits(),
                (int) (uuid.getLeastSignificantBits() >> 32),
                (int) uuid.getLeastSignificantBits()
        });
    }

    private ProposalId parseProposalId(CompoundTag tag, String key, String path) {
        return ProposalId.of(tag.getUUID(key));
    }

    private ProposalId parseCanonicalId(String value, String index) {
        return ProposalId.of(parseCanonicalUuid(value, index));
    }

    private VoteId parseVoteId(String value) {
        return VoteId.of(parseCanonicalUuid(value, VOTES));
    }

    private BillId parseBillId(String value) {
        return BillId.of(parseCanonicalUuid(value, BILLS));
    }

    private UUID parseCanonicalUuid(String value, String path) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw invalid(path + " key is not canonical: " + value);
            }
            return parsed;
        } catch (IllegalArgumentException failure) {
            if (failure instanceof ParliamentNbtException nbtFailure) {
                throw nbtFailure;
            }
            throw invalid("Invalid " + path + " key: " + value, failure);
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw invalid("Unsupported " + field + " value: " + value);
        }
    }

    private static void requireType(CompoundTag tag, String key, int type, String path) {
        if (!tag.contains(key, type)) {
            throw invalid(
                    path + " is missing required field " + key + " or has the wrong type"
            );
        }
    }

    private static void requireOnlyKeys(CompoundTag tag, Set<String> allowed, String path) {
        for (String key : tag.getAllKeys()) {
            if (!allowed.contains(key)) {
                throw invalid(path + " contains unsupported field " + key);
            }
        }
    }

    private static ParliamentNbtException invalid(String message) {
        return new ParliamentNbtException(message);
    }

    private static ParliamentNbtException invalid(String message, Throwable cause) {
        return new ParliamentNbtException(message, cause);
    }
}
