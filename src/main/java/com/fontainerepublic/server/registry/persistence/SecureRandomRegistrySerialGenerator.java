package com.fontainerepublic.server.registry.persistence;

import java.security.SecureRandom;

/**
 * Production cryptographically strong serial source (FR-ID-001-A §4.2).
 *
 * <p>Never returns a negative value in practice; a real exhaustion is not
 * reachable with {@link SecureRandom}, so allocation exhaustion remains a
 * bounded-failure contract that tests exercise with injected generators.</p>
 */
public final class SecureRandomRegistrySerialGenerator implements RegistrySerialGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public int nextSerial() {
        return random.nextInt(MAX_SERIAL + 1);
    }
}
