package com.fontainerepublic.server.parliament.persistence;

/**
 * Implementation-design bounds of the parliament store (FR-PAR-001-A §3.4:
 * bounded record counts and bytes; concrete caps are an implementation
 * choice, values below are the design defaults).
 *
 * @param maxProposals   maximum live proposal records before submission fails
 *                       closed
 * @param maxVotes       maximum live ballot records before opening fails
 *                       closed
 * @param maxBills       maximum live bill records before passage fails closed
 * @param maxTransitions maximum transition-ledger entries (append-only;
 *                       further transitions fail closed)
 * @param maxCitizens    maximum registered citizens in the passive roster
 * @param maxTotalBytes  serialized (uncompressed) namespace byte budget;
 *                       commits beyond it fail closed
 */
public record ParliamentLimits(
        int maxProposals,
        int maxVotes,
        int maxBills,
        int maxTransitions,
        int maxCitizens,
        int maxTotalBytes
) {

    public static final ParliamentLimits DEFAULT = new ParliamentLimits(
            10_000,
            10_000,
            10_000,
            65_536,
            100_000,
            8 * 1024 * 1024
    );

    public ParliamentLimits {
        if (maxProposals <= 0) {
            throw new IllegalArgumentException("maxProposals must be positive");
        }
        if (maxVotes <= 0) {
            throw new IllegalArgumentException("maxVotes must be positive");
        }
        if (maxBills <= 0) {
            throw new IllegalArgumentException("maxBills must be positive");
        }
        if (maxTransitions <= 0) {
            throw new IllegalArgumentException("maxTransitions must be positive");
        }
        if (maxCitizens <= 0) {
            throw new IllegalArgumentException("maxCitizens must be positive");
        }
        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException("maxTotalBytes must be positive");
        }
    }
}
