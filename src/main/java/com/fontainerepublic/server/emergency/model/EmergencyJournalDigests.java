package com.fontainerepublic.server.emergency.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Canonical SHA-256 helpers for the shared emergency journal records
 * (FR-EMG-001-A §10.2 tamper-evidence chain).
 *
 * <p>The self digest is computed over a fixed canonical byte encoding of all
 * record fields so equivalent records always produce identical digests and
 * any alteration breaks the chain. Only digests are persisted: never raw
 * tokens, reasons, or parameters.</p>
 */
public final class EmergencyJournalDigests {

    private static final HexFormat HEX = HexFormat.of();

    private EmergencyJournalDigests() {
    }

    /**
     * Computes the canonical self digest of one journal record over its fixed
     * ordered field encoding (never the selfDigest field itself).
     */
    public static byte[] selfDigest(
            long recordId,
            long at,
            EmergencyActorType actorType,
            byte[] actorUuidDigest,
            EmergencySourceClassification source,
            EmergencyJournalKind kind,
            long attemptId,
            String resultName,
            byte[] requestDigest,
            long configRevision,
            byte[] prevDigest
    ) {
        Objects.requireNonNull(actorType, "actorType");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(resultName, "resultName");
        Objects.requireNonNull(prevDigest, "prevDigest");
        String canonical = recordId + "|" + at + "|" + actorType.name() + "|"
                + hexOrEmpty(actorUuidDigest) + "|" + source.name() + "|"
                + kind.name() + "|" + attemptId + "|" + resultName + "|"
                + hexOrEmpty(requestDigest) + "|" + configRevision + "|"
                + HEX.formatHex(prevDigest);
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static String hexOrEmpty(byte[] digest) {
        if (digest == null) {
            return "";
        }
        if (digest.length != EmergencyDigests.DIGEST_LENGTH) {
            throw new IllegalArgumentException(
                    "digest must be exactly " + EmergencyDigests.DIGEST_LENGTH
                            + " bytes"
            );
        }
        return HEX.formatHex(digest);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
