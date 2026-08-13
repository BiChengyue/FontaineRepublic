package com.fontainerepublic.server.citizen.persistence;

/**
 * Signals invalid or unsupported citizen NBT (FR-CIT-001-A §3.3).
 *
 * <p>Thrown on strict codec violations: unknown fields, wrong NBT types,
 * non-canonical player ids, invalid or missing subject ids, unsupported
 * status/rank values, non-positive revisions, or newer store/record versions.
 * The citizen store fails closed — it never repairs, reallocates, or infers
 * missing state.</p>
 */
public final class CitizenNbtException extends IllegalArgumentException {
    public CitizenNbtException(String message) {
        super(message);
    }

    public CitizenNbtException(String message, Throwable cause) {
        super(message, cause);
    }
}
