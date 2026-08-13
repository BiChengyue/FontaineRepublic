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
import com.fontainerepublic.server.parliament.api.AmendmentDraft;
import com.fontainerepublic.server.parliament.api.BillReceipt;
import com.fontainerepublic.server.parliament.api.CourtReceipt;
import com.fontainerepublic.server.parliament.api.GuardianReceipt;
import com.fontainerepublic.server.parliament.api.ParliamentService;
import com.fontainerepublic.server.parliament.api.ProposalDraft;
import com.fontainerepublic.server.parliament.api.ProposalProjection;
import com.fontainerepublic.server.parliament.api.ProposalReceipt;
import com.fontainerepublic.server.parliament.api.ReferendumReceipt;
import com.fontainerepublic.server.parliament.api.VoteReceipt;
import com.fontainerepublic.server.parliament.model.Bill;
import com.fontainerepublic.server.parliament.model.BillId;
import com.fontainerepublic.server.parliament.model.BillState;
import com.fontainerepublic.server.parliament.model.BillTransitionTrigger;
import com.fontainerepublic.server.parliament.model.GuardianChannel;
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
import java.util.Set;
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

    /** Fixed actor of the real local Dedicated Server console channel. */
    public static final UUID CONSOLE_ACTOR =
            UUID.fromString("00000000-0000-0000-0000-00000000c0de");

    /** Guardian review window: ordinary/administrative laws 72 hours. */
    static final long GUARDIAN_REVIEW_ORDINARY_MILLIS = 72L * 60 * 60 * 1000;
    /** Guardian review window: constitutional basic laws 7 days. */
    static final long GUARDIAN_REVIEW_BASIC_MILLIS = 7L * 24 * 60 * 60 * 1000;
    /** Supreme-court review window: 14 days (extendable once by 7 days). */
    static final long COURT_REVIEW_MILLIS = 14L * 24 * 60 * 60 * 1000;
    /** Supreme-court review extension: 7 days. */
    static final long COURT_REVIEW_EXTENSION_MILLIS = 7L * 24 * 60 * 60 * 1000;
    /** Constitutional-consent window: 7 days (timeout counts as consent). */
    static final long CONSENT_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private final ParliamentRepository repository;
    private final LongSupplier clock;
    private final ParliamentIdSource idSource;
    private final ParliamentCitizenDirectory citizens;
    private final InstitutionAccessService institutionAccess;
    private final GuardianDirectory guardians;
    private final AuditService auditService;

    public DefaultParliamentService(
            ParliamentRepository repository,
            LongSupplier clock,
            ParliamentIdSource idSource,
            ParliamentCitizenDirectory citizens,
            InstitutionAccessService institutionAccess,
            GuardianDirectory guardians,
            AuditService auditService
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idSource = Objects.requireNonNull(idSource, "idSource");
        this.citizens = Objects.requireNonNull(citizens, "citizens");
        this.institutionAccess =
                Objects.requireNonNull(institutionAccess, "institutionAccess");
        this.guardians = Objects.requireNonNull(guardians, "guardians");
        this.auditService = auditService;
    }

    /**
     * Convenience constructor for pre-FR-PAR-002 callers: the guardian
     * directory defaults to a fail-closed identity that never authorizes a
     * guardian mutation (guardian actions are unavailable until the module
     * wires the real FR-ID directory).
     */
    public DefaultParliamentService(
            ParliamentRepository repository,
            LongSupplier clock,
            ParliamentIdSource idSource,
            ParliamentCitizenDirectory citizens,
            InstitutionAccessService institutionAccess,
            AuditService auditService
    ) {
        this(
                repository,
                clock,
                idSource,
                citizens,
                institutionAccess,
                new GuardianDirectory() {
                    @Override
                    public boolean isAvailable() {
                        return false;
                    }

                    @Override
                    public boolean isHydroArchon(UUID playerId) {
                        return false;
                    }
                },
                auditService
        );
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
                ProposalKind.LEGISLATION,
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
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, new ProposalStage(
                ProposalStage.CURRENT_SCHEMA_VERSION,
                proposalId,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                1
        ));
        commit(current, new ParliamentStoreSnapshot(
                ParliamentStoreSnapshot.CURRENT_STORE_VERSION,
                current.storeRevision() + 1,
                nextProposals,
                current.votes(),
                current.bills(),
                nextTransitions,
                nextRoster,
                nextStages,
                current.referendums()
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
        ProposalStage stage = current.stages().get(proposalId);
        ProposalStage nextStage = stage == null
                ? new ProposalStage(
                        ProposalStage.CURRENT_SCHEMA_VERSION,
                        proposalId,
                        Optional.of(now),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(BillState.REVIEW),
                        0,
                        1)
                : stage.withVotingOrigin(BillState.REVIEW, now);
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, nextStage);

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
                nextRoster,
                nextStages,
                current.referendums()
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
                nextRoster,
                current.stages(),
                current.referendums()
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
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        ProposalStage stage = current.stages().get(proposal.proposalId());
        ProposalStage resolvedStage = stage == null
                ? new ProposalStage(
                        ProposalStage.CURRENT_SCHEMA_VERSION,
                        proposal.proposalId(),
                        Optional.of(now),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        0,
                        1)
                : stage.withStageResolved(now);
        nextStages.put(proposal.proposalId(), resolvedStage);

        Bill existingBill = current.bills().values().stream()
                .filter(bill -> bill.proposalId().equals(proposal.proposalId()))
                .findFirst()
                .orElse(null);
        Bill createdBill = null;
        Proposal advanced;
        if (passed) {
            advanced = proposal.withState(BillState.APPROVED);
            BillTransitionTrigger trigger = BillTransitionTrigger.VOTE_PASSED;
            if (existingBill != null) {
                // A re-vote after a guardian return or a court return passes
                // with the re-vote trigger; the bill follows the proposal.
                nextBills.put(
                        existingBill.billId(),
                        existingBill.withState(BillState.APPROVED)
                );
                BillState origin = stage == null
                        ? null
                        : stage.votingEnteredFrom().orElse(null);
                if (origin == BillState.GUARDIAN_RETURNED) {
                    trigger = BillTransitionTrigger.GUARDIAN_OVERRIDE_PASSED;
                } else if (origin == BillState.COURT_REVIEW) {
                    trigger = BillTransitionTrigger.COURT_RETURN_PASSED;
                }
            } else {
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
            }
            nextTransitions.add(new TransitionRecord(
                    TransitionRecord.CURRENT_SCHEMA_VERSION,
                    proposal.proposalId(),
                    actor,
                    now,
                    trigger,
                    Optional.of(BillState.VOTING),
                    BillState.APPROVED,
                    advanced.recordRevision()
            ));
        } else {
            advanced = proposal.withState(BillState.REJECTED);
            if (existingBill != null) {
                nextBills.put(
                        existingBill.billId(),
                        existingBill.withState(BillState.REJECTED)
                );
            }
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
                nextRoster,
                nextStages,
                current.referendums()
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
                Optional.ofNullable(billOf(nextBills, proposal.proposalId())),
                passed,
                true,
                now
        );
    }

    // ------------------------------------------------------------------
    // guardian review (FR-PAR-002-A §4)
    // ------------------------------------------------------------------

    @Override
    public GuardianReceipt submitForGuardianReview(
            UUID actor,
            ProposalId proposalId,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireValidOnSite(context);
        requireActiveCitizen(actor);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        boolean ordinary = proposal.normLevel() == NormLevel.ORDINARY
                || proposal.normLevel() == NormLevel.ADMINISTRATIVE;
        if (proposal.kind() != ProposalKind.LEGISLATION
                || proposal.state() != BillState.APPROVED
                || !ordinary) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Only an APPROVED ordinary/administrative law enters the "
                            + "guardian review; " + proposalId + " is in state "
                            + proposal.state() + " (" + proposal.normLevel() + ")"
            );
        }
        long now = now();
        Proposal reviewed = proposal.withState(BillState.GUARDIAN_REVIEW);
        long deadline = now + GUARDIAN_REVIEW_ORDINARY_MILLIS;

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, reviewed);
        Map<BillId, Bill> nextBills = syncBill(
                current, proposalId, BillState.GUARDIAN_REVIEW
        );
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, stageFor(proposalId, current).withTimedStage(
                now,
                deadline,
                Optional.empty(),
                Optional.empty()
        ));
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.GUARDIAN_REVIEW_STARTED,
                Optional.of(BillState.APPROVED),
                BillState.GUARDIAN_REVIEW,
                reviewed.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                current.votes(),
                nextBills,
                nextTransitions,
                current.citizenRoster(),
                nextStages,
                current.referendums()
        ));
        audit(actor, "guardian.submit", "proposal", proposalId.canonicalKey(),
                "Submitted proposal " + proposalId + " to the guardian review");
        return new GuardianReceipt(
                reviewed,
                Optional.ofNullable(billOf(nextBills, proposalId)),
                true,
                now
        );
    }

    @Override
    public GuardianReceipt guardianApprove(
            UUID actor,
            ProposalId proposalId,
            GuardianChannel channel,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireGuardian(channel, actor, context);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.state() != BillState.GUARDIAN_REVIEW) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only GUARDIAN_REVIEW proposals can be approved"
            );
        }
        long now = now();
        Proposal approved = proposal.withState(BillState.APPROVED);

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, approved);
        Map<BillId, Bill> nextBills = syncBill(
                current, proposalId, BillState.APPROVED
        );
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, stageFor(proposalId, current).withStageResolved(now));
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.GUARDIAN_APPROVED,
                Optional.of(BillState.GUARDIAN_REVIEW),
                BillState.APPROVED,
                approved.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                current.votes(),
                nextBills,
                nextTransitions,
                current.citizenRoster(),
                nextStages,
                current.referendums()
        ));
        audit(actor, "guardian.approve", "proposal", proposalId.canonicalKey(),
                "Guardian approved proposal " + proposalId);
        return new GuardianReceipt(
                approved,
                Optional.ofNullable(billOf(nextBills, proposalId)),
                true,
                now
        );
    }

    @Override
    public GuardianReceipt guardianReturn(
            UUID actor,
            ProposalId proposalId,
            GuardianChannel channel,
            String basis,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        Objects.requireNonNull(basis, "basis");
        requireGuardian(channel, actor, context);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.state() != BillState.GUARDIAN_REVIEW) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only GUARDIAN_REVIEW proposals can be returned"
            );
        }
        if (proposal.normLevel() == NormLevel.ORGANIC) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_GUARDIAN_NO_VETO,
                    "The guardian has no veto over organic laws; proposal "
                            + proposalId + " cannot be returned"
            );
        }
        String normalizedBasis = basis.trim();
        if (normalizedBasis.isEmpty()
                || normalizedBasis.length() > ProposalStage.MAX_RETURN_BASIS_LENGTH) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_INVALID_REQUEST,
                    "The return basis must be a bounded (1.."
                            + ProposalStage.MAX_RETURN_BASIS_LENGTH
                            + ") constitutional clause or procedural error"
            );
        }
        long now = now();
        Proposal returned = proposal.withState(BillState.GUARDIAN_RETURNED);

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, returned);
        Map<BillId, Bill> nextBills = syncBill(
                current, proposalId, BillState.GUARDIAN_RETURNED
        );
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, stageFor(proposalId, current)
                .withGuardianReturn(normalizedBasis, now));
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.GUARDIAN_RETURNED,
                Optional.of(BillState.GUARDIAN_REVIEW),
                BillState.GUARDIAN_RETURNED,
                returned.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                current.votes(),
                nextBills,
                nextTransitions,
                current.citizenRoster(),
                nextStages,
                current.referendums()
        ));
        audit(actor, "guardian.return", "proposal", proposalId.canonicalKey(),
                "Guardian returned proposal " + proposalId + ": " + normalizedBasis);
        return new GuardianReceipt(
                returned,
                Optional.ofNullable(billOf(nextBills, proposalId)),
                true,
                now
        );
    }

    @Override
    public GuardianReceipt guardianRecuse(
            UUID actor,
            ProposalId proposalId,
            GuardianChannel channel,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireGuardian(channel, actor, context);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.state() != BillState.GUARDIAN_REVIEW) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; a recusal requires a GUARDIAN_REVIEW proposal"
            );
        }
        long now = now();
        Proposal inCourt = proposal.withState(BillState.COURT_REVIEW);

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, inCourt);
        Map<BillId, Bill> nextBills = syncBill(
                current, proposalId, BillState.COURT_REVIEW
        );
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, stageFor(proposalId, current).withTimedStage(
                now,
                now + COURT_REVIEW_MILLIS,
                Optional.of(BillState.GUARDIAN_REVIEW),
                Optional.empty()
        ));
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.GUARDIAN_RECUSED,
                Optional.of(BillState.GUARDIAN_REVIEW),
                BillState.COURT_REVIEW,
                inCourt.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                current.votes(),
                nextBills,
                nextTransitions,
                current.citizenRoster(),
                nextStages,
                current.referendums()
        ));
        audit(actor, "guardian.recuse", "proposal", proposalId.canonicalKey(),
                "Guardian recused on proposal " + proposalId
                        + "; the supreme court reviews instead");
        return new GuardianReceipt(
                inCourt,
                Optional.ofNullable(billOf(nextBills, proposalId)),
                true,
                now
        );
    }

    @Override
    public GuardianReceipt guardianTimeoutAdvance(
            UUID actor,
            ProposalId proposalId,
            GuardianChannel channel,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireGuardian(channel, actor, context);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.state() != BillState.GUARDIAN_REVIEW) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; a timeout advancement requires GUARDIAN_REVIEW"
            );
        }
        long now = now();
        ProposalStage stage = stageFor(proposalId, current);
        long deadline = stage.stageDeadlineAt().orElseThrow(() -> unavailable(
                ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                "Proposal " + proposalId + " has no guardian-review deadline"
        ));
        if (now < deadline) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_DEADLINE_NOT_REACHED,
                    "Guardian review of " + proposalId + " expires at " + deadline
                            + "; current time " + now
            );
        }
        Proposal approved = proposal.withState(BillState.APPROVED);

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, approved);
        Map<BillId, Bill> nextBills = syncBill(
                current, proposalId, BillState.APPROVED
        );
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, stage.withStageResolved(now));
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.GUARDIAN_TIMED_OUT,
                Optional.of(BillState.GUARDIAN_REVIEW),
                BillState.APPROVED,
                approved.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                current.votes(),
                nextBills,
                nextTransitions,
                current.citizenRoster(),
                nextStages,
                current.referendums()
        ));
        audit(actor, "guardian.timeout", "proposal", proposalId.canonicalKey(),
                "Guardian review of proposal " + proposalId
                        + " timed out and counts as approval");
        return new GuardianReceipt(
                approved,
                Optional.ofNullable(billOf(nextBills, proposalId)),
                true,
                now
        );
    }

    // ------------------------------------------------------------------
    // supreme-court review (state/deadline advancement only)
    // ------------------------------------------------------------------

    @Override
    public CourtReceipt submitForCourtReview(
            UUID actor,
            ProposalId proposalId,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireValidOnSite(context);
        requireActiveCitizen(actor);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        boolean legislationReview = proposal.kind() == ProposalKind.LEGISLATION
                && proposal.state() == BillState.APPROVED
                && (proposal.normLevel() == NormLevel.ORGANIC
                || proposal.normLevel() == NormLevel.CONSTITUTION_BASIC);
        boolean amendmentReview = proposal.kind() == ProposalKind.AMENDMENT
                && proposal.state() == BillState.PROPOSED;
        if (!legislationReview && !amendmentReview) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Only an APPROVED organic/basic law or a PROPOSED amendment "
                            + "enters the court review; " + proposalId + " is "
                            + proposal.state() + " (" + proposal.normLevel() + ")"
            );
        }
        long now = now();
        Proposal inCourt = proposal.withState(BillState.COURT_REVIEW);

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, inCourt);
        Map<BillId, Bill> nextBills = syncBill(
                current, proposalId, BillState.COURT_REVIEW
        );
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, stageFor(proposalId, current).withTimedStage(
                now,
                now + COURT_REVIEW_MILLIS,
                Optional.of(proposal.state()),
                Optional.empty()
        ));
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.COURT_REVIEW_STARTED,
                Optional.of(proposal.state()),
                BillState.COURT_REVIEW,
                inCourt.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                current.votes(),
                nextBills,
                nextTransitions,
                current.citizenRoster(),
                nextStages,
                current.referendums()
        ));
        audit(actor, "court.submit", "proposal", proposalId.canonicalKey(),
                "Submitted proposal " + proposalId + " to the supreme-court review");
        return new CourtReceipt(
                inCourt,
                Optional.ofNullable(billOf(nextBills, proposalId)),
                true,
                now
        );
    }

    @Override
    public CourtReceipt courtReviewPassed(
            UUID actor,
            ProposalId proposalId,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireValidOnSite(context);
        requireActiveCitizen(actor);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.state() != BillState.COURT_REVIEW) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only COURT_REVIEW proposals can pass review"
            );
        }
        ProposalStage stage = stageFor(proposalId, current);
        BillState enteredFrom = stage.courtReviewEnteredFrom().orElse(null);
        long now = now();
        BillState target;
        BillTransitionTrigger trigger = BillTransitionTrigger.COURT_REVIEW_PASSED;
        long deadline = 0;
        if (proposal.kind() == ProposalKind.AMENDMENT) {
            target = BillState.PARLIAMENT_VOTE;
        } else if (enteredFrom == BillState.GUARDIAN_REVIEW) {
            // Recusal replacement review: the court completes the guardian
            // review and the law takes effect directly.
            target = BillState.APPROVED;
        } else {
            target = BillState.GUARDIAN_REVIEW;
            deadline = proposal.normLevel() == NormLevel.CONSTITUTION_BASIC
                    ? now + GUARDIAN_REVIEW_BASIC_MILLIS
                    : now + GUARDIAN_REVIEW_ORDINARY_MILLIS;
        }
        Proposal advanced = proposal.withState(target);

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, advanced);
        Map<BillId, Bill> nextBills = syncBill(current, proposalId, target);
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        ProposalStage nextStage = target == BillState.GUARDIAN_REVIEW
                ? stage.withTimedStage(now, deadline, Optional.empty(), Optional.empty())
                : stage.withStageResolved(now);
        nextStages.put(proposalId, nextStage);
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                trigger,
                Optional.of(BillState.COURT_REVIEW),
                target,
                advanced.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                current.votes(),
                nextBills,
                nextTransitions,
                current.citizenRoster(),
                nextStages,
                current.referendums()
        ));
        audit(actor, "court.pass", "proposal", proposalId.canonicalKey(),
                "Supreme-court review passed for proposal " + proposalId);
        return new CourtReceipt(
                advanced,
                Optional.ofNullable(billOf(nextBills, proposalId)),
                true,
                now
        );
    }

    @Override
    public CourtReceipt courtReviewReturned(
            UUID actor,
            ProposalId proposalId,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireValidOnSite(context);
        requireActiveCitizen(actor);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.state() != BillState.COURT_REVIEW) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only COURT_REVIEW proposals can be returned"
            );
        }
        ProposalStage stage = stageFor(proposalId, current);
        long now = now();
        Proposal advanced;
        if (proposal.kind() == ProposalKind.AMENDMENT) {
            advanced = proposal.withState(BillState.REJECTED);
        } else if (stage.courtReviewEnteredFrom()
                .orElse(null) == BillState.GUARDIAN_REVIEW) {
            // A recusal replacement review that fails ends the law.
            advanced = proposal.withState(BillState.REJECTED);
        } else {
            advanced = proposal.withState(BillState.VOTING);
        }

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, advanced);
        Map<BillId, Bill> nextBills = syncBill(current, proposalId, advanced.state());
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        ProposalStage nextStage = advanced.state() == BillState.VOTING
                ? stage.withVotingOrigin(BillState.COURT_REVIEW, now)
                : stage.withStageResolved(now);
        nextStages.put(proposalId, nextStage);
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.COURT_REVIEW_RETURNED,
                Optional.of(BillState.COURT_REVIEW),
                advanced.state(),
                advanced.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                current.votes(),
                nextBills,
                nextTransitions,
                current.citizenRoster(),
                nextStages,
                current.referendums()
        ));
        audit(actor, "court.return", "proposal", proposalId.canonicalKey(),
                "Supreme-court review returned proposal " + proposalId
                        + " to " + advanced.state());
        return new CourtReceipt(
                advanced,
                Optional.ofNullable(billOf(nextBills, proposalId)),
                true,
                now
        );
    }

    @Override
    public CourtReceipt extendCourtReview(
            UUID actor,
            ProposalId proposalId,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireValidOnSite(context);
        requireActiveCitizen(actor);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.state() != BillState.COURT_REVIEW) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only a COURT_REVIEW proposal can be extended"
            );
        }
        ProposalStage stage = stageFor(proposalId, current);
        if (stage.courtExtensionCount() != 0) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_COURT_EXTENSION_LIMIT,
                    "Court review of " + proposalId + " is already extended once"
            );
        }
        long deadline = stage.stageDeadlineAt().orElseThrow(() -> unavailable(
                ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                "Proposal " + proposalId + " has no court-review deadline"
        ));
        long now = now();
        ProposalStage extended = stage.withCourtExtension(
                deadline + COURT_REVIEW_EXTENSION_MILLIS
        );
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, extended);

        // No state transition: only the persisted deadline changes.
        commit(current, snapshot(
                current,
                current.proposals(),
                current.votes(),
                current.bills(),
                current.transitions(),
                current.citizenRoster(),
                nextStages,
                current.referendums()
        ));
        audit(actor, "court.extend", "proposal", proposalId.canonicalKey(),
                "Extended the court review of proposal " + proposalId + " by 7 days");
        return new CourtReceipt(proposal, Optional.empty(), true, now);
    }

    // ------------------------------------------------------------------
    // override re-vote after a guardian return
    // ------------------------------------------------------------------

    @Override
    public VoteReceipt openOverrideVote(
            UUID actor,
            ProposalId proposalId,
            OnSiteContext context
    ) {
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
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.state() != BillState.GUARDIAN_RETURNED) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only a GUARDIAN_RETURNED proposal can open an "
                            + "override ballot"
            );
        }
        TreeSet<UUID> nextRoster = rosterWith(current, actor);
        if (nextRoster.isEmpty()) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ROSTER_UNAVAILABLE,
                    "No citizens are registered; an override ballot cannot be opened"
            );
        }
        long requiredThreshold = overrideThreshold(
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
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, stageFor(proposalId, current)
                .withVotingOrigin(BillState.GUARDIAN_RETURNED, now));
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.GUARDIAN_OVERRIDE_OPENED,
                Optional.of(BillState.GUARDIAN_RETURNED),
                BillState.VOTING,
                voting.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                nextVotes,
                current.bills(),
                nextTransitions,
                nextRoster,
                nextStages,
                current.referendums()
        ));
        audit(actor, "vote.open.override", "vote", voteId.canonicalKey(),
                "Opened override ballot " + voteId + " for proposal " + proposalId
                        + " (threshold " + requiredThreshold + " of "
                        + nextRoster.size() + ")");
        return new VoteReceipt(vote, true, now, Optional.empty());
    }

    @Override
    public VoteReceipt openCourtReVote(
            UUID actor,
            ProposalId proposalId,
            OnSiteContext context
    ) {
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
        Proposal proposal = requireProposal(current, proposalId);
        ProposalStage stage = current.stages().get(proposalId);
        boolean courtReturnedReVote = proposal.state() == BillState.VOTING
                && stage != null
                && stage.votingEnteredFrom().orElse(null) == BillState.COURT_REVIEW;
        if (!courtReturnedReVote) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only a court-returned VOTING proposal can open "
                            + "a re-vote ballot"
            );
        }
        TreeSet<UUID> nextRoster = rosterWith(current, actor);
        if (nextRoster.isEmpty()) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ROSTER_UNAVAILABLE,
                    "No citizens are registered; a re-vote ballot cannot be opened"
            );
        }
        long requiredThreshold = requiredThreshold(proposal.normLevel(), nextRoster.size());
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

        Map<VoteId, Vote> nextVotes = new LinkedHashMap<>(current.votes());
        nextVotes.put(voteId, vote);

        // The proposal stays in VOTING; no state transition.
        commit(current, snapshot(
                current,
                current.proposals(),
                nextVotes,
                current.bills(),
                current.transitions(),
                nextRoster,
                current.stages(),
                current.referendums()
        ));
        audit(actor, "vote.open.court.return", "vote", voteId.canonicalKey(),
                "Opened court-returned re-vote ballot " + voteId + " for proposal "
                        + proposalId + " (threshold " + requiredThreshold + " of "
                        + nextRoster.size() + ")");
        return new VoteReceipt(vote, true, now, Optional.empty());
    }

    // ------------------------------------------------------------------
    // amendment pipeline
    // ------------------------------------------------------------------

    @Override
    public ProposalReceipt submitAmendment(AmendmentDraft draft, OnSiteContext context) {
        Objects.requireNonNull(draft, "draft");
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
                NormLevel.CONSTITUTION_BASIC,
                ProposalKind.AMENDMENT,
                draft.fullText(),
                actor,
                BillState.DRAFT,
                now,
                1
        );
        Proposal proposed = created.withState(BillState.PROPOSED);

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, proposed);
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, new ProposalStage(
                ProposalStage.CURRENT_SCHEMA_VERSION,
                proposalId,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                1
        ));
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
                BillTransitionTrigger.AMENDMENT_PROPOSED,
                Optional.of(BillState.DRAFT),
                BillState.PROPOSED,
                proposed.recordRevision()
        ));

        TreeSet<UUID> nextRoster = rosterWith(current, actor);
        commit(current, new ParliamentStoreSnapshot(
                ParliamentStoreSnapshot.CURRENT_STORE_VERSION,
                current.storeRevision() + 1,
                nextProposals,
                current.votes(),
                current.bills(),
                nextTransitions,
                nextRoster,
                nextStages,
                current.referendums()
        ));
        audit(actor, "amendment.submit", "proposal", proposalId.canonicalKey(),
                "Submitted constitutional amendment " + proposalId
                        + " (" + draft.title() + ")");
        return new ProposalReceipt(proposed, now);
    }

    @Override
    public VoteReceipt openParliamentVote(
            UUID actor,
            ProposalId proposalId,
            OnSiteContext context
    ) {
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
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.kind() != ProposalKind.AMENDMENT
                || proposal.state() != BillState.PARLIAMENT_VOTE) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only a PARLIAMENT_VOTE amendment can open a "
                            + "parliament ballot"
            );
        }
        TreeSet<UUID> nextRoster = rosterWith(current, actor);
        if (nextRoster.isEmpty()) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ROSTER_UNAVAILABLE,
                    "No citizens are registered; a parliament ballot cannot be opened"
            );
        }
        long requiredThreshold = parliamentAmendmentThreshold(nextRoster.size());
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

        Map<VoteId, Vote> nextVotes = new LinkedHashMap<>(current.votes());
        nextVotes.put(voteId, vote);

        // The proposal stays in PARLIAMENT_VOTE; no state transition.
        commit(current, snapshot(
                current,
                current.proposals(),
                nextVotes,
                current.bills(),
                current.transitions(),
                nextRoster,
                current.stages(),
                current.referendums()
        ));
        audit(actor, "vote.open.parliament", "vote", voteId.canonicalKey(),
                "Opened parliament ballot " + voteId + " for amendment "
                        + proposalId + " (threshold " + requiredThreshold + " of "
                        + nextRoster.size() + ")");
        return new VoteReceipt(vote, true, now, Optional.empty());
    }

    @Override
    public BillReceipt closeParliamentVoteAndAdvance(
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
            return new BillReceipt(Optional.empty(), false, false, now());
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
        if (proposal == null || proposal.kind() != ProposalKind.AMENDMENT
                || proposal.state() != BillState.PARLIAMENT_VOTE) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + vote.proposalId()
                            + " is not a PARLIAMENT_VOTE amendment; the ballot "
                            + "cannot be closed"
            );
        }

        long now = now();
        Vote closed = vote.withClosed(now);
        boolean passed = vote.passed();
        Proposal advanced = proposal.withState(
                passed ? BillState.REFERENDUM_OPEN : BillState.REJECTED
        );

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposal.proposalId(), advanced);
        Map<VoteId, Vote> nextVotes = new LinkedHashMap<>(current.votes());
        nextVotes.put(voteId, closed);
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposal.proposalId(), stageFor(proposal.proposalId(), current)
                .withStageResolved(now));
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposal.proposalId(),
                actor,
                now,
                passed ? BillTransitionTrigger.PARLIAMENT_VOTE_PASSED
                        : BillTransitionTrigger.PARLIAMENT_VOTE_FAILED,
                Optional.of(BillState.PARLIAMENT_VOTE),
                advanced.state(),
                advanced.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                nextVotes,
                current.bills(),
                nextTransitions,
                current.citizenRoster(),
                nextStages,
                current.referendums()
        ));
        audit(actor, passed ? "vote.close.parliament.passed"
                        : "vote.close.parliament.failed",
                "vote", voteId.canonicalKey(),
                (passed ? "Passed" : "Rejected") + " parliament ballot " + voteId
                        + " for amendment " + proposal.proposalId());
        return new BillReceipt(Optional.empty(), passed, true, now);
    }

    @Override
    public GuardianReceipt publishAmendment(
            UUID actor,
            ProposalId proposalId,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireValidOnSite(context);
        requireActiveCitizen(actor);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.kind() != ProposalKind.AMENDMENT
                || proposal.state() != BillState.APPROVED) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only an APPROVED amendment can be published"
            );
        }
        long now = now();
        Proposal published = proposal.withState(BillState.PUBLISHED);

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, published);
        Map<BillId, Bill> nextBills = syncBill(
                current, proposalId, BillState.PUBLISHED
        );
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, stageFor(proposalId, current).withStageResolved(now));
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.PUBLISHED,
                Optional.of(BillState.APPROVED),
                BillState.PUBLISHED,
                published.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                current.votes(),
                nextBills,
                nextTransitions,
                current.citizenRoster(),
                nextStages,
                current.referendums()
        ));
        audit(actor, "amendment.publish", "proposal", proposalId.canonicalKey(),
                "Published constitutional amendment " + proposalId
                        + " (constitutional changes execute manually)");
        return new GuardianReceipt(
                published,
                Optional.ofNullable(billOf(nextBills, proposalId)),
                true,
                now
        );
    }

    // ------------------------------------------------------------------
    // referendum (FR-PAR-002-A §5)
    // ------------------------------------------------------------------

    @Override
    public ReferendumReceipt openReferendum(
            UUID actor,
            ProposalId proposalId,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireValidOnSite(context);
        requireActiveCitizen(actor);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.kind() != ProposalKind.AMENDMENT
                || proposal.state() != BillState.REFERENDUM_OPEN) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only a REFERENDUM_OPEN amendment can open a "
                            + "referendum"
            );
        }
        if (current.referendums().containsKey(proposalId)) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_INVALID_REQUEST,
                    "A referendum already exists for proposal " + proposalId
            );
        }
        TreeSet<UUID> nextRoster = rosterWith(current, actor);
        if (nextRoster.isEmpty()) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ROSTER_UNAVAILABLE,
                    "No citizens are registered; a referendum cannot be opened"
            );
        }
        long now = now();
        Referendum referendum = new Referendum(
                Referendum.CURRENT_SCHEMA_VERSION,
                proposalId,
                nextRoster.size(),
                nextRoster,
                0,
                0,
                0,
                now,
                Optional.empty(),
                1,
                Map.of()
        );
        Map<ProposalId, Referendum> nextReferendums =
                new LinkedHashMap<>(current.referendums());
        nextReferendums.put(proposalId, referendum);

        // The proposal stays in REFERENDUM_OPEN; the frozen roster is the
        // referendum's denominator and eligible-voter set.
        commit(current, snapshot(
                current,
                current.proposals(),
                current.votes(),
                current.bills(),
                current.transitions(),
                nextRoster,
                current.stages(),
                nextReferendums
        ));
        audit(actor, "referendum.open", "referendum", proposalId.canonicalKey(),
                "Opened referendum for amendment " + proposalId + " (roster "
                        + nextRoster.size() + ")");
        return new ReferendumReceipt(referendum, true, false, now);
    }

    @Override
    public ReferendumReceipt castReferendumVote(
            UUID voter,
            ProposalId proposalId,
            VoteChoice choice,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        Objects.requireNonNull(choice, "choice");
        // Referendum voting happens in the parliament public zone.
        requireValidOnSite(context, CapabilityClass.ONSITE_PUBLIC_SERVICE);
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
        Referendum referendum = current.referendums().get(proposalId);
        if (referendum == null) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_REFERENDUM_NOT_FOUND,
                    "No referendum exists for proposal " + proposalId
            );
        }
        if (referendum.closedAt().isPresent()) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_BALLOT_NOT_OPEN,
                    "Referendum for proposal " + proposalId + " is already closed"
            );
        }
        if (referendum.votes().containsKey(voter)) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ALREADY_VOTED,
                    "Citizen " + voter + " already voted on the referendum of "
                            + proposalId + " (one vote per citizen)"
            );
        }
        if (!referendum.frozenRoster().contains(voter)) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_NOT_CITIZEN,
                    "Citizen " + voter + " is not on the frozen roster of the "
                            + "referendum of " + proposalId
            );
        }

        long now = now();
        Referendum updated;
        try {
            updated = referendum.withCastVote(voter, choice, now);
        } catch (IllegalArgumentException failure) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_INVALID_REQUEST,
                    "Referendum vote rejected: " + failure.getMessage(),
                    failure
            );
        }
        TreeSet<UUID> nextRoster = rosterWith(current, voter);
        Map<ProposalId, Referendum> nextReferendums =
                new LinkedHashMap<>(current.referendums());
        nextReferendums.put(proposalId, updated);

        commit(current, snapshot(
                current,
                current.proposals(),
                current.votes(),
                current.bills(),
                current.transitions(),
                nextRoster,
                current.stages(),
                nextReferendums
        ));
        audit(voter, "referendum.cast", "referendum", proposalId.canonicalKey(),
                "Cast " + choice + " on the referendum of " + proposalId);
        return new ReferendumReceipt(updated, true, false, now);
    }

    @Override
    public ReferendumReceipt closeReferendum(
            UUID actor,
            ProposalId proposalId,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireValidOnSite(context);
        requireActiveCitizen(actor);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.state() != BillState.REFERENDUM_OPEN) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only a REFERENDUM_OPEN amendment can close its "
                            + "referendum"
            );
        }
        Referendum referendum = current.referendums().get(proposalId);
        if (referendum == null) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_REFERENDUM_NOT_FOUND,
                    "No referendum exists for proposal " + proposalId
            );
        }
        if (referendum.closedAt().isPresent()) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_BALLOT_NOT_OPEN,
                    "Referendum for proposal " + proposalId + " is already closed"
            );
        }

        long now = now();
        Referendum closed = referendum.withClosed(now);
        boolean passed = referendum.passed();
        BillState target = passed ? BillState.GUARDIAN_CONSENT : BillState.REJECTED;
        Proposal advanced = proposal.withState(target);

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, advanced);
        Map<ProposalId, Referendum> nextReferendums =
                new LinkedHashMap<>(current.referendums());
        nextReferendums.put(proposalId, closed);
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        ProposalStage stage = stageFor(proposalId, current);
        ProposalStage nextStage = passed
                ? stage.withTimedStage(
                        now,
                        now + CONSENT_MILLIS,
                        Optional.empty(),
                        Optional.empty())
                : stage.withStageResolved(now);
        nextStages.put(proposalId, nextStage);
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.REFERENDUM_CLOSED,
                Optional.of(BillState.REFERENDUM_OPEN),
                BillState.REFERENDUM_CLOSED,
                advanced.recordRevision()
        ));
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                passed ? BillTransitionTrigger.REFERENDUM_PASSED
                        : BillTransitionTrigger.REFERENDUM_FAILED,
                Optional.of(BillState.REFERENDUM_CLOSED),
                target,
                advanced.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                current.votes(),
                current.bills(),
                nextTransitions,
                current.citizenRoster(),
                nextStages,
                nextReferendums
        ));
        audit(actor, passed ? "referendum.close.passed" : "referendum.close.failed",
                "referendum", proposalId.canonicalKey(),
                (passed ? "Passed" : "Failed") + " referendum of " + proposalId
                        + " (participation " + closed.participated() + "/"
                        + closed.frozenRosterCount() + ", for "
                        + closed.votesFor() + "/" + closed.effectiveVotes() + ")");
        return new ReferendumReceipt(closed, true, passed, now);
    }

    // ------------------------------------------------------------------
    // constitutional consent (FR-PAR-002-A §3)
    // ------------------------------------------------------------------

    @Override
    public GuardianReceipt guardianConsentApprove(
            UUID actor,
            ProposalId proposalId,
            GuardianChannel channel,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireGuardian(channel, actor, context);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.kind() != ProposalKind.AMENDMENT
                || proposal.state() != BillState.GUARDIAN_CONSENT) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only a GUARDIAN_CONSENT amendment can be consented"
            );
        }
        long now = now();
        Proposal approved = proposal.withState(BillState.APPROVED);

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, approved);
        Map<BillId, Bill> nextBills = new LinkedHashMap<>(current.bills());
        BillId billId = BillId.of(idSource.nextUuid());
        Bill bill = new Bill(
                Bill.CURRENT_SCHEMA_VERSION,
                billId,
                proposalId,
                BillState.APPROVED,
                now,
                1
        );
        nextBills.put(billId, bill);
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, stageFor(proposalId, current).withStageResolved(now));
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.GUARDIAN_CONSENT_APPROVED,
                Optional.of(BillState.GUARDIAN_CONSENT),
                BillState.APPROVED,
                approved.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                current.votes(),
                nextBills,
                nextTransitions,
                current.citizenRoster(),
                nextStages,
                current.referendums()
        ));
        audit(actor, "consent.approve", "proposal", proposalId.canonicalKey(),
                "Guardian consented to constitutional amendment " + proposalId);
        return new GuardianReceipt(
                approved,
                Optional.of(bill),
                true,
                now
        );
    }

    @Override
    public GuardianReceipt guardianConsentReject(
            UUID actor,
            ProposalId proposalId,
            GuardianChannel channel,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireGuardian(channel, actor, context);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.kind() != ProposalKind.AMENDMENT
                || proposal.state() != BillState.GUARDIAN_CONSENT) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only a GUARDIAN_CONSENT amendment can be "
                            + "explicitly rejected"
            );
        }
        long now = now();
        Proposal rejected = proposal.withState(BillState.REJECTED);

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, rejected);
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, stageFor(proposalId, current).withStageResolved(now));
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.GUARDIAN_CONSENT_REJECTED,
                Optional.of(BillState.GUARDIAN_CONSENT),
                BillState.REJECTED,
                rejected.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                current.votes(),
                current.bills(),
                nextTransitions,
                current.citizenRoster(),
                nextStages,
                current.referendums()
        ));
        audit(actor, "consent.reject", "proposal", proposalId.canonicalKey(),
                "Guardian explicitly rejected constitutional amendment " + proposalId);
        return new GuardianReceipt(rejected, Optional.empty(), true, now);
    }

    @Override
    public GuardianReceipt guardianConsentTimeout(
            UUID actor,
            ProposalId proposalId,
            GuardianChannel channel,
            OnSiteContext context
    ) {
        Objects.requireNonNull(proposalId, "proposalId");
        requireGuardian(channel, actor, context);

        ParliamentStoreSnapshot current = repository.snapshot();
        Proposal proposal = requireProposal(current, proposalId);
        if (proposal.kind() != ProposalKind.AMENDMENT
                || proposal.state() != BillState.GUARDIAN_CONSENT) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                    "Proposal " + proposalId + " is in state " + proposal.state()
                            + "; only a GUARDIAN_CONSENT amendment can time out"
            );
        }
        long now = now();
        ProposalStage stage = stageFor(proposalId, current);
        long deadline = stage.stageDeadlineAt().orElseThrow(() -> unavailable(
                ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION,
                "Proposal " + proposalId + " has no consent deadline"
        ));
        if (now < deadline) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_DEADLINE_NOT_REACHED,
                    "Constitutional consent of " + proposalId + " expires at "
                            + deadline + "; current time " + now
            );
        }
        Proposal approved = proposal.withState(BillState.APPROVED);

        Map<ProposalId, Proposal> nextProposals =
                new LinkedHashMap<>(current.proposals());
        nextProposals.put(proposalId, approved);
        Map<BillId, Bill> nextBills = new LinkedHashMap<>(current.bills());
        BillId billId = BillId.of(idSource.nextUuid());
        Bill bill = new Bill(
                Bill.CURRENT_SCHEMA_VERSION,
                billId,
                proposalId,
                BillState.APPROVED,
                now,
                1
        );
        nextBills.put(billId, bill);
        Map<ProposalId, ProposalStage> nextStages =
                new LinkedHashMap<>(current.stages());
        nextStages.put(proposalId, stage.withStageResolved(now));
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                proposalId,
                actor,
                now,
                BillTransitionTrigger.GUARDIAN_CONSENT_TIMED_OUT,
                Optional.of(BillState.GUARDIAN_CONSENT),
                BillState.APPROVED,
                approved.recordRevision()
        ));

        commit(current, snapshot(
                current,
                nextProposals,
                current.votes(),
                nextBills,
                nextTransitions,
                current.citizenRoster(),
                nextStages,
                current.referendums()
        ));
        audit(actor, "consent.timeout", "proposal", proposalId.canonicalKey(),
                "Constitutional consent of " + proposalId
                        + " timed out and counts as granted");
        return new GuardianReceipt(
                approved,
                Optional.of(bill),
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
    public Optional<Referendum> referendum(ProposalId proposalId) {
        Objects.requireNonNull(proposalId, "proposalId");
        return repository.referendum(proposalId);
    }

    @Override
    public Optional<ProposalStage> stage(ProposalId proposalId) {
        Objects.requireNonNull(proposalId, "proposalId");
        return repository.stage(proposalId);
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
     * Final mutation boundary of every legislative duty (FR-PAR-001-A §2/§4):
     * the on-site context must revalidate as {@code ONSITE_OFFICIAL_DUTY} at
     * the authoritative position bound to the context. A null context and any
     * non-VALID outcome reject the mutation fail-closed with a stable code.
     */
    private void requireValidOnSite(OnSiteContext context) {
        requireValidOnSite(context, CapabilityClass.ONSITE_OFFICIAL_DUTY);
    }

    /**
     * Final mutation boundary with an explicit capability: official duty for
     * legislative operations, public service for referendum voting in the
     * parliament public zone (FR-PAR-002-A §5).
     */
    private void requireValidOnSite(OnSiteContext context, CapabilityClass capability) {
        ValidationResult result;
        if (context == null) {
            result = ValidationResult.invalid(ValidationResult.REASON_NOT_ISSUED);
        } else {
            result = institutionAccess.validateAtMutation(
                    context,
                    capability,
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
                    "On-site " + capability + " context is not valid: " + result.reason()
            );
        }
    }

    /**
     * Guardian authority boundary (FR-PAR-002 task §3.3): the Hydro Archon
     * player must be on-site in a parliament zone, or the actor must be the
     * fixed local-console actor (classified by the command layer with the
     * bootstrap classifier pattern). Everything else is rejected fail-closed
     * before any mutation.
     */
    private void requireGuardian(
            GuardianChannel channel,
            UUID actor,
            OnSiteContext context
    ) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(actor, "actor");
        switch (channel) {
            case HYDRO_ARCHON_PLAYER -> {
                requireValidOnSite(context);
                UUID onSiteActor = context.playerId();
                if (!onSiteActor.equals(actor)) {
                    throw unavailable(
                            ParliamentUnavailableException.CODE_ON_SITE_CONTEXT_INVALID,
                            "The on-site actor " + onSiteActor
                                    + " does not match the acting guardian " + actor
                    );
                }
                if (!guardians.isAvailable()) {
                    throw unavailable(
                            ParliamentUnavailableException.CODE_GUARDIAN_NOT_AUTHORIZED,
                            "Guardian identity services are not available"
                    );
                }
                if (!guardians.isHydroArchon(actor)) {
                    throw unavailable(
                            ParliamentUnavailableException.CODE_GUARDIAN_NOT_AUTHORIZED,
                            "Player " + actor + " is not the Hydro Archon"
                    );
                }
            }
            case LOCAL_CONSOLE -> {
                if (!actor.equals(CONSOLE_ACTOR)) {
                    throw unavailable(
                            ParliamentUnavailableException.CODE_GUARDIAN_NOT_AUTHORIZED,
                            "Only the real local Dedicated Server console may act "
                                    + "as the guardian console"
                    );
                }
            }
            default -> throw unavailable(
                    ParliamentUnavailableException.CODE_GUARDIAN_NOT_AUTHORIZED,
                    "Unsupported guardian channel: " + channel
            );
        }
    }

    private Proposal requireProposal(
            ParliamentStoreSnapshot snapshot,
            ProposalId proposalId
    ) {
        Proposal proposal = snapshot.proposals().get(proposalId);
        if (proposal == null) {
            throw unavailable(
                    ParliamentUnavailableException.CODE_PROPOSAL_NOT_FOUND,
                    "Proposal " + proposalId + " does not exist"
            );
        }
        return proposal;
    }

    private ProposalStage stageFor(ProposalId proposalId, ParliamentStoreSnapshot snapshot) {
        ProposalStage stage = snapshot.stages().get(proposalId);
        if (stage == null) {
            throw new IllegalStateException(
                    "Proposal " + proposalId + " has no stage metadata"
            );
        }
        return stage;
    }

    /** Bills of the given proposal updated to a target state (if one exists). */
    private Map<BillId, Bill> syncBill(
            ParliamentStoreSnapshot snapshot,
            ProposalId proposalId,
            BillState target
    ) {
        Map<BillId, Bill> nextBills = new LinkedHashMap<>(snapshot.bills());
        Bill existing = billOf(snapshot.bills(), proposalId);
        if (existing != null) {
            nextBills.put(existing.billId(), existing.withState(target));
        }
        return nextBills;
    }

    private Bill billOf(Map<BillId, Bill> bills, ProposalId proposalId) {
        return bills.values().stream()
                .filter(bill -> bill.proposalId().equals(proposalId))
                .findFirst()
                .orElse(null);
    }

    /** One complete replacement snapshot for the FR-CORE-002 durable gate. */
    private ParliamentStoreSnapshot snapshot(
            ParliamentStoreSnapshot current,
            Map<ProposalId, Proposal> proposals,
            Map<VoteId, Vote> votes,
            Map<BillId, Bill> bills,
            List<TransitionRecord> transitions,
            Set<UUID> citizenRoster,
            Map<ProposalId, ProposalStage> stages,
            Map<ProposalId, Referendum> referendums
    ) {
        return new ParliamentStoreSnapshot(
                ParliamentStoreSnapshot.CURRENT_STORE_VERSION,
                current.storeRevision() + 1,
                proposals,
                votes,
                bills,
                transitions,
                citizenRoster,
                stages,
                referendums
        );
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

    /**
     * Override re-vote threshold after a guardian return (FR-PAR-002-A §2):
     * ordinary/administrative laws 2/3, constitutional basic laws 4/5.
     */
    private long overrideThreshold(NormLevel level, long rosterSize) {
        return switch (level) {
            case CONSTITUTION_BASIC -> ceilFraction(rosterSize, 4, 5);
            case ORDINARY, ADMINISTRATIVE -> ceilFraction(rosterSize, 2, 3);
            case ORGANIC -> throw new IllegalStateException(
                    "Organic laws have no guardian veto and no override ballot"
            );
        };
    }

    /** Amendment parliament threshold: 4/5 of the frozen roster (FR-PAR-002-A §2). */
    private long parliamentAmendmentThreshold(long rosterSize) {
        return ceilFraction(rosterSize, 4, 5);
    }

    /** {@code ceil(size * numerator / denominator)} without floating point. */
    private long ceilFraction(long size, long numerator, long denominator) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        return (size * numerator + denominator - 1) / denominator;
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
