package com.fontainerepublic.server.institutionaccess.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable anchored block position of an institution terminal
 * (FR-INST-001-A §6.2).
 *
 * <p>The dimension is a canonical Minecraft resource location and the block
 * coordinates are supplied by the authoritative registration request or by
 * decoded persisted state — the module never carries any hard-coded
 * coordinate value. Coordinates are inclusive block coordinates.</p>
 */
public record TerminalPosition(
        String dimension,
        int x,
        int y,
        int z
) {

    /** Canonical dimension keys are Minecraft resource locations. */
    private static final Pattern DIMENSION_PATTERN =
            Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    public TerminalPosition {
        dimension = Objects.requireNonNull(dimension, "dimension");
        if (!DIMENSION_PATTERN.matcher(dimension).matches()) {
            throw new IllegalArgumentException(
                    "Dimension must be a canonical resource location: " + dimension
            );
        }
    }

    /** Whether this position is inside the given inclusive region bounds. */
    public boolean inside(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /**
     * Deterministic integrity digest binding this position to a terminal
     * (FR-INST-001-A §6.2 anti-clone). Any NBT tampering with the position
     * without recomputing the digest fails closed.
     */
    public String integrityDigest() {
        return com.fontainerepublic.server.institutionaccess.service.IntegrityDigest.of(
                dimension, x, y, z
        );
    }
}
