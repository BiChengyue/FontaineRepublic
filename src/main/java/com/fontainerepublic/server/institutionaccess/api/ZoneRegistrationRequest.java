package com.fontainerepublic.server.institutionaccess.api;

import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.ZoneKind;
import com.fontainerepublic.server.institutionaccess.model.ZoneRegion;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable authoritative request to add an institution zone
 * (FR-INST-002-B §2/§5).
 *
 * @param facilityId    the containing facility (must exist and be ACTIVE)
 * @param kind          the zone kind selecting the served workflow
 * @param region        the bounded 3D region (must lie inside the facility's
 *                      FR-LAND parcel region and stay within the small-size
 *                      budget; coordinates come from the authoritative
 *                      request or decoded persistence — never hard-coded)
 * @param capabilitySet the allowed capability classes (non-empty, bounded,
 *                      and a subset of the kind's allowed classes)
 */
public record ZoneRegistrationRequest(
        FacilityId facilityId,
        ZoneKind kind,
        ZoneRegion region,
        Set<CapabilityClass> capabilitySet
) {

    public ZoneRegistrationRequest {
        facilityId = Objects.requireNonNull(facilityId, "facilityId");
        kind = Objects.requireNonNull(kind, "kind");
        region = Objects.requireNonNull(region, "region");
        capabilitySet = Set.copyOf(Objects.requireNonNull(capabilitySet, "capabilitySet"));
        if (capabilitySet.isEmpty()) {
            throw new IllegalArgumentException(
                    "A zone must allow at least one capability"
            );
        }
    }
}
