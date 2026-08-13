package com.fontainerepublic.server.emergency.api;

import java.util.Objects;

/**
 * Outcome of an emergency preview (FR-EMG-001-A §8.1).
 *
 * <p>A successful preview returns the single-use plaintext confirmation token
 * plus its expiry; the plaintext is presented once and never persisted or
 * logged. A rejection returns a bounded failure code and safe summary.</p>
 *
 * @param accepted     whether a confirmation token was issued
 * @param token        the single-use plaintext token (accepted only)
 * @param expiresAt    epoch millis of the token expiry (accepted only)
 * @param attemptId    stable attempt identity recorded in the journal
 * @param failureCode  bounded failure code (rejected only)
 * @param summary      safe bounded summary of the intended result
 */
public record PreviewResult(
        boolean accepted,
        String token,
        long expiresAt,
        long attemptId,
        String failureCode,
        String summary
) {

    public PreviewResult {
        if (accepted) {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException(
                        "An accepted preview must carry the plaintext token"
                );
            }
            if (expiresAt <= 0) {
                throw new IllegalArgumentException(
                        "An accepted preview must carry a positive expiry"
                );
            }
            if (attemptId <= 0) {
                throw new IllegalArgumentException(
                        "An accepted preview must carry a positive attempt id"
                );
            }
        } else {
            if (token != null || expiresAt != 0 || attemptId != 0) {
                throw new IllegalArgumentException(
                        "A rejected preview cannot carry token/expiry/attempt fields"
                );
            }
            if (failureCode == null || failureCode.isBlank()) {
                throw new IllegalArgumentException(
                        "A rejected preview must carry a failure code"
                );
            }
        }
        summary = Objects.requireNonNull(summary, "summary");
    }

    public static PreviewResult accepted(
            String token, long expiresAt, long attemptId, String summary
    ) {
        return new PreviewResult(true, token, expiresAt, attemptId, null, summary);
    }

    public static PreviewResult rejected(String failureCode, String summary) {
        return new PreviewResult(false, null, 0L, 0L, failureCode, summary);
    }
}
