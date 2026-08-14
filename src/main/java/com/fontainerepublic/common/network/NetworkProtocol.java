package com.fontainerepublic.common.network;

import com.fontainerepublic.FontaineRepublic;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Predicate;

/**
 * Immutable channel identity and compatibility predicates.
 */
public final class NetworkProtocol {
    @SuppressWarnings("removal") // Forge 1.20.1 API surface (deprecated for removal on newer JDKs)
    public static final ResourceLocation CHANNEL_NAME =
            new ResourceLocation(FontaineRepublic.MOD_ID, "main");
    public static final String VERSION = "6";

    private NetworkProtocol() {
    }

    public static boolean clientAccepts(String remoteVersion) {
        return VERSION.equals(remoteVersion);
    }

    public static boolean serverAccepts(String remoteVersion) {
        return VERSION.equals(remoteVersion)
                || NetworkRegistry.ABSENT.version().equals(remoteVersion);
    }

    static Predicate<String> clientPredicate() {
        return NetworkProtocol::clientAccepts;
    }

    static Predicate<String> serverPredicate() {
        return NetworkProtocol::serverAccepts;
    }

    static SimpleChannel createChannel() {
        return NetworkRegistry.newSimpleChannel(
                CHANNEL_NAME,
                () -> VERSION,
                clientPredicate(),
                serverPredicate()
        );
    }
}
