package com.fontainerepublic.core.module.runtime;

import java.util.Objects;

/**
 * Immutable diagnostic for the root runtime failure of a module.
 */
public record FailureReason(Category category, String diagnostic) {
    public FailureReason {
        category = Objects.requireNonNull(category, "category");
        Objects.requireNonNull(diagnostic, "diagnostic");
        diagnostic = diagnostic.trim();
        if (diagnostic.isEmpty()) {
            throw new IllegalArgumentException("diagnostic must not be blank");
        }
    }

    public enum Category {
        FACTORY_CREATION,
        MODULE_ID_MISMATCH,
        INITIALIZATION
    }
}
