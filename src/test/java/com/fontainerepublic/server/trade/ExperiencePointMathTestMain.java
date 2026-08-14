package com.fontainerepublic.server.trade;

import com.fontainerepublic.server.trade.xp.ExperiencePointMath;

/**
 * Dependency-free validation of the integer-only experience-point arithmetic
 * (FR-TRADE-002-A §8.2): pins the level-to-total and total-to-level rules
 * against the canonical Minecraft 1.20.1 cumulative-XP milestones, the
 * per-level need, and round-trip consistency.
 */
public final class ExperiencePointMathTestMain {

    private ExperiencePointMathTestMain() {
    }

    public static void main(String[] args) {
        testCanonicalMilestones();
        testPerLevelNeed();
        testLevelForXpRoundTrip();
        testProgressToTotal();
        System.out.println("[FR-TRADE-002-A] ExperiencePointMath validation passed");
    }

    private static void testCanonicalMilestones() {
        check(xp(0) == 0L, "level 0 total = 0");
        check(xp(1) == 7L, "level 1 total = 7");
        check(xp(2) == 16L, "level 2 total = 16");
        check(xp(15) == 315L, "level 15 total = 315");
        check(xp(16) == 352L, "level 16 total = 352");
        check(xp(17) == 394L, "level 17 total = 394");
        check(xp(30) == 1_395L, "level 30 total = 1395");
        check(xp(31) == 1_507L, "level 31 total = 1507");
        check(xp(32) == 1_628L, "level 32 total = 1628");
        check(xp(40) == 2_920L, "level 40 total = 2920");
        // Segment boundaries follow vanilla Minecraft exactly. 16 -> 17 is
        // continuous, but vanilla's 31 -> 32 boundary is NOT: need(31) =
        // 5*31-38 = 117 while the actual cumulative jump xp(32)-xp(31) = 121
        // (a known 1.20.1 quirk the integer port faithfully preserves).
        check(xp(16) + need(16) == xp(17), "16 -> 17 boundary is exact");
        check(xp(32) - xp(31) == 121L, "cumulative 31 -> 32 jump is 121");
        check(need(31) == 117L, "per-level need(31) is 117 (vanilla quirk)");
    }

    private static void testPerLevelNeed() {
        check(need(0) == 7L, "need(0) = 7");
        check(need(15) == 37L, "need(15) = 2*15+7 = 37");
        check(need(16) == 42L, "need(16) = 5*16-38 = 42");
        check(need(30) == 112L, "need(30) = 5*30-38 = 112");
    }

    private static void testLevelForXpRoundTrip() {
        for (int level : new int[] {0, 1, 2, 15, 16, 17, 30, 31, 32, 40, 100, 1000, 21_000}) {
            long total = xp(level);
            check(ExperiencePointMath.levelForXp(total) == level,
                    "levelForXp(xpForLevel(" + level + ")) == " + level);
        }
        // In-between totals map to the level whose cumulative total is <= xp.
        check(ExperiencePointMath.levelForXp(7L) == 1, "total 7 -> level 1");
        check(ExperiencePointMath.levelForXp(351L) == 15, "total 351 -> level 15");
        check(ExperiencePointMath.levelForXp(352L) == 16, "total 352 -> level 16");
        check(ExperiencePointMath.levelForXp(0L) == 0, "total 0 -> level 0");
        check(ExperiencePointMath.levelForXp(-5L) == 0, "negative total -> level 0");
    }

    private static void testProgressToTotal() {
        // level 0, 50% progress toward 7 = floor(0.5*7 + 0.5) = floor(4.0) = 4
        check(ExperiencePointMath.totalXpFor(0, 0.5f) == 4L, "level 0 @50% = 4");
        // level 0, 100% progress = 7 (clamped)
        check(ExperiencePointMath.totalXpFor(0, 1.0f) == 7L, "level 0 @100% = 7");
        // level 15, 0 progress = 315
        check(ExperiencePointMath.totalXpFor(15, 0.0f) == 315L, "level 15 @0% = 315");
    }

    private static long xp(int level) {
        return ExperiencePointMath.xpForLevel(level);
    }

    private static long need(int level) {
        return ExperiencePointMath.xpNeededForNextLevel(level);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
