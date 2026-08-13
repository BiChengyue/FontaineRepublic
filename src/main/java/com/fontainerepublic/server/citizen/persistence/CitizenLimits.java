package com.fontainerepublic.server.citizen.persistence;

/**
 * Implementation-design bounds of the citizen store (FR-CIT-001-A §3.3:
 * bounded record count and bytes; concrete caps are an implementation choice,
 * values below are the design defaults).
 *
 * @param maxCitizens   maximum live citizen records before provisioning fails
 *                      closed
 * @param maxTotalBytes serialized (uncompressed) namespace byte budget;
 *                      commits beyond it fail closed
 */
public record CitizenLimits(
        int maxCitizens,
        int maxTotalBytes
) {

    public static final CitizenLimits DEFAULT =
            new CitizenLimits(10_000, 8 * 1024 * 1024);

    public CitizenLimits {
        if (maxCitizens <= 0) {
            throw new IllegalArgumentException("maxCitizens must be positive");
        }
        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException("maxTotalBytes must be positive");
        }
    }
}
