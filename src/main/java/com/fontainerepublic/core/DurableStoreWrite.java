package com.fontainerepublic.core;

/**
 * Outcome of one {@link DurableStore#writeAtomically} attempt (FR-CORE-002).
 *
 * @param ok           true only when the new root is durable and authoritative
 * @param bytesWritten bytes written to the authoritative file (0 on failure)
 * @param failureCode  stable, non-secret failure code; empty on success;
 *                     {@code NON_ATOMIC_REPLACE} signals a successful but
 *                     non-atomic fallback replacement
 */
public record DurableStoreWrite(
        boolean ok,
        long bytesWritten,
        String failureCode
) {
}
