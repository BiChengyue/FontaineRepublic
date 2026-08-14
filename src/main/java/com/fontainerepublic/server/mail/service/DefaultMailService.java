package com.fontainerepublic.server.mail.service;

import com.fontainerepublic.common.network.display.MailAlertPacket;
import com.fontainerepublic.common.network.display.MailboxSyncPacket;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.economy.api.MailPostageReceipt;
import com.fontainerepublic.server.economy.api.TransferReceipt;
import com.fontainerepublic.server.economy.persistence.EconomyUnavailableException;
import com.fontainerepublic.server.government.api.GovernmentService;
import com.fontainerepublic.server.government.model.MinistryId;
import com.fontainerepublic.server.mail.api.MailAuthority;
import com.fontainerepublic.server.mail.api.MailboxKey;
import com.fontainerepublic.server.mail.api.MailboxView;
import com.fontainerepublic.server.mail.api.MailClaimResult;
import com.fontainerepublic.server.mail.api.MailConfig;
import com.fontainerepublic.server.mail.api.MailSendResult;
import com.fontainerepublic.server.mail.api.MailService;
import com.fontainerepublic.server.mail.api.ServerMailPlayerAccess;
import com.fontainerepublic.server.mail.model.MailboxEntry;
import com.fontainerepublic.server.mail.model.MailboxState;
import com.fontainerepublic.server.mail.model.StoredMail;
import com.fontainerepublic.server.mail.persistence.MailLimits;
import com.fontainerepublic.server.mail.persistence.MailNbtCodec;
import com.fontainerepublic.server.mail.persistence.MailRepository;
import com.fontainerepublic.server.mail.persistence.MailUnavailableException;
import com.fontainerepublic.server.network.NetworkSendService;
import com.fontainerepublic.server.citizen.api.CitizenService;
import com.fontainerepublic.server.playerdata.api.PlayerDirectoryService;
import com.fontainerepublic.server.playerdata.model.PlayerNameResolution;
import com.fontainerepublic.server.playerdata.model.PlayerNameResolutionKind;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Main-thread-serialized implementation of the mail subsystem
 * (FR-MAIL-001-A §2-§6, server-authoritative).
 *
 * <p>Send/read/delete/list/broadcast all run on the logical owner thread and
 * publish only after the FR-CORE-002 durable gate. The sender communicator
 * gate, recipient resolution, attachment handling, fee computation and the
 * institution access matrix are enforced here — never by the client.
 *
 * <p>Attachment money is an intention at send (never pre-escrowed); it is
 * credited to the receiver atomically at claim. Item attachments are moved
 * from the sender's inventory into the mail slots at send and, at claim, into
 * the receiver's inventory as far as space allows (full items stay in the
 * mail for a later claim). The {@code moneyDelivered} flag and the cleared
 * item slots prevent duplication; a fully-claimed mail is irreversible.</p>
 */
public final class DefaultMailService implements MailService {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Hard bound on the enumerable citizen range for one broadcast. */
    private static final int BROADCAST_RANGE_LIMIT = 10_000;

    private final MailRepository repository;
    private final EconomyService economy;
    private final SubjectRegistryService subjectRegistry;
    private final PlayerDirectoryService playerDirectory;
    private final CitizenService citizenService;
    private final GovernmentService government;
    private final NetworkSendService sendService;
    private final MailAuthority authority;
    private final ServerMailPlayerAccess playerAccess;
    private final LongSupplier clock;
    private final MailConfig config;

    private final Map<String, Long> lastBroadcastAt = new HashMap<>();

    public DefaultMailService(
            MailRepository repository,
            EconomyService economy,
            SubjectRegistryService subjectRegistry,
            PlayerDirectoryService playerDirectory,
            CitizenService citizenService,
            GovernmentService government,
            NetworkSendService sendService,
            MailAuthority authority,
            ServerMailPlayerAccess playerAccess,
            LongSupplier clock,
            MailConfig config
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.subjectRegistry = Objects.requireNonNull(subjectRegistry, "subjectRegistry");
        this.playerDirectory = Objects.requireNonNull(playerDirectory, "playerDirectory");
        this.citizenService = Objects.requireNonNull(citizenService, "citizenService");
        this.government = Objects.requireNonNull(government, "government");
        this.sendService = Objects.requireNonNull(sendService, "sendService");
        this.authority = Objects.requireNonNull(authority, "authority");
        this.playerAccess = Objects.requireNonNull(playerAccess, "playerAccess");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.config = Objects.requireNonNull(config, "config");
    }

    // ------------------------------------------------------------------
    // send
    // ------------------------------------------------------------------

    @Override
    public MailSendResult send(
            UUID actor,
            String recipient,
            String subject,
            String body,
            long moneyAttachment,
            List<Integer> itemSlots
    ) {
        Objects.requireNonNull(actor, "actor");
        if (!playerAccess.holdsCommunicator(actor)) {
            message(actor, "Hold the Message Water Mirror (传讯水镜) to send mail.");
            return MailSendResult.fail(MailSendResult.CODE_NOT_HOLDING, "Not holding the communicator.");
        }
        if (moneyAttachment < 0 || moneyAttachment > StoredMail.MAX_MONEY_ATTACHMENT) {
            return MailSendResult.fail(MailSendResult.CODE_INVALID,
                    "Money attachment is out of range.");
        }
        if (itemSlots == null || itemSlots.size() > StoredMail.MAX_ITEM_SLOTS) {
            return MailSendResult.fail(MailSendResult.CODE_INVALID,
                    "Too many item attachments (max " + StoredMail.MAX_ITEM_SLOTS + ").");
        }

        Optional<MailboxKey> recipientBox = resolveRecipient(recipient);
        if (recipientBox.isEmpty()) {
            message(actor, "Recipient could not be resolved.");
            return MailSendResult.fail(MailSendResult.CODE_RECIPIENT_UNKNOWN,
                    "Recipient unknown or ambiguous.");
        }
        MailboxKey to = recipientBox.get();

        Optional<MailboxKey> actingInstitution = authority.actingInstitution(actor, to);
        Optional<SubjectId> senderSubject;
        MailboxKey from;
        if (actingInstitution.isPresent()) {
            // Institution sender: postage-free, no money attachment.
            if (moneyAttachment > 0) {
                return MailSendResult.fail(MailSendResult.CODE_INVALID,
                        "Institution mail cannot carry a money attachment.");
            }
            from = actingInstitution.get();
            senderSubject = Optional.empty();
        } else {
            Optional<SubjectId> playerSubject = subjectOfPlayer(actor);
            if (playerSubject.isEmpty()) {
                message(actor, "You have no registered subject; cannot send mail.");
                return MailSendResult.fail(MailSendResult.CODE_NO_SUBJECT,
                        "No registered subject.");
            }
            if (to.kind() == com.fontainerepublic.server.mail.api.MailboxKind.PERSON
                    && to.subjectUuid() != null
                    && to.subjectUuid().equals(playerSubject.get().value())) {
                return MailSendResult.fail(MailSendResult.CODE_SELF,
                        "You cannot send mail to yourself.");
            }
            from = personMailbox(playerSubject.get());
            senderSubject = playerSubject;
        }

        // Validate attachments.
        List<Integer> slots = itemSlots == null ? List.of() : itemSlots;
        List<ItemStack> itemStacks = collectItemStacks(actor, slots);
        if (itemStacks == null) {
            message(actor, "An offered inventory slot is empty or out of range.");
            return MailSendResult.fail(MailSendResult.CODE_INVALID,
                    "An item slot is empty or out of range.");
        }
        int attachmentCount = (moneyAttachment > 0 ? 1 : 0) + itemStacks.size();

        // Person senders pay postage (to the treasury); institutions are free.
        if (senderSubject.isPresent()) {
            MailSendResult fee = chargeFee(senderSubject.get(), attachmentCount);
            if (!fee.success()) {
                message(actor, fee.message());
                return fee;
            }
        }

        long mailId = repository.nextMailId();
        long now = now();
        String normalizedSubject = normalizeField(subject, StoredMail.MAX_SUBJECT);
        String normalizedBody = normalizeField(body, StoredMail.MAX_BODY);
        StoredMail mail;
        try {
            mail = new StoredMail(
                    mailId,
                    false,
                    from.key(),
                    to.key(),
                    normalizedSubject,
                    normalizedBody,
                    now,
                    moneyAttachment,
                    itemStacks
            );
        } catch (IllegalArgumentException invalid) {
            message(actor, invalid.getMessage());
            return MailSendResult.fail(MailSendResult.CODE_INVALID, invalid.getMessage());
        }

        if (senderSubject.isPresent()) {
            repository.ensureMailbox(to, now);
        }
        try {
            repository.deliverDirect(mail);
        } catch (MailUnavailableException failure) {
            LOGGER.warn("[Mail] direct delivery failed: {}", failure.getMessage());
            message(actor, "Mail could not be delivered.");
            return MailSendResult.fail(MailSendResult.CODE_STORE, "Delivery failed.");
        }
        // Only once the mail is durably stored do we remove the moved item
        // slots from the sender's inventory (the mail now owns the copies).
        moveItemsOut(actor, slots);
        alertRecipient(to, 0);
        message(actor, "Mail sent to "
                + (to.kind() == com.fontainerepublic.server.mail.api.MailboxKind.PERSON
                        ? "player" : "institution") + ".");
        return new MailSendResult(true, MailSendResult.CODE_OK,
                "Mail sent.", mailId);
    }

    private MailSendResult chargeFee(SubjectId payer, int attachmentCount) {
        long postageFee = config.postageFee();
        long totalAttachmentFee;
        try {
            totalAttachmentFee = Math.multiplyExact(config.perAttachmentFee(), attachmentCount);
        } catch (ArithmeticException overflow) {
            return MailSendResult.fail(MailSendResult.CODE_INSUFFICIENT_FUNDS, "Fee overflow.");
        }
        try {
            MailPostageReceipt receipt = economy.chargePostage(
                    payer,
                    postageFee,
                    totalAttachmentFee,
                    "mail postage"
            );
            return new MailSendResult(true, MailSendResult.CODE_OK, "Fee paid.", 0L);
        } catch (EconomyUnavailableException failure) {
            LOGGER.debug("[Mail] postage rejected ({}): {}", failure.failureCode(), failure.getMessage());
            return MailSendResult.fail(MailSendResult.CODE_INSUFFICIENT_FUNDS,
                    "You do not have enough balance to cover the postage.");
        }
    }

    // ------------------------------------------------------------------
    // list / read / delete
    // ------------------------------------------------------------------

    @Override
    public MailboxView mailbox(UUID actor) {
        Objects.requireNonNull(actor, "actor");
        long now = now();
        MailboxKey self = personMailboxOfPlayerOrNull(actor);
        if (self == null) {
            return new MailboxView(List.of(), 0, now);
        }
        MailboxState own = repository.mailbox(self).orElse(MailboxState.empty());
        List<MailboxView.MailEntryView> entries = new ArrayList<>();
        int unread = 0;

        // Derived broadcasts (unread first added, then read).
        List<StoredMail> broadcasts = new ArrayList<>(repository.broadcasts());
        for (StoredMail broadcast : broadcasts) {
            boolean read = own.isBroadcastRead(broadcast.mailId());
            entries.add(toView(broadcast, read, true));
            if (!read) {
                unread++;
            }
        }
        // Direct inbox entries.
        for (MailboxEntry entry : own.inbox()) {
            Optional<StoredMail> mail = repository.findMessage(entry.mailId());
            if (mail.isEmpty()) {
                continue;
            }
            entries.add(toView(mail.get(), entry.read(), false));
            if (!entry.read()) {
                unread++;
            }
        }
        // Institution mailboxes the actor may read (v1: only their own person
        // mailbox is shown; institution inboxes are read through the same
        // view only if the actor is a mailbox reader — see read/delete which
        // search accessible mailboxes). Per-project, institution mail is
        // surfaced to the manager through the person (actor) mailbox view.
        entries.sort((a, b) -> Long.compare(b.sentAt(), a.sentAt()));
        if (entries.size() > MailboxView.MAX_VIEW) {
            entries = new ArrayList<>(entries.subList(0, MailboxView.MAX_VIEW));
        }
        return new MailboxView(List.copyOf(entries), unread, now);
    }

    @Override
    public MailClaimResult read(UUID actor, long mailId) {
        Objects.requireNonNull(actor, "actor");
        MailboxKey self = personMailboxOfPlayerOrNull(actor);
        if (self == null) {
            return MailClaimResult.unauthorized();
        }
        Optional<StoredMail> optMail = repository.findMessage(mailId);
        if (optMail.isEmpty()) {
            return MailClaimResult.noSuchMail();
        }
        StoredMail mail = optMail.get();
        MailboxState own = repository.mailbox(self).orElse(MailboxState.empty());
        boolean broadcastMatch = mail.broadcast() && !own.isBroadcastRead(mailId);
        boolean directMatch = !mail.broadcast()
                && mail.toKey().equals(self.key())
                && own.inbox().stream().anyMatch(e -> e.mailId() == mailId);
        if (!broadcastMatch && !directMatch) {
            return MailClaimResult.unauthorized();
        }
        repository.markRead(self, mailId);
        if (mail.broadcast()) {
            return MailClaimResult.readOnly();
        }
        return claim(self, actor, mail);
    }

    private MailClaimResult claim(MailboxKey receiver, UUID actor, StoredMail mail) {
        final long targetMailId = mail.mailId();
        MailboxState box = repository.requireMailbox(receiver);
        MailboxEntry entry = box.inbox().stream()
                .filter(e -> e.mailId() == targetMailId)
                .findFirst().orElse(null);
        if (entry == null) {
            return MailClaimResult.unauthorized();
        }
        boolean moneyCredited = false;
        boolean claimed = true;
        String status = MailClaimResult.STATUS_CLAIMED;
        int itemsDelivered = 0;
        int pendingItems = 0;

        // Money attachment (person-to-person only; institution senders cannot
        // attach money).
        if (mail.moneyAttachment() > 0 && !entry.moneyDelivered()) {
            SubjectId sender = subjectIdFromKey(mail.fromKey());
            SubjectId receiverSubject = subjectIdFromKey(receiver.key());
            if (sender == null || receiverSubject == null) {
                status = MailClaimResult.STATUS_NO_FUNDS;
                claimed = false;
            } else {
                try {
                    TransferReceipt receipt = economy.transfer(
                            sender, receiverSubject, mail.moneyAttachment(),
                            "mail attachment");
                    repository.markMoneyDelivered(receiver, mail.mailId());
                    moneyCredited = true;
                    mail = repository.findMessage(mail.mailId()).orElse(mail);
                } catch (EconomyUnavailableException failure) {
                    LOGGER.debug("[Mail] money claim rejected ({}): {}",
                            failure.failureCode(), failure.getMessage());
                    status = MailClaimResult.STATUS_NO_FUNDS;
                    claimed = false;
                }
            }
        }

        // Item attachments.
        if (!mail.itemSlots().isEmpty()) {
            List<Integer> deliveredIndices = new ArrayList<>();
            // ItemStack indices are position-based on the mail slot list.
            for (int index = 0; index < mail.itemSlots().size(); index++) {
                ItemStack stack = mail.itemSlots().get(index);
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack leftover = playerAccess.addToInventory(actor, stack);
                if (leftover.isEmpty()) {
                    deliveredIndices.add(index);
                    itemsDelivered++;
                } else {
                    pendingItems++;
                }
            }
            if (!deliveredIndices.isEmpty()) {
                repository.clearDeliveredItems(receiver, mail.mailId(), deliveredIndices);
            }
            if (pendingItems > 0) {
                claimed = false;
                status = MailClaimResult.STATUS_PARTIAL;
            }
        }

        if (claimed) {
            return new MailClaimResult(true, true, moneyCredited,
                    itemsDelivered, pendingItems,
                    MailClaimResult.STATUS_CLAIMED);
        }
        return new MailClaimResult(true, false, moneyCredited,
                itemsDelivered, pendingItems, status);
    }

    @Override
    public void delete(UUID actor, long mailId) {
        Objects.requireNonNull(actor, "actor");
        MailboxKey self = personMailboxOfPlayerOrNull(actor);
        if (self == null) {
            return;
        }
        Optional<StoredMail> optMail = repository.findMessage(mailId);
        if (optMail.isEmpty() || !repository.mailbox(self).isPresent()) {
            return;
        }
        StoredMail mail = optMail.get();
        if (mail.broadcast() || mail.toKey().equals(self.key())) {
            repository.deleteMail(self, mailId);
        }
    }

    // ------------------------------------------------------------------
    // broadcast
    // ------------------------------------------------------------------

    @Override
    public MailSendResult broadcast(UUID actor, String subject, String body) {
        Objects.requireNonNull(actor, "actor");
        Optional<MailboxKey> acting = authority.actingInstitution(actor, null);
        if (acting.isEmpty()) {
            message(actor, "Only an authorized institution may broadcast to all citizens.");
            return MailSendResult.fail(MailSendResult.CODE_UNAUTHORIZED,
                    "Not an authorized institution sender.");
        }
        String institutionKey = acting.get().key();
        Long last = lastBroadcastAt.get(institutionKey);
        long now = now();
        if (last != null && now - last < config.broadcastCooldownMillis()) {
            message(actor, "Broadcast is on cooldown for this institution.");
            return MailSendResult.fail(MailSendResult.CODE_COOLDOWN, "Broadcast cooldown.");
        }
        String normalizedSubject = normalizeField(subject, StoredMail.MAX_SUBJECT);
        String normalizedBody = normalizeField(body, StoredMail.MAX_BODY);
        long mailId = repository.nextMailId();
        StoredMail broadcast;
        try {
            broadcast = new StoredMail(
                    mailId, true, acting.get().key(), null,
                    normalizedSubject, normalizedBody, now, 0L, List.of());
        } catch (IllegalArgumentException invalid) {
            return MailSendResult.fail(MailSendResult.CODE_INVALID, invalid.getMessage());
        }
        try {
            repository.storeBroadcast(broadcast);
        } catch (MailUnavailableException failure) {
            LOGGER.warn("[Mail] broadcast store failed: {}", failure.getMessage());
            return MailSendResult.fail(MailSendResult.CODE_STORE, "Broadcast failed.");
        }
        lastBroadcastAt.put(institutionKey, now);
        // Alert every online citizen whose inventory contains the communicator.
        List<CitizenService.CitizenIdentity> citizens =
                citizenService.activeCitizens(BROADCAST_RANGE_LIMIT);
        for (CitizenService.CitizenIdentity identity : citizens) {
            alertBroadcastTo(identity.playerId(), mailId);
        }
        LOGGER.debug("[Mail] {} broadcast mail #{} to {} citizens",
                institutionKey, mailId, citizens.size());
        return new MailSendResult(true, MailSendResult.CODE_OK,
                "Broadcast sent to all citizens.", mailId);
    }

    private void alertBroadcastTo(UUID playerId, long mailId) {
        if (!playerAccess.inventoryContainsCommunicator(playerId)) {
            return;
        }
        playerAccess.onlinePlayer(playerId).ifPresent(player ->
                sendService.trySendToPlayer(player,
                        new MailAlertPacket(unreadOf(playerId), "New republic announcement.")));
    }

    private int unreadOf(UUID playerId) {
        MailboxKey self = personMailboxOfPlayerOrNull(playerId);
        if (self == null) {
            return 0;
        }
        MailboxView view;
        try {
            view = mailbox(playerId);
        } catch (RuntimeException ignored) {
            return 0;
        }
        return view.unreadCount();
    }

    // ------------------------------------------------------------------
    // access / resolution
    // ------------------------------------------------------------------

    @Override
    public boolean canAccessMailbox(UUID actor, MailboxKey mailbox) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(mailbox, "mailbox");
        if (mailbox.kind() == com.fontainerepublic.server.mail.api.MailboxKind.PERSON) {
            // A person mailbox is owned by the person whose subject id matches.
            return isOwnPersonMailbox(actor, mailbox);
        }
        return authority.canReadMailbox(actor, mailbox);
    }

    private boolean isOwnPersonMailbox(UUID actor, MailboxKey mailbox) {
        Optional<SubjectId> subject = subjectOfPlayer(actor);
        if (subject.isEmpty()) {
            return false;
        }
        return subject.get().value().equals(mailbox.subjectUuid());
    }

    @Override
    public Optional<MailboxKey> resolveRecipient(String recipient) {
        Objects.requireNonNull(recipient, "recipient");
        String trimmed = recipient.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        // Institution whitelist?
        try {
            MailboxKey institution = MailboxKey.institution(trimmed);
            if (validateInstitution(institution)) {
                return Optional.of(institution);
            }
        } catch (IllegalArgumentException notInstitution) {
            // fall through to person resolution
        }
        // Canonical UUID?
        try {
            UUID candidate = UUID.fromString(trimmed);
            if (candidate.toString().equals(trimmed)) {
                Optional<SubjectId> subject = subjectOfPlayer(candidate);
                if (subject.isPresent()) {
                    return Optional.of(personMailbox(subject.get()));
                }
                return Optional.empty();
            }
        } catch (IllegalArgumentException notUuid) {
            // fall through
        }
        // Registry number?
        try {
            RegistryNumber number = RegistryNumber.parse(trimmed);
            var routing = subjectRegistry.resolveExactRegistryNumber(number);
            if (routing.status()
                    == com.fontainerepublic.server.registry.api.RoutingStatus.ROUTABLE_ACTIVE
                    && routing.subject().isPresent()) {
                return Optional.of(personMailbox(routing.subject().get().subjectId()));
            }
            return Optional.empty();
        } catch (IllegalArgumentException notNumber) {
            // fall through
        }
        // Exact game name.
        PlayerNameResolution resolution = playerDirectory.resolveExactGameName(trimmed);
        if (resolution.kind() == PlayerNameResolutionKind.UNIQUE_CURRENT) {
            Optional<SubjectId> subject = resolution.playerId()
                    .flatMap(this::subjectOfPlayer);
            return subject.map(DefaultMailService::personMailbox);
        }
        return Optional.empty();
    }

    private boolean validateInstitution(MailboxKey institution) {
        if (institution.isGovernment()
                || institution.subject().equals("parliament")
                || institution.subject().equals("court")
                || institution.subject().equals("bank")) {
            return true;
        }
        if (institution.isMinistry()) {
            try {
                return government.getMinistry(
                        MinistryId.of(UUID.fromString(institution.ministryId()))).isPresent();
            } catch (IllegalArgumentException invalid) {
                return false;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    @Override
    public void requestSync(UUID actor) {
        Objects.requireNonNull(actor, "actor");
        MailboxView view = mailbox(actor);
        pushSync(actor, view);
        // FR-MAIL-001-FIX-01: also push the new-mail alert at sync time (login /
        // list request), gated on the communicator in the inventory, so the
        // unread badge and the optional chat reminder surface even when the
        // mail arrived while the recipient was offline. The mailbox sync alone
        // seeds the HUD badge, but the design's chat reminder (§2.3) requires
        // the alert path to fire; without this, an offline recipient never
        // receives a {@link MailAlertPacket} and sees no reminder.
        if (view.unreadCount() > 0
                && playerAccess.inventoryContainsCommunicator(actor)) {
            playerAccess.onlinePlayer(actor).ifPresent(player ->
                    sendService.trySendToPlayer(player,
                            new MailAlertPacket(view.unreadCount(),
                                    buildAlertSummary(view))));
        }
    }

    /** Short, bounded latest-mail summary for the new-mail reminder. */
    private String buildAlertSummary(MailboxView view) {
        if (view.entries().isEmpty()) {
            return "You have new mail.";
        }
        MailboxView.MailEntryView latest = view.entries().get(0);
        String subject = latest.subject() == null ? "" : latest.subject();
        if (subject.length() > 40) {
            subject = subject.substring(0, 40) + "…";
        }
        return "New mail: " + subject;
    }

    private void pushSync(UUID actor, MailboxView view) {
        List<MailboxSyncPacket.Entry> entries = new ArrayList<>();
        for (MailboxView.MailEntryView entry : view.entries()) {
            entries.add(new MailboxSyncPacket.Entry(
                    entry.mailId(),
                    entry.broadcast(),
                    entry.fromDisplay(),
                    entry.subject(),
                    entry.body(),
                    entry.sentAt(),
                    entry.read(),
                    entry.moneyAttachment(),
                    entry.attachmentsClaimed(),
                    entry.itemSlotCount()
            ));
        }
        playerAccess.onlinePlayer(actor).ifPresent(player ->
                sendService.trySendToPlayer(player,
                        new MailboxSyncPacket(entries, view.unreadCount(), view.at())));
    }

    private Optional<SubjectId> subjectOfPlayer(UUID playerId) {
        if (subjectRegistry == null) {
            return Optional.empty();
        }
        return subjectRegistry.findSubjectForPlayer(playerId)
                .map(record -> record.subjectId());
    }

    private MailboxKey personMailboxOfPlayerOrNull(UUID playerId) {
        Optional<SubjectId> subject = subjectOfPlayer(playerId);
        return subject.map(DefaultMailService::personMailbox).orElse(null);
    }

    private static MailboxKey personMailbox(SubjectId subjectId) {
        return MailboxKey.person(subjectId.value());
    }

    private SubjectId subjectIdFromKey(String key) {
        MailboxKey parsed = MailboxKey.parse(key);
        if (parsed == null || parsed.kind()
                != com.fontainerepublic.server.mail.api.MailboxKind.PERSON) {
            return null;
        }
        try {
            return SubjectId.of(UUID.fromString(parsed.subject()));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** Validates the slots and captures the stacks; null when invalid/empty. */
    private List<ItemStack> collectItemStacks(UUID actor, List<Integer> slots) {
        List<ItemStack> stacks = new ArrayList<>(slots.size());
        for (int index : slots) {
            if (index < 0 || index > 35) {
                return null;
            }
            ItemStack stack = playerAccess.mainInventoryStack(actor, index);
            if (stack.isEmpty()) {
                return null;
            }
            stacks.add(stack.copy());
        }
        return stacks;
    }

    /** Clears the moved item slots in the actor's inventory. */
    private void moveItemsOut(UUID actor, List<Integer> slots) {
        for (int index : slots) {
            playerAccess.setMainInventoryStack(actor, index, ItemStack.EMPTY);
        }
    }

    private static String normalizeField(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(
                    "Field exceeds the bound of " + max + " characters");
        }
        return trimmed;
    }

    private static MailboxView.MailEntryView toView(
            StoredMail mail, boolean read, boolean broadcast
    ) {
        return new MailboxView.MailEntryView(
                mail.mailId(),
                broadcast,
                displayName(mail.fromKey()),
                mail.subject(),
                mail.body(),
                mail.sentAt(),
                read,
                mail.moneyAttachment(),
                mail.fullyClaimed(),
                (int) mail.itemSlots().stream().filter(s -> !s.isEmpty()).count()
        );
    }

    private static String displayName(String key) {
        return key == null ? "?" : key;
    }

    private void alertRecipient(MailboxKey to, int unread) {
        if (to.kind() != com.fontainerepublic.server.mail.api.MailboxKind.PERSON) {
            return; // institution recipients are not alerted per-mail
        }
        Optional<UUID> playerId = mailboxPlayerId(to);
        if (playerId.isEmpty()) {
            return;
        }
        // Only alert if the recipient is online and carries a communicator in
        // the inventory.
        if (!playerAccess.inventoryContainsCommunicator(playerId.get())) {
            return;
        }
        playerAccess.onlinePlayer(playerId.get()).ifPresent(player ->
                sendService.trySendToPlayer(player,
                        new MailAlertPacket(unreadOf(playerId.get()), "You have new mail.")));
    }

    private Optional<UUID> mailboxPlayerId(MailboxKey mailbox) {
        if (mailbox.kind() != com.fontainerepublic.server.mail.api.MailboxKind.PERSON) {
            return Optional.empty();
        }
        // person mailbox is keyed by SubjectId; find the player who owns that
        // subject. The registry's SubjectProjection withholds the owner, so we
        // fall back to a best-effort online scan (the recipient is typically
        // online when sending).
        UUID subjectUuid;
        try {
            subjectUuid = UUID.fromString(mailbox.subject());
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
        // Search the online player list for the player whose subject matches.
        List<ServerPlayer> online = onlinePlayers();
        for (ServerPlayer player : online) {
            try {
                Optional<com.fontainerepublic.server.registry.model.SubjectRecord> rec =
                        subjectRegistry.findSubjectForPlayer(player.getUUID());
                if (rec.isPresent() && rec.get().subjectId().value().equals(subjectUuid)) {
                    return Optional.of(player.getUUID());
                }
            } catch (RuntimeException ignored) {
                // offline or unavailable; skip
            }
        }
        return Optional.empty();
    }

    private List<ServerPlayer> onlinePlayers() {
        net.minecraft.server.MinecraftServer server =
                net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return List.of();
        }
        return new ArrayList<>(server.getPlayerList().getPlayers());
    }

    private void message(UUID playerId, String text) {
        if (playerAccess != null) {
            playerAccess.message(playerId, text);
        }
    }

    private long now() {
        long value = clock.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("clock returned a negative timestamp");
        }
        return value;
    }
}
