package com.fontainerepublic.server.playerdata.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Immutable bounded directory entry mapping one canonical game name to its
 * observed ownership state (FR-DATA-003-A §5.1).
 *
 * <p>Invariants enforced by this type:</p>
 * <ul>
 *   <li>{@code normalizedName} is the canonical ASCII-lowercase key and must
 *       equal the NBT map key;</li>
 *   <li>{@code lastVerifiedSpelling} is a valid server-observed spelling that
 *       normalizes to the key (presentation only);</li>
 *   <li>a non-ambiguous entry carries exactly one
 *       {@code uniqueHistoricalOwner} and zero or one matching current
 *       owner;</li>
 *   <li>an ambiguous entry never carries {@code uniqueHistoricalOwner};</li>
 *   <li>timestamps are non-negative with {@code lastObservedAt} not before
 *       {@code firstObservedAt}; {@code entryRevision} is positive.</li>
 * </ul>
 *
 * <p>{@code currentOwners} is stored as an immutable, deterministically
 * ordered, duplicate-free set.</p>
 */
public record DirectoryEntry(
        String normalizedName,
        String lastVerifiedSpelling,
        boolean permanentlyAmbiguous,
        Optional<UUID> uniqueHistoricalOwner,
        Set<UUID> currentOwners,
        long firstObservedAt,
        long lastObservedAt,
        long entryRevision
) {
    public DirectoryEntry {
        normalizedName = requireValidNormalizedKey(normalizedName);
        lastVerifiedSpelling = requireValidSpelling(
                lastVerifiedSpelling,
                normalizedName
        );
        uniqueHistoricalOwner = Objects.requireNonNull(
                uniqueHistoricalOwner,
                "uniqueHistoricalOwner"
        );
        currentOwners = java.util.Collections.unmodifiableSet(
                new TreeSet<>(Objects.requireNonNull(currentOwners, "currentOwners"))
        );
        if (firstObservedAt < 0) {
            throw new IllegalArgumentException("firstObservedAt must not be negative");
        }
        if (lastObservedAt < firstObservedAt) {
            throw new IllegalArgumentException("lastObservedAt must not precede firstObservedAt");
        }
        if (entryRevision < 1) {
            throw new IllegalArgumentException("entryRevision must be positive");
        }
        if (permanentlyAmbiguous) {
            if (uniqueHistoricalOwner.isPresent()) {
                throw new IllegalArgumentException(
                        "ambiguous entry must not carry a unique historical owner"
                );
            }
        } else {
            if (uniqueHistoricalOwner.isEmpty()) {
                throw new IllegalArgumentException(
                        "non-ambiguous entry requires a unique historical owner"
                );
            }
            if (currentOwners.size() > 1) {
                throw new IllegalArgumentException(
                        "non-ambiguous entry cannot have multiple current owners"
                );
            }
            UUID uniqueOwner = uniqueHistoricalOwner.get();
            for (UUID owner : currentOwners) {
                if (!uniqueOwner.equals(owner)) {
                    throw new IllegalArgumentException(
                            "non-ambiguous current owner must equal the unique historical owner"
                    );
                }
            }
        }
    }

    private static String requireValidNormalizedKey(String value) {
        Objects.requireNonNull(value, "normalizedName");
        if (!value.equals(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("normalizedName must be ASCII lowercase");
        }
        if (GameNameNormalizer.normalize(value).isEmpty()) {
            throw new IllegalArgumentException("normalizedName is not a valid game-name key");
        }
        return value;
    }

    private static String requireValidSpelling(String spelling, String normalizedName) {
        Objects.requireNonNull(spelling, "lastVerifiedSpelling");
        Optional<String> key = GameNameNormalizer.normalize(spelling);
        if (key.isEmpty() || !key.get().equals(normalizedName)) {
            throw new IllegalArgumentException(
                    "lastVerifiedSpelling must normalize to the entry key"
            );
        }
        return spelling;
    }
}
