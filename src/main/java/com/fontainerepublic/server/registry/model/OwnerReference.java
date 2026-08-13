package com.fontainerepublic.server.registry.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed, immutable, bounded owner identity of a subject (FR-ID-001-A §3.4).
 *
 * <p>Owner references are the stable external owner identity: a canonical
 * {@code PLAYER_UUID:<uuid>} obtained from authenticated server state or
 * PlayerData (never derived from a game name), or the constant
 * {@code OFFICE_ID:HYDRO_ARCHON} referring to the office itself. The index key
 * is a reversible, versioned canonical encoding, not a lossy display string.</p>
 */
public record OwnerReference(OwnerReferenceKind kind, String ownerId) {

    /** Constant owner of the permanent Hydro Archon office subject. */
    public static final String HYDRO_ARCHON_OFFICE_ID = "HYDRO_ARCHON";
    public static final OwnerReference HYDRO_ARCHON_OFFICE =
            new OwnerReference(OwnerReferenceKind.OFFICE_ID, HYDRO_ARCHON_OFFICE_ID);

    public OwnerReference {
        kind = Objects.requireNonNull(kind, "kind");
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        switch (kind) {
            case PLAYER_UUID -> requireCanonicalUuid(ownerId);
            case OFFICE_ID -> {
                if (!ownerId.equals(HYDRO_ARCHON_OFFICE_ID)) {
                    throw new IllegalArgumentException(
                            "OFFICE_ID owner accepts only the constant "
                                    + HYDRO_ARCHON_OFFICE_ID + "; got " + ownerId
                    );
                }
            }
        }
    }

    public static OwnerReference forPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return new OwnerReference(OwnerReferenceKind.PLAYER_UUID, playerId.toString());
    }

    /** Reversible canonical owner index key. */
    public String key() {
        return kind.keyPrefix() + ":" + ownerId;
    }

    /** Parses a canonical owner index key back into a typed reference. */
    public static OwnerReference parseKey(String key) {
        Objects.requireNonNull(key, "key");
        int separator = key.indexOf(':');
        if (separator <= 0 || separator == key.length() - 1) {
            throw new IllegalArgumentException("Invalid owner reference key: " + key);
        }
        OwnerReferenceKind kind = kindForPrefix(key.substring(0, separator));
        return new OwnerReference(kind, key.substring(separator + 1));
    }

    private static OwnerReferenceKind kindForPrefix(String prefix) {
        for (OwnerReferenceKind kind : OwnerReferenceKind.values()) {
            if (kind.keyPrefix().equals(prefix)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unsupported owner reference kind: " + prefix);
    }

    private static void requireCanonicalUuid(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException(
                        "PLAYER_UUID owner must be a canonical UUID: " + value
                );
            }
        } catch (IllegalArgumentException failure) {
            if (failure instanceof IllegalArgumentException
                    && failure.getMessage() != null
                    && failure.getMessage().startsWith("PLAYER_UUID")) {
                throw failure;
            }
            throw new IllegalArgumentException(
                    "PLAYER_UUID owner must be a canonical UUID: " + value
            );
        }
    }
}
