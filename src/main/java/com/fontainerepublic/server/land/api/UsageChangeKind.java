package com.fontainerepublic.server.land.api;

/**
 * The usage mutation a {@link UsageReceipt} reports on.
 */
public enum UsageChangeKind {
    /** A usage right was granted ({@code LandService#grantUsage}). */
    GRANTED,

    /** A usage right was renewed ({@code LandService#renewUsage}). */
    RENEWED,

    /** A usage right was revoked ({@code LandService#revokeUsage}). */
    REVOKED
}
