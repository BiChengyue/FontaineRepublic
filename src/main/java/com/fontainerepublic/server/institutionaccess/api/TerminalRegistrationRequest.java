package com.fontainerepublic.server.institutionaccess.api;

import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.TerminalPosition;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable authoritative request to register an institution terminal
 * (FR-INST-002-A §3, FR-INST-001-A §6.2).
 *
 * @param facilityId     the anchoring facility (must exist, be ACTIVE, and
 *                       match the institution type)
 * @param position       the anchored block position (must lie inside the
 *                       facility's parcel region)
 * @param capabilitySet  the allowed capability classes (non-empty, bounded)
 * @param secure         secure-operations terminal; issuing an
 *                       {@code ONSITE_OFFICIAL_DUTY} context on it selects the
 *                       high-risk workflow (FR-INST-001-B §3.3)
 */
public record TerminalRegistrationRequest(
        FacilityId facilityId,
        TerminalPosition position,
        Set<CapabilityClass> capabilitySet,
        boolean secure
) {

    public TerminalRegistrationRequest {
        facilityId = Objects.requireNonNull(facilityId, "facilityId");
        position = Objects.requireNonNull(position, "position");
        capabilitySet = Set.copyOf(Objects.requireNonNull(capabilitySet, "capabilitySet"));
        if (capabilitySet.isEmpty()) {
            throw new IllegalArgumentException(
                    "A terminal must allow at least one capability"
            );
        }
    }
}
