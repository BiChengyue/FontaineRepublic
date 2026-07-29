package com.fontainerepublic.common.network;

import net.minecraftforge.network.NetworkEvent;

/**
 * Transport adapter invoked on the receiving logical main thread.
 */
@FunctionalInterface
public interface NetworkMessageHandler<MSG> {
    void handle(MSG message, NetworkEvent.Context context);
}
