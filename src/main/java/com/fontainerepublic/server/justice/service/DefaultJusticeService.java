package com.fontainerepublic.server.justice.service;

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
import com.fontainerepublic.server.justice.api.CaseDraft;
import com.fontainerepublic.server.justice.api.CaseProjection;
import com.fontainerepublic.server.justice.api.CaseReceipt;
import com.fontainerepublic.server.justice.api.EvidenceDraft;
import com.fontainerepublic.server.justice.api.EvidenceProjection;
import com.fontainerepublic.server.justice.api.EvidenceReceipt;
import com.fontainerepublic.server.justice.api.JusticeService;
import com.fontainerepublic.server.justice.api.VerdictDraft;
import com.fontainerepublic.server.justice.api.VerdictReceipt;
import com.fontainerepublic.server.justice.model.Case;
import com.fontainerepublic.server.justice.model.CaseId;
import com.fontainerepublic.server.justice.model.CaseState;
import com.fontainerepublic.server.justice.model.CaseTransitionTrigger;
import com.fontainerepublic.server.justice.model.CaseType;
import com.fontainerepublic.server.justice.model.CourtLevel;
import com.fontainerepublic.server.justice.model.Evidence;
import com.fontainerepublic.server.justice.model.EvidenceId;
import com.fontainerepublic.server.justice.model.EvidenceState;
import com.fontainerepublic.server.justice.model.TransitionRecord;
import com.fontainerepublic.server.justice.model.Verdict;
import com.fontainerepublic.server.justice.model.VerdictId;
import com.fontainerepublic.server.justice.model.VerdictOutcome;
import com.fontainerepublic.server.justice.persistence.JusticeIdSource;
import com.fontainerepublic.server.justice.persistence.JusticeNbtCodec;
import com.fontainerepublic.server.justice.persistence.JusticeRepository;
import com.fontainerepublic.server.justice.persistence.JusticeStoreSnapshot;
import com.fontainerepublic.server.justice.persistence.JusticeUnavailableException;
import com.fontainerepublic.server.land.model.ViolationReport;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.OwnerReferenceKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Runtime implementation of {@link JusticeService} (FR-JUS-001-A §4).
 *
 * <p>The service is a thin, owner-thread-scoped facade over the single-writer
 * {@link JusticeRepository}. It supplies the server clock and id source,
 * resolves actors through the PlayerData + FR-CIT chain (standing), and
 * enforces the final mutation boundary for every authoritative mutation
 * through {@link InstitutionAccessService#validateAtMutation} — a null or
 * non-VALID context rejects the mutation fail-closed. Public service
 * mutations validate as {@code ONSITE_PUBLIC_SERVICE}; official duties as
 * {@code ONSITE_OFFICIAL_DUTY}. Authoritative mutations publish only after
 * the durable gate commits and are then recorded through the audit service
 * (audit failure never blocks an already-committed mutation). Reads are exact
 * or bounded projections only.</p>
 *
 * <p>The pipeline is closed: every transition appends a record carrying
 * actor/time/trigger/before/after/revision, illegal jumps are rejected, and
 * the verdict boundary additionally enforces evidence admissibility (all
 * submitted evidence ruled, at least one admitted when evidence exists).
 * Justice never executes a verdict: no other module's state is ever touched,
 * and no rank/office ever maps to a technical permission. The Land intake
 * consumes only the read-only {@link ViolationReport} value object — never
 * the Land NBT — and files at most one case per report.</p>
 */
public final class DefaultJusticeService implements JusticeService {

    private final JusticeRepository repository;
    private final LongSupplier clock;
    private final JusticeIdSource idSource;
    private final JusticeCitizenDirectory citizens;
    private final InstitutionAccessService institutionAccess;
    private final AuditService auditService;

    public DefaultJusticeService(
            JusticeRepository repository,
            LongSupplier clock,
            JusticeIdSource idSource,
            JusticeCitizenDirectory citizens,
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
    public CaseReceipt fileCase(CaseDraft draft, OnSiteContext context) {
        Objects.requireNonNull(draft, "draft");
        if (draft.caseType() == CaseType.LAND) {
            throw unavailable(
                    JusticeUnavailableException.CODE_INVALID_REQUEST,
                    "LAND cases are created only through the violation-report intake"
            );
        }
        requireValidOnSite(context, CapabilityClass.ONSITE_PUBLIC_SERVICE);
        UUID actor = context.playerId();
        requireStanding(actor);

        JusticeStoreSnapshot current = repository.snapshot();
        CaseId caseId = CaseId.of(idSource.nextUuid());
        long seq = nextCaseSeq(current);
        long now = now();

        Case created = newCase(
                caseId,
                seq,
                draft.caseType(),
                draft.title(),
                draft.description(),
                actor,
                draft.defendantRef(),
                Optional.empty(),
                now
        );
        // Filing enters the pipeline: DRAFT -> FILED in the same committed
        // snapshot (two transition records, revision 1 then 2).
        Case filed = created.withState(CaseState.FILED);

        Map<CaseId, Case> nextCases = new LinkedHashMap<>(current.cases());
        nextCases.put(caseId, filed);
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                caseId,
                actor,
                now,
                CaseTransitionTrigger.FILED,
                Optional.empty(),
                CaseState.DRAFT,
                created.recordRevision()
        ));
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                caseId,
                actor,
                now,
                CaseTransitionTrigger.FILED,
                Optional.of(CaseState.DRAFT),
                CaseState.FILED,
                filed.recordRevision()
        ));

        commit(current, new JusticeStoreSnapshot(
                JusticeStoreSnapshot.CURRENT_STORE_VERSION,
                current.storeRevision() + 1,
                nextCases,
                current.evidence(),
                current.verdicts(),
                nextTransitions
        ));
        audit(
                actor,
                "case.file",
                "case",
                caseId.canonicalKey(),
                "Filed " + draft.caseType() + " case " + caseId
                        + " (" + draft.title() + ")"
        );
        return new CaseReceipt(filed, true, now);
    }

    @Override
    public CaseReceipt intakeViolationReport(
            ViolationReport report,
            OnSiteContext context
    ) {
        Objects.requireNonNull(report, "report");
        requireValidOnSite(context, CapabilityClass.ONSITE_PUBLIC_SERVICE);
        UUID actor = context.playerId();
        requireStanding(actor);

        OwnerReference reporter = report.reporter();
        if (reporter.kind() != OwnerReferenceKind.PLAYER_UUID) {
            throw unavailable(
                    JusticeUnavailableException.CODE_INVALID_REQUEST,
                    "Only a player reporter can file a case through intake; got "
                            + reporter.kind()
            );
        }
        UUID plaintiff = UUID.fromString(reporter.ownerId());

        JusticeStoreSnapshot current = repository.snapshot();
        boolean alreadyFiled = current.cases().values().stream()
                .anyMatch(aCase -> aCase.sourceReportId()
                        .filter(id -> id.equals(report.reportId()))
                        .isPresent());
        if (alreadyFiled) {
            throw unavailable(
                    JusticeUnavailableException.CODE_REPORT_ALREADY_FILED,
                    "Land violation report " + report.reportId()
                            + " was already filed (one case per report)"
            );
        }

        CaseId caseId = CaseId.of(idSource.nextUuid());
        long seq = nextCaseSeq(current);
        long now = now();
        Case created = newCase(
                caseId,
                seq,
                CaseType.LAND,
                "Land violation report #" + report.reportId(),
                report.description(),
                plaintiff,
                Optional.empty(),
                Optional.of(report.reportId()),
                now
        );
        Case filed = created.withState(CaseState.FILED);

        Map<CaseId, Case> nextCases = new LinkedHashMap<>(current.cases());
        nextCases.put(caseId, filed);
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                caseId,
                actor,
                now,
                CaseTransitionTrigger.FILED,
                Optional.empty(),
                CaseState.DRAFT,
                created.recordRevision()
        ));
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                caseId,
                actor,
                now,
                CaseTransitionTrigger.FILED,
                Optional.of(CaseState.DRAFT),
                CaseState.FILED,
                filed.recordRevision()
        ));

        commit(current, new JusticeStoreSnapshot(
                JusticeStoreSnapshot.CURRENT_STORE_VERSION,
                current.storeRevision() + 1,
                nextCases,
                current.evidence(),
                current.verdicts(),
                nextTransitions
        ));
        audit(
                actor,
                "case.intake",
                "case",
                caseId.canonicalKey(),
                "Filed LAND case " + caseId + " from violation report "
                        + report.reportId() + " (parcel " + report.parcelId() + ")"
        );
        return new CaseReceipt(filed, true, now);
    }

    @Override
    public CaseReceipt acceptCase(UUID actor, CaseId caseId, OnSiteContext context) {
        Objects.requireNonNull(caseId, "caseId");
        requireOfficial(context);
        UUID onSiteActor = requireMatchingOnSiteActor(actor, context);
        requireStanding(onSiteActor);

        JusticeStoreSnapshot current = repository.snapshot();
        Case aCase = requireCase(current, caseId);
        if (aCase.state() != CaseState.FILED) {
            throw illegalTransition(
                    "Only FILED cases can be accepted; " + caseId + " is in "
                            + aCase.state()
            );
        }
        return advance(current, aCase, onSiteActor, CaseState.ADMITTED,
                CaseTransitionTrigger.ADMITTED, "case.accept");
    }

    @Override
    public CaseReceipt advanceCase(UUID actor, CaseId caseId, OnSiteContext context) {
        Objects.requireNonNull(caseId, "caseId");
        requireOfficial(context);
        UUID onSiteActor = requireMatchingOnSiteActor(actor, context);
        requireStanding(onSiteActor);

        JusticeStoreSnapshot current = repository.snapshot();
        Case aCase = requireCase(current, caseId);
        if (aCase.state() == CaseState.ADMITTED) {
            return advance(current, aCase, onSiteActor, CaseState.HEARING,
                    CaseTransitionTrigger.HEARING_STARTED, "case.advance");
        }
        if (aCase.state() == CaseState.HEARING) {
            return advance(current, aCase, onSiteActor, CaseState.VERDICT_PENDING,
                    CaseTransitionTrigger.VERDICT_PENDING, "case.advance");
        }
        throw illegalTransition(
                "Only ADMITTED or HEARING cases can be advanced; " + caseId
                        + " is in " + aCase.state()
        );
    }

    @Override
    public EvidenceReceipt submitEvidence(EvidenceDraft draft, OnSiteContext context) {
        Objects.requireNonNull(draft, "draft");
        requireValidOnSite(context, CapabilityClass.ONSITE_PUBLIC_SERVICE);
        UUID actor = context.playerId();
        requireStanding(actor);

        JusticeStoreSnapshot current = repository.snapshot();
        requireCase(current, draft.caseId());

        EvidenceId evidenceId = EvidenceId.of(idSource.nextUuid());
        long seq = nextEvidenceSeq(current, draft.caseId());
        long now = now();
        Evidence created = new Evidence(
                Evidence.CURRENT_SCHEMA_VERSION,
                evidenceId,
                draft.caseId(),
                seq,
                actor,
                draft.description(),
                draft.integrityDigest(),
                EvidenceState.SUBMITTED,
                now,
                Optional.empty(),
                1
        );

        Map<EvidenceId, Evidence> nextEvidence =
                new LinkedHashMap<>(current.evidence());
        nextEvidence.put(evidenceId, created);

        commit(current, new JusticeStoreSnapshot(
                JusticeStoreSnapshot.CURRENT_STORE_VERSION,
                current.storeRevision() + 1,
                current.cases(),
                nextEvidence,
                current.verdicts(),
                current.transitions()
        ));
        audit(
                actor,
                "evidence.submit",
                "evidence",
                evidenceId.canonicalKey(),
                "Submitted evidence " + evidenceId + " for case "
                        + draft.caseId()
        );
        return new EvidenceReceipt(created, true, now);
    }

    @Override
    public EvidenceReceipt admitEvidence(
            UUID actor,
            EvidenceId evidenceId,
            boolean admitted,
            OnSiteContext context
    ) {
        Objects.requireNonNull(evidenceId, "evidenceId");
        requireOfficial(context);
        UUID onSiteActor = requireMatchingOnSiteActor(actor, context);
        requireStanding(onSiteActor);

        JusticeStoreSnapshot current = repository.snapshot();
        Evidence evidence = current.evidence().get(evidenceId);
        if (evidence == null) {
            throw unavailable(
                    JusticeUnavailableException.CODE_EVIDENCE_NOT_FOUND,
                    "Evidence " + evidenceId + " does not exist"
            );
        }
        if (evidence.state() == EvidenceState.ADMITTED) {
            throw unavailable(
                    JusticeUnavailableException.CODE_EVIDENCE_ALREADY_RULED,
                    "Evidence " + evidenceId + " is already admitted; "
                            + "admission is final (rejection is not)"
            );
        }
        EvidenceState target = admitted
                ? EvidenceState.ADMITTED
                : EvidenceState.REJECTED;
        Evidence ruled;
        try {
            ruled = evidence.withState(target, now());
        } catch (IllegalArgumentException failure) {
            throw unavailable(
                    JusticeUnavailableException.CODE_INVALID_REQUEST,
                    "Evidence ruling rejected: " + failure.getMessage(),
                    failure
            );
        }

        Map<EvidenceId, Evidence> nextEvidence =
                new LinkedHashMap<>(current.evidence());
        nextEvidence.put(evidenceId, ruled);

        commit(current, new JusticeStoreSnapshot(
                JusticeStoreSnapshot.CURRENT_STORE_VERSION,
                current.storeRevision() + 1,
                current.cases(),
                nextEvidence,
                current.verdicts(),
                current.transitions()
        ));
        audit(
                onSiteActor,
                admitted ? "evidence.admit" : "evidence.reject",
                "evidence",
                evidenceId.canonicalKey(),
                (admitted ? "Admitted" : "Rejected") + " evidence " + evidenceId
                        + " of case " + evidence.caseId()
        );
        return new EvidenceReceipt(ruled, true, now());
    }

    @Override
    public VerdictReceipt issueVerdict(
            UUID judge,
            CaseId caseId,
            VerdictDraft draft,
            OnSiteContext context
    ) {
        Objects.requireNonNull(caseId, "caseId");
        Objects.requireNonNull(draft, "draft");
        if (!caseId.equals(draft.caseId())) {
            throw unavailable(
                    JusticeUnavailableException.CODE_INVALID_REQUEST,
                    "The verdict draft case " + draft.caseId()
                            + " does not match the adjudicated case " + caseId
            );
        }
        requireOfficial(context);
        UUID onSiteJudge = requireMatchingOnSiteActor(judge, context);
        requireStanding(onSiteJudge);

        JusticeStoreSnapshot current = repository.snapshot();
        Case aCase = requireCase(current, caseId);
        if (aCase.state() != CaseState.VERDICT_PENDING) {
            throw illegalTransition(
                    "Only VERDICT_PENDING cases can be adjudicated; " + caseId
                            + " is in " + aCase.state()
            );
        }
        requireEvidenceAdmissible(current, caseId);

        long now = now();
        VerdictId verdictId = VerdictId.of(idSource.nextUuid());
        Verdict verdict = new Verdict(
                Verdict.CURRENT_SCHEMA_VERSION,
                verdictId,
                caseId,
                draft.outcome(),
                draft.reasoning(),
                onSiteJudge,
                aCase.courtLevel(),
                now,
                1
        );

        boolean dismissed = draft.outcome() == VerdictOutcome.DISMISSED;
        CaseState outcomeState = dismissed ? CaseState.REJECTED : CaseState.VERDICTED;
        Case advanced = aCase.withState(outcomeState);

        Map<CaseId, Case> nextCases = new LinkedHashMap<>(current.cases());
        nextCases.put(caseId, advanced);
        Map<VerdictId, Verdict> nextVerdicts =
                new LinkedHashMap<>(current.verdicts());
        nextVerdicts.put(verdictId, verdict);
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                caseId,
                onSiteJudge,
                now,
                dismissed ? CaseTransitionTrigger.CASE_REJECTED
                        : CaseTransitionTrigger.VERDICTED,
                Optional.of(CaseState.VERDICT_PENDING),
                outcomeState,
                advanced.recordRevision()
        ));

        commit(current, new JusticeStoreSnapshot(
                JusticeStoreSnapshot.CURRENT_STORE_VERSION,
                current.storeRevision() + 1,
                nextCases,
                current.evidence(),
                nextVerdicts,
                nextTransitions
        ));
        audit(
                onSiteJudge,
                "verdict.issue",
                "verdict",
                verdictId.canonicalKey(),
                "Issued " + draft.outcome() + " verdict " + verdictId
                        + " for case " + caseId + " (" + outcomeState + ")"
        );
        return new VerdictReceipt(verdict, outcomeState, true, now);
    }

    @Override
    public CaseReceipt requestReview(UUID actor, CaseId caseId, OnSiteContext context) {
        Objects.requireNonNull(caseId, "caseId");
        requireValidOnSite(context, CapabilityClass.ONSITE_PUBLIC_SERVICE);
        UUID onSiteActor = requireMatchingOnSiteActor(actor, context);
        requireStanding(onSiteActor);

        JusticeStoreSnapshot current = repository.snapshot();
        Case aCase = requireCase(current, caseId);
        if (aCase.state() != CaseState.VERDICTED) {
            throw illegalTransition(
                    "Only VERDICTED cases can request a review; " + caseId
                            + " is in " + aCase.state()
            );
        }
        return advance(current, aCase, onSiteActor, CaseState.REVIEW_REQUESTED,
                CaseTransitionTrigger.REVIEW_REQUESTED, "review.request");
    }

    @Override
    public CaseReceipt decideReview(UUID actor, CaseId caseId, OnSiteContext context) {
        Objects.requireNonNull(caseId, "caseId");
        requireOfficial(context);
        UUID onSiteActor = requireMatchingOnSiteActor(actor, context);
        requireStanding(onSiteActor);

        JusticeStoreSnapshot current = repository.snapshot();
        Case aCase = requireCase(current, caseId);
        if (aCase.state() != CaseState.REVIEW_REQUESTED) {
            throw illegalTransition(
                    "Only REVIEW_REQUESTED cases can complete the review; "
                            + caseId + " is in " + aCase.state()
            );
        }
        long now = now();
        Case reviewed = aCase.withState(CaseState.REVIEWED);
        Case finalized = reviewed.withState(CaseState.FINAL);

        Map<CaseId, Case> nextCases = new LinkedHashMap<>(current.cases());
        nextCases.put(caseId, finalized);
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                caseId,
                onSiteActor,
                now,
                CaseTransitionTrigger.REVIEW_COMPLETED,
                Optional.of(CaseState.REVIEW_REQUESTED),
                CaseState.REVIEWED,
                reviewed.recordRevision()
        ));
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                caseId,
                onSiteActor,
                now,
                CaseTransitionTrigger.FINALIZED,
                Optional.of(CaseState.REVIEWED),
                CaseState.FINAL,
                finalized.recordRevision()
        ));

        commit(current, new JusticeStoreSnapshot(
                JusticeStoreSnapshot.CURRENT_STORE_VERSION,
                current.storeRevision() + 1,
                nextCases,
                current.evidence(),
                current.verdicts(),
                nextTransitions
        ));
        audit(
                onSiteActor,
                "review.decide",
                "case",
                caseId.canonicalKey(),
                "Completed the review of case " + caseId + " (FINAL)"
        );
        return new CaseReceipt(finalized, true, now);
    }

    // ------------------------------------------------------------------
    // exact reads (bounded; no enumeration API)
    // ------------------------------------------------------------------

    @Override
    public Optional<Case> caseById(CaseId caseId) {
        Objects.requireNonNull(caseId, "caseId");
        return repository.caseById(caseId);
    }

    @Override
    public Optional<Evidence> evidenceById(EvidenceId evidenceId) {
        Objects.requireNonNull(evidenceId, "evidenceId");
        return repository.evidenceById(evidenceId);
    }

    @Override
    public Optional<Verdict> verdictById(VerdictId verdictId) {
        Objects.requireNonNull(verdictId, "verdictId");
        return repository.verdictById(verdictId);
    }

    @Override
    public List<CaseProjection> cases(long afterSeq, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        int bounded = Math.min(limit, MAX_PROJECTION_SIZE);
        return repository.casesAfter(afterSeq, bounded).stream()
                .map(CaseProjection::from)
                .toList();
    }

    @Override
    public List<EvidenceProjection> evidenceFor(CaseId caseId, long afterSeq, int limit) {
        Objects.requireNonNull(caseId, "caseId");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        int bounded = Math.min(limit, MAX_PROJECTION_SIZE);
        return repository.evidenceAfter(caseId, afterSeq, bounded).stream()
                .map(EvidenceProjection::from)
                .toList();
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    /** One-step pipeline advance with a single committed transition. */
    private CaseReceipt advance(
            JusticeStoreSnapshot current,
            Case aCase,
            UUID actor,
            CaseState target,
            CaseTransitionTrigger trigger,
            String auditAction
    ) {
        long now = now();
        Case advanced = aCase.withState(target);

        Map<CaseId, Case> nextCases = new LinkedHashMap<>(current.cases());
        nextCases.put(aCase.caseId(), advanced);
        List<TransitionRecord> nextTransitions =
                new ArrayList<>(current.transitions());
        nextTransitions.add(new TransitionRecord(
                TransitionRecord.CURRENT_SCHEMA_VERSION,
                aCase.caseId(),
                actor,
                now,
                trigger,
                Optional.of(aCase.state()),
                target,
                advanced.recordRevision()
        ));

        commit(current, new JusticeStoreSnapshot(
                JusticeStoreSnapshot.CURRENT_STORE_VERSION,
                current.storeRevision() + 1,
                nextCases,
                current.evidence(),
                current.verdicts(),
                nextTransitions
        ));
        audit(
                actor,
                auditAction,
                "case",
                aCase.caseId().canonicalKey(),
                "Case " + aCase.caseId() + " advanced " + aCase.state()
                        + " -> " + target
        );
        return new CaseReceipt(advanced, true, now);
    }

    /**
     * Evidence admissibility at the verdict boundary (FR-BL-004 §11): every
     * submitted evidence of the case must be ruled, and when evidence exists
     * at least one piece must be ADMITTED — no verdict may rest on unruled or
     * rejected evidence alone.
     */
    private void requireEvidenceAdmissible(
            JusticeStoreSnapshot current,
            CaseId caseId
    ) {
        List<Evidence> caseEvidence = current.evidence().values().stream()
                .filter(item -> item.caseId().equals(caseId))
                .toList();
        if (caseEvidence.isEmpty()) {
            return;
        }
        boolean anySubmitted = caseEvidence.stream()
                .anyMatch(item -> item.state() == EvidenceState.SUBMITTED);
        if (anySubmitted) {
            throw unavailable(
                    JusticeUnavailableException.CODE_EVIDENCE_NOT_ADMISSIBLE,
                    "Case " + caseId + " has unruled evidence; every submitted "
                            + "piece must be ruled before a verdict"
            );
        }
        boolean anyAdmitted = caseEvidence.stream()
                .anyMatch(item -> item.state() == EvidenceState.ADMITTED);
        if (!anyAdmitted) {
            throw unavailable(
                    JusticeUnavailableException.CODE_EVIDENCE_NOT_ADMISSIBLE,
                    "Case " + caseId + " has no admitted evidence; a verdict "
                            + "cannot rest on rejected evidence alone"
            );
        }
    }

    private Case newCase(
            CaseId caseId,
            long seq,
            CaseType caseType,
            String title,
            String description,
            UUID plaintiffRef,
            Optional<UUID> defendantRef,
            Optional<Long> sourceReportId,
            long now
    ) {
        try {
            return new Case(
                    Case.CURRENT_SCHEMA_VERSION,
                    caseId,
                    seq,
                    caseType,
                    title,
                    description,
                    plaintiffRef,
                    defendantRef,
                    sourceReportId,
                    CaseState.DRAFT,
                    CourtLevel.FIRST_INSTANCE,
                    now,
                    1
            );
        } catch (IllegalArgumentException failure) {
            throw unavailable(
                    JusticeUnavailableException.CODE_INVALID_REQUEST,
                    "Case draft rejected: " + failure.getMessage(),
                    failure
            );
        }
    }

    private long nextCaseSeq(JusticeStoreSnapshot snapshot) {
        return snapshot.cases().values().stream()
                .mapToLong(Case::caseSeq)
                .max()
                .orElse(0L) + 1;
    }

    private long nextEvidenceSeq(JusticeStoreSnapshot snapshot, CaseId caseId) {
        return snapshot.evidence().values().stream()
                .filter(item -> item.caseId().equals(caseId))
                .mapToLong(Evidence::evidenceSeq)
                .max()
                .orElse(0L) + 1;
    }

    private Case requireCase(JusticeStoreSnapshot snapshot, CaseId caseId) {
        Case aCase = snapshot.cases().get(caseId);
        if (aCase == null) {
            throw unavailable(
                    JusticeUnavailableException.CODE_CASE_NOT_FOUND,
                    "Case " + caseId + " does not exist"
            );
        }
        return aCase;
    }

    /**
     * Final mutation boundary of an official duty (FR-JUS-001-A §2/§4): the
     * on-site context must revalidate as {@code ONSITE_OFFICIAL_DUTY} at the
     * authoritative position bound to the context. A null context and any
     * non-VALID outcome reject the mutation fail-closed with a stable code.
     */
    private void requireOfficial(OnSiteContext context) {
        requireValidOnSite(context, CapabilityClass.ONSITE_OFFICIAL_DUTY);
    }

    private void requireValidOnSite(
            OnSiteContext context,
            CapabilityClass capability
    ) {
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
                    JusticeUnavailableException.CODE_ON_SITE_CONTEXT_INVALID,
                    "On-site " + capability + " context is not valid: "
                            + result.reason()
            );
        }
    }

    /** The actor on the validated context must be the acting citizen. */
    private UUID requireMatchingOnSiteActor(UUID actor, OnSiteContext context) {
        UUID onSiteActor = context.playerId();
        if (!onSiteActor.equals(actor)) {
            throw unavailable(
                    JusticeUnavailableException.CODE_ON_SITE_CONTEXT_INVALID,
                    "The on-site actor " + onSiteActor
                            + " does not match the acting citizen " + actor
            );
        }
        return onSiteActor;
    }

    /** Standing: an authoritative PlayerData record and active citizenship. */
    private void requireStanding(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!citizens.isAvailable()) {
            throw unavailable(
                    JusticeUnavailableException.CODE_CITIZEN_DIRECTORY_UNAVAILABLE,
                    "Player/citizen services are not available at the mutation boundary"
            );
        }
        if (!citizens.hasPlayerRecord(playerId)) {
            throw unavailable(
                    JusticeUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    "Player UUID has no authoritative PlayerData record: " + playerId
            );
        }
        if (!citizens.isActiveCitizen(playerId)) {
            throw unavailable(
                    JusticeUnavailableException.CODE_NOT_CITIZEN,
                    "Player " + playerId + " is not an active citizen"
            );
        }
    }

    private void commit(
            JusticeStoreSnapshot current,
            JusticeStoreSnapshot candidate
    ) {
        repository.commit(candidate);
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
                    AuditCategory.JUDICIAL,
                    "justice",
                    actionId,
                    Optional.of(targetType),
                    Optional.of(targetId),
                    AuditClassification.PUBLIC,
                    summary,
                    Optional.empty()
            );
            AuditReceipt receipt = auditService.recordAuthoritative(draft);
            if (!receipt.committed()) {
                LoggerFactory.getLogger(DefaultJusticeService.class).warn(
                        "[Justice] Audit of {} was not durably committed: {}",
                        actionId,
                        receipt.failureCode()
                );
            }
        } catch (RuntimeException failure) {
            // Audit failure never blocks an already-committed mutation (audit
            // is a record, not an authority).
            LoggerFactory.getLogger(DefaultJusticeService.class).warn(
                    "[Justice] Audit recording failed for {}: {}",
                    actionId,
                    failure.getMessage()
            );
        }
    }

    private JusticeUnavailableException unavailable(String code, String message) {
        return new JusticeUnavailableException(code, message);
    }

    private JusticeUnavailableException unavailable(
            String code,
            String message,
            Throwable cause
    ) {
        return new JusticeUnavailableException(code, message, cause);
    }

    private JusticeUnavailableException illegalTransition(String message) {
        return new JusticeUnavailableException(
                JusticeUnavailableException.CODE_ILLEGAL_TRANSITION,
                message
        );
    }

    private long now() {
        long value = clock.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("clock returned a negative timestamp");
        }
        return value;
    }
}
