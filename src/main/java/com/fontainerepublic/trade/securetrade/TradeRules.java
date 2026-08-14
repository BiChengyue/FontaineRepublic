package com.fontainerepublic.trade.securetrade;

import java.util.List;

/**
 * Dimension/site policy helpers (FR-TRADE-003-A).
 *
 * <p>Adapted from Secure Trade's {@code TradeRules} (MIT, Copyright (c) 2026
 * Secure Trade Mod Authors). Unlike the upstream service-loader design, the
 * allowed/blocked dimension lists are passed in by the server-authoritative
 * caller, so the trade service remains the single authority.</p>
 */
public final class TradeRules {
    private TradeRules() {
    }

    /** True when the dimension is permitted by the given policy lists. */
    public static boolean isDimensionAllowed(
            String dimensionId,
            List<String> allowed,
            List<String> blocked
    ) {
        if (allowed != null && !allowed.isEmpty()) {
            return allowed.contains(dimensionId);
        }
        if (blocked != null && !blocked.isEmpty()) {
            return !blocked.contains(dimensionId);
        }
        return true;
    }
}
