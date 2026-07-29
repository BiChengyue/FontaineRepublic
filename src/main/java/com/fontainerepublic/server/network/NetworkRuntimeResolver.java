package com.fontainerepublic.server.network;

import com.fontainerepublic.core.CoreManager;
import com.fontainerepublic.core.module.runtime.ModuleState;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the current ACTIVE runtime without retaining a service instance.
 */
public final class NetworkRuntimeResolver {
    private final CoreManager coreManager;

    public NetworkRuntimeResolver(CoreManager coreManager) {
        this.coreManager = Objects.requireNonNull(coreManager, "coreManager");
    }

    public Optional<NetworkRuntimeService> resolve() {
        return coreManager.getRuntimeContainer(NetworkRuntimeModule.MODULE_ID)
                .filter(container -> container.state() == ModuleState.ACTIVE)
                .flatMap(container -> container.instance())
                .filter(NetworkRuntimeModule.class::isInstance)
                .map(NetworkRuntimeModule.class::cast)
                .flatMap(NetworkRuntimeModule::activeService);
    }
}
