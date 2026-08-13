package com.fontainerepublic.server.emergency.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Verified actor/source identity of one emergency invocation
 * (FR-EMG-001-A §4).
 *
 * <p>{@link #HYDRO_ARCHON} carries the authenticated actor UUID (from the
 * server source, never a name); {@link #SERVER_CONSOLE} carries the strict
 * local-console classification. Ordinary OP, RCON, command blocks, functions,
 * integrated hosts, and players are represented by classification only and are
 * rejected by the service even if parsing reaches the callback.</p>
 *
 * @param actorType       HYDRO_ARCHON or SERVER_CONSOLE
 * @param actorUuid       canonical UUID for HYDRO_ARCHON
 * @param classification  strict source classification (must be
 *                        LOCAL_CONSOLE for SERVER_CONSOLE)
 */
public record EmergencyActorSource(
        EmergencyActorType actorType,
        UUID actorUuid,
        EmergencySourceClassification classification
) {

    public EmergencyActorSource {
        actorType = Objects.requireNonNull(actorType, "actorType");
        classification = Objects.requireNonNull(classification, "classification");
        if (actorType == EmergencyActorType.HYDRO_ARCHON) {
            if (actorUuid == null) {
                throw new IllegalArgumentException(
                        "A HYDRO_ARCHON source must carry the actor UUID"
                );
            }
            String canonical = actorUuid.toString();
            if (!canonical.equals(canonical.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("UUID must be canonical: " + canonical);
            }
        } else {
            if (actorUuid != null) {
                throw new IllegalArgumentException(
                        "A SERVER_CONSOLE source cannot carry an actor UUID"
                );
            }
            if (classification != EmergencySourceClassification.LOCAL_CONSOLE) {
                throw new IllegalArgumentException(
                        "A SERVER_CONSOLE source requires the LOCAL_CONSOLE classification"
                );
            }
        }
    }

    /** Binds this source into a token binding (actor identity only). */
    public EmergencyTokenBinding bind(
            String moduleId,
            String actionId,
            String actionVersion,
            EmergencyTargetType targetType,
            String targetId,
            EmergencyCategory category,
            String reason,
            java.util.Map<String, String> parameters,
            byte[] revisionDigest,
            boolean offlineSafe
    ) {
        return new EmergencyTokenBinding(
                actorType,
                actorUuid,
                moduleId,
                actionId,
                actionVersion,
                targetType,
                targetId,
                category,
                reason,
                parameters,
                revisionDigest,
                offlineSafe
        );
    }

    public Optional<UUID> uuid() {
        return Optional.ofNullable(actorUuid);
    }
}
