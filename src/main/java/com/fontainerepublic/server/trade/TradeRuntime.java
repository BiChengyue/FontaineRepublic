package com.fontainerepublic.server.trade;

import com.fontainerepublic.server.trade.api.TradeService;

import java.util.Objects;
import java.util.Optional;

/**
 * Server-runtime holder of the current ACTIVE communicator trade service.
 *
 * <p>Follows the module service-routing precedent of the project
 * ({@code NetworkRuntimeResolver}, {@code EconomyEmergencyProviders}): the
 * trade C2S handlers in the common package hold no state and resolve the
 * active {@link TradeService} per invocation through this static locator.
 * {@code TradeModule} binds the service when its runtime starts and unbinds
 * it on shutdown, so a missing runtime (shutdown window) is a silent
 * best-effort no-op for the handlers.</p>
 */
public final class TradeRuntime {

    private static volatile TradeService service;

    private TradeRuntime() {
    }

    public static void bind(TradeService tradeService) {
        service = Objects.requireNonNull(tradeService, "tradeService");
    }

    public static void unbind() {
        service = null;
    }

    /** Stateless resolver of the active trade service (handlers / tests). */
    public static Optional<TradeService> resolve() {
        return Optional.ofNullable(service);
    }
}
