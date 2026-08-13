package com.fontainerepublic.server.land.event;

import com.fontainerepublic.server.land.api.PermissionResolver;
import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.persistence.LandRepository;

import java.util.Objects;
import java.util.UUID;

/**
 * Pure, Forge-free event-time land decision policy (FR-LAND-001-A §5).
 *
 * <p>Build/break/interact events resolve the affected position through the
 * repository and consult the {@link PermissionResolver} at event time — a
 * cached decision is never authoritative. Positions outside every registered
 * parcel fail closed ({@code false}); the policy contains no coordinates of
 * its own.</p>
 */
public final class LandEventPolicy {

    private final LandRepository repository;
    private final PermissionResolver resolver;

    public LandEventPolicy(LandRepository repository, PermissionResolver resolver) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    /** May the player place/break a block at the position? */
    public boolean allowBuild(UUID player, String dimension, int x, int y, int z) {
        return allow(player, dimension, x, y, z, Action.BUILD);
    }

    /** May the player break a block at the position? */
    public boolean allowBreak(UUID player, String dimension, int x, int y, int z) {
        return allow(player, dimension, x, y, z, Action.BREAK);
    }

    /** May the player interact (BlockEntity) at the position? */
    public boolean allowInteract(UUID player, String dimension, int x, int y, int z) {
        return allow(player, dimension, x, y, z, Action.INTERACT);
    }

    private boolean allow(UUID player, String dimension, int x, int y, int z, Action action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(dimension, "dimension");
        LandParcel parcel = repository.findParcelAt(dimension, x, y, z).orElse(null);
        if (parcel == null) {
            // Unknown parcel: fail closed.
            return false;
        }
        return switch (action) {
            case BUILD -> resolver.canBuild(player, parcel.parcelId());
            case BREAK -> resolver.canBreak(player, parcel.parcelId());
            case INTERACT -> resolver.canInteract(player, parcel.parcelId());
        };
    }

    private enum Action {
        BUILD,
        BREAK,
        INTERACT
    }
}
