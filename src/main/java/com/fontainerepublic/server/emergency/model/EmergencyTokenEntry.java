package com.fontainerepublic.server.emergency.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * One runtime confirmation-token entry (FR-EMG-001-A §8.2).
 *
 * <p>Entries are server-runtime memory owned exclusively by the shared
 * emergency service on the logical server main thread — never client state
 * and never SavedData. Each entry carries the SHA-256 digest of a
 * high-entropy plaintext token (the plaintext is presented once and never
 * persisted or logged), the server-instance epoch, the journal attempt id,
 * the complete canonical request binding, an expiry, and exactly one
 * lifecycle state.</p>
 *
 * @param tokenDigest SHA-256 of the plaintext token (digest-only storage)
 * @param epoch       server-instance epoch that issued the token
 * @param attemptId   journal attempt identity bound to the token
 * @param binding     complete canonical request binding
 * @param expiresAt   epoch millis after which the entry is expired
 * @param state       ISSUED / CLAIMED / CONSUMED
 */
public record EmergencyTokenEntry(
        byte[] tokenDigest,
        long epoch,
        long attemptId,
        EmergencyTokenBinding binding,
        long expiresAt,
        EmergencyTokenState state
) {

    public EmergencyTokenEntry {
        if (tokenDigest == null || tokenDigest.length != EmergencyDigests.DIGEST_LENGTH) {
            throw new IllegalArgumentException(
                    "tokenDigest must be exactly " + EmergencyDigests.DIGEST_LENGTH
                            + " bytes"
            );
        }
        if (epoch <= 0) {
            throw new IllegalArgumentException("epoch must be positive");
        }
        if (attemptId <= 0) {
            throw new IllegalArgumentException("attemptId must be positive");
        }
        binding = Objects.requireNonNull(binding, "binding");
        if (expiresAt <= 0) {
            throw new IllegalArgumentException("expiresAt must be positive");
        }
        state = Objects.requireNonNull(state, "state");
    }

    /** Returns this entry with the given lifecycle state (single-use flow). */
    public EmergencyTokenEntry withState(EmergencyTokenState nextState) {
        return new EmergencyTokenEntry(
                tokenDigest, epoch, attemptId, binding, expiresAt, nextState
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EmergencyTokenEntry that)) {
            return false;
        }
        return epoch == that.epoch
                && attemptId == that.attemptId
                && expiresAt == that.expiresAt
                && binding.equals(that.binding)
                && state == that.state
                && Arrays.equals(tokenDigest, that.tokenDigest);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(epoch, attemptId, binding, expiresAt, state);
        result = 31 * result + Arrays.hashCode(tokenDigest);
        return result;
    }
}
