package com.fontainerepublic.server.emergency;

import com.fontainerepublic.core.CoreManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.command.CommandBootstrap;
import com.fontainerepublic.server.command.CommandFeedback;
import com.fontainerepublic.server.command.CommandRuntimeResolver;
import com.fontainerepublic.server.command.registration.CommandContributionRegistry;
import com.fontainerepublic.server.emergency.api.ConfigureResult;
import com.fontainerepublic.server.emergency.api.ConfirmResult;
import com.fontainerepublic.server.emergency.api.EmergencyRequest;
import com.fontainerepublic.server.emergency.api.EmergencyService;
import com.fontainerepublic.server.emergency.api.PreviewResult;
import com.fontainerepublic.server.emergency.model.EmergencyActorSource;
import com.fontainerepublic.server.emergency.model.EmergencyActorType;
import com.fontainerepublic.server.emergency.model.EmergencyCategory;
import com.fontainerepublic.server.emergency.model.EmergencySourceClassification;
import com.fontainerepublic.server.emergency.model.EmergencyTargetType;
import com.fontainerepublic.server.emergency.persistence.EmergencyLimits;
import com.fontainerepublic.server.emergency.persistence.EmergencyNbtCodec;
import com.fontainerepublic.server.emergency.persistence.EmergencyRepository;
import com.fontainerepublic.server.emergency.persistence.EmergencyStore;
import com.fontainerepublic.server.emergency.service.DefaultEmergencyActionRegistry;
import com.fontainerepublic.server.emergency.service.DefaultEmergencyService;
import com.fontainerepublic.server.emergency.service.EmergencyAuthorityConfigSource;
import com.fontainerepublic.server.emergency.service.EmergencyTokenTable;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Dependency-free FR-EMG-CMD-001 validation (Level 1-2).
 *
 * <p>Covers the {@code /fr admin emergency} command adapter: request
 * construction bounds (module/action/version, target type, category, reason,
 * parameter pairs), the source-to-actor mapping matrix (LOCAL_CONSOLE ->
 * SERVER_CONSOLE, PLAYER -> HYDRO_ARCHON, everything else fail closed), the
 * command tree shape, fail-closed behavior when the emergency runtime is
 * unavailable, service-level rejection paths (non-console authority
 * lifecycle, invalid token), and the static guarantee that the plaintext
 * confirmation token is never logged and surfaced exactly once.</p>
 */
public final class EmergencyCommandFoundationTestMain {

    private static final String PROJECT_DIR_PROPERTY = "fontainerepublic.projectDir";
    private static final UUID HYDRO_UUID =
            UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID OTHER_UUID =
            UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final long START = 1_000L;

    private EmergencyCommandFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testRequestParsing();
        testSourceMapping();
        testTreeShapeAndFailClosed();
        testServiceReuseRejections();
        testTokenNeverLogged();
        System.out.println(
                "[FR-EMG-CMD-001] Emergency command foundation validation passed"
        );
    }

    // ------------------------------------------------------------------
    // 1. request construction (bounded inputs, fail closed)
    // ------------------------------------------------------------------

    private static void testRequestParsing() {
        check(EmergencyAdminCommand.parseTargetType("PLAYER_UUID")
                        == EmergencyTargetType.PLAYER_UUID,
                "targetType parses by enum name");
        check(EmergencyAdminCommand.parseTargetType("player_uuid")
                        == EmergencyTargetType.PLAYER_UUID,
                "targetType is case-insensitive");
        check(EmergencyAdminCommand.parseCategory("DISASTER_RELIEF")
                        == EmergencyCategory.DISASTER_RELIEF,
                "category parses by enum name");
        expectThrows(IllegalArgumentException.class,
                () -> EmergencyAdminCommand.parseTargetType("bogus"),
                "unknown targetType is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> EmergencyAdminCommand.parseCategory("bogus"),
                "unknown category is rejected");

        check(EmergencyAdminCommand.parseKeyValues(null).isEmpty(),
                "null parameter tail is empty");
        check(EmergencyAdminCommand.parseKeyValues("  ").isEmpty(),
                "blank parameter tail is empty");
        check(EmergencyAdminCommand.parseKeyValues("amount 10")
                        .equals(Map.of("amount", "10")),
                "key/value tail parses into a bounded map");
        check(EmergencyAdminCommand.parseKeyValues("amount 10 memo remediation")
                        .equals(Map.of("amount", "10", "memo", "remediation")),
                "multiple key/value pairs parse in order");
        expectThrows(IllegalArgumentException.class,
                () -> EmergencyAdminCommand.parseKeyValues("a b c"),
                "odd parameter tail is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> EmergencyAdminCommand.parseKeyValues("k".repeat(33) + " v"),
                "oversized parameter keys are rejected");
        expectThrows(IllegalArgumentException.class,
                () -> EmergencyAdminCommand.parseKeyValues("k " + "v".repeat(201)),
                "oversized parameter values are rejected");
        StringBuilder tooMany = new StringBuilder();
        for (int i = 0; i <= EmergencyRequest.MAX_PARAMETERS; i++) {
            tooMany.append("k").append(i).append(" v ");
        }
        expectThrows(IllegalArgumentException.class,
                () -> EmergencyAdminCommand.parseKeyValues(tooMany.toString()),
                "parameter pair count is bounded");

        // Request record invariants (reuse of the FR-EMG-001 contract).
        EmergencyRequest valid = new EmergencyRequest(
                "economy", "issue", "1", EmergencyTargetType.PLAYER_UUID,
                OTHER_UUID.toString(), EmergencyCategory.DEBUG, "remediation",
                Map.of("amount", "10"));
        check(valid.targetUuid().isPresent(), "player uuid target parses");
        expectThrows(IllegalArgumentException.class,
                () -> new EmergencyRequest("economy", "issue", "1",
                        EmergencyTargetType.PLAYER_UUID, "not-a-uuid",
                        EmergencyCategory.DEBUG, "r", Map.of()),
                "player target must be canonical uuid");
        expectThrows(IllegalArgumentException.class,
                () -> new EmergencyRequest("economy", "issue", "1",
                        EmergencyTargetType.PLAYER_UUID, OTHER_UUID.toString(),
                        EmergencyCategory.DEBUG, "   ", Map.of()),
                "reason must not be blank");
        expectThrows(IllegalArgumentException.class,
                () -> new EmergencyRequest("economy", "issue", "1",
                        EmergencyTargetType.PLAYER_UUID, OTHER_UUID.toString(),
                        EmergencyCategory.DEBUG, "r",
                        Map.of("k".repeat(40), "v")),
                "parameter keys are bounded");

        // Full command-surface parsing through a live dispatcher parse.
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        CommandSourceStack operator = operator();
        ParseResults<CommandSourceStack> parsed = dispatcher.parse(
                "fr admin emergency preview economy issue 1 PLAYER_UUID "
                        + OTHER_UUID + " DEBUG reason amount 10",
                operator);
        check(parsed.getExceptions().isEmpty(),
                "preview command must parse cleanly");
        EmergencyRequest request = EmergencyAdminCommand.parseRequest(
                parsed.getContext().build(parsed.getReader().getString())
        );
        check(request.moduleId().equals("economy")
                        && request.actionId().equals("issue")
                        && request.actionVersion().equals("1"),
                "preview request carries module/action/version");
        check(request.targetType() == EmergencyTargetType.PLAYER_UUID
                        && request.targetId().equals(OTHER_UUID.toString()),
                "preview request carries the typed target");
        check(request.category() == EmergencyCategory.DEBUG
                        && request.reason().equals("reason"),
                "preview request carries category and reason");
        check(request.parameters().equals(Map.of("amount", "10")),
                "preview request carries bounded parameters");

        ParseResults<CommandSourceStack> noKv = dispatcher.parse(
                "fr admin emergency preview economy issue 1 PLAYER_UUID "
                        + OTHER_UUID + " DEBUG reason",
                operator);
        check(noKv.getExceptions().isEmpty(), "preview without kv parses");
        check(EmergencyAdminCommand.parseRequest(
                        noKv.getContext().build(noKv.getReader().getString()))
                        .parameters().isEmpty(),
                "preview without kv carries no parameters");
    }

    // ------------------------------------------------------------------
    // 2. source-to-actor mapping matrix (FR-EMG-001-A §4 / §13)
    // ------------------------------------------------------------------

    private static void testSourceMapping() {
        EmergencyActorSource console = EmergencyAdminCommand.mapSource(
                EmergencySourceClassification.LOCAL_CONSOLE, null);
        check(console != null
                        && console.actorType() == EmergencyActorType.SERVER_CONSOLE
                        && console.uuid().isEmpty(),
                "LOCAL_CONSOLE maps to SERVER_CONSOLE");
        check(console.classification() == EmergencySourceClassification.LOCAL_CONSOLE,
                "SERVER_CONSOLE source carries LOCAL_CONSOLE classification");

        EmergencyActorSource hydro = EmergencyAdminCommand.mapSource(
                EmergencySourceClassification.PLAYER, OTHER_UUID);
        check(hydro != null
                        && hydro.actorType() == EmergencyActorType.HYDRO_ARCHON
                        && hydro.uuid().orElseThrow().equals(OTHER_UUID),
                "PLAYER maps to HYDRO_ARCHON with the authenticated UUID");

        check(EmergencyAdminCommand.mapSource(
                        EmergencySourceClassification.PLAYER, null) == null,
                "PLAYER without an entity fails closed");
        for (EmergencySourceClassification rejected : List.of(
                EmergencySourceClassification.RCON,
                EmergencySourceClassification.COMMAND_BLOCK,
                EmergencySourceClassification.MINECART_COMMAND_BLOCK,
                EmergencySourceClassification.INTEGRATED_HOST,
                EmergencySourceClassification.FUNCTION_OR_OTHER
        )) {
            check(EmergencyAdminCommand.mapSource(rejected, null) == null,
                    rejected + " fails closed at the command boundary");
        }

        // The actor source itself rejects inconsistent representations, so a
        // non-console source can never impersonate SERVER_CONSOLE.
        expectThrows(IllegalArgumentException.class,
                () -> new EmergencyActorSource(EmergencyActorType.SERVER_CONSOLE,
                        null, EmergencySourceClassification.RCON),
                "RCON cannot be represented as SERVER_CONSOLE");
        expectThrows(IllegalArgumentException.class,
                () -> new EmergencyActorSource(EmergencyActorType.SERVER_CONSOLE,
                        OTHER_UUID, EmergencySourceClassification.LOCAL_CONSOLE),
                "SERVER_CONSOLE cannot carry a UUID");
        expectThrows(IllegalArgumentException.class,
                () -> new EmergencyActorSource(EmergencyActorType.HYDRO_ARCHON,
                        null, EmergencySourceClassification.PLAYER),
                "HYDRO_ARCHON requires the actor UUID");
    }

    // ------------------------------------------------------------------
    // 3. command tree shape + fail closed when the runtime is unavailable
    // ------------------------------------------------------------------

    private static void testTreeShapeAndFailClosed() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        CommandNode<CommandSourceStack> admin =
                dispatcher.getRoot().getChild("fr").getChild("admin");
        CommandNode<CommandSourceStack> emergency = admin.getChild("emergency");
        check(emergency != null, "admin.emergency child must exist");

        for (String child : List.of(
                "preview", "confirm", "inspect", "status",
                "bootstrap", "stage", "recover"
        )) {
            check(emergency.getChild(child) != null,
                    "emergency." + child + " child must exist");
        }

        CommandNode<CommandSourceStack> preview = emergency.getChild("preview");
        CommandNode<CommandSourceStack> module = preview.getChild("module");
        CommandNode<CommandSourceStack> action = module.getChild("action");
        CommandNode<CommandSourceStack> version = action.getChild("version");
        CommandNode<CommandSourceStack> targetType = version.getChild("targetType");
        CommandNode<CommandSourceStack> targetId = targetType.getChild("targetId");
        CommandNode<CommandSourceStack> category = targetId.getChild("category");
        CommandNode<CommandSourceStack> reason = category.getChild("reason");
        check(module != null && action != null && version != null
                        && targetType != null && targetId != null
                        && category != null && reason != null,
                "preview arguments must be present in order");
        check(reason.getChild("kv") != null,
                "preview optional key/value tail must exist");

        check(emergency.getChild("confirm").getChild("token") != null,
                "confirm.token argument must exist");
        check(emergency.getChild("inspect").getChild("attemptId") != null,
                "inspect.attemptId argument must exist");
        for (String operation : List.of("bootstrap", "stage", "recover")) {
            CommandNode<CommandSourceStack> node = emergency.getChild(operation);
            check(node.getChild("uuid") != null
                            && node.getChild("uuid").getChild("reason") != null,
                    operation + ".uuid and reason arguments must exist");
        }

        // OP level-2 gate is inherited from the admin tree; non-OP fails early.
        CapturingSource ordinaryCapture = new CapturingSource();
        CommandSourceStack ordinary = source(ordinaryCapture, 0);
        check(!admin.canUse(ordinary), "Non-OP source must fail the admin gate");
        expectThrows(CommandSyntaxException.class,
                () -> dispatcher.execute("fr admin emergency status", ordinary),
                "Non-OP source is rejected before the callback");

        // No ACTIVE emergency module -> every surface fails closed with a
        // bounded feedback message (never reaches source classification).
        CapturingSource operatorCapture = new CapturingSource();
        CommandSourceStack operator = source(operatorCapture, Commands.LEVEL_GAMEMASTERS);
        check(admin.canUse(operator), "OP level 2 source must pass the gate");

        int status = dispatcher.execute("fr admin emergency status", operator);
        check(status == CommandFeedback.FAILURE
                        && operatorCapture.lastMessage().contains("unavailable"),
                "status fails closed without a service");

        String previewLine = "fr admin emergency preview economy issue 1 "
                + "PLAYER_UUID " + OTHER_UUID + " DEBUG reason";
        int previewResult = dispatcher.execute(previewLine, operator);
        check(previewResult == CommandFeedback.FAILURE
                        && operatorCapture.lastMessage().contains("unavailable"),
                "preview fails closed without a service");

        int confirm = dispatcher.execute(
                "fr admin emergency confirm " + "ab".repeat(32), operator);
        check(confirm == CommandFeedback.FAILURE
                        && operatorCapture.lastMessage().contains("unavailable"),
                "confirm fails closed without a service");

        int inspect = dispatcher.execute("fr admin emergency inspect 1", operator);
        check(inspect == CommandFeedback.FAILURE
                        && operatorCapture.lastMessage().contains("unavailable"),
                "inspect fails closed without a service");

        for (String operation : List.of("bootstrap", "stage", "recover")) {
            int authority = dispatcher.execute(
                    "fr admin emergency " + operation + " " + OTHER_UUID
                            + " rotate", operator);
            check(authority == CommandFeedback.FAILURE
                            && operatorCapture.lastMessage().contains("unavailable"),
                    operation + " fails closed without a service");
        }

        // Invalid input fails closed before any service resolution.
        int badTargetType = dispatcher.execute(
                "fr admin emergency preview economy issue 1 bogus "
                        + OTHER_UUID + " DEBUG reason", operator);
        check(badTargetType == CommandFeedback.FAILURE
                        && operatorCapture.lastMessage().contains("preview rejected")
                        && operatorCapture.lastMessage().contains("targetType"),
                "invalid targetType returns a bounded failure");

        int badCategory = dispatcher.execute(
                "fr admin emergency preview economy issue 1 PLAYER_UUID "
                        + OTHER_UUID + " bogus reason", operator);
        check(badCategory == CommandFeedback.FAILURE
                        && operatorCapture.lastMessage().contains("category"),
                "invalid category returns a bounded failure");

        int badUuid = dispatcher.execute(
                "fr admin emergency bootstrap not-a-uuid reason", operator);
        check(badUuid == CommandFeedback.FAILURE
                        && operatorCapture.lastMessage().contains("invalid target UUID"),
                "invalid bootstrap UUID returns a bounded failure");

        int emptyToken = dispatcher.execute(
                "fr admin emergency confirm \"\"", operator);
        check(emptyToken == CommandFeedback.FAILURE
                        && operatorCapture.lastMessage().contains("token is invalid"),
                "empty confirmation token is rejected before the service");
    }

    // ------------------------------------------------------------------
    // 4. service-level rejection paths (adapter delegates, service decides)
    // ------------------------------------------------------------------

    private static void testServiceReuseRejections() {
        TestHarness harness = new TestHarness();
        EmergencyService service = harness.service();

        PreviewResult unauthorized = service.preview(
                request(), EmergencyAdminCommand.mapSource(
                        EmergencySourceClassification.PLAYER, OTHER_UUID));
        check(!unauthorized.accepted()
                        && unauthorized.failureCode().equals(
                        DefaultEmergencyService.CODE_REJECTED_SOURCE),
                "unconfigured player preview is rejected by the service");

        ConfirmResult invalidToken = service.confirm(
                "ab".repeat(32), EmergencyAdminCommand.mapSource(
                        EmergencySourceClassification.LOCAL_CONSOLE, null));
        check(!invalidToken.success()
                        && invalidToken.failureCode().equals(
                        DefaultEmergencyService.CODE_REJECTED_TOKEN),
                "unknown confirmation token is rejected by the service");

        EmergencyActorSource hydro = EmergencyAdminCommand.mapSource(
                EmergencySourceClassification.PLAYER, OTHER_UUID);
        ConfigureResult nonConsoleBootstrap = service.bootstrapAuthority(
                OTHER_UUID, "first", hydro);
        check(!nonConsoleBootstrap.accepted()
                        && nonConsoleBootstrap.failureCode().equals(
                        DefaultEmergencyService.CODE_REJECTED_SOURCE),
                "non-console bootstrap is rejected by the service");
        ConfigureResult nonConsoleStage = service.stageAuthority(
                OTHER_UUID, "rotate", hydro);
        check(!nonConsoleStage.accepted()
                        && nonConsoleStage.failureCode().equals(
                        DefaultEmergencyService.CODE_REJECTED_SOURCE),
                "non-console stage is rejected by the service");
        ConfigureResult nonConsoleRecover = service.recoverAuthority(
                OTHER_UUID, "repair", hydro);
        check(!nonConsoleRecover.accepted()
                        && nonConsoleRecover.failureCode().equals(
                        DefaultEmergencyService.CODE_REJECTED_SOURCE),
                "non-console recovery is rejected by the service");

        // The real local console may bootstrap (configured lifecycle intact).
        EmergencyActorSource console = EmergencyAdminCommand.mapSource(
                EmergencySourceClassification.LOCAL_CONSOLE, null);
        ConfigureResult bootstrap = service.bootstrapAuthority(
                HYDRO_UUID, "first", console);
        check(bootstrap.accepted()
                        && bootstrap.configRevision() > 0,
                "console bootstrap is accepted and reported with its revision");

        // Bounded failure-code descriptions never leak internals.
        check(EmergencyAdminCommand.describe(
                        DefaultEmergencyService.CODE_REJECTED_TOKEN)
                        .contains("invalid, expired, or already used"),
                "rejected-token description is bounded and stable");
        check(EmergencyAdminCommand.describe(
                        DefaultEmergencyService.CODE_REJECTED_SOURCE)
                        .contains("not authorized"),
                "rejected-source description is bounded and stable");
        check(EmergencyAdminCommand.describe("SOME_UNKNOWN_CODE")
                        .contains("SOME_UNKNOWN_CODE"),
                "unknown codes fall back to the stable bounded code");
        check(EmergencyAdminCommand.describe(null).equals("the request failed"),
                "null failure codes fall back safely");
    }

    // ------------------------------------------------------------------
    // 5. token hygiene: never logged, surfaced exactly once
    // ------------------------------------------------------------------

    private static void testTokenNeverLogged() throws Exception {
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path adapter = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/server/emergency/"
                        + "EmergencyAdminCommand.java"
        );
        check(adapter.toFile().isFile(),
                "Production adapter source must exist");
        String source = read(adapter);
        check(!source.contains("net.minecraft.client"),
                "Adapter must not depend on the Minecraft client");
        check(!source.contains("DataManager"),
                "Adapter must not touch persistence directly");
        check(!source.contains("CommandDispatcher"),
                "Adapter must not capture a dispatcher");
        for (String line : source.split("\\R")) {
            check(!(line.contains("LOGGER") && line.contains("token")),
                    "The plaintext token must never be logged: " + line.trim());
        }
        check(countOccurrences(source, "result.token()") == 1,
                "The plaintext token must be surfaced into feedback exactly once");

        // The adapter must never cache a per-server Service (or any command
        // runtime object) in a static field.
        for (Field field : EmergencyAdminCommand.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String fieldType = field.getType().getName();
            for (String forbidden : List.of(
                    "Service", "CommandDispatcher", "CommandNode",
                    "MinecraftServer", "ServerPlayer", "RuntimeModuleContainer"
            )) {
                check(!fieldType.contains(forbidden),
                        "Static command/service caching is forbidden: "
                                + field.getName());
            }
        }
    }

    // ------------------------------------------------------------------
    // harness
    // ------------------------------------------------------------------

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandContributionRegistry registry = new CommandContributionRegistry();
        registry.freeze();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        new CommandBootstrap(
                registry,
                new CommandRuntimeResolver(new CoreManager(new ModuleRegistry()))
        ).register(dispatcher, null, Commands.CommandSelection.ALL);
        return dispatcher;
    }

    private static CommandSourceStack operator() {
        return source(new CapturingSource(), Commands.LEVEL_GAMEMASTERS);
    }

    private static CommandSourceStack source(CapturingSource source, int permission) {
        return new CommandSourceStack(
                source,
                Vec3.ZERO,
                Vec2.ZERO,
                null,
                permission,
                "test-source",
                Component.literal("test-source"),
                null,
                null
        );
    }

    private static EmergencyRequest request() {
        return new EmergencyRequest(
                "economy", "issue", "1", EmergencyTargetType.PLAYER_UUID,
                OTHER_UUID.toString(), EmergencyCategory.DEBUG,
                "remediation", Map.of("amount", "10")
        );
    }

    private static final class MutableClock implements LongSupplier {
        private long value;

        MutableClock(long value) {
            this.value = value;
        }

        @Override
        public long getAsLong() {
            return value;
        }
    }

    private static final class FakeAuthorityConfigSource
            implements EmergencyAuthorityConfigSource {
        private Optional<UUID> candidate = Optional.empty();

        @Override
        public Optional<byte[]> candidateUuidDigest() {
            return candidate.map(com.fontainerepublic.server.emergency.model
                    .EmergencyDigests::uuidDigest);
        }

        @Override
        public Optional<UUID> candidateUuid() {
            return candidate;
        }
    }

    private static final class SavedDataBackedTestStore implements EmergencyStore {
        private CompoundTag raw = new CompoundTag();
        private final AtomicBoolean failNext = new AtomicBoolean(false);

        @Override
        public CompoundTag load() {
            return raw.copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            if (failNext.getAndSet(false)) {
                return new DurableCommitResult(
                        DurableCommitStatus.FAILED, "emergency",
                        0L, 0L, "INJECTED");
            }
            raw = snapshot.copy();
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED, "emergency",
                    0L, 0L, "");
        }
    }

    private static final class TestHarness {
        private final SavedDataBackedTestStore store;
        private final EmergencyRepository repository;

        TestHarness() {
            store = new SavedDataBackedTestStore();
            repository = new EmergencyRepository(
                    store, new EmergencyNbtCodec(), EmergencyLimits.DEFAULT
            );
        }

        EmergencyService service() {
            long epoch = 1L;
            return new DefaultEmergencyService(
                    repository,
                    new DefaultEmergencyActionRegistry(),
                    new EmergencyTokenTable(epoch),
                    new MutableClock(START),
                    epoch,
                    new FakeAuthorityConfigSource(),
                    Map.of()
            );
        }
    }

    private static final class CapturingSource implements CommandSource {
        private final java.util.ArrayList<String> messages = new java.util.ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        private String lastMessage() {
            check(!messages.isEmpty(), "Expected at least one captured message");
            return messages.get(messages.size() - 1);
        }
    }

    private static String read(Path path) {
        try {
            return new String(
                    java.nio.file.Files.readAllBytes(path),
                    java.nio.charset.StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to inspect production source", exception
            );
        }
    }

    private static int countOccurrences(String source, String needle) {
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
            Class<T> expectedType,
            ThrowingRunnable action,
            String message
    ) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expectedType.isInstance(actual)) {
                return expectedType.cast(actual);
            }
            throw new AssertionError(
                    message + " (unexpected: " + actual.getClass().getSimpleName() + ")",
                    actual
            );
        }
        throw new AssertionError(message + " (no exception thrown)");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
