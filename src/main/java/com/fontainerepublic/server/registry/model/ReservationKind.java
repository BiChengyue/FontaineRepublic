package com.fontainerepublic.server.registry.model;

/**
 * Kind of a registry reservation (FR-ID-001-A §4.5/§6.2).
 *
 * <p>Currently only the two Human-fixed numbers are explicitly reserved in the
 * {@code Reservations} index before their records materialize. Future archival
 * designs may add tombstones for numbers leaving the active maps; ordinary
 * live records are inherently reserved by the {@code Subjects}/{@code Numbers}
 * indexes and need no explicit reservation.</p>
 */
public enum ReservationKind {

    /** A Human-fixed permanent number, never produced by ordinary allocation. */
    FIXED
}
