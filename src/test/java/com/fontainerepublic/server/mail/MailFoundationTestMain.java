package com.fontainerepublic.server.mail;

import com.fontainerepublic.common.mail.MailBroadcastPacket;
import com.fontainerepublic.common.mail.MailDeletePacket;
import com.fontainerepublic.common.mail.MailListRequestPacket;
import com.fontainerepublic.common.mail.MailReadPacket;
import com.fontainerepublic.common.mail.MailSendPacket;
import com.fontainerepublic.common.network.NetworkMessageRegistration;
import com.fontainerepublic.common.network.NetworkMessageSpec;
import com.fontainerepublic.common.network.NetworkProductionMessageTable;
import com.fontainerepublic.common.network.NetworkProtocol;
import com.fontainerepublic.common.network.RateLimitPolicy;
import com.fontainerepublic.common.network.display.MailAlertPacket;
import com.fontainerepublic.common.network.display.MailboxSyncPacket;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.mail.api.MailboxKey;
import com.fontainerepublic.server.mail.api.MailboxKind;
import com.fontainerepublic.server.mail.model.MailboxState;
import com.fontainerepublic.server.mail.model.StoredMail;
import com.fontainerepublic.server.mail.persistence.MailLimits;
import com.fontainerepublic.server.mail.persistence.MailNbtCodec;
import com.fontainerepublic.server.mail.persistence.MailNbtException;
import com.fontainerepublic.server.mail.persistence.MailRepository;
import com.fontainerepublic.server.mail.persistence.MailStore;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Dependency-free validation of the communicator mail subsystem
 * (FR-MAIL-001-A v1.1).
 *
 * <p>Covers headlessly: the bounded persistent repository (delivery, read,
 * delete, broadcast read-state, money-delivery marker, id and revision
 * monotonicity, restart round-trip), the {@code MailboxKey} recipient identity
 * matrix (person + institution whitelist, fail closed on unknown), the mail
 * NBT codec round-trip for money/no-item stores, the protocol v7 ledger
 * registration (IDs 16-22 with C2S rate policies), and the item-free packet
 * codecs and their bounds. Item attachment slots are serialized with the
 * Forge ItemStack NBT path and are validated by the dedicated-server runtime
 * (Level 3), following the project's established verification convention.</p>
 */
public final class MailFoundationTestMain {

    private static final UUID ALICE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("10000000-0000-0000-0000-000000000002");

    private MailFoundationTestMain() {
    }

    public static void main(String[] args) {
        testMailboxKeyIdentityMatrix();
        testInstitutionWhitelistFailClosed();
        testRepositoryDeliveryAndIds();
        testRepositoryReadDeleteAndBroadcastReadState();
        testMoneyDeliveredMarker();
        testRestartRoundTrip();
        testMainlimits();
        testProtocolLedgerRegistration();
        testPacketCodecRoundTrip();
        testPacketValueBounds();
        System.out.println("[FR-MAIL-001] Communicator mail foundation validation passed");
    }

    // ------------------------------------------------------------------
    // recipient identity matrix
    // ------------------------------------------------------------------

    private static void testMailboxKeyIdentityMatrix() {
        MailboxKey person = MailboxKey.person(ALICE);
        check(person.kind() == MailboxKind.PERSON, "person mailbox is PERSON kind");
        check(person.subjectUuid().equals(ALICE), "person mailbox subject is the UUID");

        MailboxKey gov = MailboxKey.institution("gov:government");
        check(gov.isGovernment(), "gov:government is the government mailbox");

        MailboxKey parliament = MailboxKey.institution("parliament");
        check(!parliament.isMinistry() && !parliament.isGovernment(),
                "parliament is a fixed institution mailbox");

        MailboxKey ministry = MailboxKey.institution("gov:ministry:" + BOB);
        check(ministry.isMinistry() && ministry.ministryId().equals(BOB.toString()),
                "gov:ministry:<uuid> parses a ministry id");
    }

    private static void testInstitutionWhitelistFailClosed() {
        // Unknown institution form must fail closed.
        expectThrows(IllegalArgumentException.class,
                () -> MailboxKey.institution("gov:unknown"),
                "unknown institution mailbox fails closed");
        // A bare unknown institution kind must fail closed.
        expectThrows(IllegalArgumentException.class,
                () -> MailboxKey.institution("ministry:abc"),
                "single-word unknown institution fails closed");
        // Round-trip parse of canonical key form.
        MailboxKey person = MailboxKey.person(ALICE);
        check(MailboxKey.parse(person.key()).equals(person),
                "canonical person key round-trips");
        MailboxKey bank = MailboxKey.institution("bank");
        check(MailboxKey.parse(bank.key()).equals(bank),
                "canonical institution key round-trips");
        // A structurally-valid but unknown ministry id is resolved by the
        // service (GovernmentService) at send time, not by the key type.
        MailboxKey ministry = MailboxKey.institution("gov:ministry:00000000-0000-0000-0000-000000000099");
        check(ministry.isMinistry(), "a structurally-valid ministry key parses as a ministry");
    }

    // ------------------------------------------------------------------
    // repository (bounded, read/delete, broadcast read-state, money marker)
    // ------------------------------------------------------------------

    private static void testRepositoryDeliveryAndIds() {
        RepositoryHarness h = new RepositoryHarness();
        MailboxKey alice = MailboxKey.person(ALICE);
        MailboxKey bob = MailboxKey.person(BOB);
        StoredMail m1 = new StoredMail(
                h.repository().nextMailId(), false, alice.key(), bob.key(),
                "Hello", "Body one", 1_000L, 250L, List.of());
        h.repository().deliverDirect(m1);
        check(h.repository().nextMailId() == 2L, "nextMailId increments on delivery");
        check(h.repository().messageCount() == 1, "one message stored");
        check(h.repository().mailbox(bob).isPresent(), "recipient mailbox created");

        StoredMail m2 = new StoredMail(
                h.repository().nextMailId(), false, bob.key(), alice.key(),
                "Reply", "Body two", 2_000L, 0L, List.of());
        h.repository().deliverDirect(m2);
        check(h.repository().nextMailId() == 3L, "second delivery increments id");
        check(h.repository().requireMailbox(alice).inbox().size() == 1,
                "alice has one direct mail");
    }

    private static void testRepositoryReadDeleteAndBroadcastReadState() {
        RepositoryHarness h = new RepositoryHarness();
        MailboxKey alice = MailboxKey.person(ALICE);
        MailboxKey gov = MailboxKey.institution("gov:government");

        StoredMail direct = new StoredMail(
                h.repository().nextMailId(), false, gov.key(), alice.key(),
                "Notice", "Body", 1_000L, 0L, List.of());
        h.repository().deliverDirect(direct);

        StoredMail bcast = new StoredMail(
                h.repository().nextMailId(), true, gov.key(), null,
                "Announcement", "Body", 2_000L, 0L, List.of());
        h.repository().storeBroadcast(bcast);

        // Unread: direct unread + broadcast unread.
        MailboxState aliceBox = h.repository().requireMailbox(alice);
        check(aliceBox.inbox().get(0).mailId() == direct.mailId()
                        && !aliceBox.inbox().get(0).read(),
                "direct mail starts unread");
        check(!aliceBox.isBroadcastRead(bcast.mailId()), "broadcast starts unread");

        // Mark direct read.
        h.repository().markRead(alice, direct.mailId());
        check(h.repository().requireMailbox(alice).inbox().get(0).read(),
                "direct mail is marked read");

        // Mark broadcast read (suppress).
        h.repository().markRead(alice, bcast.mailId());
        check(h.repository().requireMailbox(alice).isBroadcastRead(bcast.mailId()),
                "broadcast suppression recorded");

        // Delete direct mail.
        h.repository().deleteMail(alice, direct.mailId());
        check(h.repository().requireMailbox(alice).inbox().isEmpty(),
                "direct mail removed after delete");
    }

    private static void testMoneyDeliveredMarker() {
        RepositoryHarness h = new RepositoryHarness();
        MailboxKey alice = MailboxKey.person(ALICE);
        MailboxKey bob = MailboxKey.person(BOB);
        StoredMail mail = new StoredMail(
                h.repository().nextMailId(), false, alice.key(), bob.key(),
                "Payment", "Funds", 1_000L, 500L, List.of());
        h.repository().deliverDirect(mail);
        check(!h.repository().findMessage(mail.mailId()).get().fullyClaimed(),
                "a money attachment is not yet claimed");
        h.repository().markMoneyDelivered(bob, mail.mailId());
        check(h.repository().findMessage(mail.mailId()).get().moneyAttachment() == 0L,
                "money intention cleared after delivery");
        check(h.repository().findMessage(mail.mailId()).get().fullyClaimed(),
                "mail becomes fully claimed after money delivery");
        check(h.repository().requireMailbox(bob).inbox().get(0).moneyDelivered(),
                "entry records money delivered (anti-duplication)");
    }

    private static void testRestartRoundTrip() {
        MailNbtCodec codec = new MailNbtCodec();
        InMemoryStore store = new InMemoryStore();
        MailRepository first = new MailRepository(store, codec, MailLimits.DEFAULT);
        MailboxKey alice = MailboxKey.person(ALICE);
        MailboxKey bob = MailboxKey.person(BOB);
        StoredMail mail = new StoredMail(
                first.nextMailId(), false, alice.key(), bob.key(),
                "Persist", "Survives restart", 1_000L, 0L, List.of());
        first.deliverDirect(mail);
        check(store.data.size() > 0, "store wrote a snapshot");

        // Reload from the same store: state must round-trip.
        MailRepository second = new MailRepository(store, codec, MailLimits.DEFAULT);
        check(second.nextMailId() == 2L, "nextMailId survives restart");
        check(second.messageCount() == 1, "messages survive restart");
        check(second.requireMailbox(bob).inbox().size() == 1,
                "recipient inbox survives restart");
        var persisted = second.findMessage(mail.mailId()).get();
        check(persisted.subject().equals("Persist")
                        && persisted.body().equals("Survives restart"),
                "mail content survives restart");

        // Corrupt / unknown-version namespace fails closed.
        CompoundTag corrupt = store.data.copy();
        corrupt.putInt("StoreVersion", 999);
        InMemoryStore corruptStore = new InMemoryStore();
        corruptStore.data = corrupt;
        expectThrows(MailNbtException.class,
                () -> new MailRepository(corruptStore, codec, MailLimits.DEFAULT),
                "an unsupported mail store version fails closed");
    }

    private static void testMainlimits() {
        MailLimits limits = MailLimits.DEFAULT;
        check(limits.maxMessages() > 0 && limits.maxMailboxes() > 0,
                "mail limits are positive and bounded");
        check(MailboxState.MAX_INBOX == 100, "per-mailbox inbox is bounded to 100");
        check(StoredMail.MAX_BODY == 2000 && StoredMail.MAX_SUBJECT == 64,
                "subject/body bounds match FR-MAIL-001-A");
        check(StoredMail.MAX_ITEM_SLOTS == 4, "item attachments are bounded to 4 slots");
    }

    // ------------------------------------------------------------------
    // protocol v7 ledger (IDs 16-22, C2S rate policies)
    // ------------------------------------------------------------------

    private static void testProtocolLedgerRegistration() {
        check(NetworkProtocol.VERSION.equals("7"), "the protocol version is v7");
        check(NetworkProductionMessageTable.EXPECTED_MESSAGE_COUNT == 23,
                "the production ledger expects 23 messages (IDs 0-22)");

        Map<Integer, NetworkMessageSpec<?>> byId = collectLedger();
        check(byId.size() == NetworkProductionMessageTable.EXPECTED_MESSAGE_COUNT,
                "registerAll exposes every expected ledger message");

        assertC2s(byId, 16, MailSendPacket.class);
        assertC2s(byId, 17, MailListRequestPacket.class);
        assertC2s(byId, 18, MailReadPacket.class);
        assertC2s(byId, 19, MailDeletePacket.class);
        assertC2s(byId, 22, MailBroadcastPacket.class);

        NetworkMessageSpec<?> sync = byId.get(20);
        check(sync != null && sync.messageClass() == MailboxSyncPacket.class
                        && sync.direction() == NetworkDirection.PLAY_TO_CLIENT,
                "ID 20 is the S2C mailbox sync");
        check(sync.rateLimitPolicy().isEmpty(), "S2C sync carries no C2S rate policy");

        NetworkMessageSpec<?> alert = byId.get(21);
        check(alert != null && alert.messageClass() == MailAlertPacket.class
                        && alert.direction() == NetworkDirection.PLAY_TO_CLIENT,
                "ID 21 is the S2C mail alert");
    }

    private static void assertC2s(
            Map<Integer, NetworkMessageSpec<?>> byId,
            int id,
            Class<?> expected
    ) {
        NetworkMessageSpec<?> spec = byId.get(id);
        check(spec != null && spec.messageClass() == expected,
                "ID " + id + " registers " + expected.getSimpleName());
        check(spec.direction() == NetworkDirection.PLAY_TO_SERVER,
                "ID " + id + " is a PLAY_TO_SERVER C2S message");
        check(spec.rateLimitPolicy().isPresent(),
                "ID " + id + " carries an explicit C2S rate policy");
        RateLimitPolicy policy = spec.rateLimitPolicy().orElseThrow();
        check(policy.capacity() >= 1 && policy.refillTokens() >= 1,
                "ID " + id + " rate policy is bounded and positive");
    }

    private static Map<Integer, NetworkMessageSpec<?>> collectLedger() {
        LedgerCollector collector = new LedgerCollector();
        NetworkProductionMessageTable.registerAll(collector);
        return collector.byId;
    }

    private static final class LedgerCollector implements NetworkMessageRegistration {
        private final Map<Integer, NetworkMessageSpec<?>> byId = new LinkedHashMap<>();

        @Override
        public <MSG> void register(NetworkMessageSpec<MSG> spec) {
            byId.put(spec.id(), spec);
        }
    }

    // ------------------------------------------------------------------
    // item-free packet codecs + bounds
    // ------------------------------------------------------------------

    private static void testPacketCodecRoundTrip() {
        MailSendPacket send = roundTrip(
                new MailSendPacket("10000000-0000-0000-0000-000000000002",
                        "Subject", "Body", 500L, List.of(3, 5)),
                MailSendPacket::encode, MailSendPacket::decode);
        check(send.to().contains("000000000002") && send.subject().equals("Subject")
                        && send.body().equals("Body") && send.moneyAttachment() == 500L
                        && send.itemSlotIndices().equals(List.of(3, 5)),
                "MailSendPacket round-trips");

        MailListRequestPacket list = roundTrip(
                new MailListRequestPacket(32),
                MailListRequestPacket::encode, MailListRequestPacket::decode);
        check(list.limit() == 32, "MailListRequestPacket round-trips");

        MailReadPacket read = roundTrip(new MailReadPacket(7L),
                MailReadPacket::encode, MailReadPacket::decode);
        check(read.mailId() == 7L, "MailReadPacket round-trips");

        MailDeletePacket del = roundTrip(new MailDeletePacket(7L),
                MailDeletePacket::encode, MailDeletePacket::decode);
        check(del.mailId() == 7L, "MailDeletePacket round-trips");

        MailBroadcastPacket bcast = roundTrip(
                new MailBroadcastPacket("Announcement", "Body"),
                MailBroadcastPacket::encode, MailBroadcastPacket::decode);
        check(bcast.subject().equals("Announcement"), "MailBroadcastPacket round-trips");

        MailAlertPacket alert = roundTrip(new MailAlertPacket(3, "New mail"),
                MailAlertPacket::encode, MailAlertPacket::decode);
        check(alert.unreadCount() == 3 && alert.summary().equals("New mail"),
                "MailAlertPacket round-trips");

        MailboxSyncPacket sync = roundTrip(
                new MailboxSyncPacket(List.of(new MailboxSyncPacket.Entry(
                        9L, true, "gov:government", "Announcement", "Body",
                        1_000L, false, 0L, false, 0)), 5, 2_000L),
                MailboxSyncPacket::encode, MailboxSyncPacket::decode);
        check(sync.unreadCount() == 5 && sync.entries().size() == 1
                        && sync.entries().get(0).mailId() == 9L,
                "MailboxSyncPacket round-trips");
    }

    private static void testPacketValueBounds() {
        expectThrows(IllegalArgumentException.class,
                () -> new MailSendPacket("x".repeat(129), "s", "b", 0L, List.of()),
                "a too-long recipient is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new MailSendPacket("to", "s", "b", -1L, List.of()),
                "a negative money attachment is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new MailSendPacket("to", "s", "b", 0L, List.of(1, 2, 3, 4, 5)),
                "too many item slots are rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new MailListRequestPacket(0),
                "a non-positive list limit is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new MailReadPacket(0L),
                "a non-positive mail id is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new MailDeletePacket(0L),
                "a non-positive delete id is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new MailBroadcastPacket("", "b".repeat(2001)),
                "a too-long broadcast body is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new MailAlertPacket(-1, "s"),
                "a negative unread alert count is rejected");
    }

    // ------------------------------------------------------------------
    // repository harness (in-memory store)
    // ------------------------------------------------------------------

    private static final class RepositoryHarness {
        private final InMemoryStore store = new InMemoryStore();
        private final MailRepository repository;

        RepositoryHarness() {
            this.repository = new MailRepository(
                    store, new MailNbtCodec(), MailLimits.DEFAULT);
        }

        MailRepository repository() {
            return repository;
        }
    }

    private static final class InMemoryStore implements MailStore {
        private CompoundTag data = new CompoundTag();

        @Override
        public CompoundTag load() {
            return data.copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            this.data = snapshot.copy();
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    MailRepository.MODULE_DATA_KEY,
                    1L,
                    0L,
                    ""
            );
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static <T> T roundTrip(
            T message,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder
    ) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encoder.accept(message, buffer);
            buffer.readerIndex(0);
            return decoder.apply(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> type, ThrowingRunnable action, String message
    ) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) {
                return type.cast(failure);
            }
            throw new AssertionError(
                    "Expected " + type.getName() + " but got "
                            + failure.getClass().getName() + ": " + message,
                    failure
            );
        }
        throw new AssertionError("Expected " + type.getName() + " to be thrown: " + message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
