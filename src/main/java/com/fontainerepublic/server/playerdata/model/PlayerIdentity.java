package com.fontainerepublic.server.playerdata.model;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stable, server-observed identity for one Minecraft account.
 */
public record PlayerIdentity(
        UUID playerId,
        String lastKnownGameName,
        long firstSeenAt,
        long lastSeenAt
) {
    private static final Pattern GAME_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public PlayerIdentity {
        playerId = Objects.requireNonNull(playerId, "playerId");
        lastKnownGameName = Objects.requireNonNull(
                lastKnownGameName,
                "lastKnownGameName"
        ).trim();
        if (!GAME_NAME.matcher(lastKnownGameName).matches()) {
            throw new IllegalArgumentException(
                    "lastKnownGameName must contain 1-16 letters, digits, or underscores"
            );
        }
        if (firstSeenAt < 0) {
            throw new IllegalArgumentException("firstSeenAt must not be negative");
        }
        if (lastSeenAt < firstSeenAt) {
            throw new IllegalArgumentException("lastSeenAt must not precede firstSeenAt");
        }
    }

    public PlayerIdentity observedAs(String gameName, long observedAt) {
        return new PlayerIdentity(
                playerId,
                gameName,
                firstSeenAt,
                Math.max(lastSeenAt, observedAt)
        );
    }
}
