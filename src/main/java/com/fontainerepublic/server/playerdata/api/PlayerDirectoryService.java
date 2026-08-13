package com.fontainerepublic.server.playerdata.api;

import com.fontainerepublic.server.playerdata.model.PlayerNameResolution;

/**
 * Server-authoritative, read-only exact-name directory API
 * (FR-DATA-003-A §10).
 *
 * <p>Resolves one server-verified game-name alias to one Minecraft UUID only
 * when the result is safe and unambiguous. The service exposes no mutation
 * method, repository, codec, NBT, live map, iterator, stream, page, count,
 * bulk, prefix, or fuzzy lookup, and no historical-owner or ambiguity
 * participant data.</p>
 */
public interface PlayerDirectoryService {

    /**
     * Resolves one exact game name against the authoritative directory.
     *
     * <p>Input is validated exactly as received (no trimming). The closed
     * result is one of {@code UNIQUE_CURRENT} (carrying the UUID),
     * {@code UNKNOWN}, {@code RETIRED}, {@code AMBIGUOUS}, or
     * {@code INVALID_INPUT}. Only {@code UNIQUE_CURRENT} exposes a UUID; the
     * other kinds share a bounded public message.</p>
     */
    PlayerNameResolution resolveExactGameName(String input);
}
