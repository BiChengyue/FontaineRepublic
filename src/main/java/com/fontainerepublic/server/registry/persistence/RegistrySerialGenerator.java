package com.fontainerepublic.server.registry.persistence;

/**
 * Injected server-owned six-digit serial source for number allocation
 * (FR-ID-001-A §4.2).
 *
 * <p>Production draws from a cryptographically strong random generator; tests
 * inject deterministic sequences to force collisions and exhaustion. A
 * negative value signals that the underlying source is exhausted (true
 * allocation exhaustion), which is distinct from bounded retry exhaustion.</p>
 */
@FunctionalInterface
public interface RegistrySerialGenerator {

    /** Maximum serial value: {@code 999999} (six digits). */
    int MAX_SERIAL = 999_999;

    /**
     * @return a candidate serial in {@code [0, MAX_SERIAL]}, or a negative
     *         value when the source is exhausted
     */
    int nextSerial();
}
