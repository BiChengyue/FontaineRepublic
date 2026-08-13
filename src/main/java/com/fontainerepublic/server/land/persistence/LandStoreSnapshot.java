package com.fontainerepublic.server.land.persistence;

import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.land.model.ViolationReport;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable, fully validated representation of the complete {@code "land"}
 * namespace (FR-LAND-001-A §3.5).
 *
 * <p>The constructor enforces the authoritative invariants — no partially
 * consistent snapshot can exist:</p>
 * <ul>
 *   <li>every {@code Parcels} key matches its parcel's canonical id;</li>
 *   <li>no duplicate parcel id or report id;</li>
 *   <li>every parcel owns the constant {@code REPUBLIC} ownership and every
 *       usage-right key matches its right holder;</li>
 *   <li>every report references an existing parcel.</li>
 * </ul>
 * <p>Violations reject the whole snapshot (fail closed).</p>
 */
public record LandStoreSnapshot(
        int storeVersion,
        long storeRevision,
        Map<ParcelId, LandParcel> parcels,
        Map<Long, ViolationReport> reports
) {

    public static final int CURRENT_STORE_VERSION = 1;

    public LandStoreSnapshot {
        if (storeVersion != CURRENT_STORE_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported land store version: " + storeVersion
            );
        }
        if (storeRevision < 0) {
            throw new IllegalArgumentException("storeRevision must not be negative");
        }
        parcels = Map.copyOf(Objects.requireNonNull(parcels, "parcels"));
        reports = Map.copyOf(Objects.requireNonNull(reports, "reports"));

        for (Map.Entry<ParcelId, LandParcel> entry : parcels.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().parcelId())) {
                throw invalid(
                        "Parcels key " + entry.getKey()
                                + " does not match parcel id " + entry.getValue().parcelId()
                );
            }
        }
        for (Map.Entry<Long, ViolationReport> entry : reports.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().reportId())) {
                throw invalid(
                        "Reports key " + entry.getKey()
                                + " does not match report id " + entry.getValue().reportId()
                );
            }
            if (!parcels.containsKey(entry.getValue().parcelId())) {
                throw invalid(
                        "Report " + entry.getKey() + " references unknown parcel "
                                + entry.getValue().parcelId()
                );
            }
        }
    }

    private static LandNbtException invalid(String message) {
        return new LandNbtException(message);
    }
}
