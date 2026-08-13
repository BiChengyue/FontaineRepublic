package com.fontainerepublic.server.institutionaccess.api;

import com.fontainerepublic.server.institutionaccess.model.Terminal;

import java.util.Objects;

/**
 * Result of one terminal mutation (FR-INST-002-A §7).
 *
 * @param kind     what was committed (or attempted idempotently)
 * @param terminal the terminal after the mutation
 * @param applied  true when the state actually changed (a no-op request
 *                 commits nothing and reports {@code false})
 * @param timestamp server-assigned mutation timestamp
 */
public record TerminalReceipt(
        TerminalChangeKind kind,
        Terminal terminal,
        boolean applied,
        long timestamp
) {

    public TerminalReceipt {
        kind = Objects.requireNonNull(kind, "kind");
        terminal = Objects.requireNonNull(terminal, "terminal");
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must not be negative");
        }
    }
}
