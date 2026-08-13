package com.fontainerepublic.server.emergency.api;

import java.util.Objects;

/**
 * Typed immutable result of an emergency-action provider mutation
 * (FR-EMG-001-A §5 / §9 business mutation rules).
 *
 * <p>A successful mutation commits exactly one owned business snapshot
 * (business state + authoritative success receipt + pending notification) and
 * increments the relevant revision exactly once. Failure changes no business
 * data and no revision; the shared service then appends the failure journal
 * record.</p>
 *
 * @param applied       whether the business snapshot was durably committed
 * @param failureCode   bounded failure code (not applied)
 * @param summary       safe bounded summary
 * @param revisionLabel canonical label of the affected business revision
 */
public record EmergencyMutationResult(
        boolean applied,
        String failureCode,
        String summary,
        String revisionLabel
) {

    public EmergencyMutationResult {
        summary = Objects.requireNonNull(summary, "summary");
        revisionLabel = Objects.requireNonNull(revisionLabel, "revisionLabel");
        if (applied) {
            if (failureCode != null) {
                throw new IllegalArgumentException(
                        "An applied mutation cannot carry a failure code"
                );
            }
        } else {
            if (failureCode == null || failureCode.isBlank()) {
                throw new IllegalArgumentException(
                        "A failed mutation must carry a failure code"
                );
            }
        }
    }

    public static EmergencyMutationResult applied(String summary, String revisionLabel) {
        return new EmergencyMutationResult(true, null, summary, revisionLabel);
    }

    public static EmergencyMutationResult failed(String failureCode, String revisionLabel) {
        return new EmergencyMutationResult(false, failureCode, "", revisionLabel);
    }
}
