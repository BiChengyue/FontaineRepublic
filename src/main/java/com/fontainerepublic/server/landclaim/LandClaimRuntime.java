package com.fontainerepublic.server.landclaim;

import com.fontainerepublic.server.landclaim.api.LandClaimService;

import java.util.Objects;
import java.util.Optional;

/**
 * Server-runtime holder of the current ACTIVE land-claim service.
 *
 * <p>Follows the module service-routing precedent of the project
 * ({@code TradeRuntime}, {@code MailRuntime}, {@code NetworkRuntimeResolver}):
 * the land-claim C2S handlers in the common package hold no state and resolve
 * the active {@link LandClaimService} per invocation through this static
 * locator. {@code LandClaimModule} binds the service at runtime start and
 * unbinds it on shutdown, so a missing runtime (shutdown window) is a silent
 * best-effort no-op for the handlers.</p>
 */
public final class LandClaimRuntime {

    private static volatile LandClaimService service;

    private LandClaimRuntime() {
    }

    public static void bind(LandClaimService landClaimService) {
        service = Objects.requireNonNull(landClaimService, "landClaimService");
    }

    public static void unbind() {
        service = null;
    }

    /** Stateless resolver of the active land-claim service (handlers/commands). */
    public static Optional<LandClaimService> resolve() {
        return Optional.ofNullable(service);
    }
}
