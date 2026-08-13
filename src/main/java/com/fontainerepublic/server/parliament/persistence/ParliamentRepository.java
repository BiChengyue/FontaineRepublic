package com.fontainerepublic.server.parliament.persistence;

import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.parliament.model.Bill;
import com.fontainerepublic.server.parliament.model.BillId;
import com.fontainerepublic.server.parliament.model.Proposal;
import com.fontainerepublic.server.parliament.model.ProposalId;
import com.fontainerepublic.server.parliament.model.ProposalStage;
import com.fontainerepublic.server.parliament.model.Referendum;
import com.fontainerepublic.server.parliament.model.TransitionRecord;
import com.fontainerepublic.server.parliament.model.Vote;
import com.fontainerepublic.server.parliament.model.VoteId;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Single-writer repository owning the {@code "parliament"} NBT namespace
 * (FR-PAR-001-A §3.4).
 *
 * <p>This class is the sole production owner of
 * {@link DataManager#getModuleData(String)} /
 * {@link DataManager#commitModuleData(String, CompoundTag)} for the
 * {@code "parliament"} key. All mutations run on the logical server owner
 * thread. The repository exposes exact lookups and one ordered, bounded
 * projection ({@link #proposalsAfter}) — no full enumeration API.</p>
 *
 * <p>Every authoritative mutation is a complete immutable replacement
 * snapshot ({@link #commit(ParliamentStoreSnapshot)}): the caller builds the
 * next state, the repository encodes it, checks the byte budget, commits it
 * through the FR-CORE-002 durable gate, and only then publishes the new
 * in-memory state. A failed write has no side effects — no proposal, vote,
 * bill, transition, or revision change, and no downstream event. Store
 * revision increments exactly once per committed mutation.</p>
 *
 * <p>On an empty namespace the repository starts fresh (revision 0); a
 * present namespace is decoded strictly and fail-closed: unknown fields,
 * wrong types, newer versions, non-canonical ids, or broken referential
 * integrity reject the whole load and disable the namespace.</p>
 */
public final class ParliamentRepository {

    /** Reserved module-data key for the parliament namespace. */
    public static final String MODULE_DATA_KEY = "parliament";

    private final ParliamentStore store;
    private final ParliamentNbtCodec codec;
    private final ParliamentLimits limits;
    private final Thread ownerThread;

    private final LinkedHashMap<ProposalId, Proposal> proposals = new LinkedHashMap<>();
    private final LinkedHashMap<VoteId, Vote> votes = new LinkedHashMap<>();
    private final LinkedHashMap<BillId, Bill> bills = new LinkedHashMap<>();
    private final List<TransitionRecord> transitions = new ArrayList<>();
    private final TreeSet<UUID> citizenRoster = new TreeSet<>(Comparator.comparing(UUID::toString));
    private final LinkedHashMap<ProposalId, ProposalStage> stages = new LinkedHashMap<>();
    private final LinkedHashMap<ProposalId, Referendum> referendums = new LinkedHashMap<>();
    private long storeRevision;

    /**
     * Creates a repository backed by the production {@link DataManager} store.
     */
    public static ParliamentRepository createProduction(ParliamentNbtCodec codec) {
        Objects.requireNonNull(codec, "codec");
        return new ParliamentRepository(
                new DataManagerParliamentStore(),
                codec,
                ParliamentLimits.DEFAULT
        );
    }

    public ParliamentRepository(
            ParliamentStore store,
            ParliamentNbtCodec codec,
            ParliamentLimits limits
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.ownerThread = Thread.currentThread();

        CompoundTag loaded = store.load().copy();
        if (loaded.isEmpty()) {
            this.storeRevision = 0L;
        } else {
            ParliamentStoreSnapshot snapshot = codec.decode(loaded);
            enforceLoadedCapacity(snapshot);
            publish(snapshot);
        }
    }

    // ------------------------------------------------------------------
    // read surface (exact lookups; one bounded, ordered projection)
    // ------------------------------------------------------------------

    public Optional<Proposal> proposal(ProposalId proposalId) {
        requireOwnerThread();
        return Optional.ofNullable(
                proposals.get(Objects.requireNonNull(proposalId, "proposalId"))
        );
    }

    public Optional<Vote> vote(VoteId voteId) {
        requireOwnerThread();
        return Optional.ofNullable(votes.get(Objects.requireNonNull(voteId, "voteId")));
    }

    public Optional<Bill> bill(BillId billId) {
        requireOwnerThread();
        return Optional.ofNullable(bills.get(Objects.requireNonNull(billId, "billId")));
    }

    public Optional<ProposalStage> stage(ProposalId proposalId) {
        requireOwnerThread();
        return Optional.ofNullable(
                stages.get(Objects.requireNonNull(proposalId, "proposalId"))
        );
    }

    public Optional<Referendum> referendum(ProposalId proposalId) {
        requireOwnerThread();
        return Optional.ofNullable(
                referendums.get(Objects.requireNonNull(proposalId, "proposalId"))
        );
    }

    /**
     * Ordered, bounded projection of proposals by ascending sequence,
     * strictly after {@code afterSeq}, at most {@code limit} records
     * ({@code limit} must be positive). Never returns more than
     * {@code limit}; no unbounded enumeration exists.
     */
    public List<Proposal> proposalsAfter(long afterSeq, int limit) {
        requireOwnerThread();
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return proposals.values().stream()
                .filter(proposal -> proposal.proposalSeq() > afterSeq)
                .sorted(Comparator.comparingLong(Proposal::proposalSeq))
                .limit(limit)
                .toList();
    }

    /** Current immutable namespace snapshot. */
    public ParliamentStoreSnapshot snapshot() {
        requireOwnerThread();
        return new ParliamentStoreSnapshot(
                ParliamentStoreSnapshot.CURRENT_STORE_VERSION,
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

    // ------------------------------------------------------------------
    // write surface
    // ------------------------------------------------------------------

    /**
     * Commits one complete replacement snapshot. The caller (service layer)
     * is responsible for the semantic delta and for idempotency (a
     * same-state candidate must never be committed); this method is the
     * single mutation boundary: capacity, revision space, encoding, byte
     * budget, and the durable gate are all enforced here. On any failure
     * nothing is published and no revision is consumed.
     */
    public void commit(ParliamentStoreSnapshot candidate) {
        requireOwnerThread();
        Objects.requireNonNull(candidate, "candidate");
        if (candidate.storeRevision() != storeRevision + 1) {
            throw new IllegalStateException(
                    "Candidate storeRevision " + candidate.storeRevision()
                            + " must be current + 1 (" + (storeRevision + 1) + ")"
            );
        }
        enforceCandidateCapacity(candidate);
        requireStoreRevisionSpace();

        CompoundTag encoded = codec.encode(candidate);
        int bytes = codec.encodedSize(encoded);
        if (bytes > limits.maxTotalBytes()) {
            throw new ParliamentUnavailableException(
                    ParliamentUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Parliament namespace would exceed the byte budget (" + bytes
                            + " > " + limits.maxTotalBytes() + ")"
            );
        }

        DurableCommitResult result;
        try {
            result = store.commit(encoded);
        } catch (RuntimeException failure) {
            throw new ParliamentUnavailableException(
                    ParliamentUnavailableException.CODE_STORE_FAILURE,
                    "Parliament store rejected a commit: " + failure.getMessage(),
                    failure
            );
        }
        if (result.status() != DurableCommitStatus.COMMITTED) {
            throw new ParliamentUnavailableException(
                    ParliamentUnavailableException.CODE_STORE_FAILURE,
                    "Durable commit of parliament failed (status="
                            + result.status() + ", code=" + result.failureCode() + ")"
            );
        }
        publish(candidate);
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    /** Swaps in a store-accepted candidate; only reachable on COMMITTED. */
    private void publish(ParliamentStoreSnapshot snapshot) {
        proposals.clear();
        proposals.putAll(snapshot.proposals());
        votes.clear();
        votes.putAll(snapshot.votes());
        bills.clear();
        bills.putAll(snapshot.bills());
        transitions.clear();
        transitions.addAll(snapshot.transitions());
        citizenRoster.clear();
        citizenRoster.addAll(snapshot.citizenRoster());
        stages.clear();
        stages.putAll(snapshot.stages());
        referendums.clear();
        referendums.putAll(snapshot.referendums());
        storeRevision = snapshot.storeRevision();
    }

    private void requireStoreRevisionSpace() {
        if (storeRevision == Long.MAX_VALUE) {
            throw new ParliamentUnavailableException(
                    ParliamentUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Parliament store revision space exhausted"
            );
        }
    }

    private void enforceCandidateCapacity(ParliamentStoreSnapshot candidate) {
        if (candidate.proposals().size() > limits.maxProposals()) {
            throw capacity("proposal count " + candidate.proposals().size()
                    + " exceeds the budget of " + limits.maxProposals());
        }
        if (candidate.votes().size() > limits.maxVotes()) {
            throw capacity("vote count " + candidate.votes().size()
                    + " exceeds the budget of " + limits.maxVotes());
        }
        if (candidate.bills().size() > limits.maxBills()) {
            throw capacity("bill count " + candidate.bills().size()
                    + " exceeds the budget of " + limits.maxBills());
        }
        if (candidate.transitions().size() > limits.maxTransitions()) {
            throw capacity("transition count " + candidate.transitions().size()
                    + " exceeds the budget of " + limits.maxTransitions());
        }
        if (candidate.citizenRoster().size() > limits.maxCitizens()) {
            throw capacity("citizen roster size " + candidate.citizenRoster().size()
                    + " exceeds the budget of " + limits.maxCitizens());
        }
        // Stage metadata is one per proposal and referendums one per
        // amendment proposal: the proposal budget bounds both.
        if (candidate.stages().size() > limits.maxProposals()) {
            throw capacity("stage count " + candidate.stages().size()
                    + " exceeds the proposal budget of " + limits.maxProposals());
        }
        if (candidate.referendums().size() > limits.maxProposals()) {
            throw capacity("referendum count " + candidate.referendums().size()
                    + " exceeds the proposal budget of " + limits.maxProposals());
        }
    }

    private void enforceLoadedCapacity(ParliamentStoreSnapshot snapshot) {
        if (snapshot.proposals().size() > limits.maxProposals()
                || snapshot.votes().size() > limits.maxVotes()
                || snapshot.bills().size() > limits.maxBills()
                || snapshot.transitions().size() > limits.maxTransitions()
                || snapshot.citizenRoster().size() > limits.maxCitizens()
                || snapshot.stages().size() > limits.maxProposals()
                || snapshot.referendums().size() > limits.maxProposals()) {
            throw new ParliamentUnavailableException(
                    ParliamentUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Loaded parliament namespace exceeds the configured budget"
            );
        }
    }

    private ParliamentUnavailableException capacity(String detail) {
        return new ParliamentUnavailableException(
                ParliamentUnavailableException.CODE_CAPACITY_EXCEEDED,
                "Parliament " + detail
        );
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "ParliamentRepository may only be accessed from its owning server thread"
            );
        }
    }

    // ------------------------------------------------------------------
    // production store
    // ------------------------------------------------------------------

    private static final class DataManagerParliamentStore implements ParliamentStore {
        @Override
        public CompoundTag load() {
            return DataManager.getModuleData(MODULE_DATA_KEY).copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            return DataManager.commitModuleData(
                    MODULE_DATA_KEY,
                    Objects.requireNonNull(snapshot, "snapshot").copy()
            );
        }
    }
}
