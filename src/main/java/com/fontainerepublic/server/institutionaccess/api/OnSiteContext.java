package com.fontainerepublic.server.institutionaccess.api;

import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.InstitutionType;
import com.fontainerepublic.server.institutionaccess.model.WorkflowKind;
import com.fontainerepublic.server.institutionaccess.model.ZoneId;

import java.util.Objects;
import java.util.UUID;

/**
 * Server-runtime on-site context (FR-INST-001-A §7.2, FR-INST-002-B §3).
 *
 * <p>An {@code OnSiteContext} is a short-lived server-side runtime fact
 * binding player/institution/facility/zone/capability/workflow/issue-time/
 * expiry/facility+zone-revision/source-dimension+position. It is never
 * persisted, never client-owned, and cleared on server shutdown. Validity is
 * rechecked at the final mutation boundary and through bounded presence
 * checks and lifecycle events; returning to the zone never restores a
 * context.</p>
 *
 * <p>The record is immutable; consumption/invalidation state is tracked by
 * the server-runtime registry that issued it.</p>
 */
public record OnSiteContext(
        UUID contextId,
        UUID playerId,
        InstitutionType institutionType,
        FacilityId facilityId,
        ZoneId zoneId,
        WorkflowKind workflowKind,
        CapabilityClass capability,
        long issueTime,
        long expiryTime,
        long facilityRevision,
        long zoneRevision,
        String dimension,
        int blockX,
        int blockY,
        int blockZ
) {

    public OnSiteContext {
        contextId = Objects.requireNonNull(contextId, "contextId");
        playerId = Objects.requireNonNull(playerId, "playerId");
        institutionType = Objects.requireNonNull(institutionType, "institutionType");
        facilityId = Objects.requireNonNull(facilityId, "facilityId");
        zoneId = Objects.requireNonNull(zoneId, "zoneId");
        workflowKind = Objects.requireNonNull(workflowKind, "workflowKind");
        capability = Objects.requireNonNull(capability, "capability");
        dimension = Objects.requireNonNull(dimension, "dimension");
        if (issueTime < 0 || expiryTime < 0) {
            throw new IllegalArgumentException("timestamps must not be negative");
        }
        if (expiryTime < issueTime) {
            throw new IllegalArgumentException("expiryTime must not precede issueTime");
        }
        if (facilityRevision <= 0 || zoneRevision <= 0) {
            throw new IllegalArgumentException(
                    "context revisions must be positive"
            );
        }
    }
}
