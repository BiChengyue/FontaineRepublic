package com.fontainerepublic.server.justice.api;

import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.justice.model.Case;
import com.fontainerepublic.server.justice.model.CaseId;
import com.fontainerepublic.server.justice.model.Evidence;
import com.fontainerepublic.server.justice.model.EvidenceId;
import com.fontainerepublic.server.justice.model.Verdict;
import com.fontainerepublic.server.justice.model.VerdictId;
import com.fontainerepublic.server.land.model.ViolationReport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative public API of the justice module (FR-JUS-001-A §4).
 *
 * <p>The service owns the judicial pipeline — cases, evidence, and verdicts —
 * and the closed state machine
 * {@code DRAFT -> FILED -> ADMITTED -> HEARING -> VERDICT_PENDING ->
 * VERDICTED / REJECTED} with the review path
 * {@code VERDICTED -> REVIEW_REQUESTED -> REVIEWED -> FINAL}. It exposes no
 * bulk enumeration API — reads are exact lookups or bounded, ordered
 * projections — no repositories, no NBT, and above all <b>no verdict
 * execution</b>: a verdict never mutates any other module's state, and no
 * rank, office, or judicial position ever maps to a technical permission.</p>
 *
 * <p>All mutations run on the logical server owner thread and publish only
 * after the FR-CORE-002 durable gate reports {@code COMMITTED}; each
 * authoritative mutation is one complete replacement snapshot that
 * increments the store revision exactly once and appends the transition
 * records (actor/time/trigger/before/after/revision) of every pipeline step
 * it performs.</p>
 *
 * <p>{@link #fileCase}, {@link #submitEvidence}, {@link #intakeViolationReport}
 * and {@link #requestReview} are {@code ONSITE_PUBLIC_SERVICE};
 * {@link #acceptCase}, {@link #advanceCase}, {@link #admitEvidence},
 * {@link #issueVerdict} and {@link #decideReview} are
 * {@code ONSITE_OFFICIAL_DUTY}: the final mutation boundary revalidates the
 * on-site context through
 * {@code InstitutionAccessService#validateAtMutation(...)} and rejects the
 * mutation fail-closed when the context is not VALID. Standing (an active
 * citizen with an authoritative PlayerData record) is rechecked through FR-CIT
 * at the same boundary. The verdict boundary additionally enforces evidence
 * admissibility: every submitted evidence must be ruled and at least one
 * admitted piece must support the ruling when evidence exists. The Land
 * violation-report intake is a bounded service — one case per report, no
 * cross-module NBT access.</p>
 */
public interface JusticeService {

    // ------------------------------------------------------------------
    // authoritative mutations (single snapshot, durable gate)
    // ------------------------------------------------------------------

    /**
     * Authoritative case filing ({@code ONSITE_PUBLIC_SERVICE}): one
     * complete replacement snapshot, store revision +1 exactly once,
     * published only after the durable gate commits. The filing party is the
     * validated on-site citizen; the case enters FILED via the FILED
     * transition (born DRAFT in the same committed snapshot). The draft's
     * {@code caseType} must be a non-LAND type (LAND cases are created only
     * through the intake).
     *
     * @throws com.fontainerepublic.server.justice.persistence.JusticeUnavailableException
     *         with a stable code when the draft is invalid, the on-site
     *         context is not VALID at the final mutation boundary, the actor
     *         is not a citizen, capacity is exhausted, or the durable store
     *         rejected the snapshot
     */
    CaseReceipt fileCase(CaseDraft draft, OnSiteContext context);

    /**
     * Authoritative bounded intake of a Land violation report
     * ({@code ONSITE_PUBLIC_SERVICE}): auto-filing from the read-only
     * {@link ViolationReport} value object created by FR-LAND — this module
     * never reads the Land NBT. One case per report (a duplicate intake is
     * rejected), the reporter is the filing party, the case is typed
     * {@code LAND}, and the case enters FILED like any filing. Bounded:
     * single report per call, bounded description, capacity enforced at the
     * repository boundary.
     *
     * @throws com.fontainerepublic.server.justice.persistence.JusticeUnavailableException
     *         with a stable code when the report is invalid, the report was
     *         already filed, the reporter is not a player holder, the on-site
     *         context is not VALID, the actor is not a citizen, capacity is
     *         exhausted, or the durable store rejected the snapshot
     */
    CaseReceipt intakeViolationReport(ViolationReport report, OnSiteContext context);

    /**
     * Authoritative case acceptance ({@code ONSITE_OFFICIAL_DUTY}): advances
     * FILED -> ADMITTED with the ADMITTED transition, store revision +1
     * exactly once, published only after the durable gate commits.
     *
     * @throws com.fontainerepublic.server.justice.persistence.JusticeUnavailableException
     *         with a stable code when the case does not exist or is not in
     *         FILED, the on-site context is not VALID, the actor is not a
     *         citizen, capacity is exhausted, or the durable store rejected
     *         the snapshot
     */
    CaseReceipt acceptCase(UUID actor, CaseId caseId, OnSiteContext context);

    /**
     * Authoritative court advancement ({@code ONSITE_OFFICIAL_DUTY}): moves a
     * case one legal step further in the pipeline — ADMITTED -> HEARING
     * (HEARING_STARTED) or HEARING -> VERDICT_PENDING (VERDICT_PENDING) —
     * store revision +1 exactly once, published only after the durable gate
     * commits. Every other source state is rejected (no illegal jumps).
     *
     * @throws com.fontainerepublic.server.justice.persistence.JusticeUnavailableException
     *         with a stable code when the case does not exist or is not in
     *         ADMITTED/HEARING, the on-site context is not VALID, the actor
     *         is not a citizen, capacity is exhausted, or the durable store
     *         rejected the snapshot
     */
    CaseReceipt advanceCase(UUID actor, CaseId caseId, OnSiteContext context);

    /**
     * Authoritative evidence submission ({@code ONSITE_PUBLIC_SERVICE}):
     * append-only per case — one complete replacement snapshot, evidence and
     * store revision +1 exactly once, published only after the durable gate
     * commits. The submitting party is the validated on-site citizen; the
     * evidence is born SUBMITTED.
     *
     * @throws com.fontainerepublic.server.justice.persistence.JusticeUnavailableException
     *         with a stable code when the draft is invalid, the case does not
     *         exist, the on-site context is not VALID, the actor is not a
     *         citizen, capacity is exhausted, or the durable store rejected
     *         the snapshot
     */
    EvidenceReceipt submitEvidence(EvidenceDraft draft, OnSiteContext context);

    /**
     * Authoritative admissibility ruling ({@code ONSITE_OFFICIAL_DUTY}):
     * SUBMITTED evidence is ruled ADMITTED or REJECTED (evidence must be
     * lawful, verifiable, relevant; FR-BL-004 §11). One complete replacement
     * snapshot, evidence and store revision +1 exactly once, published only
     * after the durable gate commits. Already-ruled evidence is rejected;
     * evidence is never removed after admission.
     *
     * @throws com.fontainerepublic.server.justice.persistence.JusticeUnavailableException
     *         with a stable code when the evidence does not exist or is not
     *         SUBMITTED, the on-site context is not VALID, the actor is not a
     *         citizen, capacity is exhausted, or the durable store rejected
     *         the snapshot
     */
    EvidenceReceipt admitEvidence(
            UUID actor,
            EvidenceId evidenceId,
            boolean admitted,
            OnSiteContext context
    );

    /**
     * Authoritative verdict issue ({@code ONSITE_OFFICIAL_DUTY}): a
     * VERDICT_PENDING case is adjudicated — GUILTY/NOT_GUILTY advances it to
     * VERDICTED, DISMISSED rejects it (REJECTED) — creating the immutable
     * binding verdict record in the same snapshot. The final mutation
     * boundary validates the on-site context, the judging citizen's standing,
     * and evidence admissibility: every submitted evidence of the case must
     * be ruled, and when evidence exists at least one piece must be ADMITTED
     * (no verdict may rest on unruled or rejected evidence alone). Store
     * revision +1 exactly once, published only after the durable gate
     * commits. Justice never executes the verdict.
     *
     * @throws com.fontainerepublic.server.justice.persistence.JusticeUnavailableException
     *         with a stable code when the draft is invalid, the case does not
     *         exist or is not in VERDICT_PENDING, evidence is not admissible,
     *         the on-site context is not VALID, the judge is not a citizen,
     *         capacity is exhausted, or the durable store rejected the
     *         snapshot
     */
    VerdictReceipt issueVerdict(
            UUID judge,
            CaseId caseId,
            VerdictDraft draft,
            OnSiteContext context
    );

    /**
     * Authoritative review request ({@code ONSITE_PUBLIC_SERVICE}): an
     * interested party requests the effective review of a VERDICTED case —
     * VERDICTED -> REVIEW_REQUESTED (REVIEW_REQUESTED transition), store
     * revision +1 exactly once, published only after the durable gate
     * commits.
     *
     * @throws com.fontainerepublic.server.justice.persistence.JusticeUnavailableException
     *         with a stable code when the case does not exist or is not in
     *         VERDICTED, the on-site context is not VALID, the actor is not a
     *         citizen, capacity is exhausted, or the durable store rejected
     *         the snapshot
     */
    CaseReceipt requestReview(UUID actor, CaseId caseId, OnSiteContext context);

    /**
     * Authoritative review decision ({@code ONSITE_OFFICIAL_DUTY}): completes
     * the effective review — REVIEW_REQUESTED -> REVIEWED (REVIEW_COMPLETED)
     * and REVIEWED -> FINAL (FINALIZED) in one committed snapshot, store
     * revision +1 exactly once, published only after the durable gate
     * commits. The review composition is a later revision; the record
     * documents the decision.
     *
     * @throws com.fontainerepublic.server.justice.persistence.JusticeUnavailableException
     *         with a stable code when the case does not exist or is not in
     *         REVIEW_REQUESTED, the on-site context is not VALID, the actor
     *         is not a citizen, capacity is exhausted, or the durable store
     *         rejected the snapshot
     */
    CaseReceipt decideReview(UUID actor, CaseId caseId, OnSiteContext context);

    // ------------------------------------------------------------------
    // exact reads (bounded; no enumeration API)
    // ------------------------------------------------------------------

    /** Exact lookup: the case with the given id, if any. */
    Optional<Case> caseById(CaseId caseId);

    /** Exact lookup: the evidence with the given id, if any. */
    Optional<Evidence> evidenceById(EvidenceId evidenceId);

    /** Exact lookup: the verdict with the given id, if any. */
    Optional<Verdict> verdictById(VerdictId verdictId);

    /**
     * Ordered, bounded projection of cases by ascending sequence, strictly
     * after {@code afterSeq}, at most {@code limit} entries (positive, capped
     * at {@value #MAX_PROJECTION_SIZE}). Never an unbounded enumeration.
     */
    List<CaseProjection> cases(long afterSeq, int limit);

    /**
     * Ordered, bounded projection of one case's evidence by ascending
     * per-case sequence, strictly after {@code afterSeq}, at most
     * {@code limit} entries (positive, capped at {@value #MAX_PROJECTION_SIZE}).
     * Never an unbounded enumeration.
     */
    List<com.fontainerepublic.server.justice.api.EvidenceProjection> evidenceFor(
            CaseId caseId,
            long afterSeq,
            int limit
    );

    /** Hard cap of every bounded projection. */
    int MAX_PROJECTION_SIZE = 128;
}
