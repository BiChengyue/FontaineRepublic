package com.fontainerepublic.server.justice.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable record of one judicial state-machine transition (FR-JUS-001-A
 * §5: every transition records actor/time/trigger/before/after/revision).
 *
 * <p>Every transition appends exactly one immutable record carrying the
 * acting citizen (from the validated on-site context), the server time, the
 * trigger, the before/after states (before is empty for the initial
 * filing), and the case revision after the transition. The transition
 * ledger is bounded and append-only within a store snapshot.</p>
 */
public record TransitionRecord(
        int schemaVersion,
        CaseId caseId,
        UUID actor,
        long atMillis,
        CaseTransitionTrigger trigger,
        Optional<CaseState> before,
        CaseState after,
        long recordRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public TransitionRecord {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported transition schema version: " + schemaVersion
            );
        }
        caseId = Objects.requireNonNull(caseId, "caseId");
        actor = Objects.requireNonNull(actor, "actor");
        trigger = Objects.requireNonNull(trigger, "trigger");
        before = before == null ? Optional.empty() : before;
        after = Objects.requireNonNull(after, "after");
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
        final CaseState afterState = after;
        before.ifPresent(state -> {
            if (state == afterState) {
                throw new IllegalArgumentException(
                        "a transition must change the state (before == after)"
                );
            }
        });
    }
}
