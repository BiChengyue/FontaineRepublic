package com.fontainerepublic.server.emergency.api;

import java.util.Objects;

/**
 * Outcome of an authority-configuration operation (FR-EMG-001-A §4 lifecycle:
 * bootstrap, staged change, drift detection, console recovery).
 *
 * @param accepted       whether the operation was durably applied
 * @param failureCode    bounded failure code (not accepted)
 * @param summary        safe bounded summary
 * @param configRevision configuration revision after the operation
 */
public record ConfigureResult(
        boolean accepted,
        String failureCode,
        String summary,
        long configRevision
) {

    public ConfigureResult {
        summary = Objects.requireNonNull(summary, "summary");
        if (accepted) {
            if (failureCode != null) {
                throw new IllegalArgumentException(
                        "An accepted configure result cannot carry a failure code"
                );
            }
        } else {
            if (failureCode == null || failureCode.isBlank()) {
                throw new IllegalArgumentException(
                        "A rejected configure result must carry a failure code"
                );
            }
        }
    }

    public static ConfigureResult accepted(String summary, long configRevision) {
        return new ConfigureResult(true, null, summary, configRevision);
    }

    public static ConfigureResult rejected(String failureCode, String summary) {
        return new ConfigureResult(false, failureCode, summary, 0L);
    }
}
