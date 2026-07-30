package com.fontainerepublic.server.command.registration;

/**
 * Explicit failure raised when a command definition or tree violates the frozen contract.
 */
public final class CommandRegistrationException extends RuntimeException {
    public CommandRegistrationException(String message) {
        super(message);
    }

    public CommandRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
