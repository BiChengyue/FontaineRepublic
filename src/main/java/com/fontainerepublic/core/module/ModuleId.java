package com.fontainerepublic.core.module;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, normalized identifier for a FontaineRepublic module.
 */
public record ModuleId(String value) implements Comparable<ModuleId> {
    private static final Pattern VALID_FORMAT = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    public ModuleId {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!VALID_FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Module ID must contain only lowercase letters, digits, and single hyphen separators: " + value
            );
        }
    }

    @Override
    public int compareTo(ModuleId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
