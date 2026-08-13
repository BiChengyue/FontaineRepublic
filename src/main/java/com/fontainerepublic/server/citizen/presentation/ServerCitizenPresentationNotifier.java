package com.fontainerepublic.server.citizen.presentation;

import com.fontainerepublic.common.network.display.CitizenInfoPacket;
import com.fontainerepublic.server.citizen.model.CitizenRecord;
import com.fontainerepublic.server.network.NetworkSendService;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Production S2C presentation sender of the citizen module
 * (FR-CLIENT-001-IMPL-B2). Every send goes through
 * {@link NetworkSendService#trySendToPlayer}; an absent remote channel,
 * non-live connection, offline player, or any runtime failure is a
 * best-effort no-op that never affects the business result (no-client
 * parity). The registry number is resolved from the authoritative subject
 * registry (the citizen record is always bound to a subject); when the
 * subject cannot be resolved the presentation is skipped entirely. The
 * online-player resolver is injected so tests can substitute an
 * offline/absent view without a live server.
 */
public final class ServerCitizenPresentationNotifier implements CitizenPresentationNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ServerCitizenPresentationNotifier.class
    );

    private final NetworkSendService sendService;
    private final SubjectRegistryService subjectRegistry;
    private final LongSupplier clock;
    private final Function<UUID, Optional<ServerPlayer>> onlinePlayer;

    public ServerCitizenPresentationNotifier(
            NetworkSendService sendService,
            SubjectRegistryService subjectRegistry,
            LongSupplier clock,
            Function<UUID, Optional<ServerPlayer>> onlinePlayer
    ) {
        this.sendService = Objects.requireNonNull(sendService, "sendService");
        this.subjectRegistry = Objects.requireNonNull(subjectRegistry, "subjectRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
    }

    @Override
    public void syncCitizen(UUID playerId, CitizenRecord record) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(record, "record");
        Optional<ServerPlayer> player = onlinePlayer.apply(playerId);
        if (player.isEmpty()) {
            return;
        }
        Optional<String> registryNumber = subjectRegistry.findBySubjectId(record.subjectId())
                .map(SubjectRecord::registryNumber)
                .map(number -> number.display());
        if (registryNumber.isEmpty()) {
            LOGGER.debug(
                    "[Citizen] Display sync skipped for {}: subject {} not resolvable",
                    playerId,
                    record.subjectId()
            );
            return;
        }
        send(player.get(), new CitizenInfoPacket(
                registryNumber.get(),
                record.status().name(),
                record.rank().name(),
                record.firstCitizenAt(),
                now()
        ));
    }

    private void send(ServerPlayer player, CitizenInfoPacket message) {
        try {
            NetworkSendService.SendResult result = sendService.trySendToPlayer(player, message);
            if (result != NetworkSendService.SendResult.SENT) {
                LOGGER.debug(
                        "[Citizen] Display sync skipped for {}: {}",
                        player.getUUID(),
                        result
                );
            }
        } catch (RuntimeException failure) {
            LOGGER.debug(
                    "[Citizen] Display sync failed for {}: {}",
                    player.getUUID(),
                    failure.getMessage()
            );
        }
    }

    private long now() {
        long value = clock.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("clock returned a non-positive timestamp");
        }
        return value;
    }
}
