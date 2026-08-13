package com.fontainerepublic.server.citizen.presentation;

import com.fontainerepublic.server.citizen.model.CitizenRecord;

import java.util.UUID;

/**
 * Presentation-side sink of the citizen module (FR-CLIENT-001-IMPL-B2).
 *
 * <p>The notifier is strictly advisory: every invocation is best-effort and
 * presence-filtered, and a failure, absent channel, or offline player never
 * alters the business result (no-client parity). The production
 * implementation sends the {@code CitizenInfoPacket} display payload through
 * {@code NetworkSendService.trySendToPlayer}; tests substitute a fake.</p>
 */
public interface CitizenPresentationNotifier {

    /**
     * Citizen record ready (provisioning or refresh): sends the receiver's
     * own citizen-identity card payload when the player is online. The
     * registry number is resolved from the authoritative subject registry by
     * the implementation.
     */
    void syncCitizen(UUID playerId, CitizenRecord record);
}
