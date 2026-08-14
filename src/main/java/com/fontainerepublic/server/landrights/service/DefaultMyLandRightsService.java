package com.fontainerepublic.server.landrights.service;

import com.fontainerepublic.server.land.api.LandService;
import com.fontainerepublic.server.land.api.MyUsageRightsPage;
import com.fontainerepublic.server.land.api.MyUsageRightsStatus;
import com.fontainerepublic.server.land.api.MyUsageRightProjection;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.land.persistence.LandUnavailableException;
import com.fontainerepublic.server.landrights.api.MyLandRightsResponse;
import com.fontainerepublic.server.landrights.api.MyLandRightsServerPlayerAccess;
import com.fontainerepublic.server.landrights.api.MyLandRightsService;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Runtime implementation of the communicator my-usage-rights service
 * (FR-LAND-002-A §5.2): a thin fail-closed gate — live online {@code ServerPlayer},
 * held registered communicator — over the authoritative self-only
 * {@code LandService.myUsageRights} query. A failed gate or unavailable land/
 * identity service maps to a closed {@link MyUsageRightsStatus#UNAVAILABLE} page
 * (never leaking whether the player has any rights); an invalid limit/cursor maps
 * to {@link MyUsageRightsStatus#INVALID_REQUEST}; store-revision drift is returned
 * as {@link MyUsageRightsStatus#RESET_REQUIRED} by the land service itself.
 */
public final class DefaultMyLandRightsService implements MyLandRightsService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final LandService land;
    private final MyLandRightsServerPlayerAccess playerAccess;
    private final LongSupplier clock;

    public DefaultMyLandRightsService(
            LandService land,
            MyLandRightsServerPlayerAccess playerAccess,
            LongSupplier clock
    ) {
        this.land = Objects.requireNonNull(land, "land");
        this.playerAccess = Objects.requireNonNull(playerAccess, "playerAccess");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public MyLandRightsResponse request(
            UUID playerId,
            int requestId,
            Optional<ParcelId> afterParcelId,
            long expectedStoreRevision,
            int limit
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(afterParcelId, "afterParcelId");
        long now = now();
        if (!playerAccess.isOnline(playerId)) {
            return MyLandRightsResponse.closed(
                    MyUsageRightsStatus.UNAVAILABLE, requestId, 0L, now
            );
        }
        if (!playerAccess.holdsCommunicator(playerId)) {
            LOGGER.debug(
                    "[LandRights] Rejected request for {} : communicator not held",
                    playerId
            );
            return MyLandRightsResponse.closed(
                    MyUsageRightsStatus.UNAVAILABLE, requestId, 0L, now
            );
        }
        MyUsageRightsPage page;
        try {
            page = land.myUsageRights(playerId, afterParcelId, expectedStoreRevision, limit);
        } catch (LandUnavailableException failure) {
            return MyLandRightsResponse.closed(
                    mapFailure(failure), requestId, 0L, now
            );
        }
        // Propagate the page's own closed status exactly: a revision-drift
        // (or otherwise non-OK) page keeps its status (RESET_REQUIRED /
        // INVALID_REQUEST / UNAVAILABLE) and is never re-wrapped as OK. For a
        // non-OK page the land service already guarantees zero entries, no
        // cursor and hasMore=false, but the response invariant is re-verified
        // by the constructor.
        return new MyLandRightsResponse(
                page.status(),
                requestId,
                page.storeRevision(),
                page.generatedAt(),
                page.entries(),
                page.nextAfterParcelId(),
                page.hasMore()
        );
    }

    /** Maps a land-service failure to a stable my-rights closed status. */
    private static MyUsageRightsStatus mapFailure(LandUnavailableException failure) {
        if (failure.failureCode()
                .equals(LandUnavailableException.CODE_INVALID_REQUEST)) {
            return MyUsageRightsStatus.INVALID_REQUEST;
        }
        // Unavailable subject/identity services, unprovisioned/invalid holder,
        // store failure, capacity, land-unavailable: all fail closed as UNAVAILABLE.
        return MyUsageRightsStatus.UNAVAILABLE;
    }

    private long now() {
        long value = clock.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("clock returned a non-positive timestamp");
        }
        return value;
    }
}
