package com.fontainerepublic.server.land.api;

import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.land.model.ParcelRegion;
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
     * Exact bounded point lookup of the parcel covering a block position in a
     * dimension (FR-LAND-CLAIM-001-A §3.1). {@code dimension} must be a
     * canonical resource key; a position not part of any parcel yields
     * {@link Optional#empty()}. Deliberately a single-value bounded query,
     * never a bulk enumeration or another player's detail leak.
     */
    Optional<LandParcel> parcelAt(String dimension, int x, int y, int z);

    /**
     * Bounded, non-enumerating full-region overlap test (FR-LAND-CLAIM-001-FIX
     * F2): whether any existing parcel in {@code dimension} shares any block
     * volume with the proposed {@code region}, using the same inclusive
     * axis-aligned intersection semantics as the mutation ({@link
     * #createParcelWithUsage}). {@code dimension} must be a canonical resource
     * key and {@code region} the already-clamped proposed parcel region. This
     * is a read-only presentation-time probe — never authoritative — so
     * {@link #createParcelWithUsage} still performs the final overlap check
     * inside its single owner-thread mutation. Exposes no parcel identity or
     * detail, only a boolean.
     */
    boolean overlaps(String dimension, ParcelRegion region);

    /**
     * Authoritative atomic creation of a republic-owned parcel together with
     * an initial usage right for {@code holder} in one complete replacement
     * snapshot (FR-LAND-CLAIM-001-A §3.2). This single gated operation is what
     * a land claim uses — unlike two independent durable commits
     * (createParcel + grantUsage), it never leaves a parcel without its
     * initial right. On success the parcel and right start at revision 1 and
     * the store revision +1 exactly once, and the right is granted without
     * expiry when {@code durationMillis == 0}. On any validation, capacity,
     * same-dimension region overlap, or durable-commit failure nothing is
     * published and the call can be safely retried.
     *
     * @throws com.fontainerepublic.server.land.persistence.LandUnavailableException
     *         with a stable code when the actor or holder is not resolvable to
     *         an active subject, the holder kind is unsupported, the region
     *         overlaps an existing same-dimension parcel
     *         ({@code CODE_OVERLAP}), capacity is exhausted, the request is
     *         invalid, or the durable store rejected the snapshot
     */
    UsageReceipt createParcelWithUsage(
            UUID actor,
            CreateParcelRequest request,
            OwnerReference holder,
            long durationMillis
    );

    /**
     * Single bounded read-only aggregate of the republic's land parcels
     * (FR-CLIENT-001-IMPL-B3b): one immutable {@link LandSummary} value —
     * parcel count, total area, per-zone distribution and store revision.
     * Deliberately <b>not</b> a parcel list: the land module exposes no bulk
     * enumeration API, and this overview never leaks any parcel identity,
     * holder, region, or access detail. Display-only; the client summary is
     * assembled from it at login.
     */
    LandSummary publicSummary();

    /**
     * Self-only bounded page of the authenticated caller's own <em>current</em>
     * usage rights (FR-LAND-002-A §3.2). {@code authenticatedPlayerId} must
     * come only from the server connection or a player command source; the
     * service converts it to {@code OwnerReference.forPlayer(...)} and enforces
     * the authoritative PlayerData/subject resolution chain, failing closed
     * when unavailable. There is <b>no target/holder parameter</b>, so no
     * other player's rights can ever be queried.
     *
     * <p>{@code afterParcelId} is an exclusive cursor by
     * {@code ParcelId.canonicalKey()} ascending order; a first page uses
     * {@code expectedStoreRevision == 0}. Only {@code UsageRight.validAt(now)}
     * entries are returned. {@code limit} must be in {@code [1,
     * MyUsageRightsQueryLimits.MAX_LIMIT]} — out-of-range inputs are rejected
     * ({@link MyUsageRightsStatus#INVALID_REQUEST}), never silently clamped.
     * Revision drift returns a {@link MyUsageRightsStatus#RESET_REQUIRED} page
     * with no entries. Read-only: never persists, never increments a revision,
     * never writes an audit transaction.</p>
     *
     * <p>The returned {@link MyUsageRightsPage} is the single explicitly allowed
     * person-scoped page record (never a raw collection) and carries projection
     * fields only for the caller's own current rights.</p>
     *
     * @throws com.fontainerepublic.server.land.persistence.LandUnavailableException
     *         with a stable code when the holder cannot be resolved or the
     *         request is invalid (mapped to {@link MyUsageRightsStatus#UNAVAILABLE}
     *         / {@link MyUsageRightsStatus#INVALID_REQUEST} at the transport)
     */
    MyUsageRightsPage myUsageRights(
            UUID authenticatedPlayerId,
            Optional<ParcelId> afterParcelId,
            long expectedStoreRevision,
            int limit);

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
