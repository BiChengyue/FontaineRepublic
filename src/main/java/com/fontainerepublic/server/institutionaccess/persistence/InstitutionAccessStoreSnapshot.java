package com.fontainerepublic.server.institutionaccess.persistence;

import com.fontainerepublic.server.institutionaccess.model.Facility;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.Zone;
import com.fontainerepublic.server.institutionaccess.model.ZoneId;
import com.fontainerepublic.server.land.model.ParcelId;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable, fully validated representation of the complete
 * {@code institution-access} namespace (FR-INST-002-B §2).
 *
 * <p>The constructor enforces the authoritative invariants — no partially
 * consistent snapshot can exist:</p>
 * <ul>
 *   <li>every {@code Facilities} key matches its facility's canonical id;</li>
 *   <li>every {@code Zones} key matches its zone's canonical id;</li>
 *   <li>every zone references an existing facility;</li>
 *   <li>no duplicate facility or zone id;</li>
 *   <li>no two facilities share the same FR-LAND parcel (single binding).</li>
 * </ul>
 * <p>Violations reject the whole snapshot (fail closed).</p>
 */
public record InstitutionAccessStoreSnapshot(
        int storeVersion,
        long storeRevision,
        Map<FacilityId, Facility> facilities,
        Map<ZoneId, Zone> zones
) {

    public static final int CURRENT_STORE_VERSION = 2;

    public InstitutionAccessStoreSnapshot {
        if (storeVersion != CURRENT_STORE_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported institution-access store version: " + storeVersion
            );
        }
        if (storeRevision < 0) {
            throw new IllegalArgumentException("storeRevision must not be negative");
        }
        facilities = Map.copyOf(Objects.requireNonNull(facilities, "facilities"));
        zones = Map.copyOf(Objects.requireNonNull(zones, "zones"));

        Map<ParcelId, FacilityId> parcelBindings = new java.util.HashMap<>();
        for (Map.Entry<FacilityId, Facility> entry : facilities.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().facilityId())) {
                throw invalid(
                        "Facilities key " + entry.getKey()
                                + " does not match facility id "
                                + entry.getValue().facilityId()
                );
            }
            FacilityId previous = parcelBindings.put(
                    entry.getValue().parcelId(),
                    entry.getKey()
            );
            if (previous != null) {
                throw invalid(
                        "Parcel " + entry.getValue().parcelId()
                                + " is bound to multiple facilities ("
                                + previous + " and " + entry.getKey() + ")"
                );
            }
        }
        for (Map.Entry<ZoneId, Zone> entry : zones.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().zoneId())) {
                throw invalid(
                        "Zones key " + entry.getKey()
                                + " does not match zone id "
                                + entry.getValue().zoneId()
                );
            }
            if (!facilities.containsKey(entry.getValue().facilityId())) {
                throw invalid(
                        "Zone " + entry.getKey() + " references unknown facility "
                                + entry.getValue().facilityId()
                );
            }
        }
    }

    private static InstitutionAccessNbtException invalid(String message) {
        return new InstitutionAccessNbtException(message);
    }
}
