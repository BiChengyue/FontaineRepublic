package com.fontainerepublic.server.land.model;

import com.fontainerepublic.server.registry.model.OwnerReference;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable, value-style authoritative land parcel (FR-LAND-001-A §3.1).
 *
 * <p>{@code parcelId} is server-assigned and immutable; {@code dimension} is a
 * canonical world dimension key; {@code region} is a validated bounded box;
 * {@code ownership} is the constant {@code REPUBLIC} and never mutable;
 * {@code usageRights} maps each holder to at most one right (renewal replaces
 * it). Every committed mutation replaces the parcel and increments
 * {@code parcelRevision} exactly once.</p>
 */
public record LandParcel(
        int schemaVersion,
        ParcelId parcelId,
        String dimension,
        ParcelRegion region,
        ZoneType zoneType,
        LandOwnership ownership,
        LandAccess access,
        Map<OwnerReference, UsageRight> usageRights,
        long parcelRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** Canonical dimension keys are Minecraft resource locations. */
    private static final Pattern DIMENSION_PATTERN =
            Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    public LandParcel {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported land parcel schema version: " + schemaVersion
            );
        }
        parcelId = Objects.requireNonNull(parcelId, "parcelId");
        dimension = Objects.requireNonNull(dimension, "dimension");
        if (!DIMENSION_PATTERN.matcher(dimension).matches()) {
            throw new IllegalArgumentException(
                    "Dimension must be a canonical resource location: " + dimension
            );
        }
        region = Objects.requireNonNull(region, "region");
        zoneType = Objects.requireNonNull(zoneType, "zoneType");
        ownership = Objects.requireNonNull(ownership, "ownership");
        if (ownership != LandOwnership.REPUBLIC) {
            throw new IllegalArgumentException(
                    "Parcel ownership is permanently REPUBLIC; got " + ownership
            );
        }
        access = Objects.requireNonNull(access, "access");
        usageRights = Map.copyOf(Objects.requireNonNull(usageRights, "usageRights"));
        if (parcelRevision <= 0) {
            throw new IllegalArgumentException("parcelRevision must be positive");
        }
        if (!parcelId.value().toString()
                .equals(parcelId.value().toString().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("parcelId must be a canonical UUID");
        }
        for (Map.Entry<OwnerReference, UsageRight> entry : usageRights.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().holder())) {
                throw new IllegalArgumentException(
                        "UsageRights key " + entry.getKey().key()
                                + " does not match right holder "
                                + entry.getValue().holder().key()
                );
            }
        }
    }

    /** Replacement parcel with a new zone type: revision incremented once. */
    public LandParcel withZoneType(ZoneType newZoneType) {
        Objects.requireNonNull(newZoneType, "newZoneType");
        return new LandParcel(
                schemaVersion,
                parcelId,
                dimension,
                region,
                newZoneType,
                ownership,
                access,
                usageRights,
                parcelRevision + 1
        );
    }

    /** Replacement parcel with a new access policy: revision incremented once. */
    public LandParcel withAccess(LandAccess newAccess) {
        Objects.requireNonNull(newAccess, "newAccess");
        return new LandParcel(
                schemaVersion,
                parcelId,
                dimension,
                region,
                zoneType,
                ownership,
                newAccess,
                usageRights,
                parcelRevision + 1
        );
    }

    /** Replacement parcel with a holder's usage right added or replaced. */
    public LandParcel withUsageRight(OwnerReference holder, UsageRight right) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(right, "right");
        if (!holder.equals(right.holder())) {
            throw new IllegalArgumentException(
                    "Right holder " + right.holder().key()
                            + " does not match key " + holder.key()
            );
        }
        java.util.LinkedHashMap<OwnerReference, UsageRight> next =
                new java.util.LinkedHashMap<>(usageRights);
        next.put(holder, right);
        return new LandParcel(
                schemaVersion,
                parcelId,
                dimension,
                region,
                zoneType,
                ownership,
                access,
                next,
                parcelRevision + 1
        );
    }

    /** Replacement parcel with a holder's usage right removed (if present). */
    public LandParcel withoutUsageRight(OwnerReference holder) {
        Objects.requireNonNull(holder, "holder");
        if (!usageRights.containsKey(holder)) {
            return this;
        }
        java.util.LinkedHashMap<OwnerReference, UsageRight> next =
                new java.util.LinkedHashMap<>(usageRights);
        next.remove(holder);
        return new LandParcel(
                schemaVersion,
                parcelId,
                dimension,
                region,
                zoneType,
                ownership,
                access,
                next,
                parcelRevision + 1
        );
    }

    /** The live usage right of a holder, if any. */
    public java.util.Optional<UsageRight> usageRightOf(OwnerReference holder) {
        return java.util.Optional.ofNullable(usageRights.get(holder));
    }
}
