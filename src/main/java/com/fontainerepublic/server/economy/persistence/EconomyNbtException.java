package com.fontainerepublic.server.economy.persistence;

/**
 * Signals invalid or unsupported economy NBT (FR-ECO-001-A §5.2).
 *
 * <p>Thrown on strict codec violations: unknown fields, wrong NBT types,
 * non-canonical subject keys, negative or overflowing balances, unsupported
 * transaction types, non-positive amounts/revisions, supply inconsistency,
 * or newer store/record versions. The economy store fails closed — it never
 * repairs, reallocates, or infers missing state.</p>
 */
public final class EconomyNbtException extends IllegalArgumentException {
    public EconomyNbtException(String message) {
        super(message);
    }

    public EconomyNbtException(String message, Throwable cause) {
        super(message, cause);
    }
}
