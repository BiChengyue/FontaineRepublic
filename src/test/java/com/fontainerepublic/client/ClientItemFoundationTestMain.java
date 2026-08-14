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
