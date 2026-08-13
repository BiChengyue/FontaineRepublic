package com.fontainerepublic.server.audit.service;

import com.fontainerepublic.server.audit.api.AuditDraft;
import com.fontainerepublic.server.audit.api.AuditPage;
import com.fontainerepublic.server.audit.api.AuditReceipt;
import com.fontainerepublic.server.audit.api.AuditService;
import com.fontainerepublic.server.audit.model.AuditEntry;
import com.fontainerepublic.server.audit.persistence.AuditRepository;

import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Runtime implementation of {@link AuditService} (FR-AUD-001-A §4).
 *
 * <p>The service is a thin, owner-thread-scoped facade over the single-writer
 * {@link AuditRepository}; it supplies the server clock so timestamps are
 * server-assigned. Read surfaces are bounded and projection-only.</p>
 */
public final class DefaultAuditService implements AuditService {
    private final AuditRepository repository;
    private final LongSupplier clock;

    public DefaultAuditService(AuditRepository repository, LongSupplier clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AuditEntry record(AuditDraft draft) {
        return repository.record(draft, clock.getAsLong());
    }

    @Override
    public AuditReceipt recordAuthoritative(AuditDraft draft) {
        return repository.recordAuthoritative(draft, clock.getAsLong());
    }

    @Override
    public Optional<AuditEntry> getEntry(long entryId) {
        return repository.find(entryId);
    }

    @Override
    public AuditPage page(long afterEntryId, int limit) {
        return repository.page(afterEntryId, limit);
    }
}
