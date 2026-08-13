package com.fontainerepublic.server.emergency.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Durable authority-configuration snapshot of the shared emergency namespace
 * (FR-EMG-001-A §4 lifecycle).
 *
 * <p>Only the SHA-256 digest of the configured Hydro Archon UUID is stored —
 * never the raw UUID. {@code configRevision} advances on every accepted
 * configuration event (bootstrap, staged acceptance, recovery) so the staged
 * change can be verified at the next server start; a mismatch, missing
 * receipt, rollback, or manual file drift fails closed (no player UUID
 * authority).</p>
 *
 * @param phase             UNSET / STAGED / ACTIVE
 * @param activeUuidDigest  SHA-256 of the running authority UUID; null unless
 *                          ACTIVE
 * @param activeAt          epoch millis when the running snapshot was accepted;
 *                          0 unless ACTIVE
 * @param activeRevision    configuration revision of the running snapshot
 * @param stagedUuidDigest  SHA-256 of the staged next UUID; null unless STAGED
 * @param stagedAt          epoch millis when the change was staged; 0 unless
 *                          STAGED
 * @param stagedRevision    configuration revision of the staged change
 * @param configRevision    monotonic configuration-event counter
 * @param journalHeadDigest SHA-256 of the last journal record; null when the
 *                          journal is empty
 */
public record EmergencyConfigState(
        EmergencyConfigPhase phase,
        byte[] activeUuidDigest,
        long activeAt,
        long activeRevision,
        byte[] stagedUuidDigest,
        long stagedAt,
        long stagedRevision,
        long configRevision,
        byte[] journalHeadDigest
) {

    /** Fresh namespace before any authority configuration event. */
    public static final EmergencyConfigState INITIAL = new EmergencyConfigState(
            EmergencyConfigPhase.UNSET,
            null,
            0L,
            0L,
            null,
            0L,
            0L,
            0L,
            null
    );

    public EmergencyConfigState {
        phase = Objects.requireNonNull(phase, "phase");
        if (phase == EmergencyConfigPhase.ACTIVE) {
            requireDigest(activeUuidDigest, "activeUuidDigest");
            if (activeAt <= 0) {
                throw new IllegalArgumentException(
                        "An ACTIVE config state must carry a positive activeAt"
                );
            }
            if (activeRevision <= 0) {
                throw new IllegalArgumentException(
                        "An ACTIVE config state must carry a positive activeRevision"
                );
            }
            if (stagedUuidDigest != null || stagedAt != 0 || stagedRevision != 0) {
                throw new IllegalArgumentException(
                        "An ACTIVE config state cannot carry staged fields"
                );
            }
        } else {
            if (activeUuidDigest != null || activeAt != 0 || activeRevision != 0) {
                throw new IllegalArgumentException(
                        "A non-ACTIVE config state cannot carry active fields"
                );
            }
        }
        if (phase == EmergencyConfigPhase.STAGED) {
            requireDigest(stagedUuidDigest, "stagedUuidDigest");
            if (stagedAt <= 0) {
                throw new IllegalArgumentException(
                        "A STAGED config state must carry a positive stagedAt"
                );
            }
            if (stagedRevision <= 0) {
                throw new IllegalArgumentException(
                        "A STAGED config state must carry a positive stagedRevision"
                );
            }
        } else {
            if (stagedUuidDigest != null || stagedAt != 0 || stagedRevision != 0) {
                throw new IllegalArgumentException(
                        "A non-STAGED config state cannot carry staged fields"
                );
            }
        }
        if (configRevision < 0) {
            throw new IllegalArgumentException("configRevision must not be negative");
        }
        if (journalHeadDigest != null && journalHeadDigest.length != EmergencyDigests.DIGEST_LENGTH) {
            throw new IllegalArgumentException(
                    "journalHeadDigest must be exactly "
                            + EmergencyDigests.DIGEST_LENGTH + " bytes"
            );
        }
    }

    /** Builds the ACTIVE snapshot after a bootstrap or staged acceptance. */
    public static EmergencyConfigState active(
            byte[] uuidDigest,
            long acceptedAt,
            long revision,
            byte[] journalHeadDigest
    ) {
        return new EmergencyConfigState(
                EmergencyConfigPhase.ACTIVE,
                uuidDigest,
                acceptedAt,
                revision,
                null,
                0L,
                0L,
                revision,
                journalHeadDigest
        );
    }

    /** Builds the STAGED snapshot after a controlled configuration change. */
    public static EmergencyConfigState staged(
            byte[] stagedUuidDigest,
            long stagedAt,
            long revision,
            byte[] journalHeadDigest
    ) {
        return new EmergencyConfigState(
                EmergencyConfigPhase.STAGED,
                null,
                0L,
                0L,
                stagedUuidDigest,
                stagedAt,
                revision,
                revision,
                journalHeadDigest
        );
    }

    /** Returns this state with a new journal-head digest. */
    public EmergencyConfigState withJournalHead(byte[] newHeadDigest) {
        return new EmergencyConfigState(
                phase,
                activeUuidDigest,
                activeAt,
                activeRevision,
                stagedUuidDigest,
                stagedAt,
                stagedRevision,
                configRevision,
                newHeadDigest
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EmergencyConfigState that)) {
            return false;
        }
        return phase == that.phase
                && activeAt == that.activeAt
                && activeRevision == that.activeRevision
                && stagedAt == that.stagedAt
                && stagedRevision == that.stagedRevision
                && configRevision == that.configRevision
                && Arrays.equals(activeUuidDigest, that.activeUuidDigest)
                && Arrays.equals(stagedUuidDigest, that.stagedUuidDigest)
                && Arrays.equals(journalHeadDigest, that.journalHeadDigest);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(phase, activeAt, activeRevision, stagedAt,
                stagedRevision, configRevision);
        result = 31 * result + Arrays.hashCode(activeUuidDigest);
        result = 31 * result + Arrays.hashCode(stagedUuidDigest);
        result = 31 * result + Arrays.hashCode(journalHeadDigest);
        return result;
    }

    private static void requireDigest(byte[] digest, String field) {
        if (digest == null || digest.length != EmergencyDigests.DIGEST_LENGTH) {
            throw new IllegalArgumentException(
                    field + " must be exactly " + EmergencyDigests.DIGEST_LENGTH
                            + " bytes"
            );
        }
    }
}
