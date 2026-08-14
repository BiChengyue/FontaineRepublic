package com.fontainerepublic.client;

import com.fontainerepublic.client.net.ClientPresentationCache;
import com.fontainerepublic.client.view.ClientViewProjection;
import com.fontainerepublic.common.network.display.CitizenInfoPacket;
import com.fontainerepublic.common.network.display.TransactionHistorySyncPacket;
import com.fontainerepublic.server.citizen.api.CitizenReceipt;
import com.fontainerepublic.server.citizen.api.CitizenService;
import com.fontainerepublic.server.citizen.model.CitizenRank;
import com.fontainerepublic.server.citizen.model.CitizenRecord;
import com.fontainerepublic.server.citizen.model.CitizenStatus;
import com.fontainerepublic.server.citizen.presentation.CitizenPresentationNotifier;
import com.fontainerepublic.server.citizen.presentation.PresentationAwareCitizenService;
import com.fontainerepublic.server.economy.api.CurrencyPresentation;
import com.fontainerepublic.server.economy.api.EconomyPage;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.economy.presentation.ServerEconomyPresentationNotifier;
import com.fontainerepublic.server.network.NetworkSendService;
import com.fontainerepublic.server.registry.api.PublicRoutingResult;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dependency-free FR-CLIENT-001-IMPL-B2 validation (Level 1-2).
 *
 * <p>Covers the Stage B-2 additions: bounded codecs of the citizen card and
 * transaction-history messages, the history cache semantics, citizen/history
 * display projections, the advisory citizen presentation decorator, and the
 * economy history-sync notifier (absent/offline players and throwing
 * notifiers never change business results).</p>
 */
public final class ClientStageB2FoundationTestMain {

    private static final UUID PLAYER = UUID.fromString(
            "00000000-0000-0000-0000-0000000000aa");

    private ClientStageB2FoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testCitizenInfoCodec();
        testHistoryCodec();
        testHistoryCacheSemantics();
        testCitizenProjection();
        testHistoryProjection();
        testCitizenDecoratorSwallowsPresentationFailures();
        testHistoryNotifierAbsentNoOp();
        testSideIsolationSourceScan();
        System.out.println("[FR-CLIENT-001-B2] Client stage B-2 foundation validation passed");
    }

    // ------------------------------------------------------------------
    // 1. CitizenInfoPacket codec
    // ------------------------------------------------------------------

    private static void testCitizenInfoCodec() {
        CitizenInfoPacket original = new CitizenInfoPacket(
                "10-000001-61", "CITIZEN", "GOD", 1_000L, 2_000L);
        check(roundTrip(original, CitizenInfoPacket::encode,
                CitizenInfoPacket::decode).equals(original),
                "CitizenInfoPacket round-trips");

        expectThrows(IllegalArgumentException.class,
                () -> new CitizenInfoPacket("", "CITIZEN", "GOD", 1L, 1L),
                "empty registry number rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new CitizenInfoPacket("x".repeat(33), "CITIZEN", "GOD", 1L, 1L),
                "over-long registry number rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new CitizenInfoPacket("10-1", "", "GOD", 1L, 1L),
                "empty citizen status rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new CitizenInfoPacket("10-1", "CITIZEN", "", 1L, 1L),
                "empty citizen rank rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new CitizenInfoPacket("10-1", "CITIZEN", "GOD", 0L, 1L),
                "zero firstCitizenAt rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new CitizenInfoPacket("10-1", "CITIZEN", "GOD", 1L, 0L),
                "zero snapshot time rejected");
    }

    // ------------------------------------------------------------------
    // 2. TransactionHistorySyncPacket codec
    // ------------------------------------------------------------------

    private static void testHistoryCodec() {
        TransactionHistorySyncPacket empty = new TransactionHistorySyncPacket(
                List.of(), 0L, false, 1_000L);
        check(roundTrip(empty, TransactionHistorySyncPacket::encode,
                TransactionHistorySyncPacket::decode).equals(empty),
                "empty history page round-trips");

        TransactionHistorySyncPacket filled = new TransactionHistorySyncPacket(
                List.of(
                        new TransactionHistorySyncPacket.HistoryEntry(
                                1L, TransactionHistorySyncPacket.HistoryEntry.DIRECTION_IN,
                                50L, "ab".repeat(32), "thanks", 2_000L),
                        new TransactionHistorySyncPacket.HistoryEntry(
                                2L, TransactionHistorySyncPacket.HistoryEntry.DIRECTION_OUT,
                                20L, "cd".repeat(32), null, 3_000L)
                ),
                2L,
                true,
                4_000L
        );
        check(roundTrip(filled, TransactionHistorySyncPacket::encode,
                TransactionHistorySyncPacket::decode).equals(filled),
                "filled history page round-trips");

        List<TransactionHistorySyncPacket.HistoryEntry> tooMany = new java.util.ArrayList<>();
        for (int index = 0; index <= TransactionHistorySyncPacket.MAX_ENTRIES; index++) {
            tooMany.add(new TransactionHistorySyncPacket.HistoryEntry(
                    index + 1L,
                    TransactionHistorySyncPacket.HistoryEntry.DIRECTION_IN,
                    1L, "ab", null, index + 1L));
        }
        expectThrows(IllegalArgumentException.class,
                () -> new TransactionHistorySyncPacket(tooMany, 0L, false, 1L),
                "over-limit history page rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new TransactionHistorySyncPacket(List.of(), -1L, false, 1L),
                "negative nextAfterId rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new TransactionHistorySyncPacket.HistoryEntry(
                        1L, (byte) 2, 1L, "ab", null, 1L),
                "invalid history direction rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new TransactionHistorySyncPacket.HistoryEntry(
                        1L, TransactionHistorySyncPacket.HistoryEntry.DIRECTION_IN,
                        0L, "ab", null, 1L),
                "non-positive history amount rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new TransactionHistorySyncPacket.HistoryEntry(
                        1L, TransactionHistorySyncPacket.HistoryEntry.DIRECTION_IN,
                        1L, "zz", null, 1L),
                "non-hex history digest rejected");
    }

    // ------------------------------------------------------------------
    // 3. history cache semantics
    // ------------------------------------------------------------------

    private static void testHistoryCacheSemantics() {
        ClientPresentationCache cache = ClientPresentationCache.instance();
        cache.clear();
        check(cache.historySnapshot() == null, "cache starts without history");

        TransactionHistorySyncPacket page = new TransactionHistorySyncPacket(
                List.of(new TransactionHistorySyncPacket.HistoryEntry(
                        1L, TransactionHistorySyncPacket.HistoryEntry.DIRECTION_IN,
                        5L, "ab", null, 1L)),
                1L,
                false,
                1_000L
        );
        cache.setHistory(page);
        check(page.equals(cache.historySnapshot()), "cache stores the history page");

        cache.clear();
        check(cache.historySnapshot() == null, "logout clears history");
    }

    // ------------------------------------------------------------------
    // 4. citizen / history projections
    // ------------------------------------------------------------------

    private static void testCitizenProjection() {
        ClientPresentationCache cache = ClientPresentationCache.instance();
        cache.clear();
        check(ClientViewProjection.citizenLines(cache).size() == 1
                        && ClientViewProjection.citizenLines(cache).get(0)
                        .contains("No citizen card"),
                "empty cache renders the citizen placeholder");

        cache.setCitizen(new CitizenInfoPacket(
                "10-000001-61", "CITIZEN", "GOD", 1_000L, 2_000L));
        List<String> lines = ClientViewProjection.citizenLines(cache);
        check(lines.size() >= 4
                        && lines.get(0).contains("10-000001-61")
                        && String.join(" ", lines).contains("CITIZEN")
                        && String.join(" ", lines).contains("GOD"),
                "citizen card renders number/status/rank");
    }

    private static void testHistoryProjection() {
        ClientPresentationCache cache = ClientPresentationCache.instance();
        cache.clear();
        check(ClientViewProjection.historyLines(cache).size() == 1
                        && ClientViewProjection.historyLines(cache).get(0)
                        .contains("No history"),
                "empty cache renders the history placeholder");

        cache.setHistory(new TransactionHistorySyncPacket(
                List.of(new TransactionHistorySyncPacket.HistoryEntry(
                        7L, TransactionHistorySyncPacket.HistoryEntry.DIRECTION_OUT,
                        30L, "abcdef01", "rent", 1_000L)),
                7L,
                false,
                1_000L
        ));
        List<String> lines = ClientViewProjection.historyLines(cache);
        check(lines.size() == 1 && lines.get(0).contains("#7")
                        && lines.get(0).contains("OUT 30")
                        && lines.get(0).contains("abcdef01"),
                "history line renders id/direction/amount/digest");
    }

    // ------------------------------------------------------------------
    // 5. citizen decorator swallows presentation failures
    // ------------------------------------------------------------------

    private static void testCitizenDecoratorSwallowsPresentationFailures() {
        CitizenRecord record = new CitizenRecord(
                1, PLAYER, SubjectId.of(PLAYER), CitizenStatus.CITIZEN,
                CitizenRank.CITIZEN, 1_000L, 1L);
        CitizenService delegate = new FakeCitizenService(record);
        CitizenPresentationNotifier throwing = (playerId, citizenRecord) -> {
            throw new IllegalStateException("injected presentation failure");
        };
        PresentationAwareCitizenService service =
                new PresentationAwareCitizenService(delegate, throwing);
        check(service.ensureCitizen(PLAYER).equals(record),
                "ensureCitizen result unchanged despite throwing notifier");
        check(service.getCitizen(PLAYER).isPresent(),
                "read path delegates unchanged");
    }

    // ------------------------------------------------------------------
    // 6. history notifier: absent players -> zero sends, zero exceptions
    // ------------------------------------------------------------------

    private static void testHistoryNotifierAbsentNoOp() {
        AtomicInteger sent = new AtomicInteger();
        NetworkSendService sendService = new NetworkSendService(
                connection -> false,
                (message, connection) -> sent.incrementAndGet()
        );
        ServerEconomyPresentationNotifier notifier = new ServerEconomyPresentationNotifier(
                sendService,
                new EmptySubjectRegistry(),
                () -> 1_000L,
                CurrencyPresentation.DEFAULT,
                uuid -> Optional.empty()
        );
        notifier.syncHistory(
                PLAYER,
                new EconomyPage<>(List.of(), 0L, false)
        );
        check(sent.get() == 0,
                "offline player produces zero sends and no exceptions");
    }

    // ------------------------------------------------------------------
    // 7. side isolation source scan
    // ------------------------------------------------------------------

    private static void testSideIsolationSourceScan() throws Exception {
        Path root = Path.of(System.getProperty("fontainerepublic.projectDir", "."))
                .toAbsolutePath().normalize();
        Path display = root.resolve(
                "src/main/java/com/fontainerepublic/common/network/display");
        try (var files = Files.walk(display)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                check(!source.contains("import net.minecraft.client"),
                        "common display must not import net.minecraft.client: " + file);
                check(!source.contains("import com.fontainerepublic.client."),
                        "common display must not import client classes: " + file);
            }
        }
        Path server = root.resolve("src/main/java/com/fontainerepublic/server");
        try (var files = Files.walk(server)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                check(!source.contains("com.fontainerepublic.client."),
                        "server/ must never reference client/: " + file);
            }
        }
    }

    // ------------------------------------------------------------------
    // harness helpers
    // ------------------------------------------------------------------

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

    private static final class FakeCitizenService implements CitizenService {
        private final CitizenRecord record;

        FakeCitizenService(CitizenRecord record) {
            this.record = record;
        }

        @Override
        public CitizenRecord ensureCitizen(UUID playerId) {
            return record;
        }

        @Override
        public Optional<CitizenRecord> getCitizen(UUID playerId) {
            return Optional.of(record);
        }

        @Override
        public CitizenReceipt setRank(UUID playerId, CitizenRank rank) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public CitizenReceipt setStatus(UUID playerId, CitizenStatus status) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public List<CitizenService.CitizenIdentity> activeCitizens(int limit) {
            return List.of();
        }
    }

    private static final class EmptySubjectRegistry implements SubjectRegistryService {
        @Override
        public SubjectRecord ensurePlayerSubject(UUID playerId) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Optional<SubjectRecord> findSubjectForPlayer(UUID playerId) {
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
