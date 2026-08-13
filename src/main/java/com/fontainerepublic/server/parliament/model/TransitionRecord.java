package com.fontainerepublic.server.parliament.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable record of one legal state-machine transition (FR-BL-003 §12;
 * FR-BL-005 §12; FR-PAR-001-A §5).
 *
 * <p>Every transition appends exactly one immutable record carrying the
 * acting citizen (from the on-site context), the server time, the trigger,
 * the before/after states (before is empty for the initial submission), and
 * the proposal revision after the transition. The transition ledger is
 * bounded and append-only within a store snapshot.</p>
 */
public record TransitionRecord(
        int schemaVersion,
        ProposalId proposalId,
        UUID actor,
        long atMillis,
        BillTransitionTrigger trigger,
        Optional<BillState> before,
        BillState after,
        long recordRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public TransitionRecord {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported transition schema version: " + schemaVersion
            );
        }
        proposalId = Objects.requireNonNull(proposalId, "proposalId");
        actor = Objects.requireNonNull(actor, "actor");
        trigger = Objects.requireNonNull(trigger, "trigger");
        before = before == null ? Optional.empty() : before;
        after = Objects.requireNonNull(after, "after");
        BillState normalizedAfter = after;
        if (atMillis <= 0) {
            throw new IllegalArgumentException(
                    "atMillis must be a positive epoch millisecond"
            );
        }
        if (recordRevision <= 0) {
            throw new IllegalArgumentException("recordRevision must be positive");
        }
        if (!actor.toString().equals(actor.toString().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("actor must be a canonical UUID");
        }
        before.ifPresent(state -> {
            if (state == normalizedAfter) {
                throw new IllegalArgumentException(
                        "a transition must change the state (before == after)"
                );
            }
        });
    }
}
