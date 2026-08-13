package com.fontainerepublic.server.registry.api;

import java.util.Optional;

/**
 * Result of an exact, bounded, non-enumerating public number lookup
 * (FR-ID-001-A §7.1/§13).
 *
 * @param status  routing classification
 * @param subject present only for {@link RoutingStatus#ROUTABLE_ACTIVE}
 */
public record PublicRoutingResult(
        RoutingStatus status,
        Optional<SubjectProjection> subject
) {

    public PublicRoutingResult {
        status = status == null ? RoutingStatus.UNKNOWN_OR_INVALID : status;
        subject = subject == null ? Optional.empty() : subject;
        if (status == RoutingStatus.ROUTABLE_ACTIVE && subject.isEmpty()) {
            throw new IllegalArgumentException(
                    "ROUTABLE_ACTIVE result must carry a subject projection"
            );
        }
        if (status != RoutingStatus.ROUTABLE_ACTIVE && subject.isPresent()) {
            throw new IllegalArgumentException(
                    "Non-routable result must not carry a subject projection"
            );
        }
    }

    public static PublicRoutingResult routable(SubjectProjection projection) {
        return new PublicRoutingResult(
                RoutingStatus.ROUTABLE_ACTIVE,
                Optional.of(projection)
        );
    }

    public static PublicRoutingResult knownNonActive() {
        return new PublicRoutingResult(RoutingStatus.KNOWN_NON_ACTIVE, Optional.empty());
    }

    public static PublicRoutingResult unknownOrInvalid() {
        return new PublicRoutingResult(RoutingStatus.UNKNOWN_OR_INVALID, Optional.empty());
    }

    public static PublicRoutingResult registryUnavailable() {
        return new PublicRoutingResult(RoutingStatus.REGISTRY_UNAVAILABLE, Optional.empty());
    }
}
