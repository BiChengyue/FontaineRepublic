package com.fontainerepublic.server.login;

import com.fontainerepublic.server.citizen.api.CitizenService;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Login provisioning wiring (FR-CMD-USER-001 §3.2, FR-ECO-001-A §8.2,
 * FR-CIT-001-A §4.1): after PlayerData is ready, idempotently ensures the
 * citizen record and the economy account for the verified player UUID.
 *
 * <p>The hook is stateless and idempotent-safe: both services own
 * idempotency, every failure is logged and swallowed so a provisioning
 * problem never blocks login, and no NBT is read here. The service suppliers
 * are resolved per invocation so the hook always observes the current ACTIVE
 * runtime and never caches a service reference.</p>
 */
public final class LoginProvisioningHook {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Supplier<Optional<CitizenService>> citizenService;
    private final Supplier<Optional<EconomyService>> economyService;

    public LoginProvisioningHook(
            Supplier<Optional<CitizenService>> citizenService,
            Supplier<Optional<EconomyService>> economyService
    ) {
        this.citizenService = Objects.requireNonNull(citizenService, "citizenService");
        this.economyService = Objects.requireNonNull(economyService, "economyService");
    }

    /**
     * Provisions citizen + economy for one verified player UUID. Never throws;
     * failures are logged only.
     */
    public void provision(UUID playerId, String playerName) {
        Objects.requireNonNull(playerId, "playerId");
        String name = playerName == null ? "<unknown>" : playerName;
        citizenService.get().ifPresentOrElse(
                service -> ensureCitizen(service, playerId, name),
                () -> LOGGER.debug(
                        "[Login] Citizen service is unavailable at login for {} ({})",
                        name,
                        playerId
                )
        );
        economyService.get().ifPresentOrElse(
                service -> ensureAccount(service, playerId, name),
                () -> LOGGER.debug(
                        "[Login] Economy service is unavailable at login for {} ({})",
                        name,
                        playerId
                )
        );
    }

    private void ensureCitizen(CitizenService service, UUID playerId, String name) {
        try {
            service.ensureCitizen(playerId);
        } catch (RuntimeException failed) {
            LOGGER.warn(
                    "[Login] Citizen provisioning failed for {} ({}): {}",
                    name,
                    playerId,
                    failed.getMessage(),
                    failed
            );
        }
    }

    private void ensureAccount(EconomyService service, UUID playerId, String name) {
        try {
            service.ensureAccountForPlayer(playerId);
        } catch (RuntimeException failed) {
            LOGGER.warn(
                    "[Login] Economy provisioning failed for {} ({}): {}",
                    name,
                    playerId,
                    failed.getMessage(),
                    failed
            );
        }
    }
}
