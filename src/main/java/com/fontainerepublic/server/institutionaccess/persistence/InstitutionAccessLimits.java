package com.fontainerepublic.server.institutionaccess.persistence;

/**
 * Implementation-design bounds of the institution-access store (FR-INST-002-A
 * §3: bounded facilities, terminals, capabilities, and bytes; concrete caps
 * are an implementation choice, values below are the design defaults).
 *
 * @param maxFacilities            maximum live facilities before registration
 *                                 fails closed
 * @param maxTerminals             maximum live terminals before registration
 *                                 fails closed
 * @param maxTerminalsPerFacility  maximum terminals anchored to one facility
 * @param maxCapabilitiesPerTerminal maximum capability classes per terminal
 * @param maxTotalBytes            serialized (uncompressed) namespace byte
 *                                 budget; commits beyond it fail closed
 */
public record InstitutionAccessLimits(
        int maxFacilities,
        int maxTerminals,
        int maxTerminalsPerFacility,
        int maxCapabilitiesPerTerminal,
        int maxTotalBytes
) {

    public static final InstitutionAccessLimits DEFAULT =
            new InstitutionAccessLimits(256, 2_048, 64, 6, 8 * 1024 * 1024);

    public InstitutionAccessLimits {
        if (maxFacilities <= 0) {
            throw new IllegalArgumentException("maxFacilities must be positive");
        }
        if (maxTerminals <= 0) {
            throw new IllegalArgumentException("maxTerminals must be positive");
        }
        if (maxTerminalsPerFacility <= 0) {
            throw new IllegalArgumentException(
                    "maxTerminalsPerFacility must be positive"
            );
        }
        if (maxCapabilitiesPerTerminal <= 0) {
            throw new IllegalArgumentException(
                    "maxCapabilitiesPerTerminal must be positive"
            );
        }
        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException("maxTotalBytes must be positive");
        }
    }
}
