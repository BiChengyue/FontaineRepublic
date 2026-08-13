package com.fontainerepublic.server.government.service;

import com.fontainerepublic.server.audit.api.AuditDraft;
import com.fontainerepublic.server.audit.api.AuditReceipt;
import com.fontainerepublic.server.audit.api.AuditService;
import com.fontainerepublic.server.audit.model.AuditActorType;
import com.fontainerepublic.server.audit.model.AuditCategory;
import com.fontainerepublic.server.audit.model.AuditClassification;
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
import com.fontainerepublic.server.government.model.Office;
import com.fontainerepublic.server.government.model.PositionId;
import com.fontainerepublic.server.government.model.PositionState;
import com.fontainerepublic.server.government.persistence.GovernmentRepository;
import com.fontainerepublic.server.government.persistence.GovernmentUnavailableException;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.api.ValidationResult;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.OwnerReferenceKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Runtime implementation of {@link GovernmentService} (FR-GOV-001-A §4).
 *
 * <p>The service is a thin, owner-thread-scoped facade over the single-writer
 * {@link GovernmentRepository}. It supplies the server clock, resolves actors
 * and holders through the PlayerData + FR-ID chain, and enforces the
 * {@code ONSITE_OFFICIAL_DUTY} final mutation boundary for appoint/dismiss
 * through {@link InstitutionAccessService#validateAtMutation} — a
 * non-VALID context rejects the mutation fail-closed. Authoritative mutations
 * publish only after the durable gate commits and are then recorded through
 * the audit service (audit failure never blocks an already-committed
 * mutation). Reads are exact or bounded projections only.</p>
 */
public final class DefaultGovernmentService implements GovernmentService {

    /** Bounded dismissal reason length. */
    public static final int MAX_REASON_LENGTH = 128;

    private final GovernmentRepository repository;
    private final LongSupplier clock;
    private final HolderDirectory holders;
    private final InstitutionAccessService institutionAccess;
    private final AuditService auditService;

    public DefaultGovernmentService(
            GovernmentRepository repository,
            LongSupplier clock,
            HolderDirectory holders,
            InstitutionAccessService institutionAccess,
            AuditService auditService
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.holders = Objects.requireNonNull(holders, "holders");
        this.institutionAccess =
                Objects.requireNonNull(institutionAccess, "institutionAccess");
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // authoritative mutations
    // ------------------------------------------------------------------

    @Override
    public MinistryReceipt createMinistry(UUID actor, MinistryDraft draft) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(draft, "draft");
        requireResolvableActor(actor);
        Ministry ministry = repository.createMinistry(draft.name());
        audit(
                actor,
                "ministry.create",
                "ministry",
                ministry.ministryId().canonicalKey(),
                "Created ministry " + ministry.ministryId() + " (" + ministry.name() + ")"
        );
        return new MinistryReceipt(
                MinistryChangeKind.MINISTRY_CREATED,
                ministry,
                true,
                now()
        );
    }

    @Override
    public PositionReceipt createPosition(UUID actor, CreatePositionRequest request) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(request, "request");
        requireResolvableActor(actor);
        repository.requireMinistry(request.ministryId());
        GovernmentPosition position = repository.createPosition(
                request.ministryId(),
                request.title()
        );
        audit(
                actor,
                "position.create",
                "position",
                position.positionId().canonicalKey(),
                "Created position " + position.positionId() + " (" + position.title()
                        + ") under ministry " + request.ministryId()
        );
        return new PositionReceipt(
                PositionChangeKind.POSITION_CREATED,
                position,
                true,
                now()
        );
    }

    @Override
    public AppointmentReceipt appoint(
            UUID actor,
            PositionId positionId,
            OwnerReference holder,
            OnSiteContext context
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(positionId, "positionId");
        Objects.requireNonNull(holder, "holder");
        requireResolvableActor(actor);
        requireSupportedHolderKind(holder);
        requireResolvableHolder(holder);
        GovernmentPosition current = repository.requirePosition(positionId);
        if (current.state() == PositionState.SUSPENDED) {
            throw unavailable(
                    GovernmentUnavailableException.CODE_POSITION_SUSPENDED,
                    "Position " + positionId + " is suspended"
            );
        }
        if (current.state() == PositionState.FILLED) {
            throw unavailable(
                    GovernmentUnavailableException.CODE_POSITION_FILLED,
                    "Position " + positionId + " is already filled"
            );
        }
        requireValidOnSite(context);

        Office office = repository.appoint(
                positionId,
                holder,
                UUID.randomUUID(),
                now()
        );
        GovernmentPosition filled = repository.requirePosition(positionId);
        audit(
                actor,
                "position.appoint",
                "position",
                positionId.canonicalKey(),
                "Appointed " + holder.key() + " to position " + positionId
        );
        return new AppointmentReceipt(
                AppointmentChangeKind.APPOINTED,
                filled,
                Optional.of(office),
                true,
                now()
        );
    }

    @Override
    public AppointmentReceipt dismiss(
            UUID actor,
            PositionId positionId,
            String reason,
            OnSiteContext context
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(positionId, "positionId");
        requireResolvableActor(actor);
        requireBoundedReason(reason);
        GovernmentPosition current = repository.requirePosition(positionId);
        if (current.state() == PositionState.SUSPENDED) {
            throw unavailable(
                    GovernmentUnavailableException.CODE_POSITION_SUSPENDED,
                    "Position " + positionId + " is suspended"
            );
        }
        if (current.state() != PositionState.FILLED) {
            // Dismissing an already-VACANT position is an idempotent no-op:
            // nothing is committed, no on-site context is consumed.
            return new AppointmentReceipt(
                    AppointmentChangeKind.DISMISSED,
                    current,
                    repository.findOfficeByPosition(positionId),
                    false,
                    now()
            );
        }
        requireValidOnSite(context);

        Office revoked = repository.dismiss(positionId, now());
        GovernmentPosition vacated = repository.requirePosition(positionId);
        audit(
                actor,
                "position.dismiss",
                "position",
                positionId.canonicalKey(),
                "Dismissed holder " + current.holderRef()
                        .map(OwnerReference::key).orElse("<none>")
                        + " from position " + positionId + " (" + reason + ")"
        );
        return new AppointmentReceipt(
                AppointmentChangeKind.DISMISSED,
                vacated,
                Optional.of(revoked),
                true,
                now()
        );
    }

    // ------------------------------------------------------------------
    // exact reads (bounded; no enumeration API)
    // ------------------------------------------------------------------

    @Override
    public Optional<Ministry> getMinistry(MinistryId ministryId) {
        Objects.requireNonNull(ministryId, "ministryId");
        return repository.findMinistry(ministryId);
    }

    @Override
    public Optional<GovernmentPosition> getPosition(PositionId positionId) {
        Objects.requireNonNull(positionId, "positionId");
        return repository.findPosition(positionId);
    }

    @Override
    public Optional<Office> currentOffice(PositionId positionId) {
        Objects.requireNonNull(positionId, "positionId");
        return repository.findOfficeByPosition(positionId)
                .filter(Office::current);
    }

    @Override
    public List<MinistryProjection> ministries() {
        return repository.snapshot().ministries().values().stream()
                .sorted(Comparator.comparing(ministry ->
                        ministry.ministryId().canonicalKey()))
                .limit(MAX_PROJECTION_SIZE)
                .map(ministry -> new MinistryProjection(
                        ministry.ministryId(),
                        ministry.name(),
                        ministry.state(),
                        ministry.ministryRevision()
                ))
                .toList();
    }

    @Override
    public List<PositionProjection> positionsByMinistry(MinistryId ministryId) {
        Objects.requireNonNull(ministryId, "ministryId");
        return repository.snapshot().positions().values().stream()
                .filter(position -> position.ministryId().equals(ministryId))
                .sorted(Comparator.comparing(position ->
                        position.positionId().canonicalKey()))
                .limit(MAX_PROJECTION_SIZE)
                .map(position -> new PositionProjection(
                        position.positionId(),
                        position.title(),
                        position.state(),
                        position.holderRef()
                ))
                .toList();
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private void requireResolvableActor(UUID actor) {
        if (!holders.isAvailable()) {
            throw unavailable(
                    GovernmentUnavailableException.CODE_HOLDER_DIRECTORY_UNAVAILABLE,
                    "Player/subject services are not available at the mutation boundary"
            );
        }
        if (!holders.hasPlayerRecord(actor)) {
            throw unavailable(
                    GovernmentUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    "Player UUID has no authoritative PlayerData record: " + actor
            );
        }
        if (!holders.hasActiveSubject(actor)) {
            throw unavailable(
                    GovernmentUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    "Player UUID has no active subject: " + actor
            );
        }
    }

    private void requireSupportedHolderKind(OwnerReference holder) {
        if (holder.kind() != OwnerReferenceKind.PLAYER_UUID) {
            throw unavailable(
                    GovernmentUnavailableException.CODE_UNSUPPORTED_HOLDER_KIND,
                    "Alpha appointments accept only PLAYER_UUID holders; got "
                            + holder.kind()
            );
        }
    }

    private void requireResolvableHolder(OwnerReference holder) {
        if (!holders.isAvailable()) {
            throw unavailable(
                    GovernmentUnavailableException.CODE_HOLDER_DIRECTORY_UNAVAILABLE,
                    "Holder directory is not available at the mutation boundary"
            );
        }
        UUID holderId = UUID.fromString(holder.ownerId());
        if (!holders.hasPlayerRecord(holderId) || !holders.hasActiveSubject(holderId)) {
            throw unavailable(
                    GovernmentUnavailableException.CODE_INVALID_HOLDER,
                    "Holder " + holder.key() + " is not resolvable to an active subject"
            );
        }
    }

    private void requireBoundedReason(String reason) {
        Objects.requireNonNull(reason, "reason");
        String normalized = reason.trim();
        if (normalized.isEmpty()) {
            throw unavailable(
                    GovernmentUnavailableException.CODE_INVALID_REQUEST,
                    "dismissal reason must not be blank"
            );
        }
        if (normalized.length() > MAX_REASON_LENGTH) {
            throw unavailable(
                    GovernmentUnavailableException.CODE_INVALID_REQUEST,
                    "dismissal reason exceeds bound of " + MAX_REASON_LENGTH
                            + " characters"
            );
        }
    }

    /**
     * Final mutation boundary of every official duty (FR-GOV-001-A §2/§4):
     * the on-site context must revalidate as {@code ONSITE_OFFICIAL_DUTY} at
     * the authoritative position bound to the context. A null context and any
     * non-VALID outcome reject the mutation fail-closed with a stable code.
     */
    private void requireValidOnSite(OnSiteContext context) {
        ValidationResult result;
        if (context == null) {
            result = ValidationResult.invalid(ValidationResult.REASON_NOT_ISSUED);
        } else {
            result = institutionAccess.validateAtMutation(
                    context,
                    CapabilityClass.ONSITE_OFFICIAL_DUTY,
                    now(),
                    context.dimension(),
                    context.blockX(),
                    context.blockY(),
                    context.blockZ()
            );
        }
        if (!result.valid()) {
            throw unavailable(
                    GovernmentUnavailableException.CODE_ON_SITE_CONTEXT_INVALID,
                    "On-site official-duty context is not valid: " + result.reason()
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
                    AuditCategory.GOVERNANCE,
                    "government",
                    actionId,
                    Optional.of(targetType),
                    Optional.of(targetId),
                    AuditClassification.PUBLIC,
                    summary,
                    Optional.empty()
            );
            AuditReceipt receipt = auditService.recordAuthoritative(draft);
            if (!receipt.committed()) {
                LoggerFactory.getLogger(DefaultGovernmentService.class).warn(
                        "[Government] Audit of {} was not durably committed: {}",
                        actionId,
                        receipt.failureCode()
                );
            }
        } catch (RuntimeException failure) {
            // Audit failure never blocks an already-committed mutation (audit
            // is a record, not an authority).
            LoggerFactory.getLogger(DefaultGovernmentService.class).warn(
                    "[Government] Audit recording failed for {}: {}",
                    actionId,
                    failure.getMessage()
            );
        }
    }

    private GovernmentUnavailableException unavailable(String code, String message) {
        return new GovernmentUnavailableException(code, message);
    }

    private long now() {
        long value = clock.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("clock returned a negative timestamp");
        }
        return value;
    }
}
