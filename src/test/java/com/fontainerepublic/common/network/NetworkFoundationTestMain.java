package com.fontainerepublic.common.network;

import com.fontainerepublic.core.CoreManager;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.network.NetworkRuntimeModule;
import com.fontainerepublic.server.network.NetworkRuntimeResolver;
import com.fontainerepublic.server.network.NetworkRuntimeService;
import com.fontainerepublic.server.network.PacketRateLimiter;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Dependency-free, outcome-asserting FR-NET-001 validation entry point.
 */
public final class NetworkFoundationTestMain {
    private static final RateLimitPolicy TEST_POLICY =
            new RateLimitPolicy(2, 1, 100, 10);

    private NetworkFoundationTestMain() {
    }

    public static void main(String[] arguments) throws Exception {
        testRegistryValidation();
        testFreezeAndDeterministicLedger();
        testProtocolPredicates();
        testBoundedPayloadHelpers();
        testRatePolicyDirectionRules();
        testRuntimeResolverLifecycle();
        testRateLimiter();
        testProductionMessageLedger();
        testNoBusinessOrStaticStateContamination();
        System.out.println("[FR-NET-001] Network foundation validation passed");
    }

    private static void testRegistryValidation() {
        NetworkMessageRegistrar registrar = registrar(new ArrayList<>());
        registrar.register(c2s(0, MessageA.class));

        NetworkRegistrationException duplicateId = expectThrows(
                NetworkRegistrationException.class,
                () -> registrar.register(c2s(0, MessageB.class))
        );
        check(duplicateId.getMessage().contains("Duplicate message ID 0"),
                "Duplicate ID reason must identify ID 0");
        check(duplicateId.getMessage().contains(MessageB.class.getName()),
                "Duplicate ID reason must identify the rejected class");

        NetworkRegistrationException duplicateClass = expectThrows(
                NetworkRegistrationException.class,
                () -> registrar.register(c2s(1, MessageA.class))
        );
        check(duplicateClass.getMessage().contains("Duplicate message class"),
                "Duplicate class reason must be explicit");
        check(duplicateClass.getMessage().contains("ID 1"),
                "Duplicate class reason must identify the rejected ID");

        NetworkRegistrationException negative = expectThrows(
                NetworkRegistrationException.class,
                () -> registrar(new ArrayList<>()).register(c2s(-1, MessageA.class))
        );
        check(negative.getMessage().contains("Negative message ID"),
                "Negative ID reason must be explicit");

        NetworkRegistrationException missingDirection = expectThrows(
                NetworkRegistrationException.class,
                () -> registrar(new ArrayList<>()).register(
                        spec(4, MessageA.class, null, Optional.of(TEST_POLICY))
                )
        );
        check(missingDirection.getMessage().contains("Missing direction"),
                "Missing direction reason must be explicit");
        check(missingDirection.getMessage().contains("ID 4"),
                "Missing direction reason must identify the ID");
    }

    private static void testFreezeAndDeterministicLedger() {
        ArrayList<Integer> boundIds = new ArrayList<>();
        NetworkMessageRegistrar registrar = registrar(boundIds);
        registrar.register(c2s(2, MessageC.class));
        registrar.register(c2s(0, MessageA.class));
        registrar.register(c2s(1, MessageB.class));

        check(registrar.freeze() == 3, "Freeze must report three messages");
        check(registrar.isFrozen(), "Registrar must remain frozen");
        check(boundIds.equals(List.of(0, 1, 2)),
                "Binding order must be deterministic by explicit ID");
        check(
                registrar.ledger().stream().map(NetworkMessageRegistrar.LedgerEntry::id).toList()
                        .equals(List.of(0, 1, 2)),
                "Ledger order must be deterministic"
        );

        NetworkRegistrationException late = expectThrows(
                NetworkRegistrationException.class,
                () -> registrar.register(c2s(3, MessageD.class))
        );
        check(late.getMessage().contains("frozen"),
                "Late registration reason must identify frozen state");
        expectThrows(NetworkRegistrationException.class, registrar::freeze);
        check(registrar.isFrozen(), "A second freeze attempt must not reopen registration");
    }

    private static void testProtocolPredicates() {
        check(NetworkProtocol.clientAccepts("4"), "Client must accept exact protocol");
        check(!NetworkProtocol.clientAccepts("3"), "Client must reject the previous protocol");
        check(!NetworkProtocol.clientAccepts(NetworkRegistry.ABSENT.version()),
                "Client must reject an absent server channel");
        check(!NetworkProtocol.clientAccepts(NetworkRegistry.ACCEPTVANILLA),
                "Client must reject ACCEPTVANILLA");

        check(NetworkProtocol.serverAccepts("4"), "Server must accept exact protocol");
        check(NetworkProtocol.serverAccepts(NetworkRegistry.ABSENT.version()),
                "Server must accept ABSENT.version()");
        check(!NetworkProtocol.serverAccepts("3"),
                "Server must reject the previous protocol");
        check(!NetworkProtocol.serverAccepts(NetworkRegistry.ACCEPTVANILLA),
                "Server must reject ACCEPTVANILLA");
    }

    private static void testBoundedPayloadHelpers() {
        FriendlyByteBuf utf = buffer();
        utf.writeUtf("oversized");
        expectThrows(
                RuntimeException.class,
                () -> NetworkPayloadLimits.readUtf(utf, 4)
        );

        FriendlyByteBuf byteArray = buffer();
        byteArray.writeVarInt(NetworkPayloadLimits.MAX_BYTE_ARRAY_LENGTH + 1);
        NetworkPayloadException byteArrayFailure = expectThrows(
                NetworkPayloadException.class,
                () -> NetworkPayloadLimits.readByteArray(
                        byteArray,
                        NetworkPayloadLimits.MAX_BYTE_ARRAY_LENGTH
                )
        );
        check(byteArrayFailure.getMessage().contains("length exceeds"),
                "Oversize byte array must be rejected before allocation");

        FriendlyByteBuf collection = buffer();
        collection.writeVarInt(NetworkPayloadLimits.MAX_COLLECTION_ELEMENTS + 1);
        NetworkPayloadException collectionFailure = expectThrows(
                NetworkPayloadException.class,
                () -> NetworkPayloadLimits.readList(
                        collection,
                        NetworkPayloadLimits.MAX_COLLECTION_ELEMENTS,
                        FriendlyByteBuf::readBoolean
                )
        );
        check(collectionFailure.getMessage().contains("length exceeds"),
                "Oversize collection must be rejected before allocation");

        FriendlyByteBuf total = buffer();
        total.writeZero(NetworkPayloadLimits.MAX_PAYLOAD_BYTES + 1);
        expectThrows(
                NetworkPayloadException.class,
                () -> NetworkPayloadLimits.validateIncomingPayload(total)
        );
    }

    private static void testRatePolicyDirectionRules() {
        NetworkRegistrationException missingPolicy = expectThrows(
                NetworkRegistrationException.class,
                () -> registrar(new ArrayList<>()).register(
                        spec(
                                0,
                                MessageA.class,
                                NetworkDirection.PLAY_TO_SERVER,
                                Optional.empty()
                        )
                )
        );
        check(missingPolicy.getMessage().contains("requires an explicit rate policy"),
                "C2S without a policy must be rejected");

        NetworkRegistrationException s2cPolicy = expectThrows(
                NetworkRegistrationException.class,
                () -> registrar(new ArrayList<>()).register(
                        spec(
                                0,
                                MessageA.class,
                                NetworkDirection.PLAY_TO_CLIENT,
                                Optional.of(TEST_POLICY)
                        )
                )
        );
        check(s2cPolicy.getMessage().contains("cannot declare"),
                "S2C with a C2S policy must be rejected");
    }

    private static void testRuntimeResolverLifecycle() {
        ModuleRegistry registry = new ModuleRegistry();
        NetworkRuntimeModule.register(registry);
        CoreManager coreManager = new CoreManager(registry);
        NetworkRuntimeResolver resolver = new NetworkRuntimeResolver(coreManager);

        check(resolver.resolve().isEmpty(), "Resolver must reject a missing runtime");
        coreManager.closeRegistration();
        coreManager.preValidate();
        coreManager.startRuntime();

        NetworkRuntimeService first = resolver.resolve().orElseThrow(
                () -> new AssertionError("Resolver must return the active runtime")
        );
        check(first.isActive(), "Resolved runtime must be active");
        first.tryAcquire(
                UUID.fromString("4a302dee-2b94-47f7-8fd5-d3b9519faab8"),
                0,
                TEST_POLICY
        );
        check(first.bucketCount() == 1, "Active runtime must own its rate bucket");

        coreManager.stopRuntime();
        check(resolver.resolve().isEmpty(), "Resolver must reject a stopped runtime");
        check(!first.isActive(), "Stopped runtime service must be inactive");
        check(first.bucketCount() == 0, "Shutdown must clear runtime rate state");
        coreManager.closeRuntime();

        coreManager.preValidate();
        coreManager.startRuntime();
        NetworkRuntimeService second = resolver.resolve().orElseThrow(
                () -> new AssertionError("Resolver must return the restarted runtime")
        );
        check(second != first, "Resolver must not reuse a stopped runtime service");
        coreManager.stopRuntime();
        coreManager.closeRuntime();

        ModuleDefinition definition = registry.getModuleDefinition(NetworkRuntimeModule.MODULE_ID)
                .orElseThrow();
        check(definition.requiredDependencies().isEmpty(),
                "Network runtime must not require a feature dependency");
        check(definition.optionalDependencies().isEmpty(),
                "Network runtime must not have optional feature dependencies");
    }

    private static void testRateLimiter() {
        PacketRateLimiter limiter = new PacketRateLimiter();
        UUID playerId = UUID.fromString("161da0ed-99ce-4030-a119-6c1441466ad0");

        check(
                limiter.tryAcquire(playerId, 7, TEST_POLICY, 0)
                        == PacketRateLimiter.RateLimitDecision.ALLOWED,
                "First token must be allowed"
        );
        check(
                limiter.tryAcquire(playerId, 7, TEST_POLICY, 5)
                        == PacketRateLimiter.RateLimitDecision.MINIMUM_SPACING,
                "Minimum spacing must reject an early request"
        );
        check(
                limiter.tryAcquire(playerId, 7, TEST_POLICY, 10)
                        == PacketRateLimiter.RateLimitDecision.ALLOWED,
                "Second capacity token must be allowed"
        );
        check(
                limiter.tryAcquire(playerId, 7, TEST_POLICY, 20)
                        == PacketRateLimiter.RateLimitDecision.RATE_EXCEEDED,
                "Empty bucket must reject"
        );
        check(
                limiter.tryAcquire(playerId, 7, TEST_POLICY, 100)
                        == PacketRateLimiter.RateLimitDecision.ALLOWED,
                "Elapsed interval must refill deterministically"
        );
        check(limiter.size() == 1, "One UUID/message bucket must exist");
        limiter.clear();
        check(limiter.size() == 0, "Shutdown clear must remove every bucket");
    }

    private static void testProductionMessageLedger() {
        NetworkMessageRegistrar registrar = registrar(new ArrayList<>());
        NetworkProductionMessageTable.registerAll(registrar);
        int count = registrar.freeze();
        check(count == NetworkProductionMessageTable.EXPECTED_MESSAGE_COUNT,
                "Production message table must register the expected ledger count");
        check(NetworkProductionMessageTable.EXPECTED_MESSAGE_COUNT == 7,
                "Protocol v4 expects exactly seven display messages");
        check(registrar.isFrozen(), "Production message table must be frozen");

        List<NetworkMessageRegistrar.LedgerEntry> ledger = registrar.ledger();
        check(ledger.stream().map(NetworkMessageRegistrar.LedgerEntry::id).toList()
                        .equals(List.of(0, 1, 2, 3, 4, 5, 6)),
                "Ledger IDs are exactly 0..6 in ascending order");
        check(ledger.stream().map(NetworkMessageRegistrar.LedgerEntry::messageClassName)
                        .distinct().count() == 7,
                "Ledger message classes are unique");
        for (NetworkMessageRegistrar.LedgerEntry entry : ledger) {
            check(entry.direction() == NetworkDirection.PLAY_TO_CLIENT,
                    "Every display message is PLAY_TO_CLIENT: ID " + entry.id());
        }
    }

    private static void testNoBusinessOrStaticStateContamination() throws IOException {
        String projectDirectory = System.getProperty("fontainerepublic.projectDir");
        check(projectDirectory != null && !projectDirectory.isBlank(),
                "Project directory system property must be available");
        Path networkSourceRoot = Path.of(projectDirectory, "src", "main", "java",
                "com", "fontainerepublic");
        List<String> forbiddenTokens = List.of(
                "server.playerdata",
                "citizen",
                "economy",
                "government",
                "court",
                "land",
                "balance",
                "money"
        );

        for (String area : List.of("common/network", "server/network", "client/network")) {
            Path directory = networkSourceRoot.resolve(area);
            if (!Files.exists(directory)) {
                continue;
            }
            try (var files = Files.walk(directory)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    // The S2C presentation ledger (common/network/display and
                    // NetworkProductionMessageTable) is the approved
                    // FR-CLIENT-001-A surface; its own isolation contract is
                    // validated by the client presentation test.
                    if (file.toString().contains("display")
                            || file.getFileName().toString().equals(
                            "NetworkProductionMessageTable.java")) {
                        continue;
                    }
                    String source = Files.readString(file).toLowerCase(Locale.ROOT);
                    for (String forbidden : forbiddenTokens) {
                        check(!source.contains(forbidden),
                                "Network source contains forbidden business token "
                                        + forbidden + " in " + file);
                    }
                }
            }
        }

        Set<Class<?>> productionClasses = Set.of(
                NetworkBootstrap.class,
                NetworkMessageRegistrar.class,
                NetworkRuntimeModule.class,
                NetworkRuntimeResolver.class,
                NetworkRuntimeService.class,
                PacketRateLimiter.class
        );
        for (Class<?> type : productionClasses) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                check(!java.util.Map.class.isAssignableFrom(fieldType),
                        "Static Map state is forbidden: " + type.getName() + "#" + field.getName());
                check(!java.util.Collection.class.isAssignableFrom(fieldType),
                        "Static collection state is forbidden: "
                                + type.getName() + "#" + field.getName());
                check(fieldType != NetworkRuntimeService.class,
                        "Static runtime service is forbidden");
                check(fieldType != PacketRateLimiter.class,
                        "Static rate limiter is forbidden");
            }
        }

        for (var constructor : com.fontainerepublic.server.network.NetworkSendService.class
                .getConstructors()) {
            for (Class<?> parameterType : constructor.getParameterTypes()) {
                check(parameterType != SimpleChannel.class,
                        "Send facade constructor must not expose SimpleChannel");
            }
        }
        for (var method : NetworkBootstrap.class.getMethods()) {
            check(method.getReturnType() != SimpleChannel.class,
                    "Bootstrap must not expose SimpleChannel as a return type");
            for (Class<?> parameterType : method.getParameterTypes()) {
                check(parameterType != SimpleChannel.class,
                        "Bootstrap must not expose SimpleChannel as a parameter");
            }
        }
    }

    private static NetworkMessageRegistrar registrar(List<Integer> boundIds) {
        return new NetworkMessageRegistrar(
                specification -> boundIds.add(specification.id())
        );
    }

    private static <T> NetworkMessageSpec<T> c2s(int id, Class<T> messageClass) {
        return spec(
                id,
                messageClass,
                NetworkDirection.PLAY_TO_SERVER,
                Optional.of(TEST_POLICY)
        );
    }

    private static <T> NetworkMessageSpec<T> spec(
            int id,
            Class<T> messageClass,
            NetworkDirection direction,
            Optional<RateLimitPolicy> policy
    ) {
        return new NetworkMessageSpec<>(
                id,
                messageClass,
                direction,
                (message, buffer) -> {
                },
                buffer -> instantiate(messageClass),
                (message, context) -> {
                },
                policy
        );
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to instantiate test message " + type, failure);
        }
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expectedType,
            ThrowingRunnable operation
    ) {
        try {
            operation.run();
        } catch (Throwable failure) {
            if (expectedType.isInstance(failure)) {
                return expectedType.cast(failure);
            }
            throw new AssertionError(
                    "Expected " + expectedType.getName()
                            + " but received " + failure.getClass().getName(),
                    failure
            );
        }
        throw new AssertionError("Expected " + expectedType.getName() + " to be thrown");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static final class MessageA {
        public MessageA() {
        }
    }

    public static final class MessageB {
        public MessageB() {
        }
    }

    public static final class MessageC {
        public MessageC() {
        }
    }

    public static final class MessageD {
        public MessageD() {
        }
    }
}
