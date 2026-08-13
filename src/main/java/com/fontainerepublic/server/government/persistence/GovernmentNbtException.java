package com.fontainerepublic.server.government.persistence;

/**
 * Fail-closed government codec failure (FR-GOV-001-A §3.4).
 *
 * <p>Thrown when a persisted government namespace cannot be decoded with
 * authority: unknown fields, wrong NBT types, newer versions, non-canonical
 * ids, broken referential integrity (a position bound to a missing ministry),
 * or state/office inconsistencies. A rejected load disables the namespace
 * fail-closed; nothing is ever auto-repaired.</p>
 */
public final class GovernmentNbtException extends IllegalArgumentException {

    public GovernmentNbtException(String message) {
        super(message);
    }

    public GovernmentNbtException(String message, Throwable cause) {
        super(message, cause);
    }
}
