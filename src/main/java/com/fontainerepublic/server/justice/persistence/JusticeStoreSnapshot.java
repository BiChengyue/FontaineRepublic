package com.fontainerepublic.server.justice.persistence;

import com.fontainerepublic.server.justice.model.Case;
import com.fontainerepublic.server.justice.model.CaseId;
import com.fontainerepublic.server.justice.model.Evidence;
import com.fontainerepublic.server.justice.model.EvidenceId;
import com.fontainerepublic.server.justice.model.TransitionRecord;
import com.fontainerepublic.server.justice.model.Verdict;
import com.fontainerepublic.server.justice.model.VerdictId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, fully validated representation of the complete
 * {@code "justice"} namespace (FR-JUS-001-A §3.4).
 *
 * <p>The constructor enforces the authoritative invariants — no partially
 * consistent snapshot can exist:</p>
 * <ul>
 *   <li>every map key matches its record's canonical id;</li>
 *   <li>every evidence references an existing case;</li>
 *   <li>every verdict references an existing case;</li>
 *   <li>every transition references an existing case;</li>
 *   <li>transitions are an ordered, immutable ledger (append-only within a
 *       snapshot).</li>
 * </ul>
 * <p>Duplicates, mismatches, or dangling references reject the whole
 * snapshot (fail closed).</p>
 */
public record JusticeStoreSnapshot(
        int storeVersion,
        long storeRevision,
        Map<CaseId, Case> cases,
        Map<EvidenceId, Evidence> evidence,
        Map<VerdictId, Verdict> verdicts,
        List<TransitionRecord> transitions
) {

    public static final int CURRENT_STORE_VERSION = 1;

    public JusticeStoreSnapshot {
        if (storeVersion != CURRENT_STORE_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported justice store version: " + storeVersion
            );
        }
        if (storeRevision < 0) {
            throw new IllegalArgumentException("storeRevision must not be negative");
        }
        cases = Map.copyOf(Objects.requireNonNull(cases, "cases"));
        evidence = Map.copyOf(Objects.requireNonNull(evidence, "evidence"));
        verdicts = Map.copyOf(Objects.requireNonNull(verdicts, "verdicts"));
        transitions = List.copyOf(Objects.requireNonNull(transitions, "transitions"));

        for (Map.Entry<CaseId, Case> entry : cases.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().caseId())) {
                throw invalid(
                        "Cases key " + entry.getKey()
                                + " does not match record caseId "
                                + entry.getValue().caseId()
                );
            }
        }
        for (Map.Entry<EvidenceId, Evidence> entry : evidence.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().evidenceId())) {
                throw invalid(
                        "Evidence key " + entry.getKey()
                                + " does not match record evidenceId "
                                + entry.getValue().evidenceId()
                );
            }
            if (!cases.containsKey(entry.getValue().caseId())) {
                throw invalid(
                        "Evidence " + entry.getKey() + " references missing case "
                                + entry.getValue().caseId()
                );
            }
        }
        for (Map.Entry<VerdictId, Verdict> entry : verdicts.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().verdictId())) {
                throw invalid(
                        "Verdicts key " + entry.getKey()
                                + " does not match record verdictId "
                                + entry.getValue().verdictId()
                );
            }
            if (!cases.containsKey(entry.getValue().caseId())) {
                throw invalid(
                        "Verdict " + entry.getKey() + " references missing case "
                                + entry.getValue().caseId()
                );
            }
        }
        for (TransitionRecord transition : transitions) {
            if (!cases.containsKey(transition.caseId())) {
                throw invalid(
                        "Transition for " + transition.caseId()
                                + " references missing case"
                );
            }
        }
    }

    private static JusticeNbtException invalid(String message) {
        return new JusticeNbtException(message);
    }
}
