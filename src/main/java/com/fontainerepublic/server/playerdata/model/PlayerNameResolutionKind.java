package com.fontainerepublic.server.playerdata.model;

/**
 * Closed classification of one exact-name resolution (FR-DATA-003-A §4).
 *
 * <p>Only {@link #UNIQUE_CURRENT} may carry a UUID; {@code UNKNOWN},
 * {@code RETIRED}, and {@code AMBIGUOUS} never expose one. Consumers must
 * handle the closed kind set exhaustively and must not treat a successful
 * resolution as authentication of the command actor.</p>
 */
public enum PlayerNameResolutionKind {
    /** Exactly one UUID is the current known owner and no other UUID was ever observed. */
    UNIQUE_CURRENT,
    /** No directory entry exists. */
    UNKNOWN,
    /** Known, one historical UUID, not that UUID's current known name. */
    RETIRED,
    /** More than one UUID is or has been associated, or uniqueness cannot be proven. */
    AMBIGUOUS,
    /** Input fails the exact syntax contract. */
    INVALID_INPUT
}
