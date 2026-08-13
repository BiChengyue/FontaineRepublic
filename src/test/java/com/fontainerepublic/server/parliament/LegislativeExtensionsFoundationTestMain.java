package com.fontainerepublic.server.parliament;

import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.core.ModSavedData;
import com.fontainerepublic.server.institutionaccess.api.FacilityReceipt;
import com.fontainerepublic.server.institutionaccess.api.FacilityRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.api.ValidationResult;
import com.fontainerepublic.server.institutionaccess.api.ZoneReceipt;
import com.fontainerepublic.server.institutionaccess.api.ZoneRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.InstitutionType;
import com.fontainerepublic.server.institutionaccess.model.WorkflowKind;
import com.fontainerepublic.server.institutionaccess.model.Zone;
import com.fontainerepublic.server.institutionaccess.model.ZoneId;
import com.fontainerepublic.server.institutionaccess.model.ZoneKind;
import com.fontainerepublic.server.institutionaccess.model.ZoneRegion;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.parliament.api.AmendmentDraft;
import com.fontainerepublic.server.parliament.api.BillReceipt;
import com.fontainerepublic.server.parliament.api.CourtReceipt;
import com.fontainerepublic.server.parliament.api.GuardianReceipt;
import com.fontainerepublic.server.parliament.api.ParliamentService;
import com.fontainerepublic.server.parliament.api.ProposalDraft;
import com.fontainerepublic.server.parliament.api.ProposalReceipt;
import com.fontainerepublic.server.parliament.api.ReferendumReceipt;
import com.fontainerepublic.server.parliament.api.VoteReceipt;
import com.fontainerepublic.server.parliament.model.Bill;
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
import com.fontainerepublic.server.parliament.persistence.ParliamentLimits;
import com.fontainerepublic.server.parliament.persistence.ParliamentNbtCodec;
import com.fontainerepublic.server.parliament.persistence.ParliamentNbtException;
import com.fontainerepublic.server.parliament.persistence.ParliamentRepository;
import com.fontainerepublic.server.parliament.persistence.ParliamentStore;
import com.fontainerepublic.server.parliament.persistence.ParliamentStoreSnapshot;
import com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException;
import com.fontainerepublic.server.parliament.service.DefaultParliamentService;
import com.fontainerepublic.server.parliament.service.GuardianDirectory;
import com.fontainerepublic.server.parliament.service.ParliamentCitizenDirectory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Dependency-free validation entry point for FR-PAR-002 (legislative
 * extensions). Exercises the FR-PAR-002-A §6 acceptance matrix with an
 * injectable store and SavedData-backed restart simulation: the guardian
 * review (72h/7d deadlines, timeout=approval, one return, override re-vote
 * thresholds), the supreme-court review state/deadline advancement (14d,
 * extendable once by 7d, no adjudication), the referendum (frozen roster,
 * participation 2/3, approval 2/3 of effective votes, one vote per citizen,
 * abstention counts toward participation only), the full amendment pipeline
 * (court -> parliament 4/5 -> referendum -> consent -> publish), the closed
 * state machine with transition records, on-site gating, injection failure
 * with no publish, restart recovery, and strict codec with backward
 * compatibility.
 */
public final class LegislativeExtensionsFoundationTestMain {

    private static final String DIMENSION = "minecraft:overworld";

    private static final UUID HYDRO_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID ALPHA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CHARLIE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID DELTA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000004");

    private static final long HOUR = 60L * 60 * 1000;
    private static final long DAY = 24L * HOUR;
    private static final long GUARDIAN_ORDINARY = 72L * HOUR;
    private static final long GUARDIAN_BASIC = 7L * DAY;
    private static final long COURT_14D = 14L * DAY;
    private static final long COURT_EXTENSION_7D = 7L * DAY;
    private static final long CONSENT_7D = 7L * DAY;

    private LegislativeExtensionsFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testOrdinaryLawGuardianApproval();
        testOrdinaryLawGuardianTimeout();
        testGuardianDeadlineNotReached();
        testGuardianReturnAndOverridePasses();
        testGuardianReturnOverrideFails();
        testGuardianChannelRejected();
        testOrganicGuardianNoVeto();
        testBasicLawCourtReviewFlow();
        testCourtReviewReturnReVote();
        testGuardianRecusalReplacement();
        testAmendmentFullPipeline();
        testAmendmentConsentReject();
        testAmendmentConsentTimeout();
        testReferendumThresholds();
        testReferendumOneVotePerCitizenAndRoster();
        testStateMachineClosed();
        testOnSiteGating();
        testInjectionFailureNoPublish();
        testRestartRecovery();
        testStrictCodecAndBackwardCompatibility();
        testParliamentAmendmentThreshold();
        testNoCourtAdjudicationSurface();
        System.out.println(
                "FR-PAR-002 legislativeExtensionsFoundationTest: ALL PASSED");
    }

    // ------------------------------------------------------------------
    // acceptance: guardian review (§6)
    // ------------------------------------------------------------------

    private static void testOrdinaryLawGuardianApproval() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(100_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access, guardians
        );
        ProposalId proposalId = approvedOrdinaryLaw(service, clock);

        GuardianReceipt submitted = service.submitForGuardianReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(submitted.proposal().state() == BillState.GUARDIAN_REVIEW,
                "an approved ordinary law enters GUARDIAN_REVIEW");
        ProposalStage stage = service.stage(proposalId).orElseThrow();
        require(stage.stageDeadlineAt().isPresent()
                        && stage.stageDeadlineAt().get() == 100_000L + GUARDIAN_ORDINARY,
                "the guardian deadline is now + 72h");

        GuardianReceipt approved = service.guardianApprove(
                HYDRO_ID, proposalId, GuardianChannel.HYDRO_ARCHON_PLAYER,
                context(HYDRO_ID, clock.getAsLong())
        );
        require(approved.proposal().state() == BillState.APPROVED,
                "guardian approval advances GUARDIAN_REVIEW -> APPROVED");
        require(approved.bill().isPresent()
                        && approved.bill().get().state() == BillState.APPROVED,
                "the bill follows the proposal to APPROVED");
        require(hasTransition(store, BillTransitionTrigger.GUARDIAN_APPROVED),
                "a GUARDIAN_APPROVED transition is recorded");
    }

    private static void testOrdinaryLawGuardianTimeout() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(200_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access, guardians
        );
        ProposalId proposalId = approvedOrdinaryLaw(service, clock);
        service.submitForGuardianReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );

        // 72h later the review times out and counts as approval.
        clock.setNow(200_000L + GUARDIAN_ORDINARY + 1);
        GuardianReceipt timedOut = service.guardianTimeoutAdvance(
                HYDRO_ID, proposalId, GuardianChannel.HYDRO_ARCHON_PLAYER,
                context(HYDRO_ID, clock.getAsLong())
        );
        require(timedOut.proposal().state() == BillState.APPROVED,
                "a timed-out guardian review counts as approval");
        require(hasTransition(store, BillTransitionTrigger.GUARDIAN_TIMED_OUT),
                "a GUARDIAN_TIMED_OUT transition is recorded");
    }

    private static void testGuardianDeadlineNotReached() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(300_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access, guardians
        );
        ProposalId proposalId = approvedOrdinaryLaw(service, clock);
        service.submitForGuardianReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        ParliamentUnavailableException failure = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.guardianTimeoutAdvance(
                        HYDRO_ID, proposalId, GuardianChannel.HYDRO_ARCHON_PLAYER,
                        context(HYDRO_ID, clock.getAsLong())
                ),
                "a timeout before the deadline is rejected"
        );
        require(failure.failureCode().equals(
                        ParliamentUnavailableException.CODE_DEADLINE_NOT_REACHED),
                "an early timeout reports DEADLINE_NOT_REACHED");
    }

    private static void testGuardianReturnAndOverridePasses() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(400_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID, CHARLIE_ID),
                access, guardians
        );
        ProposalId proposalId = approvedOrdinaryLaw(service, clock);
        service.submitForGuardianReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        GuardianReceipt returned = service.guardianReturn(
                HYDRO_ID, proposalId, GuardianChannel.HYDRO_ARCHON_PLAYER,
                "FR-BL-003 §7 procedural error",
                context(HYDRO_ID, clock.getAsLong())
        );
        require(returned.proposal().state() == BillState.GUARDIAN_RETURNED,
                "a guardian return advances GUARDIAN_REVIEW -> GUARDIAN_RETURNED");
        require(service.stage(proposalId).orElseThrow().returnBasis()
                        .equals(Optional.of("FR-BL-003 §7 procedural error")),
                "the return basis is persisted");

        // Override re-vote: ordinary laws need 2/3 of the frozen roster.
        VoteReceipt override = service.openOverrideVote(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(override.vote().requiredThreshold() == 2L,
                "the override threshold of 3 is 2 (ordinary 2/3)");
        service.castVote(ALPHA_ID, override.vote().voteId(), VoteChoice.FOR,
                context(ALPHA_ID, clock.getAsLong()));
        service.castVote(BRAVO_ID, override.vote().voteId(), VoteChoice.FOR,
                context(BRAVO_ID, clock.getAsLong()));
        BillReceipt passed = service.closeVoteAndAdvance(
                ALPHA_ID, override.vote().voteId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        require(passed.passed() && passed.bill().isPresent(),
                "the override ballot passes and keeps the bill");
        require(repository(store).snapshot().proposals().get(proposalId).state()
                        == BillState.APPROVED,
                "the override re-vote makes the law effective");
        require(hasTransition(store, BillTransitionTrigger.GUARDIAN_OVERRIDE_PASSED),
                "a GUARDIAN_OVERRIDE_PASSED transition is recorded");
    }

    private static void testGuardianReturnOverrideFails() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(500_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID, CHARLIE_ID),
                access, guardians
        );
        ProposalId proposalId = approvedOrdinaryLaw(service, clock);
        service.submitForGuardianReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        service.guardianReturn(HYDRO_ID, proposalId, GuardianChannel.HYDRO_ARCHON_PLAYER,
                "FR-CON §13 procedure", context(HYDRO_ID, clock.getAsLong()));
        VoteReceipt override = service.openOverrideVote(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        service.castVote(ALPHA_ID, override.vote().voteId(), VoteChoice.FOR,
                context(ALPHA_ID, clock.getAsLong()));
        // Only 1 of 3 -> below the 2/3 override threshold.
        BillReceipt failed = service.closeVoteAndAdvance(
                ALPHA_ID, override.vote().voteId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        require(!failed.passed(), "an override ballot below 2/3 fails");
        require(repository(store).snapshot().proposals().get(proposalId).state()
                        == BillState.REJECTED,
                "a failed override re-vote rejects the law");
    }

    private static void testGuardianChannelRejected() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(600_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        // HYDRO_ID is NOT registered as the archon here.
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access, guardians
        );
        ProposalId proposalId = approvedOrdinaryLaw(service, clock);
        service.submitForGuardianReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        ParliamentUnavailableException notArchon = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.guardianApprove(
                        HYDRO_ID, proposalId, GuardianChannel.HYDRO_ARCHON_PLAYER,
                        context(HYDRO_ID, clock.getAsLong())
                ),
                "a non-archon player cannot approve a review"
        );
        require(notArchon.failureCode().equals(
                        ParliamentUnavailableException.CODE_GUARDIAN_NOT_AUTHORIZED),
                "a non-archon reports GUARDIAN_NOT_AUTHORIZED");
        ParliamentUnavailableException badConsole = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.guardianApprove(
                        ALPHA_ID, proposalId, GuardianChannel.LOCAL_CONSOLE, null
                ),
                "only the fixed console actor may use the console channel"
        );
        require(badConsole.failureCode().equals(
                        ParliamentUnavailableException.CODE_GUARDIAN_NOT_AUTHORIZED),
                "an impostor console reports GUARDIAN_NOT_AUTHORIZED");
    }

    private static void testOrganicGuardianNoVeto() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(700_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access, guardians
        );
        // Organic law: parliament 2/3 -> court review -> guardian review.
        ProposalReceipt proposal = service.submitProposal(
                new ProposalDraft("Organic", NormLevel.ORGANIC, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        service.submitProposal(
                new ProposalDraft("Second", NormLevel.ORGANIC, "Body."),
                context(BRAVO_ID, clock.getAsLong())
        );
        ProposalId proposalId = approveThroughParliament(
                service, clock, proposal.proposal().proposalId()
        );
        service.submitForCourtReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        CourtReceipt passed = service.courtReviewPassed(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(passed.proposal().state() == BillState.GUARDIAN_REVIEW,
                "an organic law passes its court review into the guardian review");
        ParliamentUnavailableException noVeto = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.guardianReturn(
                        HYDRO_ID, proposalId, GuardianChannel.HYDRO_ARCHON_PLAYER,
                        "organic veto attempt",
                        context(HYDRO_ID, clock.getAsLong())
                ),
                "the guardian has no veto over organic laws"
        );
        require(noVeto.failureCode().equals(
                        ParliamentUnavailableException.CODE_GUARDIAN_NO_VETO),
                "an organic return reports GUARDIAN_NO_VETO");
    }

    // ------------------------------------------------------------------
    // acceptance: court review (§6)
    // ------------------------------------------------------------------

    private static void testBasicLawCourtReviewFlow() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(800_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        // Constitutional basic law: the submit gate rejects CONSTITUTION_BASIC,
        // so the REVIEW-stage record is injected (FR-PAR-001 reserved the level).
        ProposalId proposalId = ProposalId.of(UUID.randomUUID());
        CompoundTag root = baseParliamentRoot();
        root.getCompound("Proposals").put(
                proposalId.canonicalKey(),
                proposalTag(proposalId, "Basic", NormLevel.CONSTITUTION_BASIC,
                        BillState.REVIEW, ALPHA_ID, 1L)
        );
        store.putRaw(root);
        ParliamentService service = service(
                repository(store), clock,
                citizens(ALPHA_ID, BRAVO_ID, CHARLIE_ID, DELTA_ID), access, guardians
        );
        approveThroughParliament(service, clock, proposalId);
        require(repository(store).snapshot().proposals().get(proposalId).state()
                        == BillState.APPROVED,
                "the basic law passes parliament 3/4");

        CourtReceipt submitted = service.submitForCourtReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(submitted.proposal().state() == BillState.COURT_REVIEW,
                "the basic law enters COURT_REVIEW");
        ProposalStage stage = service.stage(proposalId).orElseThrow();
        require(stage.stageDeadlineAt().get() == 800_000L + COURT_14D,
                "the court deadline is now + 14d");

        service.extendCourtReview(ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong()));
        require(service.stage(proposalId).orElseThrow().stageDeadlineAt().get()
                        == 800_000L + COURT_14D + COURT_EXTENSION_7D,
                "the court deadline extends by 7 days");
        require(service.stage(proposalId).orElseThrow().courtExtensionCount() == 1,
                "the extension count is 1");
        ParliamentUnavailableException twice = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.extendCourtReview(
                        ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
                ),
                "the court review is extendable exactly once"
        );
        require(twice.failureCode().equals(
                        ParliamentUnavailableException.CODE_COURT_EXTENSION_LIMIT),
                "a second extension reports COURT_EXTENSION_LIMIT");

        CourtReceipt passed = service.courtReviewPassed(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(passed.proposal().state() == BillState.GUARDIAN_REVIEW,
                "a passed basic-law court review enters the guardian review");
        ProposalStage guardianStage = service.stage(proposalId).orElseThrow();
        require(guardianStage.stageDeadlineAt().get() == clock.getAsLong() + GUARDIAN_BASIC,
                "the basic-law guardian deadline is 7d");

        clock.setNow(clock.getAsLong() + GUARDIAN_BASIC + 1);
        GuardianReceipt approved = service.guardianTimeoutAdvance(
                HYDRO_ID, proposalId, GuardianChannel.HYDRO_ARCHON_PLAYER,
                context(HYDRO_ID, clock.getAsLong())
        );
        require(approved.proposal().state() == BillState.APPROVED,
                "the basic law is approved after the 7d guardian timeout");
    }

    private static void testCourtReviewReturnReVote() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(900_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID, CHARLIE_ID),
                access, guardians
        );
        ProposalReceipt proposal = service.submitProposal(
                new ProposalDraft("Organic", NormLevel.ORGANIC, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        service.submitProposal(
                new ProposalDraft("Second", NormLevel.ORGANIC, "Body."),
                context(BRAVO_ID, clock.getAsLong())
        );
        service.submitProposal(
                new ProposalDraft("Third", NormLevel.ORGANIC, "Body."),
                context(CHARLIE_ID, clock.getAsLong())
        );
        ProposalId proposalId = approveThroughParliament(
                service, clock, proposal.proposal().proposalId()
        );
        service.submitForCourtReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        CourtReceipt returned = service.courtReviewReturned(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(returned.proposal().state() == BillState.VOTING,
                "a returned court review sends the law back to parliament");
        VoteReceipt reopened = service.openCourtReVote(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(reopened.vote().requiredThreshold() == 2L,
                "the re-vote uses the organic 2/3 threshold");
        service.castVote(ALPHA_ID, reopened.vote().voteId(), VoteChoice.FOR,
                context(ALPHA_ID, clock.getAsLong()));
        service.castVote(BRAVO_ID, reopened.vote().voteId(), VoteChoice.FOR,
                context(BRAVO_ID, clock.getAsLong()));
        BillReceipt passed = service.closeVoteAndAdvance(
                ALPHA_ID, reopened.vote().voteId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        require(passed.passed() && passed.bill().isPresent(),
                "the court-returned re-vote passes with the existing bill");
        require(hasTransition(store, BillTransitionTrigger.COURT_RETURN_PASSED),
                "a COURT_RETURN_PASSED transition is recorded");
    }

    private static void testGuardianRecusalReplacement() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access, guardians
        );
        ProposalId proposalId = approvedOrdinaryLaw(service, clock);
        service.submitForGuardianReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        GuardianReceipt recused = service.guardianRecuse(
                HYDRO_ID, proposalId, GuardianChannel.HYDRO_ARCHON_PLAYER,
                context(HYDRO_ID, clock.getAsLong())
        );
        require(recused.proposal().state() == BillState.COURT_REVIEW,
                "a recusal sends the law to the supreme court");
        require(hasTransition(store, BillTransitionTrigger.GUARDIAN_RECUSED),
                "a GUARDIAN_RECUSED transition is recorded");
        CourtReceipt passed = service.courtReviewPassed(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(passed.proposal().state() == BillState.APPROVED,
                "the recusal replacement review takes effect directly");
    }

    // ------------------------------------------------------------------
    // acceptance: amendment pipeline (§6)
    // ------------------------------------------------------------------

    private static void testAmendmentFullPipeline() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_100_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID, CHARLIE_ID),
                access, guardians
        );
        registerCitizens(service, clock, ALPHA_ID, BRAVO_ID, CHARLIE_ID);

        ProposalReceipt amendment = service.submitAmendment(
                new AmendmentDraft("First-layer amendment", "Change the constitution."),
                context(ALPHA_ID, clock.getAsLong())
        );
        ProposalId proposalId = amendment.proposal().proposalId();
        require(amendment.proposal().kind() == ProposalKind.AMENDMENT
                        && amendment.proposal().state() == BillState.PROPOSED,
                "an amendment enters the pipeline at PROPOSED");
        require(hasTransition(store, BillTransitionTrigger.AMENDMENT_PROPOSED),
                "an AMENDMENT_PROPOSED transition is recorded");

        service.submitForCourtReview(ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong()));
        CourtReceipt passed = service.courtReviewPassed(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(passed.proposal().state() == BillState.PARLIAMENT_VOTE,
                "a passed amendment court review enters PARLIAMENT_VOTE");

        VoteReceipt parliamentVote = service.openParliamentVote(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(parliamentVote.vote().requiredThreshold() == 3L,
                "the amendment parliament threshold is 4/5 of 3 (3)");
        require(parliamentVote.vote().frozenRosterCount() == 3L,
                "the parliament ballot freezes the 3-citizen roster");
        service.castVote(ALPHA_ID, parliamentVote.vote().voteId(), VoteChoice.FOR,
                context(ALPHA_ID, clock.getAsLong()));
        service.castVote(BRAVO_ID, parliamentVote.vote().voteId(), VoteChoice.FOR,
                context(BRAVO_ID, clock.getAsLong()));
        service.castVote(CHARLIE_ID, parliamentVote.vote().voteId(), VoteChoice.FOR,
                context(CHARLIE_ID, clock.getAsLong()));
        BillReceipt parliamentClosed = service.closeParliamentVoteAndAdvance(
                ALPHA_ID, parliamentVote.vote().voteId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        require(parliamentClosed.passed(), "the 4/5 parliament ballot passes");
        require(repository(store).snapshot().proposals().get(proposalId).state()
                        == BillState.REFERENDUM_OPEN,
                "a passed parliament vote opens the referendum stage");

        ReferendumReceipt opened = service.openReferendum(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(opened.referendum().frozenRosterCount() == 3L,
                "the referendum freezes the citizen roster");
        service.castReferendumVote(ALPHA_ID, proposalId, VoteChoice.FOR,
                publicContext(ALPHA_ID, clock.getAsLong()));
        service.castReferendumVote(BRAVO_ID, proposalId, VoteChoice.FOR,
                publicContext(BRAVO_ID, clock.getAsLong()));
        require(access.lastCapability == CapabilityClass.ONSITE_PUBLIC_SERVICE,
                "referendum voting revalidates as ONSITE_PUBLIC_SERVICE");
        ReferendumReceipt closed = service.closeReferendum(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(closed.passed(), "the referendum passes (participation 2/3, approval 2/3)");
        require(repository(store).snapshot().proposals().get(proposalId).state()
                        == BillState.GUARDIAN_CONSENT,
                "a passed referendum awaits the constitutional consent");

        GuardianReceipt consented = service.guardianConsentApprove(
                HYDRO_ID, proposalId, GuardianChannel.HYDRO_ARCHON_PLAYER,
                context(HYDRO_ID, clock.getAsLong())
        );
        require(consented.proposal().state() == BillState.APPROVED,
                "the guardian consent approves the amendment");
        require(consented.bill().isPresent()
                        && consented.bill().get().state() == BillState.APPROVED,
                "the amendment bill is born at constitutional consent");
        require(hasTransition(store, BillTransitionTrigger.GUARDIAN_CONSENT_APPROVED),
                "a GUARDIAN_CONSENT_APPROVED transition is recorded");

        GuardianReceipt published = service.publishAmendment(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(published.proposal().state() == BillState.PUBLISHED,
                "the amendment pipeline ends at PUBLISHED");
        require(hasTransition(store, BillTransitionTrigger.PUBLISHED),
                "a PUBLISHED transition is recorded");
    }

    private static void testAmendmentConsentReject() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_200_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access, guardians
        );
        registerCitizens(service, clock, ALPHA_ID, BRAVO_ID);
        ProposalId proposalId = amendmentAtConsent(service, clock);

        GuardianReceipt rejected = service.guardianConsentReject(
                DefaultParliamentService.CONSOLE_ACTOR, proposalId,
                GuardianChannel.LOCAL_CONSOLE, null
        );
        require(rejected.proposal().state() == BillState.REJECTED,
                "an explicit consent rejection fails the amendment");
        require(hasTransition(store, BillTransitionTrigger.GUARDIAN_CONSENT_REJECTED),
                "a GUARDIAN_CONSENT_REJECTED transition is recorded");
    }

    private static void testAmendmentConsentTimeout() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_300_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access, guardians
        );
        registerCitizens(service, clock, ALPHA_ID, BRAVO_ID);
        ProposalId proposalId = amendmentAtConsent(service, clock);

        ParliamentUnavailableException early = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.guardianConsentTimeout(
                        DefaultParliamentService.CONSOLE_ACTOR, proposalId,
                        GuardianChannel.LOCAL_CONSOLE, null
                ),
                "a consent timeout before 7d is rejected"
        );
        require(early.failureCode().equals(
                        ParliamentUnavailableException.CODE_DEADLINE_NOT_REACHED),
                "an early consent timeout reports DEADLINE_NOT_REACHED");

        clock.setNow(clock.getAsLong() + CONSENT_7D + 1);
        GuardianReceipt timedOut = service.guardianConsentTimeout(
                DefaultParliamentService.CONSOLE_ACTOR, proposalId,
                GuardianChannel.LOCAL_CONSOLE, null
        );
        require(timedOut.proposal().state() == BillState.APPROVED,
                "a timed-out consent counts as granted");
        require(timedOut.bill().isPresent(), "the amendment bill is born on consent timeout");
        require(hasTransition(store, BillTransitionTrigger.GUARDIAN_CONSENT_TIMED_OUT),
                "a GUARDIAN_CONSENT_TIMED_OUT transition is recorded");
    }

    // ------------------------------------------------------------------
    // acceptance: referendum (§6)
    // ------------------------------------------------------------------

    private static void testReferendumThresholds() {
        // 3 citizens; 2 for -> participation 2/3, approval 2/2 -> passes.
        require(referendumOutcome(List.of(VoteChoice.FOR, VoteChoice.FOR), 3)
                        == BillState.GUARDIAN_CONSENT,
                "2/3 participation with 2/3 approval passes");
        // 1 for + 1 abstain -> participation 2/3, effective 1, approval 1/1
        // -> passes (abstention counts toward participation only).
        require(referendumOutcome(List.of(VoteChoice.FOR, VoteChoice.ABSTAIN), 3)
                        == BillState.GUARDIAN_CONSENT,
                "abstention counts toward participation, not effective votes");
        // 1 for + 1 against -> participation 2/3, effective 2, approval 1/2
        // below 2/3 -> fails.
        require(referendumOutcome(List.of(VoteChoice.FOR, VoteChoice.AGAINST), 3)
                        == BillState.REJECTED,
                "1/2 approval fails the 2/3 approval threshold");
        // Only 1 voter of 3 -> participation below 2/3 -> fails.
        require(referendumOutcome(List.of(VoteChoice.FOR), 3) == BillState.REJECTED,
                "participation below 2/3 fails the referendum");
    }

    /** Runs a fresh amendment pipeline and tallies the given referendum votes. */
    private static BillState referendumOutcome(List<VoteChoice> votes, int rosterSize) {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_400_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        ParliamentService service = service(
                repository(store), clock, citizensFor(rosterSize), access, guardians
        );
        for (int index = 0; index < rosterSize; index++) {
            service.submitProposal(
                    new ProposalDraft("Roster-" + index, NormLevel.ORDINARY, "B."),
                    context(citizenFor(index), clock.getAsLong())
            );
        }
        ProposalId proposalId = amendmentAtReferendum(service, clock);
        ReferendumReceipt opened = service.openReferendum(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(opened.referendum().frozenRosterCount() == rosterSize,
                "the referendum roster is frozen at open");
        int index = 0;
        for (VoteChoice choice : votes) {
            UUID voter = citizenFor(index++);
            service.castReferendumVote(
                    voter, proposalId, choice, publicContext(voter, clock.getAsLong())
            );
        }
        service.closeReferendum(ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong()));
        return repository(store).snapshot().proposals().get(proposalId).state();
    }

    private static void testReferendumOneVotePerCitizenAndRoster() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_500_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID, CHARLIE_ID),
                access, guardians
        );
        registerCitizens(service, clock, ALPHA_ID, BRAVO_ID, CHARLIE_ID);
        ProposalId proposalId = amendmentAtReferendum(service, clock);
        service.openReferendum(ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong()));
        service.castReferendumVote(ALPHA_ID, proposalId, VoteChoice.FOR,
                publicContext(ALPHA_ID, clock.getAsLong()));
        ParliamentUnavailableException duplicate = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.castReferendumVote(ALPHA_ID, proposalId, VoteChoice.AGAINST,
                        publicContext(ALPHA_ID, clock.getAsLong())),
                "one citizen casts one referendum vote"
        );
        require(duplicate.failureCode().equals(
                        ParliamentUnavailableException.CODE_ALREADY_VOTED),
                "a duplicate referendum vote reports ALREADY_VOTED");
        // DELTA is not a citizen / not on the frozen roster.
        ParliamentUnavailableException outsider = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.castReferendumVote(DELTA_ID, proposalId, VoteChoice.FOR,
                        publicContext(DELTA_ID, clock.getAsLong())),
                "an outsider cannot vote in the referendum"
        );
        require(outsider.failureCode().equals(
                        ParliamentUnavailableException.CODE_PLAYER_NOT_PROVISIONED),
                "an outsider reports PLAYER_NOT_PROVISIONED");
    }

    // ------------------------------------------------------------------
    // acceptance: closed machine / gating / durability (§6)
    // ------------------------------------------------------------------

    private static void testStateMachineClosed() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_600_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access, guardians
        );
        ProposalId proposalId = approvedOrdinaryLaw(service, clock);
        service.submitForGuardianReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        // A REVIEW-state proposal cannot enter the guardian review directly.
        ProposalReceipt fresh = service.submitProposal(
                new ProposalDraft("Fresh", NormLevel.ORDINARY, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        expectThrows(
                ParliamentUnavailableException.class,
                () -> service.submitForGuardianReview(
                        ALPHA_ID, fresh.proposal().proposalId(),
                        context(ALPHA_ID, clock.getAsLong())
                ),
                "a REVIEW proposal cannot enter the guardian review"
        );
        // An APPROVED ordinary law cannot open an override ballot.
        expectThrows(
                ParliamentUnavailableException.class,
                () -> service.openOverrideVote(
                        ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
                ),
                "an override ballot requires GUARDIAN_RETURNED"
        );
        // A guardian-review proposal cannot be court-reviewed directly.
        expectThrows(
                ParliamentUnavailableException.class,
                () -> service.submitForCourtReview(
                        ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
                ),
                "a GUARDIAN_REVIEW proposal cannot enter the court review"
        );
    }

    private static void testOnSiteGating() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_700_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access, guardians
        );
        ProposalId proposalId = approvedOrdinaryLaw(service, clock);
        service.submitForGuardianReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        // The on-site context is rejected at the final mutation boundary.
        access.setResult(ValidationResult.invalid(ValidationResult.REASON_NOT_ISSUED));
        ParliamentUnavailableException notOnSite = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.guardianApprove(
                        HYDRO_ID, proposalId, GuardianChannel.HYDRO_ARCHON_PLAYER,
                        context(HYDRO_ID, clock.getAsLong())
                ),
                "the guardian review requires a valid on-site context"
        );
        require(notOnSite.failureCode().equals(
                        ParliamentUnavailableException.CODE_ON_SITE_CONTEXT_INVALID),
                "a rejected on-site context reports ON_SITE_CONTEXT_INVALID");
    }

    private static void testInjectionFailureNoPublish() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_800_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access, guardians
        );
        ProposalId proposalId = approvedOrdinaryLaw(service, clock);
        service.submitForGuardianReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        store.setCommitFailureCode("disk-failure");
        ParliamentUnavailableException failure = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.guardianApprove(
                        DefaultParliamentService.CONSOLE_ACTOR, proposalId,
                        GuardianChannel.LOCAL_CONSOLE, null
                ),
                "a rejected durable commit fails the guardian approval"
        );
        require(failure.failureCode().equals(
                        ParliamentUnavailableException.CODE_STORE_FAILURE),
                "a failed durable commit reports STORE_FAILURE");
        require(repository(store).snapshot().proposals().get(proposalId).state()
                        == BillState.GUARDIAN_REVIEW,
                "a failed commit publishes nothing (state unchanged)");
    }

    private static void testRestartRecovery() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_900_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access, guardians
        );
        ProposalId proposalId = approvedOrdinaryLaw(service, clock);
        service.submitForGuardianReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );

        // Restart: fresh repository over the same SavedData.
        ParliamentService restarted = service(
                restartRepository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access, guardians
        );
        require(restarted.stage(proposalId).isPresent(),
                "the stage metadata survives a restart");
        require(restarted.stage(proposalId).orElseThrow().stageDeadlineAt().isPresent(),
                "the guardian deadline survives a restart");
        require(repository(store).snapshot().proposals().get(proposalId).state()
                        == BillState.GUARDIAN_REVIEW,
                "the proposal state survives a restart");
        // The recovered guardian can still approve.
        GuardianReceipt approved = restarted.guardianApprove(
                DefaultParliamentService.CONSOLE_ACTOR, proposalId,
                GuardianChannel.LOCAL_CONSOLE, null
        );
        require(approved.proposal().state() == BillState.APPROVED,
                "a restarted guardian can complete the review");
    }

    private static void testStrictCodecAndBackwardCompatibility() {
        // Backward compatibility: a FR-PAR-001-era snapshot (no Kind, no
        // Stages, no Referendums) loads cleanly.
        CompoundTag legacy = baseParliamentRoot();
        UUID legacyId = UUID.randomUUID();
        legacy.getCompound("Proposals").put(
                legacyId.toString(), legacyProposalTag(legacyId)
        );
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(legacy);
        ParliamentRepository repo = repository(store);
        require(repo.snapshot().stages().isEmpty()
                        && repo.snapshot().referendums().isEmpty(),
                "legacy snapshots load with empty stage/referendum indexes");
        Proposal loaded = repo.snapshot().proposals().values().iterator().next();
        require(loaded.kind() == ProposalKind.LEGISLATION,
                "legacy proposals default to the LEGISLATION kind");

        // Strict decode: unknown fields are rejected.
        ParliamentNbtCodec codec = new ParliamentNbtCodec();
        CompoundTag corrupt = baseParliamentRoot();
        corrupt.putString("BogusField", "x");
        expectThrows(
                ParliamentNbtException.class,
                () -> codec.decode(corrupt),
                "an unknown store field rejects the whole snapshot"
        );

        // Round-trip: a snapshot with a stage survives encode/decode.
        MutableClock clock = new MutableClock(2_000_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        guardians.addArchon(HYDRO_ID);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access, guardians
        );
        ProposalId proposalId = approvedOrdinaryLaw(service, clock);
        service.submitForGuardianReview(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        ParliamentStoreSnapshot snapshot = repository(store).snapshot();
        CompoundTag encoded = codec.encode(snapshot);
        ParliamentStoreSnapshot decoded = codec.decode(encoded);
        require(decoded.stages().containsKey(proposalId),
                "the stage index survives an encode/decode round-trip");
        require(decoded.stages().get(proposalId).stageDeadlineAt()
                        .equals(snapshot.stages().get(proposalId).stageDeadlineAt()),
                "the stage deadline survives the round-trip");
    }

    private static void testParliamentAmendmentThreshold() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(2_100_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeGuardianDirectory guardians = new FakeGuardianDirectory();
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID, CHARLIE_ID),
                access, guardians
        );
        registerCitizens(service, clock, ALPHA_ID, BRAVO_ID, CHARLIE_ID);
        // 4/5 of 3 -> 3 (ceiling): 2 votes must fail.
        VoteReceipt parliamentVote = amendmentAtParliamentVote(service, clock);
        service.castVote(ALPHA_ID, parliamentVote.vote().voteId(), VoteChoice.FOR,
                context(ALPHA_ID, clock.getAsLong()));
        service.castVote(BRAVO_ID, parliamentVote.vote().voteId(), VoteChoice.FOR,
                context(BRAVO_ID, clock.getAsLong()));
        BillReceipt failed = service.closeParliamentVoteAndAdvance(
                ALPHA_ID, parliamentVote.vote().voteId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        require(!failed.passed(), "an amendment parliament vote below 4/5 fails");
        require(repository(store).snapshot().proposals()
                        .get(parliamentVote.vote().proposalId()).state() == BillState.REJECTED,
                "a failed 4/5 parliament vote rejects the amendment");
    }

    private static void testNoCourtAdjudicationSurface() {
        for (java.lang.reflect.Method method : ParliamentService.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            require(!name.contains("judge") && !name.contains("sentence")
                            && !name.contains("fine") && !name.contains("adjudicate"),
                    "no court-adjudication surface in ParliamentService: "
                            + method.getName());
        }
    }

    // ------------------------------------------------------------------
    // pipeline helpers
    // ------------------------------------------------------------------

    private static ProposalId approvedOrdinaryLaw(
            ParliamentService service,
            MutableClock clock
    ) {
        ProposalReceipt proposal = service.submitProposal(
                new ProposalDraft("Ordinary law", NormLevel.ORDINARY, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        service.submitProposal(
                new ProposalDraft("Second", NormLevel.ORDINARY, "Body."),
                context(BRAVO_ID, clock.getAsLong())
        );
        return approveThroughParliament(service, clock, proposal.proposal().proposalId());
    }

    private static ProposalId approveThroughParliament(
            ParliamentService service,
            MutableClock clock,
            ProposalId proposalId
    ) {
        VoteReceipt opened = service.openVote(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        for (UUID voter : opened.vote().frozenRoster()) {
            service.castVote(voter, opened.vote().voteId(), VoteChoice.FOR,
                    context(voter, clock.getAsLong()));
        }
        service.closeVoteAndAdvance(
                ALPHA_ID, opened.vote().voteId(), context(ALPHA_ID, clock.getAsLong())
        );
        return proposalId;
    }

    /** Injects a REVIEW-state proposal record (used for reserved levels). */
    private static ProposalId injectReviewProposal(
            SavedDataBackedTestStore store,
            ParliamentService service,
            String title,
            NormLevel level
    ) {
        throw new UnsupportedOperationException(
                "injectReviewProposal is obsolete; inject through the store and rebuild the service"
        );
    }

    private static ProposalId amendmentAtConsent(ParliamentService service, MutableClock clock) {
        ProposalId proposalId = amendmentAtReferendum(service, clock);
        service.openReferendum(ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong()));
        for (UUID voter : service.referendum(proposalId).orElseThrow().frozenRoster()) {
            service.castReferendumVote(voter, proposalId, VoteChoice.FOR,
                    publicContext(voter, clock.getAsLong()));
        }
        ReferendumReceipt closed = service.closeReferendum(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        require(closed.passed(), "the setup referendum passes");
        return proposalId;
    }

    private static ProposalId amendmentAtConsentStage(
            ParliamentService service,
            MutableClock clock
    ) {
        return amendmentAtConsent(service, clock);
    }

    private static ProposalId amendmentAtReferendum(ParliamentService service, MutableClock clock) {
        ProposalReceipt amendment = service.submitAmendment(
                new AmendmentDraft("Amendment", "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        ProposalId proposalId = amendment.proposal().proposalId();
        service.submitForCourtReview(ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong()));
        service.courtReviewPassed(ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong()));
        VoteReceipt parliamentVote = service.openParliamentVote(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        for (UUID voter : parliamentVote.vote().frozenRoster()) {
            service.castVote(voter, parliamentVote.vote().voteId(), VoteChoice.FOR,
                    context(voter, clock.getAsLong()));
        }
        service.closeParliamentVoteAndAdvance(
                ALPHA_ID, parliamentVote.vote().voteId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        return proposalId;
    }

    private static VoteReceipt amendmentAtParliamentVote(
            ParliamentService service,
            MutableClock clock
    ) {
        ProposalReceipt amendment = service.submitAmendment(
                new AmendmentDraft("Amendment", "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        ProposalId proposalId = amendment.proposal().proposalId();
        service.submitForCourtReview(ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong()));
        service.courtReviewPassed(ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong()));
        return service.openParliamentVote(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static ParliamentRepository repository(ParliamentStore store) {
        return new ParliamentRepository(
                store, new ParliamentNbtCodec(), ParliamentLimits.DEFAULT
        );
    }

    private static ParliamentRepository restartRepository(SavedDataBackedTestStore store) {
        return repository(store.restart());
    }

    private static ParliamentService service(
            ParliamentRepository repository,
            LongSupplier clock,
            FakeParliamentCitizenDirectory citizens,
            FakeInstitutionAccessService access,
            FakeGuardianDirectory guardians
    ) {
        return new DefaultParliamentService(
                repository,
                clock,
                new SequentialIdSource(),
                citizens,
                access,
                guardians,
                null
        );
    }

    private static FakeParliamentCitizenDirectory citizens(UUID... players) {
        FakeParliamentCitizenDirectory directory = new FakeParliamentCitizenDirectory();
        for (UUID player : players) {
            directory.addCitizen(player);
        }
        return directory;
    }

    private static FakeParliamentCitizenDirectory citizensFor(int size) {
        FakeParliamentCitizenDirectory directory = new FakeParliamentCitizenDirectory();
        for (int index = 0; index < size; index++) {
            directory.addCitizen(citizenFor(index));
        }
        return directory;
    }

    /** Registers citizens into the passive parliament roster via proposals. */
    private static void registerCitizens(
            ParliamentService service,
            MutableClock clock,
            UUID... players
    ) {
        for (UUID player : players) {
            service.submitProposal(
                    new ProposalDraft("Roster-" + player, NormLevel.ORDINARY, "B."),
                    context(player, clock.getAsLong())
            );
        }
    }

    private static UUID citizenFor(int index) {
        return switch (index % 4) {
            case 0 -> ALPHA_ID;
            case 1 -> BRAVO_ID;
            case 2 -> CHARLIE_ID;
            default -> DELTA_ID;
        };
    }

    /** A valid ONSITE_OFFICIAL_DUTY context at a PARLIAMENT facility. */
    private static OnSiteContext context(UUID playerId, long now) {
        return new OnSiteContext(
                UUID.randomUUID(),
                playerId,
                InstitutionType.PARLIAMENT,
                FacilityId.of(UUID.fromString(
                        "00000000-0000-0000-0000-0000000000dd")),
                ZoneId.of(UUID.fromString(
                        "00000000-0000-0000-0000-0000000000ee")),
                WorkflowKind.OFFICIAL_ROUTINE,
                CapabilityClass.ONSITE_OFFICIAL_DUTY,
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

    /** A valid ONSITE_PUBLIC_SERVICE context (referendum public zone). */
    private static OnSiteContext publicContext(UUID playerId, long now) {
        return new OnSiteContext(
                UUID.randomUUID(),
                playerId,
                InstitutionType.PARLIAMENT,
                FacilityId.of(UUID.fromString(
                        "00000000-0000-0000-0000-0000000000dd")),
                ZoneId.of(UUID.fromString(
                        "00000000-0000-0000-0000-0000000000ef")),
                WorkflowKind.PUBLIC,
                CapabilityClass.ONSITE_PUBLIC_SERVICE,
                now,
                now + 60_000,
                1L,
                1L,
                DIMENSION,
                20,
                64,
                20
        );
    }

    private static boolean hasTransition(
            SavedDataBackedTestStore store,
            BillTransitionTrigger trigger
    ) {
        return repository(store).snapshot().transitions().stream()
                .anyMatch(t -> t.trigger() == trigger);
    }

    // ------------------------------------------------------------------
    // NBT builders
    // ------------------------------------------------------------------

    private static CompoundTag baseParliamentRoot() {
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 0L);
        root.put("CitizenRoster", new ListTag());
        root.put("Proposals", new CompoundTag());
        root.put("Votes", new CompoundTag());
        root.put("Bills", new CompoundTag());
        root.put("Transitions", new ListTag());
        root.put("Stages", new CompoundTag());
        root.put("Referendums", new CompoundTag());
        return root;
    }

    private static CompoundTag legacyProposalTag(UUID proposalId) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ProposalVersion", 1);
        tag.putUUID("ProposalId", proposalId);
        tag.putLong("ProposalSeq", 1L);
        tag.putString("Title", "Legacy");
        tag.putString("NormLevel", NormLevel.ORDINARY.name());
        tag.putString("FullText", "Body.");
        tag.putUUID("ProposerRef", ALPHA_ID);
        tag.putString("State", BillState.REVIEW.name());
        tag.putLong("CreatedAt", 1_000L);
        tag.putLong("RecordRevision", 1L);
        return tag;
    }

    private static CompoundTag proposalTag(
            ProposalId proposalId,
            String title,
            NormLevel level,
            BillState state,
            UUID proposer,
            long revision
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ProposalVersion", 1);
        tag.putUUID("ProposalId", proposalId.value());
        tag.putLong("ProposalSeq", 1L);
        tag.putString("Title", title);
        tag.putString("NormLevel", level.name());
        tag.putString("Kind", ProposalKind.LEGISLATION.name());
        tag.putString("FullText", "Body.");
        tag.putUUID("ProposerRef", proposer);
        tag.putString("State", state.name());
        tag.putLong("CreatedAt", 1_000L);
        tag.putLong("RecordRevision", revision);
        return tag;
    }

    private static IntArrayTag uuidTag(UUID uuid) {
        return new IntArrayTag(new int[]{
                (int) (uuid.getMostSignificantBits() >> 32),
                (int) uuid.getMostSignificantBits(),
                (int) (uuid.getLeastSignificantBits() >> 32),
                (int) uuid.getLeastSignificantBits()
        });
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

    private static final class SavedDataBackedTestStore implements ParliamentStore {
        private final ModSavedData savedData;
        private String commitFailureCode;
        private RuntimeException commitException;

        private SavedDataBackedTestStore() {
            this(new ModSavedData());
        }

        private SavedDataBackedTestStore(ModSavedData savedData) {
            this.savedData = savedData;
        }

        @Override
        public CompoundTag load() {
            return savedData.getModuleData(ParliamentRepository.MODULE_DATA_KEY).copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            if (commitException != null) {
                throw commitException;
            }
            if (commitFailureCode != null) {
                return new DurableCommitResult(
                        DurableCommitStatus.FAILED,
                        ParliamentRepository.MODULE_DATA_KEY,
                        0L,
                        0L,
                        commitFailureCode
                );
            }
            savedData.putModuleData(
                    ParliamentRepository.MODULE_DATA_KEY,
                    snapshot.copy()
            );
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    ParliamentRepository.MODULE_DATA_KEY,
                    1L,
                    0L,
                    ""
            );
        }

        private void setCommitFailureCode(String failureCode) {
            this.commitFailureCode = failureCode;
        }

        private void putRaw(CompoundTag raw) {
            savedData.putModuleData(ParliamentRepository.MODULE_DATA_KEY, raw);
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

        private void setNow(long now) {
            this.now = now;
        }
    }

    private static final class SequentialIdSource implements ParliamentIdSource {
        private long counter;

        @Override
        public UUID nextUuid() {
            counter++;
            return new UUID(0L, counter);
        }
    }

    private static final class FakeParliamentCitizenDirectory
            implements ParliamentCitizenDirectory {
        private final Set<UUID> records = new java.util.HashSet<>();
        private final Set<UUID> activeCitizens = new java.util.HashSet<>();

        @Override
        public boolean isAvailable() {
            return true;
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
    }

    private static final class FakeGuardianDirectory implements GuardianDirectory {
        private final Set<UUID> archons = new java.util.HashSet<>();
        private boolean available = true;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean isHydroArchon(UUID playerId) {
            return archons.contains(playerId);
        }

        private void addArchon(UUID playerId) {
            archons.add(playerId);
        }
    }

    /**
     * Test double of the shared institution access boundary: records the
     * final mutation-boundary calls and returns a configurable result.
     */
    private static final class FakeInstitutionAccessService
            implements InstitutionAccessService {
        private ValidationResult result = ValidationResult.ok();
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
            lastCapability = capability;
            return result;
        }

        private void setResult(ValidationResult result) {
            this.result = result;
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
        public ZoneReceipt addZone(UUID actor, ZoneRegistrationRequest request) {
            throw unsupported();
        }

        @Override
        public ZoneReceipt removeZone(UUID actor, ZoneId zoneId) {
            throw unsupported();
        }

        @Override
        public ZoneReceipt resizeZone(UUID actor, ZoneId zoneId, ZoneRegion newRegion) {
            throw unsupported();
        }

        @Override
        public ZoneReceipt setZoneKind(UUID actor, ZoneId zoneId, ZoneKind newKind) {
            throw unsupported();
        }

        @Override
        public ZoneReceipt suspendZone(UUID actor, ZoneId zoneId) {
            throw unsupported();
        }

        @Override
        public ZoneReceipt activateZone(UUID actor, ZoneId zoneId) {
            throw unsupported();
        }

        @Override
        public OnSiteContext issueOnSiteContext(
                UUID playerId,
                ZoneId zoneId,
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
        public Optional<com.fontainerepublic.server.institutionaccess.model.Facility>
                getFacility(FacilityId facilityId) {
            throw unsupported();
        }

        @Override
        public Optional<Zone> getZone(ZoneId zoneId) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException(
                    "not part of the legislative-extensions test double"
            );
        }
    }
}
