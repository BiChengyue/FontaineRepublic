package com.fontainerepublic.server.trade.xp;

/**
 * Server-authoritative, integer-only experience-point arithmetic for the
 * trade module (FR-TRADE-002-A §8.2).
 *
 * <p>Secure Trade's upstream implementation converts levels and totals with
 * floating point ({@code Math.round(2.5D * level * level - 40.5D * level +
 * 360D)}); FR-TRADE-002-A §8.2 forbids floating point and requires one
 * integer-only utility whose level-to-total and total-to-level rules are
 * unit-tested against Minecraft 1.20.1 boundaries. This class re-implements
 * the canonical Minecraft 1.20.1 {@code ExperienceOrb}/{@code Player}
 * formulas using exact rational arithmetic (multiplication + floor-division
 * by 2), which yields the same integers that the floating-point formulas
 * produce for every level &lt;= 2,145 (the levels where the two piecewise
 * polynomials are tabulated in {@link #xpForLevel}).</p>
 *
 * <p>Canonical Minecraft segments (level {@code L}):</p>
 * <ul>
 *   <li>{@code L &lt;= 16}: {@code L*L + 6*L}</li>
 *   <li>{@code 17 &lt;= L &lt;= 31}: {@code (5*L*L - 81*L + 720) / 2}
 *       (equivalent to {@code 2.5L^2 - 40.5L + 360})</li>
 *   <li>{@code L &gt;= 32}: {@code (9*L*L - 325*L + 4440) / 2}
 *       (equivalent to {@code 4.5L^2 - 162.5L + 2220})</li>
 * </ul>
 *
 * <p>These numerators are always even, so the floor-division is exact and
 * never truncates a valid half step. Values may overflow {@code long} for
 * absurd level inputs; callers bound levels before entry, and
 * {@link #xpForLevel} guards against negative inputs.</p>
 */
public final class ExperiencePointMath {

    /** Highest level the vanilla formula is meaningful for before long overflow. */
    public static final int MAX_SAFE_LEVEL = 2_147_483_647;

    private ExperiencePointMath() {
    }

    /**
     * Total experience points required to reach exactly {@code level}
     * (the cumulative sum of each per-level requirement from level 0).
     * Integer-only; matches Minecraft 1.20.1 for {@code level >= 0}.
     */
    public static long xpForLevel(int level) {
        if (level <= 0) {
            return 0L;
        }
        if (level <= 16) {
            return (long) level * level + 6L * level;
        }
        if (level <= 31) {
            // (5L^2 - 81L + 720) / 2, exact because the numerator is even.
            long numerator = 5L * level * level - 81L * level + 720L;
            return Math.floorDiv(numerator, 2L);
        }
        // (9L^2 - 325L + 4440) / 2, exact because the numerator is even.
        long numerator = 9L * level * level - 325L * level + 4440L;
        return Math.floorDiv(numerator, 2L);
    }

    /**
     * Experience points required to advance from {@code level} to
     * {@code level + 1} (Minecraft {@code getXpNeededForNextLevel}). Matches
     * 1.20.1: {@code 2*level + 7} at or below 15, else {@code 5*level - 38}.
     */
    public static long xpNeededForNextLevel(int level) {
        if (level <= 15) {
            return 2L * level + 7L;
        }
        return 5L * level - 38L;
    }

    /**
     * The highest level whose total XP is {@code <= xp} (binary search over
     * the monotonic {@link #xpForLevel} curve). Integer-only; returns 0 for
     * non-positive input.
     */
    /**
     * Levels beyond this have a cumulative total that already exceeds
     * {@code Integer.MAX_VALUE} (the cap of {@code Player.totalExperience});
     * search never climbs above it, bounding this method independently of the
     * input.
     */
    private static final int MAX_MEANINGFUL_LEVEL = 21_863;

    public static int levelForXp(long xp) {
        if (xp <= 0L) {
            return 0;
        }
        // Exponential doubling to an upper bound whose total strictly exceeds
        // xp; the bound is capped so the loop always terminates in <= 22 steps.
        int low = 0;
        int high = 1;
        while (high < MAX_MEANINGFUL_LEVEL && xpForLevel(high) <= xp) {
            if (high > MAX_MEANINGFUL_LEVEL / 2) {
                high = MAX_MEANINGFUL_LEVEL;
                break;
            }
            high *= 2;
        }
        if (xpForLevel(high) <= xp) {
            return high;
        }
        while (low + 1 < high) {
            int mid = low + (high - low) / 2;
            if (xpForLevel(mid) <= xp) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /**
     * Reconstructs the player's total experience points from
     * {@code level + progress}, matching the authoritative server counter
     * ({@code Player.totalExperience}). Integer-only via
     * {@link #xpNeededForNextLevel} and a floor-aligned progress fraction.
     *
     * @param level   current experience level (&gt;= 0)
     * @param progress fractional progress toward the next level in {@code [0, 1)}
     */
    public static long totalXpFor(int level, float progress) {
        long base = xpForLevel(Math.max(0, level));
        if (level < 0 || progress <= 0.0f) {
            return base;
        }
        float bounded = Math.min(1.0f, Math.max(0.0f, progress));
        long needed = xpNeededForNextLevel(level);
        // Math.round(value) == Math.floor(value + 0.5) for this bounded fraction.
        return base + (long) Math.floor((double) bounded * needed + 0.5d);
    }
}
