package com.fontainerepublic.client;

import com.fontainerepublic.client.net.ClientPresentationCache;
import com.fontainerepublic.client.view.ClientViewProjection;
import com.fontainerepublic.common.network.NetworkProtocol;
import com.fontainerepublic.common.network.display.JusticeInfoPacket;
import com.fontainerepublic.common.network.display.LandInfoPacket;
import com.fontainerepublic.server.institution.presentation.InstitutionPresentationSync;
import com.fontainerepublic.server.network.NetworkSendService;
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
 * Dependency-free FR-CLIENT-001-IMPL-B3b validation (Level 1-2).
 *
 * <p>Covers the Stage B-3b additions: bounded codecs of the justice-case and
 * land-overview messages, the cache semantics, court/land display projections,
 * the login snapshot wiring (absent/offline players and empty service
 * suppliers produce zero sends and zero exceptions), the land aggregate value
 * bounds, and source-level side isolation.</p>
 */
public final class ClientStageB3bFoundationTestMain {

    private static final UUID PLAYER = UUID.fromString(
            "00000000-0000-0000-0000-0000000000aa");

    private ClientStageB3bFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testProtocolV5Contract();
        testJusticeCodec();
        testLandCodec();
        testCacheSemantics();
        testCourtProjection();
        testLandProjection();
        testLandSummaryBounds();
        testLoginSyncAbsentNoOp();
        testSideIsolationSourceScan();
        System.out.println("[FR-CLIENT-001-B3b] Client stage B-3b foundation validation passed");
    }

    // ------------------------------------------------------------------
    // 1. protocol v5 static contract
    // ------------------------------------------------------------------

    private static void testProtocolV5Contract() {
        check(NetworkProtocol.VERSION.equals("10"), "Protocol version is 10");
        check(NetworkProtocol.clientAccepts("10"), "Client accepts exact v10");
        check(!NetworkProtocol.clientAccepts("9"), "Client rejects old v9");
        check(NetworkProtocol.serverAccepts("10"), "Server accepts exact v10");
        check(!NetworkProtocol.serverAccepts("9"), "Server rejects old v9");
    }

    // ------------------------------------------------------------------
    // 2. JusticeInfoPacket codec
    // ------------------------------------------------------------------

    private static void testJusticeCodec() {
        JusticeInfoPacket empty = new JusticeInfoPacket(List.of(), 1_000L);
        check(roundTrip(empty, JusticeInfoPacket::encode,
                JusticeInfoPacket::decode).equals(empty),
                "empty justice summary round-trips");

        JusticeInfoPacket filled = new JusticeInfoPacket(
                List.of(
                        new JusticeInfoPacket.CaseEntry("c-1", "OPEN", "land report"),
                        new JusticeInfoPacket.CaseEntry("c-2", "CLOSED", "resolved")
                ),
                2_000L
        );
        check(roundTrip(filled, JusticeInfoPacket::encode,
                JusticeInfoPacket::decode).equals(filled),
                "filled justice summary round-trips");

        List<JusticeInfoPacket.CaseEntry> tooMany = new java.util.ArrayList<>();
        for (int index = 0; index <= JusticeInfoPacket.MAX_CASES; index++) {
            tooMany.add(new JusticeInfoPacket.CaseEntry(
                    "c-" + index, "OPEN", "x"));
        }
        expectThrows(IllegalArgumentException.class,
                () -> new JusticeInfoPacket(tooMany, 1L),
                "over-limit justice summary rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new JusticeInfoPacket.CaseEntry("", "OPEN", "x"),
                "empty case id rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new JusticeInfoPacket.CaseEntry(
                        "x".repeat(65), "OPEN", "x"),
                "over-long case id rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new JusticeInfoPacket(List.of(), 0L),
                "zero snapshot time rejected");
    }

    // ------------------------------------------------------------------
    // 3. LandInfoPacket codec
    // ------------------------------------------------------------------

    private static void testLandCodec() {
        LandInfoPacket empty = new LandInfoPacket(
                0, 0L, List.of(), 0L, 1_000L);
        check(roundTrip(empty, LandInfoPacket::encode,
                LandInfoPacket::decode).equals(empty),
                "empty land overview round-trips");

        LandInfoPacket filled = new LandInfoPacket(
                2,
                300L,
                List.of(
                        new LandInfoPacket.ZoneEntry("PUBLIC", 2, 300L)
                ),
                3L,
                2_000L
        );
        check(roundTrip(filled, LandInfoPacket::encode,
                LandInfoPacket::decode).equals(filled),
                "filled land overview round-trips");

        expectThrows(IllegalArgumentException.class,
                () -> new LandInfoPacket(-1, 0L, List.of(), 0L, 1L),
                "negative parcel count rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new LandInfoPacket(1, -1L, List.of(), 0L, 1L),
                "negative total area rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new LandInfoPacket(1, 1L, List.of(), -1L, 1L),
                "negative store revision rejected");
        List<LandInfoPacket.ZoneEntry> tooMany = new java.util.ArrayList<>();
        for (int index = 0; index <= LandInfoPacket.MAX_ZONES; index++) {
            tooMany.add(new LandInfoPacket.ZoneEntry("Z", 1, 1L));
        }
        expectThrows(IllegalArgumentException.class,
                () -> new LandInfoPacket(1, 1L, tooMany, 0L, 1L),
                "over-limit zone list rejected");
    }

    // ------------------------------------------------------------------
    // 4. cache semantics
    // ------------------------------------------------------------------

    private static void testCacheSemantics() {
        ClientPresentationCache cache = ClientPresentationCache.instance();
        cache.clear();
        check(cache.justiceSnapshot() == null && cache.landSnapshot() == null,
                "cache starts without court/land snapshots");

        JusticeInfoPacket justice = new JusticeInfoPacket(
                List.of(new JusticeInfoPacket.CaseEntry("c-1", "OPEN", "x")),
                1_000L);
        cache.setJustice(justice);
        check(justice.equals(cache.justiceSnapshot()),
                "cache stores the justice snapshot");

        LandInfoPacket land = new LandInfoPacket(
                1, 50L, List.of(new LandInfoPacket.ZoneEntry("PUBLIC", 1, 50L)),
                1L, 1_000L);
        cache.setLand(land);
        check(land.equals(cache.landSnapshot()), "cache stores the land snapshot");

        cache.clear();
        check(cache.justiceSnapshot() == null && cache.landSnapshot() == null,
                "logout clears court/land snapshots");
    }

    // ------------------------------------------------------------------
    // 5. projections
    // ------------------------------------------------------------------

    private static void testCourtProjection() {
        ClientPresentationCache cache = ClientPresentationCache.instance();
        cache.clear();
        check(ClientViewProjection.courtLines(cache).size() == 1
                        && ClientViewProjection.courtLines(cache).get(0)
                        .contains("No court"),
                "empty cache renders the court placeholder");

        cache.setJustice(new JusticeInfoPacket(
                List.of(new JusticeInfoPacket.CaseEntry("c-1", "OPEN", "land report")),
                1_000L));
        List<String> lines = ClientViewProjection.courtLines(cache);
        check(lines.size() == 1 && lines.get(0).contains("#c-1")
                        && lines.get(0).contains("OPEN")
                        && lines.get(0).contains("land report"),
                "court line renders id/stage/summary");
    }

    private static void testLandProjection() {
        ClientPresentationCache cache = ClientPresentationCache.instance();
        cache.clear();
        check(ClientViewProjection.landLines(cache).size() == 1
                        && ClientViewProjection.landLines(cache).get(0)
                        .contains("No land"),
                "empty cache renders the land placeholder");

        cache.setLand(new LandInfoPacket(
                3,
                120L,
                List.of(new LandInfoPacket.ZoneEntry("PUBLIC", 2, 100L)),
                2L,
                1_000L));
        List<String> lines = ClientViewProjection.landLines(cache);
        check(lines.size() == 2
                        && lines.get(0).contains("Parcels: 3")
                        && lines.get(0).contains("Area: 120")
                        && lines.get(1).contains("PUBLIC"),
                "land lines render totals and zones");
    }

    // ------------------------------------------------------------------
    // 6. land aggregate value bounds (server-side value, shared contract)
    // ------------------------------------------------------------------

    private static void testLandSummaryBounds() {
        expectThrows(IllegalArgumentException.class,
                () -> new com.fontainerepublic.server.land.api.LandSummary(
                        -1, 0L, List.of(), 0L),
                "negative parcel count rejected in the aggregate");
        expectThrows(IllegalArgumentException.class,
                () -> new com.fontainerepublic.server.land.api.LandSummary(
                        1, 1L, List.of(
                                new com.fontainerepublic.server.land.api.LandSummary
                                        .ZoneSummary(
                                        com.fontainerepublic.server.land.model.ZoneType.PUBLIC,
                                        -1, 1L
                                )
                        ), 0L),
                "negative zone count rejected in the aggregate");
        com.fontainerepublic.server.land.api.LandSummary valid =
                new com.fontainerepublic.server.land.api.LandSummary(
                        1, 10L, List.of(
                                new com.fontainerepublic.server.land.api.LandSummary
                                        .ZoneSummary(
                                        com.fontainerepublic.server.land.model.ZoneType.PUBLIC,
                                        1, 10L
                                )
                        ), 1L);
        check(valid.parcelCount() == 1 && valid.totalArea() == 10L
                        && valid.zones().size() == 1,
                "valid land aggregate accepted");
    }

    // ------------------------------------------------------------------
    // 7. login sync: absent/empty -> zero sends, zero exceptions
    // ------------------------------------------------------------------

    private static void testLoginSyncAbsentNoOp() {
        AtomicInteger sent = new AtomicInteger();
        NetworkSendService sendService = new NetworkSendService(
                connection -> true,
                (message, connection) -> sent.incrementAndGet()
        );
        InstitutionPresentationSync sync = new InstitutionPresentationSync(
                sendService,
                Optional::empty,
                Optional::empty,
                Optional::empty,
                Optional::empty,
                () -> 1_000L,
                uuid -> Optional.empty()
        );
        sync.sync(PLAYER);
        check(sent.get() == 0,
                "absent player and empty services produce zero sends");
    }

    // ------------------------------------------------------------------
    // 8. side isolation source scan
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
