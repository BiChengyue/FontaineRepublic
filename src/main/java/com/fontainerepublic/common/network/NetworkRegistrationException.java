package com.fontainerepublic.common.network;

/**
 * Startup-fatal invalid message-table definition.
 */
public final class NetworkRegistrationException extends IllegalStateException {
    public NetworkRegistrationException(String message) {
        super(message);
    }

    public NetworkRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
