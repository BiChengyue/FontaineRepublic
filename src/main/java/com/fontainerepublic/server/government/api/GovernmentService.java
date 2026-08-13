package com.fontainerepublic.server.government.api;

import com.fontainerepublic.server.government.model.GovernmentPosition;
import com.fontainerepublic.server.government.model.Ministry;
import com.fontainerepublic.server.government.model.MinistryId;
import com.fontainerepublic.server.government.model.Office;
import com.fontainerepublic.server.government.model.PositionId;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.registry.model.OwnerReference;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative public API of the government module (FR-GOV-001-A §4).
 *
 * <p>The service owns ministries, government positions, and offices
 * (appointment state). It exposes no bulk enumeration API — reads are exact
 * lookups or bounded projections only — no repositories, no NBT, no
 * legislative/judicial/fiscal authority, and no rank/office-to-permission
 * mapping. All mutations run on the logical server owner thread and publish
 * only after the FR-CORE-002 durable gate reports {@code COMMITTED}.</p>
 *
 * <p>{@link #appoint} and {@link #dismiss} are {@code ONSITE_OFFICIAL_DUTY}:
 * the final mutation boundary revalidates the on-site context through
 * {@code InstitutionAccessService#validateAtMutation(context, ONSITE_OFFICIAL_DUTY, ...)}
 * and rejects the mutation fail-closed when the context is not VALID.
 * Holders are resolved through the PlayerData and FR-ID subject services —
 * a game name is never stored.</p>
 */
public interface GovernmentService {

    // ------------------------------------------------------------------
    // authoritative mutations (single snapshot, durable gate)
    // ------------------------------------------------------------------

    /**
     * Authoritative ministry creation: one complete replacement snapshot,
     * store revision +1 exactly once, published only after the durable gate
     * commits. The actor must be a provisioned player with an active subject.
     *
     * @throws com.fontainerepublic.server.government.persistence.GovernmentUnavailableException
     *         with a stable code when the actor is not resolvable, the draft
     *         is invalid, capacity is exhausted, or the durable store
     *         rejected the snapshot
     */
    MinistryReceipt createMinistry(UUID actor, MinistryDraft draft);

    /**
     * Authoritative position creation bound to an existing ministry: one
     * complete replacement snapshot, store revision +1 exactly once,
     * published only after the durable gate commits. The actor must be a
     * provisioned player with an active subject.
     *
     * @throws com.fontainerepublic.server.government.persistence.GovernmentUnavailableException
     *         with a stable code when the actor is not resolvable, the
     *         ministry does not exist, the request is invalid, capacity is
     *         exhausted, or the durable store rejected the snapshot
     */
    PositionReceipt createPosition(UUID actor, CreatePositionRequest request);

    /**
     * Authoritative appointment of a holder to a VACANT position
     * ({@code ONSITE_OFFICIAL_DUTY}): one complete replacement snapshot,
     * position/office/store revision +1 exactly once, published only after
     * the durable gate commits. Alpha accepts only {@code PLAYER_UUID}
     * holders resolvable to an active subject.
     *
     * @throws com.fontainerepublic.server.government.persistence.GovernmentUnavailableException
     *         with a stable code when the actor or holder is not resolvable,
     *         the holder kind is unsupported, the position is unknown,
     *         already filled, or suspended, the on-site context is not
     *         VALID at the final mutation boundary, capacity is exhausted, or
     *         the durable store rejected the snapshot
     */
    AppointmentReceipt appoint(
            UUID actor,
            PositionId positionId,
            OwnerReference holder,
            OnSiteContext context
    );

    /**
     * Authoritative dismissal of the holder of a FILLED position
     * ({@code ONSITE_OFFICIAL_DUTY}): one complete replacement snapshot,
     * position/office/store revision +1 exactly once, published only after
     * the durable gate commits. Dismissing an already-VACANT position is an
     * idempotent no-op ({@code applied == false}, nothing committed).
     *
     * @throws com.fontainerepublic.server.government.persistence.GovernmentUnavailableException
     *         with a stable code when the actor is not resolvable, the
     *         position is unknown or suspended, the reason is invalid, the
     *         on-site context is not VALID at the final mutation boundary,
     *         capacity is exhausted, or the durable store rejected the
     *         snapshot
     */
    AppointmentReceipt dismiss(
            UUID actor,
            PositionId positionId,
            String reason,
            OnSiteContext context
    );

    // ------------------------------------------------------------------
    // exact reads (bounded; no enumeration API)
    // ------------------------------------------------------------------

    /** Exact lookup: the ministry with the given id, if any. */
    Optional<Ministry> getMinistry(MinistryId ministryId);

    /** Exact lookup: the position with the given id, if any. */
    Optional<GovernmentPosition> getPosition(PositionId positionId);

    /** Exact lookup: the current (not revoked) office of a position, if any. */
    Optional<Office> currentOffice(PositionId positionId);

    /**
     * Bounded projection of ministries (stable id order, at most
     * {@value #MAX_PROJECTION_SIZE} entries). Never an unbounded
     * enumeration.
     */
    List<MinistryProjection> ministries();

    /**
     * Bounded projection of the positions of one ministry (stable id order,
     * at most {@value #MAX_PROJECTION_SIZE} entries). Never an unbounded
     * enumeration.
     */
    List<PositionProjection> positionsByMinistry(MinistryId ministryId);

    /** Hard cap of every bounded projection. */
    int MAX_PROJECTION_SIZE = 128;
}
