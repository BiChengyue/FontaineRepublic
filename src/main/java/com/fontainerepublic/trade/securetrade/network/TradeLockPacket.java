package com.fontainerepublic.trade.securetrade.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Verbatim port of Navielon/SecureTrade's {@code TradeLockPacket} (MIT,
 * Copyright (c) 2026 Secure Trade Mod Authors).
 */
public class TradeLockPacket {
    private final boolean locked;

    public TradeLockPacket(boolean locked) {
        this.locked = locked;
    }

    public TradeLockPacket(FriendlyByteBuf buf) {
        this.locked = buf.readBoolean();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.locked);
    }

    public boolean locked() {
        return this.locked;
    }
}
