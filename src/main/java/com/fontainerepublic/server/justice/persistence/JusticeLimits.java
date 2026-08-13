package com.fontainerepublic.server.justice.persistence;

/**
 * Implementation-design bounds of the justice store (FR-JUS-001-A §3.4:
 * bounded record counts and bytes; concrete caps are an implementation
 * choice, values below are the design defaults).
 *
 * @param maxCases        maximum live case records before filing/intake fails
 *                        closed
 * @param maxEvidence     maximum live evidence records before submission
 *                        fails closed
 * @param maxVerdicts     maximum live verdict records before issue fails
 *                        closed
 * @param maxTransitions  maximum transition-ledger entries (append-only;
 *                        further transitions fail closed)
 * @param maxEvidencePerCase maximum evidence records per single case
 * @param maxTotalBytes   serialized (uncompressed) namespace byte budget;
 *                        commits beyond it fail closed
 */
public record JusticeLimits(
        int maxCases,
        int maxEvidence,
        int maxVerdicts,
        int maxTransitions,
        int maxEvidencePerCase,
        int maxTotalBytes
) {

    public static final JusticeLimits DEFAULT = new JusticeLimits(
            10_000,
            50_000,
            10_000,
            200_000,
            1_000,
            8 * 1024 * 1024
    );

    public JusticeLimits {
        if (maxCases <= 0) {
            throw new IllegalArgumentException("maxCases must be positive");
        }
        if (maxEvidence <= 0) {
            throw new IllegalArgumentException("maxEvidence must be positive");
        }
        if (maxVerdicts <= 0) {
            throw new IllegalArgumentException("maxVerdicts must be positive");
        }
        if (maxTransitions <= 0) {
            throw new IllegalArgumentException("maxTransitions must be positive");
        }
        if (maxEvidencePerCase <= 0) {
            throw new IllegalArgumentException("maxEvidencePerCase must be positive");
        }
        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException("maxTotalBytes must be positive");
        }
    }
}
