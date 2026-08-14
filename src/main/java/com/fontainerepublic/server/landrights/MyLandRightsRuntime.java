package com.fontainerepublic.server.landrights;

import com.fontainerepublic.server.landrights.api.MyLandRightsService;

import java.util.Objects;
import java.util.Optional;

/**
 * Server-runtime holder of the current ACTIVE my-usage-rights service
 * (FR-LAND-002-A §5.2).
 *
 * <p>Follows the module service-routing precedent of the project
 * ({@code LandClaimRuntime}, {@code TradeRuntime}, {@code MailRuntime}): the
 * C2S handler in the common package holds no state and resolves the active
 * {@link MyLandRightsService} per invocation through this static locator.
 * {@code LandModule} binds the service at runtime start and unbinds it on
 * shutdown (no separate top-level module owns this presentation surface), so a
 * missing runtime (shutdown window) is a silent best-effort no-op for the
 * handler.</p>
 */
public final class MyLandRightsRuntime {

    private static volatile MyLandRightsService service;

    private MyLandRightsRuntime() {
    }

    public static void bind(MyLandRightsService landRightsService) {
        service = Objects.requireNonNull(landRightsService, "landRightsService");
    }

    public static void unbind() {
        service = null;
    }

    /** Stateless resolver of the active my-usage-rights service. */
    public static Optional<MyLandRightsService> resolve() {
        return Optional.ofNullable(service);
    }
}
