package com.fontainerepublic.client;

import com.fontainerepublic.client.gui.FrMainScreen;
import com.fontainerepublic.client.gui.court.CourtScreen;
import com.fontainerepublic.client.gui.citizen.CitizenScreen;
import com.fontainerepublic.client.gui.government.GovernmentScreen;
import com.fontainerepublic.client.gui.guide.GuideScreen;
import com.fontainerepublic.client.gui.land.LandScreen;
import com.fontainerepublic.client.gui.money.HistoryScreen;
import com.fontainerepublic.client.gui.money.MoneyScreen;
import com.fontainerepublic.client.gui.notifications.NotificationScreen;
import com.fontainerepublic.client.gui.parliament.ParliamentScreen;
import com.fontainerepublic.client.hud.FrHudRenderer;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RenderGuiEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * FR client entry (FR-CLIENT-001-IMPL-B): registers the {@code /frclient}
 * client command surface and the balance/notification HUD. This class lives
 * in {@code client/} and is referenced only from the main mod class's
 * client-setup listener ({@code FMLClientSetupEvent}), which fires only on
 * the physical client; a dedicated server never loads it. All screens are
 * thin views over the non-authoritative presentation cache; every mutation
 * path re-enters the server command surface.
 */
public final class ClientManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientManager.class);

    private static boolean initialized;

    private ClientManager() {
    }

    /** Idempotent client-side initialization (safe side isolated). */
    public static void init() {
        if (initialized) {
            return;
        }
        synchronized (ClientManager.class) {
            if (initialized) {
                return;
            }
            MinecraftForge.EVENT_BUS.addListener(ClientManager::onRegisterClientCommands);
            MinecraftForge.EVENT_BUS.addListener(FrHudRenderer::onRenderGui);
            initialized = true;
            LOGGER.debug("[FR Client] ClientManager initialized (/frclient + HUD)");
        }
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("frclient")
                .executes(context -> openMain())
                .then(Commands.literal("money")
                        .executes(context -> openMoney()))
                .then(Commands.literal("citizen")
                        .executes(context -> openCitizen()))
                .then(Commands.literal("history")
                        .executes(context -> openHistory()))
                .then(Commands.literal("notifications")
                        .executes(context -> openNotifications()))
                .then(Commands.literal("government")
                        .executes(context -> openGovernment()))
                .then(Commands.literal("parliament")
                        .executes(context -> openParliament()))
                .then(Commands.literal("court")
                        .executes(context -> openCourt()))
                .then(Commands.literal("land")
                        .executes(context -> openLand()))
                .then(Commands.literal("guide")
                        .executes(context -> openGuide())));
        LOGGER.debug("[FR Client] /frclient client command registered");
    }

    private static int openMain() {
        Minecraft.getInstance().setScreen(new FrMainScreen());
        return 1;
    }

    private static int openMoney() {
        Minecraft.getInstance().setScreen(new MoneyScreen());
        return 1;
    }

    private static int openCitizen() {
        Minecraft.getInstance().setScreen(new CitizenScreen());
        return 1;
    }

    private static int openHistory() {
        Minecraft.getInstance().setScreen(new HistoryScreen());
        return 1;
    }

    private static int openNotifications() {
        Minecraft.getInstance().setScreen(new NotificationScreen());
        return 1;
    }

    private static int openGovernment() {
        Minecraft.getInstance().setScreen(new GovernmentScreen());
        return 1;
    }

    private static int openParliament() {
        Minecraft.getInstance().setScreen(new ParliamentScreen());
        return 1;
    }

    private static int openCourt() {
        Minecraft.getInstance().setScreen(new CourtScreen());
        return 1;
    }

    private static int openLand() {
        Minecraft.getInstance().setScreen(new LandScreen());
        return 1;
    }

    private static int openGuide() {
        Minecraft.getInstance().setScreen(new GuideScreen());
        return 1;
    }
}
