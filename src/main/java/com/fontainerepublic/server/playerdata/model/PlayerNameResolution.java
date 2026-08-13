package com.fontainerepublic.server.playerdata.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Typed, closed result of {@code PlayerDirectoryService.resolveExactGameName}
 * (FR-DATA-003-A §4.1).
 *
 * <p>Only {@link PlayerNameResolutionKind#UNIQUE_CURRENT} carries a UUID. The
 * bounded public feedback contract merges {@code UNKNOWN}, {@code RETIRED},
 * and {@code AMBIGUOUS} into one message ({@link #GENERIC_NON_RESOLUTION_MESSAGE})
 * to reduce existence and rename-history probing; malformed syntax may be
 * distinguished via {@link #publicMessage()}.</p>
 */
public record PlayerNameResolution(
        PlayerNameResolutionKind kind,
        Optional<UUID> playerId
) {
    /** Bounded public message shared by UNKNOWN, RETIRED, and AMBIGUOUS. */
    public static final String GENERIC_NON_RESOLUTION_MESSAGE =
            "Player name cannot be resolved uniquely.";
    private static final String INVALID_INPUT_MESSAGE =
            "Player name is not a valid game name.";

    public PlayerNameResolution {
        kind = Objects.requireNonNull(kind, "kind");
        playerId = Objects.requireNonNull(playerId, "playerId");
        if (kind == PlayerNameResolutionKind.UNIQUE_CURRENT) {
            if (playerId.isEmpty()) {
                throw new IllegalArgumentException(
                        "UNIQUE_CURRENT requires a player UUID"
                );
            }
        } else if (playerId.isPresent()) {
            throw new IllegalArgumentException(
                    kind + " must not carry a player UUID"
            );
        }
    }

    public static PlayerNameResolution uniqueCurrent(UUID playerId) {
        return new PlayerNameResolution(
                PlayerNameResolutionKind.UNIQUE_CURRENT,
                Optional.of(Objects.requireNonNull(playerId, "playerId"))
        );
    }

    public static PlayerNameResolution unknown() {
        return new PlayerNameResolution(PlayerNameResolutionKind.UNKNOWN, Optional.empty());
    }

    public static PlayerNameResolution retired() {
        return new PlayerNameResolution(PlayerNameResolutionKind.RETIRED, Optional.empty());
    }

    public static PlayerNameResolution ambiguous() {
        return new PlayerNameResolution(PlayerNameResolutionKind.AMBIGUOUS, Optional.empty());
    }

    public static PlayerNameResolution invalidInput() {
        return new PlayerNameResolution(PlayerNameResolutionKind.INVALID_INPUT, Optional.empty());
    }

    /**
     * Bounded public feedback: {@code UNIQUE_CURRENT} returns an empty string
     * (callers use the UUID through their own authorized path);
     * {@code UNKNOWN}/{@code RETIRED}/{@code AMBIGUOUS} share the same generic
     * message; {@code INVALID_INPUT} may be distinguished as malformed syntax.
     */
    public String publicMessage() {
        return switch (kind) {
            case UNIQUE_CURRENT -> "";
            case INVALID_INPUT -> INVALID_INPUT_MESSAGE;
            case UNKNOWN, RETIRED, AMBIGUOUS -> GENERIC_NON_RESOLUTION_MESSAGE;
        };
    }
}
