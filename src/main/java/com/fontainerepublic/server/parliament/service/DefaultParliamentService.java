package com.fontainerepublic.server.parliament.service;

import com.fontainerepublic.server.audit.api.AuditDraft;
import com.fontainerepublic.server.audit.api.AuditReceipt;
import com.fontainerepublic.server.audit.api.AuditService;
import com.fontainerepublic.server.audit.model.AuditActorType;
import com.fontainerepublic.server.audit.model.AuditCategory;
import com.fontainerepublic.server.audit.model.AuditClassification;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.api.ValidationResult;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.parliament.api.BillReceipt;
import com.fontainerepublic.server.parliament.api.ParliamentService;
import com.fontainerepublic.server.parliament.api.ProposalDraft;
import com.fontainerepublic.server.parliament.api.ProposalProjection;
import com.fontainerepublic.server.parliament.api.ProposalReceipt;
import com.fontainerepublic.server.parliament.api.VoteReceipt;
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
import com.fontainerepublic.server.parliament.persistence.ParliamentIdSource;
import com.fontainerepublic.server.parliament.persistence.ParliamentNbtCodec;
import com.fontainerepublic.server.parliament.persistence.ParliamentRepository;
import com.fontainerepublic.server.parliament.persistence.ParliamentStoreSnapshot;
import com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Runtime implementation of {@link ParliamentService} (FR-PAR-001-A §4).
 *
 * <p>The service is a thin, owner-thread-scoped facade over the single-writer
 * {@link ParliamentRepository}. It supplies the server clock and id source,
 * resolves actors/voters through the PlayerData + FR-CIT chain, and enforces
 * the {@code ONSITE_OFFICIAL_DUTY} final mutation boundary for every
 * authoritative mutation through
 * {@link InstitutionAccessService#validateAtMutation} — a null or non-VALID
 * context rejects the mutation fail-closed. Authoritative mutations publish
 * only after the durable gate commits and are then recorded through the
 * audit service (audit failure never blocks an already-committed mutation).
 * Reads are exact or bounded projections only.</p>
 *
 * <p>The module owns a passive citizen roster (registered, verified citizens
 * who interacted with the parliament). At vote open the roster is frozen —
 * it becomes the ballot's denominator and its eligible-voter set — and the
 * required threshold is computed from the normative level (1/2, 2/3, 3/4,
 * ceiling). Parliament never executes laws: no other module's state is ever
 * touched, and no rank/office ever maps to a technical permission.</p>
 */
public final class DefaultParliamentService implements ParliamentService {

    private final ParliamentRepository repository;
    private final LongSupplier clock;
    private final ParliamentIdSource idSource;
    private final ParliamentCitizenDirectory citizens;
    private final InstitutionAccessService institutionAccess;
    private final AuditService auditService;

    public DefaultParliamentService(
            ParliamentRepository repository,
            LongSupplier clock,
            ParliamentIdSource idSource,
            ParliamentCitizenDirectory citizens,
            InstitutionAccessService institutionAccess,
            AuditService auditService
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idSource = Objects.requireNonNull(idSource, "idSource");
        this.citizens = Objects.requireNonNull(citizens, "citizens");
        this.institutionAccess =
                Objects.requireNonNull(institutionAccess, "institutionAccess");
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // authoritative mutations
    // ------------------------------------------------------------------

    @Override
    public ProposalReceipt submitProposal(ProposalDraft draft, OnSiteContext context) {
        Objects.requireNonNull(draft, "draft");
        if (draft.normLevel() == NormLevel.CONSTITUTION_BASIC) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_INVALID_REQUEST,
                    "Constitution-amendment proposals are a deferred revision; "
                            + "the amendment pipeline is not implemented"
            );
        }
        requireValidOnSite(context);
        UUID actor = context.playerId();
        requireActiveCitizen(actor);

        ParliamentStoreSnapshot current = repository.snapshot();
        ProposalId proposalId = ProposalId.of(idSource.nextUuid());
        long seq = nextProposalSeq(current);
        long now = now();

        Proposal created = new Proposal(
                Proposal.CURRENT_SCHEMA_VERSION,
                proposalId,
                seq,
                draft.title(),
                draft.normLevel(),
                draft.fullText(),
                actor,
                BillState.DRAFT,
                now,
                1
        );
        // Submission enters the legislative pipeline: DRAFT -> REVIEW in the
        // same committed snapshot (two transition records).
        Proposal inReview = created.withState(BillState.REVIEW);

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, inReview);
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.SUBMITTED,
                Optional.empty(),
                BillState.DRAFT,
                created.recordRevision()
        ));
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.REVIEW_STARTED,
                Optional.of(BillState.DRAFT),
                BillState.REVIEW,
                inReview.recordRevision()
        ));

        TreeSet<UUID> nextRoster = rosterWith(current, actor);
        commit(current, new ParliamentStoreSnapshot(
                ParliamentStoreSnapshot.CURRENT_STORE_VERSION,
                current.storeRevision() + 1,
                nextProposals,
                current.votes(),
                current.bills(),
                nextTransitions,
                nextRoster
        ));
        audit(
                actor,
                "proposal.submit",
                "proposal",
                proposalId.canonicalKey(),
                "Submitted " + draft.normLevel() + " proposal " + proposalId
                        + " (" + draft.title() + ")"
        );
        return new ProposalReceipt(inReview, now);
    }

    @Override
    public VoteReceipt openVote(UUID actor, ProposalId proposalId, OnSiteContext context) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireValidOnSite(context);
        UUID onSiteActor = context.playerId();
        if (!onSiteActor.equals(actor)) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ON_SITE_CONTEXT_INVALID,
                    "The on-site actor " + onSiteActor
                            + " does not match the acting citizen " + actor
            );
        }
        requireActiveCitizen(actor);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = current.proposals().get(proposalId);
        if (proposal == null) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_PROPOSAL_NOT_FOUND,
                    "Proposal " + proposalId + " does not exist"
            );
        }
        if (proposal.state() != BillState.REVIEW) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only REVIEW proposals can open a vote"
            );
        }
        TreeSet<UUID> nextRoster = rosterWith(current, actor);
        if (nextRoster.isEmpty()) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ROSTER_UNAVAILABLE,
                    "No citizens are registered; a ballot cannot be opened"
            );
        }

        long requiredThreshold = requiredThreshold(
                proposal.normLevel(),
                nextRoster.size()
        );
        long now = now();
        VoteId voteId = VoteId.of(idSource.nextUuid());
        Vote vote = new Vote(
                Vote.CURRENT_SCHEMA_VERSION,
                voteId,
                proposalId,
                VoteBallotState.OPEN,
                0,
                0,
                0,
                requiredThreshold,
                nextRoster.size(),
                nextRoster,
                now,
                Optional.empty(),
                1,
                Map.of()
        );
        Proposal voting = proposal.withState(BillState.VOTING);

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, voting);
        Map<VoteId, Vote> nextVotes = new LinkedHashMap<>(current.votes());
        nextVotes.put(voteId, vote);
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.VOTE_OPENED,
                Optional.of(BillState.REVIEW),
                BillState.VOTING,
                voting.recordRevision()
        ));

        commit(current, new ParliamentStoreSnapshot(
                ParliamentStoreSnapshot.CURRENT_STORE_VERSION,
                current.storeRevision() + 1,
                nextProposals,
                nextVotes,
                current.bills(),
                nextTransitions,
                nextRoster
        ));
        audit(
                actor,
                "vote.open",
                "vote",
                voteId.canonicalKey(),
                "Opened ballot " + voteId + " for proposal " + proposalId
                        + " (threshold " + requiredThreshold + " of "
                        + nextRoster.size() + ")"
        );
        return new VoteReceipt(vote, true, now, Optional.empty());
    }

    @Override
    public VoteReceipt castVote(
            UUID voter,
            VoteId voteId,
            VoteChoice choice,
            OnSiteContext context
    ) {
        Objects.requireNonNull(voteId, "voteId");
        Objects.requireNonNull(choice, "choice");
        requireValidOnSite(context);
        UUID onSiteActor = context.playerId();
        if (!onSiteActor.equals(voter)) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ON_SITE_CONTEXT_INVALID,
                    "The on-site actor " + onSiteActor
                            + " does not match the voting citizen " + voter
            );
        }
        requireActiveCitizen(voter);

        ParliamentStoreSnapshot current = repository.snapshot();
        Vote vote = current.votes().get(voteId);
        if (vote == null) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_VOTE_NOT_FOUND,
                    "Vote " + voteId + " does not exist"
            );
        }
        if (vote.ballotState() != VoteBallotState.OPEN) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_BALLOT_NOT_OPEN,
                    "Ballot " + voteId + " is not open for voting"
            );
        }
        if (vote.votes().containsKey(voter)) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ALREADY_VOTED,
                    "Citizen " + voter + " already voted on ballot " + voteId
                            + " (one vote per citizen)"
            );
        }
        if (!vote.frozenRoster().contains(voter)) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_NOT_CITIZEN,
                    "Citizen " + voter + " is not on the frozen roster of ballot "
                            + voteId
            );
        }

        long now = now();
        Vote updated;
        try {
            updated = vote.withCastVote(voter, choice, now);
        } catch (IllegalArgumentException failure) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_INVALID_REQUEST,
                    "Vote cast rejected: " + failure.getMessage(),
                    failure
            );
        }
        TreeSet<UUID> nextRoster = rosterWith(current, voter);

        Map<VoteId, Vote> nextVotes = new LinkedHashMap<>(current.votes());
        nextVotes.put(voteId, updated);

        commit(current, new ParliamentStoreSnapshot(
                ParliamentStoreSnapshot.CURRENT_STORE_VERSION,
                current.storeRevision() + 1,
                current.proposals(),
                nextVotes,
                current.bills(),
                current.transitions(),
                nextRoster
        ));
        audit(
                voter,
                "vote.cast",
                "vote",
                voteId.canonicalKey(),
                "Cast " + choice + " on ballot " + voteId
                        + " for proposal " + vote.proposalId()
        );
        return new VoteReceipt(updated, true, now, Optional.of(choice));
    }

    @Override
    public BillReceipt closeVoteAndAdvance(
            UUID actor,
            VoteId voteId,
            OnSiteContext context
    ) {
        Objects.requireNonNull(voteId, "voteId");

        ParliamentStoreSnapshot current = repository.snapshot();
        Vote vote = current.votes().get(voteId);
        if (vote == null) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_VOTE_NOT_FOUND,
                    "Vote " + voteId + " does not exist"
            );
        }
        if (vote.ballotState() == VoteBallotState.CLOSED) {
            // Idempotent no-op: the ballot is already closed; nothing is
            // validated, consumed, or committed.
            Vote closedBallot = vote;
            Bill existing = current.bills().values().stream()
                    .filter(bill -> bill.proposalId().equals(closedBallot.proposalId()))
                    .findFirst()
                    .orElse(null);
            return new BillReceipt(
                    Optional.ofNullable(existing),
                    existing != null,
                    false,
                    now()
            );
        }

        requireValidOnSite(context);
        UUID onSiteActor = context.playerId();
        if (!onSiteActor.equals(actor)) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ON_SITE_CONTEXT_INVALID,
                    "The on-site actor " + onSiteActor
                            + " does not match the acting citizen " + actor
            );
        }
        requireActiveCitizen(actor);

        current = repository.snapshot();
        vote = current.votes().get(voteId);
        if (vote == null || vote.ballotState() != VoteBallotState.OPEN) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_VOTE_NOT_FOUND,
                    "Vote " + voteId + " is not open"
            );
        }
        Proposal proposal = current.proposals().get(vote.proposalId());
        if (proposal == null || proposal.state() != BillState.VOTING) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + vote.proposalId()
                            + " is not in VOTING; the ballot cannot be closed"
            );
        }

        long now = now();
        Vote closed = vote.withClosed(now);
        boolean passed = vote.passed();

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        Map<VoteId, Vote> nextVotes = new LinkedHashMap<>(current.votes());
        nextVotes.put(voteId, closed);
        Map<BillId, Bill> nextBills = new LinkedHashMap<>(current.bills());
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        TreeSet<UUID> nextRoster = rosterWith(current, actor);

        Bill createdBill = null;
        Proposal advanced;
        if (passed) {
            advanced = proposal.withState(BillState.APPROVED);
            BillId billId = BillId.of(idSource.nextUuid());
            createdBill = new Bill(
                    Bill.CURRENT_SCHEMA_VERSION,
                    billId,
                    proposal.proposalId(),
                    BillState.APPROVED,
                    now,
                    1
            );
            nextBills.put(billId, createdBill);
            nextTransitions.add(new TransitionRecord(
                    TransitionRecord.CURRENT_SCHEMA_VERSION,
                    proposal.proposalId(),
                    actor,
                    now,
                    BillTransitionTrigger.VOTE_PASSED,
                    Optional.of(BillState.VOTING),
                    BillState.APPROVED,
                    advanced.recordRevision()
            ));
        } else {
            advanced = proposal.withState(BillState.REJECTED);
            nextTransitions.add(new TransitionRecord(
                    TransitionRecord.CURRENT_SCHEMA_VERSION,
                    proposal.proposalId(),
                    actor,
                    now,
                    BillTransitionTrigger.VOTE_REJECTED,
                    Optional.of(BillState.VOTING),
                    BillState.REJECTED,
                    advanced.recordRevision()
            ));
        }
        nextProposals.put(proposal.proposalId(), advanced);

        commit(current, new ParliamentStoreSnapshot(
                ParliamentStoreSnapshot.CURRENT_STORE_VERSION,
                current.storeRevision() + 1,
                nextProposals,
                nextVotes,
                nextBills,
                nextTransitions,
                nextRoster
        ));
        audit(
                actor,
                passed ? "vote.close.passed" : "vote.close.rejected",
                "vote",
                voteId.canonicalKey(),
                (passed ? "Passed" : "Rejected") + " ballot " + voteId
                        + " for proposal " + proposal.proposalId()
                        + " (" + closed.votesFor() + " for / "
                        + closed.votesAgainst() + " against / "
                        + closed.votesAbstain() + " abstain; threshold "
                        + closed.requiredThreshold() + ")"
        );
        return new BillReceipt(
                Optional.ofNullable(createdBill),
                passed,
                true,
                now
        );
    }

    // ------------------------------------------------------------------
    // exact reads (bounded; no enumeration API)
    // ------------------------------------------------------------------

    @Override
    public Optional<Bill> bill(BillId billId) {
        Objects.requireNonNull(billId, "billId");
        return repository.bill(billId);
    }

    @Override
    public List<ProposalProjection> proposals(long afterSeq, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        int bounded = Math.min(limit, MAX_PROJECTION_SIZE);
        return repository.proposalsAfter(afterSeq, bounded).stream()
                .map(ProposalProjection::from)
                .toList();
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private void commit(
            ParliamentStoreSnapshot current,
            ParliamentStoreSnapshot candidate
    ) {
        repository.commit(candidate);
    }

    private long nextProposalSeq(ParliamentStoreSnapshot snapshot) {
        return snapshot.proposals().values().stream()
                .mapToLong(Proposal::proposalSeq)
                .max()
                .orElse(0L) + 1;
    }

    /** Idempotent roster registration of a verified citizen. */
    private TreeSet<UUID> rosterWith(ParliamentStoreSnapshot snapshot, UUID citizen) {
        TreeSet<UUID> next = new TreeSet<>(
                java.util.Comparator.comparing(UUID::toString)
        );
        next.addAll(snapshot.citizenRoster());
        next.add(citizen);
        return next;
    }

    /**
     * Required passage threshold by normative level against the frozen roster
     * size, rounded up (FR-PAR-001-A §5): ordinary laws and administrative
     * rules 1/2, organic laws 2/3, constitutional basic laws 3/4.
     */
    private long requiredThreshold(NormLevel level, long rosterSize) {
        return switch (level) {
            case CONSTITUTION_BASIC -> ceilFraction(rosterSize, 3, 4);
            case ORGANIC -> ceilFraction(rosterSize, 2, 3);
            case ORDINARY, ADMINISTRATIVE -> ceilFraction(rosterSize, 1, 2);
        };
    }

    /** {@code ceil(size * numerator / denominator)} without floating point. */
    private long ceilFraction(long size, long numerator, long denominator) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        return (size * numerator + denominator - 1) / denominator;
    }

    /**
     * Final mutation boundary of every legislative duty (FR-PAR-001-A §2/§4):
     * the on-site context must revalidate as {@code ONSITE_OFFICIAL_DUTY} at
     * the authoritative position bound to the context. A null context and any
     * non-VALID outcome reject the mutation fail-closed with a stable code.
     */
    private void requireValidOnSite(OnSiteContext context) {
        ValidationResult result;
        if (context == null) {
            result = ValidationResult.invalid(ValidationResult.REASON_NOT_ISSUED);
        } else {
            result = institutionAccess.validateAtMutation(
                    context,
                    CapabilityClass.ONSITE_OFFICIAL_DUTY,
                    now(),
                    context.dimension(),
                    context.blockX(),
                    context.blockY(),
                    context.blockZ()
            );
        }
        if (!result.valid()) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ON_SITE_CONTEXT_INVALID,
                    "On-site official-duty context is not valid: " + result.reason()
            );
        }
    }

    private void requireActiveCitizen(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!citizens.isAvailable()) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_CITIZEN_DIRECTORY_UNAVAILABLE,
                    "Player/citizen services are not available at the mutation boundary"
            );
        }
        if (!citizens.hasPlayerRecord(playerId)) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    "Player UUID has no authoritative PlayerData record: " + playerId
            );
        }
        if (!citizens.isActiveCitizen(playerId)) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_NOT_CITIZEN,
                    "Player " + playerId + " is not an active citizen"
            );
        }
    }

    private void audit(
            UUID actor,
            String actionId,
            String targetType,
            String targetId,
            String summary
    ) {
        if (auditService == null) {
            return;
        }
        try {
            AuditDraft draft = new AuditDraft(
                    AuditActorType.PLAYER,
                    actor.toString(),
                    AuditCategory.LEGISLATION,
                    "parliament",
                    actionId,
                    Optional.of(targetType),
                    Optional.of(targetId),
                    AuditClassification.PUBLIC,
                    summary,
                    Optional.empty()
            );
            AuditReceipt receipt = auditService.recordAuthoritative(draft);
            if (!receipt.committed()) {
                LoggerFactory.getLogger(DefaultParliamentService.class).warn(
                        "[Parliament] Audit of {} was not durably committed: {}",
                        actionId,
                        receipt.failureCode()
                );
            }
        } catch (RuntimeException failure) {
            // Audit failure never blocks an already-committed mutation (audit
            // is a record, not an authority).
            LoggerFactory.getLogger(DefaultParliamentService.class).warn(
                    "[Parliament] Audit recording failed for {}: {}",
                    actionId,
                    failure.getMessage()
            );
        }
    }

    private ParliamentUnavailableException unavailable(String code, String message) {
        return new ParliamentUnavailableException(code, message);
    }

    private ParliamentUnavailableException unavailable(
            String code,
            String message,
            Throwable cause
    ) {
        return new ParliamentUnavailableException(code, message, cause);
    }

    private long now() {
        long value = clock.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("clock returned a negative timestamp");
        }
        return value;
    }
}
