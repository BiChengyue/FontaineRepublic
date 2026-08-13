package com.fontainerepublic.server.institutionaccess.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Deterministic integrity digest for an anchored terminal position
 * (FR-INST-001-A §6.2 anti-clone).
 *
 * <p>The digest is a lowercase hex SHA-256 over the canonical position
 * rendering {@code dimension|x|y|z}. It binds the stored position to the
 * terminal record so that any NBT tampering with the position without
 * recomputing the digest fails closed on decode and at context validation.
 * It carries no coordinates by itself and is never used for spatial
 * decisions.</p>
 */
public final class IntegrityDigest {

    private IntegrityDigest() {
    }

    public static String of(String dimension, int x, int y, int z) {
        Objects.requireNonNull(dimension, "dimension");
        String canonical = dimension + '|' + x + '|' + y + '|' + z;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    canonical.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
