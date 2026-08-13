package com.fontainerepublic.server.land.api;

import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.model.ParcelId;

import java.util.UUID;

/**
 * Config-driven land access resolver (FR-LAND-001-A §4/§5).
 *
 * <p>Resolves build/break/interact decisions at event time against the
 * parcel's {@code LandAccess} policy, its live usage rights, and the
 * injected configuration. Rank/OP never bypasses it (GOD rule): no
 * rank-to-permission mapping exists anywhere in the land module. Decisions
 * fail closed — unknown parcels, unavailable services, or unresolved policy
 * yield {@code false}.</p>
 */
public interface PermissionResolver {

    /** Whether the player may build on the parcel at this moment. */
    boolean canBuild(UUID player, ParcelId parcelId);

    /** Whether the player may break on the parcel at this moment. */
    boolean canBreak(UUID player, ParcelId parcelId);

    /** Whether the player may interact on the parcel at this moment. */
    boolean canInteract(UUID player, ParcelId parcelId);
}
