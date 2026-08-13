package com.fontainerepublic.server.parliament.persistence;

import com.fontainerepublic.server.parliament.model.Bill;
import com.fontainerepublic.server.parliament.model.BillId;
import com.fontainerepublic.server.parliament.model.BillState;
import com.fontainerepublic.server.parliament.model.BillTransitionTrigger;
import com.fontainerepublic.server.parliament.model.NormLevel;
import com.fontainerepublic.server.parliament.model.Proposal;
import com.fontainerepublic.server.parliament.model.ProposalId;
import com.fontainerepublic.server.parliament.model.ProposalKind;
import com.fontainerepublic.server.parliament.model.ProposalStage;
import com.fontainerepublic.server.parliament.model.Referendum;
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
    private static final String STAGES = "Stages";
    private static final String REFERENDUMS = "Referendums";

    private static final String PROPOSAL_VERSION = "ProposalVersion";
    private static final String PROPOSAL_ID = "ProposalId";
    private static final String PROPOSAL_SEQ = "ProposalSeq";
    private static final String TITLE = "Title";
    private static final String NORM_LEVEL = "NormLevel";
    private static final String PROPOSAL_KIND = "Kind";
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

    private static final String STAGE_VERSION = "StageVersion";
    private static final String STAGE_STARTED_AT = "StageStartedAt";
    private static final String STAGE_DEADLINE_AT = "StageDeadlineAt";
    private static final String RETURN_BASIS = "ReturnBasis";
    private static final String COURT_ENTERED_FROM = "CourtEnteredFrom";
    private static final String VOTING_ENTERED_FROM = "VotingEnteredFrom";
    private static final String COURT_EXTENSION_COUNT = "CourtExtensionCount";
    private static final String STAGE_REVISION = "StageRevision";

    private static final String REFERENDUM_VERSION = "ReferendumVersion";
    private static final String REFERENDUM_REVISION = "ReferendumRevision";

    /** Hard structural caps, independent of the configurable budget. */
    private static final int HARD_MAX_PROPOSALS = 100_000;
    private static final int HARD_MAX_VOTES = 100_000;
    private static final int HARD_MAX_BILLS = 100_000;
    private static final int HARD_MAX_TRANSITIONS = 1_000_000;
    private static final int HARD_MAX_VOTES_PER_BALLOT = 100_000;
    private static final int HARD_MAX_STAGES = 100_000;
    private static final int HARD_MAX_REFERENDUMS = 100_000;

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION,
            STORE_REVISION,
            CITIZEN_ROSTER,
            PROPOSALS,
            VOTES,
            BILLS,
            TRANSITIONS,
            STAGES,
            REFERENDUMS
    );
    private static final Set<String> PROPOSAL_KEYS = Set.of(
            PROPOSAL_VERSION,
            PROPOSAL_ID,
            PROPOSAL_SEQ,
            TITLE,
            NORM_LEVEL,
            PROPOSAL_KIND,
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
    private static final Set<String> STAGE_KEYS = Set.of(
            STAGE_VERSION,
            PROPOSAL_ID,
            STAGE_STARTED_AT,
            STAGE_DEADLINE_AT,
            RETURN_BASIS,
            COURT_ENTERED_FROM,
            VOTING_ENTERED_FROM,
            COURT_EXTENSION_COUNT,
            STAGE_REVISION
    );
    private static final Set<String> REFERENDUM_KEYS = Set.of(
            REFERENDUM_VERSION,
            PROPOSAL_ID,
            FROZEN_ROSTER_COUNT,
            FROZEN_ROSTER,
            VOTES_FOR,
            VOTES_AGAINST,
            VOTES_ABSTAIN,
            OPENED_AT,
            CLOSED_AT,
            REFERENDUM_REVISION,
            BALLOT_VOTES
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
        Map<ProposalId, ProposalStage> stages = decodeStages(
                root.contains(STAGES, Tag.TAG_COMPOUND)
                        ? root.getCompound(STAGES)
                        : new CompoundTag(),
                proposals
        );
        Map<ProposalId, Referendum> referendums = decodeReferendums(
                root.contains(REFERENDUMS, Tag.TAG_COMPOUND)
                        ? root.getCompound(REFERENDUMS)
                        : new CompoundTag(),
                proposals
        );
        return new ParliamentStoreSnapshot(
                storeVersion,
                storeRevision,
                proposals,
                votes,
                bills,
                transitions,
                citizenRoster,
                stages,
                referendums
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
        if (tag.contains(PROPOSAL_KIND) && !tag.contains(PROPOSAL_KIND, Tag.TAG_STRING)) {
            throw invalid(
                    "proposal " + expectedId + " field " + PROPOSAL_KIND + " has the wrong type"
            );
        }

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
                    tag.contains(PROPOSAL_KIND, Tag.TAG_STRING)
                            ? enumValue(ProposalKind.class, tag.getString(PROPOSAL_KIND),
                                    "Kind for " + expectedId)
                            : ProposalKind.LEGISLATION,
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

    private Map<ProposalId, ProposalStage> decodeStages(
            CompoundTag tag,
            Map<ProposalId, Proposal> proposals
    ) {
        if (tag.getAllKeys().size() > HARD_MAX_STAGES) {
            throw invalid("Stage count exceeds " + HARD_MAX_STAGES);
        }
        Map<ProposalId, ProposalStage> stages = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            ProposalId proposalId = parseCanonicalId(key, STAGES);
            requireType(tag, key, Tag.TAG_COMPOUND, STAGES);
            ProposalStage stage = decodeStage(tag.getCompound(key), proposalId, proposals);
            if (stages.put(proposalId, stage) != null) {
                throw invalid("Duplicate stage for proposal: " + proposalId);
            }
        }
        return stages;
    }

    private ProposalStage decodeStage(
            CompoundTag tag,
            ProposalId expectedId,
            Map<ProposalId, Proposal> proposals
    ) {
        requireOnlyKeys(tag, STAGE_KEYS, "stage " + expectedId);
        requireType(tag, STAGE_VERSION, Tag.TAG_INT, "stage " + expectedId);
        requireType(tag, PROPOSAL_ID, Tag.TAG_INT_ARRAY, "stage " + expectedId);
        requireType(tag, COURT_EXTENSION_COUNT, Tag.TAG_INT, "stage " + expectedId);
        requireType(tag, STAGE_REVISION, Tag.TAG_LONG, "stage " + expectedId);
        if (!proposals.containsKey(expectedId)) {
            throw invalid(
                    "Stage " + expectedId + " references missing proposal"
            );
        }
        int version = tag.getInt(STAGE_VERSION);
        if (version != ProposalStage.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported stage schema version for " + expectedId + ": " + version
            );
        }
        UUID storedId = tag.getUUID(PROPOSAL_ID);
        if (!expectedId.value().equals(storedId)) {
            throw invalid(
                    "Stages key " + expectedId
                            + " does not match record proposalId " + storedId
            );
        }
        try {
            return new ProposalStage(
                    version,
                    expectedId,
                    optionalLong(tag, STAGE_STARTED_AT),
                    optionalLong(tag, STAGE_DEADLINE_AT),
                    optionalString(tag, RETURN_BASIS),
                    optionalEnum(tag, COURT_ENTERED_FROM, BillState.class),
                    optionalEnum(tag, VOTING_ENTERED_FROM, BillState.class),
                    tag.getInt(COURT_EXTENSION_COUNT),
                    tag.getLong(STAGE_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid stage " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private Map<ProposalId, Referendum> decodeReferendums(
            CompoundTag tag,
            Map<ProposalId, Proposal> proposals
    ) {
        if (tag.getAllKeys().size() > HARD_MAX_REFERENDUMS) {
            throw invalid("Referendum count exceeds " + HARD_MAX_REFERENDUMS);
        }
        Map<ProposalId, Referendum> referendums = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            ProposalId proposalId = parseCanonicalId(key, REFERENDUMS);
            requireType(tag, key, Tag.TAG_COMPOUND, REFERENDUMS);
            Referendum referendum =
                    decodeReferendum(tag.getCompound(key), proposalId, proposals);
            if (referendums.put(proposalId, referendum) != null) {
                throw invalid("Duplicate referendum for proposal: " + proposalId);
            }
        }
        return referendums;
    }

    private Referendum decodeReferendum(
            CompoundTag tag,
            ProposalId expectedId,
            Map<ProposalId, Proposal> proposals
    ) {
        requireOnlyKeys(tag, REFERENDUM_KEYS, "referendum " + expectedId);
        requireType(tag, REFERENDUM_VERSION, Tag.TAG_INT, "referendum " + expectedId);
        requireType(tag, PROPOSAL_ID, Tag.TAG_INT_ARRAY, "referendum " + expectedId);
        requireType(tag, FROZEN_ROSTER_COUNT, Tag.TAG_LONG, "referendum " + expectedId);
        requireType(tag, FROZEN_ROSTER, Tag.TAG_LIST, "referendum " + expectedId);
        requireType(tag, VOTES_FOR, Tag.TAG_LONG, "referendum " + expectedId);
        requireType(tag, VOTES_AGAINST, Tag.TAG_LONG, "referendum " + expectedId);
        requireType(tag, VOTES_ABSTAIN, Tag.TAG_LONG, "referendum " + expectedId);
        requireType(tag, OPENED_AT, Tag.TAG_LONG, "referendum " + expectedId);
        requireType(tag, REFERENDUM_REVISION, Tag.TAG_LONG, "referendum " + expectedId);
        requireType(tag, BALLOT_VOTES, Tag.TAG_COMPOUND, "referendum " + expectedId);
        if (!proposals.containsKey(expectedId)) {
            throw invalid(
                    "Referendum " + expectedId + " references missing proposal"
            );
        }
        int version = tag.getInt(REFERENDUM_VERSION);
        if (version != Referendum.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported referendum schema version for " + expectedId + ": " + version
            );
        }
        UUID storedId = tag.getUUID(PROPOSAL_ID);
        if (!expectedId.value().equals(storedId)) {
            throw invalid(
                    "Referendums key " + expectedId
                            + " does not match record proposalId " + storedId
            );
        }
        Optional<Long> closedAt = tag.contains(CLOSED_AT, Tag.TAG_LONG)
                ? Optional.of(tag.getLong(CLOSED_AT))
                : Optional.empty();
        try {
            return new Referendum(
                    version,
                    expectedId,
                    tag.getLong(FROZEN_ROSTER_COUNT),
                    decodeFrozenRoster(tag.getList(FROZEN_ROSTER, Tag.TAG_INT_ARRAY),
                            expectedId.toString()),
                    tag.getLong(VOTES_FOR),
                    tag.getLong(VOTES_AGAINST),
                    tag.getLong(VOTES_ABSTAIN),
                    tag.getLong(OPENED_AT),
                    closedAt,
                    tag.getLong(REFERENDUM_REVISION),
                    decodeBallotVotes(tag.getCompound(BALLOT_VOTES), expectedId.toString())
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid referendum " + expectedId + ": " + failure.getMessage(),
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
                            expectedId.toString()),
                    tag.getLong(OPENED_AT),
                    closedAt,
                    tag.getLong(VOTE_REVISION),
                    decodeBallotVotes(tag.getCompound(BALLOT_VOTES), expectedId.toString())
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

    private Set<UUID> decodeFrozenRoster(ListTag list, String label) {
        if (list.size() > HARD_MAX_VOTES_PER_BALLOT) {
            throw invalid(
                    label + " frozen roster exceeds "
                            + HARD_MAX_VOTES_PER_BALLOT
            );
        }
        LinkedHashSet<UUID> roster = new LinkedHashSet<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            int[] raw = list.getIntArray(index);
            if (raw.length != 4) {
                throw invalid(
                        label + " frozen roster entry at " + index
                                + " is not a canonical UUID"
                );
            }
            UUID citizen = new UUID(
                    (long) raw[0] << 32 | raw[1] & 0xFFFFFFFFL,
                    (long) raw[2] << 32 | raw[3] & 0xFFFFFFFFL
            );
            if (!roster.add(citizen)) {
                throw invalid(
                        label + " frozen roster contains duplicate "
                                + citizen
                );
            }
        }
        return roster;
    }

    private Map<UUID, VoteChoice> decodeBallotVotes(CompoundTag tag, String label) {
        if (tag.getAllKeys().size() > HARD_MAX_VOTES_PER_BALLOT) {
            throw invalid(
                    label + " vote count exceeds "
                            + HARD_MAX_VOTES_PER_BALLOT
            );
        }
        Map<UUID, VoteChoice> votes = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            UUID voter = parseCanonicalUuid(key, "Votes of " + label);
            requireType(tag, key, Tag.TAG_STRING, "Votes of " + label);
            VoteChoice choice = enumValue(
                    VoteChoice.class,
                    tag.getString(key),
                    "Votes of " + label
            );
            if (votes.put(voter, choice) != null) {
                throw invalid("Duplicate voter on ballot " + label + ": " + voter);
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

        CompoundTag stages = new CompoundTag();
        TreeMap<String, ProposalStage> orderedStages = new TreeMap<>();
        snapshot.stages().forEach(
                (id, stage) -> orderedStages.put(id.canonicalKey(), stage)
        );
        orderedStages.forEach(
                (key, stage) -> stages.put(key, encodeStage(stage))
        );
        root.put(STAGES, stages);

        CompoundTag referendums = new CompoundTag();
        TreeMap<String, Referendum> orderedReferendums = new TreeMap<>();
        snapshot.referendums().forEach(
                (id, referendum) -> orderedReferendums.put(id.canonicalKey(), referendum)
        );
        orderedReferendums.forEach(
                (key, referendum) -> referendums.put(key, encodeReferendum(referendum))
        );
        root.put(REFERENDUMS, referendums);
        return root;
    }

    private CompoundTag encodeProposal(Proposal proposal) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(PROPOSAL_VERSION, proposal.schemaVersion());
        tag.putUUID(PROPOSAL_ID, proposal.proposalId().value());
        tag.putLong(PROPOSAL_SEQ, proposal.proposalSeq());
        tag.putString(TITLE, proposal.title());
        tag.putString(NORM_LEVEL, proposal.normLevel().name());
        tag.putString(PROPOSAL_KIND, proposal.kind().name());
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

    private CompoundTag encodeStage(ProposalStage stage) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(STAGE_VERSION, stage.schemaVersion());
        tag.putUUID(PROPOSAL_ID, stage.proposalId().value());
        stage.stageStartedAt().ifPresent(at -> tag.putLong(STAGE_STARTED_AT, at));
        stage.stageDeadlineAt().ifPresent(at -> tag.putLong(STAGE_DEADLINE_AT, at));
        stage.returnBasis().ifPresent(basis -> tag.putString(RETURN_BASIS, basis));
        stage.courtReviewEnteredFrom()
                .ifPresent(state -> tag.putString(COURT_ENTERED_FROM, state.name()));
        stage.votingEnteredFrom()
                .ifPresent(state -> tag.putString(VOTING_ENTERED_FROM, state.name()));
        tag.putInt(COURT_EXTENSION_COUNT, stage.courtExtensionCount());
        tag.putLong(STAGE_REVISION, stage.stageRevision());
        return tag;
    }

    private CompoundTag encodeReferendum(Referendum referendum) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(REFERENDUM_VERSION, referendum.schemaVersion());
        tag.putUUID(PROPOSAL_ID, referendum.proposalId().value());
        tag.putLong(FROZEN_ROSTER_COUNT, referendum.frozenRosterCount());
        ListTag frozenRoster = new ListTag();
        referendum.frozenRoster().stream()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(citizen -> frozenRoster.add(encodeUuid(citizen)));
        tag.put(FROZEN_ROSTER, frozenRoster);
        tag.putLong(VOTES_FOR, referendum.votesFor());
        tag.putLong(VOTES_AGAINST, referendum.votesAgainst());
        tag.putLong(VOTES_ABSTAIN, referendum.votesAbstain());
        tag.putLong(OPENED_AT, referendum.openedAt());
        referendum.closedAt().ifPresent(at -> tag.putLong(CLOSED_AT, at));
        tag.putLong(REFERENDUM_REVISION, referendum.referendumRevision());
        CompoundTag votes = new CompoundTag();
        TreeMap<String, VoteChoice> orderedVotes = new TreeMap<>();
        referendum.votes().forEach(
                (voter, choice) -> orderedVotes.put(voter.toString(), choice)
        );
        orderedVotes.forEach(
                (key, choice) -> votes.putString(key, choice.name())
        );
        tag.put(BALLOT_VOTES, votes);
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

    private static Optional<Long> optionalLong(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return Optional.empty();
        }
        if (!tag.contains(key, Tag.TAG_LONG)) {
            throw invalid("field " + key + " has the wrong type");
        }
        return Optional.of(tag.getLong(key));
    }

    private static Optional<String> optionalString(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return Optional.empty();
        }
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw invalid("field " + key + " has the wrong type");
        }
        return Optional.of(tag.getString(key));
    }

    private static <T extends Enum<T>> Optional<T> optionalEnum(
            CompoundTag tag,
            String key,
            Class<T> type
    ) {
        if (!tag.contains(key)) {
            return Optional.empty();
        }
        if (!tag.contains(key, Tag.TAG_STRING)) {
            throw invalid("field " + key + " has the wrong type");
        }
        return Optional.of(enumValue(type, tag.getString(key), key));
    }

    private static ParliamentNbtException invalid(String message) {
        return new ParliamentNbtException(message);
    }

    private static ParliamentNbtException invalid(String message, Throwable cause) {
        return new ParliamentNbtException(message, cause);
    }
}
