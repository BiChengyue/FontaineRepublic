package com.fontainerepublic.server.parliament.model;

import java.util.Objects;

/**
 * Immutable, value-style bill (FR-PAR-001-A §3.3).
 *
 * <p>A bill is born when a ballot passes: it carries the same closed legal
 * state machine as its proposal (born at {@code APPROVED}), the passing
 * timestamp, and a revision that advances exactly once per later transition
 * (publication/activation are deferred revisions). {@code executionModule}
 * is intentionally absent — Parliament never executes laws and never decides
 * which module will execute a bill.</p>
 */
public record Bill(
        int schemaVersion,
        BillId billId,
        ProposalId proposalId,
        BillState state,
        long passedAt,
        long recordRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public Bill {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported bill schema version: " + schemaVersion
            );
        }
        billId = Objects.requireNonNull(billId, "billId");
        proposalId = Objects.requireNonNull(proposalId, "proposalId");
        state = Objects.requireNonNull(state, "state");
        if (passedAt <= 0) {
            throw new IllegalArgumentException(
                    "passedAt must be a positive epoch millisecond"
            );
        }
        if (recordRevision <= 0) {
            throw new IllegalArgumentException("recordRevision must be positive");
        }
    }

    /** Replacement bill in a new state: revision incremented exactly once. */
    public Bill withState(BillState newState) {
        Objects.requireNonNull(newState, "newState");
        return new Bill(
                schemaVersion,
                billId,
                proposalId,
                newState,
                passedAt,
                recordRevision + 1
        );
    }
}
