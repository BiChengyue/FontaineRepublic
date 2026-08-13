package com.fontainerepublic.server.audit.api;

import com.fontainerepublic.core.DurableCommitStatus;

/**
 * Outcome of {@link AuditService#recordAuthoritative(AuditDraft)}
 * (FR-AUD-001-A §4).
 *
 * <p>A receipt with {@link DurableCommitStatus#COMMITTED} is returned only
 * after the FR-CORE-002 durable commit gate acknowledged the write; it carries
 * the assigned entry id and the new store revision. Any other status means the
 * authoritative write was rejected or failed: no entry becomes visible and the
 * store revision is unchanged — {@code entryId} is {@code 0} (never assigned)
 * and {@code failureCode} carries the stable, non-secret failure code.</p>
 *
 * @param status        commit outcome (COMMITTED only after durability)
 * @param entryId       assigned entry id; 0 when not committed
 * @param storeRevision store revision after commit; unchanged on failure
 * @param failureCode   stable, non-secret code; empty on success
 */
public record AuditReceipt(
        DurableCommitStatus status,
        long entryId,
        long storeRevision,
        String failureCode
) {
    public boolean committed() {
        return status == DurableCommitStatus.COMMITTED;
    }
}
