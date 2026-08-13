package com.fontainerepublic.server.audit.model;

import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One bounded append-only segment of the audit ledger
 * (FR-AUD-001-A §3.3).
 *
 * <p>Each segment carries the digest of the previous segment (tamper-evidence
 * chain). The genesis segment has an empty {@code prevDigestHex}. A closed
 * (non-active) segment is never rewritten; only the active tail segment may be
 * extended by appending, and overflow rolls over to a new chained segment.</p>
 *
 * <p>Entry payload plaintext is kept out of the {@link AuditEntry} projections;
 * it lives in {@code payloads} keyed by entry id and is only ever used by the
 * persistence layer (encoding and chain digest). Secret entries never store a
 * payload at all.</p>
 *
 * @param segmentId     positive monotonic segment id
 * @param startEntryId  entry id of the first entry in this segment
 * @param endEntryId    entry id of the last entry in this segment
 * @param prevDigestHex lowercase hex SHA-256 of the previous segment; empty for genesis
 * @param entries       immutable, strictly consecutive entry projections
 * @param payloads      entry id -> payload plaintext (non-secret entries only)
 */
public record AuditSegment(
        long segmentId,
        long startEntryId,
        long endEntryId,
        String prevDigestHex,
        List<AuditEntry> entries,
        Map<Long, CompoundTag> payloads
) {
    public AuditSegment {
        if (segmentId <= 0) {
            throw new IllegalArgumentException("segmentId must be positive");
        }
        if (startEntryId <= 0) {
            throw new IllegalArgumentException("startEntryId must be positive");
        }
        if (endEntryId < startEntryId) {
            throw new IllegalArgumentException("endEntryId must not be before startEntryId");
        }
        prevDigestHex = Objects.requireNonNull(prevDigestHex, "prevDigestHex");
        if (!prevDigestHex.isEmpty() && !prevDigestHex.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "prevDigestHex must be lowercase 64-char hex SHA-256 or empty"
            );
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        payloads = payloads == null ? Map.of() : Map.copyOf(payloads);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("segment must contain at least one entry");
        }
        if (entries.get(0).entryId() != startEntryId) {
            throw new IllegalArgumentException(
                    "first entry id " + entries.get(0).entryId()
                            + " does not match startEntryId " + startEntryId
            );
        }
        if (entries.get(entries.size() - 1).entryId() != endEntryId) {
            throw new IllegalArgumentException(
                    "last entry id " + entries.get(entries.size() - 1).entryId()
                            + " does not match endEntryId " + endEntryId
            );
        }
        for (int index = 1; index < entries.size(); index++) {
            if (entries.get(index).entryId() != entries.get(index - 1).entryId() + 1) {
                throw new IllegalArgumentException(
                        "segment entries must have strictly consecutive entry ids"
                );
            }
        }
        for (AuditEntry entry : entries) {
            if (entry.classification()
                    == AuditClassification.SECRET_DIGEST_ONLY
                    && payloads.containsKey(entry.entryId())) {
                throw new IllegalArgumentException(
                        "secret entries must never carry persisted payload plaintext"
                );
            }
        }
    }
}
