package com.fontainerepublic.server.citizen.model;

import com.fontainerepublic.server.registry.model.SubjectId;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, value-style authoritative citizen record (FR-CIT-001-A §3.1).
 *
 * <p>{@code playerId} matches the PlayerData identity and is the storage key;
 * {@code subjectId} is bound once via the FR-ID service and never changes;
 * {@code firstCitizenAt} is server-assigned and immutable once set. Status and
 * rank changes replace the record and increment {@code recordRevision} exactly
 * once. A record always carries a valid subject — a citizen without a subject
 * is invalid and rejected at load.</p>
 */
public record CitizenRecord(
        int schemaVersion,
        UUID playerId,
        SubjectId subjectId,
        CitizenStatus status,
        CitizenRank rank,
        long firstCitizenAt,
        long recordRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public CitizenRecord {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported citizen record schema version: " + schemaVersion
            );
        }
        playerId = Objects.requireNonNull(playerId, "playerId");
        subjectId = Objects.requireNonNull(subjectId, "subjectId");
        status = Objects.requireNonNull(status, "status");
        rank = Objects.requireNonNull(rank, "rank");
        if (firstCitizenAt <= 0) {
            throw new IllegalArgumentException(
                    "firstCitizenAt must be a positive epoch millisecond"
            );
        }
        if (recordRevision <= 0) {
            throw new IllegalArgumentException("recordRevision must be positive");
        }
        if (!playerId.toString().equals(playerId.toString().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("playerId must be a canonical UUID");
        }
    }

    /** Replacement record with a new status: revision incremented exactly once. */
    public CitizenRecord withStatus(CitizenStatus newStatus) {
        Objects.requireNonNull(newStatus, "newStatus");
        return new CitizenRecord(
                schemaVersion,
                playerId,
                subjectId,
                newStatus,
                rank,
                firstCitizenAt,
                recordRevision + 1
        );
    }

    /** Replacement record with a new rank: revision incremented exactly once. */
    public CitizenRecord withRank(CitizenRank newRank) {
        Objects.requireNonNull(newRank, "newRank");
        return new CitizenRecord(
                schemaVersion,
                playerId,
                subjectId,
                status,
                newRank,
                firstCitizenAt,
                recordRevision + 1
        );
    }
}
