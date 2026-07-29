package com.fontainerepublic.server.network;

import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Presence-filtered S2C transport facade.
 */
public final class NetworkSendService {
    private final Predicate<Connection> remotePresence;
    private final BiConsumer<Object, Connection> clientSender;

    public NetworkSendService(
            Predicate<Connection> remotePresence,
            BiConsumer<Object, Connection> clientSender
    ) {
        this.remotePresence = Objects.requireNonNull(remotePresence, "remotePresence");
        this.clientSender = Objects.requireNonNull(clientSender, "clientSender");
    }

    public SendResult trySendToPlayer(ServerPlayer player, Object message) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(message, "message");

        Connection connection = player.connection.connection;
        if (!connection.isConnected()) {
            return SendResult.CONNECTION_NOT_LIVE;
        }
        if (!remotePresence.test(connection)) {
            return SendResult.REMOTE_CHANNEL_ABSENT;
        }

        clientSender.accept(message, connection);
        return SendResult.SENT;
    }

    public enum SendResult {
        SENT,
        REMOTE_CHANNEL_ABSENT,
        CONNECTION_NOT_LIVE
    }
}
