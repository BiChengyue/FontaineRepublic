package com.fontainerepublic.server.playerdata.persistence;

import com.fontainerepublic.server.playerdata.model.PlayerData;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable representation of the complete player-data namespace.
 */
public record PlayerDataStoreSnapshot(
        int storeVersion,
        long storeRevision,
        Map<UUID, PlayerData> players
) {
    public static final int CURRENT_STORE_VERSION = 1;

    public PlayerDataStoreSnapshot {
        if (storeVersion != CURRENT_STORE_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported player-data store version: " + storeVersion
            );
        }
        if (storeRevision < 0) {
            throw new IllegalArgumentException("storeRevision must not be negative");
        }
        players = Map.copyOf(Objects.requireNonNull(players, "players"));
        players.forEach((playerId, playerData) -> {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(playerData, "playerData");
            if (!playerId.equals(playerData.identity().playerId())) {
                throw new IllegalArgumentException(
                        "Player map key does not match identity UUID: " + playerId
                );
            }
        });
    }

    public static PlayerDataStoreSnapshot empty() {
        return new PlayerDataStoreSnapshot(CURRENT_STORE_VERSION, 0, Map.of());
    }
}
