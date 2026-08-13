package com.fontainerepublic.server.emergency.api;

import java.util.Map;
import java.util.Objects;

/**
 * Typed immutable plan produced by an emergency-action provider's preview
 * capability (FR-EMG-001-A §5 / §8). Side-effect-free validation; the plan is
 * the only input the mutation capability accepts.
 *
 * @param accepted      whether the preview validated the request
 * @param summary       safe bounded summary of the intended state change
 * @param revisionLabel canonical label of the affected business revision
 * @param failureCode   bounded failure code (rejected only)
 */
public record EmergencyPlan(
        boolean accepted,
        String summary,
        String revisionLabel,
        String failureCode
) {

    public EmergencyPlan {
        summary = Objects.requireNonNull(summary, "summary");
        revisionLabel = Objects.requireNonNull(revisionLabel, "revisionLabel");
        if (accepted) {
            if (failureCode != null) {
                throw new IllegalArgumentException(
                        "An accepted plan cannot carry a failure code"
                );
            }
            if (summary.isEmpty()) {
                throw new IllegalArgumentException(
                        "An accepted plan must carry a non-empty summary"
                );
            }
        } else {
            if (failureCode == null || failureCode.isBlank()) {
                throw new IllegalArgumentException(
                        "A rejected plan must carry a failure code"
                );
            }
        }
    }

    public static EmergencyPlan accepted(String summary, String revisionLabel) {
        return new EmergencyPlan(true, summary, revisionLabel, null);
    }

    public static EmergencyPlan rejected(String failureCode, String revisionLabel) {
        return new EmergencyPlan(false, "", revisionLabel, failureCode);
    }
}
