package com.fontainerepublic.server.land.service;

import com.fontainerepublic.server.land.api.CreateParcelRequest;
import com.fontainerepublic.server.land.api.HolderDirectory;
import com.fontainerepublic.server.land.api.LandChangeKind;
import com.fontainerepublic.server.land.api.LandReceipt;
import com.fontainerepublic.server.land.api.LandService;
import com.fontainerepublic.server.land.api.LandSummary;
import com.fontainerepublic.server.land.api.MyUsageRightsPage;
import com.fontainerepublic.server.land.api.MyUsageRightsQueryLimits;
import com.fontainerepublic.server.land.api.PermissionResolver;
import com.fontainerepublic.server.land.api.UsageChangeKind;
import com.fontainerepublic.server.land.api.UsageReceipt;
import com.fontainerepublic.server.land.api.ViolationDraft;
import com.fontainerepublic.server.land.api.ViolationReceipt;
import com.fontainerepublic.server.land.model.LandAccess;
import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.land.model.ParcelRegion;
import com.fontainerepublic.server.land.model.ViolationReport;
import com.fontainerepublic.server.land.model.ZoneType;
import com.fontainerepublic.server.land.persistence.LandRepository;
import com.fontainerepublic.server.land.persistence.LandStoreSnapshot;
import com.fontainerepublic.server.land.persistence.LandUnavailableException;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.OwnerReferenceKind;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.List;

/**
 * Runtime implementation of {@link LandService} (FR-LAND-001-A §4).
 *
 * <p>The service is a thin, owner-thread-scoped facade over the single-writer
 * {@link LandRepository}; it supplies the server clock, the holder/actor
 * resolution chain (PlayerData + FR-ID subject), and the config-driven
 * {@link PermissionResolver}. All mutations publish only after the durable
 * gate commits. Ownership is permanently REPUBLIC — there is no transfer API
 * and none is implemented.</p>
 */
public final class DefaultLandService implements LandService {

    private final LandRepository repository;
    private final LongSupplier clock;
    private final HolderDirectory holders;
    private final PermissionResolver resolver;

    public DefaultLandService(
            LandRepository repository,
            LongSupplier clock,
            HolderDirectory holders,
            PermissionResolver resolver
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.holders = Objects.requireNonNull(holders, "holders");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @Override
    public LandReceipt createParcel(UUID actor, CreateParcelRequest request) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(request, "request");
        requireResolvableHolder(actor);
        LandParcel parcel = repository.createParcel(
                request.dimension(),
                request.region(),
                request.zoneType(),
                request.access(),
                now()
        );
        return new LandReceipt(LandChangeKind.PARCEL_CREATED, parcel, true, now());
    }

    @Override
    public UsageReceipt grantUsage(
            UUID actor,
            ParcelId parcelId,
            OwnerReference holder,
            long durationMillis
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(holder, "holder");
        requireResolvableHolder(actor);
        requirePlayerHolder(holder);
        requireResolvableHolder(holderUuid(holder));
        long grantedAt = now();
        long expiresAt = resolveExpiry(grantedAt, durationMillis);
        LandParcel parcel = repository.grantUsage(parcelId, holder, grantedAt, expiresAt);
        return new UsageReceipt(
                UsageChangeKind.GRANTED, parcel, holder, true, now()
        );
    }

    @Override
    public UsageReceipt renewUsage(
            UUID actor,
            ParcelId parcelId,
            OwnerReference holder,
            long durationMillis
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(holder, "holder");
        requireResolvableHolder(actor);
        requirePlayerHolder(holder);
        requireResolvableHolder(holderUuid(holder));
        long expiresAt = resolveExpiry(now(), durationMillis);
        LandParcel parcel = repository.renewUsage(parcelId, holder, expiresAt);
        return new UsageReceipt(
                UsageChangeKind.RENEWED, parcel, holder, true, now()
        );
    }

    @Override
    public UsageReceipt revokeUsage(UUID actor, ParcelId parcelId, OwnerReference holder) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(holder, "holder");
        requireResolvableHolder(actor);
        LandParcel before = repository.requireParcel(parcelId);
        LandParcel after = repository.revokeUsage(parcelId, holder);
        return new UsageReceipt(
                UsageChangeKind.REVOKED,
                after,
                holder,
                !after.equals(before),
                now()
        );
    }

    @Override
    public LandReceipt setZoneType(UUID actor, ParcelId parcelId, ZoneType zone) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(zone, "zone");
        requireResolvableHolder(actor);
        LandParcel before = repository.requireParcel(parcelId);
        LandParcel after = repository.updateZoneType(parcelId, zone);
        return new LandReceipt(
                LandChangeKind.ZONE_TYPE, after, !after.equals(before), now()
        );
    }

    @Override
    public LandReceipt setAccess(UUID actor, ParcelId parcelId, LandAccess access) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(access, "access");
        requireResolvableHolder(actor);
        LandParcel before = repository.requireParcel(parcelId);
        LandParcel after = repository.updateAccess(parcelId, access);
        return new LandReceipt(
                LandChangeKind.ACCESS, after, !after.equals(before), now()
        );
    }

    @Override
    public ViolationReceipt createViolationReport(ViolationDraft draft) {
        Objects.requireNonNull(draft, "draft");
        requireParcelExists(draft.parcelId());
        requirePlayerHolder(draft.reporter());
        requireResolvableHolder(holderUuid(draft.reporter()));
        ViolationReport report = repository.createReport(
                draft.parcelId(),
                draft.reporter(),
                draft.description(),
                now()
        );
        return new ViolationReceipt(report, true, now());
    }

    @Override
    public Optional<LandParcel> getParcel(ParcelId parcelId) {
        Objects.requireNonNull(parcelId, "parcelId");
        return repository.findByParcelId(parcelId);
    }

    @Override
    public Optional<LandParcel> parcelAt(String dimension, int x, int y, int z) {
        Objects.requireNonNull(dimension, "dimension");
        return repository.parcelAt(dimension, x, y, z);
    }

    @Override
    public boolean overlaps(String dimension, ParcelRegion region) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(region, "region");
        return repository.regionOverlaps(dimension, region);
    }

    @Override
    public UsageReceipt createParcelWithUsage(
            UUID actor,
            CreateParcelRequest request,
            OwnerReference holder,
            long durationMillis
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(holder, "holder");
        requireResolvableHolder(actor);
        requirePlayerHolder(holder);
        requireResolvableHolder(holderUuid(holder));
        long grantedAt = now();
        long expiresAt = resolveExpiry(grantedAt, durationMillis);
        LandParcel parcel = repository.createParcelWithUsage(
                request.dimension(),
                request.region(),
                request.zoneType(),
                request.access(),
                holder,
                grantedAt,
                expiresAt
        );
        return new UsageReceipt(
                UsageChangeKind.GRANTED,
                parcel,
                holder,
                true,
                now()
        );
    }

    @Override
    public LandSummary publicSummary() {
        LandStoreSnapshot snapshot = repository.snapshot();
        java.util.EnumMap<ZoneType, long[]> byZone =
                new java.util.EnumMap<>(ZoneType.class);
        long totalArea = 0L;
        for (LandParcel parcel : snapshot.parcels().values()) {
            long area = areaOf(parcel.region());
            totalArea += area;
            long[] accumulated = byZone.computeIfAbsent(
                    parcel.zoneType(), zone -> new long[2]
            );
            accumulated[0] = accumulated[0] + 1;
            accumulated[1] = accumulated[1] + area;
        }
        List<LandSummary.ZoneSummary> zones = new java.util.ArrayList<>();
        for (ZoneType zone : ZoneType.values()) {
            long[] accumulated = byZone.get(zone);
            if (accumulated != null) {
                zones.add(new LandSummary.ZoneSummary(
                        zone,
                        Math.toIntExact(accumulated[0]),
                        accumulated[1]
                ));
            }
        }
        return new LandSummary(
                snapshot.parcels().size(),
                totalArea,
                zones,
                snapshot.storeRevision()
        );
    }

    @Override
    public MyUsageRightsPage myUsageRights(
            UUID authenticatedPlayerId,
            Optional<ParcelId> afterParcelId,
            long expectedStoreRevision,
            int limit
    ) {
        Objects.requireNonNull(authenticatedPlayerId, "authenticatedPlayerId");
        Objects.requireNonNull(afterParcelId, "afterParcelId");
        // Self-only: the caller is always the authenticated player; there is no
        // target-holder parameter, so no other player can be queried.
        requireResolvableHolder(authenticatedPlayerId);
        if (!MyUsageRightsQueryLimits.isValid(limit)) {
            throw new LandUnavailableException(
                    LandUnavailableException.CODE_INVALID_REQUEST,
                    "limit must be within [1, " + MyUsageRightsQueryLimits.MAX_LIMIT
                            + "]: " + limit
            );
        }
        // Cursor/revision pairing (FR-LAND-002-A §4.2): a first page has no
        // cursor and expectedStoreRevision == 0; a continuation has a cursor
        // and a positive store revision. Enforced here at the authoritative API
        // boundary so no non-network caller can bypass it.
        if (!MyUsageRightsQueryLimits.isValidCursorRevision(
                afterParcelId.isPresent(), expectedStoreRevision)) {
            throw new LandUnavailableException(
                    LandUnavailableException.CODE_INVALID_REQUEST,
                    "a cursor requires its returned positive store revision, and "
                            + "a first page must carry no cursor and revision 0"
            );
        }
        OwnerReference holder = OwnerReference.forPlayer(authenticatedPlayerId);
        return repository.myUsageRightsPage(
                holder,
                afterParcelId,
                expectedStoreRevision,
                limit,
                now()
        );
    }

    @Override
    public boolean canBuild(UUID player, ParcelId parcelId) {
        return resolver.canBuild(player, parcelId);
    }

    @Override
    public boolean canBreak(UUID player, ParcelId parcelId) {
        return resolver.canBreak(player, parcelId);
    }

    @Override
    public boolean canInteract(UUID player, ParcelId parcelId) {
        return resolver.canInteract(player, parcelId);
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private void requireParcelExists(ParcelId parcelId) {
        repository.requireParcel(parcelId);
    }

    /**
     * Resolves an actor/holder UUID through PlayerData and the FR-ID subject
     * registry (never raw NBT). Fails closed on unavailable services, an
     * unprovisioned player, or a player without an active subject.
     */
    private void requireResolvableHolder(UUID playerId) {
        if (!holders.isAvailable()) {
            throw new LandUnavailableException(
                    LandUnavailableException.CODE_HOLDER_DIRECTORY_UNAVAILABLE,
                    "PlayerData/subject services are not available for holder resolution"
            );
        }
        if (!holders.hasPlayerRecord(playerId)) {
            throw new LandUnavailableException(
                    LandUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    "Player UUID has no authoritative PlayerData record: " + playerId
            );
        }
        if (!holders.hasActiveSubject(playerId)) {
            throw new LandUnavailableException(
                    LandUnavailableException.CODE_INVALID_HOLDER,
                    "Player UUID has no active subject: " + playerId
            );
        }
    }

    /** Alpha accepts only {@code PLAYER_UUID} usage-right holders. */
    private void requirePlayerHolder(OwnerReference holder) {
        if (holder.kind() != OwnerReferenceKind.PLAYER_UUID) {
            throw new LandUnavailableException(
                    LandUnavailableException.CODE_UNSUPPORTED_HOLDER_KIND,
                    "Alpha land usage rights accept only PLAYER_UUID holders; got "
                            + holder.key()
            );
        }
    }

    private UUID holderUuid(OwnerReference holder) {
        return UUID.fromString(holder.ownerId());
    }

    /**
     * {@code 0} grants without expiry; positive durations expire from now.
     * Overflow of {@code now + durationMillis} is rejected fail-closed.
     */
    private long resolveExpiry(long grantedAt, long durationMillis) {
        if (durationMillis < 0) {
            throw new LandUnavailableException(
                    LandUnavailableException.CODE_INVALID_REQUEST,
                    "durationMillis must not be negative"
            );
        }
        if (durationMillis == 0) {
            return 0L;
        }
        if (durationMillis > Long.MAX_VALUE - grantedAt) {
            throw new LandUnavailableException(
                    LandUnavailableException.CODE_INVALID_REQUEST,
                    "durationMillis overflows the expiry timestamp"
            );
        }
        return grantedAt + durationMillis;
    }

    private long now() {
        long value = clock.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("clock returned a negative timestamp");
        }
        return value;
    }

    /**
     * Display area of an inclusive block region (block volume; never used for
     * any decision). Widths are positive (validated regions), so the product
     * cannot underflow.
     */
    private static long areaOf(com.fontainerepublic.server.land.model.ParcelRegion region) {
        long width = (long) region.maxX() - region.minX() + 1L;
        long height = (long) region.maxY() - region.minY() + 1L;
        long depth = (long) region.maxZ() - region.minZ() + 1L;
        return width * height * depth;
    }
}
