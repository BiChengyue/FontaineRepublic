package com.fontainerepublic.server.institution.presentation;

import com.fontainerepublic.common.network.display.GovernmentInfoPacket;
import com.fontainerepublic.common.network.display.ParliamentInfoPacket;
import com.fontainerepublic.server.government.api.GovernmentService;
import com.fontainerepublic.server.government.api.MinistryProjection;
import com.fontainerepublic.server.network.NetworkSendService;
import com.fontainerepublic.server.parliament.api.ParliamentService;
import com.fontainerepublic.server.parliament.api.ProposalProjection;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Login snapshot sender of the government/parliament public summaries
 * (FR-CLIENT-001-IMPL-B3a; FR-CLIENT-001-B3 §3/§4). A lightweight,
 * non-module server-side component: it reads only the existing bounded public
 * projections ({@link GovernmentService#ministries()},
 * {@link GovernmentService#positionsByMinistry} and
 * {@link ParliamentService#proposals}) and sends two display payloads, so the
 * government/parliament module definitions are untouched.
 *
 * <p>Institution data changes rarely, so the first release sends one snapshot
 * per login; live change pushes are a later stage. Every send goes through
 * {@link NetworkSendService#trySendToPlayer}: an absent remote channel,
 * non-live connection, offline player, missing module service, or any runtime
 * failure is a best-effort no-op that never affects the login (no-client
 * parity). The online-player resolver is injected so tests can substitute an
 * offline/absent view without a live server.</p>
 */
public final class InstitutionPresentationSync {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            InstitutionPresentationSync.class
    );

    private final NetworkSendService sendService;
    private final Supplier<Optional<GovernmentService>> governmentService;
    private final Supplier<Optional<ParliamentService>> parliamentService;
    private final LongSupplier clock;
    private final Function<UUID, Optional<ServerPlayer>> onlinePlayer;

    public InstitutionPresentationSync(
            NetworkSendService sendService,
            Supplier<Optional<GovernmentService>> governmentService,
            Supplier<Optional<ParliamentService>> parliamentService,
            LongSupplier clock,
            Function<UUID, Optional<ServerPlayer>> onlinePlayer
    ) {
        this.sendService = Objects.requireNonNull(sendService, "sendService");
        this.governmentService = Objects.requireNonNull(
                governmentService, "governmentService"
        );
        this.parliamentService = Objects.requireNonNull(
                parliamentService, "parliamentService"
        );
        this.clock = Objects.requireNonNull(clock, "clock");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
    }

    /**
     * Assembles and sends the government and parliament public snapshots for
     * one online player. Never throws: every failure path — including a
     * throwing online-player resolver or service supplier — is a logged
     * no-op, so a presentation problem can never affect the login.
     */
    public void sync(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        try {
            Optional<ServerPlayer> player = onlinePlayer.apply(playerId);
            if (player.isEmpty()) {
                return;
            }
            syncGovernment(player.get());
            syncParliament(player.get());
        } catch (RuntimeException failure) {
            LOGGER.debug(
                    "[Institution] Display sync failed for {}: {}",
                    playerId,
                    failure.getMessage()
            );
        }
    }

    private void syncGovernment(ServerPlayer player) {
        governmentService.get().ifPresentOrElse(
                service -> {
                    try {
                        List<GovernmentInfoPacket.MinistryEntry> entries =
                                new ArrayList<>();
                        for (MinistryProjection ministry : service.ministries()) {
                            if (entries.size()
                                    >= GovernmentInfoPacket.MAX_MINISTRIES) {
                                break;
                            }
                            int positionCount = service.positionsByMinistry(
                                    ministry.ministryId()
                            ).size();
                            entries.add(new GovernmentInfoPacket.MinistryEntry(
                                    ministry.ministryId().canonicalKey(),
                                    ministry.name(),
                                    positionCount
                            ));
                        }
                        send(player, new GovernmentInfoPacket(entries, now()));
                    } catch (RuntimeException failure) {
                        LOGGER.debug(
                                "[Institution] Government display sync failed for {}: {}",
                                player.getUUID(),
                                failure.getMessage()
                        );
                    }
                },
                () -> LOGGER.debug(
                        "[Institution] Government service is unavailable at login for {}",
                        player.getUUID()
                )
        );
    }

    private void syncParliament(ServerPlayer player) {
        parliamentService.get().ifPresentOrElse(
                service -> {
                    try {
                        List<ParliamentInfoPacket.ProposalEntry> entries =
                                new ArrayList<>();
                        for (ProposalProjection proposal : service.proposals(
                                0,
                                ParliamentService.MAX_PROJECTION_SIZE
                        )) {
                            if (entries.size()
                                    >= ParliamentInfoPacket.MAX_PROPOSALS) {
                                break;
                            }
                            entries.add(new ParliamentInfoPacket.ProposalEntry(
                                    proposal.proposalId().canonicalKey(),
                                    proposal.state().name(),
                                    proposal.normLevel().name(),
                                    proposal.title()
                            ));
                        }
                        send(player, new ParliamentInfoPacket(entries, now()));
                    } catch (RuntimeException failure) {
                        LOGGER.debug(
                                "[Institution] Parliament display sync failed for {}: {}",
                                player.getUUID(),
                                failure.getMessage()
                        );
                    }
                },
                () -> LOGGER.debug(
                        "[Institution] Parliament service is unavailable at login for {}",
                        player.getUUID()
                )
        );
    }

    private void send(ServerPlayer player, Object message) {
        try {
            NetworkSendService.SendResult result =
                    sendService.trySendToPlayer(player, message);
            if (result != NetworkSendService.SendResult.SENT) {
                LOGGER.debug(
                        "[Institution] Display sync skipped for {}: {}",
                        player.getUUID(),
                        result
                );
            }
        } catch (RuntimeException failure) {
            LOGGER.debug(
                    "[Institution] Display sync failed for {}: {}",
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
