package com.fontainerepublic.server.institutionaccess.model;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable, value-style authoritative institution zone (FR-INST-002-B §2).
 *
 * <p>A zone is a small registered area inside a facility: it carries the
 * facility's institution type, a {@link ZoneKind} that selects the served
 * workflow, a bounded 3D region that must lie fully inside the facility's
 * FR-LAND parcel region, an allowed capability set (a non-empty subset of
 * the kind's allowed classes), and a lifecycle state. Every committed
 * mutation replaces the zone and increments {@code zoneRevision} exactly
 * once; contexts issued before a revision change are invalidated by revision
 * binding (FR-INST-001-A §7.3).</p>
 */
public record Zone(
        int schemaVersion,
        ZoneId zoneId,
        FacilityId facilityId,
        InstitutionType institutionType,
        ZoneKind kind,
        ZoneRegion region,
        Set<CapabilityClass> capabilitySet,
        ZoneState state,
        long zoneRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public Zone {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported zone schema version: " + schemaVersion
            );
        }
        zoneId = Objects.requireNonNull(zoneId, "zoneId");
        facilityId = Objects.requireNonNull(facilityId, "facilityId");
        institutionType = Objects.requireNonNull(institutionType, "institutionType");
        kind = Objects.requireNonNull(kind, "kind");
        region = Objects.requireNonNull(region, "region");
        capabilitySet = Set.copyOf(Objects.requireNonNull(capabilitySet, "capabilitySet"));
        if (capabilitySet.isEmpty()) {
            throw new IllegalArgumentException(
                    "A zone must allow at least one capability"
            );
        }
        state = Objects.requireNonNull(state, "state");
        if (zoneRevision <= 0) {
            throw new IllegalArgumentException("zoneRevision must be positive");
        }
    }

    /** Replacement zone with a new state: revision incremented once. */
    public Zone withState(ZoneState newState) {
        Objects.requireNonNull(newState, "newState");
        return new Zone(
                schemaVersion,
                zoneId,
                facilityId,
                institutionType,
                kind,
                region,
                capabilitySet,
                newState,
                zoneRevision + 1
        );
    }

    /** Replacement zone with a new region: revision incremented once. */
    public Zone withRegion(ZoneRegion newRegion) {
        Objects.requireNonNull(newRegion, "newRegion");
        return new Zone(
                schemaVersion,
                zoneId,
                facilityId,
                institutionType,
                kind,
                newRegion,
                capabilitySet,
                state,
                zoneRevision + 1
        );
    }

    /** Replacement zone with a new kind: revision incremented once. */
    public Zone withKind(ZoneKind newKind) {
        Objects.requireNonNull(newKind, "newKind");
        return new Zone(
                schemaVersion,
                zoneId,
                facilityId,
                institutionType,
                newKind,
                region,
                capabilitySet,
                state,
                zoneRevision + 1
        );
    }

    /** Deterministic iteration order for codec and presentation. */
    public Set<CapabilityClass> orderedCapabilities() {
        return new TreeSet<>(capabilitySet);
    }
}
