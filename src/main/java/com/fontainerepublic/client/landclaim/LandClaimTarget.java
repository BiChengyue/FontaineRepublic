package com.fontainerepublic.client.landclaim;

import java.util.Objects;

/**
 * Pure dependency-free response-target correlation helper
 * (FR-LAND-CLAIM-001-FIX-01 F3).
 *
 * <p>The S2C land result packets echo the canonical request target
 * {@code dimension + x + y + z}. This class decides whether an echoed target
 * belongs to the currently open location screen, so a late response for a
 * previously opened location can never render in a newly opened screen for a
 * different position. It is a plain value helper with no Minecraft or client
 * superclass, so the correlation decision is directly testable in a plain JVM.
 * It never carries authority — the screen uses it only to decide whether to
 * render a display-only result.</p>
 */
public final class LandClaimTarget {

    private LandClaimTarget() {
    }

    /**
     * Whether a result echoing {@code resultDimension + x + y + z} belongs to a
     * screen whose canonical target is {@code currentDimension + cx + cy + cz}.
     * Matching is exact on every axis; a mismatched (usually stale) target is
     * rejected so it cannot alter the current screen's title or button state.
     */
    public static boolean matches(
            String currentDimension,
            int cx,
            int cy,
            int cz,
            String resultDimension,
            int x,
            int y,
            int z
    ) {
        Objects.requireNonNull(currentDimension, "currentDimension");
        Objects.requireNonNull(resultDimension, "resultDimension");
        if (!currentDimension.equals(resultDimension)) {
            return false;
        }
        return x == cx && y == cy && z == cz;
    }
}
