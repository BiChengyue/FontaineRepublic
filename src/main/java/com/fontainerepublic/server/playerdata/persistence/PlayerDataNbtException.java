package com.fontainerepublic.server.playerdata.persistence;

/**
 * Signals invalid or unsupported player-data NBT.
 */
public final class PlayerDataNbtException extends IllegalArgumentException {
    public PlayerDataNbtException(String message) {
        super(message);
    }

    public PlayerDataNbtException(String message, Throwable cause) {
        super(message, cause);
    }
}
