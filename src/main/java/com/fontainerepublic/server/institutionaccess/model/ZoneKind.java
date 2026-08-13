package com.fontainerepublic.server.institutionaccess.model;

import java.util.Set;

/**
 * Kind of an institution zone (FR-INST-002-B §2/§4).
 *
 * <p>The zone kind selects the workflow served by the zone:
 * {@link #PUBLIC} serves the citizen public workflow, {@link #OFFICIAL} the
 * official routine workflow, and {@link #SECURE} the high-risk workflow.
 * Each kind allows exactly the on-site capability classes that match its
 * workflow; the capability set of a zone must be a non-empty subset of the
 * kind's allowed set (validated at add/set-kind time).</p>
 */
public enum ZoneKind {

    /** Citizen public business: presence in the zone issues a single-use
     *  two-minute public context (FR-INST-001-B §3.1). */
    PUBLIC(Set.of(CapabilityClass.ONSITE_PUBLIC_SERVICE)),

    /** Official routine work: a 10/60-minute session refreshed only by valid
     *  institutional actions (FR-INST-001-B §3.2). */
    OFFICIAL(Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY)),

    /** High-risk secure area: a 30-second single-use authorization requiring
     *  a valid official routine session (FR-INST-001-B §3.3). */
    SECURE(Set.of(CapabilityClass.ONSITE_OFFICIAL_DUTY));

    private final Set<CapabilityClass> allowedCapabilities;

    ZoneKind(Set<CapabilityClass> allowedCapabilities) {
        this.allowedCapabilities = Set.copyOf(allowedCapabilities);
    }

    /** The capability classes this zone kind may serve (workflow match). */
    public Set<CapabilityClass> allowedCapabilities() {
        return allowedCapabilities;
    }
}
