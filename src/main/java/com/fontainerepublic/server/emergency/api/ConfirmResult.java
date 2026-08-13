package com.fontainerepublic.server.emergency.api;

import java.util.Objects;

/**
 * Outcome of an emergency confirmation (FR-EMG-001-A §8.3).
 *
 * <p>Every terminal outcome permanently consumes the claimed token. Success
 * reports the attempt id and a safe summary; rejection/failure carries a
 * bounded failure code. No business transaction id is fabricated for failed
 * actions.</p>
 *
 * @param success     whether the business mutation was durably applied
 * @param attemptId   the claimed attempt identity; 0 when no attempt was
 *                    allocated (unknown, expired, or already-used token)
 * @param failureCode bounded failure code (not success)
 * @param summary     safe bounded summary
 */
public record ConfirmResult(
        boolean success,
        long attemptId,
        String failureCode,
        String summary
) {

    public ConfirmResult {
        if (attemptId < 0) {
            throw new IllegalArgumentException(
                    "attemptId must not be negative"
            );
        }
        if (success) {
            if (attemptId <= 0) {
                throw new IllegalArgumentException(
                        "A successful confirm must carry a positive attempt id"
                );
            }
            if (failureCode != null) {
                throw new IllegalArgumentException(
                        "A successful confirm cannot carry a failure code"
                );
            }
        } else {
            if (failureCode == null || failureCode.isBlank()) {
                throw new IllegalArgumentException(
                        "A failed confirm must carry a failure code"
                );
            }
        }
        summary = Objects.requireNonNull(summary, "summary");
    }

    public static ConfirmResult success(long attemptId, String summary) {
        return new ConfirmResult(true, attemptId, null, summary);
    }

    public static ConfirmResult failure(long attemptId, String failureCode, String summary) {
        return new ConfirmResult(false, attemptId, failureCode, summary);
    }
}
