package com.fontainerepublic.server.emergency.model;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable canonical binding of one confirmation token
 * (FR-EMG-001-A §8.2).
 *
 * <p>The token is bound to actor, module, action, target, category, reason,
 * parameters, relevant revisions, and offline-safety. Parameter digests are
 * computed over the canonical encoding (ordered key-value pairs), never map
 * iteration order or display text. The plaintext token is never stored — only
 * its digest is kept at runtime and in audit evidence.</p>
 *
 * @param actorType       HYDRO_ARCHON or SERVER_CONSOLE
 * @param actorUuid       canonical actor UUID for HYDRO_ARCHON; null for
 *                        SERVER_CONSOLE
 * @param moduleId        owning module id
 * @param actionId        stable action id within the module
 * @param actionVersion   semantic version of the action
 * @param targetType      typed target
 * @param targetId        stable target id
 * @param category        emergency category
 * @param reason          normalized bounded reason
 * @param parameters      canonical ordered parameter map
 * @param revisionDigest  SHA-256 over the bound business revisions
 * @param offlineSafe     whether the action may apply to an offline target
 */
public record EmergencyTokenBinding(
        EmergencyActorType actorType,
        UUID actorUuid,
        String moduleId,
        String actionId,
        String actionVersion,
        EmergencyTargetType targetType,
        String targetId,
        EmergencyCategory category,
        String reason,
        Map<String, String> parameters,
        byte[] revisionDigest,
        boolean offlineSafe
) {

    public EmergencyTokenBinding {
        actorType = Objects.requireNonNull(actorType, "actorType");
        if (actorType == EmergencyActorType.HYDRO_ARCHON) {
            if (actorUuid == null) {
                throw new IllegalArgumentException(
                        "A HYDRO_ARCHON binding must carry the actor UUID"
                );
            }
            requireCanonical(actorUuid);
        } else {
            if (actorUuid != null) {
                throw new IllegalArgumentException(
                        "A SERVER_CONSOLE binding cannot carry an actor UUID"
                );
            }
        }
        moduleId = Objects.requireNonNull(moduleId, "moduleId");
        actionId = Objects.requireNonNull(actionId, "actionId");
        actionVersion = Objects.requireNonNull(actionVersion, "actionVersion");
        targetType = Objects.requireNonNull(targetType, "targetType");
        targetId = Objects.requireNonNull(targetId, "targetId");
        category = Objects.requireNonNull(category, "category");
        reason = Objects.requireNonNull(reason, "reason");
        parameters = Map.copyOf(new LinkedHashMap<>(parameters));
        if (revisionDigest == null || revisionDigest.length != EmergencyDigests.DIGEST_LENGTH) {
            throw new IllegalArgumentException(
                    "revisionDigest must be exactly "
                            + EmergencyDigests.DIGEST_LENGTH + " bytes"
            );
        }
    }

    /** Canonical digest over the complete binding (for evidence comparison). */
    public byte[] canonicalDigest() {
        return EmergencyTokenBindingDigests.bindingDigest(this);
    }

    private static void requireCanonical(UUID uuid) {
        String canonical = uuid.toString();
        if (!canonical.equals(canonical.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("UUID must be canonical: " + canonical);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EmergencyTokenBinding that)) {
            return false;
        }
        return actorType == that.actorType
                && Objects.equals(actorUuid, that.actorUuid)
                && moduleId.equals(that.moduleId)
                && actionId.equals(that.actionId)
                && actionVersion.equals(that.actionVersion)
                && targetType == that.targetType
                && targetId.equals(that.targetId)
                && category == that.category
                && reason.equals(that.reason)
                && parameters.equals(that.parameters)
                && Arrays.equals(revisionDigest, that.revisionDigest)
                && offlineSafe == that.offlineSafe;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(actorType, actorUuid, moduleId, actionId,
                actionVersion, targetType, targetId, category, reason,
                parameters, offlineSafe);
        result = 31 * result + Arrays.hashCode(revisionDigest);
        return result;
    }
}
