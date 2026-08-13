package com.fontainerepublic.server.land.api;

import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.land.model.ViolationReport;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.land.model.ZoneType;
import com.fontainerepublic.server.land.model.LandAccess;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative public API of the land module (FR-LAND-001-A §4).
 *
 * <p>The service owns republic land parcels, usage rights, planning
 * designation, access policy, and violation-report entry. It exposes no
 * ownership transfer/sale/lease API (ownership is permanently REPUBLIC), no
 * bulk parcel list, no repositories, no NBT, no balance, citizenship,
 * judicial, or technical-permission fields, and no rank-to-permission
 * mapping. All mutations run on the logical server owner thread and publish
 * only after the FR-CORE-002 durable gate reports {@code COMMITTED}.</p>
 *
 * <p>Event-time build/break/interact decisions go through the config-driven
 * {@link PermissionResolver} (see {@link #canBuild}/{@link #canBreak}/
 * {@link #canInteract}); rank/OP never bypass it and unknown parcels fail
 * closed.</p>
 */
public interface LandService {

    /**
     * Authoritative creation of a republic-owned parcel: server-assigned
     * immutable id, one complete replacement snapshot, published only after
     * the durable gate commits. The actor must be a provisioned player.
     *
     * @throws com.fontainerepublic.server.land.persistence.LandUnavailableException
     *         with a stable code when the actor is not provisioned, the
     *         request is invalid, capacity is exhausted, or the durable store
     *         rejected the snapshot
     */
    LandReceipt createParcel(UUID actor, CreateParcelRequest request);

    /**
     * Authoritative grant of a usage right to a holder: one complete
     * replacement snapshot, right/parcel/store revision +1 exactly once,
     * published only after the durable gate commits. Alpha accepts only
     * {@code PLAYER_UUID} holders. {@code durationMillis} of {@code 0} grants
     * without expiry; positive values expire {@code durationMillis} from now.
     *
     * @throws com.fontainerepublic.server.land.persistence.LandUnavailableException
     *         when the actor or holder is not resolvable to an active subject,
     *         the holder kind is unsupported, the holder already holds a
     *         right, the parcel is unknown, capacity is exhausted, or the
     *         durable store rejected the snapshot
     */
    UsageReceipt grantUsage(UUID actor, ParcelId parcelId, OwnerReference holder, long durationMillis);

    /**
     * Authoritative renewal of a holder's usage right: the expiry is replaced
     * from the renewal time and the right revision +1 exactly once.
     *
     * @throws com.fontainerepublic.server.land.persistence.LandUnavailableException
     *         when the actor or holder is not resolvable, the holder holds no
     *         right, the parcel is unknown, or the durable store rejected the
     *         snapshot
     */
    UsageReceipt renewUsage(UUID actor, ParcelId parcelId, OwnerReference holder, long durationMillis);

    /**
     * Authoritative revocation of a holder's usage right: parcel and store
     * revision +1 exactly once. Revoking a right the holder does not hold is
     * an idempotent no-op ({@code applied == false}, nothing committed).
     *
     * @throws com.fontainerepublic.server.land.persistence.LandUnavailableException
     *         when the actor is not resolvable, the parcel is unknown, or the
     *         durable store rejected the snapshot
     */
    UsageReceipt revokeUsage(UUID actor, ParcelId parcelId, OwnerReference holder);

    /**
     * Authoritative zone-type replacement: one complete replacement snapshot,
     * parcel and store revision +1 exactly once. Same-zone requests are
     * idempotent no-ops.
     *
     * @throws com.fontainerepublic.server.land.persistence.LandUnavailableException
     *         when the actor is not resolvable, the parcel is unknown, or the
     *         durable store rejected the snapshot
     */
    LandReceipt setZoneType(UUID actor, ParcelId parcelId, ZoneType zone);

    /**
     * Authoritative access-policy replacement: one complete replacement
     * snapshot, parcel and store revision +1 exactly once. Same-access
     * requests are idempotent no-ops.
     *
     * @throws com.fontainerepublic.server.land.persistence.LandUnavailableException
     *         when the actor is not resolvable, the parcel is unknown, or the
     *         durable store rejected the snapshot
     */
    LandReceipt setAccess(UUID actor, ParcelId parcelId, LandAccess access);

    /**
     * Authoritative creation of a read-only violation report against an
     * existing parcel: one complete replacement snapshot, published only
     * after the durable gate commits. The reporter must be resolvable to an
     * active subject.
     *
     * @throws com.fontainerepublic.server.land.persistence.LandUnavailableException
     *         when the reporter is not resolvable, the parcel is unknown, the
     *         description is invalid, capacity is exhausted, or the durable
     *         store rejected the snapshot
     */
    ViolationReceipt createViolationReport(ViolationDraft draft);

    /** Exact lookup: the parcel with the given server-assigned id, if any. */
    Optional<LandParcel> getParcel(ParcelId parcelId);

    /**
     * Config-driven event-time decision: may the player build on the parcel?
     * Resolved at event time; a cached decision is never authoritative.
     * Unknown parcels and unavailable services fail closed ({@code false}).
     */
    boolean canBuild(UUID player, ParcelId parcelId);

    /** Config-driven event-time decision: may the player break on the parcel? */
    boolean canBreak(UUID player, ParcelId parcelId);

    /** Config-driven event-time decision: may the player interact on the parcel? */
    boolean canInteract(UUID player, ParcelId parcelId);
}
