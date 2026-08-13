package com.fontainerepublic.server.registry.model;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * One immutable, self-authenticating record of the append-only bootstrap
 * attempt trail (FR-ID-BOOTSTRAP-001-A §4).
 *
 * <p>Every attempt (rejected input, rejected source, incomplete, idempotent
 * replay, persistence failure, and success) appends a terminal record. The
 * record carries no raw UUID and no raw reason — only SHA-256 digests — plus
 * the digest-chain links ({@code prevDigest} / {@code selfDigest}) and the
 * idempotency key (canonical target UUID) that correlates retries and restart
 * recovery.</p>
 *
 * <p>{@code selfDigest} is derived deterministically from the other fields at
 * construction, so decoding a tampered record fails closed.</p>
 *
 * @param attemptId          stable server-assigned attempt identity
 * @param at                 server-assigned epoch millis
 * @param source             classified invocation source
 * @param resultCode         PENDING or a terminal result code
 * @param uuidDigest         SHA-256 of the canonical target UUID
 * @param reasonDigest       SHA-256 of the operator reason
 * @param prevDigest         selfDigest of the previous attempt (all-zero for
 *                           the first)
 * @param selfDigest         digest of this record (derived)
 * @param idempotencyKey     canonical target UUID correlating retries
 */
public record BootstrapAttemptRecord(
        UUID attemptId,
        long at,
        BootstrapSourceClassification source,
        BootstrapAttemptResult resultCode,
        byte[] uuidDigest,
        byte[] reasonDigest,
        byte[] prevDigest,
        byte[] selfDigest,
        String idempotencyKey
) {

    public BootstrapAttemptRecord {
        attemptId = Objects.requireNonNull(attemptId, "attemptId");
        source = Objects.requireNonNull(source, "source");
        resultCode = Objects.requireNonNull(resultCode, "resultCode");
        uuidDigest = Objects.requireNonNull(uuidDigest, "uuidDigest");
        reasonDigest = Objects.requireNonNull(reasonDigest, "reasonDigest");
        prevDigest = Objects.requireNonNull(prevDigest, "prevDigest");
        idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (at <= 0) {
            throw new IllegalArgumentException("Bootstrap attempt timestamp must be positive");
        }
        if (uuidDigest.length != BootstrapDigests.DIGEST_LENGTH
                || reasonDigest.length != BootstrapDigests.DIGEST_LENGTH
                || prevDigest.length != BootstrapDigests.DIGEST_LENGTH) {
            throw new IllegalArgumentException(
                    "Bootstrap digests must be exactly "
                            + BootstrapDigests.DIGEST_LENGTH + " bytes"
            );
        }
        if (!idempotencyKey.equals(idempotencyKey.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Idempotency key must be a canonical UUID string: " + idempotencyKey
            );
        }
        byte[] computed = BootstrapDigests.selfDigest(
                attemptId,
                at,
                source,
                resultCode,
                uuidDigest,
                reasonDigest,
                prevDigest,
                idempotencyKey
        );
        if (selfDigest == null || !Arrays.equals(selfDigest, computed)) {
            throw new IllegalArgumentException(
                    "selfDigest does not match the canonical encoding of the attempt"
            );
        }
    }

    /**
     * Builds an attempt computing its selfDigest from the remaining fields.
     */
    public static BootstrapAttemptRecord of(
            UUID attemptId,
            long at,
            BootstrapSourceClassification source,
            BootstrapAttemptResult resultCode,
            byte[] uuidDigest,
            byte[] reasonDigest,
            byte[] prevDigest,
            String idempotencyKey
    ) {
        byte[] selfDigest = BootstrapDigests.selfDigest(
                attemptId,
                at,
                source,
                resultCode,
                uuidDigest,
                reasonDigest,
                prevDigest,
                idempotencyKey
        );
        return new BootstrapAttemptRecord(
                attemptId,
                at,
                source,
                resultCode,
                uuidDigest,
                reasonDigest,
                prevDigest,
                selfDigest,
                idempotencyKey
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BootstrapAttemptRecord that)) {
            return false;
        }
        return attemptId.equals(that.attemptId)
                && at == that.at
                && source == that.source
                && resultCode == that.resultCode
                && Arrays.equals(uuidDigest, that.uuidDigest)
                && Arrays.equals(reasonDigest, that.reasonDigest)
                && Arrays.equals(prevDigest, that.prevDigest)
                && Arrays.equals(selfDigest, that.selfDigest)
                && idempotencyKey.equals(that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(attemptId, at, source, resultCode, idempotencyKey);
        result = 31 * result + Arrays.hashCode(uuidDigest);
        result = 31 * result + Arrays.hashCode(reasonDigest);
        result = 31 * result + Arrays.hashCode(prevDigest);
        result = 31 * result + Arrays.hashCode(selfDigest);
        return result;
    }
}
