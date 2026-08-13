package com.fontainerepublic.server.land.service;

import com.fontainerepublic.server.land.api.HolderDirectory;
import com.fontainerepublic.server.land.api.PermissionResolver;
import com.fontainerepublic.server.land.model.LandAccess;
import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.land.model.UsageRight;
import com.fontainerepublic.server.land.persistence.LandRepository;
import com.fontainerepublic.server.registry.model.OwnerReference;

import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Config-driven {@link PermissionResolver} (FR-LAND-001-A §4/§5).
 *
 * <p>Decisions are resolved at event time against the parcel's access policy,
 * its live usage rights, the holder directory, and the injected
 * {@link LandPermissionConfig}. There is no rank-to-permission mapping and no
 * OP bypass (GOD rule). Unknown parcels, unavailable services, or unresolved
 * policy fail closed ({@code false}).</p>
 */
public final class ConfigDrivenPermissionResolver implements PermissionResolver {

    private final LandPermissionConfig config;
    private final LandRepository repository;
    private final HolderDirectory holders;
    private final LongSupplier clock;

    public ConfigDrivenPermissionResolver(
            LandPermissionConfig config,
            LandRepository repository,
            HolderDirectory holders,
            LongSupplier clock
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.holders = Objects.requireNonNull(holders, "holders");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean canBuild(UUID player, ParcelId parcelId) {
        return decide(player, parcelId);
    }

    @Override
    public boolean canBreak(UUID player, ParcelId parcelId) {
        return decide(player, parcelId);
    }

    @Override
    public boolean canInteract(UUID player, ParcelId parcelId) {
        return decide(player, parcelId);
    }

    private boolean decide(UUID player, ParcelId parcelId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(parcelId, "parcelId");
        if (!holders.isAvailable()) {
            return false;
        }
        LandParcel parcel = repository.findByParcelId(parcelId).orElse(null);
        if (parcel == null) {
            return false;
        }
        if (config.requireActiveSubject() && !holders.hasActiveSubject(player)) {
            return false;
        }
        return switch (parcel.access()) {
            case PUBLIC -> config.publicAccessAllowed();
            case RESTRICTED -> config.restrictedRequiresUsageRight()
                    ? hasValidUsageRight(player, parcel)
                    : config.publicAccessAllowed();
            case PRIVATE -> config.privateRequiresUsageRight()
                    ? hasValidUsageRight(player, parcel)
                    : false;
        };
    }

    private boolean hasValidUsageRight(UUID player, LandParcel parcel) {
        UsageRight right = parcel.usageRightOf(OwnerReference.forPlayer(player))
                .orElse(null);
        return right != null && right.validAt(clock.getAsLong());
    }
}
