package com.fontainerepublic.server.emergency.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical SHA-256 helpers for emergency token bindings
 * (FR-EMG-001-A §8.2).
 *
 * <p>The canonical encoding is a fixed ordered concatenation of every binding
 * field; parameters are encoded as sorted {@code key=value} pairs so the
 * digest never depends on map iteration order.</p>
 */
public final class EmergencyTokenBindingDigests {

    private static final HexFormat HEX = HexFormat.of();

    private EmergencyTokenBindingDigests() {
    }

    /** SHA-256 digest of the canonical encoding of a complete binding. */
    public static byte[] bindingDigest(EmergencyTokenBinding binding) {
        Objects.requireNonNull(binding, "binding");
        StringBuilder canonical = new StringBuilder();
        canonical.append(binding.actorType().name()).append('|');
        canonical.append(binding.actorUuid() == null
                ? ""
                : binding.actorUuid()).append('|');
        canonical.append(binding.moduleId()).append('|');
        canonical.append(binding.actionId()).append('|');
        canonical.append(binding.actionVersion()).append('|');
        canonical.append(binding.targetType().name()).append('|');
        canonical.append(binding.targetId()).append('|');
        canonical.append(binding.category().name()).append('|');
        canonical.append(binding.reason()).append('|');
        canonical.append(binding.offlineSafe()).append('|');
        canonical.append(HEX.formatHex(binding.revisionDigest())).append('|');
        binding.parameters().keySet().stream().sorted().forEach(key ->
                canonical.append(key).append('=').append(binding.parameters().get(key))
                        .append(';')
        );
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** SHA-256 digest over the canonical representations of revision labels. */
    public static byte[] revisionDigest(Iterable<String> revisions) {
        Objects.requireNonNull(revisions, "revisions");
        StringBuilder canonical = new StringBuilder();
        java.util.List<String> ordered = new java.util.ArrayList<>();
        revisions.forEach(ordered::add);
        ordered.sort(String::compareTo);
        ordered.forEach(label -> canonical.append(label).append('|'));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

}
