package com.fontainerepublic.server.economy.persistence;

/**
 * Implementation-design bounds of the economy store (FR-ECO-001-A §5.4 and
 * FR-ECO-001-C §5.3; concrete caps are an implementation choice, values below
 * are the design defaults).
 *
 * @param maxAccounts                    maximum live accounts before provisioning
 *                                       fails closed
 * @param maxTransactions                maximum operational transaction buffer;
 *                                       oldest entries are pruned without
 *                                       authority loss
 * @param maxMemoLength                  maximum transfer memo length in
 *                                       characters (0 = memo disabled)
 * @param maxBalance                     maximum single account balance
 * @param maxPendingNotificationsPerSubject maximum pending incoming-transfer
 *                                       summaries per subject; oldest dropped
 *                                       when full
 * @param maxPageSize                    maximum records per history page
 * @param maxTotalBytes                  serialized (uncompressed) namespace
 *                                       byte budget; commits beyond it fail
 *                                       closed
 * @param transferCooldownMillis         server-owned minimum interval between
 *                                       transfers from the same actor; an
 *                                       abuse-control gate, never monetary
 *                                       authority (0 = disabled)
 */
public record EconomyLimits(
        int maxAccounts,
        int maxTransactions,
        int maxMemoLength,
        long maxBalance,
        int maxPendingNotificationsPerSubject,
        int maxPageSize,
        int maxTotalBytes,
        long transferCooldownMillis
) {

    public static final long DEFAULT_MAX_BALANCE = Long.MAX_VALUE / 2;

    public static final EconomyLimits DEFAULT = new EconomyLimits(
            10_000,
            100_000,
            128,
            DEFAULT_MAX_BALANCE,
            100,
            50,
            8 * 1024 * 1024,
            1_000L
    );

    public EconomyLimits {
        if (maxAccounts <= 0) {
            throw new IllegalArgumentException("maxAccounts must be positive");
        }
        if (maxTransactions <= 0) {
            throw new IllegalArgumentException("maxTransactions must be positive");
        }
        if (maxMemoLength < 0) {
            throw new IllegalArgumentException("maxMemoLength must not be negative");
        }
        if (maxBalance <= 0) {
            throw new IllegalArgumentException("maxBalance must be positive");
        }
        if (maxPendingNotificationsPerSubject <= 0) {
            throw new IllegalArgumentException(
                    "maxPendingNotificationsPerSubject must be positive"
            );
        }
        if (maxPageSize <= 0) {
            throw new IllegalArgumentException("maxPageSize must be positive");
        }
        if (maxTotalBytes <= 0) {
            throw new IllegalArgumentException("maxTotalBytes must be positive");
        }
        if (transferCooldownMillis < 0) {
            throw new IllegalArgumentException(
                    "transferCooldownMillis must not be negative"
            );
        }
    }
}
