package com.fontainerepublic.server.emergency.api;

import java.util.Optional;

/**
 * Stable runtime resolver of the current ACTIVE emergency-action provider
 * (FR-EMG-001-A §5 / §16).
 *
 * <p>Descriptors hold immutable metadata and this resolver only; they never
 * capture a per-server Service. The resolver is invoked at preview/confirm
 * time and re-checked at the final mutation boundary, so an action-provider
 * replacement during reload cannot slip in a stale provider.</p>
 */
@FunctionalInterface
public interface EmergencyProviderResolver {
    Optional<EmergencyActionProvider> resolve();
}
