package com.fontainerepublic.server.citizen.api;

/**
 * The aspect a {@link CitizenReceipt} reports on.
 */
public enum CitizenChangeKind {
    /** A rank replacement was requested ({@code CitizenService#setRank}). */
    RANK,

    /** A status replacement was requested ({@code CitizenService#setStatus}). */
    STATUS
}
