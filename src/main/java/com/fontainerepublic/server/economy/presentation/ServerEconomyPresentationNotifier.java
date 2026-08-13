package com.fontainerepublic.server.economy.presentation;

import com.fontainerepublic.common.network.display.BalanceSyncPacket;
import com.fontainerepublic.common.network.display.NotificationPacket;
import com.fontainerepublic.common.network.display.TransactionHistorySyncPacket;
import com.fontainerepublic.common.network.display.TransactionNotifyPacket;
import com.fontainerepublic.server.economy.api.CurrencyPresentation;
import com.fontainerepublic.server.economy.api.EconomyPage;
import com.fontainerepublic.server.economy.api.TransferReceipt;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.network.NetworkSendService;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.OwnerReferenceKind;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Production S2C presentation sender of the economy module (FR-CLIENT-001-A
 * §3.3). Every send goes through {@link NetworkSendService#trySendToPlayer};
 * an absent remote channel, non-live connection, offline player, or any
 * runtime failure is a best-effort no-op that never affects the business
 * result (no-client parity). The subject-to-player resolution is injected so
 * tests can substitute an offline/absent view without a live server.
 */
public final class ServerEconomyPresentationNotifier implements EconomyPresentationNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ServerEconomyPresentationNotifier.class
    );

    /** Fixed counterparty identity of official treasury transactions. */
    private static final String TREASURY_DIGEST_SOURCE = "fontainerepublic:treasury";

    private final NetworkSendService sendService;
    private final SubjectRegistryService subjectRegistry;
    private final LongSupplier clock;
    private final CurrencyPresentation presentation;
    private final Function<UUID, Optional<ServerPlayer>> onlinePlayer;

    public ServerEconomyPresentationNotifier(
            NetworkSendService sendService,
            SubjectRegistryService subjectRegistry,
            LongSupplier clock,
            CurrencyPresentation presentation,
            Function<UUID, Optional<ServerPlayer>> onlinePlayer
    ) {
        this.sendService = Objects.requireNonNull(sendService, "sendService");
        this.subjectRegistry = Objects.requireNonNull(subjectRegistry, "subjectRegistry");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
    }

    @Override
    public void syncAccount(
            UUID playerId,
            EconomyAccount account,
            List<NotificationSummary> pending
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(account, "account");
        Optional<ServerPlayer> player = onlinePlayer.apply(playerId);
        if (player.isEmpty()) {
            return;
        }
        send(player.get(), balanceSync(account));
        send(player.get(), notification(pending));
    }

    @Override
    public void balanceChanged(SubjectId subjectId, EconomyAccount account) {
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(account, "account");
        Optional<ServerPlayer> player = resolvePlayer(subjectId);
        if (player.isEmpty()) {
            return;
        }
        send(player.get(), balanceSync(account));
    }

    @Override
    public void transferCompleted(
            TransferReceipt receipt,
            EconomyAccount from,
            EconomyAccount to
    ) {
        Objects.requireNonNull(receipt, "receipt");
        resolvePlayer(receipt.from()).ifPresent(player -> {
            if (from != null) {
                send(player, balanceSync(from));
            }
            send(player, transactionNotify(
                    receipt,
                    TransactionNotifyPacket.DIRECTION_OUT,
                    digest(receipt.to())
            ));
        });
        resolvePlayer(receipt.to()).ifPresent(player -> {
            if (to != null) {
                send(player, balanceSync(to));
            }
            send(player, transactionNotify(
                    receipt,
                    TransactionNotifyPacket.DIRECTION_IN,
                    digest(receipt.from())
            ));
        });
    }

    @Override
    public void syncHistory(UUID playerId, EconomyPage<EconomyTransaction> page) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(page, "page");
        Optional<ServerPlayer> player = onlinePlayer.apply(playerId);
        if (player.isEmpty()) {
            return;
        }
        Optional<SubjectId> ownSubject = subjectRegistry.findSubjectForPlayer(playerId)
                .map(SubjectRecord::subjectId);
        if (ownSubject.isEmpty()) {
            LOGGER.debug(
                    "[Economy] History sync skipped for {}: player subject not resolvable",
                    playerId
            );
            return;
        }
        send(player.get(), historySync(ownSubject.get(), page));
    }

    // ------------------------------------------------------------------
    // payload assembly
    // ------------------------------------------------------------------

    private BalanceSyncPacket balanceSync(EconomyAccount account) {
        return new BalanceSyncPacket(
                account.balance(),
                presentation.displayName(),
                presentation.symbol(),
                now(),
                account.accountRevision()
        );
    }

    private NotificationPacket notification(List<NotificationSummary> pending) {
        List<NotificationPacket.NotificationEntry> entries = new ArrayList<>();
        for (NotificationSummary summary : pending) {
            if (entries.size() >= NotificationPacket.MAX_ENTRIES) {
                break;
            }
            entries.add(new NotificationPacket.NotificationEntry(
                    summary.notificationId(),
                    summary.amount(),
                    summary.memo()
            ));
        }
        return new NotificationPacket(entries);
    }

    private TransactionNotifyPacket transactionNotify(
            TransferReceipt receipt,
            byte direction,
            String counterpartyDigest
    ) {
        return new TransactionNotifyPacket(
                receipt.transactionId(),
                direction,
                receipt.amount(),
                counterpartyDigest,
                receipt.memo(),
                receipt.timestamp()
        );
    }

    /**
     * Receiver-relative history page: every transaction where the receiver's
     * subject participated, direction and counterparty digest resolved for
     * the receiver. Official treasury transactions (deposit/issue/withdrawal/
     * reclaim) use the fixed treasury digest as the counterparty.
     */
    private TransactionHistorySyncPacket historySync(
            SubjectId ownSubject,
            EconomyPage<EconomyTransaction> page
    ) {
        List<TransactionHistorySyncPacket.HistoryEntry> entries = new ArrayList<>();
        for (EconomyTransaction transaction : page.items()) {
            if (entries.size() >= TransactionHistorySyncPacket.MAX_ENTRIES) {
                break;
            }
            boolean incoming = transaction.to() != null
                    && transaction.to().equals(ownSubject);
            byte direction = incoming
                    ? TransactionHistorySyncPacket.HistoryEntry.DIRECTION_IN
                    : TransactionHistorySyncPacket.HistoryEntry.DIRECTION_OUT;
            String counterparty = counterpartyDigest(ownSubject, transaction);
            entries.add(new TransactionHistorySyncPacket.HistoryEntry(
                    transaction.transactionId(),
                    direction,
                    transaction.amount(),
                    counterparty,
                    transaction.memo(),
                    transaction.timestamp()
            ));
        }
        return new TransactionHistorySyncPacket(
                entries,
                page.nextAfterId(),
                page.hasMore(),
                now()
        );
    }

    /**
     * Counterparty digest of one transaction relative to the receiver: the
     * other participant's subject identity, or the fixed treasury identity
     * for official system transactions.
     */
    private String counterpartyDigest(SubjectId ownSubject, EconomyTransaction transaction) {
        if (transaction.from() != null && !transaction.from().equals(ownSubject)) {
            return digest(transaction.from());
        }
        if (transaction.to() != null && !transaction.to().equals(ownSubject)) {
            return digest(transaction.to());
        }
        return digest(TREASURY_DIGEST_SOURCE);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private Optional<ServerPlayer> resolvePlayer(SubjectId subjectId) {
        return subjectRegistry.findBySubjectId(subjectId)
                .map(record -> record.ownerReference())
                .filter(owner -> owner.kind() == OwnerReferenceKind.PLAYER_UUID)
                .map(OwnerReference::ownerId)
                .flatMap(this::online);
    }

    private Optional<ServerPlayer> online(String canonicalUuid) {
        try {
            return onlinePlayer.apply(UUID.fromString(canonicalUuid));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private void send(ServerPlayer player, Object message) {
        try {
            NetworkSendService.SendResult result = sendService.trySendToPlayer(player, message);
            if (result != NetworkSendService.SendResult.SENT) {
                LOGGER.debug(
                        "[Economy] Display sync skipped for {}: {}",
                        player.getUUID(),
                        result
                );
            }
        } catch (RuntimeException failure) {
            LOGGER.debug(
                    "[Economy] Display sync failed for {}: {}",
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

    /**
     * Receiver-relative counterparty digest: SHA-256 hex of the counterparty
     * subject identity. Display-only projection, never a routing key.
     */
    private static String digest(SubjectId subjectId) {
        return digest(subjectId.canonicalKey());
    }

    /** SHA-256 hex digest of an arbitrary identity source string. */
    private static String digest(String source) {
        try {
            MessageDigest algorithm = MessageDigest.getInstance("SHA-256");
            byte[] hash = algorithm.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
