package com.fontainerepublic.server.economy.api;

import java.util.List;
import java.util.Objects;

/**
 * Bounded page of economy records (FR-ECO-001-C §5.2: list/history output is
 * paginated and bounded).
 *
 * <p>{@code nextAfterId} is the cursor for the following page
 * ({@code afterId} of the next call); {@code hasMore} reports whether more
 * records exist beyond this page.</p>
 */
public record EconomyPage<T>(List<T> items, long nextAfterId, boolean hasMore) {

    public EconomyPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (nextAfterId < 0) {
            throw new IllegalArgumentException("nextAfterId must not be negative");
        }
    }

    public static <T> EconomyPage<T> empty(long afterId) {
        return new EconomyPage<>(List.of(), afterId, false);
    }
}
