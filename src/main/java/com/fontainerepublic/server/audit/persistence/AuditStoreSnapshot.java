package com.fontainerepublic.server.audit.persistence;

import com.fontainerepublic.server.audit.model.AuditSegment;

import java.util.List;

/**
 * Immutable snapshot of the audit store (FR-AUD-001-A §3.3).
 *
 * <p>{@code tailDigest} is derived from the last segment during encoding and
 * stored at the root so that tampering with the tail segment is also
 * detected; it is intentionally not a snapshot field (it is recomputed by the
 * codec).</p>
 *
 * @param storeVersion  store schema version
 * @param storeRevision monotonic revision; increments once per committed write
 * @param nextEntryId   next entry id to assign (monotonic)
 * @param segments      ordered append-only segments forming the digest chain
 */
public record AuditStoreSnapshot(
        int storeVersion,
        long storeRevision,
        long nextEntryId,
        List<AuditSegment> segments
) {
    public static final int CURRENT_STORE_VERSION = 1;

    public AuditStoreSnapshot {
        if (storeVersion <= 0) {
            throw new IllegalArgumentException("storeVersion must be positive");
        }
        if (storeRevision < 0) {
            throw new IllegalArgumentException("storeRevision must not be negative");
        }
        if (nextEntryId <= 0) {
            throw new IllegalArgumentException("nextEntryId must be positive");
        }
        segments = List.copyOf(segments);
    }

    /** Fresh empty store: revision 0, first entry id 1, no segments. */
    public static AuditStoreSnapshot empty() {
        return new AuditStoreSnapshot(CURRENT_STORE_VERSION, 0L, 1L, List.of());
    }
}
