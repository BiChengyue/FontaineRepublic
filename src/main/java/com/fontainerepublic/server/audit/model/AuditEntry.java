package com.fontainerepublic.server.audit.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, authorized projection of one committed audit entry
 * (FR-AUD-001-A §3.1).
 *
 * <p>This projection deliberately exposes <em>no</em> raw payload plaintext:
 * the full payload is stored separately (bounded, typed) and is never
 * rendered into ordinary output; secrets exist only as a digest. Read-only
 * consumers ({@code getEntry}/{@code page}) receive exactly this object.</p>
 *
 * @param entryId       positive long; assigned once at commit, monotonic
 * @param timestamp     epoch millis, server-assigned
 * @param actorType     closed actor classification
 * @param actorId       bounded canonical id; canonical UUID for players
 * @param category      closed audit category
 * @param moduleId      stable owning module id
 * @param actionId      stable action id, bounded
 * @param targetType    optional stable target reference type
 * @param targetId      optional stable target reference id
 * @param classification every entry classified; never unclassified
 * @param summary       bounded safe human-readable projection
 * @param payloadDigest optional lowercase hex SHA-256 of the canonical payload
 * @param revision      positive long; increments once per commit
 */
public record AuditEntry(
        long entryId,
        long timestamp,
        AuditActorType actorType,
        String actorId,
        AuditCategory category,
        String moduleId,
        String actionId,
        Optional<String> targetType,
        Optional<String> targetId,
        AuditClassification classification,
        String summary,
        Optional<String> payloadDigest,
        long revision
) {
    public AuditEntry {
        if (entryId <= 0) {
            throw new IllegalArgumentException("entryId must be positive");
        }
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must not be negative");
        }
        actorType = Objects.requireNonNull(actorType, "actorType");
        actorId = Objects.requireNonNull(actorId, "actorId");
        category = Objects.requireNonNull(category, "category");
        moduleId = Objects.requireNonNull(moduleId, "moduleId");
        actionId = Objects.requireNonNull(actionId, "actionId");
        classification = Objects.requireNonNull(classification, "classification");
        summary = Objects.requireNonNull(summary, "summary");
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be positive");
        }
        targetType = normalize(targetType);
        targetId = normalize(targetId);
        payloadDigest = normalize(payloadDigest);
        payloadDigest.ifPresent(digest -> {
            if (!digest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "payloadDigest must be lowercase 64-char hex SHA-256"
                );
            }
        });
    }

    private static Optional<String> normalize(Optional<String> value) {
        return value == null ? Optional.empty() : value;
    }
}
