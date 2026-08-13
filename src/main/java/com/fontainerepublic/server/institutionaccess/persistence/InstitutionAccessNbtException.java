package com.fontainerepublic.server.institutionaccess.persistence;

/**
 * Fail-closed decode/validation failure of the {@code institution-access}
 * namespace (FR-INST-002-A §3: strict codec).
 *
 * <p>Thrown when persisted state cannot be validated: unknown fields, wrong
 * NBT types, unsupported versions, non-canonical ids, mismatched keys,
 * inconsistent integrity digests, or capacity violations. The whole load is
 * rejected; nothing is ever auto-repaired.</p>
 */
public final class InstitutionAccessNbtException extends RuntimeException {

    public InstitutionAccessNbtException(String message) {
        super(message);
    }

    public InstitutionAccessNbtException(String message, Throwable cause) {
        super(message, cause);
    }
}
