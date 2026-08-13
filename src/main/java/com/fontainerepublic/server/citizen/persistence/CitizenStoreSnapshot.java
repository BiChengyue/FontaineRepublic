package com.fontainerepublic.server.citizen.persistence;

import com.fontainerepublic.server.citizen.model.CitizenRecord;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, fully validated representation of the complete {@code "citizen"}
 * namespace (FR-CIT-001-A §3.3).
 *
 * <p>The constructor enforces the authoritative invariants — no partially
 * consistent snapshot can exist:</p>
 * <ul>
 *   <li>every {@code Citizens} key matches its record's canonical player id;</li>
 *   <li>no duplicate player id;</li>
 *   <li>every record carries a valid (non-null) subject id, positive revision
 *       and positive first-citizenship timestamp (enforced by
 *       {@link CitizenRecord}).</li>
 * </ul>
 * <p>Duplicates, mismatches, or records without a subject reject the whole
 * snapshot (fail closed).</p>
 */
public record CitizenStoreSnapshot(
        int storeVersion,
        long storeRevision,
        Map<UUID, CitizenRecord> citizens
) {

    public static final int CURRENT_STORE_VERSION = 1;

    public CitizenStoreSnapshot {
        if (storeVersion != CURRENT_STORE_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported citizen store version: " + storeVersion
            );
        }
        if (storeRevision < 0) {
            throw new IllegalArgumentException("storeRevision must not be negative");
        }
        citizens = Map.copyOf(Objects.requireNonNull(citizens, "citizens"));

        for (Map.Entry<UUID, CitizenRecord> entry : citizens.entrySet()) {
            UUID key = entry.getKey();
            CitizenRecord record = entry.getValue();
            if (!key.equals(record.playerId())) {
                throw invalid(
                        "Citizens key " + key + " does not match record playerId "
                                + record.playerId()
                );
            }
            if (!key.toString().equals(key.toString().toLowerCase(Locale.ROOT))) {
                throw invalid("Citizens key is not canonical: " + key);
            }
        }
    }

    private static CitizenNbtException invalid(String message) {
        return new CitizenNbtException(message);
    }
}
