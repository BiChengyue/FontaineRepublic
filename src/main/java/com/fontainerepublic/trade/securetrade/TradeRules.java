package com.fontainerepublic.trade.securetrade;

import com.fontainerepublic.trade.securetrade.platform.Services;

import java.util.List;

/**
 * Verbatim port of Navielon/SecureTrade's {@code TradeRules} (MIT,
 * Copyright (c) 2026 Secure Trade Mod Authors; upstream commit
 * {@code add98b377ffc39e5d73789a874a08c5e369790ce}).
 */
public final class TradeRules {
    private TradeRules() {
    }

    public static boolean isDimensionAllowed(String dimensionId) {
        List<String> allowed = Services.PLATFORM.getAllowedDimensions();
        List<String> blocked = Services.PLATFORM.getBlockedDimensions();

        if (allowed != null && !allowed.isEmpty()) {
            return allowed.contains(dimensionId);
        }
        if (blocked != null && !blocked.isEmpty()) {
            return !blocked.contains(dimensionId);
        }
        return true;
    }
}
