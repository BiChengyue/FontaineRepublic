package com.fontainerepublic.server.playerdata.persistence;

import java.util.UUID;

/**
 * Raised when a caller attempts to replace a record using an old revision.
 */
public final class StalePlayerDataRevisionException extends IllegalStateException {
    public StalePlayerDataRevisionException(
            UUID playerId,
            long expectedRevision,
            long actualRevision
    ) {
        super(
                "Stale player-data revision for " + playerId
                        + ": expected " + expectedRevision
                        + ", actual " + actualRevision
        );
    }
}
