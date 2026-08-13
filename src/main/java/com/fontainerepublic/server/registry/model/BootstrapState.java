package com.fontainerepublic.server.registry.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Versioned bootstrap metadata of the registry snapshot
 * (FR-ID-001-A §6.2 and FR-ID-BOOTSTRAP-001-A §4).
 *
 * <p>The FR-ID-001 implementation task §3.2 baseline fields
 * ({@code fixedReservationsEstablished}, {@code officeSubjectMaterialized},
 * {@code originalPersonalBindingApplied}) are preserved unchanged. The
 * original-person bootstrap design extends the state with the binding phase,
 * the SHA-256 digest of the bound UUID (never the raw UUID), the bound-at
 * timestamp, and the digest of the last attempt-trail record.</p>
 *
 * <p>Consistency invariants enforced at construction: the
 * {@code originalPersonalBindingApplied} flag agrees with
 * {@code phase == BOUND}; a BOUND state always carries a bound-UUID digest, a
 * positive bound-at timestamp, and a trail-head digest; an UNBOUND state never
 * carries a bound-UUID digest or a bound-at timestamp.</p>
 *
 * <p>It deliberately carries no current Hydro Archon holder UUID and no FR-EMG
 * authority UUID.</p>
 *
 * @param fixedReservationsEstablished    true once both fixed numbers are in
 *                                        the reservation index (required)
 * @param officeSubjectMaterialized       true once the office subject exists
 *                                        (required by this implementation)
 * @param originalPersonalBindingApplied  whether the original personal binding
 *                                        was applied; agrees with
 *                                        {@code phase == BOUND}
 * @param phase                           UNBOUND or BOUND
 * @param boundUuidDigest                 SHA-256 of the canonical bound UUID;
 *                                        null unless BOUND
 * @param boundAt                         server-assigned epoch millis of the
 *                                        committed binding; 0 unless BOUND
 * @param trailHeadDigest                 SHA-256 of the last attempt-trail
 *                                        record; null when the trail is empty
 */
public record BootstrapState(
        boolean fixedReservationsEstablished,
        boolean officeSubjectMaterialized,
        boolean originalPersonalBindingApplied,
        BootstrapPhase phase,
        byte[] boundUuidDigest,
        long boundAt,
        byte[] trailHeadDigest
) {

    /** State of a fresh snapshot before office materialization. */
    public static final BootstrapState INITIAL =
            new BootstrapState(true, false, false, BootstrapPhase.UNBOUND, null, 0L, null);

    /** State after the office subject has been materialized idempotently. */
    public static final BootstrapState OFFICE_MATERIALIZED =
            new BootstrapState(true, true, false, BootstrapPhase.UNBOUND, null, 0L, null);

    public BootstrapState {
        phase = Objects.requireNonNull(phase, "phase");
        if (phase == BootstrapPhase.BOUND) {
            if (!originalPersonalBindingApplied) {
                throw new IllegalArgumentException(
                        "A BOUND bootstrap state must carry originalPersonalBindingApplied=true"
                );
            }
            if (boundUuidDigest == null) {
                throw new IllegalArgumentException(
                        "A BOUND bootstrap state must carry the bound-UUID digest"
                );
            }
            if (boundUuidDigest.length != BootstrapDigests.DIGEST_LENGTH) {
                throw new IllegalArgumentException(
                        "boundUuidDigest must be exactly "
                                + BootstrapDigests.DIGEST_LENGTH + " bytes"
                );
            }
            if (boundAt <= 0) {
                throw new IllegalArgumentException(
                        "A BOUND bootstrap state must carry a positive boundAt"
                );
            }
            if (trailHeadDigest == null) {
                throw new IllegalArgumentException(
                        "A BOUND bootstrap state must carry the trail-head digest"
                );
            }
            if (trailHeadDigest.length != BootstrapDigests.DIGEST_LENGTH) {
                throw new IllegalArgumentException(
                        "trailHeadDigest must be exactly "
                                + BootstrapDigests.DIGEST_LENGTH + " bytes"
                );
            }
        } else {
            if (originalPersonalBindingApplied) {
                throw new IllegalArgumentException(
                        "An UNBOUND bootstrap state cannot claim an applied "
                                + "original personal binding"
                );
            }
            if (boundUuidDigest != null) {
                throw new IllegalArgumentException(
                        "An UNBOUND bootstrap state cannot carry a bound-UUID digest"
                );
            }
            if (boundAt != 0) {
                throw new IllegalArgumentException(
                        "An UNBOUND bootstrap state cannot carry a boundAt"
                );
            }
            if (trailHeadDigest != null && trailHeadDigest.length != BootstrapDigests.DIGEST_LENGTH) {
                throw new IllegalArgumentException(
                        "trailHeadDigest must be exactly "
                                + BootstrapDigests.DIGEST_LENGTH + " bytes"
                );
            }
        }
    }

    /**
     * Builds a BOUND state from a committed original-person binding.
     */
    public static BootstrapState bound(byte[] boundUuidDigest, long boundAt, byte[] trailHeadDigest) {
        return new BootstrapState(
                true,
                true,
                true,
                BootstrapPhase.BOUND,
                boundUuidDigest,
                boundAt,
                trailHeadDigest
        );
    }

    /**
     * Returns this state with a new trail-head digest (attempt trail extended).
     */
    public BootstrapState withTrailHead(byte[] newTrailHeadDigest) {
        return new BootstrapState(
                fixedReservationsEstablished,
                officeSubjectMaterialized,
                originalPersonalBindingApplied,
                phase,
                boundUuidDigest,
                boundAt,
                newTrailHeadDigest
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BootstrapState that)) {
            return false;
        }
        return fixedReservationsEstablished == that.fixedReservationsEstablished
                && officeSubjectMaterialized == that.officeSubjectMaterialized
                && originalPersonalBindingApplied == that.originalPersonalBindingApplied
                && phase == that.phase
                && boundAt == that.boundAt
                && Arrays.equals(boundUuidDigest, that.boundUuidDigest)
                && Arrays.equals(trailHeadDigest, that.trailHeadDigest);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                fixedReservationsEstablished,
                officeSubjectMaterialized,
                originalPersonalBindingApplied,
                phase,
                boundAt
        );
        result = 31 * result + Arrays.hashCode(boundUuidDigest);
        result = 31 * result + Arrays.hashCode(trailHeadDigest);
        return result;
    }
}
