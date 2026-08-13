package com.fontainerepublic.server.justice.persistence;

/**
 * Fail-closed justice codec failure (FR-JUS-001-A §3.4).
 *
 * <p>Thrown when a persisted justice namespace cannot be decoded with
 * authority: unknown fields, wrong NBT types, newer versions, non-canonical
 * ids, broken referential integrity (evidence or verdict bound to a missing
 * case), or inconsistent pipeline state. A rejected load disables the
 * namespace fail-closed; nothing is ever auto-repaired.</p>
 */
public final class JusticeNbtException extends IllegalArgumentException {

    public JusticeNbtException(String message) {
        super(message);
    }

    public JusticeNbtException(String message, Throwable cause) {
        super(message, cause);
    }
}
