package com.fontainerepublic.server.government;

import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.core.ModSavedData;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.server.government.api.AppointmentChangeKind;
import com.fontainerepublic.server.government.api.AppointmentReceipt;
import com.fontainerepublic.server.government.api.CreatePositionRequest;
import com.fontainerepublic.server.government.api.GovernmentService;
import com.fontainerepublic.server.government.api.MinistryChangeKind;
import com.fontainerepublic.server.government.api.MinistryDraft;
import com.fontainerepublic.server.government.api.MinistryProjection;
import com.fontainerepublic.server.government.api.MinistryReceipt;
import com.fontainerepublic.server.government.api.PositionChangeKind;
import com.fontainerepublic.server.government.api.PositionProjection;
import com.fontainerepublic.server.government.api.PositionReceipt;
import com.fontainerepublic.server.government.model.GovernmentPosition;
import com.fontainerepublic.server.government.model.Ministry;
import com.fontainerepublic.server.government.model.MinistryId;
import com.fontainerepublic.server.government.model.MinistryState;
import com.fontainerepublic.server.government.model.Office;
import com.fontainerepublic.server.government.model.PositionId;
import com.fontainerepublic.server.government.model.PositionState;
import com.fontainerepublic.server.government.persistence.GovernmentIdSource;
import com.fontainerepublic.server.government.persistence.GovernmentLimits;
import com.fontainerepublic.server.government.persistence.GovernmentNbtCodec;
import com.fontainerepublic.server.government.persistence.GovernmentNbtException;
import com.fontainerepublic.server.government.persistence.GovernmentRepository;
import com.fontainerepublic.server.government.persistence.GovernmentStore;
import com.fontainerepublic.server.government.persistence.GovernmentStoreSnapshot;
import com.fontainerepublic.server.government.persistence.GovernmentUnavailableException;
import com.fontainerepublic.server.government.service.DefaultGovernmentService;
import com.fontainerepublic.server.government.service.HolderDirectory;
import com.fontainerepublic.server.institutionaccess.api.FacilityReceipt;
import com.fontainerepublic.server.institutionaccess.api.FacilityRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.api.ValidationResult;
import com.fontainerepublic.server.institutionaccess.api.ZoneReceipt;
import com.fontainerepublic.server.institutionaccess.api.ZoneRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.Facility;
import com.fontainerepublic.server.institutionaccess.model.InstitutionType;
import com.fontainerepublic.server.institutionaccess.model.WorkflowKind;
import com.fontainerepublic.server.institutionaccess.model.Zone;
import com.fontainerepublic.server.institutionaccess.model.ZoneId;
import com.fontainerepublic.server.institutionaccess.model.ZoneKind;
import com.fontainerepublic.server.institutionaccess.model.ZoneRegion;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessUnavailableException;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.OwnerReferenceKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

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
 * Dependency-free validation entry point for FR-GOV-001 (government module).
 * Exercises the FR-GOV-001-A §6 acceptance matrix with an injectable store
 * and SavedData-backed restart simulation: single-snapshot gated ministry and
 * position creation, on-site-gated appoint/dismiss, holder resolution through
 * services, no rank/office-to-permission mapping, four-pillar boundary, strict
 * deterministic codec with referential integrity, restart recovery, injection
 * failure with no publish, and no bulk enumeration API.
 */
public final class GovernmentFoundationTestMain {

    private static final String PROJECT_DIR_PROPERTY = "fontainerepublic.projectDir";
    private static final String DIMENSION = "minecraft:overworld";

    private static final UUID ALPHA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private GovernmentFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testMinistryCreateSingleSnapshot();
        testPositionCreateSingleSnapshot();
        testAppointDismissOnSiteGated();
        testAppointPreconditions();
        testDismissNoOpAndPreconditions();
        testHolderResolution();
        testNoTechnicalPermissionMapping();
        testFourPillarBoundary();
        testStrictDeterministicCodec();
        testCorruptSnapshotFailClosed();
        testRestartPersistence();
        testNoEnumerationApi();
        testCapacityFailClosed();
        testStoreFailureAtomicity();
        testBoundedProjections();
        testModuleContract();
        System.out.println("[FR-GOV-001] Government foundation validation passed");
    }

    // ------------------------------------------------------------------
    // acceptance: ministry creation — one snapshot, one revision (§6)
    // ------------------------------------------------------------------

    private static void testMinistryCreateSingleSnapshot() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        GovernmentRepository repository = repository(store);
        GovernmentService service = service(repository, clock, holders(ALPHA_ID));

        int commitsBefore = store.commitCount();
        MinistryReceipt receipt = service.createMinistry(
                ALPHA_ID, new MinistryDraft("Ministry of Justice")
        );
        require(receipt.applied(), "ministry creation is applied");
        require(receipt.kind() == MinistryChangeKind.MINISTRY_CREATED,
                "receipt kind is MINISTRY_CREATED");
        require(receipt.ministry().state() == MinistryState.ACTIVE,
                "a new ministry is ACTIVE");
        require(receipt.ministry().ministryRevision() == 1,
                "a new ministry starts at revision 1");
        require(receipt.ministry().name().equals("Ministry of Justice"),
                "the requested name was applied");
        require(repository.snapshot().storeRevision() == 1L,
                "creation commits exactly one store revision");
        require(store.commitCount() == commitsBefore + 1,
                "creation commits exactly one snapshot");

        MinistryReceipt trimmed = service.createMinistry(
                ALPHA_ID, new MinistryDraft("  Ministry of Culture  ")
        );
        require(trimmed.ministry().name().equals("Ministry of Culture"),
                "names are normalized (trimmed) at the boundary");

        // Bounded name: rejected at draft construction (fail-fast).
        expectThrows(
                IllegalArgumentException.class,
                () -> new MinistryDraft("x".repeat(65)),
                "an over-long name is rejected at draft construction"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> new MinistryDraft("   "),
                "a blank name is rejected at draft construction"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: position creation — one snapshot, one revision (§6)
    // ------------------------------------------------------------------

    private static void testPositionCreateSingleSnapshot() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(2_000);
        GovernmentRepository repository = repository(store);
        GovernmentService service = service(repository, clock, holders(ALPHA_ID));
        Ministry ministry = service.createMinistry(
                ALPHA_ID, new MinistryDraft("Ministry of Culture")
        ).ministry();

        int commitsBefore = store.commitCount();
        PositionReceipt receipt = service.createPosition(
                ALPHA_ID,
                new CreatePositionRequest(ministry.ministryId(), "Minister of Culture")
        );
        require(receipt.applied(), "position creation is applied");
        require(receipt.kind() == PositionChangeKind.POSITION_CREATED,
                "receipt kind is POSITION_CREATED");
        require(receipt.position().state() == PositionState.VACANT,
                "a new position is VACANT");
        require(receipt.position().positionRevision() == 1,
                "a new position starts at revision 1");
        require(receipt.position().holderRef().isEmpty(),
                "a VACANT position has no holder");
        require(receipt.position().ministryId().equals(ministry.ministryId()),
                "the position is bound to the requested ministry");
        require(repository.snapshot().storeRevision() == 2L,
                "creation commits exactly one store revision");
        require(store.commitCount() == commitsBefore + 1,
                "creation commits exactly one snapshot");

        // Position creation requires an existing ministry.
        GovernmentUnavailableException missingMinistry = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.createPosition(
                        ALPHA_ID,
                        new CreatePositionRequest(
                                MinistryId.of(UUID.fromString(
                                        "00000000-0000-0000-0000-0000000000aa")),
                                "Minister of Anything"
                        )
                ),
                "a position bound to a missing ministry is rejected"
        );
        require(missingMinistry.failureCode().equals(
                        GovernmentUnavailableException.CODE_MINISTRY_NOT_FOUND),
                "missing ministry carries the stable code");
    }

    // ------------------------------------------------------------------
    // acceptance: appoint/dismiss — on-site gated, one snapshot (§6)
    // ------------------------------------------------------------------

    private static void testAppointDismissOnSiteGated() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(3_000);
        GovernmentRepository repository = repository(store);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        GovernmentService service = service(
                repository, clock, holders(ALPHA_ID, BRAVO_ID), access
        );
        MinistryId ministry = service.createMinistry(
                ALPHA_ID, new MinistryDraft("Ministry of Culture")
        ).ministry().ministryId();
        PositionId position = service.createPosition(
                ALPHA_ID,
                new CreatePositionRequest(ministry, "Minister of Culture")
        ).position().positionId();

        // No on-site context: appointment is rejected at the mutation boundary.
        GovernmentUnavailableException noContext = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.appoint(
                        ALPHA_ID, position, OwnerReference.forPlayer(BRAVO_ID), null
                ),
                "an appointment without a context is rejected"
        );
        require(noContext.failureCode().equals(
                        GovernmentUnavailableException.CODE_ON_SITE_CONTEXT_INVALID),
                "missing context carries the on-site code");

        // Invalid context: rejected.
        OnSiteContext invalidContext = context(clock.getAsLong());
        access.setResult(ValidationResult.invalid(ValidationResult.REASON_EXPIRED));
        GovernmentUnavailableException invalid = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.appoint(
                        ALPHA_ID, position, OwnerReference.forPlayer(BRAVO_ID),
                        invalidContext
                ),
                "an invalid on-site context rejects the appointment"
        );
        require(invalid.failureCode().equals(
                        GovernmentUnavailableException.CODE_ON_SITE_CONTEXT_INVALID),
                "invalid context carries the on-site code");
        require(access.validateCalls() == 1,
                "the final mutation boundary validates the context exactly once");
        require(access.lastCapability() == CapabilityClass.ONSITE_OFFICIAL_DUTY,
                "the mutation boundary validates with ONSITE_OFFICIAL_DUTY");
        require(repository.positionCount() == 1,
                "a rejected appointment publishes no position change");
        require(repository.snapshot().storeRevision() == 2L,
                "a rejected appointment advances no revision");

        // Valid context: appointment commits one snapshot.
        access.setResult(ValidationResult.ok());
        int commitsBefore = store.commitCount();
        AppointmentReceipt appoint = service.appoint(
                ALPHA_ID, position, OwnerReference.forPlayer(BRAVO_ID),
                context(clock.getAsLong())
        );
        require(appoint.applied(), "a valid on-site appointment is applied");
        require(appoint.kind() == AppointmentChangeKind.APPOINTED,
                "receipt kind is APPOINTED");
        require(appoint.position().state() == PositionState.FILLED,
                "the position becomes FILLED");
        require(appoint.position().holderRef()
                        .map(holder -> holder.equals(OwnerReference.forPlayer(BRAVO_ID)))
                        .orElse(false),
                "the position carries the appointed holder reference");
        require(appoint.position().positionRevision() == 2,
                "position revision +1 exactly once");
        require(appoint.office().isPresent(), "the appointment created an office");
        Office office = appoint.office().orElseThrow();
        require(office.current(), "the new office is current");
        require(office.holderRef().equals(OwnerReference.forPlayer(BRAVO_ID)),
                "the office carries the holder reference");
        require(office.assignedAt() == 3_000L, "assignedAt is the server clock");
        require(office.officeRevision() == 1, "the new office starts at revision 1");
        require(repository.snapshot().storeRevision() == 3L,
                "appointment commits exactly one store revision");
        require(store.commitCount() == commitsBefore + 1,
                "appointment commits exactly one snapshot");

        // currentOffice resolves.
        require(service.currentOffice(position)
                        .map(Office::holderRef)
                        .map(holder -> holder.equals(OwnerReference.forPlayer(BRAVO_ID)))
                        .orElse(false),
                "currentOffice resolves the appointed holder");

        // Dismissal without a valid context is rejected.
        access.setResult(ValidationResult.invalid(ValidationResult.REASON_CONSUMED));
        GovernmentUnavailableException dismissedInvalid = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.dismiss(
                        ALPHA_ID, position, "term complete", context(clock.getAsLong())
                ),
                "a dismissal without a valid context is rejected"
        );
        require(dismissedInvalid.failureCode().equals(
                        GovernmentUnavailableException.CODE_ON_SITE_CONTEXT_INVALID),
                "invalid dismissal context carries the on-site code");
        require(repository.findPosition(position).orElseThrow().state()
                        == PositionState.FILLED,
                "a rejected dismissal leaves the position FILLED");

        // Valid dismissal: one snapshot, position VACANT, office revoked.
        access.setResult(ValidationResult.ok());
        int commitsBeforeDismiss = store.commitCount();
        AppointmentReceipt dismiss = service.dismiss(
                ALPHA_ID, position, "term complete", context(clock.getAsLong())
        );
        require(dismiss.applied(), "a valid on-site dismissal is applied");
        require(dismiss.kind() == AppointmentChangeKind.DISMISSED,
                "receipt kind is DISMISSED");
        require(dismiss.position().state() == PositionState.VACANT,
                "the position becomes VACANT");
        require(dismiss.position().holderRef().isEmpty(),
                "the position no longer carries a holder");
        require(dismiss.position().positionRevision() == 3,
                "position revision +1 exactly once");
        require(dismiss.office().map(Office::current).orElse(true) == false,
                "the office is revoked after dismissal");
        require(dismiss.office().map(Office::officeRevision).orElse(-1L) == 2,
                "office revision +1 exactly once");
        require(repository.snapshot().storeRevision() == 4L,
                "dismissal commits exactly one store revision");
        require(store.commitCount() == commitsBeforeDismiss + 1,
                "dismissal commits exactly one snapshot");
        require(service.currentOffice(position).isEmpty(),
                "a revoked office is no longer current");

        // Re-appointment after dismissal creates a fresh current office.
        AppointmentReceipt reappoint = service.appoint(
                ALPHA_ID, position, OwnerReference.forPlayer(ALPHA_ID),
                context(clock.getAsLong())
        );
        require(reappoint.applied(), "re-appointment after dismissal is applied");
        require(reappoint.position().holderRef()
                        .map(holder -> holder.equals(OwnerReference.forPlayer(ALPHA_ID)))
                        .orElse(false),
                "the re-appointed holder is recorded");
        require(reappoint.office().map(Office::current).orElse(false),
                "the re-appointment creates a current office");
    }

    // ------------------------------------------------------------------
    // acceptance: appointment preconditions fail closed (§6)
    // ------------------------------------------------------------------

    private static void testAppointPreconditions() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(4_000);
        GovernmentRepository repository = repository(store);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        GovernmentService service = service(
                repository, clock, holders(ALPHA_ID, BRAVO_ID), access
        );
        MinistryId ministry = service.createMinistry(
                ALPHA_ID, new MinistryDraft("Ministry of Culture")
        ).ministry().ministryId();
        PositionId position = service.createPosition(
                ALPHA_ID,
                new CreatePositionRequest(ministry, "Minister of Culture")
        ).position().positionId();

        // Actor without a PlayerData record.
        GovernmentUnavailableException unknownActor = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.createMinistry(
                        UUID.fromString("00000000-0000-0000-0000-000000000099"),
                        new MinistryDraft("Foreign Ministry")
                ),
                "an unprovisioned actor cannot create ministries"
        );
        require(unknownActor.failureCode().equals(
                        GovernmentUnavailableException.CODE_PLAYER_NOT_PROVISIONED),
                "unprovisioned actor carries the stable code");

        // Holder with a record but no active subject.
        FakeHolderDirectory strictHolders = holders(ALPHA_ID);
        strictHolders.addWithoutSubject(BRAVO_ID);
        GovernmentService strictService = service(
                repository, clock, strictHolders, access
        );
        GovernmentUnavailableException invalidHolder = expectThrows(
                GovernmentUnavailableException.class,
                () -> strictService.appoint(
                        ALPHA_ID, position, OwnerReference.forPlayer(BRAVO_ID),
                        context(clock.getAsLong())
                ),
                "a holder without an active subject is rejected"
        );
        require(invalidHolder.failureCode().equals(
                        GovernmentUnavailableException.CODE_INVALID_HOLDER),
                "invalid holder carries the stable code");

        // OFFICE_ID holder kind is not supported in Alpha.
        GovernmentUnavailableException officeHolder = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.appoint(
                        ALPHA_ID, position, OwnerReference.HYDRO_ARCHON_OFFICE,
                        context(clock.getAsLong())
                ),
                "an OFFICE_ID holder is not supported in Alpha"
        );
        require(officeHolder.failureCode().equals(
                        GovernmentUnavailableException.CODE_UNSUPPORTED_HOLDER_KIND),
                "unsupported holder kind carries the stable code");

        // Unknown position.
        GovernmentUnavailableException unknownPosition = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.appoint(
                        ALPHA_ID,
                        PositionId.of(UUID.fromString(
                                "00000000-0000-0000-0000-0000000000bb")),
                        OwnerReference.forPlayer(BRAVO_ID), context(clock.getAsLong())
                ),
                "appointing an unknown position is rejected"
        );
        require(unknownPosition.failureCode().equals(
                        GovernmentUnavailableException.CODE_POSITION_NOT_FOUND),
                "unknown position carries the stable code");

        // Appointing a filled position is rejected (dismiss first).
        service.appoint(ALPHA_ID, position, OwnerReference.forPlayer(BRAVO_ID),
                context(clock.getAsLong()));
        GovernmentUnavailableException filled = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.appoint(
                        ALPHA_ID, position, OwnerReference.forPlayer(ALPHA_ID),
                        context(clock.getAsLong())
                ),
                "appointing a filled position is rejected"
        );
        require(filled.failureCode().equals(
                        GovernmentUnavailableException.CODE_POSITION_FILLED),
                "filled position carries the stable code");

        // Validation happens before the mutation: no commit occurred.
        require(repository.snapshot().storeRevision() == 3L,
                "rejected appointments advance no revision");
    }

    // ------------------------------------------------------------------
    // acceptance: dismissal no-op and reason bounds (§6)
    // ------------------------------------------------------------------

    private static void testDismissNoOpAndPreconditions() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(5_000);
        GovernmentRepository repository = repository(store);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        GovernmentService service = service(
                repository, clock, holders(ALPHA_ID, BRAVO_ID), access
        );
        MinistryId ministry = service.createMinistry(
                ALPHA_ID, new MinistryDraft("Ministry of Culture")
        ).ministry().ministryId();
        PositionId neverAppointed = service.createPosition(
                ALPHA_ID,
                new CreatePositionRequest(ministry, "Deputy Minister")
        ).position().positionId();

        // Dismissing a never-appointed VACANT position is an idempotent no-op.
        int commitsBefore = store.commitCount();
        int validatesBefore = access.validateCalls();
        AppointmentReceipt noop = service.dismiss(
                ALPHA_ID, neverAppointed, "no reason", context(clock.getAsLong())
        );
        require(!noop.applied(), "dismissing a VACANT position is an idempotent no-op");
        require(noop.office().isEmpty(),
                "a never-appointed position has no office record");
        require(store.commitCount() == commitsBefore, "a no-op commits nothing");
        require(access.validateCalls() == validatesBefore,
                "a no-op does not consume the on-site boundary");

        // Blank reason is rejected.
        GovernmentUnavailableException blank = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.dismiss(
                        ALPHA_ID, neverAppointed, "   ", context(clock.getAsLong())
                ),
                "a blank dismissal reason is rejected"
        );
        require(blank.failureCode().equals(
                        GovernmentUnavailableException.CODE_INVALID_REQUEST),
                "blank reason carries the stable code");

        // Over-long reason is rejected.
        GovernmentUnavailableException longReason = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.dismiss(
                        ALPHA_ID, neverAppointed, "x".repeat(129), context(clock.getAsLong())
                ),
                "an over-long dismissal reason is rejected"
        );
        require(longReason.failureCode().equals(
                        GovernmentUnavailableException.CODE_INVALID_REQUEST),
                "over-long reason carries the stable code");

        // Unknown position.
        GovernmentUnavailableException unknownPosition = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.dismiss(
                        ALPHA_ID,
                        PositionId.of(UUID.fromString(
                                "00000000-0000-0000-0000-0000000000cc")),
                        "reason", context(clock.getAsLong())
                ),
                "dismissing an unknown position is rejected"
        );
        require(unknownPosition.failureCode().equals(
                        GovernmentUnavailableException.CODE_POSITION_NOT_FOUND),
                "unknown position carries the stable code");
    }

    // ------------------------------------------------------------------
    // acceptance: holder resolution through services (§6)
    // ------------------------------------------------------------------

    private static void testHolderResolution() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(6_000);
        GovernmentRepository repository = repository(store);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        FakeHolderDirectory holders = holders(ALPHA_ID, BRAVO_ID);
        GovernmentService service = service(repository, clock, holders, access);
        MinistryId ministry = service.createMinistry(
                ALPHA_ID, new MinistryDraft("Ministry of Culture")
        ).ministry().ministryId();
        PositionId position = service.createPosition(
                ALPHA_ID,
                new CreatePositionRequest(ministry, "Minister of Culture")
        ).position().positionId();

        service.appoint(ALPHA_ID, position, OwnerReference.forPlayer(BRAVO_ID),
                context(clock.getAsLong()));
        GovernmentPosition filled = repository.requirePosition(position);
        require(filled.holderRef().isPresent(), "the position carries a holder");
        OwnerReference holder = filled.holderRef().orElseThrow();
        require(holder.kind() == OwnerReferenceKind.PLAYER_UUID,
                "the holder is a PLAYER_UUID reference");
        require(holder.ownerId().equals(BRAVO_ID.toString()),
                "the holder reference stores the canonical UUID");

        // The persisted form carries the typed reference, never a game name.
        CompoundTag encoded = new GovernmentNbtCodec().encode(repository.snapshot());
        String nbtText = encoded.toString();
        require(!nbtText.contains("gameName") && !nbtText.contains("playerName"),
                "the persisted form never stores a game name");
        require(nbtText.contains("PLAYER_UUID"), "the persisted form carries the kind");

        // Resolution fails closed when the directory is unavailable.
        holders.setAvailable(false);
        GovernmentUnavailableException unavailable = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.createMinistry(
                        ALPHA_ID, new MinistryDraft("Foreign Ministry")
                ),
                "an unavailable holder directory fails closed"
        );
        require(unavailable.failureCode().equals(
                        GovernmentUnavailableException.CODE_HOLDER_DIRECTORY_UNAVAILABLE),
                "unavailable directory carries the stable code");
    }

    // ------------------------------------------------------------------
    // acceptance: no rank/office-to-permission mapping (§6)
    // ------------------------------------------------------------------

    private static void testNoTechnicalPermissionMapping() throws Exception {
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path governmentDirectory = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/server/government"
        );
        require(Files.isDirectory(governmentDirectory),
                "Production government source directory must exist");
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(governmentDirectory)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> source.append(read(path)).append('\n'));
        }
        String codeOnly = stripComments(source.toString());
        for (String forbidden : List.of(
                "isOp", "opLevel", "permissionLevel", "getPermission",
                "bypass", "canBypass", "sudo", "setOp", "Commands.OP",
                "rank", "GOD", "CitizenRank", "Permissions", "hasPermission"
        )) {
            require(
                    !codeOnly.contains(forbidden),
                    "government production code must not map office to technical "
                            + "permission: " + forbidden
            );
        }

        // No hard-coded coordinates either (spatial data comes from FR-LAND /
        // FR-INST-002 read-only).
        for (String forbidden : List.of(
                "new BlockPos(", "ChunkPos", "BlockPos.containing", "new Vec3i("
        )) {
            require(
                    !codeOnly.contains(forbidden),
                    "government production code must not hard-code coordinates: "
                            + forbidden
            );
        }
    }

    // ------------------------------------------------------------------
    // acceptance: four-pillar boundary (§6)
    // ------------------------------------------------------------------

    private static void testFourPillarBoundary() {
        for (Method method : GovernmentService.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            require(!name.contains("legislate") && !name.contains("law")
                            && !name.contains("bill") && !name.contains("parliament"),
                    "no legislative authority in GovernmentService: " + method.getName());
            require(!name.contains("judge") && !name.contains("court")
                            && !name.contains("case") && !name.contains("sentence")
                            && !name.contains("verdict"),
                    "no judicial authority in GovernmentService: " + method.getName());
            require(!name.contains("treasury") && !name.contains("bank")
                            && !name.contains("fiscal") && !name.contains("budget")
                            && !name.contains("tax") && !name.contains("spend")
                            && !name.contains("balance") && !name.contains("issue")
                            && !name.contains("reclaim") && !name.contains("coin"),
                    "no fiscal authority in GovernmentService: " + method.getName());
            require(!name.contains("grantpermission") && !name.contains("setop")
                            && !name.contains("rank")
                            && !name.contains("permission"),
                    "no technical-permission surface in GovernmentService: "
                            + method.getName());
        }
        for (Method method : GovernmentRepository.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            require(!name.contains("legislate") && !name.contains("judge")
                            && !name.contains("fiscal") && !name.contains("treasury")
                            && !name.contains("permission") && !name.contains("setop"),
                    "no out-of-pillar authority in GovernmentRepository: "
                            + method.getName());
        }
    }

    // ------------------------------------------------------------------
    // acceptance: strict deterministic codec (§6)
    // ------------------------------------------------------------------

    private static void testStrictDeterministicCodec() {
        GovernmentNbtCodec codec = new GovernmentNbtCodec();
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(7_000);
        GovernmentRepository repository = repository(store);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        GovernmentService service = service(
                repository, clock, holders(ALPHA_ID, BRAVO_ID), access
        );
        MinistryId ministry = service.createMinistry(
                ALPHA_ID, new MinistryDraft("Ministry of Culture")
        ).ministry().ministryId();
        PositionId position = service.createPosition(
                ALPHA_ID,
                new CreatePositionRequest(ministry, "Minister of Culture")
        ).position().positionId();
        service.appoint(ALPHA_ID, position, OwnerReference.forPlayer(BRAVO_ID),
                context(clock.getAsLong()));

        GovernmentStoreSnapshot snapshot = repository.snapshot();
        CompoundTag first = codec.encode(snapshot);
        CompoundTag second = codec.encode(snapshot);
        require(java.util.Arrays.equals(nbtBytes(first), nbtBytes(second)),
                "same snapshot encodes to identical ordered bytes");
        require(codec.encodedSize(first) == codec.encodedSize(second),
                "same snapshot encodes to the same serialized size");
        require(codec.decode(second).equals(snapshot),
                "decode(encode(snapshot)) equals the snapshot");
        require(codec.decode(codec.encode(snapshot))
                        .equals(codec.decode(store.load())),
                "persisted form decodes equivalently to the in-memory snapshot");
        require(codec.encodedSize(first) > 0, "encoded snapshot carries real payload bytes");
    }

    // ------------------------------------------------------------------
    // acceptance: corrupt snapshot fails closed (§6)
    // ------------------------------------------------------------------

    private static void testCorruptSnapshotFailClosed() {
        GovernmentNbtCodec codec = new GovernmentNbtCodec();

        // Unknown store field.
        SavedDataBackedTestStore unknownStoreField = new SavedDataBackedTestStore();
        CompoundTag unknownRoot = baseRoot();
        unknownRoot.putString("Surprise", "x");
        unknownStoreField.putRaw(unknownRoot);
        expectThrows(
                GovernmentNbtException.class,
                () -> repository(unknownStoreField),
                "unknown store field rejects the load"
        );

        // Newer store version.
        SavedDataBackedTestStore newerStore = new SavedDataBackedTestStore();
        CompoundTag newerRoot = baseRoot();
        newerRoot.putInt("StoreVersion", 99);
        newerStore.putRaw(newerRoot);
        expectThrows(
                GovernmentNbtException.class,
                () -> repository(newerStore),
                "a newer store version is rejected fail-closed"
        );

        // Position bound to a missing ministry.
        SavedDataBackedTestStore danglingPosition = new SavedDataBackedTestStore();
        CompoundTag danglingRoot = baseRoot();
        CompoundTag positions = new CompoundTag();
        positions.put(
                "00000000-0000-0000-0000-000000000011",
                basePositionTag(
                        PositionId.of(UUID.fromString(
                                "00000000-0000-0000-0000-000000000011")),
                        MinistryId.of(UUID.fromString(
                                "00000000-0000-0000-0000-0000000000ff")),
                        "Rogue Minister",
                        "VACANT"
                )
        );
        danglingRoot.put("Positions", positions);
        danglingPosition.putRaw(danglingRoot);
        expectThrows(
                GovernmentNbtException.class,
                () -> repository(danglingPosition),
                "a position bound to a missing ministry rejects the load"
        );

        // Positions key does not match the record position id.
        SavedDataBackedTestStore mismatch = new SavedDataBackedTestStore();
        CompoundTag mismatchRoot = baseRoot();
        CompoundTag mismatchPositions = new CompoundTag();
        mismatchPositions.put(
                "00000000-0000-0000-0000-000000000011",
                basePositionTag(
                        PositionId.of(UUID.fromString(
                                "00000000-0000-0000-0000-000000000022")),
                        MinistryId.of(UUID.fromString(
                                "00000000-0000-0000-0000-0000000000aa")),
                        "Wrong Key",
                        "VACANT"
                )
        );
        mismatchRoot.put("Positions", mismatchPositions);
        mismatch.putRaw(mismatchRoot);
        expectThrows(
                GovernmentNbtException.class,
                () -> repository(mismatch),
                "a Positions key not matching the record position id rejects the load"
        );

        // FILLED position without a holder.
        SavedDataBackedTestStore holderless = new SavedDataBackedTestStore();
        CompoundTag holderlessRoot = baseRoot();
        CompoundTag holderlessPositions = new CompoundTag();
        CompoundTag holderlessTag = basePositionTag(
                PositionId.of(UUID.fromString(
                        "00000000-0000-0000-0000-000000000011")),
                MinistryId.of(UUID.fromString(
                        "00000000-0000-0000-0000-0000000000aa")),
                "Holderless",
                "FILLED"
        );
        holderlessTag.put("HolderRef", new CompoundTag()); // empty holder
        holderlessPositions.put("00000000-0000-0000-0000-000000000011", holderlessTag);
        holderlessRoot.put("Positions", holderlessPositions);
        holderless.putRaw(holderlessRoot);
        expectThrows(
                GovernmentNbtException.class,
                () -> repository(holderless),
                "a FILLED position without a holder rejects the load"
        );

        // Office bound to a missing position.
        SavedDataBackedTestStore orphanOffice = new SavedDataBackedTestStore();
        CompoundTag orphanRoot = baseRoot();
        CompoundTag offices = new CompoundTag();
        offices.put(
                "00000000-0000-0000-0000-000000000033",
                baseOfficeTag(
                        PositionId.of(UUID.fromString(
                                "00000000-0000-0000-0000-000000000033")),
                        BRAVO_ID,
                        1_000L,
                        null
                )
        );
        orphanRoot.put("Offices", offices);
        orphanOffice.putRaw(orphanRoot);
        expectThrows(
                GovernmentNbtException.class,
                () -> repository(orphanOffice),
                "an office bound to a missing position rejects the load"
        );

        // Non-FILLED position with a current office.
        SavedDataBackedTestStore currentOfficeOnVacant = new SavedDataBackedTestStore();
        CompoundTag vacantRoot = baseRoot();
        CompoundTag vacantPositions = new CompoundTag();
        vacantPositions.put(
                "00000000-0000-0000-0000-0000000000aa",
                basePositionTag(
                        PositionId.of(UUID.fromString(
                                "00000000-0000-0000-0000-0000000000aa")),
                        MinistryId.of(UUID.fromString(
                                "00000000-0000-0000-0000-0000000000aa")),
                        "Vacant Minister",
                        "VACANT"
                )
        );
        vacantRoot.put("Positions", vacantPositions);
        CompoundTag vacantOffices = new CompoundTag();
        vacantOffices.put(
                "00000000-0000-0000-0000-0000000000aa",
                baseOfficeTag(
                        PositionId.of(UUID.fromString(
                                "00000000-0000-0000-0000-0000000000aa")),
                        BRAVO_ID,
                        1_000L,
                        null
                )
        );
        vacantRoot.put("Offices", vacantOffices);
        currentOfficeOnVacant.putRaw(vacantRoot);
        expectThrows(
                GovernmentNbtException.class,
                () -> repository(currentOfficeOnVacant),
                "a current office on a VACANT position rejects the load"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: restart recovery (§6)
    // ------------------------------------------------------------------

    private static void testRestartPersistence() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(8_000);
        GovernmentRepository firstRepository = repository(store);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        GovernmentService firstService = service(
                firstRepository, clock, holders(ALPHA_ID, BRAVO_ID), access
        );
        MinistryId ministry = firstService.createMinistry(
                ALPHA_ID, new MinistryDraft("Ministry of Culture")
        ).ministry().ministryId();
        PositionId position = firstService.createPosition(
                ALPHA_ID,
                new CreatePositionRequest(ministry, "Minister of Culture")
        ).position().positionId();
        firstService.appoint(ALPHA_ID, position, OwnerReference.forPlayer(BRAVO_ID),
                context(clock.getAsLong()));
        long storeRevisionBefore = firstRepository.snapshot().storeRevision();

        GovernmentRepository restarted = restartRepository(store);
        Ministry reloadedMinistry = restarted.requireMinistry(ministry);
        require(reloadedMinistry.name().equals("Ministry of Culture"),
                "the ministry survives restart");
        require(reloadedMinistry.ministryRevision() == 1,
                "the ministry revision survives restart");
        GovernmentPosition reloadedPosition = restarted.requirePosition(position);
        require(reloadedPosition.state() == PositionState.FILLED,
                "the position survives restart as FILLED");
        require(reloadedPosition.holderRef()
                        .map(holder -> holder.equals(OwnerReference.forPlayer(BRAVO_ID)))
                        .orElse(false),
                "the holder reference survives restart");
        require(reloadedPosition.positionRevision() == 2,
                "the position revision survives restart");
        Office reloadedOffice = restarted.findOfficeByPosition(position).orElseThrow();
        require(reloadedOffice.current(), "the office survives restart as current");
        require(reloadedOffice.holderRef().equals(OwnerReference.forPlayer(BRAVO_ID)),
                "the office holder survives restart");
        require(restarted.snapshot().storeRevision() == storeRevisionBefore,
                "the store revision survives restart");
    }

    // ------------------------------------------------------------------
    // acceptance: no bulk enumeration API (§6)
    // ------------------------------------------------------------------

    private static void testNoEnumerationApi() {
        for (Class<?> type : List.of(GovernmentService.class, GovernmentRepository.class)) {
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
                // Bounded projections (design contract returns List) are
                // allowed; enumeration-style names are not. The bound itself
                // is verified at runtime in testBoundedProjections.
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

    // ------------------------------------------------------------------
    // acceptance: capacity and store failure fail closed (§6)
    // ------------------------------------------------------------------

    private static void testCapacityFailClosed() {
        GovernmentLimits tight = new GovernmentLimits(1, 2, 1, 8 * 1024 * 1024);
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(9_000);
        GovernmentRepository repository = repository(store, tight);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        GovernmentService service = service(
                repository, clock, holders(ALPHA_ID, BRAVO_ID), access
        );
        service.createMinistry(ALPHA_ID, new MinistryDraft("Ministry of Culture"));

        GovernmentUnavailableException ministryFailure = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.createMinistry(
                        ALPHA_ID, new MinistryDraft("Second Ministry")
                ),
                "ministry capacity is enforced fail-closed"
        );
        require(ministryFailure.failureCode().equals(
                        GovernmentUnavailableException.CODE_CAPACITY_EXCEEDED),
                "ministry capacity failure carries the stable code");

        MinistryId ministry = service.ministries().get(0).ministryId();
        PositionId first = service.createPosition(ALPHA_ID,
                new CreatePositionRequest(ministry, "Minister")).position().positionId();
        PositionId second = service.createPosition(ALPHA_ID,
                new CreatePositionRequest(ministry, "Deputy Minister"))
                .position().positionId();
        GovernmentUnavailableException positionFailure = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.createPosition(
                        ALPHA_ID,
                        new CreatePositionRequest(ministry, "Third Minister")
                ),
                "position capacity is enforced fail-closed"
        );
        require(positionFailure.failureCode().equals(
                        GovernmentUnavailableException.CODE_CAPACITY_EXCEEDED),
                "position capacity failure carries the stable code");

        // The office budget is exhausted by the first appointment.
        service.appoint(ALPHA_ID, first, OwnerReference.forPlayer(BRAVO_ID),
                context(clock.getAsLong()));
        GovernmentUnavailableException officeFailure = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.appoint(
                        ALPHA_ID, second, OwnerReference.forPlayer(ALPHA_ID),
                        context(clock.getAsLong())
                ),
                "office capacity is enforced fail-closed"
        );
        require(officeFailure.failureCode().equals(
                        GovernmentUnavailableException.CODE_CAPACITY_EXCEEDED),
                "office capacity failure carries the stable code");
    }

    private static void testStoreFailureAtomicity() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(10_000);
        GovernmentRepository repository = repository(store);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        GovernmentService service = service(
                repository, clock, holders(ALPHA_ID), access
        );
        store.setCommitFailureCode("INJECTED_FAILURE");

        GovernmentUnavailableException failure = expectThrows(
                GovernmentUnavailableException.class,
                () -> service.createMinistry(
                        ALPHA_ID, new MinistryDraft("Ministry of Culture")
                ),
                "a durable gate rejection fails closed"
        );
        require(failure.failureCode().equals(
                        GovernmentUnavailableException.CODE_STORE_FAILURE),
                "store failure carries the stable code");
        require(repository.ministryCount() == 0, "a failed commit publishes no ministry");
        require(repository.snapshot().storeRevision() == 0L,
                "a failed commit advances no revision");
    }

    // ------------------------------------------------------------------
    // acceptance: bounded projections (§6)
    // ------------------------------------------------------------------

    private static void testBoundedProjections() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(11_000);
        GovernmentRepository repository = repository(store);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        GovernmentService service = service(
                repository, clock, holders(ALPHA_ID, BRAVO_ID), access
        );
        MinistryId ministry = service.createMinistry(
                ALPHA_ID, new MinistryDraft("Ministry of Culture")
        ).ministry().ministryId();
        for (int index = 0; index < 3; index++) {
            service.createPosition(ALPHA_ID,
                    new CreatePositionRequest(ministry, "Position " + index));
        }

        List<MinistryProjection> ministries = service.ministries();
        require(ministries.size() == 1, "ministries() is bounded and exact here");
        require(ministries.get(0).ministryId().equals(ministry),
                "the projection carries the ministry id");
        require(ministries.get(0).name().equals("Ministry of Culture"),
                "the projection carries the name");

        List<PositionProjection> positions = service.positionsByMinistry(ministry);
        require(positions.size() == 3, "positionsByMinistry lists the ministry positions");
        require(positions.get(0).state() == PositionState.VACANT,
                "the projection carries the state");
        require(positions.get(0).holderRef().isEmpty(),
                "a VACANT projection carries no holder");
        require(positions.get(0).positionId().toString().equals(
                        positions.get(0).positionId().toString().toLowerCase(
                                java.util.Locale.ROOT)),
                "projected ids are canonical");

        // A different ministry has no positions.
        MinistryId other = service.createMinistry(
                ALPHA_ID, new MinistryDraft("Foreign Ministry")
        ).ministry().ministryId();
        require(service.positionsByMinistry(other).isEmpty(),
                "an unrelated ministry yields no positions");

        // The projection is hard-bounded: creating more positions than the
        // cap still yields at most MAX_PROJECTION_SIZE entries.
        SavedDataBackedTestStore bulkStore = new SavedDataBackedTestStore();
        MutableClock bulkClock = new MutableClock(12_000);
        GovernmentRepository bulkRepository = repository(bulkStore);
        FakeInstitutionAccessService bulkAccess = new FakeInstitutionAccessService();
        GovernmentService bulkService = service(
                bulkRepository, bulkClock, holders(ALPHA_ID), bulkAccess
        );
        MinistryId bulkMinistry = bulkService.createMinistry(
                ALPHA_ID, new MinistryDraft("Bulk Ministry")
        ).ministry().ministryId();
        for (int index = 0; index < GovernmentService.MAX_PROJECTION_SIZE + 20; index++) {
            bulkService.createPosition(ALPHA_ID,
                    new CreatePositionRequest(bulkMinistry, "Bulk Position " + index));
        }
        List<PositionProjection> capped = bulkService.positionsByMinistry(bulkMinistry);
        require(capped.size() == GovernmentService.MAX_PROJECTION_SIZE,
                "positionsByMinistry is hard-capped at the projection limit");
        List<MinistryProjection> cappedMinistries = bulkService.ministries();
        require(cappedMinistries.size() == 1,
                "ministries() stays bounded");
    }

    // ------------------------------------------------------------------
    // module contract checks
    // ------------------------------------------------------------------

    private static void testModuleContract() {
        require(GovernmentRepository.MODULE_DATA_KEY.equals("government"),
                "government owns exactly the approved namespace");
        require(GovernmentModule.MODULE_ID.value().equals("government"),
                "government module id is 'government'");
        ModuleDefinition definition = new ModuleDefinition(
                GovernmentModule.MODULE_ID,
                new ModuleMetadata("Government", "1.0.0", Optional.empty(),
                        Optional.empty()),
                Set.of(
                        PlayerDataModule.MODULE_ID,
                        SubjectRegistryModule.MODULE_ID,
                        com.fontainerepublic.server.audit.AuditModule.MODULE_ID,
                        com.fontainerepublic.server.institutionaccess
                                .InstitutionAccessModule.MODULE_ID
                ),
                Set.of(),
                80,
                GovernmentModule::new
        );
        require(definition.requiredDependencies().equals(Set.of(
                        PlayerDataModule.MODULE_ID,
                        SubjectRegistryModule.MODULE_ID,
                        com.fontainerepublic.server.audit.AuditModule.MODULE_ID,
                        com.fontainerepublic.server.institutionaccess
                                .InstitutionAccessModule.MODULE_ID)),
                "government depends only on player-data, subject-registry, "
                        + "audit, and institution-access");
        for (ModuleId dependency : definition.requiredDependencies()) {
            String value = dependency.value();
            require(!value.contains("emg") && !value.contains("emergency")
                            && !value.contains("economy") && !value.contains("justice")
                            && !value.contains("parliament") && !value.contains("court")
                            && !value.contains("bank"),
                    "government never depends on later-phase or other-pillar "
                            + "namespaces: " + value);
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static GovernmentRepository repository(GovernmentStore store) {
        return new GovernmentRepository(
                store, new GovernmentNbtCodec(), GovernmentLimits.DEFAULT,
                new SequentialIdSource()
        );
    }

    private static GovernmentRepository repository(
            GovernmentStore store,
            GovernmentLimits limits
    ) {
        return new GovernmentRepository(
                store, new GovernmentNbtCodec(), limits,
                new SequentialIdSource()
        );
    }

    private static GovernmentRepository restartRepository(
            SavedDataBackedTestStore store
    ) {
        return repository(store.restart());
    }

    private static GovernmentService service(
            GovernmentRepository repository,
            LongSupplier clock,
            FakeHolderDirectory holders
    ) {
        return service(repository, clock, holders, new FakeInstitutionAccessService());
    }

    private static GovernmentService service(
            GovernmentRepository repository,
            LongSupplier clock,
            FakeHolderDirectory holders,
            FakeInstitutionAccessService access
    ) {
        return new DefaultGovernmentService(
                repository, clock, holders, access, null
        );
    }

    private static FakeHolderDirectory holders(UUID... players) {
        FakeHolderDirectory holders = new FakeHolderDirectory();
        for (UUID player : players) {
            holders.add(player);
        }
        return holders;
    }

    /** A valid OFFICIAL_ROUTINE on-site context at a GOVERNMENT facility. */
    private static OnSiteContext context(long now) {
        return new OnSiteContext(
                UUID.randomUUID(),
                ALPHA_ID,
                InstitutionType.GOVERNMENT,
                FacilityId.of(UUID.fromString(
                        "00000000-0000-0000-0000-0000000000dd")),
                ZoneId.of(UUID.fromString(
                        "00000000-0000-0000-0000-0000000000ee")),
                WorkflowKind.OFFICIAL_ROUTINE,
                CapabilityClass.ONSITE_OFFICIAL_DUTY,
                now,
                now + 600_000L,
                1L,
                1L,
                DIMENSION,
                10,
                64,
                10
        );
    }

    private static CompoundTag baseRoot() {
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 0L);
        CompoundTag ministries = new CompoundTag();
        ministries.put(
                "00000000-0000-0000-0000-0000000000aa",
                baseMinistryTag()
        );
        root.put("Ministries", ministries);
        root.put("Positions", new CompoundTag());
        root.put("Offices", new CompoundTag());
        return root;
    }

    private static CompoundTag baseMinistryTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("MinistryVersion", 1);
        tag.putUUID("MinistryId", UUID.fromString(
                "00000000-0000-0000-0000-0000000000aa"));
        tag.putString("Name", "Ministry of Culture");
        tag.putString("State", "ACTIVE");
        tag.putLong("MinistryRevision", 1L);
        return tag;
    }

    private static CompoundTag basePositionTag(
            PositionId positionId,
            MinistryId ministryId,
            String title,
            String state
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("PositionVersion", 1);
        tag.putUUID("PositionId", positionId.value());
        tag.putUUID("MinistryId", ministryId.value());
        tag.putString("Title", title);
        tag.putString("State", state);
        CompoundTag holder = new CompoundTag();
        if (state.equals("FILLED")) {
            holder.putString("HolderKind", "PLAYER_UUID");
            holder.putString("HolderOwnerId", BRAVO_ID.toString());
        }
        tag.put("HolderRef", holder);
        tag.putLong("PositionRevision", 1L);
        return tag;
    }

    private static CompoundTag baseOfficeTag(
            PositionId positionId,
            UUID holderId,
            long assignedAt,
            Long revokedAt
    ) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("OfficeVersion", 1);
        tag.putUUID("OfficeId", UUID.fromString(
                "00000000-0000-0000-0000-0000000000ab"));
        tag.putUUID("PositionId", positionId.value());
        CompoundTag holder = new CompoundTag();
        holder.putString("HolderKind", "PLAYER_UUID");
        holder.putString("HolderOwnerId", holderId.toString());
        tag.put("HolderRef", holder);
        tag.putLong("AssignedAt", assignedAt);
        if (revokedAt != null) {
            tag.putLong("RevokedAt", revokedAt);
        }
        tag.putLong("OfficeRevision", 1L);
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

    private static final class SavedDataBackedTestStore implements GovernmentStore {
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
            return savedData.getModuleData(GovernmentRepository.MODULE_DATA_KEY).copy();
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
                        GovernmentRepository.MODULE_DATA_KEY,
                        0L,
                        0L,
                        commitFailureCode
                );
            }
            savedData.putModuleData(
                    GovernmentRepository.MODULE_DATA_KEY,
                    snapshot.copy()
            );
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    GovernmentRepository.MODULE_DATA_KEY,
                    1L,
                    0L,
                    ""
            );
        }

        private void putRaw(CompoundTag raw) {
            savedData.putModuleData(GovernmentRepository.MODULE_DATA_KEY, raw);
        }

        private void setCommitFailureCode(String failureCode) {
            this.commitFailureCode = failureCode;
        }

        private int commitCount() {
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

    private static final class SequentialIdSource implements GovernmentIdSource {
        private long counter;

        @Override
        public UUID nextUuid() {
            counter++;
            return new UUID(0L, counter);
        }
    }

    private static final class FakeHolderDirectory implements HolderDirectory {
        private final Set<UUID> records = new java.util.HashSet<>();
        private final Set<UUID> activeSubjects = new java.util.HashSet<>();
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
        public boolean hasActiveSubject(UUID playerId) {
            return activeSubjects.contains(playerId);
        }

        private void add(UUID playerId) {
            records.add(playerId);
            activeSubjects.add(playerId);
        }

        /** A player record without an active FR-ID subject. */
        private void addWithoutSubject(UUID playerId) {
            records.add(playerId);
        }

        private void setAvailable(boolean available) {
            this.available = available;
        }
    }

    /**
     * Test double of the shared institution access boundary: records every
     * final mutation-boundary call and its capability, and returns a
     * configurable result. Every other operation is unsupported — the
     * government module consumes the boundary at mutation time only.
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
        public Optional<Facility> getFacility(FacilityId facilityId) {
            throw unsupported();
        }

        @Override
        public Optional<Zone> getZone(ZoneId zoneId) {
            throw unsupported();
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException(
                    "not part of the government mutation boundary"
            );
        }
    }
}
