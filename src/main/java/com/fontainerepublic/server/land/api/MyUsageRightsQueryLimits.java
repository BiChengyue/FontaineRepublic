package com.fontainerepublic.server.land.api;

/**
 * Query bounds of the self-only bounded my-usage-rights page
 * (FR-LAND-002-A §3.2).
 *
 * <p>{@code limit} must be in {@code [1, MAX_LIMIT]}; the client's default page
 * size is {@link #DEFAULT_LIMIT}. Out-of-range limits are rejected
 * ({@link MyUsageRightsStatus#INVALID_REQUEST}) — never silently clamped
 * upward.</p>
 */
public final class MyUsageRightsQueryLimits {

    /** Client default page size. */
    public static final int DEFAULT_LIMIT = 12;

    /** Hard maximum page size. */
    public static final int MAX_LIMIT = 32;

    private MyUsageRightsQueryLimits() {
    }

    /** Whether {@code limit} is an acceptable page size (1..MAX_LIMIT). */
    public static boolean isValid(int limit) {
        return limit >= 1 && limit <= MAX_LIMIT;
    }

    /**
     * Cursor/revision pairing rule (FR-LAND-002-A §4.2): the first page carries
     * no cursor and {@code expectedStoreRevision == 0}; every continuation page
     * carries a cursor and the returned positive {@code storeRevision}. A
     * cursor paired with revision 0, a negative revision (with or without a
     * cursor), or no cursor with a non-zero revision, is an invalid request
     * and must be rejected at every service/API boundary.
     *
     * @param hasCursor whether the request carries an exclusive cursor
     * @param expectedStoreRevision the expected store revision (0 for a first page)
     * @return {@code true} only for the valid first-page form (no cursor,
     *         revision 0) or the valid continuation form (cursor, revision &gt; 0)
     */
    public static boolean isValidCursorRevision(
            boolean hasCursor,
            long expectedStoreRevision
    ) {
        return hasCursor ? expectedStoreRevision > 0 : expectedStoreRevision == 0;
    }
}
