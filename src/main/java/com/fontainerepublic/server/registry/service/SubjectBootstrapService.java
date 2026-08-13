package com.fontainerepublic.server.registry.service;

import com.fontainerepublic.server.registry.model.BootstrapAttemptResult;
import com.fontainerepublic.server.registry.model.BootstrapSourceClassification;
import com.fontainerepublic.server.registry.model.BootstrapStatus;

import java.util.UUID;

/**
 * Console-only, audited one-time binding of the original Hydro Archon personal
 * subject ({@code 10-000001-61}) to a designated player UUID
 * (FR-ID-BOOTSTRAP-001-A §3/§5).
 *
 * <p>Only the local dedicated-server console may invoke the binding; the
 * classification is revalidated at the final mutation boundary. Every attempt
 * (rejected source/input, incomplete, idempotent replay, persistence failure,
 * success) first appends a PENDING trail record through the FR-CORE-002 durable
 * gate and then a terminal record. The binding is immutable once committed —
 * there is no unbind, reassign, or correction path.</p>
 */
public interface SubjectBootstrapService {

    /**
     * Attempts the one-time original-person binding for the target UUID.
     *
     * @param playerUuid canonical target player UUID
     * @param reason     mandatory, bounded operator reason (audited as digest
     *                   only)
     * @param source     classified invocation source
     * @return the stable terminal result code
     */
    BootstrapAttemptResult bootstrapOriginalPerson(
            UUID playerUuid,
            String reason,
            BootstrapSourceClassification source
    );

    /**
     * Read-only bootstrap status for the {@code /fr admin bootstrap status}
     * command. Exposes no raw UUID, reason, or FR-EMG information.
     */
    BootstrapStatus status();
}
