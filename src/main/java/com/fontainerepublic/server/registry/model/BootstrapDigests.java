package com.fontainerepublic.server.registry.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic SHA-256 helpers for the bootstrap attempt digest chain
 * (FR-ID-BOOTSTRAP-001-A §4).
 *
 * <p>The target UUID is never stored in clear form in trail records: only its
 * SHA-256 digest is persisted, and the reason is likewise stored only as a
 * digest. The per-attempt {@code selfDigest} is computed over a fixed canonical
 * byte encoding of all record fields so equivalent records always produce
 * identical digests and any alteration breaks the chain.</p>
 */
public final class BootstrapDigests {

    public static final int DIGEST_LENGTH = 32;

    /** All-zero digest used as the {@code prevDigest} of the first attempt. */
    public static final byte[] ZERO_DIGEST = new byte[DIGEST_LENGTH];

    private static final HexFormat HEX = HexFormat.of();

    private BootstrapDigests() {
    }

    /**
     * SHA-256 digest of a canonical (lowercase hyphenated) UUID string's UTF-8
     * bytes. Rejects non-canonical inputs.
     */
    public static byte[] uuidDigest(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        String canonical = uuid.toString();
        if (!canonical.equals(canonical.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("UUID must be canonical: " + canonical);
        }
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 digest of the reason's UTF-8 bytes. */
    public static byte[] reasonDigest(String reason) {
        Objects.requireNonNull(reason, "reason");
        return sha256(reason.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes the canonical self-digest of one attempt record over its fixed
     * ordered field encoding (never the {@code selfDigest} field itself).
     */
    public static byte[] selfDigest(
            UUID attemptId,
            long at,
            BootstrapSourceClassification source,
            BootstrapAttemptResult result,
            byte[] uuidDigest,
            byte[] reasonDigest,
            byte[] prevDigest,
            String idempotencyKey
    ) {
        Objects.requireNonNull(attemptId, "attemptId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(uuidDigest, "uuidDigest");
        Objects.requireNonNull(reasonDigest, "reasonDigest");
        Objects.requireNonNull(prevDigest, "prevDigest");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (uuidDigest.length != DIGEST_LENGTH
                || reasonDigest.length != DIGEST_LENGTH
                || prevDigest.length != DIGEST_LENGTH) {
            throw new IllegalArgumentException("Bootstrap digests must be exactly "
                    + DIGEST_LENGTH + " bytes");
        }
        String canonical = attemptId + "|" + at + "|" + source.name() + "|"
                + result.name() + "|" + HEX.formatHex(uuidDigest) + "|"
                + HEX.formatHex(reasonDigest) + "|" + HEX.formatHex(prevDigest) + "|"
                + idempotencyKey;
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
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
