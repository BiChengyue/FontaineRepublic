package com.fontainerepublic.server.citizen.presentation;

import com.fontainerepublic.server.citizen.api.CitizenReceipt;
import com.fontainerepublic.server.citizen.api.CitizenService;
import com.fontainerepublic.server.citizen.model.CitizenRank;
import com.fontainerepublic.server.citizen.model.CitizenRecord;
import com.fontainerepublic.server.citizen.model.CitizenStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Citizen service decorator that publishes the citizen-identity card after
 * provisioning succeeded (FR-CLIENT-001-IMPL-B2).
 *
 * <p>Every presentation call is advisory and guarded: a throwing notifier,
 * absent remote channel, or offline player never changes the business result
 * (no-client parity). The decorator never intercepts or alters the delegated
 * service's own outcome or exceptions; rank/status mutations stay plain
 * pass-throughs (the first release publishes the card on provisioning only).</p>
 */
public final class PresentationAwareCitizenService implements CitizenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            PresentationAwareCitizenService.class
    );

    private final CitizenService delegate;
    private final CitizenPresentationNotifier notifier;

    public PresentationAwareCitizenService(
            CitizenService delegate,
            CitizenPresentationNotifier notifier
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    @Override
    public CitizenRecord ensureCitizen(UUID playerId) {
        CitizenRecord record = delegate.ensureCitizen(playerId);
        try {
            notifier.syncCitizen(playerId, record);
        } catch (RuntimeException failure) {
            LOGGER.debug(
                    "[Citizen] Presentation sync failed for {}: {}",
                    playerId,
                    failure.getMessage()
            );
        }
        return record;
    }

    @Override
    public Optional<CitizenRecord> getCitizen(UUID playerId) {
        return delegate.getCitizen(playerId);
    }

    @Override
    public CitizenReceipt setRank(UUID playerId, CitizenRank rank) {
        return delegate.setRank(playerId, rank);
    }

    @Override
    public CitizenReceipt setStatus(UUID playerId, CitizenStatus status) {
        return delegate.setStatus(playerId, status);
    }

    @Override
    public java.util.List<CitizenService.CitizenIdentity> activeCitizens(int limit) {
        return delegate.activeCitizens(limit);
    }
}
