package com.fontainerepublic.server.emergency.service;

import com.fontainerepublic.core.ConfigManager;
import com.fontainerepublic.server.emergency.model.EmergencyDigests;

import java.util.Optional;
import java.util.UUID;

/**
 * Production candidate authority-configuration source backed by
 * {@link ConfigManager} (FR-EMG-001-A §4 authority configuration
 * integration).
 *
 * <p>The Forge config holds the candidate UUID; the shared emergency
 * namespace holds the last accepted digest and revision. The candidate is
 * read at service activation for digest comparison; editing the config file
 * alone is never an authorized change (drift fails closed).</p>
 */
public enum ConfigManagerAuthorityConfigSource implements EmergencyAuthorityConfigSource {
    INSTANCE;

    @Override
    public Optional<byte[]> candidateUuidDigest() {
        return candidateUuid().map(EmergencyDigests::uuidDigest);
    }

    @Override
    public Optional<UUID> candidateUuid() {
        String value = ConfigManager.emergencyHydroArchonUuid();
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                return Optional.empty();
            }
            return Optional.of(parsed);
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
