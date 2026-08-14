package com.fontainerepublic.server.trade.model;

/**
 * Server-authoritative lifecycle of one trade session (FR-TRADE-001-A §2).
 *
 * <p>Ordinary flow: {@code REQUESTED -> OPEN -> LOCKED(5s) -> EXECUTING ->
 * COMPLETED}. Any state may transition to {@code CANCELLED} (cancel, timeout,
 * disconnect, shutdown); during {@code LOCKED} an agreement toggle or any
 * offer change reverts the session to {@code OPEN} and restarts the
 * countdown. {@code EXECUTING} is transient — the whole atomic exchange runs
 * inside one server tick.</p>
 */
public enum TradePhase {
    REQUESTED,
    OPEN,
    LOCKED,
    EXECUTING,
    COMPLETED,
    CANCELLED
}
