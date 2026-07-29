package com.fontainerepublic.server.playerdata.model;

import java.util.Objects;

/**
 * Immutable authoritative base record for one player.
 */
public record PlayerData(
        int schemaVersion,
        long revision,
        PlayerIdentity identity,
        PlayerProfile profile
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PlayerData {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported player record schema version: " + schemaVersion
            );
        }
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be positive");
        }
        identity = Objects.requireNonNull(identity, "identity");
        profile = Objects.requireNonNull(profile, "profile");
    }

    public PlayerData nextRevision(PlayerIdentity newIdentity, PlayerProfile newProfile) {
        if (revision == Long.MAX_VALUE) {
            throw new IllegalStateException("player revision overflow");
        }
        return new PlayerData(
                schemaVersion,
                revision + 1,
                Objects.requireNonNull(newIdentity, "newIdentity"),
                Objects.requireNonNull(newProfile, "newProfile")
        );
    }
}
