package com.fontainerepublic.server.landrights.api;

import com.fontainerepublic.server.land.api.MyUsageRightProjection;
import com.fontainerepublic.server.land.api.MyUsageRightsStatus;
import com.fontainerepublic.server.land.model.ParcelId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Server-side result of one C2S my-usage-rights request
 * (FR-LAND-002-A §3.2/§7). Carries the closed status, the echoed positive
 * {@code requestId}, the store revision observed at query time, the server
 * {code generatedAt} clock, at most {@value MAX_ENTRIES} self-only active
 * projections, the exclusive next cursor, and {@code hasMore}. Non-OK responses
 * carry zero entries and no cursor/hasMore projection. Held by the common C2S
 * handler, which maps it to the wire S2C packet.
 */
public record MyLandRightsResponse(
        MyUsageRightsStatus status,
        int requestId,
        long storeRevision,
        long generatedAt,
        List<MyUsageRightProjection> entries,
        Optional<ParcelId> nextAfterParcelId,
        boolean hasMore
) {

    public static final int MAX_ENTRIES = 32;

    public MyLandRightsResponse {
        status = Objects.requireNonNull(status, "status");
        if (requestId <= 0) {
            throw new IllegalArgumentException("requestId must be positive: " + requestId);
        }
        if (storeRevision < 0) {
            throw new IllegalArgumentException("storeRevision must not be negative");
        }
        if (generatedAt <= 0) {
            throw new IllegalArgumentException("generatedAt must be positive");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException(
                    "Entry count exceeds " + MAX_ENTRIES + ": " + entries.size()
            );
        }
        nextAfterParcelId =
                Objects.requireNonNull(nextAfterParcelId, "nextAfterParcelId");
        if (status != MyUsageRightsStatus.OK
                && (!entries.isEmpty() || nextAfterParcelId.isPresent() || hasMore)) {
            throw new IllegalArgumentException(
                    "A non-OK response must carry zero entries and no cursor/hasMore projection"
            );
        }
    }

    public static MyLandRightsResponse ok(
            int requestId,
            long storeRevision,
            long generatedAt,
            List<MyUsageRightProjection> entries,
            Optional<ParcelId> nextAfterParcelId,
            boolean hasMore
    ) {
        return new MyLandRightsResponse(
                MyUsageRightsStatus.OK,
                requestId,
                storeRevision,
                generatedAt,
                entries,
                nextAfterParcelId,
                hasMore
        );
    }

    public static MyLandRightsResponse closed(
            MyUsageRightsStatus status,
            int requestId,
            long storeRevision,
            long generatedAt
    ) {
        return new MyLandRightsResponse(
                status,
                requestId,
                storeRevision,
                generatedAt,
                List.of(),
                Optional.empty(),
                false
        );
    }
}
