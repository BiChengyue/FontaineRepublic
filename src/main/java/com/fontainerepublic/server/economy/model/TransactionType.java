package com.fontainerepublic.server.economy.model;

/**
 * Kind of an immutable Economy transaction (FR-ECO-001-A §3.3, aligned by
 * FR-ECO-001-C-ACCOUNT-ALIGN-01 §2.4).
 *
 * <p>Phase 1 player services implement exactly ordinary player-to-player
 * {@code TRANSFER}. System {@code DEPOSIT}/{@code WITHDRAWAL} belong to the
 * deferred official Central-Bank interface and are deliberately absent so no
 * ordinary money-creation or destruction path can exist (FR-ECO-001-C §3.4:
 * no ordinary issuance).</p>
 */
public enum TransactionType {

    /** Ordinary player-to-player transfer; conserves total supply. */
    TRANSFER
}
