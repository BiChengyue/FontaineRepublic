package com.fontainerepublic.server.trade;

import com.fontainerepublic.core.ConfigManager;
import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.economy.EconomyModule;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.network.NetworkRuntimeModule;
import com.fontainerepublic.server.network.NetworkSendService;
import com.fontainerepublic.server.trade.api.TradeService;
import com.fontainerepublic.server.trade.service.DefaultTradeService;
import com.fontainerepublic.server.trade.service.ServerPlayerAccess;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Communicator trade module (FR-TRADE-001-A): the server-authoritative
 * trade state machine bound to one server runtime.
 *
 * <p>Depends on the economy module (the settlement channel
 * {@code executeTradeSettlement}) and the network runtime (the S2C send
 * service for the per-viewer snapshots). The module drives the LOCKED
 * countdown from the server tick and cancels sessions on logout; the main
 * mod class hooks {@link #playerDisconnected} at its logout listener too
 * (the module listener is the in-module fallback). On shutdown every live
 * session is simply dropped — the intent model escrows nothing.</p>
 */
public final class TradeModule implements IModule {

    public static final ModuleId MODULE_ID = new ModuleId("trade");
    private static final Logger LOGGER = LogUtils.getLogger();

    private final LongSupplier tickSource;
    private final LongSupplier clock;
    private DefaultTradeService service;
    private volatile EconomyService boundEconomy;
    private volatile NetworkSendService boundSendService;
    private boolean listenersRegistered;

    public TradeModule() {
        this(
                () -> {
                    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                    return server == null ? 0L : server.getTickCount();
                },
                System::currentTimeMillis
        );
    }

    TradeModule(LongSupplier tickSource, LongSupplier clock) {
        this.tickSource = Objects.requireNonNull(tickSource, "tickSource");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Communicator Trade",
                        "1.0.0",
                        Optional.of("Server-authoritative intent-model trade sessions"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(
                        EconomyModule.MODULE_ID,
                        NetworkRuntimeModule.MODULE_ID
                ),
                Set.of(),
                70,
                TradeModule::new
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
        if (!listenersRegistered) {
            MinecraftForge.EVENT_BUS.register(this);
            listenersRegistered = true;
        }
        LOGGER.info("[Trade] Module initialized (no sessions yet)");
    }

    /**
     * Binds the authoritative economy and network services after the runtime
     * start and builds the runtime service. Until bound, every C2S handler
     * resolves no service and silently drops the message.
     */
    public void bindServices(
            EconomyService economyService,
            NetworkSendService sendService
    ) {
        this.boundEconomy = Objects.requireNonNull(economyService, "economyService");
        this.boundSendService = Objects.requireNonNull(sendService, "sendService");
        this.service = new DefaultTradeService(
                boundEconomy,
                new ServerPlayerAccess(),
                boundSendService,
                tickSource,
                clock,
                ConfigManager.tradeTaxRatePercent()
        );
        TradeRuntime.bind(service);
        LOGGER.info(
                "[Trade] Runtime initialized (taxRatePercent={})",
                ConfigManager.tradeTaxRatePercent()
        );
    }

    @Override
    public void shutdown() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        TradeRuntime.unbind();
        if (listenersRegistered) {
            MinecraftForge.EVENT_BUS.unregister(this);
            listenersRegistered = false;
        }
        boundEconomy = null;
        boundSendService = null;
        LOGGER.info("[Trade] Runtime closed");
    }

    /** Server tick: advances the LOCKED countdown and executes confirmed
     *  sessions; also drops sessions of players that went offline. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        TradeService current = service;
        if (current != null) {
            try {
                current.tick();
            } catch (RuntimeException failure) {
                LOGGER.warn(
                        "[Trade] Tick failed; sessions kept: {}",
                        failure.getMessage()
                );
            }
        }
    }

    /** Logout: cancels every session of the player (counterparty notified). */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }
        playerDisconnected(player.getUUID());
    }

    /** Public disconnect hook for the main mod class logout listener. */
    public void playerDisconnected(UUID playerId) {
        TradeService current = service;
        if (current != null) {
            try {
                current.playerDisconnected(playerId);
            } catch (RuntimeException failure) {
                LOGGER.warn(
                        "[Trade] Logout cancellation failed for {}: {}",
                        playerId,
                        failure.getMessage()
                );
            }
        }
    }

    public TradeService service() {
        if (service == null) {
            throw new IllegalStateException("Trade service is not active");
        }
        return service;
    }
}
