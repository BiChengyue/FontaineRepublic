package com.fontainerepublic.server.institutionaccess.service;

import com.fontainerepublic.server.audit.api.AuditDraft;
import com.fontainerepublic.server.audit.api.AuditReceipt;
import com.fontainerepublic.server.audit.api.AuditService;
import com.fontainerepublic.server.audit.model.AuditActorType;
import com.fontainerepublic.server.audit.model.AuditCategory;
import com.fontainerepublic.server.audit.model.AuditClassification;
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
import com.fontainerepublic.server.institutionaccess.model.Terminal;
import com.fontainerepublic.server.institutionaccess.model.TerminalId;
import com.fontainerepublic.server.institutionaccess.model.WorkflowKind;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessRepository;
import com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessUnavailableException;
import com.fontainerepublic.server.land.api.LandService;
import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.model.ParcelId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Runtime implementation of {@link InstitutionAccessService}
 * (FR-INST-002-A §4/§5).
 *
 * <p>The service is a thin, owner-thread-scoped facade over the single-writer
 * {@link InstitutionAccessRepository}. It consumes FR-LAND spatial data
 * read-only through {@link LandService} (never copied, never hard-coded),
 * resolves actors through PlayerData + FR-ID, records authoritative directory
 * mutations through the audit service, and owns the server-runtime on-site
 * context registry with mandatory final mutation-time revalidation that
 * cannot be disabled.</p>
 *
 * <p>Workflow selection (FR-INST-001-B §3): {@code ONSITE_PUBLIC_SERVICE}
 * selects the public workflow; {@code ONSITE_OFFICIAL_DUTY} selects the
 * official routine workflow on a normal terminal and the high-risk workflow
 * on a secure terminal (which additionally requires an already-valid
 * official routine session).</p>
 */
public final class DefaultInstitutionAccessService implements InstitutionAccessService {

    private static final Set<CapabilityClass> ON_SITE_CAPABILITIES =
            Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE, CapabilityClass.ONSITE_OFFICIAL_DUTY);

    private final InstitutionAccessRepository repository;
    private final LandService landService;
    private final ActorResolver actors;
    private final AuditService auditService;
    private final LongSupplier clock;
    private final InstitutionAccessConfig config;
    private final OnSiteContextRegistry contexts;

    public DefaultInstitutionAccessService(
            InstitutionAccessRepository repository,
            LandService landService,
            ActorResolver actors,
            AuditService auditService,
            LongSupplier clock,
            InstitutionAccessConfig config
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.landService = Objects.requireNonNull(landService, "landService");
        this.actors = Objects.requireNonNull(actors, "actors");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.config = Objects.requireNonNull(config, "config");
        this.contexts = new OnSiteContextRegistry(DEFAULT_MAX_CONTEXTS);
    }

    /** Bounded server-runtime context budget (FR-INST-001-A §7.4). */
    public static final int DEFAULT_MAX_CONTEXTS = 4096;

    // ------------------------------------------------------------------
    // facility directory
    // ------------------------------------------------------------------

    @Override
    public FacilityReceipt registerFacility(
            UUID actor,
            FacilityRegistrationRequest request
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(request, "request");
        requireResolvableActor(actor);
        LandParcel parcel = requireParcel(request.parcelId());
        if (repository.findByParcelId(parcel.parcelId()).isPresent()) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_PARCEL_ALREADY_BOUND,
                    "Parcel " + parcel.parcelId() + " is already bound to a facility"
            );
        }
        Facility facility = repository.registerFacility(
                request.institutionType(),
                request.parcelId()
        );
        audit(
                actor,
                "facility.register",
                "facility",
                facility.facilityId().canonicalKey(),
                "Registered " + facility.institutionType() + " facility "
                        + facility.facilityId() + " on parcel " + facility.parcelId()
        );
        return new FacilityReceipt(
                FacilityChangeKind.FACILITY_REGISTERED,
                facility,
                true,
                now()
        );
    }

    @Override
    public FacilityReceipt suspendFacility(UUID actor, FacilityId facilityId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(facilityId, "facilityId");
        requireResolvableActor(actor);
        Facility before = repository.requireFacility(facilityId);
        Facility after = repository.suspendFacility(facilityId);
        if (!after.equals(before)) {
            contexts.invalidateByFacility(facilityId);
            audit(actor, "facility.suspend", "facility",
                    facilityId.canonicalKey(),
                    "Suspended facility " + facilityId);
        }
        return new FacilityReceipt(
                FacilityChangeKind.FACILITY_SUSPENDED, after, !after.equals(before), now()
        );
    }

    @Override
    public FacilityReceipt activateFacility(UUID actor, FacilityId facilityId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(facilityId, "facilityId");
        requireResolvableActor(actor);
        Facility before = repository.requireFacility(facilityId);
        Facility after = repository.activateFacility(facilityId);
        if (!after.equals(before)) {
            audit(actor, "facility.activate", "facility",
                    facilityId.canonicalKey(),
                    "Activated facility " + facilityId);
        }
        return new FacilityReceipt(
                FacilityChangeKind.FACILITY_ACTIVATED, after, !after.equals(before), now()
        );
    }

    @Override
    public FacilityReceipt relocateFacility(
            UUID actor,
            FacilityId facilityId,
            ParcelId newParcelId
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(facilityId, "facilityId");
        Objects.requireNonNull(newParcelId, "newParcelId");
        requireResolvableActor(actor);
        Facility current = repository.requireFacility(facilityId);
        if (current.parcelId().equals(newParcelId)) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_INVALID_REQUEST,
                    "Relocation target parcel equals the current parcel of "
                            + facilityId
            );
        }
        LandParcel parcel = requireParcel(newParcelId);
        if (repository.findByParcelId(parcel.parcelId()).isPresent()) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_PARCEL_ALREADY_BOUND,
                    "Parcel " + parcel.parcelId() + " is already bound to a facility"
            );
        }
        Facility after = repository.relocateFacility(facilityId, newParcelId);
        contexts.invalidateByFacility(facilityId);
        audit(actor, "facility.relocate", "facility",
                facilityId.canonicalKey(),
                "Relocated facility " + facilityId + " onto parcel " + newParcelId);
        return new FacilityReceipt(
                FacilityChangeKind.FACILITY_RELOCATED, after, true, now()
        );
    }

    @Override
    public FacilityReceipt disableFacility(UUID actor, FacilityId facilityId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(facilityId, "facilityId");
        requireResolvableActor(actor);
        Facility before = repository.requireFacility(facilityId);
        Facility after = repository.disableFacility(facilityId);
        if (!after.equals(before)) {
            contexts.invalidateByFacility(facilityId);
            audit(actor, "facility.disable", "facility",
                    facilityId.canonicalKey(),
                    "Disabled facility " + facilityId);
        }
        return new FacilityReceipt(
                FacilityChangeKind.FACILITY_DISABLED, after, !after.equals(before), now()
        );
    }

    // ------------------------------------------------------------------
    // terminal directory
    // ------------------------------------------------------------------

    @Override
    public TerminalReceipt registerTerminal(
            UUID actor,
            TerminalRegistrationRequest request
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(request, "request");
        requireResolvableActor(actor);
        Facility facility = repository.requireFacility(request.facilityId());
        if (facility.state() != com.fontainerepublic.server.institutionaccess.model.FacilityState.ACTIVE) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_FACILITY_NOT_ACTIVE,
                    "Facility " + request.facilityId() + " is not ACTIVE; "
                            + "terminals may only be registered on an ACTIVE facility"
            );
        }
        verifyTerminalInsideFacilityRegion(facility, request);
        if (repository.findByPosition(
                request.position().dimension(),
                request.position().x(),
                request.position().y(),
                request.position().z()
        ).isPresent()) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_INVALID_REQUEST,
                    "A terminal is already anchored at "
                            + request.position().dimension() + " "
                            + request.position().x() + " " + request.position().y()
                            + " " + request.position().z()
            );
        }
        Terminal terminal = repository.registerTerminal(
                request.facilityId(),
                facility.institutionType(),
                request.position(),
                request.capabilitySet(),
                request.secure()
        );
        audit(actor, "terminal.register", "terminal",
                terminal.terminalId().canonicalKey(),
                "Registered " + terminal.institutionType() + " terminal "
                        + terminal.terminalId() + " on facility "
                        + terminal.facilityId()
                        + (terminal.secure() ? " (secure)" : ""));
        return new TerminalReceipt(
                TerminalChangeKind.TERMINAL_REGISTERED,
                terminal,
                true,
                now()
        );
    }

    @Override
    public TerminalReceipt suspendTerminal(UUID actor, TerminalId terminalId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(terminalId, "terminalId");
        requireResolvableActor(actor);
        Terminal before = repository.requireTerminal(terminalId);
        Terminal after = repository.suspendTerminal(terminalId);
        if (!after.equals(before)) {
            contexts.invalidateByTerminal(terminalId);
            audit(actor, "terminal.suspend", "terminal",
                    terminalId.canonicalKey(),
                    "Suspended terminal " + terminalId);
        }
        return new TerminalReceipt(
                TerminalChangeKind.TERMINAL_SUSPENDED, after, !after.equals(before), now()
        );
    }

    @Override
    public TerminalReceipt disableTerminal(UUID actor, TerminalId terminalId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(terminalId, "terminalId");
        requireResolvableActor(actor);
        Terminal before = repository.requireTerminal(terminalId);
        Terminal after = repository.disableTerminal(terminalId);
        if (!after.equals(before)) {
            contexts.invalidateByTerminal(terminalId);
            audit(actor, "terminal.disable", "terminal",
                    terminalId.canonicalKey(),
                    "Disabled terminal " + terminalId);
        }
        return new TerminalReceipt(
                TerminalChangeKind.TERMINAL_DISABLED, after, !after.equals(before), now()
        );
    }

    // ------------------------------------------------------------------
    // on-site contexts
    // ------------------------------------------------------------------

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
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(terminalId, "terminalId");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(playerDimension, "playerDimension");
        requireResolvableActor(playerId);

        Terminal terminal = repository.requireTerminal(terminalId);
        if (terminal.state() != com.fontainerepublic.server.institutionaccess.model.TerminalState.ACTIVE) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_TERMINAL_NOT_ACTIVE,
                    "Terminal " + terminalId + " is not ACTIVE"
            );
        }
        Facility facility = repository.requireFacility(terminal.facilityId());
        if (facility.state() != com.fontainerepublic.server.institutionaccess.model.FacilityState.ACTIVE) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_FACILITY_NOT_ACTIVE,
                    "Facility " + facility.facilityId() + " is not ACTIVE"
            );
        }
        if (facility.institutionType() != terminal.institutionType()) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_INSTITUTION_MISMATCH,
                    "Terminal " + terminalId + " institution type does not match "
                            + "facility " + facility.facilityId()
            );
        }
        if (!ON_SITE_CAPABILITIES.contains(capability)) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_UNSUPPORTED_CAPABILITY,
                    "On-site contexts may only be issued for on-site capability "
                            + "classes; got " + capability
            );
        }
        if (!terminal.capabilitySet().contains(capability)) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_CAPABILITY_NOT_ALLOWED,
                    "Terminal " + terminalId + " does not allow " + capability
            );
        }
        if (!terminal.position().dimension().equals(playerDimension)) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_DIMENSION_MISMATCH,
                    "Player dimension " + playerDimension
                            + " does not match terminal dimension "
                            + terminal.position().dimension()
            );
        }
        LandParcel parcel = requireParcel(facility.parcelId());
        requireTerminalInsideRegion(parcel, terminal);

        WorkflowKind workflow = workflowFor(capability, terminal.secure());
        if (workflow == WorkflowKind.HIGH_RISK
                && contexts.findActive(playerId, WorkflowKind.OFFICIAL_ROUTINE).isEmpty()) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_OFFICIAL_SESSION_REQUIRED,
                    "A high-risk authorization requires a valid official "
                            + "routine session at " + facility.institutionType()
                            + " facility " + facility.facilityId()
            );
        }

        long now = now();
        long lifetime = lifetimeMillis(workflow);
        long expiry = checkedExpiry(now, lifetime);
        verifyPresenceRange(workflow, terminal, parcel, playerDimension, x, y, z);

        OnSiteContext context = new OnSiteContext(
                UUID.randomUUID(),
                playerId,
                facility.institutionType(),
                facility.facilityId(),
                terminalId,
                workflow,
                capability,
                now,
                expiry,
                facility.facilityRevision(),
                terminal.terminalRevision(),
                playerDimension,
                x,
                y,
                z
        );
        contexts.issue(context, now);
        return context;
    }

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
        if (context == null) {
            return ValidationResult.invalid(ValidationResult.REASON_NOT_ISSUED);
        }
        if (!contexts.isActive(context.contextId())) {
            return ValidationResult.invalid(
                    contexts.statusOf(context.contextId())
                            .map(status -> status == OnSiteContextRegistry.Status.CONSUMED
                                    ? ValidationResult.REASON_CONSUMED
                                    : ValidationResult.REASON_INVALIDATED)
                            .orElse(ValidationResult.REASON_NOT_ISSUED)
            );
        }
        if (!Objects.equals(capability, context.capability())) {
            return ValidationResult.invalid(ValidationResult.REASON_CAPABILITY_MISMATCH);
        }
        if (now > context.expiryTime()) {
            return ValidationResult.invalid(ValidationResult.REASON_EXPIRED);
        }

        Facility facility = repository.findByFacilityId(context.facilityId()).orElse(null);
        if (facility == null
                || facility.state() != com.fontainerepublic.server.institutionaccess.model.FacilityState.ACTIVE) {
            return ValidationResult.invalid(ValidationResult.REASON_FACILITY_NOT_ACTIVE);
        }
        if (facility.facilityRevision() != context.facilityRevision()) {
            return ValidationResult.invalid(ValidationResult.REASON_FACILITY_REVISION);
        }
        Terminal terminal = repository.findByTerminalId(context.terminalId()).orElse(null);
        if (terminal == null
                || terminal.state() != com.fontainerepublic.server.institutionaccess.model.TerminalState.ACTIVE) {
            return ValidationResult.invalid(ValidationResult.REASON_TERMINAL_NOT_ACTIVE);
        }
        if (terminal.terminalRevision() != context.terminalRevision()) {
            return ValidationResult.invalid(ValidationResult.REASON_TERMINAL_REVISION);
        }
        if (!terminal.position().dimension().equals(dimension)) {
            return ValidationResult.invalid(ValidationResult.REASON_DIMENSION_MISMATCH);
        }
        if (!terminal.capabilitySet().contains(context.capability())) {
            return ValidationResult.invalid(ValidationResult.REASON_CAPABILITY_MISMATCH);
        }

        LandParcel parcel;
        try {
            parcel = landService.getParcel(facility.parcelId()).orElse(null);
        } catch (RuntimeException failure) {
            parcel = null;
        }
        if (parcel == null) {
            return ValidationResult.invalid(ValidationResult.REASON_TERMINAL_NOT_IN_REGION);
        }
        if (!isInsideParcel(parcel, terminal.position().dimension(),
                terminal.position().x(), terminal.position().y(), terminal.position().z())) {
            return ValidationResult.invalid(ValidationResult.REASON_TERMINAL_NOT_IN_REGION);
        }
        if (!presenceOk(context.workflowKind(), terminal, parcel, dimension, x, y, z)) {
            return ValidationResult.invalid(ValidationResult.REASON_PLAYER_OUT_OF_RANGE);
        }

        switch (context.workflowKind()) {
            case HIGH_RISK ->
                // Single-use authorization is consumed at the final submission
                // boundary, whether the business mutation later succeeds or fails
                // (FR-INST-001-B §3.3).
                    contexts.consume(context.contextId());
            case OFFICIAL_ROUTINE ->
                // Only valid institutional actions refresh the idle clock
                // (FR-INST-001-B §3.2).
                    contexts.refreshActivity(context.contextId(), now);
            case PUBLIC ->
                // Consumed by the caller on successful submission (§3.1);
                // validation failure does not consume.
                    { }
        }
        return ValidationResult.ok();
    }

    @Override
    public void consume(OnSiteContext context) {
        Objects.requireNonNull(context, "context");
        if (context.workflowKind() == WorkflowKind.PUBLIC
                || context.workflowKind() == WorkflowKind.HIGH_RISK) {
            contexts.consume(context.contextId());
        }
        // OFFICIAL_ROUTINE sessions are not single-use; consume is a no-op.
    }

    @Override
    public void invalidateOnLeave(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        contexts.invalidateAll(playerId);
    }

    @Override
    public Optional<Facility> getFacility(FacilityId facilityId) {
        Objects.requireNonNull(facilityId, "facilityId");
        return repository.findByFacilityId(facilityId);
    }

    @Override
    public Optional<Terminal> getTerminal(TerminalId terminalId) {
        Objects.requireNonNull(terminalId, "terminalId");
        return repository.findByTerminalId(terminalId);
    }

    // ------------------------------------------------------------------
    // presence monitoring hooks (bounded 1-second check; lifecycle events)
    // ------------------------------------------------------------------

    /** All players currently holding at least one active context. */
    public Set<UUID> playersWithActiveContexts() {
        return contexts.playersWithActiveContexts();
    }

    /** Every active context of a player (used by the presence monitor). */
    public java.util.List<OnSiteContext> activeContextsOf(UUID playerId) {
        return contexts.activeContextsOf(playerId);
    }

    /**
     * Bounded presence evaluation of one active context. Returns true when
     * the player position still satisfies the workflow constraints; returns
     * false (and invalidates the context) when the player left range, changed
     * dimension, expired, or hit an official idle/hard limit. Never consumes
     * single-use authorizations — only the final mutation boundary does.
     */
    public boolean evaluatePresence(
            OnSiteContext context,
            String dimension,
            int x,
            int y,
            int z,
            long now
    ) {        if (!contexts.isActive(context.contextId())) {
            return false;
        }
        if (now > context.expiryTime()) {
            contexts.invalidate(context.contextId());
            return false;
        }
        Facility facility = repository.findByFacilityId(context.facilityId()).orElse(null);
        Terminal terminal = repository.findByTerminalId(context.terminalId()).orElse(null);
        if (facility == null || terminal == null) {
            contexts.invalidate(context.contextId());
            return false;
        }
        LandParcel parcel = safeParcel(facility.parcelId());
        if (parcel == null) {
            contexts.invalidate(context.contextId());
            return false;
        }
        if (!isInsideParcel(parcel, terminal.position().dimension(),
                terminal.position().x(), terminal.position().y(), terminal.position().z())) {
            contexts.invalidate(context.contextId());
            return false;
        }
        if (context.workflowKind() == WorkflowKind.OFFICIAL_ROUTINE) {
            long lastActivity = contexts.lastActivityOf(context.contextId()).orElse(now);
            if (now - lastActivity > config.officialIdleTimeoutMillis()) {
                contexts.invalidate(context.contextId());
                return false;
            }
            if (now - context.issueTime() > config.officialHardLimitMillis()) {
                contexts.invalidate(context.contextId());
                return false;
            }
        }
        if (!presenceOk(context.workflowKind(), terminal, parcel, dimension, x, y, z)) {
            contexts.invalidate(context.contextId());
            return false;
        }
        return true;
    }

    /** Clears every runtime context (server shutdown). */
    public void clearContexts() {
        contexts.clear();
    }

    /** Number of live runtime contexts (monitoring/tests). */
    public int contextCount() {
        return contexts.size();
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private void verifyTerminalInsideFacilityRegion(
            Facility facility,
            TerminalRegistrationRequest request
    ) {
        LandParcel parcel = requireParcel(facility.parcelId());
        if (!request.position().dimension().equals(parcel.dimension())) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_TERMINAL_OUTSIDE_REGION,
                    "Terminal dimension " + request.position().dimension()
                            + " does not match facility parcel dimension "
                            + parcel.dimension()
            );
        }
        if (!isInsideParcel(parcel, request.position().dimension(),
                request.position().x(), request.position().y(), request.position().z())) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_TERMINAL_OUTSIDE_REGION,
                    "Terminal position is outside the parcel region of facility "
                            + facility.facilityId()
            );
        }
    }

    private void requireTerminalInsideRegion(LandParcel parcel, Terminal terminal) {
        if (!isInsideParcel(parcel, terminal.position().dimension(),
                terminal.position().x(), terminal.position().y(), terminal.position().z())) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_TERMINAL_OUTSIDE_REGION,
                    "Terminal " + terminal.terminalId()
                            + " is no longer inside its facility's parcel region"
            );
        }
    }

    private void verifyPresenceRange(
            WorkflowKind workflow,
            Terminal terminal,
            LandParcel parcel,
            String playerDimension,
            int x,
            int y,
            int z
    ) {
        if (!presenceOk(workflow, terminal, parcel, playerDimension, x, y, z)) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_PLAYER_OUT_OF_RANGE,
                    "Player is not within the " + workflow
                            + " presence range of terminal "
                            + terminal.terminalId()
            );
        }
    }

    private boolean presenceOk(
            WorkflowKind workflow,
            Terminal terminal,
            LandParcel parcel,
            String playerDimension,
            int x,
            int y,
            int z
    ) {
        if (workflow == WorkflowKind.OFFICIAL_ROUTINE) {
            // Official routine presence is the registered internal work zone
            // (the facility parcel region in this task); the terminal anchors
            // the interaction that issued the session.
            return isInsideParcel(parcel, playerDimension, x, y, z);
        }
        return terminal.position().dimension().equals(playerDimension)
                && distanceSquared(terminal, x, y, z)
                <= workflowDistance(workflow) * (long) workflowDistance(workflow);
    }

    private boolean isInsideParcel(
            LandParcel parcel,
            String dimension,
            int x,
            int y,
            int z
    ) {
        return parcel.dimension().equals(dimension)
                && parcel.region().contains(x, y, z);
    }

    private long distanceSquared(Terminal terminal, int x, int y, int z) {
        long dx = (long) terminal.position().x() - x;
        long dy = (long) terminal.position().y() - y;
        long dz = (long) terminal.position().z() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private WorkflowKind workflowFor(CapabilityClass capability, boolean secureTerminal) {
        if (capability == CapabilityClass.ONSITE_PUBLIC_SERVICE) {
            return WorkflowKind.PUBLIC;
        }
        return secureTerminal ? WorkflowKind.HIGH_RISK : WorkflowKind.OFFICIAL_ROUTINE;
    }

    private long lifetimeMillis(WorkflowKind workflow) {
        return switch (workflow) {
            case PUBLIC -> config.publicContextLifetimeMillis();
            case OFFICIAL_ROUTINE -> config.officialHardLimitMillis();
            case HIGH_RISK -> config.highRiskLifetimeMillis();
        };
    }

    private int workflowDistance(WorkflowKind workflow) {
        return switch (workflow) {
            case PUBLIC -> config.publicDistanceBlocks();
            case OFFICIAL_ROUTINE -> 0;
            case HIGH_RISK -> config.highRiskDistanceBlocks();
        };
    }

    private long checkedExpiry(long now, long lifetimeMillis) {
        if (lifetimeMillis > Long.MAX_VALUE - now) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_INVALID_REQUEST,
                    "Workflow lifetime overflows the expiry timestamp"
            );
        }
        return now + lifetimeMillis;
    }

    private LandParcel requireParcel(ParcelId parcelId) {
        LandParcel parcel = safeParcel(parcelId);
        if (parcel == null) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_PARCEL_NOT_FOUND,
                    "No FR-LAND parcel exists for " + parcelId
            );
        }
        return parcel;
    }

    private LandParcel safeParcel(ParcelId parcelId) {
        try {
            return landService.getParcel(parcelId).orElse(null);
        } catch (RuntimeException failure) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_LAND_UNAVAILABLE,
                    "Land service rejected parcel resolution for "
                            + parcelId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private void requireResolvableActor(UUID playerId) {
        if (!actors.isAvailable()) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_HOLDER_DIRECTORY_UNAVAILABLE,
                    "PlayerData/subject services are not available for actor resolution"
            );
        }
        if (!actors.hasPlayerRecord(playerId)) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    "Player UUID has no authoritative PlayerData record: " + playerId
            );
        }
        if (!actors.hasActiveSubject(playerId)) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_INVALID_HOLDER,
                    "Player UUID has no active subject: " + playerId
            );
        }
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
                    AuditCategory.ADMINISTRATION,
                    "institution-access",
                    actionId,
                    Optional.of(targetType),
                    Optional.of(targetId),
                    AuditClassification.PUBLIC,
                    summary,
                    Optional.empty()
            );
            AuditReceipt receipt = auditService.recordAuthoritative(draft);
            if (!receipt.committed()) {
                com.mojang.logging.LogUtils.getLogger().warn(
                        "[InstitutionAccess] Audit of {} was not durably committed: {}",
                        actionId,
                        receipt.failureCode()
                );
            }
        } catch (RuntimeException failure) {
            // Audit failure never blocks an already-committed directory
            // mutation (audit is a record, not an authority).
            com.mojang.logging.LogUtils.getLogger().warn(
                    "[InstitutionAccess] Audit recording failed for {}: {}",
                    actionId,
                    failure.getMessage()
            );
        }
    }

    private InstitutionAccessUnavailableException unavailable(String code, String message) {
        return new InstitutionAccessUnavailableException(code, message);
    }

    private InstitutionAccessUnavailableException unavailable(
            String code,
            String message,
            Throwable cause
    ) {
        return new InstitutionAccessUnavailableException(code, message, cause);
    }

    private long now() {
        long value = clock.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("clock returned a negative timestamp");
        }
        return value;
    }
}
