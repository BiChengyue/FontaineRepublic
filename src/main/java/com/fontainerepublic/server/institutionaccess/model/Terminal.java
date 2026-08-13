package com.fontainerepublic.server.institutionaccess.model;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable, value-style authoritative institution terminal (FR-INST-002-A
 * §3).
 *
 * <p>A terminal is anchored to a registered facility, carries the institution
 * type of that facility, an allowed capability set, an anchored position with
 * an integrity digest (anti-clone, FR-INST-001-A §6.2), and a lifecycle
 * state. {@code secure} marks a secure-operations terminal: issuing an
 * {@code ONSITE_OFFICIAL_DUTY} context on it selects the high-risk workflow
 * (FR-INST-001-B §3.3). Every committed mutation replaces the terminal and
 * increments {@code terminalRevision} exactly once.</p>
 */
public record Terminal(
        int schemaVersion,
        TerminalId terminalId,
        FacilityId facilityId,
        InstitutionType institutionType,
        TerminalPosition position,
        Set<CapabilityClass> capabilitySet,
        TerminalState state,
        boolean secure,
        String integrity,
        long terminalRevision
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public Terminal {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported terminal schema version: " + schemaVersion
            );
        }
        terminalId = Objects.requireNonNull(terminalId, "terminalId");
        facilityId = Objects.requireNonNull(facilityId, "facilityId");
        institutionType = Objects.requireNonNull(institutionType, "institutionType");
        position = Objects.requireNonNull(position, "position");
        capabilitySet = Set.copyOf(Objects.requireNonNull(capabilitySet, "capabilitySet"));
        if (capabilitySet.isEmpty()) {
            throw new IllegalArgumentException(
                    "A terminal must allow at least one capability"
            );
        }
        state = Objects.requireNonNull(state, "state");
        integrity = Objects.requireNonNull(integrity, "integrity");
        if (!integrity.equals(position.integrityDigest())) {
            throw new IllegalArgumentException(
                    "Terminal integrity digest does not match its anchored position"
            );
        }
        if (terminalRevision <= 0) {
            throw new IllegalArgumentException("terminalRevision must be positive");
        }
    }

    /** Replacement terminal with a new state: revision incremented once. */
    public Terminal withState(TerminalState newState) {
        Objects.requireNonNull(newState, "newState");
        return new Terminal(
                schemaVersion,
                terminalId,
                facilityId,
                institutionType,
                position,
                capabilitySet,
                newState,
                secure,
                integrity,
                terminalRevision + 1
        );
    }

    /** Deterministic iteration order for codec and presentation. */
    public Set<CapabilityClass> orderedCapabilities() {
        return new TreeSet<>(capabilitySet);
    }
}
