package com.fontainerepublic.server.audit.api;

import com.fontainerepublic.server.audit.model.AuditEntry;

import java.util.Optional;

/**
 * Append-only audit service contract (FR-AUD-001-A §4).
 *
 * <p>There is intentionally no update, delete, clear, export-all or
 * enumeration-by-secret method: the ledger is append-only and read surfaces
 * are bounded and projection-only.</p>
 */
public interface AuditService {
    /**
     * Records an entry on the normal autosave path. Documented risk: the
     * entry may be lost on a hard crash before autosave (acceptable for
     * diagnostic/statistical audit).
     *
     * @return the committed projection
     */
    AuditEntry record(AuditDraft draft);

    /**
     * Records an entry through the FR-CORE-002 durable commit gate. The entry
     * becomes visible and the store revision advances only after
     * {@code COMMITTED}; on any failure no receipt, no visible entry and no
     * revision change occur.
     *
     * @return the receipt (never null; committed() only on durability)
     */
    AuditReceipt recordAuthoritative(AuditDraft draft);

    /**
     * Returns the authorized projection for an entry id.
     */
    Optional<AuditEntry> getEntry(long entryId);

    /**
     * Bounded page of entries strictly after {@code afterEntryId}, ascending,
     * respecting classification. {@code limit} must be within
     * {@code [1, AuditRepository.MAX_PAGE_SIZE]}.
     */
    AuditPage page(long afterEntryId, int limit);
}
