package com.fontainerepublic.server.playerdata.persistence;

import com.fontainerepublic.server.playerdata.model.DirectoryEntry;
import com.fontainerepublic.server.playerdata.model.GameNameNormalizer;
import com.fontainerepublic.server.playerdata.model.MigrationProvenance;
import com.fontainerepublic.server.playerdata.model.PlayerData;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable representation of the complete player-data namespace
 * (Players + Directory, FR-DATA-003-A §7.2).
 */
public record PlayerDataStoreSnapshot(
        int storeVersion,
        long storeRevision,
        Map<UUID, PlayerData> players,
        Map<String, DirectoryEntry> directory,
        MigrationProvenance migrationProvenance
) {
    public static final int CURRENT_STORE_VERSION = 2;
    public static final int CURRENT_DIRECTORY_VERSION = 1;

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
        directory = Map.copyOf(Objects.requireNonNull(directory, "directory"));
        directory.forEach((key, entry) -> {
            Objects.requireNonNull(key, "directory key");
            Objects.requireNonNull(entry, "directory entry");
            if (!key.equals(entry.normalizedName())) {
                throw new IllegalArgumentException(
                        "Directory key " + key + " does not match entry "
                                + entry.normalizedName()
                );
            }
        });
        migrationProvenance = Objects.requireNonNull(
                migrationProvenance,
                "migrationProvenance"
        );
        crossValidate(players, directory);
    }

    public static PlayerDataStoreSnapshot empty() {
        return new PlayerDataStoreSnapshot(
                CURRENT_STORE_VERSION,
                0,
                Map.of(),
                Map.of(),
                MigrationProvenance.NONE
        );
    }

    /**
     * Bidirectional cross-validation (FR-DATA-003-A §7.3): every current owner
     * exists in Players with a name normalizing to its entry, every owner is
     * listed exactly once across the directory, and every PlayerData record
     * appears in exactly one entry's {@code currentOwners}.
     */
    private static void crossValidate(
            Map<UUID, PlayerData> players,
            Map<String, DirectoryEntry> directory
    ) {
        Set<UUID> covered = new HashSet<>();
        for (DirectoryEntry entry : directory.values()) {
            for (UUID owner : entry.currentOwners()) {
                PlayerData player = players.get(owner);
                if (player == null) {
                    throw new IllegalArgumentException(
                            "Directory current owner " + owner + " has no player record"
                    );
                }
                String playerKey = GameNameNormalizer.normalize(
                        player.identity().lastKnownGameName()
                ).orElseThrow(() -> new IllegalArgumentException(
                        "Player " + owner + " has an invalid lastKnownGameName"
                ));
                if (!playerKey.equals(entry.normalizedName())) {
                    throw new IllegalArgumentException(
                            "Player " + owner + " name does not normalize to entry "
                                    + entry.normalizedName()
                    );
                }
                if (!covered.add(owner)) {
                    throw new IllegalArgumentException(
                            "Player " + owner + " is listed in more than one directory entry"
                    );
                }
            }
        }
        for (UUID playerId : players.keySet()) {
            if (!covered.contains(playerId)) {
                throw new IllegalArgumentException(
                        "Player " + playerId + " is not a current owner of any directory entry"
                );
            }
        }
    }
}
