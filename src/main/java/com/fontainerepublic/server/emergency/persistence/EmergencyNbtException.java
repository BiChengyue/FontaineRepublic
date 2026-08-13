package com.fontainerepublic.server.emergency.persistence;

/**
 * Strict NBT decode failure of the shared emergency namespace. Never
 * auto-repaired; the store fails closed.
 */
public final class EmergencyNbtException extends RuntimeException {
    public EmergencyNbtException(String message) {
        super(message);
    }

    public EmergencyNbtException(String message, Throwable cause) {
        super(message, cause);
    }
}
