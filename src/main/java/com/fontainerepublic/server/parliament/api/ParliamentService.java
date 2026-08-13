package com.fontainerepublic.server.parliament.api;

import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.parliament.model.Bill;
import com.fontainerepublic.server.parliament.model.BillId;
import com.fontainerepublic.server.parliament.model.NormLevel;
import com.fontainerepublic.server.parliament.model.ProposalId;
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
    // exact reads (bounded; no enumeration API)
    // ------------------------------------------------------------------

    /** Exact lookup: the bill with the given id, if any. */
    Optional<Bill> bill(BillId billId);

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
