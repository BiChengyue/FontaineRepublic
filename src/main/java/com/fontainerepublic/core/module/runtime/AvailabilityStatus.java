package com.fontainerepublic.core.module.runtime;

/**
 * Final or in-progress runtime availability classification for a structurally resolvable module.
 */
public enum AvailabilityStatus {
    PENDING,
    AVAILABLE,
    DIRECT_FAILURE,
    DEPENDENCY_FAILURE
}
