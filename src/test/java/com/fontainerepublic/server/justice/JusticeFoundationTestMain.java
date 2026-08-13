package com.fontainerepublic.server.justice;

import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.core.ModSavedData;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.server.audit.AuditModule;
import com.fontainerepublic.server.citizen.CitizenModule;
import com.fontainerepublic.server.institutionaccess.InstitutionAccessModule;
import com.fontainerepublic.server.institutionaccess.api.FacilityReceipt;
import com.fontainerepublic.server.institutionaccess.api.FacilityRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.api.TerminalReceipt;
import com.fontainerepublic.server.institutionaccess.api.TerminalRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.api.ValidationResult;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.Facility;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.InstitutionType;
import com.fontainerepublic.server.institutionaccess.model.Terminal;
import com.fontainerepublic.server.institutionaccess.model.TerminalId;
import com.fontainerepublic.server.institutionaccess.model.WorkflowKind;
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
import com.fontainerepublic.server.justice.model.Evidence;
import com.fontainerepublic.server.justice.model.EvidenceId;
import com.fontainerepublic.server.justice.model.EvidenceState;
import com.fontainerepublic.server.justice.model.TransitionRecord;
import com.fontainerepublic.server.justice.model.Verdict;
import com.fontainerepublic.server.justice.model.VerdictId;
import com.fontainerepublic.server.justice.model.VerdictOutcome;
import com.fontainerepublic.server.justice.persistence.JusticeIdSource;
import com.fontainerepublic.server.justice.persistence.JusticeLimits;
import com.fontainerepublic.server.justice.persistence.JusticeNbtCodec;
import com.fontainerepublic.server.justice.persistence.JusticeNbtException;
import com.fontainerepublic.server.justice.persistence.JusticeRepository;
import com.fontainerepublic.server.justice.persistence.JusticeStore;
import com.fontainerepublic.server.justice.persistence.JusticeStoreSnapshot;
import com.fontainerepublic.server.justice.persistence.JusticeUnavailableException;
import com.fontainerepublic.server.justice.service.DefaultJusticeService;
import com.fontainerepublic.server.justice.service.JusticeCitizenDirectory;
import com.fontainerepublic.server.land.LandModule;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.land.model.ViolationReport;
import com.fontainerepublic.server.land.model.ViolationStatus;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.OwnerReferenceKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * Dependency-free validation entry point for FR-JUS-001 (justice module).
 * Exercises the FR-JUS-001-A §6 acceptance matrix with an injectable store
 * and SavedData-backed restart simulation: on-site-gated filing and evidence
 * submission (append-only), on-site official-duty acceptance/adjudication
 * with binding records, bounded Land violation-report intake (no cross-module
 * NBT), the closed judicial pipeline with transition records and no illegal
 * jumps, the effective review path, evidence admissibility at the verdict
 * boundary, judicial independence (no verdict execution, no other-module
 * direction), injection failure with no publish, restart recovery, strict
 * deterministic codec, bounded projections, and no bulk enumeration API.
 */
public final class JusticeFoundationTestMain {

    private static final String PROJECT_DIR_PROPERTY = "fontainerepublic.projectDir";
    private static final String DIMENSION = "minecraft:overworld";

    private static final UUID ALPHA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CHARLIE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID DELTA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000004");

    private JusticeFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testFilingOnSiteGated();
        testLandTypeOnlyThroughIntake();
        testEvidenceOnSiteGatedAppendOnly();
        testAcceptanceOfficialDuty();
        testPipelineAdvancement();
        testVerdictBindingRecord();
        testEvidenceAdmissibility();
        testLandIntakeBounded();
        testReviewPath();
        testNoIllegalJumps();
        testIndependenceNoExecution();
        testNoTechnicalPermissionMapping();
        testInjectionFailureNoPublish();
        testRestartPersistence();
        testStrictDeterministicCodec();
        testCorruptSnapshotFailClosed();
        testNoEnumerationApi();
        testBoundedProjections();
        testCapacityFailClosed();
        testModuleContract();
        System.out.println("[FR-JUS-001] Justice foundation validation passed");
    }

    // ------------------------------------------------------------------
    // acceptance: filing — on-site gated (§6)
    // ------------------------------------------------------------------

    private static void testFilingOnSiteGated() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        JusticeService service = service(
                repository(store), clock, citizens(ALPHA_ID), access
        );

        // No on-site context -> fail closed.
        JusticeUnavailableException noContext = expectThrows(
                JusticeUnavailableException.class,
                () -> service.fileCase(
                        new CaseDraft(CaseType.CIVIL, "T", "text", Optional.empty()),
                        null
                ),
                "filing without an on-site context must fail closed"
        );
        require(noContext.failureCode().equals(
                        JusticeUnavailableException.CODE_ON_SITE_CONTEXT_INVALID),
                "no-context filing reports ON_SITE_CONTEXT_INVALID");

        // Invalid on-site context -> fail closed.
        access.setResult(ValidationResult.invalid("EXPIRED"));
        JusticeUnavailableException expired = expectThrows(
                JusticeUnavailableException.class,
                () -> service.fileCase(
                        new CaseDraft(CaseType.CIVIL, "T", "text", Optional.empty()),
                        publicContext(ALPHA_ID, clock.getAsLong())
                ),
                "filing with an invalid on-site context must fail closed"
        );
        require(expired.failureCode().equals(
                        JusticeUnavailableException.CODE_ON_SITE_CONTEXT_INVALID),
                "invalid-context filing reports ON_SITE_CONTEXT_INVALID");
        access.setResult(ValidationResult.ok());

        // Non-citizen actor -> fail closed (player record but no citizenship).
        FakeJusticeCitizenDirectory strictCitizens = citizens(ALPHA_ID);
        strictCitizens.addNonCitizen(BRAVO_ID);
        JusticeService strictService = service(
                repository(store), clock, strictCitizens, access
        );
        strictService.fileCase(
                new CaseDraft(CaseType.CIVIL, "One", "body", Optional.empty()),
                publicContext(ALPHA_ID, clock.getAsLong())
        );
        JusticeUnavailableException notCitizen = expectThrows(
                JusticeUnavailableException.class,
                () -> strictService.fileCase(
                        new CaseDraft(CaseType.CIVIL, "Two", "body", Optional.empty()),
                        publicContext(BRAVO_ID, clock.getAsLong())
                ),
                "a non-citizen cannot file a case"
        );
        require(notCitizen.failureCode().equals(
                        JusticeUnavailableException.CODE_NOT_CITIZEN),
                "non-citizen filing reports NOT_CITIZEN");

        // Valid filing uses ONSITE_PUBLIC_SERVICE and commits one snapshot.
        JusticeRepository fresh = repository(new SavedDataBackedTestStore());
        FakeInstitutionAccessService freshAccess = new FakeInstitutionAccessService();
        JusticeService freshService = service(
                fresh, clock, citizens(ALPHA_ID), freshAccess
        );
        CaseReceipt receipt = freshService.fileCase(
                new CaseDraft(
                        CaseType.CIVIL, "First case", "Body text.",
                        Optional.of(BRAVO_ID)
                ),
                publicContext(ALPHA_ID, clock.getAsLong())
        );
        require(receipt.aCase().caseSeq() == 1L,
                "the first case has sequence 1");
        require(receipt.aCase().plaintiffRef().equals(ALPHA_ID),
                "the filing party is the validated on-site citizen");
        require(receipt.aCase().defendantRef().equals(Optional.of(BRAVO_ID)),
                "the draft defendant is preserved");
        require(receipt.aCase().state() == CaseState.FILED,
                "a filed case enters FILED");
        require(receipt.aCase().recordRevision() == 2L,
                "two transitions advance the case revision to 2");
        require(fresh.snapshot().storeRevision() == 1L,
                "filing commits exactly one store revision");
        require(freshAccess.lastCapability() == CapabilityClass.ONSITE_PUBLIC_SERVICE,
                "filing validates as ONSITE_PUBLIC_SERVICE");
        require(fresh.snapshot().transitions().size() == 2,
                "filing writes exactly two transition records");
        TransitionRecord first = fresh.snapshot().transitions().get(0);
        require(first.trigger() == CaseTransitionTrigger.FILED
                        && first.before().isEmpty()
                        && first.after() == CaseState.DRAFT,
                "first transition is FILED into DRAFT");
        TransitionRecord second = fresh.snapshot().transitions().get(1);
        require(second.trigger() == CaseTransitionTrigger.FILED
                        && second.before().equals(Optional.of(CaseState.DRAFT))
                        && second.after() == CaseState.FILED,
                "second transition is FILED DRAFT -> FILED");
        require(second.actor().equals(ALPHA_ID) && second.atMillis() == 1_000L,
                "every transition records actor and time");
    }

    private static void testLandTypeOnlyThroughIntake() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_100);
        JusticeService service = service(
                repository(store), clock, citizens(ALPHA_ID),
                new FakeInstitutionAccessService()
        );
        JusticeUnavailableException rejected = expectThrows(
                JusticeUnavailableException.class,
                () -> service.fileCase(
                        new CaseDraft(
                                CaseType.LAND, "T", "text", Optional.empty()
                        ),
                        publicContext(ALPHA_ID, clock.getAsLong())
                ),
                "LAND cases cannot be filed through the ordinary path"
        );
        require(rejected.failureCode().equals(
                        JusticeUnavailableException.CODE_INVALID_REQUEST),
                "LAND filing reports INVALID_REQUEST");
    }

    // ------------------------------------------------------------------
    // acceptance: evidence — on-site gated, append-only (§6)
    // ------------------------------------------------------------------

    private static void testEvidenceOnSiteGatedAppendOnly() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(2_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        JusticeService service = service(
                repository(store), clock, citizens(ALPHA_ID), access
        );
        CaseReceipt filed = service.fileCase(
                new CaseDraft(CaseType.CIVIL, "C", "body", Optional.empty()),
                publicContext(ALPHA_ID, clock.getAsLong())
        );
        CaseId caseId = filed.aCase().caseId();

        JusticeUnavailableException noContext = expectThrows(
                JusticeUnavailableException.class,
                () -> service.submitEvidence(
                        new EvidenceDraft(caseId, "d", Optional.empty()),
                        null
                ),
                "evidence submission without an on-site context must fail closed"
        );
        require(noContext.failureCode().equals(
                        JusticeUnavailableException.CODE_ON_SITE_CONTEXT_INVALID),
                "no-context evidence submission reports ON_SITE_CONTEXT_INVALID");

        // Unknown case -> fail closed.
        JusticeUnavailableException unknown = expectThrows(
                JusticeUnavailableException.class,
                () -> service.submitEvidence(
                        new EvidenceDraft(
                                CaseId.of(UUID.randomUUID()), "d", Optional.empty()
                        ),
                        publicContext(ALPHA_ID, clock.getAsLong())
                ),
                "evidence for an unknown case must fail closed"
        );
        require(unknown.failureCode().equals(
                        JusticeUnavailableException.CODE_CASE_NOT_FOUND),
                "unknown-case evidence reports CASE_NOT_FOUND");

        // Valid submissions: append-only, per-case sequence, public service.
        EvidenceReceipt first = service.submitEvidence(
                new EvidenceDraft(
                        caseId, "photo of the plot",
                        Optional.of("sha256:abc")
                ),
                publicContext(ALPHA_ID, clock.getAsLong())
        );
        require(first.evidence().state() == EvidenceState.SUBMITTED,
                "submitted evidence is born SUBMITTED");
        require(first.evidence().evidenceSeq() == 1L,
                "the first evidence of a case has sequence 1");
        require(first.evidence().submittedByRef().equals(ALPHA_ID),
                "the submitting party is the validated on-site citizen");
        require(access.lastCapability() == CapabilityClass.ONSITE_PUBLIC_SERVICE,
                "evidence submission validates as ONSITE_PUBLIC_SERVICE");
        EvidenceReceipt second = service.submitEvidence(
                new EvidenceDraft(caseId, "witness statement", Optional.empty()),
                publicContext(ALPHA_ID, clock.getAsLong())
        );
        require(second.evidence().evidenceSeq() == 2L,
                "the second evidence of a case has sequence 2");
        require(repository(store).snapshot().evidence().size() == 2,
                "evidence is append-only: both submissions are retained");
        require(repository(store).snapshot().storeRevision() == 3L,
                "two evidence submissions commit exactly two more revisions");
    }

    // ------------------------------------------------------------------
    // acceptance: acceptance — official duty, binding record (§6)
    // ------------------------------------------------------------------

    private static void testAcceptanceOfficialDuty() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(3_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        JusticeService service = service(
                repository(store), clock, citizens(ALPHA_ID), access
        );
        CaseReceipt filed = service.fileCase(
                new CaseDraft(CaseType.CIVIL, "C", "body", Optional.empty()),
                publicContext(ALPHA_ID, clock.getAsLong())
        );
        CaseId caseId = filed.aCase().caseId();

        JusticeUnavailableException noContext = expectThrows(
                JusticeUnavailableException.class,
                () -> service.acceptCase(ALPHA_ID, caseId, null),
                "acceptance without an on-site context must fail closed"
        );
        require(noContext.failureCode().equals(
                        JusticeUnavailableException.CODE_ON_SITE_CONTEXT_INVALID),
                "no-context acceptance reports ON_SITE_CONTEXT_INVALID");

        // Actor must match the validated on-site actor.
        JusticeUnavailableException mismatch = expectThrows(
                JusticeUnavailableException.class,
                () -> service.acceptCase(
                        BRAVO_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())
                ),
                "acceptance by a mismatched actor must fail closed"
        );
        require(mismatch.failureCode().equals(
                        JusticeUnavailableException.CODE_ON_SITE_CONTEXT_INVALID),
                "mismatched acceptance reports ON_SITE_CONTEXT_INVALID");

        CaseReceipt accepted = service.acceptCase(
                ALPHA_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())
        );
        require(accepted.aCase().state() == CaseState.ADMITTED,
                "acceptance advances FILED -> ADMITTED");
        require(access.lastCapability() == CapabilityClass.ONSITE_OFFICIAL_DUTY,
                "acceptance validates as ONSITE_OFFICIAL_DUTY");
        List<TransitionRecord> transitions =
                repository(store).snapshot().transitions();
        TransitionRecord acceptRecord = transitions.get(transitions.size() - 1);
        require(acceptRecord.trigger() == CaseTransitionTrigger.ADMITTED
                        && acceptRecord.before().equals(Optional.of(CaseState.FILED))
                        && acceptRecord.after() == CaseState.ADMITTED
                        && acceptRecord.actor().equals(ALPHA_ID),
                "acceptance appends the ADMITTED binding record");
    }

    // ------------------------------------------------------------------
    // acceptance: pipeline advancement (§5/§6)
    // ------------------------------------------------------------------

    private static void testPipelineAdvancement() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(4_000);
        JusticeService service = service(
                repository(store), clock, citizens(ALPHA_ID),
                new FakeInstitutionAccessService()
        );
        CaseId caseId = service.fileCase(
                new CaseDraft(CaseType.CIVIL, "C", "body", Optional.empty()),
                publicContext(ALPHA_ID, clock.getAsLong())
        ).aCase().caseId();

        // Illegal jump: a FILED case cannot be advanced to a hearing.
        JusticeUnavailableException fromFiled = expectThrows(
                JusticeUnavailableException.class,
                () -> service.advanceCase(
                        ALPHA_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())
                ),
                "advancing a FILED case must be rejected"
        );
        require(fromFiled.failureCode().equals(
                        JusticeUnavailableException.CODE_ILLEGAL_TRANSITION),
                "FILED advancement reports ILLEGAL_TRANSITION");

        service.acceptCase(
                ALPHA_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())
        );
        CaseReceipt hearing = service.advanceCase(
                ALPHA_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())
        );
        require(hearing.aCase().state() == CaseState.HEARING,
                "advance moves ADMITTED -> HEARING");
        CaseReceipt pending = service.advanceCase(
                ALPHA_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())
        );
        require(pending.aCase().state() == CaseState.VERDICT_PENDING,
                "advance moves HEARING -> VERDICT_PENDING");

        List<TransitionRecord> transitions =
                repository(store).snapshot().transitions();
        TransitionRecord hearingRecord = transitions.get(transitions.size() - 2);
        require(hearingRecord.trigger() == CaseTransitionTrigger.HEARING_STARTED
                        && hearingRecord.before().equals(Optional.of(CaseState.ADMITTED))
                        && hearingRecord.after() == CaseState.HEARING,
                "HEARING_STARTED transition carries before/after");
        TransitionRecord pendingRecord = transitions.get(transitions.size() - 1);
        require(pendingRecord.trigger() == CaseTransitionTrigger.VERDICT_PENDING
                        && pendingRecord.before().equals(Optional.of(CaseState.HEARING))
                        && pendingRecord.after() == CaseState.VERDICT_PENDING,
                "VERDICT_PENDING transition carries before/after");

        // A VERDICT_PENDING case cannot be advanced further.
        JusticeUnavailableException beyond = expectThrows(
                JusticeUnavailableException.class,
                () -> service.advanceCase(
                        ALPHA_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())
                ),
                "advancing a VERDICT_PENDING case must be rejected"
        );
        require(beyond.failureCode().equals(
                        JusticeUnavailableException.CODE_ILLEGAL_TRANSITION),
                "VERDICT_PENDING advancement reports ILLEGAL_TRANSITION");
    }

    // ------------------------------------------------------------------
    // acceptance: verdict — binding immutable record (§6)
    // ------------------------------------------------------------------

    private static void testVerdictBindingRecord() throws Exception {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(5_000);
        JusticeService service = service(
                repository(store), clock, citizens(ALPHA_ID),
                new FakeInstitutionAccessService()
        );
        CaseId caseId = verdictPendingCase(service, clock);

        JusticeUnavailableException noContext = expectThrows(
                JusticeUnavailableException.class,
                () -> service.issueVerdict(
                        ALPHA_ID,
                        caseId,
                        new VerdictDraft(caseId, VerdictOutcome.GUILTY, "reason"),
                        null
                ),
                "verdict issue without an on-site context must fail closed"
        );
        require(noContext.failureCode().equals(
                        JusticeUnavailableException.CODE_ON_SITE_CONTEXT_INVALID),
                "no-context verdict issue reports ON_SITE_CONTEXT_INVALID");

        VerdictReceipt receipt = service.issueVerdict(
                ALPHA_ID,
                caseId,
                new VerdictDraft(caseId, VerdictOutcome.GUILTY, "evidence weighs"),
                officialContext(ALPHA_ID, clock.getAsLong())
        );
        require(receipt.applied() && receipt.outcomeState() == CaseState.VERDICTED,
                "a guilty verdict advances the case to VERDICTED");
        Verdict verdict = receipt.verdict();
        require(verdict.caseId().equals(caseId)
                        && verdict.outcome() == VerdictOutcome.GUILTY
                        && verdict.reasoning().equals("evidence weighs")
                        && verdict.judgeRef().equals(ALPHA_ID),
                "the verdict record binds case/outcome/reasoning/judge");
        require(verdict.recordRevision() == 1L,
                "a fresh verdict has revision 1");
        require(service.verdictById(verdict.verdictId()).isPresent(),
                "the verdict is readable by exact lookup");
        Case adjudicated = service.caseById(caseId).orElseThrow();
        require(adjudicated.state() == CaseState.VERDICTED,
                "the case is VERDICTED after issue");
        List<TransitionRecord> transitions =
                repository(store).snapshot().transitions();
        TransitionRecord verdictRecord = transitions.get(transitions.size() - 1);
        require(verdictRecord.trigger() == CaseTransitionTrigger.VERDICTED
                        && verdictRecord.before().equals(
                                Optional.of(CaseState.VERDICT_PENDING))
                        && verdictRecord.after() == CaseState.VERDICTED,
                "the VERDICTED transition is recorded");

        // Dismissal rejects the case.
        CaseId dismissedId = verdictPendingCase(service, clock);
        VerdictReceipt dismissed = service.issueVerdict(
                ALPHA_ID,
                dismissedId,
                new VerdictDraft(dismissedId, VerdictOutcome.DISMISSED, "no merit"),
                officialContext(ALPHA_ID, clock.getAsLong())
        );
        require(dismissed.outcomeState() == CaseState.REJECTED,
                "a dismissal rejects the case");
        require(service.caseById(dismissedId).orElseThrow().state()
                        == CaseState.REJECTED,
                "the dismissed case is REJECTED");

        // A verdict is immutable: no state-replacement method exists.
        for (Method method : Verdict.class.getDeclaredMethods()) {
            require(!method.getName().startsWith("with"),
                    "verdict records are immutable (no with* method): "
                            + method.getName());
        }
    }

    // ------------------------------------------------------------------
    // acceptance: evidence admissibility at the verdict boundary (§2/§6)
    // ------------------------------------------------------------------

    private static void testEvidenceAdmissibility() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(6_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        JusticeService service = service(
                repository(store), clock, citizens(ALPHA_ID), access
        );
        CaseId caseId = verdictPendingCase(service, clock);
        EvidenceReceipt submitted = service.submitEvidence(
                new EvidenceDraft(caseId, "photo", Optional.empty()),
                publicContext(ALPHA_ID, clock.getAsLong())
        );

        // Unruled evidence blocks the verdict.
        JusticeUnavailableException unruled = expectThrows(
                JusticeUnavailableException.class,
                () -> service.issueVerdict(
                        ALPHA_ID,
                        caseId,
                        new VerdictDraft(caseId, VerdictOutcome.GUILTY, "r"),
                        officialContext(ALPHA_ID, clock.getAsLong())
                ),
                "a verdict must not rest on unruled evidence"
        );
        require(unruled.failureCode().equals(
                        JusticeUnavailableException.CODE_EVIDENCE_NOT_ADMISSIBLE),
                "unruled evidence reports EVIDENCE_NOT_ADMISSIBLE");

        // Rejecting the only evidence still blocks the verdict.
        EvidenceReceipt rejected = service.admitEvidence(
                ALPHA_ID, submitted.evidence().evidenceId(), false,
                officialContext(ALPHA_ID, clock.getAsLong())
        );
        require(rejected.evidence().state() == EvidenceState.REJECTED
                        && rejected.evidence().recordRevision() == 2L,
                "a ruling advances the evidence revision exactly once");
        require(rejected.evidence().admittedAt().isPresent(),
                "a ruling stamps the ruling time");
        require(access.lastCapability() == CapabilityClass.ONSITE_OFFICIAL_DUTY,
                "an admissibility ruling validates as ONSITE_OFFICIAL_DUTY");
        JusticeUnavailableException rejectedOnly = expectThrows(
                JusticeUnavailableException.class,
                () -> service.issueVerdict(
                        ALPHA_ID,
                        caseId,
                        new VerdictDraft(caseId, VerdictOutcome.GUILTY, "r"),
                        officialContext(ALPHA_ID, clock.getAsLong())
                ),
                "a verdict must not rest on rejected evidence alone"
        );
        require(rejectedOnly.failureCode().equals(
                        JusticeUnavailableException.CODE_EVIDENCE_NOT_ADMISSIBLE),
                "rejected-only evidence reports EVIDENCE_NOT_ADMISSIBLE");

        // Admitted evidence enables the verdict; a second ruling is final.
        EvidenceReceipt admitted = service.admitEvidence(
                ALPHA_ID, submitted.evidence().evidenceId(), true,
                officialContext(ALPHA_ID, clock.getAsLong())
        );
        require(admitted.evidence().state() == EvidenceState.ADMITTED,
                "a ruling may admit previously rejected evidence");
        JusticeUnavailableException rerule = expectThrows(
                JusticeUnavailableException.class,
                () -> service.admitEvidence(
                        ALPHA_ID, submitted.evidence().evidenceId(), false,
                        officialContext(ALPHA_ID, clock.getAsLong())
                ),
                "an already-ruled piece cannot be ruled again"
        );
        require(rerule.failureCode().equals(
                        JusticeUnavailableException.CODE_EVIDENCE_ALREADY_RULED),
                "a second ruling reports EVIDENCE_ALREADY_RULED");
        VerdictReceipt verdict = service.issueVerdict(
                ALPHA_ID,
                caseId,
                new VerdictDraft(caseId, VerdictOutcome.GUILTY, "r"),
                officialContext(ALPHA_ID, clock.getAsLong())
        );
        require(verdict.applied(),
                "admitted evidence enables the verdict");
    }

    // ------------------------------------------------------------------
    // acceptance: land violation-report intake — bounded (§6)
    // ------------------------------------------------------------------

    private static void testLandIntakeBounded() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(7_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        JusticeService service = service(
                repository(store), clock, citizens(ALPHA_ID), access
        );
        ViolationReport report = violationReport(1L, ALPHA_ID);

        CaseReceipt intake = service.intakeViolationReport(
                report, publicContext(ALPHA_ID, clock.getAsLong())
        );
        require(intake.aCase().caseType() == CaseType.LAND,
                "intake files a LAND case");
        require(intake.aCase().state() == CaseState.FILED,
                "an intake case enters FILED like any filing");
        require(intake.aCase().plaintiffRef().equals(ALPHA_ID),
                "the reporter is the filing party");
        require(intake.aCase().sourceReportId().equals(Optional.of(1L)),
                "the case is bound to its source report");
        require(intake.aCase().title().contains("1"),
                "the intake title references the report");
        require(access.lastCapability() == CapabilityClass.ONSITE_PUBLIC_SERVICE,
                "intake validates as ONSITE_PUBLIC_SERVICE");

        // One case per report.
        JusticeUnavailableException duplicate = expectThrows(
                JusticeUnavailableException.class,
                () -> service.intakeViolationReport(
                        report, publicContext(ALPHA_ID, clock.getAsLong())
                ),
                "a report cannot be filed twice"
        );
        require(duplicate.failureCode().equals(
                        JusticeUnavailableException.CODE_REPORT_ALREADY_FILED),
                "duplicate intake reports REPORT_ALREADY_FILED");

        // A non-player reporter cannot file through intake.
        ViolationReport officeReport = violationReport(
                2L, OwnerReference.HYDRO_ARCHON_OFFICE
        );
        JusticeUnavailableException notPlayer = expectThrows(
                JusticeUnavailableException.class,
                () -> service.intakeViolationReport(
                        officeReport, publicContext(ALPHA_ID, clock.getAsLong())
                ),
                "an office reporter cannot file through intake"
        );
        require(notPlayer.failureCode().equals(
                        JusticeUnavailableException.CODE_INVALID_REQUEST),
                "non-player intake reports INVALID_REQUEST");
    }

    // ------------------------------------------------------------------
    // acceptance: review — effective review path (§2/§5/§6)
    // ------------------------------------------------------------------

    private static void testReviewPath() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(8_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        JusticeService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access
        );
        CaseId caseId = verdictPendingCase(service, clock);
        service.issueVerdict(
                ALPHA_ID,
                caseId,
                new VerdictDraft(caseId, VerdictOutcome.GUILTY, "r"),
                officialContext(ALPHA_ID, clock.getAsLong())
        );

        CaseReceipt requested = service.requestReview(
                BRAVO_ID, caseId, publicContext(BRAVO_ID, clock.getAsLong())
        );
        require(requested.aCase().state() == CaseState.REVIEW_REQUESTED,
                "a review request moves VERDICTED -> REVIEW_REQUESTED");
        require(access.lastCapability() == CapabilityClass.ONSITE_PUBLIC_SERVICE,
                "a review request validates as ONSITE_PUBLIC_SERVICE");

        CaseReceipt decided = service.decideReview(
                ALPHA_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())
        );
        require(decided.aCase().state() == CaseState.FINAL,
                "a review decision completes the case to FINAL");
        require(decided.aCase().recordRevision() == 9L,
                "the review completes in two transitions (revision +2)");
        require(access.lastCapability() == CapabilityClass.ONSITE_OFFICIAL_DUTY,
                "a review decision validates as ONSITE_OFFICIAL_DUTY");
        List<TransitionRecord> transitions =
                repository(store).snapshot().transitions();
        TransitionRecord completed = transitions.get(transitions.size() - 2);
        require(completed.trigger() == CaseTransitionTrigger.REVIEW_COMPLETED
                        && completed.before().equals(
                                Optional.of(CaseState.REVIEW_REQUESTED))
                        && completed.after() == CaseState.REVIEWED,
                "REVIEW_COMPLETED carries before/after");
        TransitionRecord finalized = transitions.get(transitions.size() - 1);
        require(finalized.trigger() == CaseTransitionTrigger.FINALIZED
                        && finalized.before().equals(Optional.of(CaseState.REVIEWED))
                        && finalized.after() == CaseState.FINAL,
                "FINALIZED carries before/after");

        // Only VERDICTED cases can request a review.
        CaseId freshId = verdictPendingCase(service, clock);
        JusticeUnavailableException premature = expectThrows(
                JusticeUnavailableException.class,
                () -> service.requestReview(
                        ALPHA_ID, freshId, publicContext(ALPHA_ID, clock.getAsLong())
                ),
                "a non-VERDICTED case cannot request a review"
        );
        require(premature.failureCode().equals(
                        JusticeUnavailableException.CODE_ILLEGAL_TRANSITION),
                "premature review request reports ILLEGAL_TRANSITION");
    }

    // ------------------------------------------------------------------
    // acceptance: closed pipeline — no illegal jumps (§6)
    // ------------------------------------------------------------------

    private static void testNoIllegalJumps() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(9_000);
        JusticeService service = service(
                repository(store), clock, citizens(ALPHA_ID),
                new FakeInstitutionAccessService()
        );
        CaseId caseId = service.fileCase(
                new CaseDraft(CaseType.CIVIL, "C", "body", Optional.empty()),
                publicContext(ALPHA_ID, clock.getAsLong())
        ).aCase().caseId();

        // FILED: no verdict, no review decision, no advance.
        requireRejected(service, () -> service.issueVerdict(
                        ALPHA_ID, caseId,
                        new VerdictDraft(caseId, VerdictOutcome.GUILTY, "r"),
                        officialContext(ALPHA_ID, clock.getAsLong())),
                "a FILED case cannot be adjudicated");
        requireRejected(service, () -> service.decideReview(
                        ALPHA_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())),
                "a FILED case cannot complete a review");

        // VERDICT_PENDING: no acceptance, no review request.
        CaseId pendingId = verdictPendingCase(service, clock);
        requireRejected(service, () -> service.acceptCase(
                        ALPHA_ID, pendingId, officialContext(ALPHA_ID, clock.getAsLong())),
                "a VERDICT_PENDING case cannot be accepted");
        requireRejected(service, () -> service.requestReview(
                        ALPHA_ID, pendingId, publicContext(ALPHA_ID, clock.getAsLong())),
                "a VERDICT_PENDING case cannot request a review");
    }

    private static void requireRejected(
            JusticeService service,
            Runnable action,
            String message
    ) {
        JusticeUnavailableException failure = expectThrows(
                JusticeUnavailableException.class,
                action,
                message
        );
        require(failure.failureCode().equals(
                        JusticeUnavailableException.CODE_ILLEGAL_TRANSITION),
                message + " reports ILLEGAL_TRANSITION");
    }

    // ------------------------------------------------------------------
    // acceptance: independence — no verdict execution (§6)
    // ------------------------------------------------------------------

    private static void testIndependenceNoExecution() throws Exception {
        for (Method method : JusticeService.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            require(!name.contains("execute") && !name.contains("enforce")
                            && !name.contains("apply") && !name.contains("judge")
                            && !name.contains("sentence") && !name.contains("fine")
                            && !name.contains("ban") && !name.contains("seize")
                            && !name.contains("deport") && !name.contains("arrest"),
                    "no verdict-execution surface in JusticeService: "
                            + method.getName());
        }
        for (Method method : JusticeRepository.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            require(!name.contains("execute") && !name.contains("enforce")
                            && !name.contains("judge") && !name.contains("sentence")
                            && !name.contains("fine") && !name.contains("ban")
                            && !name.contains("seize"),
                    "no verdict-execution surface in JusticeRepository: "
                            + method.getName());
        }
        // Justice consumes only the value objects of FR-LAND; it never reads
        // the Land NBT, never calls Land/Economy/Government/Parliament
        // services, and never touches their storage keys.
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path justiceDirectory = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/server/justice"
        );
        require(Files.isDirectory(justiceDirectory),
                "Production justice source directory must exist");
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(justiceDirectory)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> source.append(read(path)).append('\n'));
        }
        String codeOnly = stripComments(source.toString());
        // FR-JUS-001-A §2 authorizes justice to consume the core
        // infrastructure services directly (FR-CIT standing, FR-AUD audit,
        // FR-INST-002 on-site boundary); those api/persistence references are
        // dependencies, not forbidden cross-module storage or services. The
        // ban targets business-module (Land/Economy/Government/Parliament)
        // services/storage and direct SavedData access only.
        for (String forbidden : List.of(
                "server.economy", "server.government", "server.parliament",
                "server.land.persistence", "server.land.api", "server.land.service",
                "server.citizen.persistence", "server.citizen.service",
                "server.audit.persistence",
                "commitModuleData(\"economy\"", "commitModuleData(\"land\"",
                "commitModuleData(\"citizen\"", "commitModuleData(\"government\"",
                "commitModuleData(\"parliament\"", "commitModuleData(\"audit\"",
                "commitModuleData(\"playerdata\"",
                "getModuleData(\"economy\"", "getModuleData(\"land\"",
                "getModuleData(\"citizen\"", "getModuleData(\"government\"",
                "getModuleData(\"parliament\"", "getModuleData(\"audit\"",
                "getModuleData(\"playerdata\""
        )) {
            require(!codeOnly.contains(forbidden),
                    "justice production code must not touch other modules' "
                            + "storage or services: " + forbidden);
        }
    }

    private static void testNoTechnicalPermissionMapping() throws Exception {
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path justiceDirectory = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/server/justice"
        );
        require(Files.isDirectory(justiceDirectory),
                "Production justice source directory must exist");
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(justiceDirectory)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> source.append(read(path)).append('\n'));
        }
        String codeOnly = stripComments(source.toString());
        for (String forbidden : List.of(
                "isOp", "opLevel", "permissionLevel", "getPermission",
                "bypass", "canBypass", "sudo", "setOp", "Commands.OP",
                "CitizenRank", "Permissions", "hasPermission"
        )) {
            require(!codeOnly.contains(forbidden),
                    "justice production code must not map political office "
                            + "to technical permission: " + forbidden
            );
        }
    }

    // ------------------------------------------------------------------
    // acceptance: injection failure publishes nothing (§6)
    // ------------------------------------------------------------------

    private static void testInjectionFailureNoPublish() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(10_000);
        JusticeService service = service(
                repository(store), clock, citizens(ALPHA_ID),
                new FakeInstitutionAccessService()
        );
        service.fileCase(
                new CaseDraft(CaseType.CIVIL, "Law", "Body.", Optional.empty()),
                publicContext(ALPHA_ID, clock.getAsLong())
        );
        long revisionBefore = repository(store).snapshot().storeRevision();

        store.setCommitFailureCode("INJECTED_FAILURE");
        JusticeUnavailableException failure = expectThrows(
                JusticeUnavailableException.class,
                () -> service.fileCase(
                        new CaseDraft(CaseType.CIVIL, "Lost", "Body.", Optional.empty()),
                        publicContext(ALPHA_ID, clock.getAsLong())
                ),
                "a rejected durable commit must fail closed"
        );
        require(failure.failureCode().equals(
                        JusticeUnavailableException.CODE_STORE_FAILURE),
                "store rejection reports STORE_FAILURE");
        require(repository(store).snapshot().storeRevision() == revisionBefore,
                "a failed commit publishes nothing (revision unchanged)");
        require(repository(store).snapshot().cases().size() == 1,
                "a failed commit publishes nothing (no case)");

        store.setCommitException(new IllegalStateException("store down"));
        expectThrows(
                JusticeUnavailableException.class,
                () -> service.fileCase(
                        new CaseDraft(CaseType.CIVIL, "Lost again", "Body.", Optional.empty()),
                        publicContext(ALPHA_ID, clock.getAsLong())
                ),
                "a store exception fails closed"
        );
        require(repository(store).snapshot().storeRevision() == revisionBefore,
                "a store exception publishes nothing");
    }

    // ------------------------------------------------------------------
    // acceptance: restart recovery (§6)
    // ------------------------------------------------------------------

    private static void testRestartPersistence() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(11_000);
        JusticeService service = service(
                repository(store), clock, citizens(ALPHA_ID),
                new FakeInstitutionAccessService()
        );
        CaseId caseId = service.fileCase(
                new CaseDraft(CaseType.CIVIL, "C", "body", Optional.empty()),
                publicContext(ALPHA_ID, clock.getAsLong())
        ).aCase().caseId();
        EvidenceReceipt submitted = service.submitEvidence(
                new EvidenceDraft(caseId, "photo", Optional.empty()),
                publicContext(ALPHA_ID, clock.getAsLong())
        );
        service.acceptCase(
                ALPHA_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())
        );
        service.advanceCase(
                ALPHA_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())
        );
        service.advanceCase(
                ALPHA_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())
        );
        EvidenceReceipt admitted = service.admitEvidence(
                ALPHA_ID, submitted.evidence().evidenceId(), true,
                officialContext(ALPHA_ID, clock.getAsLong())
        );
        require(admitted.evidence().state() == EvidenceState.ADMITTED,
                "restart-scenario evidence must be admitted before the verdict");
        service.issueVerdict(
                ALPHA_ID,
                caseId,
                new VerdictDraft(caseId, VerdictOutcome.GUILTY, "r"),
                officialContext(ALPHA_ID, clock.getAsLong())
        );
        long revisionBefore = repository(store).snapshot().storeRevision();

        JusticeRepository restarted = restartRepository(store);
        JusticeService restartedService = service(
                restarted, clock, citizens(ALPHA_ID),
                new FakeInstitutionAccessService()
        );
        JusticeStoreSnapshot recovered = restarted.snapshot();
        require(recovered.storeRevision() == revisionBefore,
                "the store revision survives restart");
        require(recovered.cases().size() == 1,
                "cases survive restart");
        require(recovered.evidence().size() == 1,
                "evidence survives restart");
        require(recovered.verdicts().size() == 1,
                "verdicts survive restart");
        require(recovered.transitions().size() == 6,
                "the transition ledger survives restart");
        Case recoveredCase = recovered.cases().get(caseId);
        require(recoveredCase.state() == CaseState.VERDICTED,
                "the pipeline state survives restart");

        // The restarted runtime can continue along the review path.
        CaseReceipt requested = restartedService.requestReview(
                ALPHA_ID, caseId, publicContext(ALPHA_ID, clock.getAsLong())
        );
        require(requested.aCase().state() == CaseState.REVIEW_REQUESTED,
                "the restarted runtime accepts a review request");
    }

    // ------------------------------------------------------------------
    // acceptance: strict deterministic codec (§6)
    // ------------------------------------------------------------------

    private static void testStrictDeterministicCodec() throws Exception {
        JusticeNbtCodec codec = new JusticeNbtCodec();
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(12_000);
        JusticeService service = service(
                repository(store), clock,
                citizens(ALPHA_ID, BRAVO_ID),
                new FakeInstitutionAccessService()
        );
        CaseReceipt filed = service.fileCase(
                new CaseDraft(CaseType.CIVIL, "Deterministic", "Body.",
                        Optional.of(BRAVO_ID)),
                publicContext(ALPHA_ID, clock.getAsLong())
        );
        EvidenceReceipt submitted = service.submitEvidence(
                new EvidenceDraft(
                        filed.aCase().caseId(), "photo",
                        Optional.of("sha256:abc")
                ),
                publicContext(ALPHA_ID, clock.getAsLong())
        );
        service.acceptCase(
                ALPHA_ID, filed.aCase().caseId(),
                officialContext(ALPHA_ID, clock.getAsLong())
        );
        service.advanceCase(
                ALPHA_ID, filed.aCase().caseId(),
                officialContext(ALPHA_ID, clock.getAsLong())
        );
        service.advanceCase(
                ALPHA_ID, filed.aCase().caseId(),
                officialContext(ALPHA_ID, clock.getAsLong())
        );
        service.admitEvidence(
                ALPHA_ID, submitted.evidence().evidenceId(), true,
                officialContext(ALPHA_ID, clock.getAsLong())
        );
        service.issueVerdict(
                ALPHA_ID,
                filed.aCase().caseId(),
                new VerdictDraft(
                        filed.aCase().caseId(), VerdictOutcome.NOT_GUILTY, "no proof"
                ),
                officialContext(ALPHA_ID, clock.getAsLong())
        );

        JusticeStoreSnapshot snapshot = repository(store).snapshot();
        CompoundTag encoded = codec.encode(snapshot);
        CompoundTag encodedAgain = codec.encode(snapshot);
        require(java.util.Arrays.equals(
                        nbtBytes(encoded), nbtBytes(encodedAgain)),
                "encoding the same snapshot is deterministic");
        JusticeStoreSnapshot decoded = codec.decode(encoded.copy());
        require(decoded.equals(snapshot),
                "decode(encode(snapshot)) round-trips exactly");
        require(decoded.cases().get(filed.aCase().caseId()).defendantRef()
                        .equals(Optional.of(BRAVO_ID)),
                "optional fields survive the round-trip");
        require(decoded.evidence().values().iterator().next()
                        .integrityDigest().equals(Optional.of("sha256:abc")),
                "the integrity digest survives the round-trip");
    }

    // ------------------------------------------------------------------
    // acceptance: corruption fails closed (§6)
    // ------------------------------------------------------------------

    private static void testCorruptSnapshotFailClosed() {
        JusticeNbtCodec codec = new JusticeNbtCodec();

        // Unknown store field.
        CompoundTag unknownField = baseJusticeRoot();
        unknownField.putString("Sneaky", "x");
        expectThrows(JusticeNbtException.class,
                () -> codec.decode(unknownField),
                "an unknown field rejects the load");

        // Wrong store version.
        CompoundTag wrongVersion = baseJusticeRoot();
        wrongVersion.putInt("StoreVersion", 99);
        expectThrows(JusticeNbtException.class,
                () -> codec.decode(wrongVersion),
                "a newer store version rejects the load");

        // Wrong NBT type.
        CompoundTag wrongType = baseJusticeRoot();
        wrongType.putString("StoreRevision", "1");
        expectThrows(JusticeNbtException.class,
                () -> codec.decode(wrongType),
                "a wrong NBT type rejects the load");

        // Dangling evidence reference.
        CompoundTag dangling = baseJusticeRoot();
        CompoundTag evidenceIndex = new CompoundTag();
        CompoundTag evidenceEntry = evidenceTag(
                EvidenceId.of(UUID.fromString(
                        "00000000-0000-0000-0000-0000000000aa")),
                CaseId.of(UUID.fromString(
                        "00000000-0000-0000-0000-0000000000bb")),
                EvidenceState.SUBMITTED
        );
        evidenceIndex.put(
                "00000000-0000-0000-0000-0000000000aa", evidenceEntry
        );
        dangling.put("Evidence", evidenceIndex);
        expectThrows(JusticeNbtException.class,
                () -> codec.decode(dangling),
                "evidence referencing a missing case rejects the load");

        // Invalid pipeline state string.
        CompoundTag badState = baseJusticeRoot();
        CompoundTag caseIndex = new CompoundTag();
        CompoundTag caseEntry = caseTag(
                CaseId.of(UUID.fromString(
                        "00000000-0000-0000-0000-0000000000cc")),
                CaseType.CIVIL,
                "BOGUS"
        );
        caseIndex.put("00000000-0000-0000-0000-0000000000cc", caseEntry);
        badState.put("Cases", caseIndex);
        expectThrows(JusticeNbtException.class,
                () -> codec.decode(badState),
                "an invalid pipeline state rejects the load");
    }

    // ------------------------------------------------------------------
    // acceptance: no enumeration API (§6)
    // ------------------------------------------------------------------

    private static void testNoEnumerationApi() {
        for (Class<?> type : List.of(
                JusticeService.class, JusticeRepository.class
        )) {
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Class<?> returnType = method.getReturnType();
                boolean collectionReturn = Collection.class.isAssignableFrom(returnType)
                        || Map.class.isAssignableFrom(returnType)
                        || returnType.isArray()
                        || Stream.class.isAssignableFrom(returnType);
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                boolean enumerationName = name.contains("findall")
                        || name.contains("listall") || name.contains("getall")
                        || name.contains("values") || name.equals("list")
                        || name.contains("enumerate") || name.contains("allelements");
                require(!(collectionReturn && enumerationName),
                        "no bulk enumeration method in " + type.getSimpleName() + ": "
                                + method.getName());
            }
        }
    }

    private static void testBoundedProjections() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(13_000);
        JusticeService service = service(
                repository(store), clock,
                citizens(ALPHA_ID, BRAVO_ID, CHARLIE_ID, DELTA_ID),
                new FakeInstitutionAccessService()
        );
        CaseId caseId = null;
        for (int index = 0; index < 4; index++) {
            CaseReceipt filed = service.fileCase(
                    new CaseDraft(
                            CaseType.CIVIL, "P" + index, "body " + index,
                            Optional.empty()
                    ),
                    publicContext(citizenFor(index), clock.getAsLong())
            );
            if (index == 0) {
                caseId = filed.aCase().caseId();
            }
        }
        for (int index = 0; index < 3; index++) {
            service.submitEvidence(
                    new EvidenceDraft(
                            caseId, "evidence " + index, Optional.empty()
                    ),
                    publicContext(ALPHA_ID, clock.getAsLong())
            );
        }
        List<CaseProjection> page = service.cases(0, 3);
        require(page.size() == 3,
                "a case projection is bounded by the requested limit");
        require(page.get(0).caseSeq() == 1L
                        && page.get(1).caseSeq() == 2L
                        && page.get(2).caseSeq() == 3L,
                "case projections are ordered by ascending sequence");
        List<CaseProjection> next = service.cases(3, 10);
        require(next.size() == 1 && next.get(0).caseSeq() == 4L,
                "case paging resumes strictly after the cursor");
        require(service.cases(0, 10_000).size() == 4,
                "an over-large case limit is capped by MAX_PROJECTION_SIZE");

        List<EvidenceProjection> evidence = service.evidenceFor(caseId, 0, 2);
        require(evidence.size() == 2
                        && evidence.get(0).evidenceSeq() == 1L
                        && evidence.get(1).evidenceSeq() == 2L,
                "evidence projections are bounded and ordered per case");
        List<EvidenceProjection> evidenceRest =
                service.evidenceFor(caseId, 2, 10);
        require(evidenceRest.size() == 1
                        && evidenceRest.get(0).evidenceSeq() == 3L,
                "evidence paging resumes strictly after the cursor");
    }

    // ------------------------------------------------------------------
    // acceptance: capacity (§6)
    // ------------------------------------------------------------------

    private static void testCapacityFailClosed() {
        JusticeLimits tight = new JusticeLimits(
                1, 2, 1, 64, 10, 8 * 1024 * 1024
        );
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(14_000);
        JusticeService service = service(
                repository(store, tight), clock, citizens(ALPHA_ID),
                new FakeInstitutionAccessService()
        );
        service.fileCase(
                new CaseDraft(CaseType.CIVIL, "Only", "Body.", Optional.empty()),
                publicContext(ALPHA_ID, clock.getAsLong())
        );
        JusticeUnavailableException capacity = expectThrows(
                JusticeUnavailableException.class,
                () -> service.fileCase(
                        new CaseDraft(CaseType.CIVIL, "Over", "Body.", Optional.empty()),
                        publicContext(ALPHA_ID, clock.getAsLong())
                ),
                "capacity exhaustion fails closed"
        );
        require(capacity.failureCode().equals(
                        JusticeUnavailableException.CODE_CAPACITY_EXCEEDED),
                "capacity exhaustion reports CAPACITY_EXCEEDED");
        require(repository(store).snapshot().cases().size() == 1,
                "capacity rejection publishes nothing");
    }

    // ------------------------------------------------------------------
    // acceptance: module contract (§6)
    // ------------------------------------------------------------------

    private static void testModuleContract() {
        require(JusticeRepository.MODULE_DATA_KEY.equals("justice"),
                "justice owns exactly the approved namespace");
        require(JusticeModule.MODULE_ID.value().equals("justice"),
                "justice module id is 'justice'");
        ModuleDefinition definition = new ModuleDefinition(
                JusticeModule.MODULE_ID,
                new ModuleMetadata("Justice", "1.0.0", Optional.empty(),
                        Optional.empty()),
                Set.of(
                        PlayerDataModule.MODULE_ID,
                        CitizenModule.MODULE_ID,
                        AuditModule.MODULE_ID,
                        InstitutionAccessModule.MODULE_ID,
                        LandModule.MODULE_ID
                ),
                Set.of(),
                95,
                JusticeModule::new
        );
        require(definition.requiredDependencies().equals(Set.of(
                        PlayerDataModule.MODULE_ID,
                        CitizenModule.MODULE_ID,
                        AuditModule.MODULE_ID,
                        InstitutionAccessModule.MODULE_ID,
                        LandModule.MODULE_ID)),
                "justice depends only on player-data, citizen, audit, "
                        + "institution-access, and land");
        for (ModuleId dependency : definition.requiredDependencies()) {
            String value = dependency.value();
            require(!value.contains("emg") && !value.contains("emergency")
                            && !value.contains("economy")
                            && !value.contains("parliament")
                            && !value.contains("government")
                            && !value.contains("bank"),
                    "justice never depends on other-pillar namespaces: " + value);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static JusticeRepository repository(JusticeStore store) {
        return new JusticeRepository(
                store, new JusticeNbtCodec(), JusticeLimits.DEFAULT
        );
    }

    private static JusticeRepository repository(
            JusticeStore store,
            JusticeLimits limits
    ) {
        return new JusticeRepository(store, new JusticeNbtCodec(), limits);
    }

    private static JusticeRepository restartRepository(
            SavedDataBackedTestStore store
    ) {
        return repository(store.restart());
    }

    private static JusticeService service(
            JusticeRepository repository,
            LongSupplier clock,
            FakeJusticeCitizenDirectory citizens,
            FakeInstitutionAccessService access
    ) {
        return new DefaultJusticeService(
                repository,
                clock,
                new SequentialIdSource(),
                citizens,
                access,
                null
        );
    }

    private static FakeJusticeCitizenDirectory citizens(UUID... players) {
        FakeJusticeCitizenDirectory directory = new FakeJusticeCitizenDirectory();
        for (UUID player : players) {
            directory.addCitizen(player);
        }
        return directory;
    }

    /** A case advanced to VERDICT_PENDING through the legal steps. */
    private static CaseId verdictPendingCase(
            JusticeService service,
            MutableClock clock
    ) {
        CaseId caseId = service.fileCase(
                new CaseDraft(CaseType.CIVIL, "C", "body", Optional.empty()),
                publicContext(ALPHA_ID, clock.getAsLong())
        ).aCase().caseId();
        service.acceptCase(
                ALPHA_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())
        );
        service.advanceCase(
                ALPHA_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())
        );
        service.advanceCase(
                ALPHA_ID, caseId, officialContext(ALPHA_ID, clock.getAsLong())
        );
        return caseId;
    }

    private static UUID citizenFor(int index) {
        return switch (index % 4) {
            case 0 -> ALPHA_ID;
            case 1 -> BRAVO_ID;
            case 2 -> CHARLIE_ID;
            default -> DELTA_ID;
        };
    }

    /** A valid ONSITE_PUBLIC_SERVICE context at a COURT facility. */
    private static OnSiteContext publicContext(UUID playerId, long now) {
        return context(playerId, now, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                WorkflowKind.PUBLIC);
    }

    /** A valid ONSITE_OFFICIAL_DUTY context at a COURT facility. */
    private static OnSiteContext officialContext(UUID playerId, long now) {
        return context(playerId, now, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                WorkflowKind.OFFICIAL_ROUTINE);
    }

    private static OnSiteContext context(
            UUID playerId,
            long now,
            CapabilityClass capability,
            WorkflowKind workflow
    ) {
        return new OnSiteContext(
                UUID.randomUUID(),
                playerId,
                InstitutionType.COURT,
                FacilityId.of(UUID.fromString(
                        "00000000-0000-0000-0000-0000000000dd")),
                TerminalId.of(UUID.fromString(
                        "00000000-0000-0000-0000-0000000000ee")),
                workflow,
                capability,
                now,
                now + 60_000,
                1L,
                1L,
                DIMENSION,
                10,
                64,
                10
        );
    }

    /** A read-only Land violation report value object (created by FR-LAND). */
    private static ViolationReport violationReport(long reportId, OwnerReference reporter) {
        return new ViolationReport(
                ViolationReport.CURRENT_SCHEMA_VERSION,
                reportId,
                ParcelId.of(UUID.fromString(
                        "00000000-0000-0000-0000-0000000000ff")),
                reporter,
                "unauthorized construction",
                7_000L,
                ViolationStatus.OPEN
        );
    }

    private static ViolationReport violationReport(long reportId, UUID reporterUuid) {
        return violationReport(
                reportId,
                new OwnerReference(OwnerReferenceKind.PLAYER_UUID, reporterUuid.toString())
        );
    }

    // ------------------------------------------------------------------
    // NBT builders for corruption tests
    // ------------------------------------------------------------------

    private static CompoundTag baseJusticeRoot() {
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 0L);
        root.put("Cases", new CompoundTag());
        root.put("Evidence", new CompoundTag());
        root.put("Verdicts", new CompoundTag());
        root.put("Transitions", new ListTag());
        return root;
    }

    private static CompoundTag caseTag(CaseId caseId, CaseType caseType, String state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("CaseVersion", 1);
        tag.putUUID("CaseId", caseId.value());
        tag.putLong("CaseSeq", 1L);
        tag.putString("CaseType", caseType.name());
        tag.putString("Title", "T");
        tag.putString("Description", "Body.");
        tag.putUUID("PlaintiffRef", ALPHA_ID);
        tag.putString("State", state);
        tag.putString("CourtLevel", "FIRST_INSTANCE");
        tag.putLong("CreatedAt", 1_000L);
        tag.putLong("RecordRevision", 2L);
        return tag;
    }

    private static CompoundTag evidenceTag(
            EvidenceId evidenceId,
            CaseId caseId,
            EvidenceState state
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("EvidenceVersion", 1);
        tag.putUUID("EvidenceId", evidenceId.value());
        tag.putUUID("CaseId", caseId.value());
        tag.putLong("EvidenceSeq", 1L);
        tag.putUUID("SubmittedByRef", ALPHA_ID);
        tag.putString("Description", "photo");
        tag.putString("State", state.name());
        tag.putLong("SubmittedAt", 1_000L);
        tag.putLong("RecordRevision", 1L);
        return tag;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to read " + path, failure);
        }
    }

    private static byte[] nbtBytes(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeUnnamedTag(tag, new DataOutputStream(out));
            return out.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to serialize NBT", failure);
        }
    }

    /** Removes line and block comments while preserving string literals. */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;
        boolean inString = false;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (inString) {
                out.append(current);
                if (current == '\\' && index + 1 < source.length()) {
                    out.append(source.charAt(index + 1));
                    index += 2;
                    continue;
                }
                if (current == '"') {
                    inString = false;
                }
                index++;
                continue;
            }
            if (current == '"') {
                inString = true;
                out.append(current);
                index++;
                continue;
            }
            if (current == '/' && index + 1 < source.length()
                    && source.charAt(index + 1) == '/') {
                while (index < source.length() && source.charAt(index) != '\n') {
                    index++;
                }
                continue;
            }
            if (current == '/' && index + 1 < source.length()
                    && source.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < source.length()
                        && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                    index++;
                }
                index += 2;
                continue;
            }
            out.append(current);
            index++;
        }
        return out.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expected,
            Runnable action,
            String message
    ) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return expected.cast(throwable);
            }
            throw new AssertionError(
                    message + ": expected " + expected.getSimpleName()
                            + ", got " + throwable.getClass().getSimpleName(),
                    throwable
            );
        }
        throw new AssertionError(message + ": expected " + expected.getSimpleName());
    }

    // ------------------------------------------------------------------
    // test doubles
    // ------------------------------------------------------------------

    private static final class SavedDataBackedTestStore implements JusticeStore {
        private final ModSavedData savedData;
        private String commitFailureCode;
        private RuntimeException commitException;
        private int commitCount;

        private SavedDataBackedTestStore() {
            this(new ModSavedData());
        }

        private SavedDataBackedTestStore(ModSavedData savedData) {
            this.savedData = savedData;
        }

        @Override
        public CompoundTag load() {
            return savedData.getModuleData(JusticeRepository.MODULE_DATA_KEY).copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            commitCount++;
            if (commitException != null) {
                throw commitException;
            }
            if (commitFailureCode != null) {
                return new DurableCommitResult(
                        DurableCommitStatus.FAILED,
                        JusticeRepository.MODULE_DATA_KEY,
                        0L,
                        0L,
                        commitFailureCode
                );
            }
            savedData.putModuleData(
                    JusticeRepository.MODULE_DATA_KEY,
                    snapshot.copy()
            );
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    JusticeRepository.MODULE_DATA_KEY,
                    1L,
                    0L,
                    ""
            );
        }

        private void setCommitFailureCode(String failureCode) {
            this.commitFailureCode = failureCode;
        }

        private void setCommitException(RuntimeException exception) {
            this.commitException = exception;
        }

        private int commits() {
            return commitCount;
        }

        private SavedDataBackedTestStore restart() {
            CompoundTag root = savedData.save(new CompoundTag());
            return new SavedDataBackedTestStore(ModSavedData.load(root));
        }
    }

    private static final class MutableClock implements LongSupplier {
        private long now;

        private MutableClock(long now) {
            this.now = now;
        }

        @Override
        public long getAsLong() {
            return now;
        }
    }

    private static final class SequentialIdSource implements JusticeIdSource {
        private long counter;

        @Override
        public UUID nextUuid() {
            counter++;
            return new UUID(0L, counter);
        }
    }

    private static final class FakeJusticeCitizenDirectory
            implements JusticeCitizenDirectory {
        private final Set<UUID> records = new java.util.HashSet<>();
        private final Set<UUID> activeCitizens = new java.util.HashSet<>();
        private boolean available = true;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean hasPlayerRecord(UUID playerId) {
            return records.contains(playerId);
        }

        @Override
        public boolean isActiveCitizen(UUID playerId) {
            return activeCitizens.contains(playerId);
        }

        private void addCitizen(UUID playerId) {
            records.add(playerId);
            activeCitizens.add(playerId);
        }

        /** A player record that is not (yet) a citizen. */
        private void addNonCitizen(UUID playerId) {
            records.add(playerId);
        }
    }

    /**
     * Test double of the shared institution access boundary: records every
     * final mutation-boundary call and its capability, and returns a
     * configurable result. Every other operation is unsupported — the
     * justice module consumes the boundary at mutation time only.
     */
    private static final class FakeInstitutionAccessService
            implements InstitutionAccessService {
        private ValidationResult result = ValidationResult.ok();
        private int validateCalls;
        private CapabilityClass lastCapability;

        @Override
        public ValidationResult validateAtMutation(
                OnSiteContext context,
                CapabilityClass capability,
                long now,
                String dimension,
                int x,
                int y,
                int z
        ) {
            validateCalls++;
            lastCapability = capability;
            return result;
        }

        private void setResult(ValidationResult result) {
            this.result = result;
        }

        private CapabilityClass lastCapability() {
            return lastCapability;
        }

        @Override
        public FacilityReceipt registerFacility(UUID actor, FacilityRegistrationRequest request) {
            throw unsupported();
        }

        @Override
        public FacilityReceipt suspendFacility(UUID actor, FacilityId facilityId) {
            throw unsupported();
        }

        @Override
        public FacilityReceipt activateFacility(UUID actor, FacilityId facilityId) {
            throw unsupported();
        }

        @Override
        public FacilityReceipt relocateFacility(UUID actor, FacilityId facilityId, ParcelId newParcelId) {
            throw unsupported();
        }

        @Override
        public FacilityReceipt disableFacility(UUID actor, FacilityId facilityId) {
            throw unsupported();
        }

        @Override
        public TerminalReceipt registerTerminal(UUID actor, TerminalRegistrationRequest request) {
            throw unsupported();
        }

        @Override
        public TerminalReceipt suspendTerminal(UUID actor, TerminalId terminalId) {
            throw unsupported();
        }

        @Override
        public TerminalReceipt disableTerminal(UUID actor, TerminalId terminalId) {
            throw unsupported();
        }

        @Override
        public OnSiteContext issueOnSiteContext(
                UUID playerId,
                TerminalId terminalId,
                CapabilityClass capability,
                String playerDimension,
                int x,
                int y,
                int z
        ) {
            throw unsupported();
        }

        @Override
        public void consume(OnSiteContext context) {
            // no-op test double
        }

        @Override
        public void invalidateOnLeave(UUID playerId) {
            // no-op test double
        }

        @Override
        public Optional<Facility> getFacility(FacilityId facilityId) {
            throw unsupported();
        }

        @Override
        public Optional<Terminal> getTerminal(TerminalId terminalId) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException(
                    "not part of the justice test double"
            );
        }
    }
}
