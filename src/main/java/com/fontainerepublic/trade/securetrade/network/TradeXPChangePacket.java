package com.fontainerepublic.trade.securetrade.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Verbatim port of Navielon/SecureTrade's {@code TradeXPChangePacket} (MIT,
 * Copyright (c) 2026 Secure Trade Mod Authors).
 */
public class TradeXPChangePacket {
    private final long xpPoints;

    public TradeXPChangePacket(long xpPoints) {
        this.xpPoints = xpPoints;
    }

    public TradeXPChangePacket(FriendlyByteBuf buf) {
        this.xpPoints = buf.readVarLong();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarLong(this.xpPoints);
    }

    public long xpPoints() {
        return this.xpPoints;
    }
}
