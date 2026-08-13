package com.fontainerepublic.server.emergency.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic SHA-256 helpers for the shared emergency authority
 * (FR-EMG-001-A §4 / §8 / §10).
 *
 * <p>The configured Hydro Archon UUID and the confirmation plaintext token
 * are never stored in clear form: only their SHA-256 digests are persisted,
 * and the reason is stored only as a digest. Digests are computed over fixed
 * canonical byte encodings so equivalent values always produce identical
 * digests and any alteration breaks the chain.</p>
 */
public final class EmergencyDigests {

    public static final int DIGEST_LENGTH = 32;

    /** All-zero digest used as the prevDigest of the first chain element. */
    public static final byte[] ZERO_DIGEST = new byte[DIGEST_LENGTH];

    private static final HexFormat HEX = HexFormat.of();

    private EmergencyDigests() {
    }

    /** SHA-256 digest of a canonical (lowercase hyphenated) UUID's UTF-8 bytes. */
    public static byte[] uuidDigest(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        String canonical = uuid.toString();
        if (!canonical.equals(canonical.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("UUID must be canonical: " + canonical);
        }
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 digest of a bounded reason's UTF-8 bytes. */
    public static byte[] reasonDigest(String reason) {
        Objects.requireNonNull(reason, "reason");
        return sha256(reason.getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 digest of the canonical token binding/request encoding. */
    public static byte[] requestDigest(byte[] canonicalEncoding) {
        Objects.requireNonNull(canonicalEncoding, "canonicalEncoding");
        return sha256(canonicalEncoding);
    }

    /** SHA-256 of arbitrary bytes. */
    public static byte[] sha256(byte[] input) {
        Objects.requireNonNull(input, "input");
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    /** Lowercase hex representation of a digest (for projections). */
    public static String toHex(byte[] digest) {
        Objects.requireNonNull(digest, "digest");
        return HEX.formatHex(digest);
    }
}
