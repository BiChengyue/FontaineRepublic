package com.fontainerepublic.server.playerdata.api;

import com.fontainerepublic.server.playerdata.model.PlayerData;
import com.fontainerepublic.server.playerdata.model.PlayerProfileUpdate;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative API for base player records.
 *
 * <h3>Method contracts</h3>
 * <ul>
 *   <li>{@link #ensurePlayer(UUID, String)} — idempotent: creates a record if
 *       none exists; returns an existing record <em>unchanged</em>.</li>
 *   <li>{@link #recordLogin(UUID, String)} — records a verified login: updates
 *       {@code lastKnownGameName} and {@code lastSeenAt}; creates a new record
 *       only when one does not already exist.</li>
 *   <li>{@link #recordLogout(UUID)} — records a verified logout: updates
 *       {@code lastSeenAt} only when a record exists.</li>
 * </ul>
 */
public interface PlayerDataService {
    /**
     * Returns an existing record or creates a minimal record for a new player.
     * Does <strong>not</strong> mutate an existing record's identity or revision.
     */
    PlayerData ensurePlayer(UUID playerId, String verifiedGameName);

    /**
     * Records a verified login event. Creates the record when one does not exist,
     * then updates {@code lastKnownGameName} and {@code lastSeenAt}.
     */
    PlayerData recordLogin(UUID playerId, String verifiedGameName);

    Optional<PlayerData> find(UUID playerId);

    PlayerData require(UUID playerId);

    PlayerData updateProfile(
            UUID playerId,
            long expectedRevision,
            PlayerProfileUpdate update
    );

    void recordLogout(UUID playerId);
}
