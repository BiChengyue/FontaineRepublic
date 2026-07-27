package com.fontainerepublic.core.module;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable descriptive metadata supplied as part of a module's static contract.
 */
public record ModuleMetadata(
        String name,
        String version,
        Optional<String> description,
        Optional<String> author
) {
    public ModuleMetadata {
        name = requireNonBlank(name, "name");
        version = requireNonBlank(version, "version");
        description = normalizeOptional(description, "description");
        author = normalizeOptional(author, "author");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static Optional<String> normalizeOptional(Optional<String> value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        return value.map(String::trim).filter(text -> !text.isEmpty());
    }
}
