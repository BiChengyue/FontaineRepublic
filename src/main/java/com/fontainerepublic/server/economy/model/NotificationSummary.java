package com.fontainerepublic.server.economy.model;

import com.fontainerepublic.server.registry.model.SubjectId;

import java.util.Objects;

/**
 * Bounded, participant-safe summary of an incoming transfer that is delivered
 * to the recipient on next login (FR-ECO-001-C §6).
 *
 * <p>Notification state is owned by Economy and committed in the same
 * replacement snapshot as the balances and the transaction; it never
 * duplicates an authoritative balance and is never stored in PlayerData.
 * {@code notificationId} is the acknowledgement key; the referenced
 * transaction may already have been pruned from the bounded operational
 * buffer, so only its id is retained.</p>
 *
 * <p>{@code from} identifies the sender subject of an ordinary incoming
 * transfer. It is {@code null} for a system-originated movement — the
 * emergency {@code ISSUE}/{@code RECLAIM} notification (FR-ECO-001-C §6/§12)
 * — mirroring the {@code null} system participant of the underlying
 * transaction.</p>
 */
public record NotificationSummary(
        int schemaVersion,
        long notificationId,
        long transactionId,
        long timestamp,
        SubjectId from,
        long amount,
        String memo
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public NotificationSummary {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported notification schema version: " + schemaVersion
            );
        }
        if (notificationId <= 0) {
            throw new IllegalArgumentException("Notification id must be positive");
        }
        if (transactionId <= 0) {
            throw new IllegalArgumentException("Transaction id must be positive");
        }
        if (timestamp <= 0) {
            throw new IllegalArgumentException(
                    "timestamp must be a positive epoch millisecond"
            );
        }
        // from is null for system-originated movements (emergency ISSUE/RECLAIM).
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
