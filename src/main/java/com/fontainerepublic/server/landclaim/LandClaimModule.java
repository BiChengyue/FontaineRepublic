package com.fontainerepublic.server.landclaim;

import com.fontainerepublic.core.ConfigManager;
import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.land.LandModule;
import com.fontainerepublic.server.land.api.LandService;
import com.fontainerepublic.server.landclaim.api.LandClaimService;
import com.fontainerepublic.server.landclaim.service.DefaultLandClaimService;
import com.fontainerepublic.server.landclaim.service.LandClaimServerPlayerAccess;
import com.fontainerepublic.server.network.NetworkRuntimeModule;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Communicator land-claim module (FR-LAND-CLAIM-001-A): the server-authoritative
 * republic-parcel claim entry bound to one server runtime.
 *
 * <p>Depends only on {@code land} (the single authoritative
 * {@code createParcelWithUsage} operation and the bounded exact-point query)
 * and {@code network} (the protocol/rate surface). It builds the
 * {@link DefaultLandClaimService} from the configured default parcel shape and
 * binds it to {@link LandClaimRuntime} so both the common C2S handlers and the
 * no-client commands resolve the same ACTIVE service. Ownership stays
 * permanently REPUBLIC; the claimant only receives a non-expiring usage right.</p>
 */
public final class LandClaimModule implements IModule {

    public static final ModuleId MODULE_ID = new ModuleId("landclaim");
    private static final Logger LOGGER = LogUtils.getLogger();

    private final LongSupplier clock;
    private LandClaimService service;
    private volatile LandService boundLand;

    public LandClaimModule() {
        this(System::currentTimeMillis);
    }

    LandClaimModule(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Communicator Land Claim",
                        "1.0.0",
                        Optional.of("Server-authoritative republic parcel claim via the communicator"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(LandModule.MODULE_ID, NetworkRuntimeModule.MODULE_ID),
                Set.of(),
                76,
                LandClaimModule::new
        );
        if (!registry.register(definition)) {
            throw new IllegalStateException("Unable to register module " + MODULE_ID);
        }
    }

    @Override
    public String getName() {
        return MODULE_ID.value();
    }

    @Override
    public void init() {
        LOGGER.info("[LandClaim] Module initialized (no service yet)");
    }

    /**
     * Binds the authoritative land service after the runtime start and builds
     * the runtime land-claim service from the configured default parcel shape.
     * Until bound, every C2S handler resolves no service and silently drops.
     *
     * <p>FR-LAND-CLAIM-001-FIX-01 F5: the hard dependency is expected to be
     * present (module resolution guarantees Land is ACTIVE first), but if the
     * service is unexpectedly unavailable we must fail closed without crashing
     * startup. In that case one explicit warning is logged, the service is left
     * unbound and {@code LandClaimRuntime} stays empty so command/C2S
     * resolution returns empty rather than throwing on a null-guard.</p>
     */
    public void bindServices(LandService land) {
        if (land == null) {
            LOGGER.warn(
                    "[LandClaim] Land service unexpectedly unavailable at bind; "
                            + "land-claim runtime left unbound (fail closed)"
            );
            return;
        }
        this.boundLand = land;
        this.service = new DefaultLandClaimService(
                boundLand,
                new LandClaimServerPlayerAccess(),
                clock,
                ConfigManager.landClaimHalfWidth(),
                ConfigManager.landClaimHeight(),
                ConfigManager.landClaimZoneTypeName()
        );
        LandClaimRuntime.bind(service);
        LOGGER.info(
                "[LandClaim] Runtime initialized (halfWidth={}, height={}, zone={})",
                ConfigManager.landClaimHalfWidth(),
                ConfigManager.landClaimHeight(),
                ConfigManager.landClaimZoneTypeName()
        );
    }

    @Override
    public void shutdown() {
        service = null;
        boundLand = null;
        LandClaimRuntime.unbind();
        LOGGER.info("[LandClaim] Runtime closed");
    }

    /**
     * The bound service, or {@code null} while unbound. Returns {@code null}
     * (never throws) so the {@link Optional#map} resolvers in the command and
     * mod entry produce an empty result for an unbound runtime instead of
     * propagating an exception (FR-LAND-CLAIM-001-FIX-01 F5).
     */
    public LandClaimService service() {
        return service;
    }
}
