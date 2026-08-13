package com.fontainerepublic.server.emergency.api;

import com.fontainerepublic.server.emergency.model.EmergencyActorSource;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared emergency authority service contract (FR-EMG-001-A §3.1,
 * implementation task §3.1).
 *
 * <p>The service owns: actor verification (configured Hydro Archon UUID +
 * strict local console), the server-runtime confirmation-token table
 * (30-second single-use, ISSUED -> CLAIMED -> CONSUMED), the shared
 * attempt/configuration journal, reconciliation watermarks, and the
 * authority-configuration lifecycle. Every operation fails closed whenever
 * authority, target, mutation, or audit integrity cannot be established.</p>
 *
 * <p>No client packet and no ordinary OP/RCON/command-block/function source
 * may confirm authoritatively; the service revalidates the trusted actor at
 * the final mutation boundary.</p>
 */
public interface EmergencyService {

    /**
     * Two-step preview: validates actor, action availability, target,
     * category, reason, and parameters; reads the relevant business revision;
     * and issues a server-side unguessable single-use confirmation token.
     */
    PreviewResult preview(EmergencyRequest request, EmergencyActorSource source);

    /**
     * Confirmation: atomically claims the token (ISSUED -> CLAIMED) before
     * final validation; revalidates everything at the final mutation
     * boundary; every terminal outcome consumes the token.
     */
    ConfirmResult confirm(String token, EmergencyActorSource source);

    /**
     * Restricted projection of one journal attempt (inspect matrix): only the
     * authorized redacted projection is exposed.
     */
    Optional<EmergencyInspection> inspect(long attemptId);

    /**
     * First bootstrap: only the real local Dedicated Server console may
     * establish the first Hydro Archon UUID (UNSET -> ACTIVE, audited).
     */
    ConfigureResult bootstrapAuthority(
            UUID hydroArchonUuid,
            String reason,
            EmergencyActorSource source
    );

    /**
     * Controlled change: an already-authorized Hydro Archon or the real local
     * console stages a new UUID for the next server runtime; the running
     * snapshot stays immutable.
     */
    ConfigureResult stageAuthority(
            UUID nextHydroArchonUuid,
            String reason,
            EmergencyActorSource source
    );

    /**
     * Startup acceptance: accepts the staged value only when its digest and
     * revision match the durable configuration-change receipt; otherwise the
     * runtime fails closed (no player UUID authority).
     *
     * @return whether the staged value was accepted as the running snapshot
     */
    boolean acceptStagedAtStartup();

    /**
     * Audited recovery path: only the real local console may repair a drift
     * or recover from a failed staged acceptance.
     */
    ConfigureResult recoverAuthority(
            UUID hydroArchonUuid,
            String reason,
            EmergencyActorSource source
    );

    /** Restricted status projection. */
    EmergencyStatus status();

    /** Reconciles one registered receipt provider against its watermark. */
    ReconciliationResult reconcile(String providerId);

    /** Invalidates all pending confirmation tokens (server stop). */
    void invalidateTokens();
}
