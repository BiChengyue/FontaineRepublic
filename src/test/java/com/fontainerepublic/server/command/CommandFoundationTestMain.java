package com.fontainerepublic.server.command;

import com.fontainerepublic.core.CoreManager;
import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.citizen.api.CitizenReceipt;
import com.fontainerepublic.server.citizen.api.CitizenService;
import com.fontainerepublic.server.citizen.model.CitizenRank;
import com.fontainerepublic.server.citizen.model.CitizenRecord;
import com.fontainerepublic.server.citizen.model.CitizenStatus;
import com.fontainerepublic.server.command.registration.CommandContributionRegistry;
import com.fontainerepublic.server.command.registration.CommandContributionSpec;
import com.fontainerepublic.server.command.registration.CommandRegistrationException;
import com.fontainerepublic.server.economy.api.EconomyPage;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.economy.api.TransferReceipt;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.login.LoginProvisioningHook;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Dependency-free, outcome-asserting FR-CMD-001 validation entry point.
 */
public final class CommandFoundationTestMain {
    private static final String PROJECT_DIR_PROPERTY = "fontainerepublic.projectDir";

    private CommandFoundationTestMain() {
    }

    public static void main(String[] arguments) throws Exception {
        testRegistryValidation();
        testFreezeAndImmutableOrder();
        testFreshTreeAndDeterministicRebuild();
        testInvalidFactoriesAndAtomicRegistration();
        testRootCollision();
        testPermissionAndRuntimeOutcomes();
        testFeedbackBounds();
        testProductionBoundaries();
        testBusinessCommandTree();
        testLoginProvisioningHook();
        System.out.println("[FR-CMD-001] Command foundation validation passed");
    }

    private static void testRegistryValidation() {
        CommandContributionRegistry registry = new CommandContributionRegistry();
        registry.register(spec("alpha"));

        for (String invalid : List.of(
                "",
                " ",
                " Alpha",
                "Alpha",
                "1alpha",
                "alpha.beta",
                "alpha beta",
                "abcdefghijklmnopqrstuvwxyzabcdefg"
        )) {
            expectThrows(
                    CommandRegistrationException.class,
                    () -> new CommandContributionRegistry().register(spec(invalid))
            );
        }
        expectThrows(
                CommandRegistrationException.class,
                () -> new CommandContributionRegistry().register(
                        new CommandContributionSpec(null, factory("alpha"))
                )
        );
        expectThrows(
                NullPointerException.class,
                () -> new CommandContributionSpec("alpha", null)
        );
        expectThrows(
                CommandRegistrationException.class,
                () -> registry.register(spec("alpha"))
        );
        for (String reserved : List.of("admin", "fr", "help")) {
            expectThrows(
                    CommandRegistrationException.class,
                    () -> new CommandContributionRegistry().register(spec(reserved))
            );
        }
    }

    private static void testFreezeAndImmutableOrder() {
        CommandContributionRegistry registry = new CommandContributionRegistry();
        registry.register(spec("zeta"));
        registry.register(spec("alpha"));
        registry.register(spec("middle"));

        List<CommandContributionSpec> snapshot = registry.freeze();
        check(registry.isFrozen(), "Registry must report FROZEN");
        check(
                snapshot.stream().map(CommandContributionSpec::topLevelLiteral).toList()
                        .equals(List.of("alpha", "middle", "zeta")),
                "Frozen specifications must use ASCII lexical order"
        );
        expectThrows(UnsupportedOperationException.class, snapshot::clear);
        check(
                registry.requireFrozenSnapshot() == snapshot,
                "Frozen snapshot must remain the same immutable definition table"
        );
        expectThrows(CommandRegistrationException.class, registry::freeze);
        expectThrows(
                CommandRegistrationException.class,
                () -> registry.register(spec("later"))
        );
        expectThrows(
                CommandRegistrationException.class,
                new CommandContributionRegistry()::requireFrozenSnapshot
        );
    }

    private static void testFreshTreeAndDeterministicRebuild() {
        ArrayList<LiteralArgumentBuilder<CommandSourceStack>> created = new ArrayList<>();
        CommandContributionRegistry registry = new CommandContributionRegistry();
        registry.register(new CommandContributionSpec(
                "zeta",
                (context, resolver) -> recordBuilder(created, "zeta")
        ));
        registry.register(new CommandContributionSpec(
                "alpha",
                (context, resolver) -> recordBuilder(created, "alpha")
        ));
        registry.freeze();

        CommandBootstrap bootstrap = bootstrap(registry, new CoreManager(new ModuleRegistry()));
        CommandDispatcher<CommandSourceStack> first = new CommandDispatcher<>();
        CommandDispatcher<CommandSourceStack> second = new CommandDispatcher<>();
        bootstrap.register(first, null, Commands.CommandSelection.ALL);
        bootstrap.register(second, null, Commands.CommandSelection.DEDICATED);

        CommandNode<CommandSourceStack> firstRoot = first.getRoot().getChild("fr");
        CommandNode<CommandSourceStack> secondRoot = second.getRoot().getChild("fr");
        check(firstRoot != null && secondRoot != null, "Both dispatchers must receive /fr");
        check(firstRoot != secondRoot, "Each dispatcher must receive a fresh root node");
        check(created.size() == 4, "Factories must execute once per spec per rebuild");
        check(created.get(0) != created.get(2), "Alpha builder must be fresh per rebuild");
        check(created.get(1) != created.get(3), "Zeta builder must be fresh per rebuild");

        List<String> contributionOrder = firstRoot.getChildren().stream()
                .map(CommandNode::getName)
                .filter(name -> name.equals("alpha") || name.equals("zeta"))
                .toList();
        check(
                contributionOrder.equals(List.of("alpha", "zeta")),
                "Contribution children must attach in frozen lexical order"
        );
        check(firstRoot.getChild("help") != null, "Built-in help child must exist once");
        CommandNode<CommandSourceStack> admin = firstRoot.getChild("admin");
        check(admin != null, "Built-in admin child must exist once");
        check(admin.getChild("status") != null, "Admin status child must exist");
        check(admin.getChild("modules") != null, "Admin modules child must exist");

        int childCount = firstRoot.getChildren().size();
        expectThrows(
                CommandRegistrationException.class,
                () -> bootstrap.register(first, null, Commands.CommandSelection.ALL)
        );
        check(
                firstRoot.getChildren().size() == childCount,
                "Rejected rebuild must not accumulate children"
        );
    }

    private static void testInvalidFactoriesAndAtomicRegistration() {
        CommandContributionRegistry nullRegistry = new CommandContributionRegistry();
        nullRegistry.register(new CommandContributionSpec(
                "alpha",
                (context, resolver) -> null
        ));
        nullRegistry.freeze();
        CommandDispatcher<CommandSourceStack> nullDispatcher = new CommandDispatcher<>();
        expectThrows(
                CommandRegistrationException.class,
                () -> bootstrap(nullRegistry, new CoreManager(new ModuleRegistry()))
                        .register(nullDispatcher, null, Commands.CommandSelection.ALL)
        );
        check(
                nullDispatcher.getRoot().getChild("fr") == null,
                "Null factory result must not publish a partial root"
        );

        CommandContributionRegistry mismatchRegistry = new CommandContributionRegistry();
        mismatchRegistry.register(new CommandContributionSpec(
                "alpha",
                factory("wrong")
        ));
        mismatchRegistry.freeze();
        CommandDispatcher<CommandSourceStack> mismatchDispatcher = new CommandDispatcher<>();
        CommandRegistrationException mismatch = expectThrows(
                CommandRegistrationException.class,
                () -> bootstrap(mismatchRegistry, new CoreManager(new ModuleRegistry()))
                        .register(mismatchDispatcher, null, Commands.CommandSelection.ALL)
        );
        check(
                mismatch.getMessage().contains("literal mismatch"),
                "Wrong factory literal reason must be explicit"
        );
        check(
                mismatchDispatcher.getRoot().getChild("fr") == null,
                "Wrong literal must not publish a partial root"
        );
    }

    private static void testRootCollision() {
        CommandContributionRegistry registry = new CommandContributionRegistry();
        registry.freeze();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(Commands.literal("fr"));
        CommandNode<CommandSourceStack> existing = dispatcher.getRoot().getChild("fr");

        CommandRegistrationException collision = expectThrows(
                CommandRegistrationException.class,
                () -> bootstrap(registry, new CoreManager(new ModuleRegistry()))
                        .register(dispatcher, null, Commands.CommandSelection.ALL)
        );
        check(
                collision.getMessage().contains("root collision"),
                "Root collision reason must be explicit"
        );
        check(
                dispatcher.getRoot().getChild("fr") == existing,
                "Collision must not merge, replace, or remove the unknown root"
        );
    }

    private static void testPermissionAndRuntimeOutcomes() throws Exception {
        ModuleRegistry modules = new ModuleRegistry();
        CoreManager coreManager = new CoreManager(modules);
        CommandContributionRegistry contributions = new CommandContributionRegistry();
        contributions.freeze();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        bootstrap(contributions, coreManager)
                .register(dispatcher, null, Commands.CommandSelection.ALL);

        CapturingSource ordinaryCapture = new CapturingSource();
        CommandSourceStack ordinary = source(ordinaryCapture, 0);
        CommandNode<CommandSourceStack> admin =
                dispatcher.getRoot().getChild("fr").getChild("admin");
        check(!admin.canUse(ordinary), "Non-OP source must fail the admin early gate");
        expectThrows(
                CommandSyntaxException.class,
                () -> dispatcher.execute("fr admin status", ordinary)
        );

        CapturingSource operatorCapture = new CapturingSource();
        CommandSourceStack operator = source(operatorCapture, Commands.LEVEL_GAMEMASTERS);
        check(admin.canUse(operator), "OP level 2 source must pass the admin early gate");
        int unavailable = dispatcher.execute("fr admin status", operator);
        check(unavailable == CommandFeedback.FAILURE, "Unavailable runtime must return 0");
        check(
                operatorCapture.lastMessage().contains("runtime is unavailable"),
                "Unavailable runtime feedback must be clear"
        );

        int rootResult = dispatcher.execute("fr", ordinary);
        check(
                ordinaryCapture.lastMessage().contains("0.1.0-alpha"),
                "Root command must report the current Mod version"
        );
        int helpResult = dispatcher.execute("fr help", ordinary);
        check(rootResult == CommandFeedback.SUCCESS, "Root command must return 1");
        check(helpResult == CommandFeedback.SUCCESS, "Help command must return 1");
        check(
                ordinaryCapture.lastMessage().contains("Available: help"),
                "Non-OP help must use Brigadier visibility and hide admin"
        );

        ModuleId moduleId = new ModuleId("test-command-runtime");
        ModuleId failedModuleId = new ModuleId("test-command-failure");
        check(modules.register(definition(moduleId)), "Test module must register");
        check(
                modules.register(failedDefinition(failedModuleId)),
                "Failing test module must register"
        );
        coreManager.closeRegistration();
        coreManager.preValidate();
        coreManager.startRuntime();
        int modulesResult = dispatcher.execute("fr admin modules", operator);
        check(modulesResult == CommandFeedback.SUCCESS, "Available modules command must return 1");
        check(
                operatorCapture.messages().stream()
                        .anyMatch(message -> message.contains(moduleId.value())),
                "Modules output must identify the active module"
        );
        check(
                operatorCapture.messages().stream()
                        .anyMatch(message -> message.contains(failedModuleId.value())
                                && message.contains("FACTORY_CREATION")),
                "Modules output must include sanitized failures without a container"
        );
        check(
                operatorCapture.messages().size() <= 4,
                "Two-module diagnostics must remain bounded"
        );
        coreManager.stopRuntime();
        coreManager.closeRuntime();

        int afterClose = dispatcher.execute("fr admin modules", operator);
        check(afterClose == CommandFeedback.FAILURE, "Closed runtime must not use stale state");
    }

    private static void testFeedbackBounds() {
        String bounded = CommandFeedback.bounded("x".repeat(500) + "\nsecret");
        check(bounded.length() == 240, "Feedback must cap messages at 240 characters");
        check(!bounded.contains("\n"), "Feedback must remain one line");
        check(bounded.endsWith("..."), "Truncated feedback must be explicit");
    }

    private static void testProductionBoundaries() throws Exception {
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path commandDirectory = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/server/command"
        );
        check(Files.isDirectory(commandDirectory), "Production command directory must exist");

        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(commandDirectory)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> source.append(read(path)).append('\n'));
        }
        String production = source.toString();
        for (String forbidden : List.of(
                "net.minecraft.client",
                "DataManager.getModuleData",
                "DataManager.putModuleData",
                "playerdata.persistence",
                "PlayerDataNbtCodec",
                "SimpleChannel",
                "CompletableFuture",
                "performPrefixedCommand"
        )) {
            check(
                    !production.contains(forbidden),
                    "Production command source must not contain forbidden dependency: "
                            + forbidden
            );
        }
        // Argument parsing is allowed only in the foundation-owned admin
        // child adapter (FrameworkAdminCommand), which hosts the approved
        // /fr admin bootstrap subject-hydro <uuid> <reason> command
        // (FR-ID-BOOTSTRAP-001-A §3), and in the approved business command
        // surfaces MoneyCommand (UUID/amount/memo/page arguments) and
        // CitizenCommand (read-only, argument-free by design,
        // FR-CIT-001-A §5). Every other command source file must remain
        // argument-free.
        for (Path file : commandFiles(commandDirectory)) {
            String fileName = file.getFileName().toString();
            if (fileName.equals("FrameworkAdminCommand.java")
                    || fileName.equals("MoneyCommand.java")
                    || fileName.equals("CitizenCommand.java")) {
                continue;
            }
            check(
                    !read(file).contains("Commands.argument("),
                    "Command argument parsing is only permitted in "
                            + "FrameworkAdminCommand/MoneyCommand/CitizenCommand: "
                            + file.getFileName()
            );
        }
        for (String forbiddenLiteral : List.of(
                "literal(\"economy\")",
                "literal(\"bank\")",
                "literal(\"top\")",
                "literal(\"land\")",
                "literal(\"court\")",
                "literal(\"election\")",
                "literal(\"save\")",
                "literal(\"reload\")"
        )) {
            check(
                    !production.contains(forbiddenLiteral),
                    "Production command tree must not contain forbidden literal "
                            + forbiddenLiteral
            );
        }
        for (String approvedLiteral : List.of(
                "literal(\"money\")",
                "literal(\"citizen\")"
        )) {
            check(
                    production.contains(approvedLiteral),
                    "Production command tree must contain approved literal "
                            + approvedLiteral
            );
        }

        for (Class<?> type : List.of(
                CommandBootstrap.class,
                CommandRuntimeResolver.class,
                FRCommand.class,
                FrameworkAdminCommand.class,
                CommandFeedback.class,
                CommandContributionRegistry.class
        )) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String fieldType = field.getType().getName();
                for (String forbiddenType : List.of(
                        "CommandDispatcher",
                        "CommandNode",
                        "MinecraftServer",
                        "ServerPlayer",
                        "RuntimeModuleContainer",
                        "Service"
                )) {
                    check(
                            !fieldType.contains(forbiddenType),
                            "Static command cache is forbidden: "
                                    + type.getSimpleName() + "." + field.getName()
                    );
                }
            }
        }
    }

    private static void testBusinessCommandTree() throws Exception {
        CommandContributionRegistry registry = new CommandContributionRegistry();
        registry.register(new CommandContributionSpec("money", MoneyCommand::create));
        registry.register(new CommandContributionSpec("citizen", CitizenCommand::create));
        registry.freeze();

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        bootstrap(registry, new CoreManager(new ModuleRegistry()))
                .register(dispatcher, null, Commands.CommandSelection.ALL);

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("fr");
        check(root != null, "Business contributions must attach under /fr");
        CommandNode<CommandSourceStack> money = root.getChild("money");
        CommandNode<CommandSourceStack> citizen = root.getChild("citizen");
        check(money != null, "Approved /fr money tree must be contributed");
        check(citizen != null, "Approved /fr citizen tree must be contributed");
        check(money.getChild("balance") != null, "/fr money balance must exist");
        check(money.getChild("pay") != null, "/fr money pay must exist");
        check(money.getChild("history") != null, "/fr money history must exist");
        check(citizen.getChild("info") != null, "/fr citizen info must exist");

        // 禁用命令守卫:top / bank / 他人余额 / rank 变更一律不注册。
        check(money.getChild("top") == null, "Forbidden /fr money top must not be registered");
        check(root.getChild("bank") == null, "Forbidden /fr bank must not be registered");
        check(money.getChild("balance").getChild("player") == null,
                "Forbidden other-player balance path must not be registered");
        check(citizen.getChild("rank") == null, "Forbidden /fr citizen rank must not be registered");
        check(citizen.getChild("set") == null, "Forbidden /fr citizen set must not be registered");

        // 服务不可用反馈(空 runtime,无玩家实体)。
        CapturingSource unavailableCapture = new CapturingSource();
        CommandSourceStack unavailableSource = source(unavailableCapture, 0);
        int balanceResult = dispatcher.execute("fr money balance", unavailableSource);
        check(balanceResult == CommandFeedback.FAILURE,
                "Unavailable economy runtime must fail balance");
        check(unavailableCapture.lastMessage().contains("Economy runtime is unavailable"),
                "Unavailable economy feedback must be explicit");
        int payResult = dispatcher.execute(
                "fr money pay 00000000-0000-0000-0000-000000000001 5",
                unavailableSource
        );
        check(payResult == CommandFeedback.FAILURE,
                "Unavailable economy runtime must fail pay");
        check(unavailableCapture.lastMessage().contains("Economy runtime is unavailable"),
                "Unavailable pay feedback must be explicit");
        int historyResult = dispatcher.execute("fr money history", unavailableSource);
        check(historyResult == CommandFeedback.FAILURE,
                "Unavailable economy runtime must fail history");
        int infoResult = dispatcher.execute("fr citizen info", unavailableSource);
        check(infoResult == CommandFeedback.FAILURE,
                "Unavailable citizen runtime must fail info");
        check(unavailableCapture.lastMessage().contains("Citizen runtime is unavailable"),
                "Unavailable citizen feedback must be explicit");

        // 参数校验:非法 UUID / 超长 memo 拒绝;非法数字 / 多余参数走 Brigadier 语法失败。
        CapturingSource invalidCapture = new CapturingSource();
        CommandSourceStack invalidSource = source(invalidCapture, 0);
        int invalidUuid = dispatcher.execute("fr money pay not-a-uuid 5", invalidSource);
        check(invalidUuid == CommandFeedback.FAILURE, "Invalid target UUID must fail");
        check(invalidCapture.lastMessage().contains("invalid target UUID"),
                "Invalid UUID feedback must be explicit");
        int invalidMemo = dispatcher.execute(
                "fr money pay 00000000-0000-0000-0000-000000000001 5 "
                        + "x".repeat(129),
                invalidSource
        );
        check(invalidMemo == CommandFeedback.FAILURE, "Oversized memo must fail");
        check(invalidCapture.lastMessage().contains("memo must be at most"),
                "Oversized memo feedback must be explicit");

        CapturingSource syntaxCapture = new CapturingSource();
        CommandSourceStack syntaxSource = source(syntaxCapture, 0);
        expectThrows(CommandSyntaxException.class,
                () -> dispatcher.execute("fr money history 0", syntaxSource));
        expectThrows(CommandSyntaxException.class,
                () -> dispatcher.execute(
                        "fr money pay 00000000-0000-0000-0000-000000000001 0",
                        syntaxSource
                ));
        expectThrows(CommandSyntaxException.class,
                () -> dispatcher.execute("fr money balance extra", syntaxSource));
        check(syntaxCapture.messages().isEmpty(),
                "Rejected syntax must not emit feedback");
    }

    private static void testLoginProvisioningHook() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

        CountingCitizen citizen = new CountingCitizen();
        CountingEconomy economy = new CountingEconomy();
        LoginProvisioningHook hook = new LoginProvisioningHook(
                () -> Optional.of(citizen),
                () -> Optional.of(economy)
        );
        hook.provision(playerId, "alpha");
        check(citizen.ensureCalls == 1, "Citizen provisioning must be invoked once");
        check(economy.ensureCalls == 1, "Economy provisioning must be invoked once");

        hook.provision(playerId, "alpha");
        check(citizen.ensureCalls == 2 && economy.ensureCalls == 2,
                "Hook must remain safely callable (idempotency owned by services)");

        FailingCitizen failing = new FailingCitizen();
        CountingEconomy resilient = new CountingEconomy();
        LoginProvisioningHook failureHook = new LoginProvisioningHook(
                () -> Optional.of(failing),
                () -> Optional.of(resilient)
        );
        failureHook.provision(playerId, "alpha");
        check(resilient.ensureCalls == 1,
                "Economy provisioning must still run when citizen provisioning fails");

        LoginProvisioningHook emptyHook = new LoginProvisioningHook(
                Optional::empty,
                Optional::empty
        );
        emptyHook.provision(playerId, "alpha");
        check(failing.ensureCalls == 1,
                "Unavailable services must be skipped without failure");
    }

    private static List<Path> commandFiles(Path commandDirectory) throws Exception {        List<Path> files = new java.util.ArrayList<>();
        try (var paths = Files.walk(commandDirectory)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(files::add);
        }
        return files;
    }

    private static CommandContributionSpec spec(String literal) {
        return new CommandContributionSpec(literal, factory(literal));
    }

    private static com.fontainerepublic.server.command.api.CommandTreeFactory factory(
            String literal
    ) {
        return (context, resolver) -> Commands.literal(literal)
                .executes(command -> CommandFeedback.SUCCESS);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> recordBuilder(
            List<LiteralArgumentBuilder<CommandSourceStack>> created,
            String literal
    ) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(literal);
        created.add(builder);
        return builder;
    }

    private static CommandBootstrap bootstrap(
            CommandContributionRegistry registry,
            CoreManager coreManager
    ) {
        return new CommandBootstrap(registry, new CommandRuntimeResolver(coreManager));
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

    private static ModuleDefinition definition(ModuleId moduleId) {
        return new ModuleDefinition(
                moduleId,
                new ModuleMetadata(
                        "Test Command Runtime",
                        "1",
                        Optional.empty(),
                        Optional.empty()
                ),
                Set.of(),
                Set.of(),
                50,
                () -> new IModule() {
                    @Override
                    public String getName() {
                        return moduleId.value();
                    }

                    @Override
                    public void init() {
                    }

                    @Override
                    public void shutdown() {
                    }
                }
        );
    }

    private static ModuleDefinition failedDefinition(ModuleId moduleId) {
        return new ModuleDefinition(
                moduleId,
                new ModuleMetadata(
                        "Failed Test Command Runtime",
                        "1",
                        Optional.empty(),
                        Optional.empty()
                ),
                Set.of(),
                Set.of(),
                50,
                () -> {
                    throw new IllegalStateException("sensitive factory detail");
                }
        );
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to inspect production source", exception);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expectedType,
            ThrowingRunnable action
    ) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (expectedType.isInstance(actual)) {
                return expectedType.cast(actual);
            }
            throw new AssertionError(
                    "Expected " + expectedType.getSimpleName()
                            + " but received " + actual.getClass().getSimpleName(),
                    actual
            );
        }
        throw new AssertionError(
                "Expected " + expectedType.getSimpleName() + " but no exception was thrown"
        );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class CountingCitizen implements CitizenService {
        private int ensureCalls;

        @Override
        public CitizenRecord ensureCitizen(UUID playerId) {
            ensureCalls++;
            return null;
        }

        @Override
        public Optional<CitizenRecord> getCitizen(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public CitizenReceipt setRank(UUID playerId, CitizenRank rank) {
            throw new UnsupportedOperationException("not used in command tests");
        }

        @Override
        public CitizenReceipt setStatus(UUID playerId, CitizenStatus status) {
            throw new UnsupportedOperationException("not used in command tests");
        }
    }

    private static final class FailingCitizen implements CitizenService {
        private int ensureCalls;

        @Override
        public CitizenRecord ensureCitizen(UUID playerId) {
            ensureCalls++;
            throw new IllegalStateException("simulated citizen provisioning failure");
        }

        @Override
        public Optional<CitizenRecord> getCitizen(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public CitizenReceipt setRank(UUID playerId, CitizenRank rank) {
            throw new UnsupportedOperationException("not used in command tests");
        }

        @Override
        public CitizenReceipt setStatus(UUID playerId, CitizenStatus status) {
            throw new UnsupportedOperationException("not used in command tests");
        }
    }

    private static final class CountingEconomy implements EconomyService {
        private int ensureCalls;

        @Override
        public EconomyAccount ensureAccountForPlayer(UUID playerId) {
            ensureCalls++;
            return null;
        }

        @Override
        public EconomyAccount ensureAccount(SubjectId subjectId) {
            return null;
        }

        @Override
        public Optional<EconomyAccount> getAccount(SubjectId subjectId) {
            return Optional.empty();
        }

        @Override
        public long getBalance(SubjectId subjectId) {
            return 0L;
        }

        @Override
        public EconomyPage<EconomyTransaction> getRecentTransactions(
                SubjectId subjectId,
                long afterId,
                int limit
        ) {
            return EconomyPage.empty(afterId);
        }

        @Override
        public TransferReceipt transfer(
                SubjectId from,
                SubjectId to,
                long amount,
                String memo
        ) {
            throw new UnsupportedOperationException("not used in command tests");
        }

        @Override
        public TransferReceipt transferByPlayer(
                UUID fromPlayerId,
                UUID toPlayerId,
                long amount,
                String memo
        ) {
            throw new UnsupportedOperationException("not used in command tests");
        }

        @Override
        public List<NotificationSummary> pendingNotifications(SubjectId subjectId) {
            return List.of();
        }

        @Override
        public void acknowledgeNotification(SubjectId subjectId, long notificationId) {
            // no-op stub
        }

        @Override
        public String formatBalance(long amount) {
            return Long.toString(amount);
        }
    }

    private static final class CapturingSource implements CommandSource {
        private final ArrayList<String> messages = new ArrayList<>();

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

        private List<String> messages() {
            return List.copyOf(messages);
        }

        private String lastMessage() {
            check(!messages.isEmpty(), "Expected at least one captured message");
            return messages.get(messages.size() - 1);
        }
    }
}
