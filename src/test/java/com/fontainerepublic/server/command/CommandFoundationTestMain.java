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
import com.fontainerepublic.server.economy.api.MailPostageReceipt;
import com.fontainerepublic.server.economy.api.TradeSettlementReceipt;
import com.fontainerepublic.server.economy.api.TransferReceipt;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.login.LoginProvisioningHook;
import com.fontainerepublic.server.playerdata.api.PlayerDirectoryService;
import com.fontainerepublic.server.playerdata.model.PlayerNameResolution;
import com.fontainerepublic.server.playerdata.model.PlayerNameResolutionKind;
import com.fontainerepublic.server.registry.api.PublicRoutingResult;
import com.fontainerepublic.server.registry.api.SubjectProjection;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import com.fontainerepublic.server.registry.model.SubjectType;
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
import java.util.Map;
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
        testHelpGuide();
        testMoneyPayTargetResolution();
        testLoginProvisioningHook();
        testLandCommandNonPlayerRejection();
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
                ordinaryCapture.messages().stream()
                        .anyMatch(message -> message.contains("FontaineRepublic help")),
                "Help index must identify itself"
        );
        check(
                ordinaryCapture.messages().stream()
                        .anyMatch(message -> message.contains("money")),
                "Help index must list the money module"
        );
        check(
                ordinaryCapture.messages().stream()
                        .noneMatch(message -> message.contains("admin status")
                                || message.contains("admin modules")),
                "Help must not enumerate hidden admin commands"
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
        // (FR-ID-BOOTSTRAP-001-A §3), in the approved business command
        // surfaces MoneyCommand (UUID/amount/memo/page arguments),
        // BankCommand (official-duty target/amount/terminal/reason arguments,
        // FR-ECO-002-A), CitizenCommand (read-only, argument-free by design,
        // FR-CIT-001-A §5), in HelpCommand (/fr help <module>
        // module argument, FR-CMD-GUIDE-001), and in LandCommand
        // (/fr land inspect <x> <y> <z> and /fr land claim <x> <y> <z>
        // block-coordinate arguments, FR-LAND-CLAIM-001-A §3.4). Every other
        // command source file must remain argument-free.
        for (Path file : commandFiles(commandDirectory)) {
            String fileName = file.getFileName().toString();
            if (fileName.equals("FrameworkAdminCommand.java")
                    || fileName.equals("MoneyCommand.java")
                    || fileName.equals("BankCommand.java")
                    || fileName.equals("CitizenCommand.java")
                    || fileName.equals("HelpCommand.java")
                    || fileName.equals("LandCommand.java")
                    || fileName.equals("CommunicatorCommand.java")
                    || fileName.equals("SecureTradeCommand.java")) {
                continue;
            }
            check(
                    !read(file).contains("Commands.argument("),
                    "Command argument parsing is only permitted in "
                            + "FrameworkAdminCommand/MoneyCommand/BankCommand/"
                            + "CitizenCommand/HelpCommand/LandCommand/"
                            + "CommunicatorCommand/SecureTradeCommand: "
                            + file.getFileName()
            );
        }
        for (String forbiddenLiteral : List.of(
                "literal(\"economy\")",
                "literal(\"top\")",
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
                "literal(\"bank\")",
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
        registry.register(new CommandContributionSpec("bank", BankCommand::create));
        registry.register(new CommandContributionSpec("citizen", CitizenCommand::create));
        registry.freeze();

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        bootstrap(registry, new CoreManager(new ModuleRegistry()))
                .register(dispatcher, null, Commands.CommandSelection.ALL);

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("fr");
        check(root != null, "Business contributions must attach under /fr");
        CommandNode<CommandSourceStack> money = root.getChild("money");
        CommandNode<CommandSourceStack> bank = root.getChild("bank");
        CommandNode<CommandSourceStack> citizen = root.getChild("citizen");
        check(money != null, "Approved /fr money tree must be contributed");
        check(bank != null, "Approved /fr bank tree must be contributed");
        check(citizen != null, "Approved /fr citizen tree must be contributed");
        check(money.getChild("balance") != null, "/fr money balance must exist");
        check(money.getChild("pay") != null, "/fr money pay must exist");
        check(money.getChild("history") != null, "/fr money history must exist");
        check(citizen.getChild("info") != null, "/fr citizen info must exist");
        check(bank.getChild("balance") != null, "/fr bank balance must exist");
        check(bank.getChild("deposit") != null, "/fr bank deposit must exist");
        check(bank.getChild("withdraw") != null, "/fr bank withdraw must exist");
        check(bank.getChild("freeze") != null, "/fr bank freeze must exist");
        check(bank.getChild("unfreeze") != null, "/fr bank unfreeze must exist");

        // 禁用命令守卫:top / 他人余额 / rank 变更 / 现金表面一律不注册。
        check(money.getChild("top") == null, "Forbidden /fr money top must not be registered");
        check(money.getChild("balance").getChild("player") == null,
                "Forbidden other-player balance path must not be registered");
        check(citizen.getChild("rank") == null, "Forbidden /fr citizen rank must not be registered");
        check(citizen.getChild("set") == null, "Forbidden /fr citizen set must not be registered");
        check(bank.getChild("atm") == null, "Forbidden /fr bank atm must not be registered");
        check(bank.getChild("cash") == null, "Forbidden /fr bank cash must not be registered");

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

        // 参数校验:畸形目标 / 超长 memo 拒绝;非法数字 / 多余参数走 Brigadier 语法失败。
        CapturingSource invalidCapture = new CapturingSource();
        CommandSourceStack invalidSource = source(invalidCapture, 0);
        int invalidTarget = dispatcher.execute("fr money pay not-a-uuid 5", invalidSource);
        check(invalidTarget == CommandFeedback.FAILURE, "Malformed target must fail");
        check(invalidCapture.lastMessage().contains("target must be a canonical UUID"),
                "Malformed target feedback must be explicit");
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

    private static void testHelpGuide() throws Exception {
        CommandContributionRegistry registry = new CommandContributionRegistry();
        registry.freeze();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        bootstrap(registry, new CoreManager(new ModuleRegistry()))
                .register(dispatcher, null, Commands.CommandSelection.ALL);

        CommandNode<CommandSourceStack> help =
                dispatcher.getRoot().getChild("fr").getChild("help");
        check(help != null, "Help child must exist");
        check(help.getChild("module") != null, "Help must accept a module argument");

        // Index: fixed bounded lines covering every guide module, no admin leak.
        CapturingSource indexCapture = new CapturingSource();
        CommandSourceStack indexSource = source(indexCapture, 0);
        int indexResult = dispatcher.execute("fr help", indexSource);
        check(indexResult == CommandFeedback.SUCCESS, "Index help must return 1");
        List<String> indexMessages = indexCapture.messages();
        check(!indexMessages.isEmpty() && indexMessages.size() <= 8,
                "Index help must stay bounded");
        check(indexMessages.get(0).contains("FontaineRepublic help"),
                "Index must identify itself");
        for (String module : List.of(
                "money", "bank", "citizen", "government", "parliament", "court", "institution")) {
            check(indexMessages.stream().anyMatch(message -> message.contains(module)),
                    "Index must list " + module);
        }
        check(indexMessages.stream().noneMatch(message -> message.contains("admin status")
                        || message.contains("admin modules")),
                "Index must not enumerate hidden admin commands");

        // Per-module pages: bounded and mention their commands.
        assertModulePage(dispatcher, "money", List.of("balance", "pay", "history"));
        assertModulePage(dispatcher, "bank",
                List.of("balance", "deposit", "withdraw", "freeze", "unfreeze"));
        assertModulePage(dispatcher, "citizen", List.of("info"));
        assertModulePage(dispatcher, "government",
                List.of("ministry", "position", "appoint", "dismiss", "office"));
        assertModulePage(dispatcher, "parliament",
                List.of("proposal", "vote", "bill"));
        assertModulePage(dispatcher, "court",
                List.of("case", "evidence", "verdict", "review"));

        // Restricted module: operator boundary stated, commands not enumerated.
        CapturingSource restrictedCapture = new CapturingSource();
        CommandSourceStack restrictedSource = source(restrictedCapture, 0);
        int restrictedResult = dispatcher.execute("fr help institution", restrictedSource);
        check(restrictedResult == CommandFeedback.SUCCESS,
                "Institution help must return 1");
        String restrictedJoined = String.join(" ", restrictedCapture.messages());
        check(restrictedJoined.contains("operator"),
                "Institution help must state the operator restriction");
        check(!restrictedJoined.contains("facility")
                        && !restrictedJoined.contains("terminal"),
                "Institution help must not enumerate restricted commands");
        for (String message : restrictedCapture.messages()) {
            check(message.length() <= 240, "Institution help must be bounded");
        }

        // Unknown module: one bounded standard rejection, no enumeration.
        CapturingSource unknownCapture = new CapturingSource();
        CommandSourceStack unknownSource = source(unknownCapture, 0);
        int unknownResult = dispatcher.execute("fr help unknown", unknownSource);
        check(unknownResult == CommandFeedback.FAILURE, "Unknown module must fail");
        check(unknownCapture.messages().size() == 1,
                "Unknown module must emit exactly one bounded message");
        check(unknownCapture.lastMessage().contains("Unknown module"),
                "Unknown module feedback must be explicit");
        check(unknownCapture.lastMessage().length() <= 240,
                "Unknown module feedback must be bounded");

        // Language keys: every guide key must exist in both lang files.
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path langDirectory = projectDirectory.resolve(
                "src/main/resources/assets/fontainerepublic/lang"
        );
        String zh = Files.readString(langDirectory.resolve("zh_cn.json"));
        String en = Files.readString(langDirectory.resolve("en_us.json"));
        for (String key : HelpCommand.languageKeys()) {
            check(zh.contains(key), "zh_cn.json must define " + key);
            check(en.contains(key), "en_us.json must define " + key);
        }
    }

    private static void assertModulePage(
            CommandDispatcher<CommandSourceStack> dispatcher,
            String module,
            List<String> expectedKeywords
    ) throws Exception {
        CapturingSource capture = new CapturingSource();
        CommandSourceStack pageSource = source(capture, 0);
        int result = dispatcher.execute("fr help " + module, pageSource);
        check(result == CommandFeedback.SUCCESS, module + " help must return 1");
        List<String> messages = capture.messages();
        check(!messages.isEmpty() && messages.size() <= 10,
                module + " help must stay bounded");
        for (String message : messages) {
            check(message.length() <= 240, module + " help lines must be bounded");
        }
        for (String keyword : expectedKeywords) {
            check(messages.stream().anyMatch(message -> message.contains(keyword)),
                    module + " help must mention " + keyword);
        }
    }

    private static void testMoneyPayTargetResolution() {
        UUID actorPlayer = UUID.fromString("00000000-0000-0000-0000-0000000000ac");
        UUID targetPlayer = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
        SubjectId actorSubject = SubjectId.of(
                UUID.fromString("22222222-2222-2222-2222-222222222222")
        );
        SubjectId targetSubject = SubjectId.of(
                UUID.fromString("11111111-1111-1111-1111-111111111111")
        );
        RegistryNumber actorNumber = RegistryNumber.forTypeAndSerial(
                SubjectType.NATURAL_PERSON,
                6
        );
        RegistryNumber targetNumber = RegistryNumber.forTypeAndSerial(
                SubjectType.NATURAL_PERSON,
                7
        );
        RegistryNumber otherNumber = RegistryNumber.forTypeAndSerial(
                SubjectType.NATURAL_PERSON,
                8
        );

        StubDirectory directory = new StubDirectory(Map.of(
                "Alpha", PlayerNameResolution.uniqueCurrent(targetPlayer),
                "Ghost", PlayerNameResolution.unknown(),
                "OldName", PlayerNameResolution.retired(),
                "Shared", PlayerNameResolution.ambiguous()
        ));
        StubRegistry registry = new StubRegistry(
                Map.of(
                        targetPlayer, subject(targetSubject, targetNumber, targetPlayer),
                        actorPlayer, subject(actorSubject, actorNumber, actorPlayer)
                ),
                Map.of(
                        targetNumber, PublicRoutingResult.routable(
                                projection(targetSubject, targetNumber)
                        )
                )
        );
        RecordingEconomy economy = new RecordingEconomy();

        // 三种输入收敛到同一目标 SubjectId,且都真正到达 economy 转账边界。
        MoneyCommand.PayOutcome byUuid = MoneyCommand.executePay(
                economy, Optional.of(directory), Optional.of(registry),
                actorPlayer, targetPlayer.toString(), 5, null
        );
        MoneyCommand.PayOutcome byName = MoneyCommand.executePay(
                economy, Optional.of(directory), Optional.of(registry),
                actorPlayer, "Alpha", 5, null
        );
        MoneyCommand.PayOutcome byNumber = MoneyCommand.executePay(
                economy, Optional.of(directory), Optional.of(registry),
                actorPlayer, targetNumber.display(), 5, null
        );
        check(byUuid.receipt() != null && byUuid.receipt().to().equals(targetSubject),
                "UUID target must resolve and transfer to the target subject");
        check(byName.receipt() != null && byName.receipt().to().equals(targetSubject),
                "Exact game name must converge on the same subject");
        check(byNumber.receipt() != null && byNumber.receipt().to().equals(targetSubject),
                "Registry number must converge on the same subject");
        check(economy.transfers.size() == 3,
                "Each convergent input must reach exactly one economy transfer");
        check(economy.transfers.stream().allMatch(transfer -> transfer.to().equals(targetSubject)),
                "All three inputs must address the identical target subject");
        check(economy.transfers.stream().allMatch(transfer -> transfer.from().equals(actorSubject)),
                "Actor subject must be resolved once per transfer");

        // 未知/退役/歧义统一受限反馈,绝不触碰 economy 转账。
        int beforeFailures = economy.transfers.size();
        for (String name : List.of("Ghost", "OldName", "Shared")) {
            MoneyCommand.PayOutcome outcome = MoneyCommand.executePay(
                    economy, Optional.of(directory), Optional.of(registry),
                    actorPlayer, name, 5, null
            );
            check(outcome.receipt() == null, "Non-resolving name must not transfer");
            check(
                    outcome.failureMessage().equals(
                            "Player name cannot be resolved uniquely."
                    ),
                    "UNKNOWN/RETIRED/AMBIGUOUS must share one bounded message for '"
                            + name + "'"
            );
        }

        // 畸形输入拒绝:含非法字符、校验失败的登记号、超长名、空串。
        for (String malformed : List.of(
                "not-a-uuid",
                "1234567890",
                "12-345678-9z",
                "x".repeat(17),
                ""
        )) {
            MoneyCommand.PayOutcome outcome = MoneyCommand.executePay(
                    economy, Optional.of(directory), Optional.of(registry),
                    actorPlayer, malformed, 5, null
            );
            check(outcome.receipt() == null, "Malformed target must not transfer");
            check(
                    outcome.failureMessage().contains(
                            "target must be a canonical UUID"
                    ),
                    "Malformed input must produce the bounded syntax hint"
            );
        }

        // 登记号存在但不可路由(非 ACTIVE / 未知)与目录不可用均受限反馈。
        MoneyCommand.PayOutcome nonRoutable = MoneyCommand.executePay(
                economy, Optional.of(directory), Optional.of(registry),
                actorPlayer, otherNumber.display(), 5, null
        );
        check(nonRoutable.receipt() == null
                        && nonRoutable.failureMessage().equals(
                        "Payment rejected: target cannot be resolved."),
                "Non-routable number must fail closed without classification detail");
        MoneyCommand.PayOutcome noServices = MoneyCommand.executePay(
                economy, Optional.empty(), Optional.empty(),
                actorPlayer, targetPlayer.toString(), 5, null
        );
        check(noServices.receipt() == null
                        && noServices.failureMessage().equals(
                        "Payment rejected: target cannot be resolved."),
                "Unavailable directory/registry must fail closed with bounded feedback");

        // 发送方无 subject:目标解析成功后仍拒绝,且不转账。
        MoneyCommand.PayOutcome noActor = MoneyCommand.executePay(
                economy, Optional.of(directory), Optional.of(registry),
                UUID.fromString("00000000-0000-0000-0000-0000000000ad"),
                targetPlayer.toString(), 5, null
        );
        check(noActor.receipt() == null
                        && noActor.failureMessage().equals(
                        "Your account is not available."),
                "Actor without a subject must fail closed");

        // 所有失败路径合计零转账:解析失败绝不到达 economy 变更边界。
        check(economy.transfers.size() == beforeFailures,
                "Failed target resolution must never reach the economy mutation");
    }

    // ------------------------------------------------------------------
    // FR-LAND-CLAIM-001-FIX-01 F4: behavioral land command surface
    // ------------------------------------------------------------------

    /**
     * The no-client /fr land commands are player-only. With a non-player
     * source the real Brigadier callback must fail closed immediately with
     * the bounded "Only a player can ..." feedback and must never reach a
     * LandClaimService. Proved through the executable callback, not a source
     * scan.
     */
    private static void testLandCommandNonPlayerRejection() {
        CoreManager coreManager = new CoreManager(new ModuleRegistry());
        CommandRuntimeResolver resolver = new CommandRuntimeResolver(coreManager);

        CapturingSource inspectCapture = new CapturingSource();
        CommandSourceStack inspectSource = source(inspectCapture, 0);
        int inspectResult = LandCommand.inspect(inspectSource, resolver, 0, 60, 0);
        check(inspectResult == CommandFeedback.FAILURE,
                "land inspect on a non-player source must fail");
        check(inspectCapture.lastMessage().contains("Only a player can inspect land."),
                "land inspect non-player feedback must be explicit");

        CapturingSource claimCapture = new CapturingSource();
        CommandSourceStack claimSource = source(claimCapture, 0);
        int claimResult = LandCommand.claim(claimSource, resolver, 0, 60, 0);
        check(claimResult == CommandFeedback.FAILURE,
                "land claim on a non-player source must fail");
        check(claimCapture.lastMessage().contains("Only a player can claim land."),
                "land claim non-player feedback must be explicit");

        // An OP level does not change the player-only gate (it is satisfied
        // only by a live ServerPlayer, independent of the command permission
        // level), so even an OP source without a player entity is rejected.
        CapturingSource operatorCapture = new CapturingSource();
        CommandSourceStack operatorSource = source(operatorCapture, Commands.LEVEL_GAMEMASTERS);
        int opInspect = LandCommand.inspect(operatorSource, resolver, 0, 60, 0);
        check(opInspect == CommandFeedback.FAILURE
                        && operatorCapture.lastMessage().contains(
                        "Only a player can inspect land."),
                "an OP source without a player entity must still be rejected");

        // The command tree exposes /fr land to every source (no requires (op)
        // gate), which is exactly why the authority must live in the shared
        // LandClaimService — the command itself must not gate on OP.
        CommandContributionRegistry registry = new CommandContributionRegistry();
        registry.register(new CommandContributionSpec(
                "land", (context, cResolver) -> LandCommand.create(context, cResolver)));
        registry.freeze();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        bootstrap(registry, coreManager)
                .register(dispatcher, null, Commands.CommandSelection.ALL);
        CommandNode<CommandSourceStack> land =
                dispatcher.getRoot().getChild("fr").getChild("land");
        check(land != null, "/fr land must be contributed");
        check(land.canUse(source(new CapturingSource(), 0)),
                "/fr land must not require an OP-level permission gate "
                        + "(authority lives in the shared service)");
    }

    private static SubjectRecord subject(
            SubjectId subjectId,
            RegistryNumber number,
            UUID ownerPlayer
    ) {
        return new SubjectRecord(
                SubjectRecord.CURRENT_SCHEMA_VERSION,
                subjectId,
                number,
                SubjectType.NATURAL_PERSON,
                OwnerReference.forPlayer(ownerPlayer),
                SubjectStatus.ACTIVE,
                1L,
                1_000L,
                1_000L
        );
    }

    private static SubjectProjection projection(
            SubjectId subjectId,
            RegistryNumber number
    ) {
        return new SubjectProjection(
                subjectId,
                number,
                SubjectType.NATURAL_PERSON,
                SubjectStatus.ACTIVE
        );
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

        @Override
        public List<CitizenService.CitizenIdentity> activeCitizens(int limit) {
            return List.of();
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

        @Override
        public List<CitizenService.CitizenIdentity> activeCitizens(int limit) {
            return List.of();
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
        public TradeSettlementReceipt executeTradeSettlement(
                SubjectId a,
                SubjectId b,
                long aOffered,
                long bOffered,
                int taxRatePercent,
                String memo
        ) {
            throw new UnsupportedOperationException("not used in command tests");
        }

        @Override
        public MailPostageReceipt chargePostage(
                SubjectId payer,
                long postageFee,
                long attachmentFee,
                String memo
        ) {
            return new MailPostageReceipt(
                    1L,
                    payer,
                    postageFee,
                    attachmentFee,
                    postageFee + attachmentFee,
                    1L,
                    true
            );
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

        @Override
        public long getTreasuryBalance() {
            return 0L;
        }

        @Override
        public EconomyTransaction deposit(
                SubjectId to,
                long amount,
                String memo,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used in command tests");
        }

        @Override
        public EconomyTransaction withdraw(
                SubjectId from,
                long amount,
                String memo,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used in command tests");
        }

        @Override
        public EconomyAccount freeze(SubjectId subjectId, String memo, OnSiteContext context) {
            throw new UnsupportedOperationException("not used in command tests");
        }

        @Override
        public EconomyAccount unfreeze(SubjectId subjectId, String memo, OnSiteContext context) {
            throw new UnsupportedOperationException("not used in command tests");
        }
    }

    private static final class StubDirectory implements PlayerDirectoryService {
        private final Map<String, PlayerNameResolution> resolutions;

        private StubDirectory(Map<String, PlayerNameResolution> resolutions) {
            this.resolutions = Map.copyOf(resolutions);
        }

        @Override
        public PlayerNameResolution resolveExactGameName(String input) {
            return resolutions.getOrDefault(input, PlayerNameResolution.unknown());
        }
    }

    private static final class StubRegistry implements SubjectRegistryService {
        private final Map<UUID, SubjectRecord> byPlayer;
        private final Map<RegistryNumber, PublicRoutingResult> byNumber;

        private StubRegistry(
                Map<UUID, SubjectRecord> byPlayer,
                Map<RegistryNumber, PublicRoutingResult> byNumber
        ) {
            this.byPlayer = Map.copyOf(byPlayer);
            this.byNumber = Map.copyOf(byNumber);
        }

        @Override
        public SubjectRecord ensurePlayerSubject(UUID playerId) {
            throw new UnsupportedOperationException("not used in command tests");
        }

        @Override
        public Optional<SubjectRecord> findSubjectForPlayer(UUID playerId) {
            return Optional.ofNullable(byPlayer.get(playerId));
        }

        @Override
        public Optional<SubjectRecord> findBySubjectId(SubjectId subjectId) {
            return Optional.empty();
        }

        @Override
        public PublicRoutingResult resolveExactRegistryNumber(RegistryNumber number) {
            return byNumber.getOrDefault(number, PublicRoutingResult.unknownOrInvalid());
        }

        @Override
        public Optional<SubjectStatus> status(SubjectId subjectId) {
            return Optional.empty();
        }

        @Override
        public SubjectRecord updateStatus(SubjectId subjectId, SubjectStatus status) {
            throw new UnsupportedOperationException("not used in command tests");
        }
    }

    private static final class RecordingEconomy implements EconomyService {
        private final List<TransferReceipt> transfers = new ArrayList<>();

        @Override
        public EconomyAccount ensureAccountForPlayer(UUID playerId) {
            throw new UnsupportedOperationException("not used in command tests");
        }

        @Override
        public EconomyAccount ensureAccount(SubjectId subjectId) {
            throw new UnsupportedOperationException("not used in command tests");
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
            TransferReceipt receipt = new TransferReceipt(
                    transfers.size() + 1L,
                    2_000L,
                    from,
                    to,
                    amount,
                    memo,
                    true
            );
            transfers.add(receipt);
            return receipt;
        }

        @Override
        public TransferReceipt transferByPlayer(
                UUID fromPlayerId,
                UUID toPlayerId,
                long amount,
                String memo
        ) {
            throw new UnsupportedOperationException(
                    "command layer must transfer by resolved SubjectId"
            );
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
            throw new UnsupportedOperationException("not used in command tests");
        }

        @Override
        public MailPostageReceipt chargePostage(
                SubjectId payer,
                long postageFee,
                long attachmentFee,
                String memo
        ) {
            return new MailPostageReceipt(
                    1L,
                    payer,
                    postageFee,
                    attachmentFee,
                    postageFee + attachmentFee,
                    1L,
                    true
            );
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

        @Override
        public long getTreasuryBalance() {
            return 0L;
        }

        @Override
        public EconomyTransaction deposit(
                SubjectId to,
                long amount,
                String memo,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used in command tests");
        }

        @Override
        public EconomyTransaction withdraw(
                SubjectId from,
                long amount,
                String memo,
                OnSiteContext context
        ) {
            throw new UnsupportedOperationException("not used in command tests");
        }

        @Override
        public EconomyAccount freeze(SubjectId subjectId, String memo, OnSiteContext context) {
            throw new UnsupportedOperationException("not used in command tests");
        }

        @Override
        public EconomyAccount unfreeze(SubjectId subjectId, String memo, OnSiteContext context) {
            throw new UnsupportedOperationException("not used in command tests");
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
