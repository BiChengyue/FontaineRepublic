package com.fontainerepublic.server.audit.api;

import com.fontainerepublic.server.audit.model.AuditEntry;

import java.util.List;

/**
 * Bounded read result of {@link AuditService#page(long, int)}
 * (FR-AUD-001-A §4).
 *
 * @param entries projections strictly ascending by entry id; at most the
 *                 requested limit
 * @param hasMore  whether entries with a higher entry id exist
 */
public record AuditPage(
        List<AuditEntry> entries,
        boolean hasMore
) {
    public AuditPage {
        entries = List.copyOf(entries);
    }
}
