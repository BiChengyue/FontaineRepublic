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
import com.fontainerepublic.server.institutionaccess.api.ValidationResult;
import com.fontainerepublic.server.institutionaccess.api.ZoneChangeKind;
import com.fontainerepublic.server.institutionaccess.api.ZoneReceipt;
import com.fontainerepublic.server.institutionaccess.api.ZoneRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.Facility;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.FacilityState;
import com.fontainerepublic.server.institutionaccess.model.WorkflowKind;
import com.fontainerepublic.server.institutionaccess.model.Zone;
import com.fontainerepublic.server.institutionaccess.model.ZoneId;
import com.fontainerepublic.server.institutionaccess.model.ZoneKind;
import com.fontainerepublic.server.institutionaccess.model.ZoneRegion;
import com.fontainerepublic.server.institutionaccess.model.ZoneState;
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
 * (FR-INST-002-A §4/§5, revised to zones by FR-INST-002-B).
 *
 * <p>The service is a thin, owner-thread-scoped facade over the single-writer
 * {@link InstitutionAccessRepository}. It consumes FR-LAND spatial data
 * read-only through {@link LandService} (never copied, never hard-coded),
 * resolves actors through PlayerData + FR-ID, records authoritative directory
 * mutations through the audit service, and owns the server-runtime on-site
 * context registry with mandatory final mutation-time revalidation that
 * cannot be disabled.</p>
 *
 * <p>Workflow selection (FR-INST-001-B §3, FR-INST-002-B §4): a PUBLIC zone
 * serves the public workflow, an OFFICIAL zone the official routine
 * workflow, and a SECURE zone the high-risk workflow (which additionally
 * requires an already-valid official routine session). On-site presence is
 * zone containment: the player must be inside the registered zone region.</p>
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
    // zone directory
    // ------------------------------------------------------------------

    @Override
    public ZoneReceipt addZone(UUID actor, ZoneRegistrationRequest request) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(request, "request");
        requireResolvableActor(actor);
        Facility facility = repository.requireFacility(request.facilityId());
        if (facility.state() != FacilityState.ACTIVE) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_FACILITY_NOT_ACTIVE,
                    "Facility " + request.facilityId() + " is not ACTIVE; "
                            + "zones may only be added to an ACTIVE facility"
            );
        }
        verifyZoneRegion(facility, request.region());
        verifyKindCapabilities(request.kind(), request.capabilitySet());
        Zone zone = repository.addZone(
                request.facilityId(),
                facility.institutionType(),
                request.kind(),
                request.region(),
                request.capabilitySet()
        );
        audit(actor, "zone.add", "zone",
                zone.zoneId().canonicalKey(),
                "Added " + zone.kind() + " zone " + zone.zoneId()
                        + " on facility " + zone.facilityId());
        return new ZoneReceipt(
                ZoneChangeKind.ZONE_ADDED,
                zone,
                true,
                now()
        );
    }

    @Override
    public ZoneReceipt removeZone(UUID actor, ZoneId zoneId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(zoneId, "zoneId");
        requireResolvableActor(actor);
        Zone removed = repository.removeZone(zoneId);
        contexts.invalidateByZone(zoneId);
        audit(actor, "zone.remove", "zone",
                zoneId.canonicalKey(),
                "Removed zone " + zoneId);
        return new ZoneReceipt(
                ZoneChangeKind.ZONE_REMOVED,
                null,
                true,
                now()
        );
    }

    @Override
    public ZoneReceipt resizeZone(UUID actor, ZoneId zoneId, ZoneRegion newRegion) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(newRegion, "newRegion");
        requireResolvableActor(actor);
        Zone before = repository.requireZone(zoneId);
        Facility facility = repository.requireFacility(before.facilityId());
        verifyZoneRegion(facility, newRegion);
        Zone after = repository.resizeZone(zoneId, newRegion);
        if (!after.equals(before)) {
            contexts.invalidateByZone(zoneId);
            audit(actor, "zone.resize", "zone",
                    zoneId.canonicalKey(),
                    "Resized zone " + zoneId);
        }
        return new ZoneReceipt(
                ZoneChangeKind.ZONE_RESIZED, after, !after.equals(before), now()
        );
    }

    @Override
    public ZoneReceipt setZoneKind(UUID actor, ZoneId zoneId, ZoneKind newKind) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(newKind, "newKind");
        requireResolvableActor(actor);
        Zone before = repository.requireZone(zoneId);
        verifyKindCapabilities(newKind, before.capabilitySet());
        Zone after = repository.setZoneKind(zoneId, newKind);
        if (!after.equals(before)) {
            contexts.invalidateByZone(zoneId);
            audit(actor, "zone.set-kind", "zone",
                    zoneId.canonicalKey(),
                    "Set zone " + zoneId + " kind to " + newKind);
        }
        return new ZoneReceipt(
                ZoneChangeKind.ZONE_KIND_CHANGED, after, !after.equals(before), now()
        );
    }

    @Override
    public ZoneReceipt suspendZone(UUID actor, ZoneId zoneId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(zoneId, "zoneId");
        requireResolvableActor(actor);
        Zone before = repository.requireZone(zoneId);
        Zone after = repository.suspendZone(zoneId);
        if (!after.equals(before)) {
            contexts.invalidateByZone(zoneId);
            audit(actor, "zone.suspend", "zone",
                    zoneId.canonicalKey(),
                    "Suspended zone " + zoneId);
        }
        return new ZoneReceipt(
                ZoneChangeKind.ZONE_SUSPENDED, after, !after.equals(before), now()
        );
    }

    @Override
    public ZoneReceipt activateZone(UUID actor, ZoneId zoneId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(zoneId, "zoneId");
        requireResolvableActor(actor);
        Zone before = repository.requireZone(zoneId);
        Zone after = repository.activateZone(zoneId);
        if (!after.equals(before)) {
            audit(actor, "zone.activate", "zone",
                    zoneId.canonicalKey(),
                    "Activated zone " + zoneId);
        }
        return new ZoneReceipt(
                ZoneChangeKind.ZONE_ACTIVATED, after, !after.equals(before), now()
        );
    }

    // ------------------------------------------------------------------
    // on-site contexts
    // ------------------------------------------------------------------

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
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(playerDimension, "playerDimension");
        requireResolvableActor(playerId);

        Zone zone = repository.requireZone(zoneId);
        if (zone.state() != ZoneState.ACTIVE) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_ZONE_NOT_ACTIVE,
                    "Zone " + zoneId + " is not ACTIVE"
            );
        }
        Facility facility = repository.requireFacility(zone.facilityId());
        if (facility.state() != FacilityState.ACTIVE) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_FACILITY_NOT_ACTIVE,
                    "Facility " + facility.facilityId() + " is not ACTIVE"
            );
        }
        if (facility.institutionType() != zone.institutionType()) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_INSTITUTION_MISMATCH,
                    "Zone " + zoneId + " institution type does not match "
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
        if (!zone.capabilitySet().contains(capability)) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_CAPABILITY_NOT_ALLOWED,
                    "Zone " + zoneId + " does not allow " + capability
            );
        }
        if (!zone.kind().allowedCapabilities().contains(capability)) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_KIND_CAPABILITY_MISMATCH,
                    "Capability " + capability + " does not match the "
                            + zone.kind() + " zone kind of " + zoneId
            );
        }
        if (!zone.region().dimension().equals(playerDimension)) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_DIMENSION_MISMATCH,
                    "Player dimension " + playerDimension
                            + " does not match zone dimension "
                            + zone.region().dimension()
            );
        }
        LandParcel parcel = requireParcel(facility.parcelId());
        requireZoneInsideRegion(parcel, zone);

        WorkflowKind workflow = workflowFor(zone.kind(), capability);
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
        verifyPresenceInZone(zone, playerDimension, x, y, z);

        OnSiteContext context = new OnSiteContext(
                UUID.randomUUID(),
                playerId,
                facility.institutionType(),
                facility.facilityId(),
                zoneId,
                workflow,
                capability,
                now,
                expiry,
                facility.facilityRevision(),
                zone.zoneRevision(),
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
        if (facility == null || facility.state() != FacilityState.ACTIVE) {
            return ValidationResult.invalid(ValidationResult.REASON_FACILITY_NOT_ACTIVE);
        }
        if (facility.facilityRevision() != context.facilityRevision()) {
            return ValidationResult.invalid(ValidationResult.REASON_FACILITY_REVISION);
        }
        Zone zone = repository.findByZoneId(context.zoneId()).orElse(null);
        if (zone == null || zone.state() != ZoneState.ACTIVE) {
            return ValidationResult.invalid(ValidationResult.REASON_ZONE_NOT_ACTIVE);
        }
        if (zone.zoneRevision() != context.zoneRevision()) {
            return ValidationResult.invalid(ValidationResult.REASON_ZONE_REVISION);
        }
        if (!zone.region().dimension().equals(dimension)) {
            return ValidationResult.invalid(ValidationResult.REASON_DIMENSION_MISMATCH);
        }
        if (!zone.capabilitySet().contains(context.capability())) {
            return ValidationResult.invalid(ValidationResult.REASON_CAPABILITY_MISMATCH);
        }

        LandParcel parcel;
        try {
            parcel = landService.getParcel(facility.parcelId()).orElse(null);
        } catch (RuntimeException failure) {
            parcel = null;
        }
        if (parcel == null) {
            return ValidationResult.invalid(ValidationResult.REASON_ZONE_NOT_IN_REGION);
        }
        if (!zone.region().inside(parcel)) {
            return ValidationResult.invalid(ValidationResult.REASON_ZONE_NOT_IN_REGION);
        }
        if (!presenceOk(zone, dimension, x, y, z)) {
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
    public Optional<Zone> getZone(ZoneId zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");
        return repository.findByZoneId(zoneId);
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
     * false (and invalidates the context) when the player left the zone,
     * changed dimension, expired, or hit an official idle/hard limit. Never
     * consumes single-use authorizations — only the final mutation boundary
     * does.
     */
    public boolean evaluatePresence(
            OnSiteContext context,
            String dimension,
            int x,
            int y,
            int z,
            long now
    ) {
        if (!contexts.isActive(context.contextId())) {
            return false;
        }
        if (now > context.expiryTime()) {
            contexts.invalidate(context.contextId());
            return false;
        }
        Facility facility = repository.findByFacilityId(context.facilityId()).orElse(null);
        Zone zone = repository.findByZoneId(context.zoneId()).orElse(null);
        if (facility == null || zone == null) {
            contexts.invalidate(context.contextId());
            return false;
        }
        LandParcel parcel = safeParcel(facility.parcelId());
        if (parcel == null) {
            contexts.invalidate(context.contextId());
            return false;
        }
        if (!zone.region().inside(parcel)) {
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
        if (!presenceOk(zone, dimension, x, y, z)) {
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

    private void verifyZoneRegion(Facility facility, ZoneRegion region) {
        LandParcel parcel = requireParcel(facility.parcelId());
        if (!region.dimension().equals(parcel.dimension())) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_ZONE_OUTSIDE_REGION,
                    "Zone dimension " + region.dimension()
                            + " does not match facility parcel dimension "
                            + parcel.dimension()
            );
        }
        if (!region.inside(parcel)) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_ZONE_OUTSIDE_REGION,
                    "Zone region is outside the parcel region of facility "
                            + facility.facilityId()
            );
        }
        if (region.sizeX() > config.maxZoneXSize()
                || region.sizeY() > config.maxZoneYSize()
                || region.sizeZ() > config.maxZoneZSize()) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_ZONE_SIZE_EXCEEDED,
                    "Zone region exceeds the small-size budget "
                            + "(max " + config.maxZoneXSize() + "x"
                            + config.maxZoneYSize() + "x" + config.maxZoneZSize()
                            + "): " + region.sizeX() + "x" + region.sizeY()
                            + "x" + region.sizeZ()
            );
        }
    }

    private void verifyKindCapabilities(
            ZoneKind kind,
            Set<CapabilityClass> capabilitySet
    ) {
        if (!kind.allowedCapabilities().containsAll(capabilitySet)) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_KIND_CAPABILITY_MISMATCH,
                    "Capability set " + capabilitySet + " is not a subset of "
                            + "the " + kind + " zone kind's allowed classes "
                            + kind.allowedCapabilities()
            );
        }
    }

    private void requireZoneInsideRegion(LandParcel parcel, Zone zone) {
        if (!zone.region().inside(parcel)) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_ZONE_OUTSIDE_REGION,
                    "Zone " + zone.zoneId()
                            + " is no longer inside its facility's parcel region"
            );
        }
    }

    private void verifyPresenceInZone(
            Zone zone,
            String playerDimension,
            int x,
            int y,
            int z
    ) {
        if (!presenceOk(zone, playerDimension, x, y, z)) {
            throw unavailable(
                    InstitutionAccessUnavailableException.CODE_PLAYER_OUT_OF_RANGE,
                    "Player is not inside the region of zone " + zone.zoneId()
            );
        }
    }

    /** On-site presence is zone containment: same dimension and inside the
     *  bounded zone region. */
    private boolean presenceOk(Zone zone, String dimension, int x, int y, int z) {
        return zone.region().dimension().equals(dimension)
                && zone.region().contains(x, y, z);
    }

    private WorkflowKind workflowFor(ZoneKind kind, CapabilityClass capability) {
        return switch (kind) {
            case PUBLIC -> WorkflowKind.PUBLIC;
            case OFFICIAL -> WorkflowKind.OFFICIAL_ROUTINE;
            case SECURE -> WorkflowKind.HIGH_RISK;
        };
    }

    private long lifetimeMillis(WorkflowKind workflow) {
        return switch (workflow) {
            case PUBLIC -> config.publicContextLifetimeMillis();
            case OFFICIAL_ROUTINE -> config.officialHardLimitMillis();
            case HIGH_RISK -> config.highRiskLifetimeMillis();
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
