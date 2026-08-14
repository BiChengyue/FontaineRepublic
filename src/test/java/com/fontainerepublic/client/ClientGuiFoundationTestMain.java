package com.fontainerepublic.client;

import com.fontainerepublic.client.gui.money.TransferFormComposer;
import com.fontainerepublic.client.net.ClientPresentationCache;
import com.fontainerepublic.client.view.ClientViewProjection;
import com.fontainerepublic.common.network.display.BalanceSyncPacket;
import com.fontainerepublic.common.network.display.NotificationPacket;
import com.fontainerepublic.common.network.display.TransactionNotifyPacket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Dependency-free FR-CLIENT-001-IMPL-B validation (Level 1-2).
 *
 * <p>Covers the GUI/HUD foundation: transfer-form command assembly with all
 * display bounds, the cache-to-view projections (balance rendering, bounded
 * flow, notification list), and the source-level side isolation (client/gui
 * and client/hud never import server classes; the server path never
 * references client/gui or client/hud; the mod entry references ClientManager
 * only through the DistExecutor supplier).</p>
 */
public final class ClientGuiFoundationTestMain {

    private ClientGuiFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testFormCommandAssembly();
        testFormRejections();
        testBalanceProjection();
        testFlowProjection();
        testNotificationProjection();
        testLandLocationResponseCorrelation();
        testSideIsolationSourceScan();
        testMainEntrySideIsolation();
        System.out.println("[FR-CLIENT-001-B] Client GUI/HUD foundation validation passed");
    }

    // ------------------------------------------------------------------
    // FR-LAND-CLAIM-001-FIX-01 F3: S2C result / screen target correlation
    // ------------------------------------------------------------------

    /**
     * A late inspect/claim response for one opened location must never render
     * in a newly opened screen for a different block. The pure target
     * correlation is outcome-tested: a mismatched echoed target is rejected
     * and a matching one is accepted, on both axis and dimension mismatches.
     */
    private static void testLandLocationResponseCorrelation() {
        String dim = "minecraft:overworld";
        // A screen opened at (100, 60, 200):
        //  - exact match of the echoed result target is accepted.
        check(com.fontainerepublic.client.landclaim.LandClaimTarget.matches(
                        dim, 100, 60, 200, dim, 100, 60, 200),
                "a matching dimension + coordinates must be accepted");
        //  - a different x is stale and must be ignored.
        check(!com.fontainerepublic.client.landclaim.LandClaimTarget.matches(
                        dim, 100, 60, 200, dim, 101, 60, 200),
                "a mismatched x target must be rejected");
        //  - a different y/z also rejects.
        check(!com.fontainerepublic.client.landclaim.LandClaimTarget.matches(
                        dim, 100, 60, 200, dim, 100, 61, 200),
                "a mismatched y target must be rejected");
        check(!com.fontainerepublic.client.landclaim.LandClaimTarget.matches(
                        dim, 100, 60, 200, dim, 100, 60, 201),
                "a mismatched z target must be rejected");
        //  - a different dimension rejects regardless of coordinates.
        check(!com.fontainerepublic.client.landclaim.LandClaimTarget.matches(
                        dim, 100, 60, 200, "minecraft:the_nether", 100, 60, 200),
                "a mismatched dimension must be rejected");
    }

    // ------------------------------------------------------------------
    // 1. transfer form command assembly
    // ------------------------------------------------------------------

    private static void testFormCommandAssembly() {
        check(
                TransferFormComposer.compose("Alice", "100", null)
                        .equals("fr money pay Alice 100"),
                "plain pay command assembled"
        );
        check(
                TransferFormComposer.compose("  Alice  ", "  100  ", "  thanks  ")
                        .equals("fr money pay Alice 100 thanks"),
                "inputs are trimmed and memo appended"
        );
        check(
                TransferFormComposer.compose(
                                "00000000-0000-0000-0000-0000000000bb",
                                "1",
                                "")
                        .equals("fr money pay 00000000-0000-0000-0000-0000000000bb 1"),
                "blank memo is omitted"
        );
        check(
                TransferFormComposer.compose("TT-000001-01", "9000000000000000", "memo with spaces")
                        .equals("fr money pay TT-000001-01 9000000000000000 memo with spaces"),
                "maximum amount and multi-word memo assemble"
        );
        check(
                TransferFormComposer.validateTarget(
                        "x".repeat(TransferFormComposer.MAX_TARGET)).length()
                        == TransferFormComposer.MAX_TARGET,
                "target at the exact bound is accepted"
        );
        check(
                TransferFormComposer.validateAmount("00042") == 42L,
                "leading-zero amount parses to 42"
        );
    }

    private static void testFormRejections() {
        expectThrows(IllegalArgumentException.class,
                () -> TransferFormComposer.compose(null, "1", null),
                "null target rejected");
        expectThrows(IllegalArgumentException.class,
                () -> TransferFormComposer.compose(" ", "1", null),
                "blank target rejected");
        expectThrows(IllegalArgumentException.class,
                () -> TransferFormComposer.compose(
                        "x".repeat(TransferFormComposer.MAX_TARGET + 1), "1", null),
                "over-long target rejected");
        expectThrows(IllegalArgumentException.class,
                () -> TransferFormComposer.compose("Al ice", "1", null),
                "whitespace target rejected (would break argument parsing)");
        expectThrows(IllegalArgumentException.class,
                () -> TransferFormComposer.compose("Alice", "", null),
                "blank amount rejected");
        expectThrows(IllegalArgumentException.class,
                () -> TransferFormComposer.compose("Alice", "-5", null),
                "negative amount rejected");
        expectThrows(IllegalArgumentException.class,
                () -> TransferFormComposer.compose("Alice", "1.5", null),
                "fractional amount rejected");
        expectThrows(IllegalArgumentException.class,
                () -> TransferFormComposer.compose("Alice", "1e3", null),
                "scientific-notation amount rejected");
        expectThrows(IllegalArgumentException.class,
                () -> TransferFormComposer.compose("Alice", "0", null),
                "zero amount rejected (below floor)");
        expectThrows(IllegalArgumentException.class,
                () -> TransferFormComposer.compose(
                        "Alice",
                        Long.toString(TransferFormComposer.MAX_AMOUNT + 1),
                        null),
                "amount above the display ceiling rejected");
        expectThrows(IllegalArgumentException.class,
                () -> TransferFormComposer.compose(
                        "Alice",
                        "999999999999999999999999999999",
                        null),
                "overflowing amount rejected");
        expectThrows(IllegalArgumentException.class,
                () -> TransferFormComposer.compose(
                        "Alice",
                        "1",
                        "x".repeat(TransferFormComposer.MAX_MEMO + 1)),
                "over-long memo rejected");
        check(TransferFormComposer.normalizeMemo("   ") == null,
                "blank memo normalizes to absent");
    }

    // ------------------------------------------------------------------
    // 2. cache-to-view projections
    // ------------------------------------------------------------------

    private static void testBalanceProjection() {
        ClientPresentationCache cache = newCache();
        check(ClientViewProjection.balanceLine(cache).equals("--"),
                "empty cache renders the balance placeholder");
        check(ClientViewProjection.currencyName(cache).equals("--"),
                "empty cache renders the currency placeholder");

        cache.setBalance(new BalanceSyncPacket(1_234_567L, "Mora", "M", 1_000L, 7L));
        check(ClientViewProjection.balanceLine(cache).equals("1,234,567 M"),
                "balance line renders grouped amount and symbol");
        check(ClientViewProjection.currencyName(cache).equals("Mora"),
                "currency caption renders the name");
    }

    private static void testFlowProjection() {
        ClientPresentationCache cache = newCache();
        check(ClientViewProjection.transactionLines(cache).isEmpty(),
                "empty cache renders an empty flow");

        cache.appendTransaction(new TransactionNotifyPacket(
                1L, TransactionNotifyPacket.DIRECTION_IN, 50L,
                "ab12cd34", "thanks", 1_000L));
        cache.appendTransaction(new TransactionNotifyPacket(
                2L, TransactionNotifyPacket.DIRECTION_OUT, 20L,
                "deadbeef", null, 2_000L));
        List<String> lines = ClientViewProjection.transactionLines(cache);
        check(lines.size() == 2, "flow renders one line per notice");
        check(lines.get(0).equals("#2 OUT 20 deadbeef"),
                "newest first, no memo omitted");
        check(lines.get(1).equals("#1 IN 50 ab12cd34 \"thanks\""),
                "incoming direction and memo rendered");
        check(ClientViewProjection.digestPrefix("abcdef0123456789").equals("abcdef01"),
                "digest prefix is bounded to 8 chars");
    }

    private static void testNotificationProjection() {
        ClientPresentationCache cache = newCache();
        check(ClientViewProjection.notificationLines(cache).isEmpty(),
                "empty cache renders an empty notification list");

        cache.setNotifications(List.of(
                new NotificationPacket.NotificationEntry(11L, 5L, "a"),
                new NotificationPacket.NotificationEntry(12L, 7L, null)
        ));
        List<String> lines = ClientViewProjection.notificationLines(cache);
        check(lines.size() == 2, "notification list renders one line per entry");
        check(lines.get(0).equals("#11 +5 a"), "first notification line");
        check(lines.get(1).equals("#12 +7"), "second notification line (no memo)");
    }

    // ------------------------------------------------------------------
    // 3. side isolation source scan
    // ------------------------------------------------------------------

    private static void testSideIsolationSourceScan() throws IOException {
        Path root = Path.of(System.getProperty("fontainerepublic.projectDir", "."))
                .toAbsolutePath().normalize();
        Path client = root.resolve("src/main/java/com/fontainerepublic/client");
        Path server = root.resolve("src/main/java/com/fontainerepublic/server");

        try (var files = Files.walk(client)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                check(!source.contains("import com.fontainerepublic.server."),
                        "client/ must never import server classes: " + file);
                check(!source.contains("import net.minecraft.server."),
                        "client/ must never import server-side Minecraft classes: " + file);
            }
        }

        try (var files = Files.walk(server)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                check(!source.contains("com.fontainerepublic.client."),
                        "server/ must never reference client/: " + file);
            }
        }

        check(Files.isRegularFile(client.resolve(
                        "gui/money/MoneyScreen.java"))
                        && Files.isRegularFile(client.resolve(
                        "hud/FrHudRenderer.java")),
                "client gui/hud surfaces exist");
    }

    // ------------------------------------------------------------------
    // 4. mod entry references ClientManager only from the client-setup
    //    listener (FMLClientSetupEvent fires only on the physical client)
    // ------------------------------------------------------------------

    private static void testMainEntrySideIsolation() throws IOException {
        Path root = Path.of(System.getProperty("fontainerepublic.projectDir", "."))
                .toAbsolutePath().normalize();
        Path main = root.resolve("src/main/java/com/fontainerepublic/FontaineRepublic.java");
        String source = Files.readString(main, StandardCharsets.UTF_8);
        check(source.contains("modEventBus.addListener(this::onClientSetup)"),
                "mod entry registers the client-setup listener");
        check(source.contains("private void onClientSetup(FMLClientSetupEvent event)"),
                "mod entry declares the client-only setup handler");
        int clientManagerReferences = count(source, "com.fontainerepublic.client.ClientManager");
        check(clientManagerReferences == 1,
                "mod entry references ClientManager exactly once");
        int handlerLine = -1;
        String[] lines = source.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].contains("private void onClientSetup")) {
                handlerLine = index;
            }
            if (lines[index].contains("com.fontainerepublic.client.ClientManager")) {
                check(handlerLine >= 0 && index > handlerLine,
                        "ClientManager reference sits inside the client-setup handler");
            }
        }
    }

    // ------------------------------------------------------------------
    // harness helpers
    // ------------------------------------------------------------------

    private static ClientPresentationCache newCache() {
        // The presentation cache is a singleton; every projection test starts
        // from a cleared cache so the order of tests does not matter.
        ClientPresentationCache cache = ClientPresentationCache.instance();
        cache.clear();
        return cache;
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
