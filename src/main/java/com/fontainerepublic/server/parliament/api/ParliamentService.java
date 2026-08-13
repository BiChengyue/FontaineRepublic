package com.fontainerepublic.server.parliament.api;

import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.parliament.model.Bill;
import com.fontainerepublic.server.parliament.model.BillId;
import com.fontainerepublic.server.parliament.model.GuardianChannel;
import com.fontainerepublic.server.parliament.model.NormLevel;
import com.fontainerepublic.server.parliament.model.ProposalId;
import com.fontainerepublic.server.parliament.model.ProposalStage;
import com.fontainerepublic.server.parliament.model.Referendum;
import com.fontainerepublic.server.parliament.model.VoteChoice;
import com.fontainerepublic.server.parliament.model.VoteId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative public API of the parliament module (FR-PAR-001-A §4).
 *
 * <p>The service owns the legislative pipeline — proposals, ballots, and the
 * resulting bills — and the legal state machine
 * {@code DRAFT -> REVIEW -> VOTING -> APPROVED -> PUBLISHED -> ACTIVE /
 * REJECTED} (SUSPENDED/INVALID/EXPIRED reserved for later revisions). It
 * exposes no bulk enumeration API — reads are exact lookups or bounded,
 * ordered projections — no repositories, no NBT, and above all <b>no legal
 * execution</b>: a passed bill never mutates any other module's state, and
 * no rank, office, or legislative position ever maps to a technical
 * permission.</p>
 *
 * <p>All mutations run on the logical server owner thread and publish only
 * after the FR-CORE-002 durable gate reports {@code COMMITTED}; each
 * authoritative mutation is one complete replacement snapshot that
 * increments the store revision exactly once.</p>
 *
 * <p>{@link #submitProposal}, {@link #openVote}, {@link #castVote} and
 * {@link #closeVoteAndAdvance} are {@code ONSITE_OFFICIAL_DUTY}: the final
 * mutation boundary revalidates the on-site context through
 * {@code InstitutionAccessService#validateAtMutation(context, ONSITE_OFFICIAL_DUTY, ...)}
 * and rejects the mutation fail-closed when the context is not VALID. The
 * acting citizen is taken from the validated context; citizenship is
 * rechecked through FR-CIT at the same boundary. Thresholds follow the
 * normative hierarchy (ordinary 1/2, organic 2/3, constitutional basic 3/4,
 * ceiling, frozen citizen roster as denominator) and one citizen casts
 * exactly one vote per ballot.</p>
 */
public interface ParliamentService {

    // ------------------------------------------------------------------
    // authoritative mutations (single snapshot, durable gate)
    // ------------------------------------------------------------------

    /**
     * Authoritative proposal submission ({@code ONSITE_OFFICIAL_DUTY}): one
     * complete replacement snapshot, store revision +1 exactly once,
     * published only after the durable gate commits. The proposer is the
     * validated on-site citizen; the proposal enters REVIEW via the
     * SUBMITTED transition. The citizen roster is updated (idempotently)
     * with the proposer in the same snapshot. Constitution-amendment
     * proposals ({@link NormLevel#CONSTITUTION_BASIC}) are rejected: the
     * amendment pipeline is a deferred revision.
     *
     * @throws com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException
     *         with a stable code when the draft is invalid, the on-site
     *         context is not VALID at the final mutation boundary, the actor
     *         is not a citizen, capacity is exhausted, or the durable store
     *         rejected the snapshot
     */
    ProposalReceipt submitProposal(ProposalDraft draft, OnSiteContext context);

    /**
     * Authoritative vote open ({@code ONSITE_OFFICIAL_DUTY}): freezes the
     * current citizen roster as the ballot denominator and eligible-voter
     * set, computes and persists the required threshold from the normative
     * level, advances the proposal REVIEW -> VOTING, and creates the OPEN
     * ballot — one complete replacement snapshot, store revision +1 exactly
     * once, published only after the durable gate commits.
     *
     * @throws com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException
     *         with a stable code when the proposal does not exist or is not
     *         in REVIEW, the on-site context is not VALID, the actor is not
     *         a citizen, no citizens are registered (no frozen roster),
     *         capacity is exhausted, or the durable store rejected the
     *         snapshot
     */
    VoteReceipt openVote(UUID actor, ProposalId proposalId, OnSiteContext context);

    /**
     * Authoritative vote cast ({@code ONSITE_OFFICIAL_DUTY}): one citizen,
     * one vote per ballot. The voter must be an active citizen and on the
     * ballot's frozen roster. One complete replacement snapshot, vote/record
     * and store revision +1 exactly once, published only after the durable
     * gate commits.
     *
     * @throws com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException
     *         with a stable code when the vote does not exist or is not
     *         OPEN, the voter already voted, the voter is not an active
     *         citizen or not on the frozen roster, the on-site context is
     *         not VALID, capacity is exhausted, or the durable store
     *         rejected the snapshot
     */
    VoteReceipt castVote(UUID voter, VoteId voteId, VoteChoice choice, OnSiteContext context);

    /**
     * Authoritative ballot close and state advance
     * ({@code ONSITE_OFFICIAL_DUTY}): computes the outcome against the
     * persisted frozen-roster threshold and advances VOTING -> APPROVED
     * (creating the bill at APPROVED) or VOTING -> REJECTED. One complete
     * replacement snapshot, store revision +1 exactly once, published only
     * after the durable gate commits. Closing an already-closed ballot is an
     * idempotent no-op ({@code applied == false}, nothing committed).
     *
     * @throws com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException
     *         with a stable code when the vote does not exist, the on-site
     *         context is not VALID, the actor is not a citizen, capacity is
     *         exhausted, or the durable store rejected the snapshot
     */
    BillReceipt closeVoteAndAdvance(UUID actor, VoteId voteId, OnSiteContext context);

    // ------------------------------------------------------------------
    // guardian review (FR-PAR-002-A §4)
    // ------------------------------------------------------------------

    /**
     * Authoritative submission of an APPROVED ordinary/administrative law
     * into the water-god guardian review ({@code ONSITE_OFFICIAL_DUTY}):
     * APPROVED -> GUARDIAN_REVIEW (72h deadline), one complete replacement
     * snapshot, store revision +1 exactly once, published only after the
     * durable gate commits. Organic and constitutional-basic laws enter the
     * guardian review automatically after their court review passes.
     */
    GuardianReceipt submitForGuardianReview(
            UUID actor,
            ProposalId proposalId,
            OnSiteContext context
    );

    /**
     * Authoritative guardian approval: GUARDIAN_REVIEW -> APPROVED
     * (GUARDIAN_APPROVED), the bill (if any) follows. The guardian is the
     * Hydro Archon player on-site in a parliament zone
     * ({@link GuardianChannel#HYDRO_ARCHON_PLAYER}) or the real local
     * Dedicated Server console ({@link GuardianChannel#LOCAL_CONSOLE});
     * anything else is rejected fail-closed.
     */
    GuardianReceipt guardianApprove(
            UUID actor,
            ProposalId proposalId,
            GuardianChannel channel,
            OnSiteContext context
    );

    /**
     * Authoritative guardian return (once): GUARDIAN_REVIEW ->
     * GUARDIAN_RETURNED with a bounded constitutional-basis or
     * procedural-error text. Organic laws cannot be returned (the guardian
     * has no veto over organic laws — {@code GUARDIAN_NO_VETO}).
     */
    GuardianReceipt guardianReturn(
            UUID actor,
            ProposalId proposalId,
            GuardianChannel channel,
            String basis,
            OnSiteContext context
    );

    /**
     * Authoritative guardian recusal: when the guardian has a conflict of
     * interest the supreme court completes the procedural/constitutionality
     * review instead — GUARDIAN_REVIEW -> COURT_REVIEW (GUARDIAN_RECUSED).
     */
    GuardianReceipt guardianRecuse(
            UUID actor,
            ProposalId proposalId,
            GuardianChannel channel,
            OnSiteContext context
    );

    /**
     * Authoritative guardian-timeout advancement: when the guardian-review
     * deadline (72h ordinary / 7d basic) has passed, the review counts as
     * approval — GUARDIAN_REVIEW -> APPROVED (GUARDIAN_TIMED_OUT). A review
     * that has not reached its deadline is rejected
     * ({@code DEADLINE_NOT_REACHED}).
     */
    GuardianReceipt guardianTimeoutAdvance(
            UUID actor,
            ProposalId proposalId,
            GuardianChannel channel,
            OnSiteContext context
    );

    // ------------------------------------------------------------------
    // supreme-court review (state/deadline advancement only; FR-PAR-002-A §3)
    // ------------------------------------------------------------------

    /**
     * Authoritative entry into the supreme-court constitutionality review
     * ({@code ONSITE_OFFICIAL_DUTY}): an APPROVED organic/basic law or a
     * PROPOSED amendment enters COURT_REVIEW with the 14-day deadline. The
     * adjudication itself is a later justice revision — this module only
     * records the stage and its deadlines.
     */
    CourtReceipt submitForCourtReview(
            UUID actor,
            ProposalId proposalId,
            OnSiteContext context
    );

    /**
     * Authoritative court-review pass: COURT_REVIEW -> PARLIAMENT_VOTE
     * (amendment), COURT_REVIEW -> GUARDIAN_REVIEW (organic/basic law), or
     * COURT_REVIEW -> APPROVED (recusal replacement review of an ordinary
     * law). One complete replacement snapshot.
     */
    CourtReceipt courtReviewPassed(UUID actor, ProposalId proposalId, OnSiteContext context);

    /**
     * Authoritative court-review return: COURT_REVIEW -> VOTING (organic/basic
     * law re-vote) or COURT_REVIEW -> REJECTED (amendment fails its
     * constitutionality review).
     */
    CourtReceipt courtReviewReturned(UUID actor, ProposalId proposalId, OnSiteContext context);

    /**
     * Authoritative court-review deadline extension: extends the active
     * 14-day court deadline by 7 days, exactly once. No state transition —
     * only the persisted stage deadline changes.
     */
    CourtReceipt extendCourtReview(UUID actor, ProposalId proposalId, OnSiteContext context);

    // ------------------------------------------------------------------
    // override re-vote after a guardian return (FR-PAR-002-A §3/§4)
    // ------------------------------------------------------------------

    /**
     * Authoritative override ballot open ({@code ONSITE_OFFICIAL_DUTY}):
     * GUARDIAN_RETURNED -> VOTING, freezing the citizen roster and computing
     * the override threshold (ordinary 2/3, basic 4/5). One complete
     * replacement snapshot, store revision +1 exactly once.
     */
    VoteReceipt openOverrideVote(UUID actor, ProposalId proposalId, OnSiteContext context);

    /**
     * Authoritative court-returned re-vote open ({@code ONSITE_OFFICIAL_DUTY}):
     * a law returned by the court review (COURT_REVIEW -> VOTING) opens a
     * fresh ballot with the ordinary normative threshold. The proposal stays
     * in VOTING; no state transition.
     */
    VoteReceipt openCourtReVote(UUID actor, ProposalId proposalId, OnSiteContext context);

    // ------------------------------------------------------------------
    // amendment pipeline (FR-PAR-002-A §3)
    // ------------------------------------------------------------------

    /**
     * Authoritative amendment proposal submission
     * ({@code ONSITE_OFFICIAL_DUTY}): enters the amendment pipeline at
     * PROPOSED (AMENDMENT_PROPOSED). The amendment always carries the
     * CONSTITUTION_BASIC norm level; the proposer is the validated on-site
     * citizen.
     */
    ProposalReceipt submitAmendment(AmendmentDraft draft, OnSiteContext context);

    /**
     * Authoritative amendment parliament-vote open ({@code ONSITE_OFFICIAL_DUTY}):
     * freezes the citizen roster and creates the parliament ballot with the
     * 4/5 threshold. The proposal stays in PARLIAMENT_VOTE; the ballot is
     * cast through {@link #castVote}.
     */
    VoteReceipt openParliamentVote(UUID actor, ProposalId proposalId, OnSiteContext context);

    /**
     * Authoritative amendment parliament-vote close and advance
     * ({@code ONSITE_OFFICIAL_DUTY}): PARLIAMENT_VOTE -> REFERENDUM_OPEN on
     * a passed 4/5 ballot, PARLIAMENT_VOTE -> REJECTED otherwise. No bill
     * exists yet (amendment bills are born at constitutional consent).
     */
    BillReceipt closeParliamentVoteAndAdvance(UUID actor, VoteId voteId, OnSiteContext context);

    /**
     * Authoritative amendment publication ({@code ONSITE_OFFICIAL_DUTY}):
     * APPROVED -> PUBLISHED at the end of the amendment pipeline (the
     * constitutional changes themselves are executed manually by the
     * corresponding modules — this module never executes laws).
     */
    GuardianReceipt publishAmendment(UUID actor, ProposalId proposalId, OnSiteContext context);

    // ------------------------------------------------------------------
    // referendum (FR-PAR-002-A §5)
    // ------------------------------------------------------------------

    /**
     * Authoritative referendum open ({@code ONSITE_OFFICIAL_DUTY}): freezes
     * the citizen roster as the referendum denominator and eligible-voter
     * set. The proposal must be in REFERENDUM_OPEN (a passed amendment
     * parliament vote).
     */
    ReferendumReceipt openReferendum(UUID actor, ProposalId proposalId, OnSiteContext context);

    /**
     * Authoritative referendum vote cast ({@code ONSITE_PUBLIC_SERVICE}): one
     * citizen, one vote per referendum, on the frozen roster, physically in
     * the parliament public zone. Abstention counts toward participation but
     * never toward the effective votes.
     */
    ReferendumReceipt castReferendumVote(
            UUID voter,
            ProposalId proposalId,
            VoteChoice choice,
            OnSiteContext context
    );

    /**
     * Authoritative referendum close ({@code ONSITE_OFFICIAL_DUTY}): tallies
     * the outcome — participation 2/3 of the frozen roster and approval 2/3
     * of the effective votes — and advances REFERENDUM_OPEN ->
     * GUARDIAN_CONSENT (passed) or REFERENDUM_OPEN -> REJECTED (failed).
     */
    ReferendumReceipt closeReferendum(UUID actor, ProposalId proposalId, OnSiteContext context);

    // ------------------------------------------------------------------
    // constitutional consent (FR-PAR-002-A §3)
    // ------------------------------------------------------------------

    /**
     * Authoritative amendment constitutional consent: GUARDIAN_CONSENT ->
     * APPROVED (GUARDIAN_CONSENT_APPROVED), creating the amendment bill at
     * APPROVED. The guardian is the Hydro Archon player on-site or the local
     * console (see {@link GuardianChannel}).
     */
    GuardianReceipt guardianConsentApprove(
            UUID actor,
            ProposalId proposalId,
            GuardianChannel channel,
            OnSiteContext context
    );

    /**
     * Authoritative explicit consent rejection: GUARDIAN_CONSENT -> REJECTED
     * (GUARDIAN_CONSENT_REJECTED) — an explicit guardian refusal fails the
     * amendment.
     */
    GuardianReceipt guardianConsentReject(
            UUID actor,
            ProposalId proposalId,
            GuardianChannel channel,
            OnSiteContext context
    );

    /**
     * Authoritative consent-timeout advancement: when the 7-day consent
     * deadline has passed, the consent counts as granted — GUARDIAN_CONSENT
     * -> APPROVED (GUARDIAN_CONSENT_TIMED_OUT), creating the amendment bill.
     */
    GuardianReceipt guardianConsentTimeout(
            UUID actor,
            ProposalId proposalId,
            GuardianChannel channel,
            OnSiteContext context
    );

    // ------------------------------------------------------------------
    // exact reads (bounded; no enumeration API)
    // ------------------------------------------------------------------

    /** Exact lookup: the bill with the given id, if any. */
    Optional<Bill> bill(BillId billId);

    /** Exact lookup: the referendum of an amendment proposal, if any. */
    Optional<Referendum> referendum(ProposalId proposalId);

    /** Exact lookup: the stage metadata of a proposal, if any. */
    Optional<ProposalStage> stage(ProposalId proposalId);

    /**
     * Ordered, bounded projection of proposals by ascending sequence,
     * strictly after {@code afterSeq}, at most {@code limit} entries
     * (positive, capped at {@value #MAX_PROJECTION_SIZE}). Never an
     * unbounded enumeration.
     */
    List<ProposalProjection> proposals(long afterSeq, int limit);

    /** Hard cap of every bounded projection. */
    int MAX_PROJECTION_SIZE = 128;
}
