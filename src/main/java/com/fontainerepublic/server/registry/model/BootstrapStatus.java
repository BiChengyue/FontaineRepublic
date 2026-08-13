package com.fontainerepublic.server.registry.model;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only projection of the bootstrap state and trail for the
 * {@code /fr admin bootstrap status} command
 * (FR-ID-BOOTSTRAP-001-A §3).
 *
 * <p>Exposes no raw UUID, no reason, and no FR-EMG information — only the
 * phase, the safe digest of the bound UUID, the bound-at timestamp, the trail
 * head digest, and attempt counts.</p>
 */
public record BootstrapStatus(
        BootstrapPhase phase,
        byte[] boundUuidDigest,
        long boundAt,
        byte[] trailHeadDigest,
        int attemptCount,
        Optional<BootstrapAttemptResult> lastResult
) {

    public BootstrapStatus {
        phase = Objects.requireNonNull(phase, "phase");
        attemptCount = Objects.requireNonNull(attemptCount, "attemptCount");
        lastResult = Objects.requireNonNull(lastResult, "lastResult");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must not be negative");
        }
        if (boundUuidDigest != null
                && boundUuidDigest.length != BootstrapDigests.DIGEST_LENGTH) {
            throw new IllegalArgumentException(
                    "boundUuidDigest must be exactly "
                            + BootstrapDigests.DIGEST_LENGTH + " bytes"
            );
        }
        if (trailHeadDigest != null
                && trailHeadDigest.length != BootstrapDigests.DIGEST_LENGTH) {
            throw new IllegalArgumentException(
                    "trailHeadDigest must be exactly "
                            + BootstrapDigests.DIGEST_LENGTH + " bytes"
            );
        }
    }

    public boolean isBound() {
        return phase == BootstrapPhase.BOUND;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BootstrapStatus that)) {
            return false;
        }
        return phase == that.phase
                && boundAt == that.boundAt
                && attemptCount == that.attemptCount
                && lastResult.equals(that.lastResult)
                && Arrays.equals(boundUuidDigest, that.boundUuidDigest)
                && Arrays.equals(trailHeadDigest, that.trailHeadDigest);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(phase, boundAt, attemptCount, lastResult);
        result = 31 * result + Arrays.hashCode(boundUuidDigest);
        result = 31 * result + Arrays.hashCode(trailHeadDigest);
        return result;
    }
}
