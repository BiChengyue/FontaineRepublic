package com.fontainerepublic.server.land;

import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.core.ModSavedData;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.server.land.api.CreateParcelRequest;
import com.fontainerepublic.server.land.api.HolderDirectory;
import com.fontainerepublic.server.land.api.LandChangeKind;
import com.fontainerepublic.server.land.api.LandReceipt;
import com.fontainerepublic.server.land.api.LandService;
import com.fontainerepublic.server.land.api.PermissionResolver;
import com.fontainerepublic.server.land.api.UsageChangeKind;
import com.fontainerepublic.server.land.api.UsageReceipt;
import com.fontainerepublic.server.land.api.ViolationDraft;
import com.fontainerepublic.server.land.api.ViolationReceipt;
import com.fontainerepublic.server.land.event.LandEventPolicy;
import com.fontainerepublic.server.land.model.LandAccess;
import com.fontainerepublic.server.land.model.LandOwnership;
import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.land.model.ParcelRegion;
import com.fontainerepublic.server.land.model.UsageRight;
import com.fontainerepublic.server.land.model.ViolationReport;
import com.fontainerepublic.server.land.model.ViolationStatus;
import com.fontainerepublic.server.land.model.ZoneType;
import com.fontainerepublic.server.land.persistence.LandLimits;
import com.fontainerepublic.server.land.persistence.LandNbtCodec;
import com.fontainerepublic.server.land.persistence.LandNbtException;
import com.fontainerepublic.server.land.persistence.LandRepository;
import com.fontainerepublic.server.land.persistence.LandStore;
import com.fontainerepublic.server.land.persistence.LandStoreSnapshot;
import com.fontainerepublic.server.land.persistence.LandUnavailableException;
import com.fontainerepublic.server.land.persistence.ParcelIdSource;
import com.fontainerepublic.server.land.service.ConfigDrivenPermissionResolver;
import com.fontainerepublic.server.land.service.DefaultLandService;
import com.fontainerepublic.server.land.service.LandPermissionConfig;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.registry.model.OwnerReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * Dependency-free validation entry point for FR-LAND-001 (land module).
 * Exercises the FR-LAND-001-A §7 acceptance matrix with an injectable store
 * and SavedData-backed restart simulation: immutable REPUBLIC ownership with
 * no transfer path, single-snapshot gated grant/renew/revoke, holder
 * resolution through services, config-driven permission resolution with no
 * rank/GOD bypass, strict deterministic codec with holder-index consistency,
 * restart recovery, event-time fail-closed policy, and no bulk enumeration
 * API.
 */
public final class LandFoundationTestMain {

    private static final String PROJECT_DIR_PROPERTY = "fontainerepublic.projectDir";
    private static final String DIMENSION = "minecraft:overworld";

    private static final UUID ALPHA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private LandFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testOwnershipImmutable();
        testCreateParcelSingleSnapshot();
        testGrantRenewRevokeSingleSnapshot();
        testUsagePreconditions();
        testZoneAccessChange();
        testViolationReportReadOnly();
        testPermissionResolverConfigDriven();
        testRankNoBypass();
        testNoHardcodedCoordinates();
        testStrictDeterministicCodec();
        testCorruptSnapshotFailClosed();
        testRestartPersistence();
        testNoEnumerationApi();
        testCapacityFailClosed();
        testStoreFailureAtomicity();
        testEventPolicyFailClosed();
        testModuleContract();
        System.out.println("[FR-LAND-001] Land foundation validation passed");
    }

    // ------------------------------------------------------------------
    // acceptance: ownership immutable, no transfer API (§7)
    // ------------------------------------------------------------------

    private static void testOwnershipImmutable() {
        require(
                List.of(LandOwnership.values()).equals(List.of(LandOwnership.REPUBLIC)),
                "LandOwnership exposes exactly REPUBLIC"
        );
        require(LandOwnership.REPUBLIC == LandOwnership.REPUBLIC,
                "REPUBLIC is the sole ownership value");

        LandParcel parcel = parcel(createParcel());
        require(parcel.ownership() == LandOwnership.REPUBLIC,
                "every parcel is owned by the Republic");

        // No transfer/ownership-mutation API on the service or the parcel.
        for (Method method : LandService.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            require(!name.contains("transfer") && !name.contains("sell")
                            && !name.contains("lease") && !name.contains("auction")
                            && !name.contains("setownership")
                            && !name.contains("changeownership"),
                    "no ownership transfer API in LandService: " + method.getName());
        }
        for (Method method : LandParcel.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            require(!name.contains("withownership") && !name.contains("transfer")
                            && !name.contains("sell") && !name.contains("lease"),
                    "no ownership mutation on LandParcel: " + method.getName());
        }
    }

    // ------------------------------------------------------------------
    // acceptance: create parcel — one snapshot, one revision, gated (§7)
    // ------------------------------------------------------------------

    private static void testCreateParcelSingleSnapshot() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        LandRepository repository = repository(store);
        LandService service = service(repository, clock, holders(ALPHA_ID));

        LandReceipt receipt = service.createParcel(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL)
        );
        require(receipt.applied(), "parcel creation is applied");
        require(receipt.kind() == LandChangeKind.PARCEL_CREATED,
                "receipt kind is PARCEL_CREATED");
        require(receipt.parcel().ownership() == LandOwnership.REPUBLIC,
                "created parcel is republic-owned");
        require(receipt.parcel().parcelRevision() == 1,
                "new parcel starts at revision 1");
        require(receipt.parcel().zoneType() == ZoneType.RESIDENTIAL,
                "zone type was applied");
        require(receipt.parcel().access() == LandAccess.PUBLIC,
                "default access is PUBLIC");
        require(receipt.parcel().usageRights().isEmpty(),
                "new parcel has no usage rights");
        require(repository.snapshot().storeRevision() == 1L,
                "creation commits exactly one store revision");
        require(store.commitCount() == 1, "creation commits exactly one snapshot");

        LandReceipt explicit = service.createParcel(
                ALPHA_ID,
                new CreateParcelRequest(DIMENSION, region(), ZoneType.GOVERNMENT,
                        LandAccess.PRIVATE)
        );
        require(explicit.parcel().access() == LandAccess.PRIVATE,
                "explicit access is honored");
        require(explicit.parcel().zoneType() == ZoneType.GOVERNMENT,
                "explicit zone is honored");
        require(!explicit.parcel().parcelId().equals(receipt.parcel().parcelId()),
                "each parcel gets a distinct server-assigned id");
    }

    // ------------------------------------------------------------------
    // acceptance: grant/renew/revoke — one snapshot, gated (§7)
    // ------------------------------------------------------------------

    private static void testGrantRenewRevokeSingleSnapshot() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(2_000);
        LandRepository repository = repository(store);
        LandService service = service(repository, clock, holders(ALPHA_ID, BRAVO_ID));
        LandParcel parcel = service.createParcel(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL)
        ).parcel();
        OwnerReference bravo = OwnerReference.forPlayer(BRAVO_ID);

        // grant
        int commitsBeforeGrant = store.commitCount();
        UsageReceipt grant = service.grantUsage(ALPHA_ID, parcel.parcelId(), bravo, 10_000);
        require(grant.applied(), "grant is applied");
        require(grant.kind() == UsageChangeKind.GRANTED, "receipt kind is GRANTED");
        require(grant.holder().equals(bravo), "receipt carries the holder");
        UsageRight right = grant.parcel().usageRightOf(bravo).orElseThrow();
        require(right.rightRevision() == 1, "new right starts at revision 1");
        require(right.grantedAt() == 2_000L, "grantedAt is the server clock");
        require(right.expiresAt() == 12_000L, "expiry is grantedAt + duration");
        require(grant.parcel().parcelRevision() == 2, "parcel revision +1 exactly once");
        require(repository.snapshot().storeRevision() == 2L,
                "store revision +1 exactly once");
        require(store.commitCount() == commitsBeforeGrant + 1,
                "grant commits exactly one snapshot");

        // renew
        int commitsBeforeRenew = store.commitCount();
        clock.setNow(3_000);
        UsageReceipt renew = service.renewUsage(ALPHA_ID, parcel.parcelId(), bravo, 20_000);
        require(renew.applied(), "renew is applied");
        require(renew.kind() == UsageChangeKind.RENEWED, "receipt kind is RENEWED");
        UsageRight renewed = renew.parcel().usageRightOf(bravo).orElseThrow();
        require(renewed.grantedAt() == 2_000L, "renewal keeps the original grant time");
        require(renewed.expiresAt() == 23_000L, "renewal replaces the expiry");
        require(renewed.rightRevision() == 2, "right revision +1 exactly once");
        require(renew.parcel().parcelRevision() == 3, "parcel revision +1 exactly once");
        require(repository.snapshot().storeRevision() == 3L,
                "store revision +1 exactly once");
        require(store.commitCount() == commitsBeforeRenew + 1,
                "renew commits exactly one snapshot");

        // revoke
        int commitsBeforeRevoke = store.commitCount();
        UsageReceipt revoke = service.revokeUsage(ALPHA_ID, parcel.parcelId(), bravo);
        require(revoke.applied(), "revoke is applied");
        require(revoke.kind() == UsageChangeKind.REVOKED, "receipt kind is REVOKED");
        require(revoke.parcel().usageRightOf(bravo).isEmpty(),
                "the right is gone after revocation");
        require(revoke.parcel().parcelRevision() == 4, "parcel revision +1 exactly once");
        require(repository.snapshot().storeRevision() == 4L,
                "store revision +1 exactly once");
        require(store.commitCount() == commitsBeforeRevoke + 1,
                "revoke commits exactly one snapshot");

        // revoke again: idempotent no-op
        int commitsBeforeNoop = store.commitCount();
        UsageReceipt noop = service.revokeUsage(ALPHA_ID, parcel.parcelId(), bravo);
        require(!noop.applied(), "revoking a missing right is an idempotent no-op");
        require(store.commitCount() == commitsBeforeNoop, "no-op commits nothing");
        require(repository.snapshot().storeRevision() == 4L,
                "no-op does not advance the store revision");

        // permanent grant (duration 0)
        UsageReceipt permanent = service.grantUsage(ALPHA_ID, parcel.parcelId(), bravo, 0);
        require(permanent.parcel().usageRightOf(bravo).orElseThrow().expiresAt() == 0L,
                "duration 0 grants without expiry");
    }

    // ------------------------------------------------------------------
    // acceptance: usage preconditions fail closed with stable codes (§7)
    // ------------------------------------------------------------------

    private static void testUsagePreconditions() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(4_000);
        LandRepository repository = repository(store);
        LandService service = service(repository, clock, holders(ALPHA_ID, BRAVO_ID));
        LandParcel parcel = service.createParcel(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL)
        ).parcel();
        OwnerReference bravo = OwnerReference.forPlayer(BRAVO_ID);

        // actor not provisioned
        LandUnavailableException unknownActor = expectThrows(
                LandUnavailableException.class,
                () -> service.createParcel(
                        UUID.fromString("00000000-0000-0000-0000-000000000099"),
                        createRequest(ZoneType.OTHER)
                ),
                "an unprovisioned actor cannot create parcels"
        );
        require(unknownActor.failureCode().equals(
                        LandUnavailableException.CODE_PLAYER_NOT_PROVISIONED),
                "unprovisioned actor carries the stable code");

        // holder with a record but no active subject
        FakeHolderDirectory noSubject = holders(ALPHA_ID);
        noSubject.addWithoutSubject(BRAVO_ID);
        LandService strictService = service(repository, clock, noSubject);
        LandUnavailableException invalidHolder = expectThrows(
                LandUnavailableException.class,
                () -> strictService.grantUsage(
                        ALPHA_ID, parcel.parcelId(), bravo, 1_000
                ),
                "a holder without an active subject is rejected"
        );
        require(invalidHolder.failureCode().equals(
                        LandUnavailableException.CODE_INVALID_HOLDER),
                "invalid holder carries the stable code");

        // non-player holder kind
        LandUnavailableException officeHolder = expectThrows(
                LandUnavailableException.class,
                () -> service.grantUsage(
                        ALPHA_ID, parcel.parcelId(),
                        OwnerReference.HYDRO_ARCHON_OFFICE, 1_000
                ),
                "an OFFICE_ID holder is not supported in Alpha"
        );
        require(officeHolder.failureCode().equals(
                        LandUnavailableException.CODE_UNSUPPORTED_HOLDER_KIND),
                "unsupported holder kind carries the stable code");

        // duplicate grant
        service.grantUsage(ALPHA_ID, parcel.parcelId(), bravo, 1_000);
        LandUnavailableException duplicate = expectThrows(
                LandUnavailableException.class,
                () -> service.grantUsage(ALPHA_ID, parcel.parcelId(), bravo, 2_000),
                "a duplicate grant is rejected"
        );
        require(duplicate.failureCode().equals(
                        LandUnavailableException.CODE_DUPLICATE_GRANT),
                "duplicate grant carries the stable code");

        // renew without a right
        OwnerReference alpha = OwnerReference.forPlayer(ALPHA_ID);
        LandUnavailableException noRight = expectThrows(
                LandUnavailableException.class,
                () -> service.renewUsage(ALPHA_ID, parcel.parcelId(), alpha, 1_000),
                "renewing a missing right is rejected"
        );
        require(noRight.failureCode().equals(
                        LandUnavailableException.CODE_NO_USAGE_RIGHT),
                "missing right carries the stable code");

        // negative duration
        LandUnavailableException negative = expectThrows(
                LandUnavailableException.class,
                () -> service.grantUsage(ALPHA_ID, parcel.parcelId(), bravo, -1),
                "a negative duration is rejected"
        );
        require(negative.failureCode().equals(
                        LandUnavailableException.CODE_INVALID_REQUEST),
                "negative duration carries the stable code");

        // unknown parcel
        LandUnavailableException unknownParcel = expectThrows(
                LandUnavailableException.class,
                () -> service.setZoneType(
                        ALPHA_ID,
                        ParcelId.of(UUID.fromString(
                                "00000000-0000-0000-0000-0000000000aa")),
                        ZoneType.OTHER
                ),
                "mutating an unknown parcel is rejected"
        );
        require(unknownParcel.failureCode().equals(
                        LandUnavailableException.CODE_PARCEL_NOT_FOUND),
                "unknown parcel carries the stable code");
    }

    // ------------------------------------------------------------------
    // acceptance: zone/access authoritative change, idempotent no-op (§7)
    // ------------------------------------------------------------------

    private static void testZoneAccessChange() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(5_000);
        LandRepository repository = repository(store);
        LandService service = service(repository, clock, holders(ALPHA_ID));
        LandParcel parcel = service.createParcel(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL)
        ).parcel();

        int commitsBefore = store.commitCount();
        LandReceipt zone = service.setZoneType(ALPHA_ID, parcel.parcelId(), ZoneType.PROTECTED);
        require(zone.applied(), "zone change is applied");
        require(zone.kind() == LandChangeKind.ZONE_TYPE, "receipt kind is ZONE_TYPE");
        require(zone.parcel().zoneType() == ZoneType.PROTECTED, "zone was replaced");
        require(zone.parcel().access() == LandAccess.PUBLIC,
                "zone change leaves access untouched");
        require(zone.parcel().parcelRevision() == 2, "parcel revision +1 exactly once");
        require(repository.snapshot().storeRevision() == 2L,
                "store revision +1 exactly once");
        require(store.commitCount() == commitsBefore + 1,
                "zone change commits exactly one snapshot");

        int commitsBeforeAccess = store.commitCount();
        LandReceipt access = service.setAccess(ALPHA_ID, parcel.parcelId(), LandAccess.RESTRICTED);
        require(access.applied(), "access change is applied");
        require(access.kind() == LandChangeKind.ACCESS, "receipt kind is ACCESS");
        require(access.parcel().access() == LandAccess.RESTRICTED, "access was replaced");
        require(access.parcel().parcelRevision() == 3, "parcel revision +1 exactly once");
        require(store.commitCount() == commitsBeforeAccess + 1,
                "access change commits exactly one snapshot");

        // idempotent no-ops
        int commitsBeforeNoop = store.commitCount();
        LandReceipt sameZone = service.setZoneType(ALPHA_ID, parcel.parcelId(), ZoneType.PROTECTED);
        require(!sameZone.applied(), "same-zone request is an idempotent no-op");
        require(store.commitCount() == commitsBeforeNoop, "no-op commits nothing");
        LandReceipt sameAccess = service.setAccess(
                ALPHA_ID, parcel.parcelId(), LandAccess.RESTRICTED
        );
        require(!sameAccess.applied(), "same-access request is an idempotent no-op");
        require(store.commitCount() == commitsBeforeNoop, "no-op commits nothing");
    }

    // ------------------------------------------------------------------
    // acceptance: violation report — created, read-only (§7)
    // ------------------------------------------------------------------

    private static void testViolationReportReadOnly() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(6_000);
        LandRepository repository = repository(store);
        LandService service = service(repository, clock, holders(ALPHA_ID, BRAVO_ID));
        LandParcel parcel = service.createParcel(
                ALPHA_ID, createRequest(ZoneType.PROTECTED)
        ).parcel();

        int commitsBefore = store.commitCount();
        ViolationReceipt receipt = service.createViolationReport(
                new ViolationDraft(parcel.parcelId(), OwnerReference.forPlayer(BRAVO_ID),
                        "Unauthorized structure inside the protected zone")
        );
        require(receipt.applied(), "report creation is applied");
        ViolationReport report = receipt.report();
        require(report.parcelId().equals(parcel.parcelId()), "report references the parcel");
        require(report.reporter().equals(OwnerReference.forPlayer(BRAVO_ID)),
                "report carries the reporter holder");
        require(report.status() == ViolationStatus.OPEN, "new report is OPEN");
        require(report.reportId() == 1L, "first report id is 1");
        require(report.reportedAt() == 6_000L, "reportedAt is the server clock");
        require(store.commitCount() == commitsBefore + 1,
                "report creation commits exactly one snapshot");

        ViolationReceipt second = service.createViolationReport(
                new ViolationDraft(parcel.parcelId(), OwnerReference.forPlayer(BRAVO_ID),
                        "Second report")
        );
        require(second.report().reportId() == 2L, "report ids ascend");

        // Read-only: no update/delete/close/status-change entry exists.
        for (Method method : ViolationReport.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            require(!name.contains("withstatus") && !name.contains("close")
                            && !name.contains("resolve") && !name.contains("delete")
                            && !name.contains("update"),
                    "no mutation API on ViolationReport: " + method.getName());
        }
        for (Method method : LandService.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(java.util.Locale.ROOT);
            require(!name.contains("updateviolation") && !name.contains("deleteviolation")
                            && !name.contains("closeviolation") && !name.contains("resolveviolation"),
                    "no violation mutation API on LandService: " + method.getName());
        }
        require(ViolationStatus.values().length == 1 && ViolationStatus.OPEN != null,
                "ViolationStatus is OPEN-only in FR-LAND");

        // Invalid reports fail closed.
        LandUnavailableException unknownParcel = expectThrows(
                LandUnavailableException.class,
                () -> service.createViolationReport(
                        new ViolationDraft(
                                ParcelId.of(UUID.fromString(
                                        "00000000-0000-0000-0000-0000000000bb")),
                                OwnerReference.forPlayer(BRAVO_ID), "x")
                ),
                "a report on an unknown parcel is rejected"
        );
        require(unknownParcel.failureCode().equals(
                        LandUnavailableException.CODE_PARCEL_NOT_FOUND),
                "unknown parcel carries the stable code");

        LandUnavailableException longDescription = expectThrows(
                LandUnavailableException.class,
                () -> service.createViolationReport(
                        new ViolationDraft(parcel.parcelId(),
                                OwnerReference.forPlayer(BRAVO_ID), "x".repeat(501))
                ),
                "an over-long description is rejected"
        );
        require(longDescription.failureCode().equals(
                        LandUnavailableException.CODE_INVALID_REQUEST),
                "over-long description carries the stable code");
    }

    // ------------------------------------------------------------------
    // acceptance: PermissionResolver config-driven, fail closed (§7)
    // ------------------------------------------------------------------

    private static void testPermissionResolverConfigDriven() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(10_000);
        LandRepository repository = repository(store);
        FakeHolderDirectory holders = holders(ALPHA_ID, BRAVO_ID);
        LandService service = service(repository, clock, holders);
        LandParcel parcel = service.createParcel(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL)
        ).parcel();
        OwnerReference bravo = OwnerReference.forPlayer(BRAVO_ID);
        service.grantUsage(ALPHA_ID, parcel.parcelId(), bravo, 10_000);

        // Unknown parcel fails closed.
        require(!service.canBuild(ALPHA_ID, ParcelId.of(UUID.fromString(
                        "00000000-0000-0000-0000-0000000000cc"))),
                "an unknown parcel fails closed");

        // PUBLIC parcel: open to qualified players.
        require(service.canBuild(ALPHA_ID, parcel.parcelId()),
                "PUBLIC parcel is open to a qualified player");
        require(service.canBreak(ALPHA_ID, parcel.parcelId()),
                "PUBLIC parcel allows break");
        require(service.canInteract(ALPHA_ID, parcel.parcelId()),
                "PUBLIC parcel allows interact");

        // RESTRICTED parcel: requires a live usage right under default config.
        LandParcel restricted = service.setAccess(
                ALPHA_ID, parcel.parcelId(), LandAccess.RESTRICTED
        ).parcel();
        require(service.canBuild(BRAVO_ID, parcel.parcelId()),
                "a holder with a live right may build on RESTRICTED");
        require(!service.canBuild(ALPHA_ID, parcel.parcelId()),
                "a player without a right is denied on RESTRICTED");

        // PRIVATE parcel: requires a live usage right under default config.
        service.setAccess(ALPHA_ID, parcel.parcelId(), LandAccess.PRIVATE);
        require(service.canBuild(BRAVO_ID, parcel.parcelId()),
                "a holder with a live right may build on PRIVATE");
        require(!service.canBuild(ALPHA_ID, parcel.parcelId()),
                "a player without a right is denied on PRIVATE");

        // Expired right loses access.
        clock.setNow(21_000);
        require(!service.canBuild(BRAVO_ID, parcel.parcelId()),
                "an expired right grants no access");
        clock.setNow(10_000);

        // Config: subject gate off + restricted grant gate off.
        LandPermissionConfig open = new LandPermissionConfig(false, true, false, true);
        ConfigDrivenPermissionResolver openResolver = new ConfigDrivenPermissionResolver(
                open, repository, holders, clock
        );
        require(openResolver.canBuild(BRAVO_ID, parcel.parcelId()),
                "a live right still opens PRIVATE without the subject gate");
        require(!openResolver.canBuild(ALPHA_ID, parcel.parcelId()),
                "PRIVATE still requires a right even without the subject gate");

        // Config: public access fully disabled / enabled by configuration.
        LandParcel publicParcel = service.createParcel(
                ALPHA_ID, createRequest(ZoneType.PUBLIC)
        ).parcel();
        require(openResolver.canBuild(ALPHA_ID, publicParcel.parcelId()),
                "config without the subject gate opens PUBLIC access");
        LandPermissionConfig closed = new LandPermissionConfig(false, false, true, true);
        ConfigDrivenPermissionResolver closedResolver = new ConfigDrivenPermissionResolver(
                closed, repository, holders, clock
        );
        require(!closedResolver.canBuild(ALPHA_ID, publicParcel.parcelId()),
                "config can close PUBLIC parcels entirely");

        // Fail closed when the holder directory is unavailable.
        holders.setAvailable(false);
        require(!service.canBuild(ALPHA_ID, publicParcel.parcelId()),
                "an unavailable holder directory fails closed");
        holders.setAvailable(true);
    }

    // ------------------------------------------------------------------
    // acceptance: rank/GOD never bypasses permissions (§7)
    // ------------------------------------------------------------------

    private static void testRankNoBypass() throws Exception {
        // Source-level check: no rank-to-permission mapping exists anywhere in
        // the land module production code (comments excluded).
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path landDirectory = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/server/land"
        );
        require(Files.isDirectory(landDirectory),
                "Production land source directory must exist");
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(landDirectory)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> source.append(read(path)).append('\n'));
        }
        String codeOnly = stripComments(source.toString());
        for (String forbidden : List.of(
                "isOp", "opLevel", "permissionLevel", "getPermission",
                "bypass", "canBypass", "sudo", "setOp", "Commands.OP",
                "rank", "GOD", "CitizenRank"
        )) {
            require(
                    !codeOnly.contains(forbidden),
                    "land production code must not contain rank-to-permission mapping: "
                            + forbidden
            );
        }
    }

    // ------------------------------------------------------------------
    // acceptance: no hard-coded coordinates (§7)
    // ------------------------------------------------------------------

    private static void testNoHardcodedCoordinates() throws Exception {
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path landDirectory = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/server/land"
        );
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(landDirectory)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> source.append(read(path)).append('\n'));
        }
        String codeOnly = stripComments(source.toString());
        for (String forbidden : List.of(
                "new BlockPos(", "ChunkPos", "BlockPos.containing",
                "new Vec3i("
        )) {
            require(
                    !codeOnly.contains(forbidden),
                    "land production code must not hard-code coordinates: " + forbidden
            );
        }
        // Regions may only be built from caller requests or decoded
        // persistence — never from literal coordinate tuples in code.
        java.util.regex.Pattern literalRegion = java.util.regex.Pattern.compile(
                "new ParcelRegion\\(\\s*[-0-9]"
        );
        require(
                !literalRegion.matcher(codeOnly).find(),
                "land production code must not build a region from literal coordinates"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: strict deterministic codec (§7)
    // ------------------------------------------------------------------

    private static void testStrictDeterministicCodec() {
        LandNbtCodec codec = new LandNbtCodec();
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(7_000);
        LandRepository repository = repository(store);
        LandService service = service(repository, clock, holders(ALPHA_ID, BRAVO_ID));
        LandParcel parcel = service.createParcel(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL)
        ).parcel();
        service.setZoneType(ALPHA_ID, parcel.parcelId(), ZoneType.COMMERCIAL);
        service.grantUsage(ALPHA_ID, parcel.parcelId(),
                OwnerReference.forPlayer(BRAVO_ID), 10_000);
        service.createViolationReport(
                new ViolationDraft(parcel.parcelId(),
                        OwnerReference.forPlayer(BRAVO_ID), "Report")
        );

        LandStoreSnapshot snapshot = repository.snapshot();
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

        // Holder index is derived from parcels and survives the round-trip.
        CompoundTag root = codec.encode(snapshot);
        require(root.getCompound("HolderIndex").getAllKeys().size() == 1,
                "holder index carries exactly the grant holder");
        require(root.getCompound("HolderIndex").getList(
                        OwnerReference.forPlayer(BRAVO_ID).key(), net.minecraft.nbt.Tag.TAG_STRING)
                        .size() == 1,
                "holder index points at the parcel");
    }

    // ------------------------------------------------------------------
    // acceptance: corrupt snapshot fails closed (§7)
    // ------------------------------------------------------------------

    private static void testCorruptSnapshotFailClosed() {
        // Unknown store field.
        SavedDataBackedTestStore unknownStoreField = new SavedDataBackedTestStore();
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 0L);
        root.put("Parcels", new CompoundTag());
        root.put("HolderIndex", new CompoundTag());
        root.put("Reports", new CompoundTag());
        root.putString("Surprise", "x");
        unknownStoreField.putRaw(root);
        expectThrows(
                LandNbtException.class,
                () -> repository(unknownStoreField),
                "unknown store field rejects the load"
        );

        // Newer store version.
        SavedDataBackedTestStore newerStore = new SavedDataBackedTestStore();
        CompoundTag newerRoot = new CompoundTag();
        newerRoot.putInt("StoreVersion", 99);
        newerRoot.putLong("StoreRevision", 0L);
        newerRoot.put("Parcels", new CompoundTag());
        newerRoot.put("HolderIndex", new CompoundTag());
        newerRoot.put("Reports", new CompoundTag());
        newerStore.putRaw(newerRoot);
        expectThrows(
                LandNbtException.class,
                () -> repository(newerStore),
                "a newer store version is rejected fail-closed"
        );

        // Parcel ownership not REPUBLIC.
        SavedDataBackedTestStore wrongOwnership = new SavedDataBackedTestStore();
        CompoundTag parcelTag = baseParcelTag();
        parcelTag.putString("Ownership", "PLAYER");
        wrongOwnership.putRaw(storeWith(parcelTag));
        expectThrows(
                LandNbtException.class,
                () -> repository(wrongOwnership),
                "a parcel not owned by REPUBLIC is rejected at load"
        );

        // Key does not match the record parcel id.
        SavedDataBackedTestStore mismatch = new SavedDataBackedTestStore();
        CompoundTag mismatchParcel = baseParcelTag();
        mismatchParcel.putUUID("ParcelId", BRAVO_ID); // key is ALPHA_ID
        mismatch.putRaw(storeWith(mismatchParcel));
        expectThrows(
                LandNbtException.class,
                () -> repository(mismatch),
                "a Parcels key not matching the record parcel id rejects the load"
        );

        // Invalid region (min > max).
        SavedDataBackedTestStore invalidRegion = new SavedDataBackedTestStore();
        CompoundTag invalidRegionParcel = baseParcelTag();
        CompoundTag region = new CompoundTag();
        region.putInt("MinX", 50);
        region.putInt("MinY", 0);
        region.putInt("MinZ", 0);
        region.putInt("MaxX", 10);
        region.putInt("MaxY", 0);
        region.putInt("MaxZ", 0);
        invalidRegionParcel.put("Region", region);
        invalidRegion.putRaw(storeWith(invalidRegionParcel));
        expectThrows(
                LandNbtException.class,
                () -> repository(invalidRegion),
                "an invalid region is rejected at load"
        );

        // Orphan holder index entry.
        SavedDataBackedTestStore orphanIndex = new SavedDataBackedTestStore();
        CompoundTag orphanRoot = storeWith(baseParcelTag());
        CompoundTag index = new CompoundTag();
        ListTag parcels = new ListTag();
        parcels.add(StringTag.valueOf(ALPHA_ID.toString()));
        index.put("PLAYER_UUID:" + ALPHA_ID, parcels);
        index.put("PLAYER_UUID:" + BRAVO_ID, new ListTag()); // orphan holder
        orphanRoot.put("HolderIndex", index);
        orphanIndex.putRaw(orphanRoot);
        expectThrows(
                LandNbtException.class,
                () -> repository(orphanIndex),
                "an orphan holder index entry rejects the load"
        );

        // Inconsistent holder index (parcel listed under the wrong holder).
        SavedDataBackedTestStore wrongIndex = new SavedDataBackedTestStore();
        CompoundTag wrongRoot = storeWith(baseParcelTag());
        CompoundTag wrongIndexTag = new CompoundTag();
        ListTag wrongParcels = new ListTag();
        wrongParcels.add(StringTag.valueOf(ALPHA_ID.toString()));
        wrongIndexTag.put("PLAYER_UUID:" + BRAVO_ID, wrongParcels);
        wrongRoot.put("HolderIndex", wrongIndexTag);
        wrongIndex.putRaw(wrongRoot);
        expectThrows(
                LandNbtException.class,
                () -> repository(wrongIndex),
                "an inconsistent holder index rejects the load"
        );

        // Report referencing an unknown parcel.
        SavedDataBackedTestStore orphanReport = new SavedDataBackedTestStore();
        CompoundTag report = new CompoundTag();
        report.putInt("ReportVersion", 1);
        report.putLong("ReportId", 1L);
        report.putUUID("ParcelId", UUID.fromString("00000000-0000-0000-0000-0000000000dd"));
        report.put("Reporter", ownerTag(ALPHA_ID));
        report.putString("Description", "x");
        report.putLong("ReportedAt", 1_000L);
        report.putString("Status", "OPEN");
        CompoundTag reportsTag = new CompoundTag();
        reportsTag.put("1", report);
        CompoundTag orphanRoot2 = storeWith(baseParcelTag());
        orphanRoot2.put("Reports", reportsTag);
        orphanReport.putRaw(orphanRoot2);
        expectThrows(
                LandNbtException.class,
                () -> repository(orphanReport),
                "a report referencing an unknown parcel rejects the load"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: restart recovery (§7)
    // ------------------------------------------------------------------

    private static void testRestartPersistence() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(8_000);
        LandRepository firstRepository = repository(store);
        LandService firstService = service(
                firstRepository, clock, holders(ALPHA_ID, BRAVO_ID)
        );
        LandParcel parcel = firstService.createParcel(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL)
        ).parcel();
        firstService.setZoneType(ALPHA_ID, parcel.parcelId(), ZoneType.AGRICULTURAL);
        firstService.setAccess(ALPHA_ID, parcel.parcelId(), LandAccess.RESTRICTED);
        firstService.grantUsage(ALPHA_ID, parcel.parcelId(),
                OwnerReference.forPlayer(BRAVO_ID), 10_000);
        firstService.createViolationReport(
                new ViolationDraft(parcel.parcelId(),
                        OwnerReference.forPlayer(BRAVO_ID), "Survives restart")
        );

        LandRepository restarted = restartRepository(store);
        LandParcel reloaded = restarted.requireParcel(parcel.parcelId());
        require(reloaded.equals(firstRepository.requireParcel(parcel.parcelId())),
                "the same parcel is recovered after restart");
        require(reloaded.zoneType() == ZoneType.AGRICULTURAL, "zone survives restart");
        require(reloaded.access() == LandAccess.RESTRICTED, "access survives restart");
        require(reloaded.ownership() == LandOwnership.REPUBLIC,
                "ownership survives restart as REPUBLIC");
        require(reloaded.parcelRevision() == 4, "parcel revision survives restart");
        require(reloaded.usageRightOf(OwnerReference.forPlayer(BRAVO_ID)).isPresent(),
                "usage right survives restart");
        require(restarted.snapshot().storeRevision()
                        == firstRepository.snapshot().storeRevision(),
                "store revision survives restart");
        require(restarted.reportCount() == 1, "reports survive restart");
        require(restarted.findParcelAt(DIMENSION, 11, 21, 31).isPresent(),
                "coordinate lookup survives restart");
    }

    // ------------------------------------------------------------------
    // acceptance: no bulk enumeration API (§7)
    // ------------------------------------------------------------------

    private static void testNoEnumerationApi() {
        for (Class<?> type : List.of(LandService.class, LandRepository.class)) {
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Class<?> returnType = method.getReturnType();
                require(!Collection.class.isAssignableFrom(returnType)
                                && !Map.class.isAssignableFrom(returnType)
                                && !returnType.isArray()
                                && !Stream.class.isAssignableFrom(returnType),
                        "no bulk enumeration method in " + type.getSimpleName() + ": "
                                + method.getName());
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                require(!name.contains("findall") && !name.contains("list")
                                && !name.contains("values") && !name.contains("all"),
                        "no bulk enumeration method name in " + type.getSimpleName() + ": "
                                + method.getName());
            }
        }
    }

    // ------------------------------------------------------------------
    // acceptance: capacity and store failure fail closed (§7)
    // ------------------------------------------------------------------

    private static void testCapacityFailClosed() {
        LandLimits tight = new LandLimits(1, 1, 1, 500, 8 * 1024 * 1024);
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(9_000);
        LandRepository repository = repository(store, tight);
        LandService service = service(repository, clock, holders(ALPHA_ID, BRAVO_ID));
        LandParcel parcel = service.createParcel(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL)
        ).parcel();

        LandUnavailableException parcelFailure = expectThrows(
                LandUnavailableException.class,
                () -> service.createParcel(ALPHA_ID, createRequest(ZoneType.OTHER)),
                "parcel capacity is enforced fail-closed"
        );
        require(parcelFailure.failureCode().equals(
                        LandUnavailableException.CODE_CAPACITY_EXCEEDED),
                "parcel capacity failure carries the stable code");

        service.grantUsage(ALPHA_ID, parcel.parcelId(),
                OwnerReference.forPlayer(BRAVO_ID), 1_000);
        LandUnavailableException rightFailure = expectThrows(
                LandUnavailableException.class,
                () -> service.grantUsage(ALPHA_ID, parcel.parcelId(),
                        OwnerReference.forPlayer(ALPHA_ID), 1_000),
                "per-parcel usage-right capacity is enforced fail-closed"
        );
        require(rightFailure.failureCode().equals(
                        LandUnavailableException.CODE_CAPACITY_EXCEEDED),
                "right capacity failure carries the stable code");
    }

    private static void testStoreFailureAtomicity() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(11_000);
        LandRepository repository = repository(store);
        LandService service = service(repository, clock, holders(ALPHA_ID));
        store.setCommitFailureCode("INJECTED_FAILURE");

        LandUnavailableException failure = expectThrows(
                LandUnavailableException.class,
                () -> service.createParcel(ALPHA_ID, createRequest(ZoneType.RESIDENTIAL)),
                "a durable gate rejection fails closed"
        );
        require(failure.failureCode().equals(
                        LandUnavailableException.CODE_STORE_FAILURE),
                "store failure carries the stable code");
        require(repository.size() == 0, "a failed commit publishes no parcel");
        require(repository.snapshot().storeRevision() == 0L,
                "a failed commit advances no revision");
    }

    // ------------------------------------------------------------------
    // acceptance: events resolved at event time, fail closed (§7)
    // ------------------------------------------------------------------

    private static void testEventPolicyFailClosed() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(12_000);
        LandRepository repository = repository(store);
        FakeHolderDirectory holders = holders(ALPHA_ID, BRAVO_ID);
        LandService service = service(repository, clock, holders);
        LandParcel parcel = service.createParcel(
                ALPHA_ID, createRequest(ZoneType.RESIDENTIAL)
        ).parcel();

        LandEventPolicy policy = new LandEventPolicy(
                repository,
                new ConfigDrivenPermissionResolver(
                        LandPermissionConfig.DEFAULT, repository, holders, clock
                )
        );

        // Inside the parcel: allowed for a qualified player.
        require(policy.allowBuild(ALPHA_ID, DIMENSION, 11, 21, 31),
                "build inside the parcel is resolved at event time");
        require(policy.allowBreak(ALPHA_ID, DIMENSION, 15, 25, 35),
                "break inside the parcel is resolved at event time");
        require(policy.allowInteract(ALPHA_ID, DIMENSION, 11, 21, 31),
                "interact inside the parcel is resolved at event time");

        // Outside every parcel: fail closed.
        require(!policy.allowBuild(ALPHA_ID, DIMENSION, 999, 999, 999),
                "build outside registered parcels fails closed");
        require(!policy.allowBreak(ALPHA_ID, DIMENSION, 999, 999, 999),
                "break outside registered parcels fails closed");
        require(!policy.allowInteract(ALPHA_ID, DIMENSION, 999, 999, 999),
                "interact outside registered parcels fails closed");

        // Wrong dimension: fail closed even at matching coordinates.
        require(!policy.allowBuild(ALPHA_ID, "minecraft:the_nether", 11, 21, 31),
                "a different dimension fails closed");

        // Policy responds to live changes (access tightened).
        service.setAccess(ALPHA_ID, parcel.parcelId(), LandAccess.PRIVATE);
        require(!policy.allowBuild(ALPHA_ID, DIMENSION, 11, 21, 31),
                "a cached decision is never authoritative: tightening access "
                        + "denies the same position immediately");
        service.grantUsage(ALPHA_ID, parcel.parcelId(),
                OwnerReference.forPlayer(BRAVO_ID), 5_000);
        require(policy.allowBuild(BRAVO_ID, DIMENSION, 11, 21, 31),
                "a granted right is honored at the next event");
    }

    // ------------------------------------------------------------------
    // module contract checks
    // ------------------------------------------------------------------

    private static void testModuleContract() {
        require(LandRepository.MODULE_DATA_KEY.equals("land"),
                "land owns exactly the approved namespace");
        require(LandModule.MODULE_ID.value().equals("land"),
                "land module id is 'land'");
        ModuleDefinition definition = new ModuleDefinition(
                LandModule.MODULE_ID,
                new ModuleMetadata("Land", "1.0.0", Optional.empty(), Optional.empty()),
                Set.of(PlayerDataModule.MODULE_ID, SubjectRegistryModule.MODULE_ID),
                Set.of(),
                60,
                LandModule::new
        );
        require(definition.requiredDependencies().equals(
                        Set.of(PlayerDataModule.MODULE_ID, SubjectRegistryModule.MODULE_ID)),
                "land depends only on player-data and subject-registry");
        for (ModuleId dependency : definition.requiredDependencies()) {
            String value = dependency.value();
            require(!value.contains("emg") && !value.contains("emergency")
                            && !value.contains("audit") && !value.contains("economy")
                            && !value.contains("citizen") && !value.contains("justice"),
                    "land never depends on later-phase or political namespaces");
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static LandRepository repository(LandStore store) {
        return new LandRepository(
                store, new LandNbtCodec(), LandLimits.DEFAULT,
                new SequentialParcelIdSource()
        );
    }

    private static LandRepository repository(LandStore store, LandLimits limits) {
        return new LandRepository(
                store, new LandNbtCodec(), limits,
                new SequentialParcelIdSource()
        );
    }

    private static LandRepository restartRepository(SavedDataBackedTestStore store) {
        return repository(store.restart());
    }

    private static LandService service(
            LandRepository repository,
            LongSupplier clock,
            FakeHolderDirectory holders
    ) {
        PermissionResolver resolver = new ConfigDrivenPermissionResolver(
                LandPermissionConfig.DEFAULT, repository, holders, clock
        );
        return new DefaultLandService(repository, clock, holders, resolver);
    }

    private static FakeHolderDirectory holders(UUID... players) {
        FakeHolderDirectory holders = new FakeHolderDirectory();
        for (UUID player : players) {
            holders.add(player);
        }
        return holders;
    }

    private static CreateParcelRequest createRequest(ZoneType zoneType) {
        return new CreateParcelRequest(DIMENSION, region(), zoneType);
    }

    private static ParcelRegion region() {
        return new ParcelRegion(10, 20, 30, 40, 50, 60);
    }

    private static LandParcel parcel(ParcelId parcelId) {
        return new LandParcel(
                LandParcel.CURRENT_SCHEMA_VERSION,
                parcelId,
                DIMENSION,
                region(),
                ZoneType.RESIDENTIAL,
                LandOwnership.REPUBLIC,
                LandAccess.PUBLIC,
                Map.of(),
                1
        );
    }

    private static ParcelId createParcel() {
        return ParcelId.of(UUID.fromString("00000000-0000-0000-0000-0000000000ee"));
    }

    /** Builds a valid base parcel tag under the ALPHA key with no usage rights. */
    private static CompoundTag baseParcelTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ParcelVersion", 1);
        tag.putUUID("ParcelId", ALPHA_ID);
        tag.putString("Dimension", DIMENSION);
        CompoundTag region = new CompoundTag();
        region.putInt("MinX", 10);
        region.putInt("MinY", 20);
        region.putInt("MinZ", 30);
        region.putInt("MaxX", 40);
        region.putInt("MaxY", 50);
        region.putInt("MaxZ", 60);
        tag.put("Region", region);
        tag.putString("ZoneType", "RESIDENTIAL");
        tag.putString("Ownership", "REPUBLIC");
        tag.putString("Access", "PUBLIC");
        tag.put("UsageRights", new CompoundTag());
        tag.putLong("ParcelRevision", 1L);
        return tag;
    }

    private static CompoundTag storeWith(CompoundTag parcelTag) {
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 1L);
        CompoundTag parcels = new CompoundTag();
        parcels.put(ALPHA_ID.toString(), parcelTag);
        root.put("Parcels", parcels);
        root.put("HolderIndex", new CompoundTag());
        root.put("Reports", new CompoundTag());
        return root;
    }

    private static CompoundTag ownerTag(UUID playerId) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", "PLAYER_UUID");
        tag.putString("OwnerId", playerId.toString());
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

    private static final class SavedDataBackedTestStore implements LandStore {
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
            return savedData.getModuleData(LandRepository.MODULE_DATA_KEY).copy();
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
                        LandRepository.MODULE_DATA_KEY,
                        0L,
                        0L,
                        commitFailureCode
                );
            }
            savedData.putModuleData(
                    LandRepository.MODULE_DATA_KEY,
                    snapshot.copy()
            );
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    LandRepository.MODULE_DATA_KEY,
                    1L,
                    0L,
                    ""
            );
        }

        private void putRaw(CompoundTag raw) {
            savedData.putModuleData(LandRepository.MODULE_DATA_KEY, raw);
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

    private static final class SequentialParcelIdSource implements ParcelIdSource {
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
}
