package com.fontainerepublic.server.emergency.api;

import com.fontainerepublic.server.emergency.model.EmergencyActorSource;
import com.fontainerepublic.server.emergency.model.EmergencyConfigPhase;

import java.util.Map;
import java.util.Objects;

/**
 * Restricted projection of the shared emergency state (FR-EMG-001-A §10.2
 * inspect matrix). Never exposes raw tokens, reasons, parameters, or secrets.
 *
 * @param phase             UNSET / STAGED / ACTIVE
 * @param activeUuidDigest  hex digest of the running authority UUID (ACTIVE)
 * @param configRevision    current configuration revision
 * @param driftDetected     whether authority configuration drift is present
 * @param recordCount       journal record count
 * @param journalHeadDigest hex digest of the last journal record
 * @param providerWatermarks per-provider reconciliation watermarks (id ->
 *                           status)
 */
public record EmergencyStatus(
        EmergencyConfigPhase phase,
        String activeUuidDigest,
        long configRevision,
        boolean driftDetected,
        int recordCount,
        String journalHeadDigest,
        Map<String, String> providerWatermarks
) {

    public EmergencyStatus {
        phase = Objects.requireNonNull(phase, "phase");
        if (configRevision < 0) {
            throw new IllegalArgumentException("configRevision must not be negative");
        }
        if (recordCount < 0) {
            throw new IllegalArgumentException("recordCount must not be negative");
        }
        providerWatermarks = Map.copyOf(providerWatermarks);
    }
}
