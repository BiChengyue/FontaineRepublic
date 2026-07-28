package com.fontainerepublic.core.module.dependency;

import com.fontainerepublic.core.module.ModuleId;

import java.util.Objects;

/**
 * Immutable declaration that one module depends on another module.
 */
public record DependencyEdge(ModuleId dependent, ModuleId dependency, Type type) {
    public DependencyEdge {
        dependent = Objects.requireNonNull(dependent, "dependent");
        dependency = Objects.requireNonNull(dependency, "dependency");
        type = Objects.requireNonNull(type, "type");
    }

    public enum Type {
        REQUIRED,
        OPTIONAL
    }
}
