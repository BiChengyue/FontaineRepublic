package com.fontainerepublic.server.land.api;

import com.fontainerepublic.server.land.model.ZoneType;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, bounded read-only aggregate of the republic's land parcels
 * (FR-CLIENT-001-IMPL-B3b; FR-CLIENT-001-B3 §5.1).
 *
 * <p>This is the single public-overview projection of the land module: one
 * value object (never a parcel list, never an enumeration) carrying the parcel
 * count, the total area, the per-{@link ZoneType} distribution (only zones
 * with at least one parcel, in deterministic {@code ZoneType} declaration
 * order), and the current store revision. The client summary is assembled
 * from this aggregate at login and is display-only; every counter is
 * non-negative and the zone list is capped at {@value #MAX_ZONES} entries.</p>
 */
public record LandSummary(
        int parcelCount,
        long totalArea,
        List<ZoneSummary> zones,
        long storeRevision
) {

    /** Hard cap of the zone distribution (matches the wire bound). */
    public static final int MAX_ZONES = 16;

    public LandSummary {
        if (parcelCount < 0) {
            throw new IllegalArgumentException(
                    "parcelCount must not be negative: " + parcelCount
            );
        }
        if (totalArea < 0) {
            throw new IllegalArgumentException(
                    "totalArea must not be negative: " + totalArea
            );
        }
        if (storeRevision < 0) {
            throw new IllegalArgumentException(
                    "storeRevision must not be negative: " + storeRevision
            );
        }
        zones = List.copyOf(Objects.requireNonNull(zones, "zones"));
        if (zones.size() > MAX_ZONES) {
            throw new IllegalArgumentException(
                    "Zones exceed " + MAX_ZONES + ": " + zones.size()
            );
        }
        for (ZoneSummary zone : zones) {
            Objects.requireNonNull(zone, "zone");
        }
    }

    /**
     * One zone of the distribution: the planning designation, the number of
     * parcels in it and their aggregate area (block volume).
     */
    public record ZoneSummary(
            ZoneType zoneType,
            int count,
            long area
    ) {

        public ZoneSummary {
            zoneType = Objects.requireNonNull(zoneType, "zoneType");
            if (count < 0) {
                throw new IllegalArgumentException(
                        "Zone count must not be negative: " + count
                );
            }
            if (area < 0) {
                throw new IllegalArgumentException(
                        "Zone area must not be negative: " + area
                );
            }
        }
    }
}
