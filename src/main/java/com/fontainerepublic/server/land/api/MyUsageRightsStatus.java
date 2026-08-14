package com.fontainerepublic.server.land.api;

/**
 * Closed response status of a self-only bounded my-usage-rights page query
 * (FR-LAND-002-A §3.2/§7).
 *
 * <p>Exactly one of these statuses is returned for every request:</p>
 * <ul>
 *   <li>{@link #OK} — the page was produced (possibly empty when the holder
 *       holds no current right).</li>
 *   <li>{@link #RESET_REQUIRED} — the {@code expectedStoreRevision} did not
 *       match the current store revision; the caller must re-query from the
 *       first page. No entries are returned.</li>
 *   <li>{@link #INVALID_REQUEST} — the request was malformed (invalid limit,
 *       cursor, or negative data). No entries are returned.</li>
 *   <li>{@link #UNAVAILABLE} — a required service (land runtime, PlayerData or
 *       subject registry) failed closed, or the caller failed a hard gate. No
 *       entries are returned.</li>
 * </ul>
 *
 * <p>Only {@link #OK} may carry projection entries; every non-OK status returns
 * zero entries and leaks no cursor/hasMore projection.</p>
 */
public enum MyUsageRightsStatus {
    OK,
    RESET_REQUIRED,
    INVALID_REQUEST,
    UNAVAILABLE
}
