package com.fontainerepublic.server.registry.api;

/**
 * Outcome classification of an exact public number resolution
 * (FR-ID-001-A §7.1).
 *
 * <p>Ordinary output collapses unknown, malformed, reserved, and unauthorized
 * details as appropriate to resist probing; consumers must re-resolve and
 * revalidate status at their final mutation boundary.</p>
 */
public enum RoutingStatus {

    /** Exact number resolves to an {@code ACTIVE} subject. */
    ROUTABLE_ACTIVE,

    /** Exact number resolves to a known but non-{@code ACTIVE} subject. */
    KNOWN_NON_ACTIVE,

    /** Number is unknown to the registry or invalid. */
    UNKNOWN_OR_INVALID,

    /** The registry is unavailable; nothing can be resolved. */
    REGISTRY_UNAVAILABLE
}
