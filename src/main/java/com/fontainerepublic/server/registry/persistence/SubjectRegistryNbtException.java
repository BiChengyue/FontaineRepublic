package com.fontainerepublic.server.registry.persistence;

/**
 * Signals invalid or unsupported subject-registry NBT (FR-ID-001-A §6.3).
 *
 * <p>Thrown on strict codec violations: unknown fields, wrong NBT types,
 * non-canonical ids/numbers, checksum failures, type disagreement, index
 * mismatch, missing fixed reservations, or bootstrap state this implementation
 * does not support. The registry fails closed — it never repairs or
 * reallocates.</p>
 */
public final class SubjectRegistryNbtException extends IllegalArgumentException {
    public SubjectRegistryNbtException(String message) {
        super(message);
    }

    public SubjectRegistryNbtException(String message, Throwable cause) {
        super(message, cause);
    }
}
