package com.fontainerepublic.server.emergency.service;

import com.fontainerepublic.server.emergency.model.EmergencyDigests;

import java.util.Optional;
import java.util.UUID;

/**
 * Candidate authority-configuration source (FR-EMG-001-A §4).
 *
 * <p>The shared emergency namespace stores the last accepted value (digest)
 * and configuration revision; the owner loads a candidate value before
 * emergency service activation and compares its canonical digest with the
 * accepted value. Merely editing a Forge configuration file is not an
 * authorized change — only the audited service operations (bootstrap /
 * staged change / recovery) advance the durable configuration state. Drift
 * (candidate digest mismatch) fails closed: no player UUID receives
 * emergency authority until the real local console recovers.</p>
 */
public interface EmergencyAuthorityConfigSource {

    /**
     * The candidate configured Hydro Archon UUID digest, if a value is
     * configured. The raw UUID is never exposed by this interface.
     */
    Optional<byte[]> candidateUuidDigest();

    /**
     * Whether a candidate value is currently configured.
     */
    default boolean isConfigured() {
        return candidateUuidDigest().isPresent();
    }

    /**
     * Reads the raw candidate UUID for the inspect surface. Implementations
     * must not store or log the raw value beyond their configured backing.
     */
    Optional<UUID> candidateUuid();

    /** Digest of the raw candidate, if configured. */
    default Optional<byte[]> digestOf(Optional<UUID> uuid) {
        return uuid.map(EmergencyDigests::uuidDigest);
    }
}
