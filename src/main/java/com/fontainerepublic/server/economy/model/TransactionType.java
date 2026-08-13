package com.fontainerepublic.server.economy.model;

/**
 * Kind of an immutable Economy transaction (FR-ECO-001-A §3.3, aligned by
 * FR-ECO-001-C-ACCOUNT-ALIGN-01 §2.4).
 *
 * <p>Ordinary player services implement exactly {@code TRANSFER}. The system
 * {@code DEPOSIT} (central-bank issuance: treasury to a personal account,
 * {@code from == null}) and {@code WITHDRAWAL} (central-bank withdrawal:
 * personal account back to the treasury, {@code to == null}) belong to the
 * on-site official Central-Bank interface (FR-ECO-002-A): every such record
 * is created through the officially gated {@code /fr bank} surface only, so
 * no ordinary money-creation or destruction path can exist.</p>
 */
public enum TransactionType {

    /** Ordinary player-to-player transfer; conserves total supply. */
    TRANSFER,

    /** Official issuance: system (treasury) to a personal account. */
    DEPOSIT,

    /** Official withdrawal: personal account to the system (treasury). */
    WITHDRAWAL
}
