package com.fontainerepublic.common.network;

import java.util.Objects;

/**
 * Complete compiled production ledger for the current protocol.
 *
 * <p>FR-NET-001 intentionally registers no application messages.</p>
 */
public final class NetworkProductionMessageTable {
    private NetworkProductionMessageTable() {
    }

    public static void registerAll(NetworkMessageRegistration registration) {
        Objects.requireNonNull(registration, "registration");
    }
}
