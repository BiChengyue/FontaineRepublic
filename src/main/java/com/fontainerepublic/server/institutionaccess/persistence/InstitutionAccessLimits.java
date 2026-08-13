package com.fontainerepublic.server.institutionaccess.persistence;

/**
 * Implementation-design bounds of the institution-access store (FR-INST-002-B
 * §2: bounded facilities, zones, capabilities, and bytes; concrete caps are
 * an implementation choice, values below are the design defaults).
 *
 * @param maxFacilities              maximum live facilities before
 *                                   registration fails closed
 * @param maxZones                   maximum live zones before registration
 *                                   fails closed
 * @param maxZonesPerFacility        maximum zones anchored to one facility
 * @param maxCapabilitiesPerZone     maximum capability classes per zone
 * @param maxTotalBytes              serialized (uncompressed) namespace byte
 *                                   budget; commits beyond it fail closed
 */
public record InstitutionAccessLimits(
        int maxFacilities,
        int maxZones,
        int maxZonesPerFacility,
        int maxCapabilitiesPerZone,
        int maxTotalBytes
) {

    public static final InstitutionAccessLimits DEFAULT =
            new InstitutionAccessLimits(256, 2_048, 64, 6, 8 * 1024 * 1024);

    public InstitutionAccessLimits {
        if (maxFacilities <= 0) {
            throw new IllegalArgumentException("maxFacilities must be positive");
        }
        if (maxZones <= 0) {
            throw new IllegalArgumentException("maxZones must be positive");
        }
        if (maxZonesPerFacility <= 0) {
            throw new IllegalArgumentException(
                    "maxZonesPerFacility must be positive"
            );
        }
        if (maxCapabilitiesPerZone <= 0) {
            throw new IllegalArgumentException(
                    "maxCapabilitiesPerZone must be positive"
            );
        }
        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException("maxTotalBytes must be positive");
        }
    }
}
