package com.fontainerepublic.server.emergency.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * One immutable, self-authenticating record of the shared emergency journal
 * (FR-EMG-001-A §10.3).
 *
 * <p>The journal is authoritative for attempts and configuration events. An
 * {@link EmergencyJournalKind#ATTEMPT} record carries the shared attempt id,
 * the attempt result, and the safe request digest; a
 * {@link EmergencyJournalKind#CONFIG_EVENT} record carries the configuration
 * result and the affected configuration revision. Every record links
 * {@code prevDigest} / {@code selfDigest} so deletion, reordering, or
 * replacement is detectable during reconciliation.</p>
 *
 * <p>{@code selfDigest} is derived deterministically from the other fields at
 * construction, so decoding a tampered record fails closed. Raw reasons,
 * tokens, secrets, and plaintext parameters are never stored — only digests.</p>
 */
public record EmergencyJournalRecord(
        long recordId,
        long at,
        EmergencyActorType actorType,
        byte[] actorUuidDigest,
        EmergencySourceClassification source,
        EmergencyJournalKind kind,
        long attemptId,
        EmergencyAttemptResult attemptResult,
        EmergencyConfigResult configResult,
        byte[] requestDigest,
        long configRevision,
        byte[] prevDigest,
        byte[] selfDigest
) {

    public EmergencyJournalRecord {
        at = requirePositive(at, "timestamp");
        actorType = Objects.requireNonNull(actorType, "actorType");
        source = Objects.requireNonNull(source, "source");
        kind = Objects.requireNonNull(kind, "kind");
        if (kind == EmergencyJournalKind.ATTEMPT) {
            if (attemptId <= 0) {
                throw new IllegalArgumentException(
                        "An ATTEMPT record must carry a positive attempt id"
                );
            }
            if (attemptResult == null) {
                throw new IllegalArgumentException(
                        "An ATTEMPT record must carry an attempt result"
                );
            }
            if (configResult != null) {
                throw new IllegalArgumentException(
                        "An ATTEMPT record cannot carry a config result"
                );
            }
            if (requestDigest == null || requestDigest.length != EmergencyDigests.DIGEST_LENGTH) {
                throw new IllegalArgumentException(
                        "An ATTEMPT record must carry a " + EmergencyDigests.DIGEST_LENGTH
                                + "-byte request digest"
                );
            }
        } else {
            if (configResult == null) {
                throw new IllegalArgumentException(
                        "A CONFIG_EVENT record must carry a config result"
                );
            }
            if (attemptResult != null) {
                throw new IllegalArgumentException(
                        "A CONFIG_EVENT record cannot carry an attempt result"
                );
            }
            if (configRevision <= 0) {
                throw new IllegalArgumentException(
                        "A CONFIG_EVENT record must carry a positive configRevision"
                );
            }
            if (requestDigest != null) {
                throw new IllegalArgumentException(
                        "A CONFIG_EVENT record cannot carry a request digest"
                );
            }
        }
        if (actorUuidDigest != null
                && actorUuidDigest.length != EmergencyDigests.DIGEST_LENGTH) {
            throw new IllegalArgumentException(
                    "actorUuidDigest must be exactly "
                            + EmergencyDigests.DIGEST_LENGTH + " bytes"
            );
        }
        if (prevDigest == null || prevDigest.length != EmergencyDigests.DIGEST_LENGTH) {
            throw new IllegalArgumentException(
                    "prevDigest must be exactly " + EmergencyDigests.DIGEST_LENGTH
                            + " bytes"
            );
        }
        byte[] computed = EmergencyJournalDigests.selfDigest(
                recordId,
                at,
                actorType,
                actorUuidDigest,
                source,
                kind,
                attemptId,
                resultName(attemptResult, configResult),
                requestDigest,
                configRevision,
                prevDigest
        );
        if (selfDigest == null || !Arrays.equals(selfDigest, computed)) {
            throw new IllegalArgumentException(
                    "selfDigest does not match the canonical encoding of the record"
            );
        }
    }

    public static EmergencyJournalRecord attempt(
            long recordId,
            long at,
            EmergencyActorType actorType,
            byte[] actorUuidDigest,
            EmergencySourceClassification source,
            long attemptId,
            EmergencyAttemptResult result,
            byte[] requestDigest,
            byte[] prevDigest
    ) {
        return new EmergencyJournalRecord(
                recordId,
                at,
                actorType,
                actorUuidDigest,
                source,
                EmergencyJournalKind.ATTEMPT,
                attemptId,
                result,
                null,
                requestDigest,
                0L,
                prevDigest,
                EmergencyJournalDigests.selfDigest(
                        recordId,
                        at,
                        actorType,
                        actorUuidDigest,
                        source,
                        EmergencyJournalKind.ATTEMPT,
                        attemptId,
                        result.name(),
                        requestDigest,
                        0L,
                        prevDigest
                )
        );
    }

    public static EmergencyJournalRecord configEvent(
            long recordId,
            long at,
            EmergencyActorType actorType,
            byte[] actorUuidDigest,
            EmergencySourceClassification source,
            EmergencyConfigResult result,
            long configRevision,
            byte[] prevDigest
    ) {
        return new EmergencyJournalRecord(
                recordId,
                at,
                actorType,
                actorUuidDigest,
                source,
                EmergencyJournalKind.CONFIG_EVENT,
                0L,
                null,
                result,
                null,
                configRevision,
                prevDigest,
                EmergencyJournalDigests.selfDigest(
                        recordId,
                        at,
                        actorType,
                        actorUuidDigest,
                        source,
                        EmergencyJournalKind.CONFIG_EVENT,
                        0L,
                        result.name(),
                        null,
                        configRevision,
                        prevDigest
                )
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EmergencyJournalRecord that)) {
            return false;
        }
        return recordId == that.recordId
                && at == that.at
                && actorType == that.actorType
                && source == that.source
                && kind == that.kind
                && attemptId == that.attemptId
                && attemptResult == that.attemptResult
                && configResult == that.configResult
                && configRevision == that.configRevision
                && Arrays.equals(actorUuidDigest, that.actorUuidDigest)
                && Arrays.equals(requestDigest, that.requestDigest)
                && Arrays.equals(prevDigest, that.prevDigest)
                && Arrays.equals(selfDigest, that.selfDigest);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(recordId, at, actorType, source, kind,
                attemptId, attemptResult, configResult, configRevision);
        result = 31 * result + Arrays.hashCode(actorUuidDigest);
        result = 31 * result + Arrays.hashCode(requestDigest);
        result = 31 * result + Arrays.hashCode(prevDigest);
        result = 31 * result + Arrays.hashCode(selfDigest);
        return result;
    }

    /** Canonical result-name of a record (attempt or config event). */
    public static String resultName(EmergencyAttemptResult attempt, EmergencyConfigResult config) {
        return attempt != null ? attempt.name() : config.name();
    }

    private static long requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
