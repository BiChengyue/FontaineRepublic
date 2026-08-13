package com.fontainerepublic.server.parliament.persistence;

/**
 * Fail-closed parliament codec failure (FR-PAR-001-A §3.4).
 *
 * <p>Thrown when a persisted parliament namespace cannot be decoded with
 * authority: unknown fields, wrong NBT types, newer versions, non-canonical
 * ids, broken referential integrity (a vote or bill bound to a missing
 * proposal), or inconsistent ballot state. A rejected load disables the
 * namespace fail-closed; nothing is ever auto-repaired.</p>
 */
public final class ParliamentNbtException extends IllegalArgumentException {

    public ParliamentNbtException(String message) {
        super(message);
    }

    public ParliamentNbtException(String message, Throwable cause) {
        super(message, cause);
    }
}
