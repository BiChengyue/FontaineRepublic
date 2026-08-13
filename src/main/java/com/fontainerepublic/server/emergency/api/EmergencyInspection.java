package com.fontainerepublic.server.emergency.api;

import java.util.Objects;

/**
 * Restricted redacted projection of one emergency journal record
 * (FR-EMG-001-A §10.2 inspect matrix). Never exposes raw reasons, parameters,
 * or secrets — only safe category/summary or digests.
 *
 * @param recordId      journal record identity
 * @param at            server-assigned epoch millis
 * @param actorType     HYDRO_ARCHON or SERVER_CONSOLE
 * @param kind          journal kind ("emergency.attempt" or
 *                      "emergency.config-event")
 * @param resultName    result code name
 * @param attemptId     shared attempt identity (0 for config events)
 * @param requestDigest hex digest of the canonical request (evidence only)
 */
public record EmergencyInspection(
        long recordId,
        long at,
        String actorType,
        String kind,
        String resultName,
        long attemptId,
        String requestDigest
) {

    public EmergencyInspection {
        actorType = Objects.requireNonNull(actorType, "actorType");
        kind = Objects.requireNonNull(kind, "kind");
        resultName = Objects.requireNonNull(resultName, "resultName");
        requestDigest = Objects.requireNonNull(requestDigest, "requestDigest");
        if (recordId <= 0) {
            throw new IllegalArgumentException("recordId must be positive");
        }
        if (at <= 0) {
            throw new IllegalArgumentException("at must be positive");
        }
    }
}
