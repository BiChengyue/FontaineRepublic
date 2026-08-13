package com.fontainerepublic.server.audit.persistence;

/**
 * Implementation-design bounds of the audit ledger (FR-AUD-001-A §6: values
 * deferred to implementation design; bounded segments + bounded total bytes).
 *
 * @param maxEntriesPerSegment entries per segment before a new chained segment
 *                             rolls over
 * @param maxTotalBytes        serialized (uncompressed) namespace byte budget;
 *                             writes beyond it fail closed with
 *                             {@link AuditUnavailableException#CODE_CAPACITY_EXCEEDED}
 */
public record AuditLimits(
        int maxEntriesPerSegment,
        int maxTotalBytes
) {
    public static final AuditLimits DEFAULT =
            new AuditLimits(512, 8 * 1024 * 1024);

    public AuditLimits {
        if (maxEntriesPerSegment <= 0) {
            throw new IllegalArgumentException("maxEntriesPerSegment must be positive");
        }
        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException("maxTotalBytes must be positive");
        }
    }
}
