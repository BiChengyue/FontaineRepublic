package com.fontainerepublic.server.audit.api;

import com.fontainerepublic.server.audit.model.AuditActorType;
import com.fontainerepublic.server.audit.model.AuditCategory;
import com.fontainerepublic.server.audit.model.AuditClassification;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable draft for one audit write (FR-AUD-001-A §4).
 *
 * <p>Validation is fail-fast: null/blank fields, out-of-bounds lengths,
 * non-canonical player ids and unclassified drafts are rejected at
 * construction. The entry id, timestamp and revision are server-assigned at
 * commit time and are therefore not part of the draft.</p>
 *
 * <p>The optional {@code payload} is the bounded full payload; it is stored
 * separately from the projection and is never rendered into ordinary output.
 * For {@link AuditClassification#SECRET_DIGEST_ONLY} drafts the payload
 * plaintext is never persisted — only its canonical digest is kept.</p>
 */
public record AuditDraft(
        AuditActorType actorType,
        String actorId,
        AuditCategory category,
        String moduleId,
        String actionId,
        Optional<String> targetType,
        Optional<String> targetId,
        AuditClassification classification,
        String summary,
        Optional<CompoundTag> payload
) {
    public static final int MAX_ACTOR_ID_LENGTH = 64;
    public static final int MAX_MODULE_ID_LENGTH = 64;
    public static final int MAX_ACTION_ID_LENGTH = 64;
    public static final int MAX_TARGET_TYPE_LENGTH = 64;
    public static final int MAX_TARGET_ID_LENGTH = 128;
    public static final int MAX_SUMMARY_LENGTH = 256;
    public static final int MAX_PAYLOAD_BYTES = 4096;

    private static final String MODULE_ID_FORMAT = "[a-z0-9]+(?:-[a-z0-9]+)*";

    public AuditDraft {
        actorType = Objects.requireNonNull(actorType, "actorType");
        category = Objects.requireNonNull(category, "category");
        classification = Objects.requireNonNull(classification, "classification");
        moduleId = requireBounded(moduleId, "moduleId", MAX_MODULE_ID_LENGTH);
        actionId = requireBounded(actionId, "actionId", MAX_ACTION_ID_LENGTH);
        summary = requireBounded(summary, "summary", MAX_SUMMARY_LENGTH);
        if (!moduleId.matches(MODULE_ID_FORMAT)) {
            throw new IllegalArgumentException(
                    "moduleId must contain only lowercase letters, digits, and single hyphen separators"
            );
        }
        targetType = normalizeOptional(targetType, "targetType", MAX_TARGET_TYPE_LENGTH);
        targetId = normalizeOptional(targetId, "targetId", MAX_TARGET_ID_LENGTH);
        payload = normalizePayload(payload);
        actorId = normalizeActorId(actorId, actorType);
    }

    private static String normalizeActorId(String actorId, AuditActorType actorType) {
        String normalized = requireBounded(actorId, "actorId", MAX_ACTOR_ID_LENGTH);
        if (actorType == AuditActorType.PLAYER) {
            try {
                java.util.UUID parsed = java.util.UUID.fromString(normalized);
                if (!parsed.toString().equals(normalized)) {
                    throw new IllegalArgumentException(
                            "PLAYER actorId must be a canonical UUID: " + normalized
                    );
                }
            } catch (IllegalArgumentException invalid) {
                if (invalid.getMessage() != null
                        && invalid.getMessage().contains("canonical UUID")) {
                    throw invalid;
                }
                throw new IllegalArgumentException(
                        "PLAYER actorId must be a canonical UUID: " + normalized,
                        invalid
                );
            }
        }
        return normalized;
    }

    private static String requireBounded(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " exceeds bound of " + maxLength + " characters"
            );
        }
        return normalized;
    }

    private static Optional<String> normalizeOptional(
            Optional<String> value,
            String field,
            int maxLength
    ) {
        if (value == null) {
            return Optional.empty();
        }
        return value.map(candidate -> requireBounded(candidate, field, maxLength));
    }

    private static Optional<CompoundTag> normalizePayload(Optional<CompoundTag> value) {
        if (value == null) {
            return Optional.empty();
        }
        CompoundTag payload = value.orElse(null);
        if (payload == null || payload.isEmpty()) {
            return Optional.empty();
        }
        int encodedBytes;
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            NbtIo.writeUnnamedTag(payload, new DataOutputStream(buffer));
            encodedBytes = buffer.size();
        } catch (IOException impossible) {
            encodedBytes = Integer.MAX_VALUE;
        }
        if (encodedBytes > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "payload exceeds bound of " + MAX_PAYLOAD_BYTES + " bytes"
            );
        }
        return Optional.of(payload);
    }
}
