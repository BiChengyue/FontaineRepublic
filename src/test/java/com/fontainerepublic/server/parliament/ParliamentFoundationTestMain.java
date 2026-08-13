package com.fontainerepublic.server.parliament;

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
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessUnavailableException;
import com.fontainerepublic.server.land.model.ParcelId;
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
import com.fontainerepublic.server.parliament.persistence.ParliamentLimits;
import com.fontainerepublic.server.parliament.persistence.ParliamentNbtCodec;
import com.fontainerepublic.server.parliament.persistence.ParliamentNbtException;
import com.fontainerepublic.server.parliament.persistence.ParliamentRepository;
import com.fontainerepublic.server.parliament.persistence.ParliamentStore;
import com.fontainerepublic.server.parliament.persistence.ParliamentStoreSnapshot;
import com.fontainerepublic.server.parliament.persistence.ParliamentUnavailableException;
import com.fontainerepublic.server.parliament.service.DefaultParliamentService;
import com.fontainerepublic.server.parliament.service.ParliamentCitizenDirectory;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
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
 * Dependency-free validation entry point for FR-PAR-001 (parliament module).
 * Exercises the FR-PAR-001-A §6 acceptance matrix with an injectable store
 * and SavedData-backed restart simulation: proposal submission with on-site
 * context and normative level, ballot open/cast/close with citizenship and
 * one-vote-per-citizen, threshold math (1/2, 2/3, 3/4, ceiling, frozen
 * roster denominator), the closed legal state machine with transition
 * records and no illegal jumps, no legal execution, no technical-permission
 * mapping, strict deterministic codec with referential integrity and
 * fail-closed corruption handling, restart recovery, injection failure with
 * no publish, bounded projections, and no bulk enumeration API.
 */
public final class ParliamentFoundationTestMain {

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
    private static final UUID ERIN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000005");

    private ParliamentFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testProposalSubmissionOnSiteGated();
        testSubmissionEntersReviewWithTransitions();
        testNormLevelThresholds();
        testVoteOpenCastClosePassed();
        testVoteRejectedNoBill();
        testOneVotePerCitizen();
        testFrozenRosterEligibility();
        testStateMachineClosedNoIllegalJumps();
        testCloseIdempotentNoOp();
        testNoLegalExecution();
        testNoTechnicalPermissionMapping();
        testStrictDeterministicCodec();
        testCorruptSnapshotFailClosed();
        testRestartPersistence();
        testInjectionFailureNoPublish();
        testCapacityFailClosed();
        testNoEnumerationApi();
        testBoundedProjections();
        testModuleContract();
        System.out.println("[FR-PAR-001] Parliament foundation validation passed");
    }

    // ------------------------------------------------------------------
    // acceptance: proposal submission — on-site context + norm level (§6)
    // ------------------------------------------------------------------

    private static void testProposalSubmissionOnSiteGated() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        ParliamentRepository repository = repository(store);
        ParliamentService service = service(
                repository, clock, citizens(ALPHA_ID), access
        );

        // No on-site context -> fail closed.
        ParliamentUnavailableException noContext = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.submitProposal(
                        new ProposalDraft("T", NormLevel.ORDINARY, "text"),
                        null
                ),
                "submission without an on-site context must fail closed"
        );
        require(noContext.failureCode().equals(
                        ParliamentUnavailableException.CODE_ON_SITE_CONTEXT_INVALID),
                "no-context submission reports ON_SITE_CONTEXT_INVALID");

        // Invalid on-site context -> fail closed.
        access.setResult(ValidationResult.invalid("EXPIRED"));
        ParliamentUnavailableException expired = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.submitProposal(
                        new ProposalDraft("T", NormLevel.ORDINARY, "text"),
                        context(ALPHA_ID, clock.getAsLong())
                ),
                "submission with an invalid on-site context must fail closed"
        );
        require(expired.failureCode().equals(
                        ParliamentUnavailableException.CODE_ON_SITE_CONTEXT_INVALID),
                "invalid-context submission reports ON_SITE_CONTEXT_INVALID");
        access.setResult(ValidationResult.ok());

        // Constitution-amendment proposals are a deferred revision.
        ParliamentUnavailableException constitutional = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.submitProposal(
                        new ProposalDraft(
                                "Amend", NormLevel.CONSTITUTION_BASIC, "text"
                        ),
                        context(ALPHA_ID, clock.getAsLong())
                ),
                "constitution-amendment submission must be rejected"
        );
        require(constitutional.failureCode().equals(
                        ParliamentUnavailableException.CODE_INVALID_REQUEST),
                "constitutional submission reports INVALID_REQUEST");

        // Non-citizen actor -> fail closed (player record but no citizenship).
        FakeParliamentCitizenDirectory strictCitizens = citizens(ALPHA_ID);
        strictCitizens.addNonCitizen(BRAVO_ID);
        ParliamentService strictService = service(
                repository(store), clock, strictCitizens, access
        );
        strictService.submitProposal(
                new ProposalDraft("One", NormLevel.ORDINARY, "body"),
                context(ALPHA_ID, clock.getAsLong())
        );
        ParliamentUnavailableException notCitizen = expectThrows(
                ParliamentUnavailableException.class,
                () -> strictService.submitProposal(
                        new ProposalDraft("Two", NormLevel.ORDINARY, "body"),
                        context(BRAVO_ID, clock.getAsLong())
                ),
                "a non-citizen cannot submit a proposal"
        );
        require(notCitizen.failureCode().equals(
                        ParliamentUnavailableException.CODE_NOT_CITIZEN),
                "non-citizen submission reports NOT_CITIZEN");

        // Valid submission: exactly one snapshot, one store revision.
        ParliamentRepository fresh = repository(new SavedDataBackedTestStore());
        ParliamentService freshService = service(
                fresh, clock, citizens(ALPHA_ID), new FakeInstitutionAccessService()
        );
        ProposalReceipt receipt = freshService.submitProposal(
                new ProposalDraft("First law", NormLevel.ORDINARY, "Body text."),
                context(ALPHA_ID, clock.getAsLong())
        );
        require(receipt.proposal().proposalSeq() == 1L,
                "the first proposal has sequence 1");
        require(receipt.proposal().proposerRef().equals(ALPHA_ID),
                "the proposer is the validated on-site citizen");
        require(fresh.snapshot().storeRevision() == 1L,
                "submission commits exactly one store revision");
        require(fresh.snapshot().citizenRoster().equals(Set.of(ALPHA_ID)),
                "the proposer is registered on the citizen roster");
    }

    private static void testSubmissionEntersReviewWithTransitions() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(2_000);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID),
                new FakeInstitutionAccessService()
        );
        ProposalReceipt receipt = service.submitProposal(
                new ProposalDraft("Draft", NormLevel.ADMINISTRATIVE, "Rules."),
                context(ALPHA_ID, clock.getAsLong())
        );
        require(receipt.proposal().state() == BillState.REVIEW,
                "a submitted proposal enters REVIEW");
        require(receipt.proposal().recordRevision() == 2L,
                "two transitions advance the proposal revision to 2");
        ParliamentStoreSnapshot snapshot = repository(store).snapshot();
        require(snapshot.transitions().size() == 2,
                "submission writes exactly two transition records");
        TransitionRecord first = snapshot.transitions().get(0);
        require(first.trigger() == BillTransitionTrigger.SUBMITTED
                        && first.before().isEmpty()
                        && first.after() == BillState.DRAFT,
                "first transition is SUBMITTED into DRAFT");
        TransitionRecord second = snapshot.transitions().get(1);
        require(second.trigger() == BillTransitionTrigger.REVIEW_STARTED
                        && second.before().equals(Optional.of(BillState.DRAFT))
                        && second.after() == BillState.REVIEW,
                "second transition is REVIEW_STARTED DRAFT -> REVIEW");
        require(second.actor().equals(ALPHA_ID) && second.atMillis() == 2_000L,
                "every transition records actor and time");
    }

    // ------------------------------------------------------------------
    // acceptance: thresholds by norm level (§6)
    // ------------------------------------------------------------------

    private static void testNormLevelThresholds() {
        // Ordinary: 1/2 of a 2-citizen roster -> 1; ceiling of a 3-citizen
        // roster -> 2.
        require(openBallotThreshold(NormLevel.ORDINARY, 2) == 1L,
                "ordinary majority of 2 is 1");
        require(openBallotThreshold(NormLevel.ORDINARY, 3) == 2L,
                "ordinary majority of 3 rounds up to 2");
        // Organic: 2/3 of 3 -> 2; 2/3 of 4 -> 3 (ceiling).
        require(openBallotThreshold(NormLevel.ORGANIC, 3) == 2L,
                "organic 2/3 of 3 is 2");
        require(openBallotThreshold(NormLevel.ORGANIC, 4) == 3L,
                "organic 2/3 of 4 rounds up to 3");
        // Constitutional basic: 3/4 of 4 -> 3; 3/4 of 3 -> 3 (ceiling).
        require(openBallotThreshold(NormLevel.CONSTITUTION_BASIC, 4) == 3L,
                "constitutional 3/4 of 4 is 3");
        require(openBallotThreshold(NormLevel.CONSTITUTION_BASIC, 3) == 3L,
                "constitutional 3/4 of 3 rounds up to 3");
        // Administrative: simple majority like ordinary laws.
        require(openBallotThreshold(NormLevel.ADMINISTRATIVE, 2) == 1L,
                "administrative 1/2 of 2 is 1");
    }

    /** Opens a ballot with a roster of the given size and returns threshold. */
    private static long openBallotThreshold(NormLevel level, int rosterSize) {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(3_000);
        FakeParliamentCitizenDirectory directory = new FakeParliamentCitizenDirectory();
        for (int index = 0; index < rosterSize; index++) {
            directory.addCitizen(citizenFor(index));
        }
        // Inject the frozen roster and one REVIEW proposal directly, bypassing
        // the submission gate (which rejects CONSTITUTION_BASIC as a deferred
        // revision). This also exercises the strict load path.
        CompoundTag root = baseParliamentRoot();
        ListTag roster = new ListTag();
        for (int index = 0; index < rosterSize; index++) {
            roster.add(uuidTag(citizenFor(index)));
        }
        root.put("CitizenRoster", roster);
        ProposalId proposalId = ProposalId.of(UUID.fromString(
                "00000000-0000-0000-0000-0000000000aa"));
        root.getCompound("Proposals").put(proposalId.value().toString(),
                proposalTag(proposalId, "T", level, BillState.REVIEW, ALPHA_ID));
        store.putRaw(root);
        ParliamentService service = service(
                repository(store), clock, directory,
                new FakeInstitutionAccessService()
        );
        VoteReceipt opened = service.openVote(
                ALPHA_ID, proposalId, context(ALPHA_ID, clock.getAsLong())
        );
        return opened.vote().requiredThreshold();
    }

    private static UUID citizenFor(int index) {
        return switch (index % 5) {
            case 0 -> ALPHA_ID;
            case 1 -> BRAVO_ID;
            case 2 -> CHARLIE_ID;
            case 3 -> DELTA_ID;
            default -> ERIN_ID;
        };
    }

    // ------------------------------------------------------------------
    // acceptance: vote open/cast/close (§6)
    // ------------------------------------------------------------------

    private static void testVoteOpenCastClosePassed() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(4_000);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID),
                new FakeInstitutionAccessService()
        );
        ProposalReceipt proposal = service.submitProposal(
                new ProposalDraft("Ordinary law", NormLevel.ORDINARY, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        // Register Bravo as well.
        service.submitProposal(
                new ProposalDraft("Second", NormLevel.ORDINARY, "Body."),
                context(BRAVO_ID, clock.getAsLong())
        );
        require(service.proposals(0, 10).size() == 2,
                "two proposals are listed");

        VoteReceipt opened = service.openVote(
                ALPHA_ID, proposal.proposal().proposalId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        Vote ballot = opened.vote();
        require(ballot.ballotState() == VoteBallotState.OPEN,
                "an opened ballot is OPEN");
        require(ballot.requiredThreshold() == 1L,
                "ordinary majority of 2 is 1");
        require(ballot.frozenRosterCount() == 2L,
                "the frozen roster size is 2");
        require(ballot.frozenRoster().equals(Set.of(ALPHA_ID, BRAVO_ID)),
                "the frozen roster is the citizen roster at open");
        require(ballot.voteRevision() == 1L,
                "a new ballot starts at revision 1");

        VoteReceipt cast = service.castVote(
                ALPHA_ID, ballot.voteId(), VoteChoice.FOR,
                context(ALPHA_ID, clock.getAsLong())
        );
        require(cast.vote().votesFor() == 1L && cast.vote().voteRevision() == 2L,
                "a cast increments the tally and the ballot revision");
        service.castVote(
                BRAVO_ID, ballot.voteId(), VoteChoice.AGAINST,
                context(BRAVO_ID, clock.getAsLong())
        );

        BillReceipt closed = service.closeVoteAndAdvance(
                ALPHA_ID, ballot.voteId(), context(ALPHA_ID, clock.getAsLong())
        );
        require(closed.applied() && closed.passed(),
                "a ballot with 1 of 1 required passes");
        require(closed.bill().isPresent(),
                "a passed ballot produces a bill");
        Bill bill = closed.bill().get();
        require(bill.state() == BillState.APPROVED,
                "a new bill is APPROVED");
        require(bill.proposalId().equals(proposal.proposal().proposalId()),
                "the bill is bound to the originating proposal");
        require(bill.recordRevision() == 1L,
                "a new bill starts at revision 1");
        require(repository(store).snapshot().bills().size() == 1,
                "exactly one bill exists");
        Proposal advanced = repository(store).snapshot().proposals()
                .get(proposal.proposal().proposalId());
        require(advanced.state() == BillState.APPROVED,
                "the proposal advances to APPROVED");
        require(repository(store).snapshot().transitions().stream()
                        .anyMatch(t -> t.trigger() == BillTransitionTrigger.VOTE_PASSED),
                "a VOTE_PASSED transition is recorded");
    }

    private static void testVoteRejectedNoBill() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(5_000);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID),
                new FakeInstitutionAccessService()
        );
        ProposalReceipt proposal = service.submitProposal(
                new ProposalDraft("Unpopular", NormLevel.ORGANIC, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        service.submitProposal(
                new ProposalDraft("Second", NormLevel.ORGANIC, "Body."),
                context(BRAVO_ID, clock.getAsLong())
        );
        VoteReceipt opened = service.openVote(
                ALPHA_ID, proposal.proposal().proposalId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        require(opened.vote().requiredThreshold() == 2L,
                "organic 2/3 of 2 rounds up to 2");
        service.castVote(
                ALPHA_ID, opened.vote().voteId(), VoteChoice.AGAINST,
                context(ALPHA_ID, clock.getAsLong())
        );
        service.castVote(
                BRAVO_ID, opened.vote().voteId(), VoteChoice.ABSTAIN,
                context(BRAVO_ID, clock.getAsLong())
        );
        BillReceipt closed = service.closeVoteAndAdvance(
                ALPHA_ID, opened.vote().voteId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        require(closed.applied() && !closed.passed(),
                "a ballot below threshold is rejected");
        require(closed.bill().isEmpty(),
                "a rejected ballot produces no bill");
        Proposal advanced = repository(store).snapshot().proposals()
                .get(proposal.proposal().proposalId());
        require(advanced.state() == BillState.REJECTED,
                "the proposal advances to REJECTED");
        require(repository(store).snapshot().transitions().stream()
                        .anyMatch(t -> t.trigger() == BillTransitionTrigger.VOTE_REJECTED),
                "a VOTE_REJECTED transition is recorded");
    }

    private static void testOneVotePerCitizen() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(6_000);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID),
                new FakeInstitutionAccessService()
        );
        ProposalReceipt proposal = service.submitProposal(
                new ProposalDraft("Law", NormLevel.ORDINARY, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        service.submitProposal(
                new ProposalDraft("Second", NormLevel.ORDINARY, "Body."),
                context(BRAVO_ID, clock.getAsLong())
        );
        VoteReceipt opened = service.openVote(
                ALPHA_ID, proposal.proposal().proposalId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        service.castVote(
                ALPHA_ID, opened.vote().voteId(), VoteChoice.FOR,
                context(ALPHA_ID, clock.getAsLong())
        );
        ParliamentUnavailableException duplicate = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.castVote(
                        ALPHA_ID, opened.vote().voteId(), VoteChoice.AGAINST,
                        context(ALPHA_ID, clock.getAsLong())
                ),
                "a citizen cannot vote twice on the same ballot"
        );
        require(duplicate.failureCode().equals(
                        ParliamentUnavailableException.CODE_ALREADY_VOTED),
                "a second vote reports ALREADY_VOTED");
        require(repository(store).snapshot().votes().get(opened.vote().voteId())
                        .votes().size() == 1,
                "exactly one citizen vote is stored");
    }

    private static void testFrozenRosterEligibility() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(7_000);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID, CHARLIE_ID),
                new FakeInstitutionAccessService()
        );
        ProposalReceipt proposal = service.submitProposal(
                new ProposalDraft("Law", NormLevel.ORDINARY, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        service.submitProposal(
                new ProposalDraft("Second", NormLevel.ORDINARY, "Body."),
                context(BRAVO_ID, clock.getAsLong())
        );
        // Charlie registers after the ballot opens (via a new proposal), so he
        // is NOT on the frozen roster of the first ballot.
        VoteReceipt opened = service.openVote(
                ALPHA_ID, proposal.proposal().proposalId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        require(opened.vote().frozenRosterCount() == 2L,
                "the frozen roster is fixed at open");
        service.submitProposal(
                new ProposalDraft("Late", NormLevel.ORDINARY, "Body."),
                context(CHARLIE_ID, clock.getAsLong())
        );
        ParliamentUnavailableException outside = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.castVote(
                        CHARLIE_ID, opened.vote().voteId(), VoteChoice.FOR,
                        context(CHARLIE_ID, clock.getAsLong())
                ),
                "a citizen outside the frozen roster cannot vote"
        );
        require(outside.failureCode().equals(
                        ParliamentUnavailableException.CODE_NOT_CITIZEN),
                "frozen-roster violation reports NOT_CITIZEN");
    }

    // ------------------------------------------------------------------
    // acceptance: closed state machine (§6)
    // ------------------------------------------------------------------

    private static void testStateMachineClosedNoIllegalJumps() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(8_000);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID),
                new FakeInstitutionAccessService()
        );
        ProposalReceipt proposal = service.submitProposal(
                new ProposalDraft("Law", NormLevel.ORDINARY, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );

        // Unknown proposal.
        ParliamentUnavailableException unknown = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.openVote(
                        ALPHA_ID, ProposalId.of(UUID.randomUUID()),
                        context(ALPHA_ID, clock.getAsLong())
                ),
                "opening a vote on an unknown proposal fails closed"
        );
        require(unknown.failureCode().equals(
                        ParliamentUnavailableException.CODE_PROPOSAL_NOT_FOUND),
                "unknown proposal reports PROPOSAL_NOT_FOUND");

        // A REVIEW proposal opens its ballot once.
        VoteReceipt opened = service.openVote(
                ALPHA_ID, proposal.proposal().proposalId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        require(opened.applied() && opened.vote().ballotState() == VoteBallotState.OPEN,
                "a REVIEW proposal opens a ballot");

        // Opening a second ballot on the same (now VOTING) proposal fails:
        // the closed state machine has no VOTING -> VOTING jump.
        ParliamentUnavailableException doubleOpen = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.openVote(
                        ALPHA_ID, proposal.proposal().proposalId(),
                        context(ALPHA_ID, clock.getAsLong())
                ),
                "a proposal already in VOTING cannot open another ballot"
        );
        require(doubleOpen.failureCode().equals(
                        ParliamentUnavailableException.CODE_ILLEGAL_TRANSITION),
                "double open reports ILLEGAL_TRANSITION");
        service.castVote(
                ALPHA_ID, opened.vote().voteId(), VoteChoice.FOR,
                context(ALPHA_ID, clock.getAsLong())
        );
        service.closeVoteAndAdvance(
                ALPHA_ID, opened.vote().voteId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        // The proposal is APPROVED; further mutation of the proposal state is
        // not reachable through any service method (closed machine).
        require(repository(store).snapshot().proposals()
                        .get(proposal.proposal().proposalId()).state()
                        == BillState.APPROVED,
                "proposal ends in APPROVED");
    }

    private static void testCloseIdempotentNoOp() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(9_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID), access
        );
        ProposalReceipt proposal = service.submitProposal(
                new ProposalDraft("Law", NormLevel.ORDINARY, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        VoteReceipt opened = service.openVote(
                ALPHA_ID, proposal.proposal().proposalId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        service.castVote(
                ALPHA_ID, opened.vote().voteId(), VoteChoice.FOR,
                context(ALPHA_ID, clock.getAsLong())
        );
        int commitsAfterFirstClose = store.commits();
        BillReceipt first = service.closeVoteAndAdvance(
                ALPHA_ID, opened.vote().voteId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        require(first.applied() && first.passed(),
                "the first close applies");
        int commitsAfterClose = store.commits();

        int validatesBeforeNoOp = access.validateCalls();
        BillReceipt second = service.closeVoteAndAdvance(
                BRAVO_ID, opened.vote().voteId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        require(!second.applied(),
                "a second close is an idempotent no-op");
        require(store.commits() == commitsAfterClose,
                "a no-op close commits nothing");
        require(access.validateCalls() == validatesBeforeNoOp,
                "a no-op close does not validate or consume an on-site context");
        require(second.bill().isPresent() && second.passed(),
                "the no-op close reports the existing bill");
        require(commitsAfterClose == commitsAfterFirstClose + 1,
                "the first close committed exactly one snapshot");
    }

    // ------------------------------------------------------------------
    // acceptance: no legal execution; no technical permission (§6)
    // ------------------------------------------------------------------

    private static void testNoLegalExecution() throws Exception {
        for (Method method : ParliamentService.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            require(!name.contains("execute") && !name.contains("enforce")
                            && !name.contains("apply") && !name.contains("judge")
                            && !name.contains("sentence") && !name.contains("fine")
                            && !name.contains("ban") && !name.contains("seize")
                            && !name.contains("deport") && !name.contains("arrest"),
                    "no legal-execution surface in ParliamentService: "
                            + method.getName());
        }
        for (Method method : ParliamentRepository.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            require(!name.contains("execute") && !name.contains("enforcelaw")
                            && !name.contains("judge") && !name.contains("sentence")
                            && !name.contains("fine") && !name.contains("ban")
                            && !name.contains("seize"),
                    "no legal-execution surface in ParliamentRepository: "
                            + method.getName());
        }
        // The parliament production code must not depend on other modules'
        // storage or business services (no economy/land/government/justice).
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path parliamentDirectory = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/server/parliament"
        );
        require(Files.isDirectory(parliamentDirectory),
                "Production parliament source directory must exist");
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(parliamentDirectory)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> source.append(read(path)).append('\n'));
        }
        String codeOnly = stripComments(source.toString());
        for (String forbidden : List.of(
                "server.economy", "server.land", "server.government",
                "server.justice", "server.court", "server.bank",
                "commitModuleData(\"economy\"", "commitModuleData(\"land\"",
                "commitModuleData(\"citizen\"", "commitModuleData(\"government\"",
                "commitModuleData(\"audit\"", "commitModuleData(\"playerdata\"",
                "getModuleData(\"economy\"", "getModuleData(\"land\"",
                "getModuleData(\"citizen\"", "getModuleData(\"government\""
        )) {
            require(!codeOnly.contains(forbidden),
                    "parliament production code must not touch other modules' "
                            + "storage or services: " + forbidden);
        }
    }

    private static void testNoTechnicalPermissionMapping() throws Exception {
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path parliamentDirectory = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/server/parliament"
        );
        require(Files.isDirectory(parliamentDirectory),
                "Production parliament source directory must exist");
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(parliamentDirectory)) {
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
                    "parliament production code must not map political office "
                            + "to technical permission: " + forbidden
            );
        }
    }

    // ------------------------------------------------------------------
    // acceptance: strict deterministic codec (§6)
    // ------------------------------------------------------------------

    private static void testStrictDeterministicCodec() {
        ParliamentNbtCodec codec = new ParliamentNbtCodec();
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(10_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        ParliamentService service = service(
                repository(store), clock,
                citizens(ALPHA_ID, BRAVO_ID, CHARLIE_ID), access
        );
        ProposalReceipt proposal = service.submitProposal(
                new ProposalDraft("Deterministic", NormLevel.ORDINARY, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        service.submitProposal(
                new ProposalDraft("Second", NormLevel.ORDINARY, "Body."),
                context(BRAVO_ID, clock.getAsLong())
        );
        service.submitProposal(
                new ProposalDraft("Third", NormLevel.ORDINARY, "Body."),
                context(CHARLIE_ID, clock.getAsLong())
        );
        VoteReceipt opened = service.openVote(
                ALPHA_ID, proposal.proposal().proposalId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        service.castVote(
                ALPHA_ID, opened.vote().voteId(), VoteChoice.FOR,
                context(ALPHA_ID, clock.getAsLong())
        );
        service.castVote(
                BRAVO_ID, opened.vote().voteId(), VoteChoice.FOR,
                context(BRAVO_ID, clock.getAsLong())
        );
        service.closeVoteAndAdvance(
                ALPHA_ID, opened.vote().voteId(),
                context(ALPHA_ID, clock.getAsLong())
        );

        ParliamentStoreSnapshot snapshot = repository(store).snapshot();
        CompoundTag first = codec.encode(snapshot);
        CompoundTag second = codec.encode(snapshot);
        require(java.util.Arrays.equals(nbtBytes(first), nbtBytes(second)),
                "same snapshot encodes to identical ordered bytes");
        require(codec.encodedSize(first) == codec.encodedSize(second),
                "same snapshot encodes to the same serialized size");
        ParliamentStoreSnapshot decoded = codec.decode(second);
        require(decoded.equals(snapshot),
                "decode(encode(snapshot)) equals the snapshot");
        require(decoded.citizenRoster().equals(snapshot.citizenRoster()),
                "the citizen roster round-trips");
        require(decoded.transitions().equals(snapshot.transitions()),
                "the transition ledger round-trips in order");
    }

    private static void testCorruptSnapshotFailClosed() {
        ParliamentNbtCodec codec = new ParliamentNbtCodec();
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(11_000);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID),
                new FakeInstitutionAccessService()
        );
        ProposalReceipt proposal = service.submitProposal(
                new ProposalDraft("Law", NormLevel.ORDINARY, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        ProposalId proposalId = proposal.proposal().proposalId();

        // A valid root decodes fine.
        CompoundTag root = baseParliamentRoot();
        root.getCompound("Proposals").put(proposalId.value().toString(),
                proposalTag(proposalId, "Law",
                        NormLevel.ORDINARY, BillState.REVIEW, ALPHA_ID));
        codec.decode(root);

        // Unknown store field.
        CompoundTag unknownField = baseParliamentRoot();
        unknownField.putString("Surprise", "x");
        expectDecodeFailure(codec, unknownField, "unknown store field fails closed");

        // Wrong NBT type for a required field.
        CompoundTag wrongType = baseParliamentRoot();
        wrongType.putString("StoreVersion", "one");
        expectDecodeFailure(codec, wrongType, "wrong type fails closed");

        // Newer store version.
        CompoundTag newVersion = baseParliamentRoot();
        newVersion.putInt("StoreVersion", 2);
        expectDecodeFailure(codec, newVersion, "newer store version fails closed");

        // Non-canonical proposal key.
        CompoundTag nonCanonical = baseParliamentRoot();
        CompoundTag proposalTag = proposalTag(
                proposalId, "Law", NormLevel.ORDINARY, BillState.REVIEW, ALPHA_ID
        );
        String upperKey = "ABCDEF00-0000-0000-0000-0000000000aa"
                .toUpperCase(java.util.Locale.ROOT);
        nonCanonical.getCompound("Proposals").put(upperKey, proposalTag);
        expectDecodeFailure(codec, nonCanonical,
                "non-canonical id key fails closed");

        // Dangling vote reference.
        CompoundTag danglingVote = baseParliamentRoot();
        danglingVote.getCompound("Votes").put(
                UUID.fromString("00000000-0000-0000-0000-0000000000aa").toString(),
                voteTag(VoteId.of(UUID.fromString(
                                "00000000-0000-0000-0000-0000000000aa")),
                        ProposalId.of(UUID.randomUUID()),
                        VoteBallotState.OPEN, 1L, 1L,
                        Set.of(ALPHA_ID), Map.of()));
        expectDecodeFailure(codec, danglingVote,
                "a vote referencing a missing proposal fails closed");

        // Dangling transition reference.
        CompoundTag danglingTransition = baseParliamentRoot();
        danglingTransition.getList("Transitions", Tag.TAG_COMPOUND).add(
                transitionTag(UUID.randomUUID(), ALPHA_ID,
                        BillTransitionTrigger.SUBMITTED, BillState.DRAFT, 1L)
        );
        expectDecodeFailure(codec, danglingTransition,
                "a transition referencing a missing proposal fails closed");

        // Frozen roster inconsistent with its count.
        CompoundTag badRoster = baseParliamentRoot();
        badRoster.getCompound("Proposals").put(proposalId.value().toString(),
                proposalTag(proposalId, "Law", NormLevel.ORDINARY,
                        BillState.VOTING, ALPHA_ID));
        badRoster.getCompound("Votes").put(
                UUID.fromString("00000000-0000-0000-0000-0000000000aa").toString(),
                voteTag(VoteId.of(UUID.fromString(
                                "00000000-0000-0000-0000-0000000000aa")),
                        proposalId, VoteBallotState.OPEN, 2L, 2L,
                        Set.of(ALPHA_ID), Map.of()));
        expectDecodeFailure(codec, badRoster,
                "a frozen roster inconsistent with its count fails closed");
    }

    private static void expectDecodeFailure(
            ParliamentNbtCodec codec,
            CompoundTag root,
            String message
    ) {
        try {
            codec.decode(root);
        } catch (ParliamentNbtException failure) {
            return;
        }
        throw new AssertionError(message);
    }

    // ------------------------------------------------------------------
    // acceptance: restart recovery (§6)
    // ------------------------------------------------------------------

    private static void testRestartPersistence() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(12_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID, BRAVO_ID), access
        );
        ProposalReceipt proposal = service.submitProposal(
                new ProposalDraft("Law", NormLevel.ORDINARY, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        service.submitProposal(
                new ProposalDraft("Second", NormLevel.ORDINARY, "Body."),
                context(BRAVO_ID, clock.getAsLong())
        );
        VoteReceipt opened = service.openVote(
                ALPHA_ID, proposal.proposal().proposalId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        service.castVote(
                ALPHA_ID, opened.vote().voteId(), VoteChoice.FOR,
                context(ALPHA_ID, clock.getAsLong())
        );
        long storeRevisionBefore = repository(store).snapshot().storeRevision();

        // Simulated restart: a fresh repository over the same persisted bytes.
        ParliamentRepository restarted = restartRepository(store);
        ParliamentService restartedService = service(
                restarted, clock, citizens(ALPHA_ID, BRAVO_ID), access
        );
        ParliamentStoreSnapshot recovered = restarted.snapshot();
        require(recovered.storeRevision() == storeRevisionBefore,
                "store revision survives restart");
        require(recovered.proposals().size() == 2,
                "proposals survive restart");
        require(recovered.votes().size() == 1,
                "ballots survive restart");
        require(recovered.citizenRoster().equals(Set.of(ALPHA_ID, BRAVO_ID)),
                "the citizen roster survives restart");
        require(recovered.transitions().size() == 5,
                "the transition ledger survives restart");

        // The restarted runtime can continue: the OPEN ballot still accepts
        // the remaining frozen-roster vote and can be closed.
        Vote vote = recovered.votes().get(opened.vote().voteId());
        require(vote.ballotState() == VoteBallotState.OPEN,
                "an OPEN ballot is still open after restart");
        VoteReceipt cast = restartedService.castVote(
                BRAVO_ID, opened.vote().voteId(), VoteChoice.FOR,
                context(BRAVO_ID, clock.getAsLong())
        );
        require(cast.vote().votesFor() == 2L,
                "the restarted ballot accepts the second vote");
        BillReceipt closed = restartedService.closeVoteAndAdvance(
                ALPHA_ID, opened.vote().voteId(),
                context(ALPHA_ID, clock.getAsLong())
        );
        require(closed.applied() && closed.passed(),
                "the restarted ballot closes and passes");
        require(restartedService.bill(closed.bill().get().billId()).isPresent(),
                "the bill survives restart");
    }

    // ------------------------------------------------------------------
    // acceptance: injection failure publishes nothing (§6)
    // ------------------------------------------------------------------

    private static void testInjectionFailureNoPublish() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(13_000);
        ParliamentService service = service(
                repository(store), clock, citizens(ALPHA_ID),
                new FakeInstitutionAccessService()
        );
        service.submitProposal(
                new ProposalDraft("Law", NormLevel.ORDINARY, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        long revisionBefore = repository(store).snapshot().storeRevision();

        store.setCommitFailureCode("INJECTED_FAILURE");
        ParliamentUnavailableException failure = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.submitProposal(
                        new ProposalDraft("Lost", NormLevel.ORDINARY, "Body."),
                        context(ALPHA_ID, clock.getAsLong())
                ),
                "a rejected durable commit must fail closed"
        );
        require(failure.failureCode().equals(
                        ParliamentUnavailableException.CODE_STORE_FAILURE),
                "store rejection reports STORE_FAILURE");
        require(repository(store).snapshot().storeRevision() == revisionBefore,
                "a failed commit publishes nothing (revision unchanged)");
        require(repository(store).snapshot().proposals().size() == 1,
                "a failed commit publishes nothing (no proposal)");
        require(repository(store).snapshot().citizenRoster().size() == 1,
                "a failed commit publishes nothing (roster unchanged)");

        // Store exception -> fail closed as well.
        store.setCommitException(new IllegalStateException("store down"));
        expectThrows(
                ParliamentUnavailableException.class,
                () -> service.submitProposal(
                        new ProposalDraft("Lost again", NormLevel.ORDINARY, "Body."),
                        context(ALPHA_ID, clock.getAsLong())
                ),
                "a store exception fails closed"
        );
        require(repository(store).snapshot().storeRevision() == revisionBefore,
                "a store exception publishes nothing");
    }

    // ------------------------------------------------------------------
    // acceptance: capacity (§6)
    // ------------------------------------------------------------------

    private static void testCapacityFailClosed() {
        ParliamentLimits tight = new ParliamentLimits(
                1, 1, 1, 16, 2, 8 * 1024 * 1024
        );
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(14_000);
        ParliamentService service = service(
                repository(store, tight), clock, citizens(ALPHA_ID),
                new FakeInstitutionAccessService()
        );
        service.submitProposal(
                new ProposalDraft("Only", NormLevel.ORDINARY, "Body."),
                context(ALPHA_ID, clock.getAsLong())
        );
        ParliamentUnavailableException capacity = expectThrows(
                ParliamentUnavailableException.class,
                () -> service.submitProposal(
                        new ProposalDraft("Over", NormLevel.ORDINARY, "Body."),
                        context(ALPHA_ID, clock.getAsLong())
                ),
                "capacity exhaustion fails closed"
        );
        require(capacity.failureCode().equals(
                        ParliamentUnavailableException.CODE_CAPACITY_EXCEEDED),
                "capacity exhaustion reports CAPACITY_EXCEEDED");
        require(repository(store).snapshot().proposals().size() == 1,
                "capacity rejection publishes nothing");
    }

    // ------------------------------------------------------------------
    // acceptance: no enumeration API (§6)
    // ------------------------------------------------------------------

    private static void testNoEnumerationApi() {
        for (Class<?> type : List.of(
                ParliamentService.class, ParliamentRepository.class
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
        MutableClock clock = new MutableClock(15_000);
        ParliamentService service = service(
                repository(store), clock,
                citizens(ALPHA_ID, BRAVO_ID, CHARLIE_ID, DELTA_ID),
                new FakeInstitutionAccessService()
        );
        for (int index = 0; index < 4; index++) {
            service.submitProposal(
                    new ProposalDraft(
                            "P" + index, NormLevel.ORDINARY, "body " + index
                    ),
                    context(citizenFor(index), clock.getAsLong())
            );
        }
        List<ProposalProjection> page = service.proposals(0, 3);
        require(page.size() == 3,
                "a projection is bounded by the requested limit");
        require(page.get(0).proposalSeq() == 1L
                        && page.get(1).proposalSeq() == 2L
                        && page.get(2).proposalSeq() == 3L,
                "projections are ordered by ascending sequence");
        List<ProposalProjection> next = service.proposals(3, 10);
        require(next.size() == 1 && next.get(0).proposalSeq() == 4L,
                "paging resumes strictly after the cursor");
        List<ProposalProjection> capped = service.proposals(0, 10_000);
        require(capped.size() == 4,
                "an over-large limit is capped by MAX_PROJECTION_SIZE");
    }

    // ------------------------------------------------------------------
    // acceptance: module contract (§6)
    // ------------------------------------------------------------------

    private static void testModuleContract() {
        require(ParliamentRepository.MODULE_DATA_KEY.equals("parliament"),
                "parliament owns exactly the approved namespace");
        require(ParliamentModule.MODULE_ID.value().equals("parliament"),
                "parliament module id is 'parliament'");
        ModuleDefinition definition = new ModuleDefinition(
                ParliamentModule.MODULE_ID,
                new ModuleMetadata("Parliament", "1.0.0", Optional.empty(),
                        Optional.empty()),
                Set.of(
                        PlayerDataModule.MODULE_ID,
                        CitizenModule.MODULE_ID,
                        AuditModule.MODULE_ID,
                        InstitutionAccessModule.MODULE_ID
                ),
                Set.of(),
                90,
                ParliamentModule::new
        );
        require(definition.requiredDependencies().equals(Set.of(
                        PlayerDataModule.MODULE_ID,
                        CitizenModule.MODULE_ID,
                        AuditModule.MODULE_ID,
                        InstitutionAccessModule.MODULE_ID)),
                "parliament depends only on player-data, citizen, audit, and "
                        + "institution-access");
        for (ModuleId dependency : definition.requiredDependencies()) {
            String value = dependency.value();
            require(!value.contains("emg") && !value.contains("emergency")
                            && !value.contains("economy") && !value.contains("justice")
                            && !value.contains("land") && !value.contains("court")
                            && !value.contains("bank"),
                    "parliament never depends on later-phase or other-pillar "
                            + "namespaces: " + value);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static ParliamentRepository repository(ParliamentStore store) {
        return new ParliamentRepository(
                store, new ParliamentNbtCodec(), ParliamentLimits.DEFAULT
        );
    }

    private static ParliamentRepository repository(
            ParliamentStore store,
            ParliamentLimits limits
    ) {
        return new ParliamentRepository(store, new ParliamentNbtCodec(), limits);
    }

    private static ParliamentRepository restartRepository(
            SavedDataBackedTestStore store
    ) {
        return repository(store.restart());
    }

    private static ParliamentService service(
            ParliamentRepository repository,
            LongSupplier clock,
            FakeParliamentCitizenDirectory citizens,
            FakeInstitutionAccessService access
    ) {
        return new DefaultParliamentService(
                repository,
                clock,
                new SequentialIdSource(),
                citizens,
                access,
                null
        );
    }

    private static FakeParliamentCitizenDirectory citizens(UUID... players) {
        FakeParliamentCitizenDirectory directory =
                new FakeParliamentCitizenDirectory();
        for (UUID player : players) {
            directory.addCitizen(player);
        }
        return directory;
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

    // ------------------------------------------------------------------
    // NBT builders for corruption tests
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
        return root;
    }

    private static CompoundTag proposalTag(
            ProposalId proposalId,
            String title,
            NormLevel level,
            BillState state,
            UUID proposer
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ProposalVersion", 1);
        tag.putUUID("ProposalId", proposalId.value());
        tag.putLong("ProposalSeq", 1L);
        tag.putString("Title", title);
        tag.putString("NormLevel", level.name());
        tag.putString("FullText", "Body.");
        tag.putUUID("ProposerRef", proposer);
        tag.putString("State", state.name());
        tag.putLong("CreatedAt", 1_000L);
        tag.putLong("RecordRevision", 1L);
        return tag;
    }

    private static CompoundTag voteTag(
            VoteId voteId,
            ProposalId proposalId,
            VoteBallotState ballotState,
            long threshold,
            long rosterCount,
            Set<UUID> frozenRoster,
            Map<UUID, VoteChoice> votes
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("VoteVersion", 1);
        tag.putUUID("VoteId", voteId.value());
        tag.putUUID("ProposalId", proposalId.value());
        tag.putString("BallotState", ballotState.name());
        tag.putLong("VotesFor", 0L);
        tag.putLong("VotesAgainst", 0L);
        tag.putLong("VotesAbstain", 0L);
        tag.putLong("RequiredThreshold", threshold);
        tag.putLong("FrozenRosterCount", rosterCount);
        ListTag roster = new ListTag();
        frozenRoster.stream().sorted().forEach(uuid -> roster.add(uuidTag(uuid)));
        tag.put("FrozenRoster", roster);
        tag.putLong("OpenedAt", 1_000L);
        tag.putLong("VoteRevision", 1L);
        CompoundTag ballotVotes = new CompoundTag();
        votes.forEach((voter, choice) ->
                ballotVotes.putString(voter.toString(), choice.name()));
        tag.put("Votes", ballotVotes);
        return tag;
    }

    private static CompoundTag transitionTag(
            UUID proposalId,
            UUID actor,
            BillTransitionTrigger trigger,
            BillState after,
            long revision
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("TransitionVersion", 1);
        tag.putUUID("ProposalId", proposalId);
        tag.putUUID("Actor", actor);
        tag.putLong("AtMillis", 1_000L);
        tag.putString("Trigger", trigger.name());
        tag.putString("After", after.name());
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

    private static final class SavedDataBackedTestStore implements ParliamentStore {
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
            return savedData.getModuleData(ParliamentRepository.MODULE_DATA_KEY).copy();
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

        private void setCommitException(RuntimeException exception) {
            this.commitException = exception;
        }

        private void putRaw(CompoundTag raw) {
            savedData.putModuleData(ParliamentRepository.MODULE_DATA_KEY, raw);
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
     * parliament module consumes the boundary at mutation time only.
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

        private int validateCalls() {
            return validateCalls;
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
                    "not part of the parliament test double"
            );
        }
    }
}
