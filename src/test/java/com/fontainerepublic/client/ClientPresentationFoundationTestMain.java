package com.fontainerepublic.client;

import com.fontainerepublic.client.net.ClientPresentationCache;
import com.fontainerepublic.common.network.NetworkProtocol;
import com.fontainerepublic.common.network.display.BalanceSyncPacket;
import com.fontainerepublic.common.network.display.NotificationPacket;
import com.fontainerepublic.common.network.display.TransactionNotifyPacket;
import com.fontainerepublic.server.economy.api.EconomyPage;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.economy.api.MailPostageReceipt;
import com.fontainerepublic.server.economy.api.TransferReceipt;
import com.fontainerepublic.server.economy.api.TradeSettlementReceipt;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.economy.presentation.EconomyPresentationNotifier;
import com.fontainerepublic.server.economy.presentation.PresentationAwareEconomyService;
import com.fontainerepublic.server.economy.presentation.ServerEconomyPresentationNotifier;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.network.NetworkSendService;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.api.PublicRoutingResult;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.OwnerReferenceKind;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dependency-free FR-CLIENT-001-IMPL-A validation (Level 1-2).
 *
 * <p>Covers the S2C presentation surface: protocol predicates, bounded codecs
 * of the three display messages, source-level side isolation (no client
 * imports in common/display, the client executor referenced only through the
 * DistExecutor supplier, server/ never referencing client/), the
 * non-authoritative client presentation cache, and the best-effort economy
 * send wiring (absent/offline players and throwing notifiers never change the
 * business result).</p>
 */
public final class ClientPresentationFoundationTestMain {

    private static final UUID PLAYER = UUID.fromString(
            "00000000-0000-0000-0000-0000000000aa");
    private static final SubjectId TARGET = SubjectId.of(
            UUID.fromString("00000000-0000-0000-0000-0000000000bb"));

    private ClientPresentationFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testProtocolPredicates();
        testBalanceSyncCodec();
        testTransactionNotifyCodec();
        testNotificationPacketCodec();
        testSideIsolationSourceScan();
        testPresentationCache();
        testNotifierAbsentNoOp();
        testDecoratorSwallowsPresentationFailures();
        System.out.println("[FR-CLIENT-001-A] Client presentation foundation validation passed");
    }

    // ------------------------------------------------------------------
    // 1. protocol predicates
    // ------------------------------------------------------------------

    private static void testProtocolPredicates() {
        check(NetworkProtocol.VERSION.equals("8"), "Protocol version is 8");
        check(NetworkProtocol.clientAccepts("8"), "Client accepts exact v8");
        check(!NetworkProtocol.clientAccepts("7"), "Client rejects v7");
        check(!NetworkProtocol.clientAccepts(NetworkRegistry.ABSENT.version()),
                "Client rejects an absent server channel");
        check(!NetworkProtocol.clientAccepts(NetworkRegistry.ACCEPTVANILLA),
                "Client rejects ACCEPTVANILLA");
        check(NetworkProtocol.serverAccepts("8"), "Server accepts exact v8");
        check(NetworkProtocol.serverAccepts(NetworkRegistry.ABSENT.version()),
                "Server accepts ABSENT.version()");
        check(!NetworkProtocol.serverAccepts("7"), "Server rejects v7");
        check(!NetworkProtocol.serverAccepts(NetworkRegistry.ACCEPTVANILLA),
                "Server rejects ACCEPTVANILLA");
    }

    // ------------------------------------------------------------------
    // 2. BalanceSyncPacket codec
    // ------------------------------------------------------------------

    private static void testBalanceSyncCodec() {
        BalanceSyncPacket original = new BalanceSyncPacket(
                1234L, "Mora", "M", 1_000L, 7L);
        BalanceSyncPacket decoded = roundTrip(original, BalanceSyncPacket::encode,
                BalanceSyncPacket::decode);
        check(decoded.equals(original), "BalanceSyncPacket round-trips");

        expectThrows(IllegalArgumentException.class,
                () -> new BalanceSyncPacket(-1L, "Mora", "M", 1L, 1L),
                "negative balance rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new BalanceSyncPacket(1L, "M".repeat(33), "M", 1L, 1L),
                "over-long currency name rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new BalanceSyncPacket(1L, "Mora", "M".repeat(9), 1L, 1L),
                "over-long currency symbol rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new BalanceSyncPacket(1L, "Mora", "M", 0L, 1L),
                "zero timestamp rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new BalanceSyncPacket(1L, "Mora", "M", 1L, 0L),
                "zero sequence rejected");
    }

    // ------------------------------------------------------------------
    // 3. TransactionNotifyPacket codec
    // ------------------------------------------------------------------

    private static void testTransactionNotifyCodec() {
        TransactionNotifyPacket withMemo = new TransactionNotifyPacket(
                9L, TransactionNotifyPacket.DIRECTION_IN, 50L,
                "ab".repeat(32), "thanks", 2_000L);
        check(roundTrip(withMemo, TransactionNotifyPacket::encode,
                TransactionNotifyPacket::decode).equals(withMemo),
                "TransactionNotifyPacket round-trips with memo");

        TransactionNotifyPacket withoutMemo = new TransactionNotifyPacket(
                10L, TransactionNotifyPacket.DIRECTION_OUT, 50L,
                "ab".repeat(32), null, 2_000L);
        check(roundTrip(withoutMemo, TransactionNotifyPacket::encode,
                TransactionNotifyPacket::decode).equals(withoutMemo),
                "TransactionNotifyPacket round-trips without memo");

        expectThrows(IllegalArgumentException.class,
                () -> new TransactionNotifyPacket(1L, (byte) 2, 1L,
                        "ab", null, 1L),
                "invalid direction rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new TransactionNotifyPacket(1L,
                        TransactionNotifyPacket.DIRECTION_IN, 0L, "ab", null, 1L),
                "non-positive amount rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new TransactionNotifyPacket(1L,
                        TransactionNotifyPacket.DIRECTION_IN, 1L, "zz", null, 1L),
                "non-hex counterparty digest rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new TransactionNotifyPacket(1L,
                        TransactionNotifyPacket.DIRECTION_IN, 1L,
                        "ab".repeat(33), null, 1L),
                "over-long counterparty digest rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new TransactionNotifyPacket(1L,
                        TransactionNotifyPacket.DIRECTION_IN, 1L, "ab",
                        "x".repeat(129), 1L),
                "over-long memo rejected");
    }

    // ------------------------------------------------------------------
    // 4. NotificationPacket codec
    // ------------------------------------------------------------------

    private static void testNotificationPacketCodec() {
        NotificationPacket empty = new NotificationPacket(List.of());
        check(roundTrip(empty, NotificationPacket::encode,
                NotificationPacket::decode).count() == 0,
                "empty notification list round-trips");

        NotificationPacket filled = new NotificationPacket(List.of(
                new NotificationPacket.NotificationEntry(1L, 10L, "a"),
                new NotificationPacket.NotificationEntry(2L, 20L, null)
        ));
        check(roundTrip(filled, NotificationPacket::encode,
                NotificationPacket::decode).equals(filled),
                "notification list round-trips");

        List<NotificationPacket.NotificationEntry> tooMany = new ArrayList<>();
        for (int index = 0; index <= NotificationPacket.MAX_ENTRIES; index++) {
            tooMany.add(new NotificationPacket.NotificationEntry(
                    index + 1L, 1L, null));
        }
        expectThrows(IllegalArgumentException.class,
                () -> new NotificationPacket(tooMany),
                "over-limit notification list rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new NotificationPacket.NotificationEntry(0L, 1L, null),
                "non-positive notification id rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new NotificationPacket.NotificationEntry(1L, 0L, null),
                "non-positive amount rejected");
    }

    // ------------------------------------------------------------------
    // 5. side isolation source scan
    // ------------------------------------------------------------------

    private static void testSideIsolationSourceScan() throws IOException {
        Path root = Path.of(System.getProperty("fontainerepublic.projectDir", "."))
                .toAbsolutePath().normalize();
        Path display = root.resolve(
                "src/main/java/com/fontainerepublic/common/network/display");
        Path clientNet = root.resolve(
                "src/main/java/com/fontainerepublic/client/net");
        Path server = root.resolve("src/main/java/com/fontainerepublic/server");

        try (var files = Files.walk(display)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                check(!source.contains("net.minecraft.client"),
                        "common display must not import net.minecraft.client: " + file);
                check(!source.contains("import com.fontainerepublic.client."),
                        "common display must not import client classes: " + file);
                if (file.getFileName().toString().equals("DisplayMessageHandlers.java")) {
                    int references = count(source, "com.fontainerepublic.client.net");
                    int unsafeCalls = count(source, "DistExecutor.unsafeRunWhenOn");
                    check(references == 14 && unsafeCalls == 14,
                            "client executor referenced exactly fourteen times, "
                                    + "each inside a DistExecutor.unsafeRunWhenOn supplier");
                    check(!source.contains("DistExecutor.safeRunWhenOn"),
                            "handlers use unsafeRunWhenOn (Forge safe-referent "
                                    + "validation rejects mod-owned client classes)");
                    int lastUnsafeCall = -1;
                    String[] lines = source.split("\\R");
                    for (int index = 0; index < lines.length; index++) {
                        if (lines[index].contains("unsafeRunWhenOn")) {
                            lastUnsafeCall = index;
                        }
                        if (lines[index].contains("com.fontainerepublic.client.net")) {
                            check(lastUnsafeCall >= 0 && index > lastUnsafeCall,
                                    "client reference only inside the unsafe supplier: "
                                            + lines[index].trim());
                        }
                    }
                }
            }
        }

        check(Files.isRegularFile(clientNet.resolve("ClientNetworkExecutor.java"))
                        && Files.isRegularFile(
                        clientNet.resolve("ClientPresentationCache.java")),
                "client/net executor and cache exist");

        try (var files = Files.walk(server)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                check(!source.contains("com.fontainerepublic.client."),
                        "server/ must never reference client/: " + file);
            }
        }
    }

    // ------------------------------------------------------------------
    // 6. non-authoritative presentation cache
    // ------------------------------------------------------------------

    private static void testPresentationCache() {
        ClientPresentationCache cache = ClientPresentationCache.instance();
        cache.clear();

        BalanceSyncPacket balance = new BalanceSyncPacket(5L, "Mora", "M", 1L, 1L);
        cache.setBalance(balance);
        check(balance.equals(cache.balanceSnapshot()), "cache stores the balance snapshot");

        TransactionNotifyPacket notice = new TransactionNotifyPacket(
                1L, TransactionNotifyPacket.DIRECTION_IN, 5L, "ab", null, 1L);
        cache.appendTransaction(notice);
        check(cache.transactionNotices().size() == 1, "cache stores one transaction notice");

        for (int index = 0; index < ClientPresentationCache.MAX_TRANSACTIONS + 10; index++) {
            cache.appendTransaction(new TransactionNotifyPacket(
                    index + 2L, TransactionNotifyPacket.DIRECTION_OUT, 1L,
                    "ab", null, index + 2L));
        }
        check(cache.transactionNotices().size() == ClientPresentationCache.MAX_TRANSACTIONS,
                "transaction ring is bounded");

        cache.setNotifications(List.of(
                new NotificationPacket.NotificationEntry(1L, 5L, null)));
        check(cache.notificationEntries().size() == 1,
                "cache stores pending notification presentation");

        cache.clear();
        check(cache.balanceSnapshot() == null
                        && cache.transactionNotices().isEmpty()
                        && cache.notificationEntries().isEmpty(),
                "logout clears every presentation entry");
    }

    // ------------------------------------------------------------------
    // 7. absent/offline players: zero sends, zero exceptions
    // ------------------------------------------------------------------

    private static void testNotifierAbsentNoOp() {
        AtomicInteger sent = new AtomicInteger();
        NetworkSendService sendService = new NetworkSendService(
                connection -> false,
                (message, connection) -> sent.incrementAndGet()
        );
        ServerEconomyPresentationNotifier notifier = new ServerEconomyPresentationNotifier(
                sendService,
                new EmptySubjectRegistry(),
                () -> 1_000L,
                com.fontainerepublic.server.economy.api.CurrencyPresentation.DEFAULT,
                uuid -> Optional.empty()
        );

        EconomyAccount account = account(100L, 1L);
        notifier.syncAccount(PLAYER, account, List.of());
        notifier.balanceChanged(TARGET, account);
        TransferReceipt receipt = new TransferReceipt(
                1L, 1_000L, TARGET, TARGET, 10L, null, true);
        notifier.transferCompleted(receipt, account, account);
        check(sent.get() == 0,
                "offline/absent players produce zero sends and no exceptions");
    }

    // ------------------------------------------------------------------
    // 8. decorator: presentation failures never change the business result
    // ------------------------------------------------------------------

    private static void testDecoratorSwallowsPresentationFailures() {
        EconomyService delegate = new FakeEconomyService();
        EconomyPresentationNotifier throwing = new EconomyPresentationNotifier() {
            @Override
            public void syncAccount(UUID playerId, EconomyAccount account,
                                    List<NotificationSummary> pending) {
                throw new IllegalStateException("injected presentation failure");
            }

            @Override
            public void balanceChanged(SubjectId subjectId, EconomyAccount account) {
                throw new IllegalStateException("injected presentation failure");
            }

            @Override
            public void transferCompleted(TransferReceipt receipt,
                                          EconomyAccount from, EconomyAccount to) {
                throw new IllegalStateException("injected presentation failure");
            }

            @Override
            public void syncHistory(UUID playerId,
                                    com.fontainerepublic.server.economy.api.EconomyPage
                                            <com.fontainerepublic.server.economy.model
                                                    .EconomyTransaction> page) {
                throw new IllegalStateException("injected presentation failure");
            }
        };
        PresentationAwareEconomyService service =
                new PresentationAwareEconomyService(delegate, throwing);

        EconomyAccount ensured = service.ensureAccountForPlayer(PLAYER);
        check(ensured.balance() == 0L,
                "ensureAccountForPlayer result unchanged despite throwing notifier");
        TransferReceipt transferred = service.transfer(TARGET, TARGET, 10L, null);
        check(transferred.transactionId() == 5L,
                "transfer result unchanged despite throwing notifier");
        check(service.getBalance(TARGET) == 42L,
                "read path delegates unchanged");
    }

    // ------------------------------------------------------------------
    // harness helpers
    // ------------------------------------------------------------------

    private static EconomyAccount account(long balance, long revision) {
        return new EconomyAccount(
                EconomyAccount.CURRENT_SCHEMA_VERSION,
                TARGET,
                balance,
                revision,
                1_000L,
                revision,
                false
        );
    }

    private static <T> T roundTrip(
            T message,
            java.util.function.BiConsumer<T, FriendlyByteBuf> encoder,
            java.util.function.Function<FriendlyByteBuf, T> decoder
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

    private static int count(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static final class EmptySubjectRegistry implements SubjectRegistryService {
        @Override
        public com.fontainerepublic.server.registry.model.SubjectRecord ensurePlayerSubject(
                UUID playerId) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Optional<com.fontainerepublic.server.registry.model.SubjectRecord>
                findSubjectForPlayer(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public Optional<SubjectRecord> findBySubjectId(SubjectId subjectId) {
            return Optional.empty();
        }

        @Override
        public PublicRoutingResult resolveExactRegistryNumber(RegistryNumber number) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Optional<SubjectStatus> status(SubjectId subjectId) {
            return Optional.empty();
        }

        @Override
        public SubjectRecord updateStatus(SubjectId subjectId, SubjectStatus status) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static final class FakeEconomyService implements EconomyService {
        @Override
        public EconomyAccount ensureAccountForPlayer(UUID playerId) {
            return account(0L, 1L);
        }

        @Override
        public EconomyAccount ensureAccount(SubjectId subjectId) {
            return account(0L, 1L);
        }

        @Override
        public Optional<EconomyAccount> getAccount(SubjectId subjectId) {
            return Optional.of(account(42L, 2L));
        }

        @Override
        public long getBalance(SubjectId subjectId) {
            return 42L;
        }

        @Override
        public EconomyPage<EconomyTransaction> getRecentTransactions(
                SubjectId subjectId, long afterId, int limit) {
            return new EconomyPage<>(List.of(), afterId, false);
        }

        @Override
        public TransferReceipt transfer(SubjectId from, SubjectId to,
                                        long amount, String memo) {
            return new TransferReceipt(5L, 1_000L, from, to, amount, memo, true);
        }

        @Override
        public TransferReceipt transferByPlayer(UUID fromPlayerId, UUID toPlayerId,
                                                long amount, String memo) {
            return transfer(TARGET, TARGET, amount, memo);
        }

        @Override
        public TradeSettlementReceipt executeTradeSettlement(
                SubjectId a,
                SubjectId b,
                long aOffered,
                long bOffered,
                int taxRatePercent,
                String memo
        ) {
            return new TradeSettlementReceipt(
                    2_000L,
                    a,
                    b,
                    aOffered,
                    bOffered,
                    0L,
                    0L,
                    List.of(),
                    true
            );
        }

        @Override
        public MailPostageReceipt chargePostage(
                SubjectId payer, long postageFee, long attachmentFee, String memo) {
            return new MailPostageReceipt(
                    2_000L, payer, postageFee, attachmentFee,
                    postageFee + attachmentFee, 6L, true);
        }

        @Override
        public List<NotificationSummary> pendingNotifications(SubjectId subjectId) {
            return List.of();
        }

        @Override
        public void acknowledgeNotification(SubjectId subjectId, long notificationId) {
        }

        @Override
        public String formatBalance(long amount) {
            return Long.toString(amount);
        }

        @Override
        public long getTreasuryBalance() {
            return 0L;
        }

        @Override
        public EconomyTransaction deposit(SubjectId to, long amount, String memo,
                                          OnSiteContext context) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public EconomyTransaction withdraw(SubjectId from, long amount, String memo,
                                           OnSiteContext context) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public EconomyAccount freeze(SubjectId subjectId, String memo,
                                     OnSiteContext context) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public EconomyAccount unfreeze(SubjectId subjectId, String memo,
                                       OnSiteContext context) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> type, Runnable action, String message
    ) {
        try {
            action.run();
        } catch (Throwable thrown) {
            if (type.isInstance(thrown)) {
                return type.cast(thrown);
            }
            throw new AssertionError(message + " (unexpected: " + thrown + ")", thrown);
        }
        throw new AssertionError(message + " (no exception thrown)");
    }
}
