package com.fontainerepublic.client;

import com.fontainerepublic.common.item.FRItemIds;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Dependency-free FR-ITEM-003 validation (Level 1-2).
 *
 * <p>Covers the custom communicator item contract (registry id/path, display
 * name) and source-level assertions that the {@code DeferredRegister<Item>}
 * custom item is back (reversing FR-ITEM-002-A's removal) and that the common
 * item package references no client classes. The UI gate and live item
 * registry are verified at Level 3 real-client.</p>
 */
public final class ClientItemFoundationTestMain {

    private ClientItemFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testItemConstants();
        testCustomItemIsRegistered();
        testPrefillTargetName();
        testLandRightClickCancelsBlockInteraction();
        testMainHandAirRightClickClaimsItemUse();
        testSideIsolationSourceScan();
        System.out.println(
                "[FR-ITEM-003] Custom communicator item + UI gate foundation validation passed");
    }

    // ------------------------------------------------------------------
    // 1. item constants (custom communicator item)
    // ------------------------------------------------------------------

    private static void testItemConstants() {
        check("communicator".equals(FRItemIds.COMMUNICATOR_ID),
                "communicator item id is 'communicator'");
        check("fontainerepublic:communicator".equals(FRItemIds.COMMUNICATOR_REGISTRY_NAME),
                "communicator registry name is fontainerepublic:communicator");
        check("fontainerepublic".equals(FRItemIds.COMMUNICATOR_ITEM.getNamespace()),
                "communicator item namespace is fontainerepublic");
        check("communicator".equals(FRItemIds.COMMUNICATOR_ITEM.getPath()),
                "communicator item path is communicator");
        check("传讯水镜".equals(FRItemIds.DISPLAY_NAME),
                "display name is 传讯水镜");
    }

    // ------------------------------------------------------------------
    // 2. the custom DeferredRegister<Item> is registered again (reversing
    //    FR-ITEM-002-A's removal)
    // ------------------------------------------------------------------

    private static void testCustomItemIsRegistered() throws Exception {
        Path root = Path.of(System.getProperty("fontainerepublic.projectDir", "."))
                .toAbsolutePath().normalize();
        Path items = root.resolve(
                "src/main/java/com/fontainerepublic/common/item/FRItems.java");
        check(Files.exists(items), "FRItems.java (DeferredRegister<Item>) is present");

        String source = Files.readString(items, StandardCharsets.UTF_8);
        check(source.contains("DeferredRegister<Item>"),
                "FRItems.java declares a DeferredRegister<Item>");
        check(source.contains("ForgeRegistries.ITEMS"),
                "FRItems.java targets ForgeRegistries.ITEMS");
        check(source.contains("FRItemIds.COMMUNICATOR_ID"),
                "FRItems.java registers under FRItemIds.COMMUNICATOR_ID");
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

    private static void testLandRightClickCancelsBlockInteraction() throws Exception {
        Path root = Path.of(System.getProperty("fontainerepublic.projectDir", "."))
                .toAbsolutePath().normalize();
        Path interaction = root.resolve(
                "src/main/java/com/fontainerepublic/client/CommunicatorInteraction.java");
        List<String> lines = Files.readAllLines(interaction, StandardCharsets.UTF_8);

        int blockStart = indexOf(lines, "onRightClickBlock(");
        check(blockStart >= 0, "CommunicatorInteraction must define onRightClickBlock");
        int cancelLine = indexOf(lines, "event.setCanceled(true)");
        check(cancelLine >= 0,
                "the block handler must cancel the RightClickBlock Forge event");
        check(cancelLine > blockStart,
                "the cancellation must occur inside onRightClickBlock, not before it");

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

        check(indexOf(lines, "onEntityInteract(") >= 0,
                "the entity path must remain present");
        check(indexOf(lines, "onRightClickEmpty(") >= 0,
                "the empty-air path must remain present");

        long cancelCount = lines.stream()
                .filter(line -> line.contains("event.setCanceled(true)"))
                .count();
        check(cancelCount == 2,
                "exactly two setCanceled(true) must exist on the client handler "
                        + "(block + main-hand air item; got " + cancelCount + ")");
        long resultCount = lines.stream()
                .filter(line -> line.contains("event.setCancellationResult("))
                .count();
        check(resultCount == 2,
                "exactly two setCancellationResult(...) must exist on the client "
                        + "handler (block + main-hand air item; got " + resultCount + ")");

        int screenOpen = indexOf(lines, "new LandLocationScreen(");
        check(screenOpen >= 0, "the block handler must open the land location screen");
        check(screenOpen > cancelLine,
                "the screen must open after the event is cancelled");
    }

    // ------------------------------------------------------------------
    // 6. offhand-conflict fix: the main-hand communicator's air
    //    right-click must cancel RightClickItem with SUCCESS (so a usable
    //    offhand item cannot steal priority) and open the FR main menu.
    // ------------------------------------------------------------------

    private static void testMainHandAirRightClickClaimsItemUse() throws Exception {
        Path root = Path.of(System.getProperty("fontainerepublic.projectDir", "."))
                .toAbsolutePath().normalize();
        Path interaction = root.resolve(
                "src/main/java/com/fontainerepublic/client/CommunicatorInteraction.java");
        List<String> lines = Files.readAllLines(interaction, StandardCharsets.UTF_8);

        int itemStart = indexOf(lines, "onRightClickItem(");
        check(itemStart >= 0, "CommunicatorInteraction must define onRightClickItem");
        check(indexOf(lines, "InteractionHand.MAIN_HAND") > itemStart,
                "the air/item handler must gate on the main hand");

        int itemCancel = indexOfFrom(lines, "event.setCanceled(true)", itemStart);
        check(itemCancel >= 0,
                "the air/item handler must cancel the RightClickItem Forge event");
        int itemResult = indexOfFrom(lines, "event.setCancellationResult(", itemStart);
        check(itemResult >= 0 && itemResult < itemCancel,
                "the air/item handler must report SUCCESS before cancelling");

        int itemScreen = indexOfFrom(lines, "new FrMainScreen(", itemStart);
        check(itemScreen > itemCancel,
                "the air/item handler must open the FR main menu after cancelling");
    }

    private static int indexOf(List<String> lines, String needle) {
        return indexOfFrom(lines, needle, 0);
    }

    private static int indexOfFrom(List<String> lines, String needle, int start) {
        for (int index = start; index < lines.size(); index++) {
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
                check(!source.contains("import com.fontainerepublic.client."),
                        "server must not import client classes: " + file);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("FAIL: " + message);
        }
    }
}
