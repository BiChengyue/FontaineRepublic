package com.fontainerepublic.trade.securetrade.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * FR-TRADE-004 money-offer C2S packet (FR currency leg added to the Secure
 * Trade escrow session). Mirrors upstream {@code TradeXPChangePacket} wire
 * shape; carries the paying-side money offer (server still clamps/validates
 * it against the authoritative FR EconomyService).
 */
public class TradeMoneyChangePacket {
    private final long moneyAmount;

    public TradeMoneyChangePacket(long moneyAmount) {
        this.moneyAmount = moneyAmount;
    }

    public TradeMoneyChangePacket(FriendlyByteBuf buf) {
        this.moneyAmount = buf.readVarLong();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarLong(this.moneyAmount);
    }

    public long moneyAmount() {
        return this.moneyAmount;
    }
}
