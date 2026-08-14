package com.fontainerepublic.server.land.api;

import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.land.model.UsageType;
import com.fontainerepublic.server.land.model.ZoneType;

import java.util.Objects;

/**
 * Read-only display projection of one of the calling player's own current usage
 * rights (FR-LAND-002-A §3.1).
 *
 * <p>This is the <b>only</b> explicitly allowed per-holder parcel projection:
 * it carries exactly the presentation fields the "我的地块" view needs — parcel
 * id, dimension, inclusive region, zone type, usage type, grant/expiry, and the
 * per-right/per-parcel revisions. It deliberately excludes other holders,
 * audit records, violation reports, internal holder-index state, and any
 * target identity (the projection is always the authenticated player's own).</p>
 *
 * <p>{@code parcelId} is a server-assigned {@link ParcelId}; {@code region} is
 * the parcel's inclusive block bounding box; {@code zoneType}/{@code usageType}
 * are the typed planning/usage enums.</p>
 */
public record MyUsageRightProjection(
        ParcelId parcelId,
        String dimension,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        ZoneType zoneType,
        UsageType usageType,
        long grantedAt,
        long expiresAt,
        long rightRevision,
        long parcelRevision
) {
    public MyUsageRightProjection {
        Objects.requireNonNull(parcelId, "parcelId");
        dimension = Objects.requireNonNull(dimension, "dimension");
        if (dimension.isEmpty()) {
            throw new IllegalArgumentException("dimension must not be empty");
        }
        zoneType = Objects.requireNonNull(zoneType, "zoneType");
        usageType = Objects.requireNonNull(usageType, "usageType");
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException(
                    "region min must not exceed max on any axis"
            );
        }
        if (grantedAt <= 0) {
            throw new IllegalArgumentException(
                    "grantedAt must be a positive epoch millisecond"
            );
        }
        if (expiresAt != 0 && expiresAt <= grantedAt) {
            throw new IllegalArgumentException(
                    "expiresAt must be 0 (no expiry) or after grantedAt"
            );
        }
        if (rightRevision <= 0) {
            throw new IllegalArgumentException("rightRevision must be positive");
        }
        if (parcelRevision <= 0) {
            throw new IllegalArgumentException("parcelRevision must be positive");
        }
    }
}
