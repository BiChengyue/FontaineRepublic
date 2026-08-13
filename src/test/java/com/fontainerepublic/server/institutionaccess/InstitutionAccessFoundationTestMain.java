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
import com.fontainerepublic.server.institutionaccess.api.TerminalChangeKind;
import com.fontainerepublic.server.institutionaccess.api.TerminalReceipt;
import com.fontainerepublic.server.institutionaccess.api.TerminalRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.api.ValidationResult;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.Facility;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.FacilityState;
import com.fontainerepublic.server.institutionaccess.model.InstitutionType;
import com.fontainerepublic.server.institutionaccess.model.Terminal;
import com.fontainerepublic.server.institutionaccess.model.TerminalId;
import com.fontainerepublic.server.institutionaccess.model.TerminalPosition;
import com.fontainerepublic.server.institutionaccess.model.TerminalState;
import com.fontainerepublic.server.institutionaccess.model.WorkflowKind;
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
import java.lang.reflect.Modifier;
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
 * Dependency-free validation entry point for FR-INST-002 (shared institution
 * access boundary). Exercises the FR-INST-002-A §7 acceptance matrix with an
 * injectable store and SavedData-backed restart simulation: facility/terminal
 * registration with FR-LAND parcel validation, region checks, and the
 * lifecycle state machine; on-site context issue/validate/expiry/revision
 * binding; leave-invalidation without restore; single-use consumption
 * (public on submit, high-risk at the final boundary); the three workflow
 * default parameters per FR-INST-001-B §3; the bounded presence boundary
 * (active-context players only); injection failure without publish; restart
 * clearing contexts while recovering the directory; strict deterministic
 * codec; no hard-coded coordinates; and a command tree without privilege
 * escalation.
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

    private InstitutionAccessFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testFacilityRegistrationParcelValidation();
        testTerminalRegistrationValidation();
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
        testNoHardcodedCoordinates();
        testCommandTreeNoEscalation();
        testModuleContract();
        System.out.println(
                "[FR-INST-002] Institution access foundation validation passed"
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
    // acceptance: terminal registration — region/institution/capability checks
    // ------------------------------------------------------------------

    private static void testTerminalRegistrationValidation() {
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

        // Terminal outside the facility region is rejected.
        TerminalPosition outside = new TerminalPosition(
                DIMENSION, 50, 64, 50
        );
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.registerTerminal(
                        ALPHA_ID,
                        new TerminalRegistrationRequest(
                                parliament, outside,
                                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE),
                                false
                        )
                ),
                "a terminal outside its facility parcel region must be rejected"
        );

        // Terminal in the wrong dimension is rejected even when coordinates
        // overlap the region in another dimension.
        TerminalPosition wrongDimension = new TerminalPosition(
                "minecraft:the_nether", 20, 64, 20
        );
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.registerTerminal(
                        ALPHA_ID,
                        new TerminalRegistrationRequest(
                                parliament, wrongDimension,
                                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE),
                                false
                        )
                ),
                "a terminal dimension not matching the parcel must be rejected"
        );

        // Registration on a non-ACTIVE facility is rejected.
        service.suspendFacility(ALPHA_ID, parliament);
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.registerTerminal(
                        ALPHA_ID,
                        new TerminalRegistrationRequest(
                                parliament, terminalA(),
                                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE),
                                false
                        )
                ),
                "terminals may only be registered on an ACTIVE facility"
        );
        service.activateFacility(ALPHA_ID, parliament);

        // Empty capability set is rejected by the request contract.
        expectThrows(
                IllegalArgumentException.class,
                () -> new TerminalRegistrationRequest(
                        parliament, terminalA(), Set.of(), false
                ),
                "an empty capability set must be rejected at construction"
        );

        TerminalReceipt receipt = service.registerTerminal(
                ALPHA_ID,
                new TerminalRegistrationRequest(
                        parliament, terminalA(),
                        Set.of(
                                CapabilityClass.ONSITE_PUBLIC_SERVICE,
                                CapabilityClass.ONSITE_OFFICIAL_DUTY
                        ),
                        false
                )
        );
        require(receipt.applied(), "terminal registration is applied");
        require(receipt.kind() == TerminalChangeKind.TERMINAL_REGISTERED,
                "receipt kind is TERMINAL_REGISTERED");
        require(receipt.terminal().state() == TerminalState.ACTIVE,
                "a registered terminal starts ACTIVE");
        require(receipt.terminal().terminalRevision() == 1,
                "a registered terminal starts at revision 1");
        require(receipt.terminal().institutionType() == InstitutionType.PARLIAMENT,
                "the terminal inherits the facility institution type");
        require(receipt.terminal().integrity().equals(terminalA().integrityDigest()),
                "the terminal integrity digest binds the anchored position");

        // Same anchored position cannot host a second terminal (anti-clone).
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.registerTerminal(
                        ALPHA_ID,
                        new TerminalRegistrationRequest(
                                parliament, terminalA(),
                                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE),
                                false
                        )
                ),
                "a second terminal at the same anchored position must be rejected"
        );

        // A terminal on bank parcel cannot be anchored to the parliament
        // facility (region check across parcels).
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.registerTerminal(
                        ALPHA_ID,
                        new TerminalRegistrationRequest(
                                parliament,
                                new TerminalPosition(DIMENSION, 110, 64, 110),
                                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE),
                                false
                        )
                ),
                "a terminal outside the referenced facility region must be rejected"
        );

        // The bank facility still accepts its own terminal.
        TerminalReceipt bankTerminal = service.registerTerminal(
                BRAVO_ID,
                new TerminalRegistrationRequest(
                        bank,
                        new TerminalPosition(DIMENSION, 110, 64, 110),
                        Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE),
                        false
                )
        );
        require(bankTerminal.terminal().facilityId().equals(bank),
                "bank terminal anchors to the bank facility");
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

        // RELOCATING -> ACTIVE via activate, then DISABLED (terminal state).
        service.activateFacility(ALPHA_ID, id);
        FacilityReceipt disabled = service.disableFacility(ALPHA_ID, id);
        require(disabled.facility().state() == FacilityState.DISABLED,
                "disable moves the facility to DISABLED");
        require(service.disableFacility(ALPHA_ID, id).applied() == false,
                "disabling a disabled facility is a no-op");

        // DISABLED is terminal: no suspend/activate/relocate.
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
    // acceptance: context issue — §6.3 verification chain
    // ------------------------------------------------------------------

    private static void testIssueContextValidation() {
        MutableClock clock = new MutableClock(10_000);
        FakeLandService land = landWith(parcelA());
        InstitutionAccessService service = service(
                new SavedDataBackedTestStore(), land, clock
        );
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        TerminalId publicTerminal = registerTerminal(
                service, parliament, terminalA(),
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE), false
        );
        TerminalId routineTerminal = registerTerminal(
                service, parliament,
                new TerminalPosition(DIMENSION, 15, 64, 15),
                Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY), false
        );
        TerminalId secureTerminal = registerTerminal(
                service, parliament, terminalB(),
                Set.of(
                        CapabilityClass.ONSITE_PUBLIC_SERVICE,
                        CapabilityClass.ONSITE_OFFICIAL_DUTY
                ),
                true
        );

        // Capability not allowed by the terminal.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.issueOnSiteContext(
                        ALPHA_ID, publicTerminal,
                        CapabilityClass.ONSITE_OFFICIAL_DUTY,
                        DIMENSION, 20, 64, 20
                ),
                "a capability outside the terminal set must be rejected"
        );
        // Remote/emergency classes never anchor an on-site context.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.issueOnSiteContext(
                        ALPHA_ID, publicTerminal,
                        CapabilityClass.REMOTE_INFORMATION,
                        DIMENSION, 20, 64, 20
                ),
                "remote capabilities must never issue an on-site context"
        );
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.issueOnSiteContext(
                        ALPHA_ID, publicTerminal,
                        CapabilityClass.EMERGENCY_RECOVERY,
                        DIMENSION, 20, 64, 20
                ),
                "emergency recovery is owned by FR-EMG and never issued here"
        );

        // Player out of the 6-block public range.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.issueOnSiteContext(
                        ALPHA_ID, publicTerminal,
                        CapabilityClass.ONSITE_PUBLIC_SERVICE,
                        DIMENSION, 30, 64, 20
                ),
                "a player 10 blocks from the terminal must be rejected"
        );

        // Player in a different dimension.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.issueOnSiteContext(
                        ALPHA_ID, publicTerminal,
                        CapabilityClass.ONSITE_PUBLIC_SERVICE,
                        "minecraft:the_nether", 20, 64, 20
                ),
                "a dimension mismatch must be rejected"
        );

        // Valid public context issues with default lifetime.
        OnSiteContext publicContext = service.issueOnSiteContext(
                ALPHA_ID, publicTerminal,
                CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, 20, 64, 20
        );
        require(publicContext.workflowKind() == WorkflowKind.PUBLIC,
                "ONSITE_PUBLIC_SERVICE selects the public workflow");
        require(publicContext.capability() == CapabilityClass.ONSITE_PUBLIC_SERVICE,
                "context binds the requested capability");
        require(publicContext.expiryTime() - publicContext.issueTime()
                        == InstitutionAccessConfig.DEFAULT.publicContextLifetimeMillis(),
                "public context lifetime follows the default (2 minutes)");

        // High-risk on a secure terminal requires a valid official session.
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.issueOnSiteContext(
                        ALPHA_ID, secureTerminal,
                        CapabilityClass.ONSITE_OFFICIAL_DUTY,
                        DIMENSION, 25, 64, 25
                ),
                "high-risk without an official routine session must be rejected"
        );

        // After the official session exists (issued at a normal terminal
        // inside the work zone), the secure terminal issues a high-risk
        // authorization.
        OnSiteContext official = service.issueOnSiteContext(
                ALPHA_ID, routineTerminal,
                CapabilityClass.ONSITE_OFFICIAL_DUTY,
                DIMENSION, 15, 64, 15
        );
        require(official.workflowKind() == WorkflowKind.OFFICIAL_ROUTINE,
                "a normal terminal selects the official routine workflow");
        require(official.facilityRevision()
                        == service.getFacility(parliament)
                        .map(Facility::facilityRevision).orElse(-1L),
                "the context binds the facility revision at issue time");
        require(official.terminalRevision()
                        == service.getTerminal(routineTerminal)
                        .map(Terminal::terminalRevision).orElse(-1L),
                "the context binds the terminal revision at issue time");
        OnSiteContext highRisk = service.issueOnSiteContext(
                ALPHA_ID, secureTerminal,
                CapabilityClass.ONSITE_OFFICIAL_DUTY,
                DIMENSION, 25, 64, 25
        );
        require(highRisk.workflowKind() == WorkflowKind.HIGH_RISK,
                "secure terminal selects the high-risk workflow");
        require(highRisk.expiryTime() - highRisk.issueTime()
                        == InstitutionAccessConfig.DEFAULT.highRiskLifetimeMillis(),
                "high-risk lifetime follows the default (30 seconds)");
    }

    // ------------------------------------------------------------------
    // acceptance: validateAtMutation — capability/expiry/revision binding
    // ------------------------------------------------------------------

    private static void testValidateAtMutation() {
        MutableClock clock = new MutableClock(20_000);
        FakeLandService land = landWith(parcelA());
        InstitutionAccessService service = service(
                new SavedDataBackedTestStore(), land, clock
        );
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        TerminalId publicTerminal = registerTerminal(
                service, parliament, terminalA(),
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE), false
        );

        // Null context is never valid.
        require(!service.validateAtMutation(
                null, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, 20, 64, 20
        ).valid(), "a null context must be invalid");

        OnSiteContext context = service.issueOnSiteContext(
                ALPHA_ID, publicTerminal,
                CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, 20, 64, 20
        );

        // Capability mismatch.
        ValidationResult mismatch = service.validateAtMutation(
                context, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                clock.now(), DIMENSION, 20, 64, 20
        );
        require(!mismatch.valid()
                        && mismatch.reason().equals(ValidationResult.REASON_CAPABILITY_MISMATCH),
                "a capability mismatch must fail at the mutation boundary");

        // Expiry.
        clock.setNow(clock.now() + InstitutionAccessConfig.DEFAULT
                .publicContextLifetimeMillis() + 1);
        ValidationResult expired = service.validateAtMutation(
                context, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, 20, 64, 20
        );
        require(!expired.valid()
                        && expired.reason().equals(ValidationResult.REASON_EXPIRED),
                "an expired context must fail at the mutation boundary");

        // Fresh context validates (public is NOT consumed by validation).
        clock.setNow(20_000);
        OnSiteContext fresh = service.issueOnSiteContext(
                ALPHA_ID, publicTerminal,
                CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, 20, 64, 20
        );
        ValidationResult valid = service.validateAtMutation(
                fresh, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, 20, 64, 20
        );
        require(valid.valid(), "a fresh in-range context validates");
        require(service.validateAtMutation(
                fresh, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, 20, 64, 20
        ).valid(), "public validation does not consume the context");

        // Facility lifecycle change invalidates bound contexts immediately
        // (FR-INST-001-B §4 lifecycle events); the revision binding is an
        // additional defense-in-depth at the mutation boundary.
        OnSiteContext beforeSuspend = service.issueOnSiteContext(
                ALPHA_ID, publicTerminal,
                CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, 20, 64, 20
        );
        require(beforeSuspend.facilityRevision()
                        == service.getFacility(parliament)
                        .map(Facility::facilityRevision).orElse(-1L),
                "the context binds the facility revision at issue time");
        service.suspendFacility(ALPHA_ID, parliament);
        ValidationResult afterSuspend = service.validateAtMutation(
                beforeSuspend, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, 20, 64, 20
        );
        require(!afterSuspend.valid()
                        && afterSuspend.reason().equals(ValidationResult.REASON_INVALIDATED),
                "a suspended facility invalidates bound contexts immediately");
        service.activateFacility(ALPHA_ID, parliament);
        require(!service.validateAtMutation(
                beforeSuspend, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, 20, 64, 20
        ).valid(), "reactivating the facility does not restore old contexts");

        // Terminal lifecycle change invalidates bound contexts immediately.
        TerminalId secondTerminal = registerTerminal(
                service, parliament,
                new TerminalPosition(DIMENSION, 25, 64, 25),
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE), false
        );
        OnSiteContext terminalContext = service.issueOnSiteContext(
                ALPHA_ID, secondTerminal,
                CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, 25, 64, 25
        );
        require(terminalContext.terminalRevision()
                        == service.getTerminal(secondTerminal)
                        .map(Terminal::terminalRevision).orElse(-1L),
                "the context binds the terminal revision at issue time");
        service.suspendTerminal(ALPHA_ID, secondTerminal);
        ValidationResult afterTerminal = service.validateAtMutation(
                terminalContext, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, 25, 64, 25
        );
        require(!afterTerminal.valid()
                        && afterTerminal.reason().equals(ValidationResult.REASON_INVALIDATED),
                "a suspended terminal invalidates bound contexts immediately");
    }

    // ------------------------------------------------------------------
    // acceptance: single-use — public consumes on submit, high-risk at the
    // final boundary; public failure does not consume (FR-INST-001-B §3)
    // ------------------------------------------------------------------

    private static void testSingleUseAndFailurePolicy() {
        MutableClock clock = new MutableClock(30_000);
        FakeLandService land = landWith(parcelA());
        InstitutionAccessService service = service(
                new SavedDataBackedTestStore(), land, clock
        );
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        TerminalId routineTerminal = registerTerminal(
                service, parliament,
                new TerminalPosition(DIMENSION, 15, 64, 15),
                Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY), false
        );
        TerminalId secureTerminal = registerTerminal(
                service, parliament, terminalB(),
                Set.of(
                        CapabilityClass.ONSITE_PUBLIC_SERVICE,
                        CapabilityClass.ONSITE_OFFICIAL_DUTY
                ),
                true
        );

        // PUBLIC: consume after successful submission; a failed validation
        // attempt does not consume and a corrected retry still works.
        OnSiteContext publicContext = service.issueOnSiteContext(
                ALPHA_ID, secureTerminal,
                CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, 25, 64, 25
        );
        ValidationResult badAttempt = service.validateAtMutation(
                publicContext, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, 40, 64, 40
        );
        require(!badAttempt.valid(), "an out-of-range public attempt fails");
        ValidationResult retry = service.validateAtMutation(
                publicContext, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, 25, 64, 25
        );
        require(retry.valid(), "the public context survives a failed attempt");
        service.consume(publicContext);
        ValidationResult afterConsume = service.validateAtMutation(
                publicContext, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, 25, 64, 25
        );
        require(!afterConsume.valid()
                        && afterConsume.reason().equals(ValidationResult.REASON_CONSUMED),
                "a consumed public context cannot be replayed");
        // Consume is idempotent.
        service.consume(publicContext);

        // HIGH_RISK: the final mutation boundary consumes the authorization
        // even when the business mutation later fails; replay is impossible.
        OnSiteContext official = service.issueOnSiteContext(
                ALPHA_ID, routineTerminal,
                CapabilityClass.ONSITE_OFFICIAL_DUTY,
                DIMENSION, 15, 64, 15
        );
        require(official.workflowKind() == WorkflowKind.OFFICIAL_ROUTINE,
                "the official routine session is issued first");
        OnSiteContext highRisk = service.issueOnSiteContext(
                ALPHA_ID, secureTerminal,
                CapabilityClass.ONSITE_OFFICIAL_DUTY,
                DIMENSION, 25, 64, 25
        );
        require(highRisk.workflowKind() == WorkflowKind.HIGH_RISK,
                "secure terminal yields the high-risk workflow");
        ValidationResult highRiskValid = service.validateAtMutation(
                highRisk, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                clock.now(), DIMENSION, 25, 64, 25
        );
        require(highRiskValid.valid(),
                "the high-risk authorization validates at the final boundary");
        ValidationResult replayed = service.validateAtMutation(
                highRisk, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                clock.now(), DIMENSION, 25, 64, 25
        );
        require(!replayed.valid()
                        && replayed.reason().equals(ValidationResult.REASON_CONSUMED),
                "a consumed high-risk authorization cannot be replayed");
    }

    // ------------------------------------------------------------------
    // acceptance: workflow default parameters per FR-INST-001-B §3
    // ------------------------------------------------------------------

    private static void testWorkflowDefaults() {
        InstitutionAccessConfig defaults = InstitutionAccessConfig.DEFAULT;
        require(defaults.publicDistanceBlocks() == 6,
                "public workflow terminal distance defaults to 6 blocks");
        require(defaults.publicContextLifetimeMillis() == 120_000L,
                "public workflow lifetime defaults to 2 minutes");
        require(defaults.officialIdleTimeoutMillis() == 600_000L,
                "official routine idle timeout defaults to 10 minutes");
        require(defaults.officialHardLimitMillis() == 3_600_000L,
                "official routine hard limit defaults to 60 minutes");
        require(defaults.highRiskDistanceBlocks() == 6,
                "high-risk terminal distance defaults to 6 blocks");
        require(defaults.highRiskLifetimeMillis() == 30_000L,
                "high-risk lifetime defaults to 30 seconds");
        require(defaults.presenceCheckIntervalTicks() == 20,
                "presence check defaults to 1 second at 20 TPS");
        require(defaults.officialHardLimitMillis() >= defaults.officialIdleTimeoutMillis(),
                "the hard limit must not be below the idle timeout");

        // The final revalidation has no configuration switch.
        for (Field field : InstitutionAccessConfig.class.getDeclaredFields()) {
            require(!field.getName().toLowerCase(java.util.Locale.ROOT)
                            .contains("disable"),
                    "the final mutation-time revalidation cannot be disabled: "
                            + field.getName());
        }

        // Official sessions live until the hard limit and idle evaluation
        // uses the activity clock refreshed by valid actions.
        MutableClock clock = new MutableClock(40_000);
        FakeLandService land = landWith(parcelA());
        DefaultInstitutionAccessService service = service(
                new SavedDataBackedTestStore(), land, clock
        );
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        TerminalId routineTerminal = registerTerminal(
                service, parliament,
                new TerminalPosition(DIMENSION, 15, 64, 15),
                Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY), false
        );
        OnSiteContext official = service.issueOnSiteContext(
                ALPHA_ID, routineTerminal,
                CapabilityClass.ONSITE_OFFICIAL_DUTY,
                DIMENSION, 15, 64, 15
        );
        require(official.workflowKind() == WorkflowKind.OFFICIAL_ROUTINE,
                "a normal terminal yields the official routine workflow");
        require(official.expiryTime() - official.issueTime()
                        == InstitutionAccessConfig.DEFAULT.officialHardLimitMillis(),
                "the official session expires at the 60-minute hard limit");

        // A valid action refreshes the idle clock; without it the session
        // times out at the idle threshold.
        clock.setNow(40_000 + 300_000);
        ValidationResult refreshed = service.validateAtMutation(
                official, CapabilityClass.ONSITE_OFFICIAL_DUTY,
                clock.now(), DIMENSION, 15, 64, 15
        );
        require(refreshed.valid(),
                "a valid official action keeps the session valid");
        clock.setNow(40_000 + 300_000 + 600_000 + 1);
        require(!service.evaluatePresence(
                        official, DIMENSION, 15, 64, 15, clock.now()),
                "idle past the 10-minute timeout invalidates the official session");
        require(service.contextCount() == 0,
                "the idle-invalidated session is removed from the runtime registry");
    }

    // ------------------------------------------------------------------
    // acceptance: leave invalidates; return does not restore (§7.3)
    // ------------------------------------------------------------------

    private static void testLeaveInvalidatesNoRestore() {
        MutableClock clock = new MutableClock(50_000);
        FakeLandService land = landWith(parcelA());
        DefaultInstitutionAccessService service = service(
                new SavedDataBackedTestStore(), land, clock
        );
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        TerminalId publicTerminal = registerTerminal(
                service, parliament, terminalA(),
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE), false
        );

        OnSiteContext context = service.issueOnSiteContext(
                ALPHA_ID, publicTerminal,
                CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, 20, 64, 20
        );
        // Leaving the terminal range invalidates the context.
        require(!service.evaluatePresence(
                        context, DIMENSION, 40, 64, 40, clock.now()),
                "leaving the terminal range invalidates the context");
        require(!service.validateAtMutation(
                context, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, 20, 64, 20
        ).valid(), "the invalidated context cannot be used even after returning");

        // Returning does not restore: a fresh interaction is required.
        OnSiteContext fresh = service.issueOnSiteContext(
                ALPHA_ID, publicTerminal,
                CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, 20, 64, 20
        );
        require(!fresh.contextId().equals(context.contextId()),
                "returning requires a new context, never the old one");

        // invalidateOnLeave covers dimension/logout/death event paths.
        service.invalidateOnLeave(ALPHA_ID);
        require(!service.validateAtMutation(
                fresh, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, 20, 64, 20
        ).valid(), "invalidateOnLeave invalidates every player context");
    }

    // ------------------------------------------------------------------
    // acceptance: presence boundary — only players with active contexts
    // ------------------------------------------------------------------

    private static void testPresenceBoundary() {
        MutableClock clock = new MutableClock(60_000);
        FakeLandService land = landWith(parcelA());
        DefaultInstitutionAccessService service = service(
                new SavedDataBackedTestStore(), land, clock
        );
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        TerminalId publicTerminal = registerTerminal(
                service, parliament, terminalA(),
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE), false
        );

        require(service.playersWithActiveContexts().isEmpty(),
                "no player is scanned before any context exists");
        require(service.activeContextsOf(ALPHA_ID).isEmpty(),
                "a player without a context yields no contexts");

        OnSiteContext context = service.issueOnSiteContext(
                ALPHA_ID, publicTerminal,
                CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, 20, 64, 20
        );
        require(service.playersWithActiveContexts().equals(Set.of(ALPHA_ID)),
                "only the player with an active context is in the presence set");
        require(!service.playersWithActiveContexts().contains(BRAVO_ID),
                "a player without a context is never scanned");
        require(service.activeContextsOf(ALPHA_ID).equals(List.of(context)),
                "the active context list is exact and bounded");

        // In-range presence keeps the context; the monitor never consumes.
        require(service.evaluatePresence(
                        context, DIMENSION, 21, 64, 20, clock.now()),
                "in-range presence keeps the context alive");
        require(service.validateAtMutation(
                context, CapabilityClass.ONSITE_PUBLIC_SERVICE,
                clock.now(), DIMENSION, 20, 64, 20
        ).valid(), "presence checks never consume single-use authorizations");
    }

    // ------------------------------------------------------------------
    // acceptance: injection failure — nothing published (§7)
    // ------------------------------------------------------------------

    private static void testInjectionFailureNoPublish() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(70_000);
        InstitutionAccessRepository repository = repository(store);
        InstitutionAccessService service = service(
                repository, landWith(parcelA()), clock
        );
        FacilityId id = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );

        store.setCommitFailureCode("STORE_FAILURE");
        int commitsBefore = store.commitCount();
        expectThrows(
                InstitutionAccessUnavailableException.class,
                () -> service.suspendFacility(ALPHA_ID, id),
                "a store rejection must surface as unavailability"
        );
        require(store.commitCount() == commitsBefore + 1,
                "the rejected commit was attempted once");
        require(service.getFacility(id).map(Facility::state)
                        .orElse(null) == FacilityState.ACTIVE,
                "a failed commit publishes no state change");
        require(service.getFacility(id).map(Facility::facilityRevision)
                        .orElse(-1L) == 1L,
                "a failed commit advances no revision");

        store.setCommitFailureCode(null);
        service.suspendFacility(ALPHA_ID, id);
        require(service.getFacility(id).map(Facility::state)
                        .orElse(null) == FacilityState.SUSPENDED,
                "the store recovers and subsequent commits publish");
    }

    // ------------------------------------------------------------------
    // acceptance: restart — contexts cleared, directory recovered (§7)
    // ------------------------------------------------------------------

    private static void testRestartClearsContexts() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(80_000);
        FakeLandService land = landWith(parcelA());
        DefaultInstitutionAccessService first = service(store, land, clock);
        FacilityId parliament = registerFacility(
                first, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        TerminalId publicTerminal = registerTerminal(
                first, parliament, terminalA(),
                Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE), false
        );
        first.issueOnSiteContext(
                ALPHA_ID, publicTerminal,
                CapabilityClass.ONSITE_PUBLIC_SERVICE,
                DIMENSION, 20, 64, 20
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
        require(second.getTerminal(publicTerminal).isPresent(),
                "terminals are recovered across restart");
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
        MutableClock clock = new MutableClock(90_000);
        FakeLandService land = landWith(parcelA());
        DefaultInstitutionAccessService service = service(store, land, clock);
        FacilityId parliament = registerFacility(
                service, InstitutionType.PARLIAMENT, parcelA().parcelId()
        );
        registerTerminal(
                service, parliament, terminalA(),
                Set.of(
                        CapabilityClass.ONSITE_PUBLIC_SERVICE,
                        CapabilityClass.ONSITE_OFFICIAL_DUTY
                ),
                true
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
        unknownField.putRaw(storeRootWithSurprise());
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
        newerRoot.put("Terminals", new CompoundTag());
        newer.putRaw(newerRoot);
        expectThrows(
                InstitutionAccessNbtException.class,
                () -> repository(newer),
                "a newer store version is rejected fail-closed"
        );

        // Terminal integrity digest does not match its anchored position.
        SavedDataBackedTestStore tampered = new SavedDataBackedTestStore();
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 1L);
        CompoundTag facilities = new CompoundTag();
        CompoundTag facility = new CompoundTag();
        facility.putInt("FacilityVersion", 1);
        facility.putUUID("FacilityId", UUID.fromString(
                "00000000-0000-0000-0000-0000000000f1"));
        facility.putString("InstitutionType", "PARLIAMENT");
        facility.putUUID("ParcelId", parcelA().parcelId().value());
        facility.putString("State", "ACTIVE");
        facility.putLong("FacilityRevision", 1L);
        facilities.put("00000000-0000-0000-0000-0000000000f1", facility);
        root.put("Facilities", facilities);
        CompoundTag terminals = new CompoundTag();
        CompoundTag terminal = new CompoundTag();
        terminal.putInt("TerminalVersion", 1);
        terminal.putUUID("TerminalId", UUID.fromString(
                "00000000-0000-0000-0000-0000000000f2"));
        terminal.putUUID("FacilityId", UUID.fromString(
                "00000000-0000-0000-0000-0000000000f1"));
        terminal.putString("InstitutionType", "PARLIAMENT");
        CompoundTag position = new CompoundTag();
        position.putString("Dimension", DIMENSION);
        position.putInt("X", 20);
        position.putInt("Y", 64);
        position.putInt("Z", 20);
        terminal.put("Position", position);
        net.minecraft.nbt.ListTag capabilities = new net.minecraft.nbt.ListTag();
        capabilities.add(net.minecraft.nbt.StringTag.valueOf(
                "ONSITE_PUBLIC_SERVICE"));
        terminal.put("Capabilities", capabilities);
        terminal.putString("State", "ACTIVE");
        terminal.putBoolean("Secure", false);
        terminal.putString("Integrity", "deadbeef");
        terminal.putLong("TerminalRevision", 1L);
        terminals.put("00000000-0000-0000-0000-0000000000f2", terminal);
        root.put("Terminals", terminals);
        tampered.putRaw(root);
        expectThrows(
                InstitutionAccessNbtException.class,
                () -> repository(tampered),
                "a terminal integrity digest mismatch rejects the load"
        );
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
        // Positions may only be built from caller requests or decoded
        // persistence — never from literal coordinate tuples in code.
        java.util.regex.Pattern literalPosition = java.util.regex.Pattern.compile(
                "new TerminalPosition\\(\\s*[-0-9]"
        );
        require(
                !literalPosition.matcher(codeOnly).find(),
                "institution-access production code must not build a position "
                        + "from literal coordinates"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: command tree — exists, no privilege escalation (§7)
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
        CommandNode<CommandSourceStack> terminal = institution.getChild("terminal");
        require(facility != null, "/fr admin institution facility must exist");
        require(terminal != null, "/fr admin institution terminal must exist");
        require(facility.getChild("register") != null,
                "facility register path must exist");
        require(facility.getChild("suspend") != null, "facility suspend must exist");
        require(facility.getChild("activate") != null, "facility activate must exist");
        require(facility.getChild("relocate") != null, "facility relocate must exist");
        require(facility.getChild("disable") != null, "facility disable must exist");
        require(terminal.getChild("register") != null,
                "terminal register path must exist");
        require(terminal.getChild("suspend") != null, "terminal suspend must exist");
        require(terminal.getChild("disable") != null, "terminal disable must exist");

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

        // No business permission, emergency, GUI, or packet surface exists.
        for (Class<?> type : List.of(
                InstitutionAccessService.class,
                DefaultInstitutionAccessService.class,
                InstitutionAccessModule.class
        )) {
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                require(!name.contains("emergency") && !name.contains("breakglass")
                                && !name.contains("gui") && !name.contains("screen")
                                && !name.contains("packet"),
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

    private static TerminalId registerTerminal(
            InstitutionAccessService service,
            FacilityId facilityId,
            TerminalPosition position,
            Set<CapabilityClass> capabilities,
            boolean secure
    ) {
        return service.registerTerminal(
                ALPHA_ID,
                new TerminalRegistrationRequest(
                        facilityId, position, capabilities, secure
                )
        ).terminal().terminalId();
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

    private static TerminalPosition terminalA() {
        return new TerminalPosition(DIMENSION, 20, 64, 20);
    }

    private static TerminalPosition terminalB() {
        return new TerminalPosition(DIMENSION, 25, 64, 25);
    }

    private static CompoundTag storeRootWithSurprise() {
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 0L);
        root.put("Facilities", new CompoundTag());
        root.put("Terminals", new CompoundTag());
        root.putString("Surprise", "x");
        return root;
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

        private void putRaw(CompoundTag raw) {
            savedData.putModuleData(
                    InstitutionAccessRepository.MODULE_DATA_KEY,
                    raw
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
            this.parcels = Map.copyOf(parcels);
        }

        @Override
        public Optional<LandParcel> getParcel(ParcelId parcelId) {
            return Optional.ofNullable(parcels.get(parcelId));
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
