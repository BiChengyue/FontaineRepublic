package com.fontainerepublic;

import com.fontainerepublic.trade.securetrade.TradeLogger;
import com.fontainerepublic.trade.securetrade.command.TradeCommand;
import com.fontainerepublic.trade.securetrade.menu.TradeSessionManager;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * FR-TRADE-004 Forge event adapter for the copied Navielon/SecureTrade (MIT)
 * escrow session life-cycle.
 *
 * <p>Owns the {@code /trade} command registration, the server-tick driver of
 * {@link TradeSessionManager} (countdown + expiry pruning) and the stop-time
 * cleanup (cancel all escrow sessions, clear pending requests, shut the
 * logger). Sits outside the FR module registry because the Secure Trade
 * surface is a self-contained escrow authority, not an FR
 * {@code com.fontainerepublic.core.IModule} service.</p>
 */
public final class FRTradeEvents {

    private static int cleanupTicks = 0;

    private FRTradeEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        TradeCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        TradeSessionManager.tick();
        cleanupTicks++;
        if (cleanupTicks >= 1200) {
            cleanupTicks = 0;
            TradeCommand.pruneExpired();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TradeSessionManager.cancelAllAndClear();
        TradeCommand.clearAll();
        TradeLogger.shutdown();
    }
}
