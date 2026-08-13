package com.fontainerepublic.server.emergency.service;

import com.fontainerepublic.server.emergency.model.EmergencyDigests;
import com.fontainerepublic.server.emergency.model.EmergencyTokenBinding;
import com.fontainerepublic.server.emergency.model.EmergencyTokenEntry;
import com.fontainerepublic.server.emergency.model.EmergencyTokenState;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-runtime confirmation-token table of the shared emergency service
 * (FR-EMG-001-A §8.2).
 *
 * <p>Owned exclusively by the shared emergency service; never client state
 * and never SavedData. Entries are stored by the SHA-256 digest of a
 * high-entropy plaintext token — the plaintext is presented once and never
 * persisted or logged. Each entry is bound to the server-instance epoch and a
 * complete canonical request binding, with one lifecycle state
 * (ISSUED -> CLAIMED -> CONSUMED) and a 30-second default expiry.</p>
 */
public final class EmergencyTokenTable {

    /** Default confirmation-token lifetime (30 seconds). */
    public static final long DEFAULT_TOKEN_LIFETIME_MILLIS = 30_000L;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, EmergencyTokenEntry> entries =
            new ConcurrentHashMap<>();
    private final long epoch;
    private final long tokenLifetimeMillis;

    public EmergencyTokenTable(long epoch) {
        this(epoch, DEFAULT_TOKEN_LIFETIME_MILLIS);
    }

    public EmergencyTokenTable(long epoch, long tokenLifetimeMillis) {
        if (epoch <= 0) {
            throw new IllegalArgumentException("epoch must be positive");
        }
        if (tokenLifetimeMillis <= 0) {
            throw new IllegalArgumentException("tokenLifetimeMillis must be positive");
        }
        this.epoch = epoch;
        this.tokenLifetimeMillis = tokenLifetimeMillis;
    }

    /** Generates a high-entropy plaintext token and stores its digest. */
    public TokenIssue issue(EmergencyTokenBinding binding, long bindingAttemptId, long now) {
        Objects.requireNonNull(binding, "binding");
        if (bindingAttemptId <= 0) {
            throw new IllegalArgumentException("bindingAttemptId must be positive");
        }
        if (now <= 0) {
            throw new IllegalArgumentException("now must be positive");
        }
        byte[] plaintext = new byte[32];
        random.nextBytes(plaintext);
        String token = HexFormat.of().formatHex(plaintext);
        byte[] digest = EmergencyDigests.sha256(plaintext);
        EmergencyTokenEntry entry = new EmergencyTokenEntry(
                digest,
                epoch,
                bindingAttemptId,
                binding,
                now + tokenLifetimeMillis,
                EmergencyTokenState.ISSUED
        );
        entries.put(HexFormat.of().formatHex(digest), entry);
        return new TokenIssue(token, entry.expiresAt());
    }

    /**
     * Atomically claims a token (ISSUED -> CLAIMED). Returns null when the
     * token is unknown, expired, already claimed, consumed, or from another
     * epoch. The caller revalidates the binding after claiming.
     */
    public EmergencyTokenEntry claim(String token, long now) {
        Objects.requireNonNull(token, "token");
        if (now <= 0) {
            throw new IllegalArgumentException("now must be positive");
        }
        String key = tokenKey(token);
        EmergencyTokenEntry current = entries.get(key);
        if (current == null) {
            return null;
        }
        if (current.epoch() != epoch) {
            return null;
        }
        if (current.expiresAt() <= now) {
            entries.remove(key);
            return null;
        }
        if (current.state() != EmergencyTokenState.ISSUED) {
            return null;
        }
        EmergencyTokenEntry claimed = current.withState(EmergencyTokenState.CLAIMED);
        if (!entries.replace(key, current, claimed)) {
            return null; // concurrent claim lost
        }
        return claimed;
    }

    /** Marks a token permanently consumed (every terminal outcome). */
    public void consume(EmergencyTokenEntry claimed) {
        Objects.requireNonNull(claimed, "claimed");
        String key = tokenKey(HexFormat.of().formatHex(claimed.tokenDigest()));
        entries.computeIfPresent(key, (k, current) ->
                current.state() == EmergencyTokenState.CONSUMED
                        ? current
                        : current.withState(EmergencyTokenState.CONSUMED)
        );
    }

    /** Invalidates all pending entries (server stop / logout). */
    public void invalidateAll() {
        entries.clear();
    }

    /** Number of live token entries (diagnostics only). */
    public int size() {
        return entries.size();
    }

    /** Whether a token entry is currently bound to this table's epoch. */
    public boolean isEpoch(long candidate) {
        return epoch == candidate;
    }

    private static String tokenKey(String token) {
        byte[] plaintext = HexFormat.of().parseHex(token);
        return HexFormat.of().formatHex(EmergencyDigests.sha256(plaintext));
    }

    /** Plaintext token plus expiry returned to the caller exactly once. */
    public record TokenIssue(String token, long expiresAt) {
        public TokenIssue {
            token = Objects.requireNonNull(token, "token");
            if (expiresAt <= 0) {
                throw new IllegalArgumentException("expiresAt must be positive");
            }
        }
    }
}
