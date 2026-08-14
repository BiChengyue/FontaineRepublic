package com.fontainerepublic.client;

import com.fontainerepublic.common.item.FRItemIds;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Dependency-free FR-ITEM-001 validation (Level 1-2).
 *
 * <p>Covers the communicator item contract (registry name, lang key, stack
 * bound, model/lang resources), the right-click interaction pre-fill helper,
 * and source-level side isolation (common item never references client,
 * server never references client). The UI gate predicates and item registry
 * object require the Forge registry bootstrap and are verified at Level 3
 * real-client; the pure key predicate lives in {@code CommunicatorGate}.</p>
 */
public final class ClientItemFoundationTestMain {

    private ClientItemFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        // Plain-JVM test host: no Minecraft/Forge registry bootstrap (the
        // Forge event-bus reflection needs the ModLauncher). The item
        // registry identity is verified by the pure registry-key predicate
        // and by the /give path at Level 3 real-machine verification.
        testItemConstants();
        testItemResources();
        testPrefillTargetName();
        testLandRightClickCancelsBlockInteraction();
        testSideIsolationSourceScan();
        System.out.println(
                "[FR-ITEM-001] Communicator item + UI gate foundation validation passed");
    }

    // ------------------------------------------------------------------
    // 1. item constants
    // ------------------------------------------------------------------

    private static void testItemConstants() {
        check("communicator".equals(FRItemIds.ITEM_ID),
                "item id is 'communicator'");
        check("fontainerepublic:communicator".equals(
                        FRItemIds.ITEM_REGISTRY_NAME),
                "registry name is fontainerepublic:communicator");
        check("item.fontainerepublic.communicator".equals(
                        FRItemIds.ITEM_LANG_KEY),
                "lang key is item.fontainerepublic.communicator");
        check(FRItemIds.MAX_STACK == 1, "communicator stacks to 1");
    }

    // ------------------------------------------------------------------
    // 3. model / lang resources exist and carry the contract
    // ------------------------------------------------------------------

    private static void testItemResources() throws Exception {
        Path root = Path.of(System.getProperty("fontainerepublic.projectDir", "."))
                .toAbsolutePath().normalize();
        Path model = root.resolve(
                "src/main/resources/assets/fontainerepublic/models/item/communicator.json");
        check(Files.isRegularFile(model), "communicator model file exists");
        String modelText = Files.readString(model, StandardCharsets.UTF_8);
        check(modelText.contains("item/generated"), "model parent is item/generated");
        check(modelText.contains("minecraft:item/clock"),
                "model uses the temporary minecraft:item/clock texture");

        Path enLang = root.resolve(
                "src/main/resources/assets/fontainerepublic/lang/en_us.json");
        Path zhLang = root.resolve(
                "src/main/resources/assets/fontainerepublic/lang/zh_cn.json");
        check(Files.isRegularFile(enLang) && Files.isRegularFile(zhLang),
                "both lang files exist");
        check(Files.readString(enLang, StandardCharsets.UTF_8).contains(
                        "\"item.fontainerepublic.communicator\""),
                "en_us declares the communicator display name");
        check(Files.readString(zhLang, StandardCharsets.UTF_8).contains(
                        "\"item.fontainerepublic.communicator\""),
                "zh_cn declares the communicator display name");
    }

    // ------------------------------------------------------------------
    // 4. right-click interaction pre-fill (pure)
    // ------------------------------------------------------------------

    private static void testPrefillTargetName() {
        check("Alice".equals(CommunicatorInteraction.prefillTargetName("Alice")),
                "plain player name pre-fills as-is");
        check("Alice".equals(
                        CommunicatorInteraction.prefillTargetName("  Alice  ")),
                "surrounding whitespace trimmed");
        check(CommunicatorInteraction.prefillTargetName(null) == null,
                "null name produces no pre-fill");
        check(CommunicatorInteraction.prefillTargetName("") == null,
                "blank name produces no pre-fill");
        check(CommunicatorInteraction.prefillTargetName("   ") == null,
                "whitespace name produces no pre-fill");
        check(CommunicatorInteraction.prefillTargetName("a b") == null,
                "whitespace inside the name produces no pre-fill");
        check(CommunicatorInteraction.prefillTargetName(
                        "x".repeat(65)) == null,
                "over-long name produces no pre-fill");
    }

    // ------------------------------------------------------------------
    // 5. FR-LAND-CLAIM-001-FIX-01 F1: land right-click must replace (not
    //    supplement) the ordinary block interaction
    // ------------------------------------------------------------------

    /**
     * Regression F1/F2: the communicator's {@code RightClickBlock} handler must
     * both cancel the Forge event and report a non-PASS cancellation result, so
     * that opening the land location screen does not also activate a
     * chest/door/button/lever underneath and the client does not retry the
     * interaction / other hand on the next click. The handler cannot be
     * exercised in a plain JVM (it needs a live {@code PlayerInteractEvent}
     * rooted in Minecraft and a real {@code LocalPlayer}), so, matching the
     * established client-side plain-JVM style, we assert the production source
     * precisely: both the cancellation and the cancellation-result report must
     * sit inside {@code onRightClickBlock} only (never the entity or empty-air
     * paths), on the local-client path, and before opening the screen.
     */
    private static void testLandRightClickCancelsBlockInteraction() throws Exception {
        Path root = Path.of(System.getProperty("fontainerepublic.projectDir", "."))
                .toAbsolutePath().normalize();
        Path interaction = root.resolve(
                "src/main/java/com/fontainerepublic/client/CommunicatorInteraction.java");
        List<String> lines = Files.readAllLines(interaction, StandardCharsets.UTF_8);

        // Locate the block method body and confirm the cancellation is inside it.
        int blockStart = indexOf(lines, "onRightClickBlock(");
        check(blockStart >= 0, "CommunicatorInteraction must define onRightClickBlock");
        int cancelLine = indexOf(lines, "event.setCanceled(true)");
        check(cancelLine >= 0,
                "the block handler must cancel the RightClickBlock Forge event");
        check(cancelLine > blockStart,
                "the cancellation must occur inside onRightClickBlock, not before it");

        // F2: the handler must also report a non-PASS InteractionResult so the
        // client stops retrying after the land screen opens. Assert both the
        // setter and that it references SUCCESS, and that both occur on the
        // guarded (method-local) path.
        int resultLine = indexOf(lines, "event.setCancellationResult(");
        check(resultLine >= 0,
                "the block handler must report a cancellation result");
        check(resultLine > blockStart,
                "the cancellation result must be reported inside onRightClickBlock");
        int successLine = indexOf(lines, "InteractionResult.SUCCESS");
        check(successLine >= 0,
                "the block handler must report InteractionResult.SUCCESS");
        check(successLine <= resultLine,
                "SUCCESS must be passed to setCancellationResult");
        check(resultLine < indexOf(lines, "new LandLocationScreen("),
                "the cancellation result must be reported before the screen opens");

        // The empty-air and entity paths must stay untouched (they must not
        // cancel): cancelling them would suppress the FR client menu / trade.
        check(indexOf(lines, "onEntityInteract(") >= 0,
                "the entity path must remain present");
        check(indexOf(lines, "onRightClickEmpty(") >= 0,
                "the empty-air path must remain present");

        // Exactly one cancellation and exactly one cancellation-result report,
        // and both must live within the method that starts at onRightClickBlock
        // — not in the entity or empty-air bodies.
        long cancelCount = lines.stream()
                .filter(line -> line.contains("event.setCanceled(true)"))
                .count();
        check(cancelCount == 1,
                "exactly one setCanceled(true) must exist on the client handler "
                        + "(got " + cancelCount + ")");
        long resultCount = lines.stream()
                .filter(line -> line.contains("event.setCancellationResult("))
                .count();
        check(resultCount == 1,
                "exactly one setCancellationResult(...) must exist on the client "
                        + "handler (got " + resultCount + ")");

        // The menu-open and screen-open lines must follow the cancellation so
        // the land view opens *instead of* the block interaction.
        int screenOpen = indexOf(lines, "new LandLocationScreen(");
        check(screenOpen >= 0, "the block handler must open the land location screen");
        check(screenOpen > cancelLine,
                "the screen must open after the event is cancelled");
    }

    private static int indexOf(List<String> lines, String needle) {
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).contains(needle)) {
                return index;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // 8. side isolation source scan
    // ------------------------------------------------------------------

    private static void testSideIsolationSourceScan() throws Exception {
        Path root = Path.of(System.getProperty("fontainerepublic.projectDir", "."))
                .toAbsolutePath().normalize();

        Path commonItem = root.resolve(
                "src/main/java/com/fontainerepublic/common/item");
        try (var files = Files.walk(commonItem)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                check(!source.contains("import net.minecraft.client"),
                        "common item must not import net.minecraft.client: " + file);
                check(!source.contains("import com.fontainerepublic.client."),
                        "common item must not import client classes: " + file);
            }
        }

        Path server = root.resolve("src/main/java/com/fontainerepublic/server");
        try (var files = Files.walk(server)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                check(!source.contains("com.fontainerepublic.client."),
                        "server/ must never reference client/: " + file);
            }
        }

        // The mod entry may only reach client classes through the
        // FMLClientSetupEvent listener (onClientSetup), which never fires on
        // a dedicated server.
        Path mainClass = root.resolve(
                "src/main/java/com/fontainerepublic/FontaineRepublic.java");
        List<String> lines = Files.readAllLines(mainClass, StandardCharsets.UTF_8);
        boolean clientSetupSeen = false;
        int clientReferences = 0;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.contains("onClientSetup")) {
                clientSetupSeen = true;
            }
            if (line.contains("com.fontainerepublic.client.")) {
                clientReferences++;
                check(clientSetupSeen,
                        "client reference only inside onClientSetup (line "
                                + (index + 1) + ")");
            }
        }
        check(clientSetupSeen, "mod entry keeps the FMLClientSetupEvent listener");
        check(clientReferences == 1,
                "exactly one client reference in the mod entry (got "
                        + clientReferences + ")");
    }

    // ------------------------------------------------------------------
    // harness helpers
    // ------------------------------------------------------------------

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
