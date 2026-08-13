package com.fontainerepublic.server.institutionaccess;

import com.fontainerepublic.core.CoreManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.core.ModSavedData;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.audit.api.AuditDraft;
import com.fontainerepublic.server.audit.api.AuditPage;
import com.fontainerepublic.server.audit.api.AuditReceipt;
import com.fontainerepublic.server.audit.api.AuditService;
import com.fontainerepublic.server.audit.model.AuditActorType;
import com.fontainerepublic.server.audit.model.AuditEntry;
import com.fontainerepublic.server.command.CommandBootstrap;
import com.fontainerepublic.server.command.CommandFeedback;
import com.fontainerepublic.server.command.CommandRuntimeResolver;
import com.fontainerepublic.server.command.registration.CommandContributionRegistry;
import com.fontainerepublic.server.institutionaccess.api.FacilityChangeKind;
import com.fontainerepublic.server.institutionaccess.api.FacilityReceipt;
import com.fontainerepublic.server.institutionaccess.api.FacilityRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.api.ValidationResult;
import com.fontainerepublic.server.institutionaccess.api.ZoneChangeKind;
import com.fontainerepublic.server.institutionaccess.api.ZoneReceipt;
import com.fontainerepublic.server.institutionaccess.api.ZoneRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.Facility;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.FacilityState;
import com.fontainerepublic.server.institutionaccess.model.InstitutionType;
import com.fontainerepublic.server.institutionaccess.model.WorkflowKind;
import com.fontainerepublic.server.institutionaccess.model.Zone;
import com.fontainerepublic.server.institutionaccess.model.ZoneId;
import com.fontainerepublic.server.institutionaccess.model.ZoneKind;
import com.fontainerepublic.server.institutionaccess.model.ZoneRegion;
import com.fontainerepublic.server.institutionaccess.model.ZoneState;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessLimits;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessNbtCodec;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessNbtException;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessRepository;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessStore;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessStoreSnapshot;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessUnavailableException;
import com.fontainerepublic.server.institutionaccess.service.ActorResolver;
import com.fontainerepublic.server.institutionaccess.service.DefaultInstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.service.InstitutionAccessConfig;
import com.fontainerepublic.server.land.api.CreateParcelRequest;
import com.fontainerepublic.server.land.api.LandReceipt;
import com.fontainerepublic.server.land.api.LandService;
import com.fontainerepublic.server.land.api.UsageReceipt;
import com.fontainerepublic.server.land.api.ViolationDraft;
import com.fontainerepublic.server.land.api.ViolationReceipt;
import com.fontainerepublic.server.land.model.LandAccess;
import com.fontainerepublic.server.land.model.LandOwnership;
import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.land.model.ParcelRegion;
import com.fontainerepublic.server.land.model.ZoneType;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * Dependency-free validation entry point for FR-INST-002-B (zone-based shared
 * institution access boundary). Exercises the FR-INST-002-B §7 acceptance
 * matrix with an injectable store and SavedData-backed restart simulation:
 * facility/zone registration with FR-LAND parcel validation, the small-size
 * budget, the zone kind/capability match, the lifecycle state machine; on-site
 * context issue/validate/expiry/revision binding; leave-invalidation without
 * restore; single-use consumption (public on submit, high-risk at the final
 * boundary); the three workflow default parameters per FR-INST-001-B §3; the
 * bounded presence boundary (active-context players only); injection failure
 * without publish; restart clearing contexts while recovering the directory;
 * strict deterministic codec; no hard-coded coordinates; no terminal-model
 * references; a command tree without privilege escalation; and the v1
 * (terminal-model) root rejection.
 */
public final class InstitutionAccessFoundationTestMain {

    private static final String PROJECT_DIR_PROPERTY = "fontainerepublic.projectDir";
    private static final String DIMENSION = "minecraft:overworld";

    private static final UUID ALPHA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** Parcel A region: x 10..30, y 60..70, z 10..30. */
    private static final ParcelRegion REGION_A = new ParcelRegion(10, 60, 10, 30, 70, 30);
    /** Parcel B region: x 100..120, y 60..70, z 100..120. */
    private static final ParcelRegion REGION_B = new ParcelRegion(100, 60, 100, 120, 70, 120);

    /** Small bounded zone region A inside parcel A (16×8×16). */
    private static final ZoneRegion ZONE_A = new ZoneRegion(DIMENSION, 12, 62, 12, 27, 69, 27);
    /** Small bounded zone region B inside parcel A (16×8×16). */
    private static final ZoneRegion ZONE_B = new ZoneRegion(DIMENSION, 13, 62, 13, 28, 69, 28);
    /** Small bounded zone region C inside parcel B (16×8×16). */
    private static final ZoneRegion ZONE_C = new ZoneRegion(DIMENSION, 105, 62, 105, 120, 69, 120);

    /** A point inside ZONE_A / ZONE_B. */
    private static final int IN_ZONE_X = 20;
    private static final int IN_ZONE_Y = 64;
    private static final int IN_ZONE_Z = 20;

    /** A point inside parcel A but outside any zone region. */
    private static final int OUT_ZONE_X = 11;
    private static final int OUT_ZONE_Y = 64;
    private static final int OUT_ZONE_Z = 11;

    private InstitutionAccessFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testFacilityRegistrationParcelValidation();
        testZoneRegistrationValidation();
        testFacilityStateMachine();
        testIssueContextValidation();
        testValidateAtMutation();
        testSingleUseAndFailurePolicy();
        testWorkflowDefaults();
        testLeaveInvalidatesNoRestore();
        testPresenceBoundary();
        testInjectionFailureNoPublish();
        testRestartClearsContexts();
        testStrictDeterministicCodec();
        testCorruptSnapshotFailClosed();
        testV1RootRejected();
        testNoHardcodedCoordinates();
        testNoTerminalReferences();
        testCommandTreeNoEscalation();
        testModuleContract();
        System.out.println(
                "[FR-INST-002-B] Institution access foundation validation passed"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: facility registration — parcel validation, single binding
    // ------------------------------------------------------------------

    private static void testFacilityRegistrationParcelValidation() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        FakeLandService land = landWith(parcelA());
        InstitutionAccessService service = service(store, land, clock);

        // Unknown parcel is rejected; nothing is published.
        ParcelId unknown = ParcelId.of(
                UUID.fromString("00000000-0000-0000-0000-0000000000ff")
        );
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.registerFacility(
                        ALPHA_ID,
                        new FacilityRegistrationRequest(
                                InstitutionType.PARLIAMENT, unknown
                        )
                ),
                "registration on an unknown parcel must fail closed"
        );
        require(store.commitCount() == 0,
                "failed registration must not reach the durable store");

        FacilityReceipt receipt = service.registerFacility(
                ALPHA_ID,
                new FacilityRegistrationRequest(
                        InstitutionType.PARLIAMENT, parcelA().parcelId()
                )
        );
        require(receipt.applied(), "facility registration is applied");
        require(receipt.kind() == FacilityChangeKind.FACILITY_REGISTERED,
                "receipt kind is FACILITY_REGISTERED");
        require(receipt.facility().state() == FacilityState.ACTIVE,
                "a registered facility starts ACTIVE");
        require(receipt.facility().facilityRevision() == 1,
                "a registered facility starts at revision 1");
        require(receipt.facility().institutionType() == InstitutionType.PARLIAMENT,
                "facility institution type is applied");
        require(store.commitCount() == 1,
                "registration commits exactly one snapshot");

        // Same parcel cannot be bound to a second facility.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.registerFacility(
                        ALPHA_ID,
                        new FacilityRegistrationRequest(
                                InstitutionType.GOVERNMENT, parcelA().parcelId()
                        )
                ),
                "a parcel bound to one facility cannot be bound to another"
        );

        // Actor resolution is mandatory (unprovisioned player rejected).
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.registerFacility(
                        UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
                        new FacilityRegistrationRequest(
                                InstitutionType.COURT, parcelB().parcelId()
                        )
                ),
                "an unprovisioned actor must fail closed"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: zone registration — parcel containment, size budget,
    // kind/capability match, state machine (§7)
    // ------------------------------------------------------------------

    private static void testZoneRegistrationValidation() {
        MutableClock clock = new MutableClock(2_000);
        FakeLandService land = landWith(parcelA(), parcelB());
        InstitutionAccessService service = service(new SavedDataBackedTestStore(),
                land, clock);
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        FacilityId bank = registerFacility(
                service, InstitutionType.CENTRAL_BANK, parcelB().parcelId()
        );

        // Zone region outside the facility parcel region is rejected.
        ZoneRegion outside = new ZoneRegion(
                DIMENSION, 40, 60, 40, 50, 64, 50
        );
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.addZone(
                        ALPHA_ID,
                        new ZoneRegistrationRequest(
                                parliament, ZoneKind.PUBLIC, outside,
                                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
                        )
                ),
                "a zone outside its facility parcel region must be rejected"
        );

        // Zone in the wrong dimension is rejected even when coordinates
        // overlap the region in another dimension.
        ZoneRegion wrongDimension = new ZoneRegion(
                "minecraft:the_nether", 20, 62, 20, 27, 69, 27
        );
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.addZone(
                        ALPHA_ID,
                        new ZoneRegistrationRequest(
                                parliament, ZoneKind.PUBLIC, wrongDimension,
                                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
                        )
                ),
                "a zone dimension not matching the parcel must be rejected"
        );

        // A zone covering the whole parcel exceeds the small-size budget.
        ZoneRegion oversized = new ZoneRegion(
                DIMENSION, 10, 60, 10, 30, 70, 30
        );
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.addZone(
                        ALPHA_ID,
                        new ZoneRegistrationRequest(
                                parliament, ZoneKind.PUBLIC, oversized,
                                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
                        )
                ),
                "a zone beyond the small-size budget must be rejected"
        );

        // Registration on a non-ACTIVE facility is rejected.
        service.suspendFacility(ALPHA_ID, parliament);
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.addZone(
                        ALPHA_ID,
                        new ZoneRegistrationRequest(
                                parliament, ZoneKind.PUBLIC, ZONE_A,
                                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
                        )
                ),
                "zones may only be added to an ACTIVE facility"
        );
        service.activateFacility(ALPHA_ID, parliament);

        // Empty capability set is rejected by the request contract.
        expectThrows(
                IllegalArgumentException.class,
                () -> new ZoneRegistrationRequest(
                        parliament, ZoneKind.PUBLIC, ZONE_A, Set.of()
                ),
                "an empty capability set must be rejected at construction"
        );

        // Capability set not matching the zone kind is rejected.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.addZone(
                        ALPHA_ID,
                        new ZoneRegistrationRequest(
                                parliament, ZoneKind.PUBLIC, ZONE_A,
                                Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY)
                        )
                ),
                "a PUBLIC zone may not allow official-duty capabilities"
        );

        ZoneReceipt receipt = service.addZone(
                ALPHA_ID,
                new ZoneRegistrationRequest(
                        parliament, ZoneKind.PUBLIC, ZONE_A,
                        Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
                )
        );
        require(receipt.applied(), "zone registration is applied");
        require(receipt.kind() == ZoneChangeKind.ZONE_ADDED,
                "receipt kind is ZONE_ADDED");
        require(receipt.zone().state() == ZoneState.ACTIVE,
                "a registered zone starts ACTIVE");
        require(receipt.zone().zoneRevision() == 1,
                "a registered zone starts at revision 1");
        require(receipt.zone().institutionType() == InstitutionType.PARLIAMENT,
                "the zone inherits the facility institution type");
        require(receipt.zone().kind() == ZoneKind.PUBLIC,
                "the zone kind is applied");
        require(receipt.zone().region().equals(ZONE_A),
                "the zone region is applied");
        require(receipt.zone().capabilitySet().equals(
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)),
                "the zone capability set is applied");

        // Overlapping regions are allowed (zones are areas, not anchored
        // positions; no anti-clone constraint exists in the zone model).
        ZoneId overlapping = addZone(
                service, parliament, ZoneKind.OFFICIAL, ZONE_B,
                Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY)
        );
        require(service.getZone(overlapping).isPresent(),
                "a second zone may share the facility");

        // A zone on the bank parcel cannot be anchored to the parliament
        // facility (region check across parcels).
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.addZone(
                        ALPHA_ID,
                        new ZoneRegistrationRequest(
                                parliament, ZoneKind.PUBLIC, ZONE_C,
                                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
                        )
                ),
                "a zone outside the referenced facility region must be rejected"
        );

        // The bank facility still accepts its own zone.
        ZoneId bankZone = addZone(
                service, bank, ZoneKind.PUBLIC, ZONE_C,
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
        );
        require(service.getZone(bankZone).get().facilityId().equals(bank),
                "the bank zone anchors to the bank facility");

        // resize: onto an out-of-parcel region is rejected; a valid resize
        // increments the revision exactly once.
        ZoneId publicZone = addZone(
                service, parliament, ZoneKind.PUBLIC, ZONE_A,
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
        );
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.resizeZone(
                        ALPHA_ID, publicZone,
                        new ZoneRegion(DIMENSION, 40, 60, 40, 50, 64, 50)
                ),
                "resizing outside the parcel region must be rejected"
        );
        ZoneRegion grown = new ZoneRegion(
                DIMENSION, 12, 62, 12, 27, 69, 28
        );
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.resizeZone(ALPHA_ID, publicZone, grown),
                "resizing beyond the small-size budget must be rejected"
        );
        ZoneReceipt resized = service.resizeZone(
                ALPHA_ID, publicZone, ZONE_B
        );
        require(resized.applied(), "resize applies");
        require(resized.zone().region().equals(ZONE_B), "the new region applies");
        require(resized.zone().zoneRevision() == 2,
                "resize increments the zone revision exactly once");

        // set-kind: a capability set not allowed by the new kind is rejected;
        // a valid kind change increments the revision exactly once.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.setZoneKind(ALPHA_ID, publicZone, ZoneKind.OFFICIAL),
                "a PUBLIC zone without official-duty capabilities cannot become "
                        + "an OFFICIAL zone"
        );
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.setZoneKind(ALPHA_ID, publicZone, ZoneKind.SECURE),
                "a PUBLIC zone without official-duty capabilities cannot become "
                        + "a SECURE zone"
        );
        ZoneReceipt rekinded = service.setZoneKind(
                ALPHA_ID, overlapping, ZoneKind.SECURE
        );
        require(rekinded.applied(), "set-kind applies");
        require(rekinded.zone().kind() == ZoneKind.SECURE, "the new kind applies");
        require(rekinded.zone().zoneRevision() == 2,
                "set-kind increments the zone revision exactly once");

        // Zone state machine: suspend -> activate -> remove.
        ZoneReceipt suspended = service.suspendZone(ALPHA_ID, publicZone);
        require(suspended.zone().state() == ZoneState.SUSPENDED,
                "suspend moves the zone to SUSPENDED");
        require(suspended.zone().zoneRevision() == 3,
                "suspend increments the revision once");
        require(!service.suspendZone(ALPHA_ID, publicZone).applied(),
                "suspending a suspended zone is a no-op");
        require(!service.suspendZone(ALPHA_ID, publicZone).zone().equals(suspended.zone())
                        || service.suspendZone(ALPHA_ID, publicZone).zone().zoneRevision() == 3,
                "a no-op suspend commits nothing");
        ZoneReceipt activated = service.activateZone(ALPHA_ID, publicZone);
        require(activated.zone().state() == ZoneState.ACTIVE,
                "activate restores ACTIVE");
        require(activated.zone().zoneRevision() == 4,
                "activate increments the revision once");
        ZoneReceipt removed = service.removeZone(ALPHA_ID, publicZone);
        require(removed.kind() == ZoneChangeKind.ZONE_REMOVED, "removal kind applies");
        require(service.getZone(publicZone).isEmpty(),
                "the removed zone is gone from the directory");
    }

    // ------------------------------------------------------------------
    // acceptance: facility lifecycle state machine (§7)
    // ------------------------------------------------------------------

    private static void testFacilityStateMachine() {
        MutableClock clock = new MutableClock(3_000);
        InstitutionAccessService service = service(
                new SavedDataBackedTestStore(),
                landWith(parcelA(), parcelB(), parcelC()), clock
        );
        FacilityId id = registerFacility(
                service, InstitutionType.GOVERNMENT, parcelA().parcelId()
        );
        FacilityId other = registerFacility(
                service, InstitutionType.COURT, parcelC().parcelId()
        );

        // ACTIVE -> SUSPENDED -> ACTIVE.
        FacilityReceipt suspended = service.suspendFacility(ALPHA_ID, id);
        require(suspended.applied(), "suspend applies");
        require(suspended.facility().state() == FacilityState.SUSPENDED,
                "facility is SUSPENDED");
        require(suspended.facility().facilityRevision() == 2,
                "suspend increments the facility revision once");

        FacilityReceipt reSuspended = service.suspendFacility(ALPHA_ID, id);
        require(!reSuspended.applied(), "suspend of a suspended facility is a no-op");
        require(reSuspended.facility().facilityRevision() == 2,
                "a no-op suspend commits nothing");

        FacilityReceipt activated = service.activateFacility(ALPHA_ID, id);
        require(activated.facility().state() == FacilityState.ACTIVE,
                "activate restores ACTIVE");

        // Relocation: ACTIVE -> RELOCATING on a new unbound parcel.
        FacilityReceipt relocated = service.relocateFacility(
                ALPHA_ID, id, parcelB().parcelId()
        );
        require(relocated.applied(), "relocate applies");
        require(relocated.facility().state() == FacilityState.RELOCATING,
                "relocation enters RELOCATING");
        require(relocated.facility().parcelId().equals(parcelB().parcelId()),
                "relocation binds the new parcel");
        require(relocated.facility().facilityRevision()
                        == activated.facility().facilityRevision() + 1,
                "relocation increments the revision once");

        // Relocation to the current parcel is rejected.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.relocateFacility(ALPHA_ID, id, parcelB().parcelId()),
                "relocating onto the current parcel must be rejected"
        );
        // Relocation onto a parcel bound to another facility is rejected.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.relocateFacility(ALPHA_ID, id, parcelC().parcelId()),
                "relocating onto a bound parcel must be rejected"
        );
        require(other.equals(other), "the second facility stays bound to parcelC");

        // RELOCATING -> ACTIVE via activate, then DISABLED (final state).
        service.activateFacility(ALPHA_ID, id);
        FacilityReceipt disabled = service.disableFacility(ALPHA_ID, id);
        require(disabled.facility().state() == FacilityState.DISABLED,
                "disable moves the facility to DISABLED");
        require(!service.disableFacility(ALPHA_ID, id).applied(),
                "disabling a disabled facility is a no-op");

        // DISABLED is final: no suspend/activate/relocate.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.suspendFacility(ALPHA_ID, id),
                "a disabled facility cannot be suspended"
        );
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.activateFacility(ALPHA_ID, id),
                "a disabled facility cannot be activated"
        );
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.relocateFacility(
                        ALPHA_ID, id,
                        ParcelId.of(
                                UUID.fromString(
                                        "00000000-0000-0000-0000-0000000000fe"
                                )
                        )
                ),
                "a disabled facility cannot be relocated"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: context issue — zone presence chain (§7)
    // ------------------------------------------------------------------

    private static void testIssueContextValidation() {
        MutableClock clock = new MutableClock(10_000);
        FakeLandService land = landWith(parcelA());
        DefaultInstitutionAccessService service = service(
                new SavedDataBackedTestStore(), land, clock
        );
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        ZoneId publicZone = addZone(
                service, parliament, ZoneKind.PUBLIC, ZONE_A,
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
        );
        ZoneId officialZone = addZone(
                service, parliament, ZoneKind.OFFICIAL, ZONE_B,
                Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY)
        );
        // Overlapping SECURE zone (areas may overlap; kind selects workflow).
        ZoneId secureZone = addZone(
                service, parliament, ZoneKind.SECURE, ZONE_B,
                Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY)
        );

        // Unknown zone is rejected.
        ZoneId unknown = ZoneId.of(
                UUID.fromString("00000000-0000-0000-0000-0000000000dd")
        );
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.issueOnSiteContext(
                        ALPHA_ID, unknown, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                        DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
                ),
                "issue on an unknown zone must fail closed"
        );

        // A suspended zone cannot anchor contexts.
        service.suspendZone(ALPHA_ID, officialZone);
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.issueOnSiteContext(
                        ALPHA_ID, officialZone, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                        DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
                ),
                "a suspended zone must reject issue"
        );
        service.activateZone(ALPHA_ID, officialZone);

        // A suspended facility cannot anchor contexts.
        service.suspendFacility(ALPHA_ID, parliament);
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.issueOnSiteContext(
                        ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                        DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
                ),
                "a suspended facility must reject issue"
        );
        service.activateFacility(ALPHA_ID, parliament);

        // Remote capabilities may never anchor an on-site context.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.issueOnSiteContext(
                        ALPHA_ID, publicZone, CapabilityClass.REMOTE_INFORMATION,
                        DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
                ),
                "remote capabilities must be rejected for on-site contexts"
        );

        // A capability not in the zone capability set is rejected.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.issueOnSiteContext(
                        ALPHA_ID, publicZone, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                        DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
                ),
                "a capability outside the zone capability set must be rejected"
        );

        // Dimension mismatch is rejected.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.issueOnSiteContext(
                        ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                        "minecraft:the_nether", IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
                ),
                "a dimension mismatch must be rejected"
        );

        // Presence is zone containment: a player inside the parcel but
        // outside the zone region is rejected.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.issueOnSiteContext(
                        ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                        DIMENSION, OUT_ZONE_X, OUT_ZONE_Y, OUT_ZONE_Z
                ),
                "a player outside the zone region must be rejected"
        );

        // Public workflow: presence inside a PUBLIC zone issues a 2-minute
        // single-use context.
        OnSiteContext publicContext = service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(publicContext.workflowKind() == WorkflowKind.PUBLIC,
                "a PUBLIC zone serves the public workflow");
        require(publicContext.zoneId().equals(publicZone),
                "the context binds the zone id");
        require(publicContext.facilityRevision()
                        == service.getFacility(parliament).get().facilityRevision(),
                "the context binds the facility revision");
        require(publicContext.zoneRevision()
                        == service.getZone(publicZone).get().zoneRevision(),
                "the context binds the zone revision");
        require(publicContext.expiryTime() - publicContext.issueTime()
                        == InstitutionAccessConfig.DEFAULT.publicContextLifetimeMillis(),
                "the public context lifetime is the configured 2 minutes");
        require(publicContext.dimension().equals(DIMENSION),
                "the context records the source dimension");
        require(publicContext.blockX() == IN_ZONE_X
                        && publicContext.blockY() == IN_ZONE_Y
                        && publicContext.blockZ() == IN_ZONE_Z,
                "the context records the source position");

        // High-risk workflow requires an already-valid official session:
        // rejected while the player holds no official routine session.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.issueOnSiteContext(
                        ALPHA_ID, secureZone, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                        DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
                ),
                "a high-risk authorization without an official session must be rejected"
        );

        // Official routine workflow: 60-minute session lifetime.
        OnSiteContext officialContext = service.issueOnSiteContext(
                ALPHA_ID, officialZone, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(officialContext.workflowKind() == WorkflowKind.OFFICIAL_ROUTINE,
                "an OFFICIAL zone serves the official routine workflow");
        require(officialContext.expiryTime() - officialContext.issueTime()
                        == InstitutionAccessConfig.DEFAULT.officialHardLimitMillis(),
                "the official session lifetime is the configured 60 minutes");

        // With a valid official session the SECURE zone issues a 30-second
        // single-use high-risk authorization.
        OnSiteContext highRiskContext = service.issueOnSiteContext(
                ALPHA_ID, secureZone, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(highRiskContext.workflowKind() == WorkflowKind.HIGH_RISK,
                "a SECURE zone serves the high-risk workflow");
        require(highRiskContext.expiryTime() - highRiskContext.issueTime()
                        == InstitutionAccessConfig.DEFAULT.highRiskLifetimeMillis(),
                "the high-risk authorization lifetime is the configured 30 seconds");
        require(service.activeContextsOf(ALPHA_ID).size() == 3,
                "the player holds the public, official, and high-risk contexts");
    }

    // ------------------------------------------------------------------
    // acceptance: final mutation-time revalidation (§7)
    // ------------------------------------------------------------------

    private static void testValidateAtMutation() {
        MutableClock clock = new MutableClock(20_000);
        FakeLandService land = landWith(parcelA());
        DefaultInstitutionAccessService service = service(
                new SavedDataBackedTestStore(), land, clock
        );
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        ZoneId publicZone = addZone(
                service, parliament, ZoneKind.PUBLIC, ZONE_A,
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
        );
        OnSiteContext context = service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );

        // Null context is rejected.
        require(!service.validateAtMutation(
                null, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        ).valid(), "a null context must not validate");

        // Capability mismatch.
        ValidationResult wrongCapability = service.validateAtMutation(
                context, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(!wrongCapability.valid()
                        && wrongCapability.reason().equals(
                        ValidationResult.REASON_CAPABILITY_MISMATCH),
                "a capability mismatch must be rejected with CAPABILITY_MISMATCH");

        // Expiry.
        clock.setNow(clock.now() + InstitutionAccessConfig.DEFAULT
                .publicContextLifetimeMillis() + 1);
        require(!service.validateAtMutation(
                context, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        ).valid(), "an expired context must not validate");
        clock.setNow(20_000);

        // Facility lifecycle change invalidates bound contexts immediately;
        // the revision binding is an additional defense-in-depth at the
        // mutation boundary.
        OnSiteContext beforeSuspend = service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(beforeSuspend.facilityRevision()
                        == service.getFacility(parliament)
                        .map(Facility::facilityRevision).orElse(-1L),
                "the context binds the facility revision at issue time");
        service.suspendFacility(ALPHA_ID, parliament);
        ValidationResult afterSuspend = service.validateAtMutation(
                beforeSuspend, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(!afterSuspend.valid()
                        && afterSuspend.reason().equals(
                        ValidationResult.REASON_INVALIDATED),
                "a suspended facility invalidates bound contexts immediately");
        service.activateFacility(ALPHA_ID, parliament);
        require(!service.validateAtMutation(
                beforeSuspend, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        ).valid(), "reactivating the facility does not restore old contexts");

        // The final boundary rechecks directory state independently of event
        // invalidation (defense-in-depth): a directory change that bypassed
        // the lifecycle events still fails with the specific reason. The
        // repository is driven directly to simulate that bypass.
        OnSiteContext againstSuspendedFacility = service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        repositoryOf(service).suspendFacility(parliament);
        ValidationResult facilitySuspended = service.validateAtMutation(
                againstSuspendedFacility, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(!facilitySuspended.valid()
                        && facilitySuspended.reason().equals(
                        ValidationResult.REASON_FACILITY_NOT_ACTIVE),
                "a suspended facility must reject with FACILITY_NOT_ACTIVE");
        repositoryOf(service).activateFacility(parliament);

        // Facility revision change invalidates: a suspend/activate cycle
        // moves the revision while the facility is ACTIVE again; the context
        // was issued before the cycle and stays active in the runtime
        // registry (repository bypasses the lifecycle invalidation).
        OnSiteContext beforeRevisionCycle = service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        repositoryOf(service).suspendFacility(parliament);
        repositoryOf(service).activateFacility(parliament);
        ValidationResult facilityRevised = service.validateAtMutation(
                beforeRevisionCycle, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(!facilityRevised.valid()
                        && facilityRevised.reason().equals(
                        ValidationResult.REASON_FACILITY_REVISION),
                "a facility revision change must reject with FACILITY_REVISION");

        // Zone state change bypassing events rejects with ZONE_NOT_ACTIVE.
        OnSiteContext beforeZoneSuspend = service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        repositoryOf(service).suspendZone(publicZone);
        ValidationResult zoneSuspended = service.validateAtMutation(
                beforeZoneSuspend, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(!zoneSuspended.valid()
                        && zoneSuspended.reason().equals(
                        ValidationResult.REASON_ZONE_NOT_ACTIVE),
                "a suspended zone must reject with ZONE_NOT_ACTIVE");
        repositoryOf(service).activateZone(publicZone);

        // Zone revision change (resize) invalidates.
        OnSiteContext beforeResize = service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        repositoryOf(service).resizeZone(publicZone, ZONE_B);
        ValidationResult zoneRevised = service.validateAtMutation(
                beforeResize, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(!zoneRevised.valid()
                        && zoneRevised.reason().equals(
                        ValidationResult.REASON_ZONE_REVISION),
                "a zone revision change must reject with ZONE_REVISION");

        // Zone removal invalidates.
        OnSiteContext beforeRemove = service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        repositoryOf(service).removeZone(publicZone);
        ValidationResult zoneRemoved = service.validateAtMutation(
                beforeRemove, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(!zoneRemoved.valid()
                        && zoneRemoved.reason().equals(
                        ValidationResult.REASON_ZONE_NOT_ACTIVE),
                "a removed zone must reject with ZONE_NOT_ACTIVE");

        // Parcel shrinking out from under the zone rejects (spatial
        // revalidation against the authoritative FR-LAND parcel).
        ZoneId freshZone = addZone(
                service, parliament, ZoneKind.PUBLIC, ZONE_A,
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
        );
        OnSiteContext beforeShrink = service.issueOnSiteContext(
                ALPHA_ID, freshZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        LandParcel shrunk = new LandParcel(
                LandParcel.CURRENT_SCHEMA_VERSION,
                parcelA().parcelId(),
                DIMENSION,
                new ParcelRegion(10, 60, 10, 25, 70, 25),
                ZoneType.GOVERNMENT,
                LandOwnership.REPUBLIC,
                LandAccess.PUBLIC,
                Map.of(),
                2L
        );
        land.setParcel(shrunk);
        ValidationResult outsideParcel = service.validateAtMutation(
                beforeShrink, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(!outsideParcel.valid()
                        && outsideParcel.reason().equals(
                        ValidationResult.REASON_ZONE_NOT_IN_REGION),
                "a zone region outside the parcel must reject with "
                        + "ZONE_NOT_IN_REGION");
        land.setParcel(parcelA());

        // Player leaving the zone region rejects.
        OnSiteContext fresh = service.issueOnSiteContext(
                ALPHA_ID, freshZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        ValidationResult outOfRange = service.validateAtMutation(
                fresh, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, OUT_ZONE_X, OUT_ZONE_Y, OUT_ZONE_Z
        );
        require(!outOfRange.valid()
                        && outOfRange.reason().equals(
                        ValidationResult.REASON_PLAYER_OUT_OF_RANGE),
                "a player outside the zone region must reject with "
                        + "PLAYER_OUT_OF_RANGE");

        // Happy path: valid presence validates.
        ValidationResult valid = service.validateAtMutation(
                fresh, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(valid.valid(), "an in-zone, unexpired context validates");
    }

    // ------------------------------------------------------------------
    // acceptance: single-use consumption policy (§7)
    // ------------------------------------------------------------------

    private static void testSingleUseAndFailurePolicy() {
        MutableClock clock = new MutableClock(30_000);
        FakeLandService land = landWith(parcelA());
        DefaultInstitutionAccessService service = service(
                new SavedDataBackedTestStore(), land, clock
        );
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        ZoneId publicZone = addZone(
                service, parliament, ZoneKind.PUBLIC, ZONE_A,
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
        );
        ZoneId officialZone = addZone(
                service, parliament, ZoneKind.OFFICIAL, ZONE_B,
                Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY)
        );
        ZoneId secureZone = addZone(
                service, parliament, ZoneKind.SECURE, ZONE_B,
                Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY)
        );

        // Public contexts are single-use on successful submission only.
        OnSiteContext publicContext = service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(service.validateAtMutation(
                publicContext, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        ).valid(), "a public context validates without consumption");
        require(service.validateAtMutation(
                publicContext, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        ).valid(), "revalidation without consumption stays valid");
        service.consume(publicContext);
        require(!service.validateAtMutation(
                publicContext, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        ).valid(), "a consumed public context must not validate");

        // High-risk authorizations are consumed at the final mutation
        // boundary whether the business mutation later succeeds or fails.
        service.issueOnSiteContext(
                ALPHA_ID, officialZone, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        OnSiteContext highRisk = service.issueOnSiteContext(
                ALPHA_ID, secureZone, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(service.validateAtMutation(
                highRisk, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        ).valid(), "the high-risk authorization validates once");
        require(!service.validateAtMutation(
                highRisk, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        ).valid(), "the high-risk authorization is consumed at the boundary");

        // Official routine sessions are not single-use; consume is a no-op.
        OnSiteContext official = service.issueOnSiteContext(
                ALPHA_ID, officialZone, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        service.consume(official);
        require(service.validateAtMutation(
                official, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        ).valid(), "official routine sessions are not consumed by consume()");
    }

    // ------------------------------------------------------------------
    // acceptance: three workflow default parameters (§7, FR-INST-001-B §3)
    // ------------------------------------------------------------------

    private static void testWorkflowDefaults() {
        // The three workflow parameter groups have server-configured defaults.
        require(InstitutionAccessConfig.DEFAULT.publicContextLifetimeMillis() == 120_000L,
                "public workflow context lifetime defaults to 2 minutes");
        require(InstitutionAccessConfig.DEFAULT.officialIdleTimeoutMillis() == 600_000L,
                "official routine idle timeout defaults to 10 minutes");
        require(InstitutionAccessConfig.DEFAULT.officialHardLimitMillis() == 3_600_000L,
                "official routine hard limit defaults to 60 minutes");
        require(InstitutionAccessConfig.DEFAULT.highRiskLifetimeMillis() == 30_000L,
                "high-risk authorization lifetime defaults to 30 seconds");
        require(InstitutionAccessConfig.DEFAULT.maxZoneXSize() == 16
                        && InstitutionAccessConfig.DEFAULT.maxZoneYSize() == 8
                        && InstitutionAccessConfig.DEFAULT.maxZoneZSize() == 16,
                "the small-size zone budget defaults to 16×8×16");
        require(InstitutionAccessConfig.DEFAULT.presenceCheckIntervalTicks() == 20,
                "the presence check interval defaults to 1 second at 20 TPS");

        // The final mutation-time revalidation has no disable switch.
        for (java.lang.reflect.RecordComponent component
                : InstitutionAccessConfig.class.getRecordComponents()) {
            require(!component.getName().toLowerCase(java.util.Locale.ROOT)
                            .contains("disable"),
                    "the final mutation revalidation must not be configurable "
                            + "off: " + component.getName());
        }

        // Official routine idle refresh: only valid institutional actions
        // refresh the idle clock.
        MutableClock clock = new MutableClock(40_000);
        FakeLandService land = landWith(parcelA());
        DefaultInstitutionAccessService service = service(
                new SavedDataBackedTestStore(), land, clock
        );
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        ZoneId officialZone = addZone(
                service, parliament, ZoneKind.OFFICIAL, ZONE_B,
                Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY)
        );
        OnSiteContext official = service.issueOnSiteContext(
                ALPHA_ID, officialZone, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );

        // Valid institutional action refreshes the idle clock: after a
        // validation at t+5min, presence survives until t+15min (5 + 10).
        clock.setNow(clock.now() + 5 * 60_000L);
        require(service.validateAtMutation(
                official, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        ).valid(), "an official routine context validates within the session");
        clock.setNow(clock.now() + 6 * 60_000L);
        require(service.evaluatePresence(
                official, DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z,
                clock.now()
        ), "idle timeout counts from the last valid action");

        // Without refresh, the 10-minute idle timeout expires the session.
        clock.setNow(clock.now() + 6 * 60_000L);
        require(!service.evaluatePresence(
                official, DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z,
                clock.now()
        ), "an idle official session must expire after the idle timeout");
        require(!service.validateAtMutation(
                official, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        ).valid(), "an idle-expired official session must not validate");

        // The 60-minute hard session limit applies regardless of activity;
        // the idle clock must be refreshed by valid actions along the way.
        MutableClock hardClock = new MutableClock(50_000);
        DefaultInstitutionAccessService hardService = service(
                new SavedDataBackedTestStore(), landWith(parcelA()), hardClock
        );
        FacilityId bank = registerFacility(
                hardService, InstitutionType.CENTRAL_BANK, parcelA().parcelId()
        );
        ZoneId bankOfficialZone = addZone(
                hardService, bank, ZoneKind.OFFICIAL, ZONE_B,
                Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY)
        );
        OnSiteContext session = hardService.issueOnSiteContext(
                ALPHA_ID, bankOfficialZone, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        long sessionStart = hardClock.now();
        for (int i = 1; i <= 11; i++) {
            hardClock.setNow(sessionStart + i * 5 * 60_000L);
            require(hardService.validateAtMutation(
                    session, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                    hardClock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
            ).valid(), "a valid official action within the session validates");
        }
        require(hardService.evaluatePresence(
                session, DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z,
                sessionStart + 59 * 60_000L
        ), "the session survives within the 60-minute hard limit");
        require(!hardService.evaluatePresence(
                session, DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z,
                sessionStart + 61 * 60_000L
        ), "the session must expire at the 60-minute hard limit");
    }

    // ------------------------------------------------------------------
    // acceptance: leave/return — invalidation without restore (§7)
    // ------------------------------------------------------------------

    private static void testLeaveInvalidatesNoRestore() {
        MutableClock clock = new MutableClock(60_000);
        FakeLandService land = landWith(parcelA());
        DefaultInstitutionAccessService service = service(
                new SavedDataBackedTestStore(), land, clock
        );
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        ZoneId publicZone = addZone(
                service, parliament, ZoneKind.PUBLIC, ZONE_A,
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
        );

        // Leaving the zone invalidates the context.
        OnSiteContext context = service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(service.evaluatePresence(
                context, DIMENSION, OUT_ZONE_X, OUT_ZONE_Y, OUT_ZONE_Z,
                clock.now()
        ) == false, "leaving the zone region fails presence");
        require(!service.validateAtMutation(
                context, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, OUT_ZONE_X, OUT_ZONE_Y, OUT_ZONE_Z
        ).valid(), "a left context must not validate");

        // Dimension change also invalidates.
        OnSiteContext dimensional = service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(!service.evaluatePresence(
                dimensional, "minecraft:the_nether",
                IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z, clock.now()
        ), "a dimension change fails presence");

        // Logout/death invalidates every context of the player.
        service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(service.activeContextsOf(ALPHA_ID).size() >= 1,
                "the player holds at least one context");
        service.invalidateOnLeave(ALPHA_ID);
        require(service.activeContextsOf(ALPHA_ID).isEmpty(),
                "invalidateOnLeave clears every context of the player");

        // Returning to the zone never restores a context: the old one stays
        // invalidated and a fresh issue requires an in-zone presence again.
        OnSiteContext old = service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        service.invalidateOnLeave(ALPHA_ID);
        require(service.validateAtMutation(
                old, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        ).reason().equals(ValidationResult.REASON_INVALIDATED),
                "the pre-leave context stays invalidated");
        OnSiteContext fresh = service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(!fresh.contextId().equals(old.contextId()),
                "returning issues a fresh context instead of restoring the old one");
    }

    // ------------------------------------------------------------------
    // acceptance: bounded presence monitoring (§7)
    // ------------------------------------------------------------------

    private static void testPresenceBoundary() {
        MutableClock clock = new MutableClock(70_000);
        FakeLandService land = landWith(parcelA());
        DefaultInstitutionAccessService service = service(
                new SavedDataBackedTestStore(), land, clock
        );
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        ZoneId publicZone = addZone(
                service, parliament, ZoneKind.PUBLIC, ZONE_A,
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
        );
        ZoneId officialZone = addZone(
                service, parliament, ZoneKind.OFFICIAL, ZONE_B,
                Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY)
        );

        service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        service.issueOnSiteContext(
                BRAVO_ID, officialZone, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(service.playersWithActiveContexts().containsAll(
                Set.of(ALPHA_ID, BRAVO_ID)),
                "active-context players are exactly the bounded presence set");
        require(service.playersWithActiveContexts().size() == 2,
                "no other player is tracked");

        // A player leaving the zone stops being tracked.
        for (OnSiteContext context : service.activeContextsOf(ALPHA_ID)) {
            require(service.evaluatePresence(
                    context, DIMENSION, OUT_ZONE_X, OUT_ZONE_Y, OUT_ZONE_Z,
                    clock.now()
            ) == false, "leaving the zone fails bounded presence");
        }
        require(!service.playersWithActiveContexts().contains(ALPHA_ID),
                "a player with no active context leaves the presence set");

        // A public context has no idle clock: presence is zone containment
        // only; expiry is enforced through the lifetime.
        OnSiteContext publicContext = service.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        clock.setNow(clock.now() + InstitutionAccessConfig.DEFAULT
                .publicContextLifetimeMillis() + 1);
        require(!service.evaluatePresence(
                publicContext, DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z,
                clock.now()
        ), "an expired public context fails presence");
    }

    // ------------------------------------------------------------------
    // acceptance: injection failure — nothing is published (§7)
    // ------------------------------------------------------------------

    private static void testInjectionFailureNoPublish() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(80_000);
        FakeLandService land = landWith(parcelA());
        DefaultInstitutionAccessService service = service(store, land, clock);
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );

        store.setCommitFailureCode("injected-commit-failure");
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.addZone(
                        ALPHA_ID,
                        new ZoneRegistrationRequest(
                                parliament, ZoneKind.PUBLIC, ZONE_A,
                                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
                        )
                ),
                "a zone add under an injected commit failure must fail closed"
        );
        require(service.getZone(ZoneId.of(
                UUID.fromString("00000000-0000-0000-0000-0000000000ee")
        )).isEmpty(), "a failed zone add publishes no zone");

        // The zone directory is unchanged: store holds only the facility.
        require(store.commitCount() == 2,
                "the failed zone add does not reach the durable store");

        // State changes also fail without publishing.
        store.setCommitFailureCode(null);
        ZoneId zone = addZone(
                service, parliament, ZoneKind.PUBLIC, ZONE_A,
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
        );
        store.setCommitFailureCode("injected-commit-failure");
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.suspendZone(ALPHA_ID, zone),
                "a zone suspend under an injected commit failure must fail closed"
        );
        require(service.getZone(zone).get().state() == ZoneState.ACTIVE,
                "a failed zone suspend publishes no state change");

        // Recovery: the same operation succeeds and publishes.
        store.setCommitFailureCode(null);
        ZoneReceipt suspended = service.suspendZone(ALPHA_ID, zone);
        require(suspended.applied()
                        && service.getZone(zone).get().state() == ZoneState.SUSPENDED,
                "after recovery the zone suspend commits and publishes");
    }

    // ------------------------------------------------------------------
    // acceptance: restart — contexts cleared, directory recovered (§7)
    // ------------------------------------------------------------------

    private static void testRestartClearsContexts() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(90_000);
        FakeLandService land = landWith(parcelA());
        DefaultInstitutionAccessService first = service(store, land, clock);
        FacilityId parliament = registerFacility(
                first, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        ZoneId publicZone = addZone(
                first, parliament, ZoneKind.PUBLIC, ZONE_A,
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
        );
        first.issueOnSiteContext(
                ALPHA_ID, publicZone, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, IN_ZONE_X, IN_ZONE_Y, IN_ZONE_Z
        );
        require(first.contextCount() == 1,
                "the first runtime holds one live context");

        // Server shutdown clears the runtime registry.
        first.clearContexts();
        require(first.contextCount() == 0,
                "server shutdown clears every runtime context");

        // Restart: directory recovered, contexts start empty.
        SavedDataBackedTestStore restarted = store.restart();
        DefaultInstitutionAccessService second = service(restarted, land, clock);
        require(second.getFacility(parliament).isPresent(),
                "facilities are recovered across restart");
        require(second.getZone(publicZone).isPresent(),
                "zones are recovered across restart");
        require(second.contextCount() == 0,
                "contexts never survive a restart");
        require(second.getFacility(parliament).map(Facility::facilityRevision)
                        .orElse(-1L) == 1L,
                "the recovered directory carries its committed revisions");
    }

    // ------------------------------------------------------------------
    // acceptance: strict deterministic codec (§7)
    // ------------------------------------------------------------------

    private static void testStrictDeterministicCodec() {
        InstitutionAccessNbtCodec codec = new InstitutionAccessNbtCodec();
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(100_000);
        FakeLandService land = landWith(parcelA(), parcelB());
        DefaultInstitutionAccessService service = service(store, land, clock);
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        addZone(
                service, parliament, ZoneKind.PUBLIC, ZONE_A,
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)
        );
        addZone(
                service, parliament, ZoneKind.OFFICIAL, ZONE_B,
                Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY)
        );
        service.suspendFacility(ALPHA_ID, parliament);

        InstitutionAccessStoreSnapshot snapshot =
                ((InstitutionAccessRepository) repositoryOf(service)).snapshot();
        CompoundTag first = codec.encode(snapshot);
        CompoundTag second = codec.encode(snapshot);
        require(java.util.Arrays.equals(nbtBytes(first), nbtBytes(second)),
                "the same snapshot encodes to identical ordered bytes");
        require(codec.encodedSize(first) == codec.encodedSize(second),
                "the same snapshot encodes to the same serialized size");
        require(codec.decode(second).equals(snapshot),
                "decode(encode(snapshot)) equals the snapshot");
        require(codec.decode(codec.encode(snapshot))
                        .equals(codec.decode(store.load())),
                "the persisted form decodes equivalently to the in-memory snapshot");
        require(codec.encodedSize(first) > 0,
                "the encoded snapshot carries real payload bytes");
    }

    // ------------------------------------------------------------------
    // acceptance: corrupt snapshot fails closed (§7)
    // ------------------------------------------------------------------

    private static void testCorruptSnapshotFailClosed() {
        // Unknown store field.
        SavedDataBackedTestStore unknownField = new SavedDataBackedTestStore();
        CompoundTag surprise = new CompoundTag();
        surprise.putInt("StoreVersion", 2);
        surprise.putLong("StoreRevision", 0L);
        surprise.put("Facilities", new CompoundTag());
        surprise.put("Zones", new CompoundTag());
        surprise.putString("Surprise", "x");
        unknownField.putRaw(surprise);
        expectThrows(
                InstitutionAccessNbtException.class,
                () -> repository(unknownField),
                "an unknown store field rejects the load"
        );

        // Newer store version.
        SavedDataBackedTestStore newer = new SavedDataBackedTestStore();
        CompoundTag newerRoot = new CompoundTag();
        newerRoot.putInt("StoreVersion", 99);
        newerRoot.putLong("StoreRevision", 0L);
        newerRoot.put("Facilities", new CompoundTag());
        newerRoot.put("Zones", new CompoundTag());
        newer.putRaw(newerRoot);
        expectThrows(
                InstitutionAccessNbtException.class,
                () -> repository(newer),
                "a newer store version is rejected fail-closed"
        );

        // A v2 store carrying the legacy v1 collection key is rejected
        // (explicit migration required — nothing is silently dropped).
        SavedDataBackedTestStore legacy = new SavedDataBackedTestStore();
        CompoundTag legacyRoot = new CompoundTag();
        legacyRoot.putInt("StoreVersion", 2);
        legacyRoot.putLong("StoreRevision", 0L);
        legacyRoot.put("Facilities", new CompoundTag());
        legacyRoot.put("Zones", new CompoundTag());
        legacyRoot.put("Terminals", new CompoundTag());
        legacy.putRaw(legacyRoot);
        expectThrows(
                InstitutionAccessNbtException.class,
                () -> repository(legacy),
                "a v2 root carrying legacy terminal fields must be rejected"
        );

        // A zone referencing an unknown facility is rejected.
        SavedDataBackedTestStore dangling = new SavedDataBackedTestStore();
        CompoundTag danglingRoot = new CompoundTag();
        danglingRoot.putInt("StoreVersion", 2);
        danglingRoot.putLong("StoreRevision", 1L);
        danglingRoot.put("Facilities", new CompoundTag());
        CompoundTag zones = new CompoundTag();
        CompoundTag zone = new CompoundTag();
        zone.putInt("ZoneVersion", 1);
        zone.putUUID("ZoneId", UUID.fromString(
                "00000000-0000-0000-0000-0000000000f2"));
        zone.putUUID("FacilityId", UUID.fromString(
                "00000000-0000-0000-0000-0000000000f1"));
        zone.putString("InstitutionType", "PARLIAMENT");
        zone.putString("Kind", "PUBLIC");
        CompoundTag region = new CompoundTag();
        region.putString("Dimension", DIMENSION);
        region.putInt("MinX", 12);
        region.putInt("MinY", 62);
        region.putInt("MinZ", 12);
        region.putInt("MaxX", 27);
        region.putInt("MaxY", 69);
        region.putInt("MaxZ", 27);
        zone.put("Region", region);
        net.minecraft.nbt.ListTag capabilities = new net.minecraft.nbt.ListTag();
        capabilities.add(net.minecraft.nbt.StringTag.valueOf(
                "ONSITE_PUBLIC_SERVICE"));
        zone.put("Capabilities", capabilities);
        zone.putString("State", "ACTIVE");
        zone.putLong("ZoneRevision", 1L);
        zones.put("00000000-0000-0000-0000-0000000000f2", zone);
        danglingRoot.put("Zones", zones);
        dangling.putRaw(danglingRoot);
        expectThrows(
                InstitutionAccessNbtException.class,
                () -> repository(dangling),
                "a zone referencing an unknown facility rejects the load"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: v1 (terminal-model) root requires explicit migration (§7)
    // ------------------------------------------------------------------

    private static void testV1RootRejected() {
        SavedDataBackedTestStore v1 = new SavedDataBackedTestStore();
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 0L);
        root.put("Facilities", new CompoundTag());
        root.put("Terminals", new CompoundTag());
        v1.putRaw(root);
        InstitutionAccessNbtException failure = expectThrows(
                InstitutionAccessNbtException.class,
                () -> repository(v1),
                "a v1 terminal-model root must be rejected"
        );
        require(failure.getMessage().contains("migration"),
                "the rejection must demand an explicit migration");
    }

    // ------------------------------------------------------------------
    // acceptance: no hard-coded coordinates (§7)
    // ------------------------------------------------------------------

    private static void testNoHardcodedCoordinates() throws Exception {
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path moduleDirectory = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/server/institutionaccess"
        );
        StringBuilder source = new StringBuilder();
        try (Stream<Path> paths = Files.walk(moduleDirectory)) {
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
                    "institution-access production code must not hard-code "
                            + "coordinates: " + forbidden
            );
        }
        // Regions may only be built from caller requests or decoded
        // persistence — never from literal coordinate tuples in code.
        java.util.regex.Pattern literalRegion = java.util.regex.Pattern.compile(
                "new ZoneRegion\\(\\s*[-0-9]"
        );
        require(
                !literalRegion.matcher(codeOnly).find(),
                "institution-access production code must not build a zone region "
                        + "from literal coordinates"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: no terminal-model references anywhere in production code (§7)
    // ------------------------------------------------------------------

    private static void testNoTerminalReferences() throws Exception {
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path mainDirectory = projectDirectory.resolve("src/main/java");
        List<String> forbidden = List.of(
                "TerminalId", "TerminalPosition", "TerminalState",
                "TerminalReceipt", "TerminalChangeKind",
                "TerminalRegistrationRequest",
                "registerTerminal", "suspendTerminal", "disableTerminal",
                "getTerminal", "parseTerminalId", "terminalId",
                "invalidateByTerminal", "findByTerminalId", "terminalCount",
                "literal(\"terminal\")"
        );
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(mainDirectory)) {
            List<Path> files = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
            for (Path path : files) {
                String codeOnly = stripComments(read(path));
                for (String token : forbidden) {
                    if (codeOnly.contains(token)) {
                        violations.add(path.getFileName() + ": " + token);
                    }
                }
                // The v1 migration guard in the codec must reference the
                // legacy 'Terminals' collection key; anywhere else it is a
                // terminal-model residue.
                if (codeOnly.contains("Terminals")
                        && !path.getFileName().toString()
                        .equals("InstitutionAccessNbtCodec.java")) {
                    violations.add(path.getFileName() + ": Terminals");
                }
            }
        }
        require(violations.isEmpty(),
                "the terminal model must be fully removed from production "
                        + "code, found: " + violations);
    }

    // ------------------------------------------------------------------
    // acceptance: command tree — zones only, no privilege escalation (§7)
    // ------------------------------------------------------------------

    private static void testCommandTreeNoEscalation() throws Exception {
        CommandContributionRegistry contributions = new CommandContributionRegistry();
        contributions.freeze();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        new CommandBootstrap(
                contributions,
                new CommandRuntimeResolver(new CoreManager(new ModuleRegistry()))
        ).register(dispatcher, null, Commands.CommandSelection.ALL);

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("fr");
        require(root != null, "the /fr root must exist");
        CommandNode<CommandSourceStack> admin = root.getChild("admin");
        require(admin != null, "the /fr admin node must exist");
        CommandNode<CommandSourceStack> institution = admin.getChild("institution");
        require(institution != null, "/fr admin institution must exist");
        CommandNode<CommandSourceStack> facility = institution.getChild("facility");
        CommandNode<CommandSourceStack> zone = institution.getChild("zone");
        require(facility != null, "/fr admin institution facility must exist");
        require(zone != null, "/fr admin institution zone must exist; institution children="
                + institution.getChildren().stream()
                .map(node -> node.getName()).sorted().toList());
        require(institution.getChild("terminal") == null,
                "the terminal command subtree must be gone");
        require(institution.getChild("terminal") == null,
                "the terminal command subtree must be gone");
        require(facility.getChild("register") != null,
                "facility register path must exist");
        require(facility.getChild("suspend") != null, "facility suspend must exist");
        require(facility.getChild("activate") != null, "facility activate must exist");
        require(facility.getChild("relocate") != null, "facility relocate must exist");
        require(facility.getChild("disable") != null, "facility disable must exist");
        require(zone.getChild("add") != null, "zone add path must exist");
        require(zone.getChild("remove") != null, "zone remove path must exist");
        require(zone.getChild("resize") != null, "zone resize path must exist");
        require(zone.getChild("set-kind") != null,
                "zone set-kind path must exist; zone children="
                        + zone.getChildren().stream()
                        .map(node -> node.getName()).sorted().toList());
        require(zone.getChild("suspend") != null, "zone suspend path must exist");
        require(zone.getChild("activate") != null, "zone activate path must exist");

        // OP level-2 early gate: ordinary sources cannot use the institution
        // admin paths; OP 2 sources can.
        CapturingSource ordinaryCapture = new CapturingSource();
        CommandSourceStack ordinary = source(ordinaryCapture, 0);
        require(!admin.canUse(ordinary), "non-OP sources must fail the admin gate");
        expectThrows(
                CommandSyntaxException.class,
                () -> dispatcher.execute(
                        "fr admin institution facility register PARLIAMENT "
                                + parcelA().parcelId(),
                        ordinary
                ),
                "non-OP execution must be rejected by Brigadier"
        );

        CapturingSource operatorCapture = new CapturingSource();
        CommandSourceStack operator = source(operatorCapture, Commands.LEVEL_GAMEMASTERS);
        require(admin.canUse(operator), "OP level-2 sources pass the admin gate");
        int unavailable = dispatcher.execute(
                "fr admin institution facility register PARLIAMENT "
                        + parcelA().parcelId(),
                operator
        );
        require(unavailable == CommandFeedback.FAILURE,
                "an unavailable institution runtime must return failure");
        require(operatorCapture.lastMessage().contains(
                        "institution-access runtime is unavailable"),
                "unavailable runtime feedback must be explicit");
    }

    // ------------------------------------------------------------------
    // acceptance: module contract — namespace, id, dependencies (§6)
    // ------------------------------------------------------------------

    private static void testModuleContract() {
        require(InstitutionAccessRepository.MODULE_DATA_KEY.equals("institution-access"),
                "institution-access owns exactly the approved namespace");
        require(InstitutionAccessModule.MODULE_ID.value().equals("institution-access"),
                "institution-access module id is 'institution-access'");
        ModuleDefinition definition = new ModuleDefinition(
                InstitutionAccessModule.MODULE_ID,
                new ModuleMetadata(
                        "Institution Access", "1.0.0",
                        Optional.empty(), Optional.empty()
                ),
                Set.of(
                        new ModuleId("land"),
                        new ModuleId("audit"),
                        PlayerDataModule.MODULE_ID,
                        SubjectRegistryModule.MODULE_ID
                ),
                Set.of(),
                70,
                InstitutionAccessModule::new
        );
        require(definition.requiredDependencies().equals(Set.of(
                        new ModuleId("land"),
                        new ModuleId("audit"),
                        PlayerDataModule.MODULE_ID,
                        SubjectRegistryModule.MODULE_ID
                )),
                "institution-access depends on land, audit, player-data, subject-registry");
        for (ModuleId dependency : definition.requiredDependencies()) {
            String value = dependency.value();
            require(!value.contains("emg") && !value.contains("emergency")
                            && !value.contains("economy") && !value.contains("citizen")
                            && !value.contains("justice"),
                    "institution-access never depends on later-phase or "
                            + "political namespaces");
        }

        // No business permission, emergency, GUI, terminal, or packet surface
        // exists.
        for (Class<?> type : List.of(
                InstitutionAccessService.class,
                DefaultInstitutionAccessService.class,
                InstitutionAccessModule.class
        )) {
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                require(!name.contains("emergency") && !name.contains("breakglass")
                                && !name.contains("gui") && !name.contains("screen")
                                && !name.contains("packet")
                                && !name.contains("terminal"),
                        "institution access exposes no out-of-scope surface: "
                                + type.getSimpleName() + "." + method.getName());
            }
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static InstitutionAccessRepository repository(InstitutionAccessStore store) {
        return new InstitutionAccessRepository(
                store,
                new InstitutionAccessNbtCodec(),
                InstitutionAccessLimits.DEFAULT,
                new SequentialIdSource(0x1000_0000_0000_0000L),
                new SequentialIdSource(0x2000_0000_0000_0000L)
        );
    }

    private static DefaultInstitutionAccessService service(
            InstitutionAccessStore store,
            FakeLandService land,
            MutableClock clock
    ) {
        return service(repository(store), land, clock);
    }

    private static DefaultInstitutionAccessService service(
            InstitutionAccessRepository repository,
            FakeLandService land,
            MutableClock clock
    ) {
        return new DefaultInstitutionAccessService(
                repository,
                land,
                new FakeActorResolver(ALPHA_ID, BRAVO_ID),
                new FakeAuditService(),
                clock,
                InstitutionAccessConfig.DEFAULT
        );
    }

    private static InstitutionAccessRepository repositoryOf(
            DefaultInstitutionAccessService service
    ) {
        try {
            Field repository = DefaultInstitutionAccessService.class
                    .getDeclaredField("repository");
            repository.setAccessible(true);
            return (InstitutionAccessRepository) repository.get(service);
        } catch (ReflectiveOperationException impossible) {
            throw new AssertionError("repository field must exist", impossible);
        }
    }

    private static FacilityId registerFacility(
            InstitutionAccessService service,
            InstitutionType type,
            ParcelId parcelId
    ) {
        return service.registerFacility(
                ALPHA_ID,
                new FacilityRegistrationRequest(type, parcelId)
        ).facility().facilityId();
    }

    private static ZoneId addZone(
            InstitutionAccessService service,
            FacilityId facilityId,
            ZoneKind kind,
            ZoneRegion region,
            Set<CapabilityClass> capabilities
    ) {
        return service.addZone(
                ALPHA_ID,
                new ZoneRegistrationRequest(facilityId, kind, region, capabilities)
        ).zone().zoneId();
    }

    private static LandParcel parcelA() {
        return parcel(ParcelId.of(
                UUID.fromString("00000000-0000-0000-0000-0000000000f1")),
                REGION_A);
    }

    private static LandParcel parcelB() {
        return parcel(ParcelId.of(
                UUID.fromString("00000000-0000-0000-0000-0000000000f2")),
                REGION_B);
    }

    private static LandParcel parcelC() {
        return parcel(ParcelId.of(
                UUID.fromString("00000000-0000-0000-0000-0000000000f3")),
                new ParcelRegion(200, 60, 200, 220, 70, 220));
    }

    private static LandParcel parcel(ParcelId parcelId, ParcelRegion region) {
        return new LandParcel(
                LandParcel.CURRENT_SCHEMA_VERSION,
                parcelId,
                DIMENSION,
                region,
                ZoneType.GOVERNMENT,
                LandOwnership.REPUBLIC,
                LandAccess.PUBLIC,
                Map.of(),
                1L
        );
    }

    private static FakeLandService landWith(LandParcel... parcels) {
        LinkedHashMap<ParcelId, LandParcel> map = new LinkedHashMap<>();
        for (LandParcel parcel : parcels) {
            map.put(parcel.parcelId(), parcel);
        }
        return new FakeLandService(map);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new AssertionError("Failed to read " + path, failure);
        }
    }

    private static String stripComments(String source) {
        return source.replaceAll("//[^\\n]*", "")
                .replaceAll("/\\*.*?\\*/", "");
    }

    private static byte[] nbtBytes(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeUnnamedTag(tag, new DataOutputStream(out));
            return out.toByteArray();
        } catch (IOException impossible) {
            throw new AssertionError("NBT serialization failed", impossible);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expected,
            ThrowingRunnable action,
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

    private static CommandSourceStack source(CapturingSource source, int permission) {
        return new CommandSourceStack(
                source,
                Vec3.ZERO,
                Vec2.ZERO,
                null,
                permission,
                "test-source",
                Component.literal("test-source"),
                null,
                null
        );
    }

    private static final class SavedDataBackedTestStore implements InstitutionAccessStore {
        private final ModSavedData savedData;
        private String commitFailureCode;
        private int commitCount;

        private SavedDataBackedTestStore() {
            this(new ModSavedData());
        }

        private SavedDataBackedTestStore(ModSavedData savedData) {
            this.savedData = savedData;
        }

        @Override
        public CompoundTag load() {
            return savedData.getModuleData(
                    InstitutionAccessRepository.MODULE_DATA_KEY
            ).copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            commitCount++;
            if (commitFailureCode != null) {
                return new DurableCommitResult(
                        DurableCommitStatus.FAILED,
                        InstitutionAccessRepository.MODULE_DATA_KEY,
                        0L,
                        0L,
                        commitFailureCode
                );
            }
            savedData.putModuleData(
                    InstitutionAccessRepository.MODULE_DATA_KEY,
                    snapshot.copy()
            );
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    InstitutionAccessRepository.MODULE_DATA_KEY,
                    1L,
                    0L,
                    ""
            );
        }

        private void putRaw(CompoundTag root) {
            savedData.putModuleData(
                    InstitutionAccessRepository.MODULE_DATA_KEY,
                    root.copy()
            );
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

        private long now() {
            return now;
        }

        private void setNow(long now) {
            this.now = now;
        }
    }

    private static final class SequentialIdSource implements java.util.function.Supplier<UUID> {
        private long counter;

        private SequentialIdSource(long start) {
            this.counter = start;
        }

        @Override
        public UUID get() {
            counter++;
            return new UUID(0L, counter);
        }
    }

    private static final class FakeLandService implements LandService {
        private final Map<ParcelId, LandParcel> parcels;

        private FakeLandService(Map<ParcelId, LandParcel> parcels) {
            this.parcels = new HashMap<>(parcels);
        }

        @Override
        public Optional<LandParcel> getParcel(ParcelId parcelId) {
            return Optional.ofNullable(parcels.get(parcelId));
        }

        @Override
        public com.fontainerepublic.server.land.api.LandSummary publicSummary() {
            return new com.fontainerepublic.server.land.api.LandSummary(
                    parcels.size(), 0L, List.of(), 0L);
        }

        private void setParcel(LandParcel parcel) {
            parcels.put(parcel.parcelId(), parcel);
        }

        @Override
        public com.fontainerepublic.server.land.api.LandReceipt createParcel(
                UUID actor, CreateParcelRequest request
        ) {
            throw new UnsupportedOperationException("not used by the access boundary");
        }

        @Override
        public UsageReceipt grantUsage(
                UUID actor, ParcelId parcelId,
                com.fontainerepublic.server.registry.model.OwnerReference holder,
                long durationMillis
        ) {
            throw new UnsupportedOperationException("not used by the access boundary");
        }

        @Override
        public UsageReceipt renewUsage(
                UUID actor, ParcelId parcelId,
                com.fontainerepublic.server.registry.model.OwnerReference holder,
                long durationMillis
        ) {
            throw new UnsupportedOperationException("not used by the access boundary");
        }

        @Override
        public UsageReceipt revokeUsage(
                UUID actor, ParcelId parcelId,
                com.fontainerepublic.server.registry.model.OwnerReference holder
        ) {
            throw new UnsupportedOperationException("not used by the access boundary");
        }

        @Override
        public LandReceipt setZoneType(
                UUID actor, ParcelId parcelId,
                com.fontainerepublic.server.land.model.ZoneType zone
        ) {
            throw new UnsupportedOperationException("not used by the access boundary");
        }

        @Override
        public LandReceipt setAccess(
                UUID actor, ParcelId parcelId,
                com.fontainerepublic.server.land.model.LandAccess access
        ) {
            throw new UnsupportedOperationException("not used by the access boundary");
        }

        @Override
        public ViolationReceipt createViolationReport(ViolationDraft draft) {
            throw new UnsupportedOperationException("not used by the access boundary");
        }

        @Override
        public boolean canBuild(UUID player, ParcelId parcelId) {
            return false;
        }

        @Override
        public boolean canBreak(UUID player, ParcelId parcelId) {
            return false;
        }

        @Override
        public boolean canInteract(UUID player, ParcelId parcelId) {
            return false;
        }
    }

    private static final class FakeActorResolver implements ActorResolver {
        private final Set<UUID> records;
        private final Set<UUID> activeSubjects;

        private FakeActorResolver(UUID... players) {
            this.records = Set.of(players);
            this.activeSubjects = Set.of(players);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean hasPlayerRecord(UUID playerId) {
            return records.contains(playerId);
        }

        @Override
        public boolean hasActiveSubject(UUID playerId) {
            return activeSubjects.contains(playerId);
        }
    }

    private static final class FakeAuditService implements AuditService {
        private final ArrayList<AuditDraft> drafts = new ArrayList<>();
        private long nextEntryId;

        @Override
        public AuditEntry record(AuditDraft draft) {
            return buildEntry(draft);
        }

        @Override
        public AuditReceipt recordAuthoritative(AuditDraft draft) {
            drafts.add(draft);
            buildEntry(draft);
            return new AuditReceipt(
                    DurableCommitStatus.COMMITTED,
                    nextEntryId,
                    nextEntryId,
                    ""
            );
        }

        @Override
        public Optional<AuditEntry> getEntry(long entryId) {
            return Optional.empty();
        }

        @Override
        public AuditPage page(long afterEntryId, int limit) {
            throw new UnsupportedOperationException("not used by the access boundary");
        }

        private AuditEntry buildEntry(AuditDraft draft) {
            nextEntryId++;
            return new AuditEntry(
                    nextEntryId,
                    1L,
                    draft.actorType(),
                    draft.actorId(),
                    draft.category(),
                    draft.moduleId(),
                    draft.actionId(),
                    draft.targetType(),
                    draft.targetId(),
                    draft.classification(),
                    draft.summary(),
                    Optional.empty(),
                    nextEntryId
            );
        }

        private List<AuditDraft> drafts() {
            return List.copyOf(drafts);
        }
    }

    private static final class CapturingSource implements net.minecraft.commands.CommandSource {
        private final ArrayList<String> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        private String lastMessage() {
            require(!messages.isEmpty(), "Expected at least one captured message");
            return messages.get(messages.size() - 1);
        }
    }
}
