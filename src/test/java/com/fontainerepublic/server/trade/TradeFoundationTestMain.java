package com.fontainerepublic.server.trade;

import com.fontainerepublic.common.network.NetworkMessageRegistration;
import com.fontainerepublic.common.network.NetworkMessageSpec;
import com.fontainerepublic.common.network.NetworkProductionMessageTable;
import com.fontainerepublic.common.network.NetworkProtocol;
import com.fontainerepublic.common.network.RateLimitPolicy;
import com.fontainerepublic.common.network.display.TradeStateSyncPacket;
import com.fontainerepublic.common.trade.TradeAgreePacket;
import com.fontainerepublic.common.trade.TradeCancelPacket;
import com.fontainerepublic.common.trade.TradeOfferItemPacket;
import com.fontainerepublic.common.trade.TradeOfferMoneyPacket;
import com.fontainerepublic.common.trade.TradeRequestPacket;
import com.fontainerepublic.common.trade.TradeRespondPacket;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.economy.api.TradeSettlementReceipt;
import com.fontainerepublic.server.economy.model.TransactionType;
import com.fontainerepublic.server.economy.persistence.EconomyLimits;
import com.fontainerepublic.server.economy.persistence.EconomyNbtCodec;
import com.fontainerepublic.server.economy.persistence.EconomyRepository;
import com.fontainerepublic.server.economy.persistence.EconomyStore;
import com.fontainerepublic.server.economy.persistence.EconomyUnavailableException;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.trade.model.TradePhase;
import com.fontainerepublic.server.trade.model.TradeSession;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Dependency-free validation of the communicator trade subsystem
 * (FR-TRADE-001-A, intent model — Human-confirmed 2026-08-14).
 *
 * <p>Covers headlessly: the authoritative multi-leg economy settlement
 * ({@code EconomyRepository.executeTradeSettlement} — tax floor and
 * boundaries, disabled tax, fail-closed, total-supply conservation, multi-leg
 * transaction accounting), the server state-machine phase ordering and LOCKED
 * window (model contract), the protocol v6 network ledger registration
 * (message IDs 9-15 with the trade classes and C2S rate policies), the codec
 * round-trip of the non-item C2S trade packets, and their value bounds.</p>
 *
 * <p>State-machine execution, item-slot handling, the S2C snapshot item codec
 * and the {@code DefaultTradeService} request/respond/agree/execute flow touch
 * {@link net.minecraft.world.item.ItemStack} and the live
 * {@code ServerPlayer}/{@code NetworkSendService} surface. Any headless
 * reference to those classes triggers the Minecraft registry
 * static-initializers, which require the Forge launcher's bootstrap core-mod
 * (the eventbus demands a no-arg {@code NetworkEvent} constructor that only the
 * launcher patches in). Following the project's established verification
 * convention for Forge-lifecycle code, that surface is validated by the
 * dedicated-server runtime (Level 3) smoke rather than in a bare unit test.
 * This harness pins the authoritative settlement, the ledger contract, the
 * model contract and the item-free codec instead.</p>
 *
 * <p>The intent model is asserted in the parts reachable headlessly: money is
 * never pre-escrowed and items never leave the owners' inventories before the
 * atomic execution, so cancel / disconnect / shutdown simply drop the session
 * — nothing to refund.</p>
 */
public final class TradeFoundationTestMain {

    private static final UUID ALICE_SUBJECT_UUID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BOB_SUBJECT_UUID =
            UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final SubjectId ALICE_SUBJECT = SubjectId.of(ALICE_SUBJECT_UUID);
    private static final SubjectId BOB_SUBJECT = SubjectId.of(BOB_SUBJECT_UUID);

    private TradeFoundationTestMain() {
    }

    public static void main(String[] args) {
        testStateMachineModelOrdering();
        testSettlementDisabledTax();
        testSettlementTaxFloorAndBoundaries();
        testMultiLegSettlementConservesSupply();
        testSettlementValidationFailClosed();
        testProtocolLedgerRegistration();
        testCodecRoundTrip();
        testPacketValueBounds();
        System.out.println("[FR-TRADE-001] Communicator trade foundation validation passed");
    }

    /** The phase enum ordering and LOCKED window match the server contract. */
    private static void testStateMachineModelOrdering() {
        TradePhase[] phases = TradePhase.values();
        check(phases[0] == TradePhase.REQUESTED
                        && phases[1] == TradePhase.OPEN
                        && phases[2] == TradePhase.LOCKED
                        && phases[3] == TradePhase.EXECUTING
                        && phases[4] == TradePhase.COMPLETED
                        && phases[5] == TradePhase.CANCELLED,
                "server state machine orders REQUESTED -> OPEN -> LOCKED -> "
                        + "EXECUTING -> COMPLETED, plus CANCELLED");
        check(TradeSession.LOCKED_TICKS == 100L,
                "LOCKED confirmation window is 5 seconds at 20 TPS (100 ticks)");
        check(TradeStateSyncPacket.LOCKED_SECONDS == 5,
                "the S2C countdown ceiling is 5 seconds");
        check(TradeStateSyncPacket.PHASE_REQUESTED == 0
                        && TradeStateSyncPacket.PHASE_OPEN == 1
                        && TradeStateSyncPacket.PHASE_LOCKED == 2
                        && TradeStateSyncPacket.PHASE_EXECUTING == 3
                        && TradeStateSyncPacket.PHASE_COMPLETED == 4
                        && TradeStateSyncPacket.PHASE_CANCELLED == 5,
                "the S2C phase ordinals mirror the server state machine");
    }

    // ------------------------------------------------------------------
    // economy settlement (tax floor, fail closed, supply)
    // ------------------------------------------------------------------

    private static void testSettlementDisabledTax() {
        RepositoryHarness h = RepositoryHarness.withBalances(2_000L, 2_000L);
        TradeSettlementReceipt receipt = h.repository.executeTradeSettlement(
                ALICE_SUBJECT, BOB_SUBJECT, 1_200L, 800L, 0, null,
                h.repository.storeRevision(), 5_000L
        );
        check(receipt.aTax() == 0L && receipt.bTax() == 0L,
                "a zero tax rate deducts no tax");
        check(receipt.applied(), "settlement applied");
        check(h.repository.snapshot().totalSupply() == 4_000L,
                "supply is conserved with tax disabled");
        check(h.repository.requireAccount(ALICE_SUBJECT).balance() == 1_600L
                        && h.repository.requireAccount(BOB_SUBJECT).balance() == 2_400L,
                "the two legs swap the offers");
    }

    private static void testSettlementTaxFloorAndBoundaries() {
        // floor(123 * 10 / 100) = 12
        RepositoryHarness h = RepositoryHarness.withBalances(1_000L, 1_000L);
        TradeSettlementReceipt receipt = h.repository.executeTradeSettlement(
                ALICE_SUBJECT, BOB_SUBJECT, 123L, 7L, 10, null,
                h.repository.storeRevision(), 5_000L
        );
        check(receipt.aTax() == 12L,
                "floor(123 * 10 / 100) = 12, actual " + receipt.aTax());
        check(receipt.bTax() == 0L,
                "floor(7 * 10 / 100) = 0, actual " + receipt.bTax());

        // floor(2007 * 5 / 100) = 100
        RepositoryHarness h2 = RepositoryHarness.withBalances(10_000L, 10_000L);
        TradeSettlementReceipt r2 = h2.repository.executeTradeSettlement(
                ALICE_SUBJECT, BOB_SUBJECT, 2_007L, 0L, 5, null,
                h2.repository.storeRevision(), 5_000L
        );
        check(r2.aTax() == 100L,
                "floor(2007 * 5 / 100) = 100, actual " + r2.aTax());
    }

    private static void testMultiLegSettlementConservesSupply() {
        RepositoryHarness h = RepositoryHarness.withBalances(5_123L, 2_987L);
        EconomyRepository repository = h.repository;
        long before = repository.snapshot().totalSupply();
        long alphaBefore = repository.requireAccount(ALICE_SUBJECT).balance();
        long bravoBefore = repository.requireAccount(BOB_SUBJECT).balance();

        TradeSettlementReceipt receipt = repository.executeTradeSettlement(
                ALICE_SUBJECT, BOB_SUBJECT, 2_000L, 700L, 5, "memo",
                repository.storeRevision(), 5_000L
        );
        check(repository.snapshot().totalSupply() == before,
                "multi-leg settlement conserves total supply");

        long aTax = receipt.aTax();
        long bTax = receipt.bTax();
        check(repository.requireAccount(ALICE_SUBJECT).balance()
                        == alphaBefore - aTax - 2_000L + 700L,
                "A balance reflects its offer minus tax plus B's offer");
        check(repository.requireAccount(BOB_SUBJECT).balance()
                        == bravoBefore - bTax - 700L + 2_000L,
                "B balance reflects its offer minus tax plus A's offer");

        long expectedLegs = 2 + (aTax > 0 ? 1 : 0) + (bTax > 0 ? 1 : 0);
        check(receipt.transactionIds().size() == expectedLegs,
                "created transaction count matches the positive legs");
        check(repository.snapshot().transactions().values().stream()
                        .anyMatch(tx -> tx.type() == TransactionType.TAX),
                "a TAX transaction leg is recorded");
    }

    private static void testSettlementValidationFailClosed() {
        // A cannot cover offer + tax
        RepositoryHarness h = RepositoryHarness.withBalances(100L, 1_000L);
        EconomyRepository repository = h.repository;
        long before = repository.snapshot().totalSupply();
        expectThrows(EconomyUnavailableException.class,
                () -> repository.executeTradeSettlement(
                        ALICE_SUBJECT, BOB_SUBJECT, 100L, 0L, 5, null,
                        repository.storeRevision(), 5_000L
                ),
                "balance below offer + tax rejects the whole settlement");
        check(repository.snapshot().totalSupply() == before,
                "failed settlement publishes nothing (supply unchanged)");

        expectThrows(EconomyUnavailableException.class,
                () -> repository.executeTradeSettlement(
                        ALICE_SUBJECT, BOB_SUBJECT, 0L, 0L, 0, null,
                        repository.storeRevision(), 5_000L
                ),
                "a settlement that moves no offer and no tax is rejected");

        expectThrows(EconomyUnavailableException.class,
                () -> repository.executeTradeSettlement(
                        ALICE_SUBJECT, BOB_SUBJECT, -1L, 0L, 5, null,
                        repository.storeRevision(), 5_000L
                ),
                "a negative offer is rejected");
        expectThrows(EconomyUnavailableException.class,
                () -> repository.executeTradeSettlement(
                        ALICE_SUBJECT, BOB_SUBJECT, 1L, 1L, 101, null,
                        repository.storeRevision(), 5_000L
                ),
                "a tax rate above 100 is rejected");
        expectThrows(EconomyUnavailableException.class,
                () -> repository.executeTradeSettlement(
                        ALICE_SUBJECT, ALICE_SUBJECT, 10L, 10L, 5, null,
                        repository.storeRevision(), 5_000L
                ),
                "a self settlement is rejected");
    }

    // ------------------------------------------------------------------
    // protocol v6 ledger registration (IDs 9-15, C2S rate policies)
    // ------------------------------------------------------------------

    private static void testProtocolLedgerRegistration() {
        check(NetworkProtocol.VERSION.equals("7"),
                "the protocol version is v7");
        check(NetworkProductionMessageTable.EXPECTED_MESSAGE_COUNT == 23,
                "the production ledger expects 23 messages (IDs 0-22)");

        Map<Integer, NetworkMessageSpec<?>> byId = collectLedger();
        check(byId.size() == NetworkProductionMessageTable.EXPECTED_MESSAGE_COUNT,
                "registerAll exposes every expected ledger message");

        assertC2s(byId, 9, TradeRequestPacket.class);
        assertC2s(byId, 10, TradeRespondPacket.class);
        assertC2s(byId, 11, TradeOfferMoneyPacket.class);
        assertC2s(byId, 12, TradeOfferItemPacket.class);
        assertC2s(byId, 13, TradeAgreePacket.class);
        assertC2s(byId, 14, TradeCancelPacket.class);

        NetworkMessageSpec<?> snapshot = byId.get(15);
        check(snapshot != null && snapshot.messageClass() == TradeStateSyncPacket.class,
                "ID 15 registers the per-viewer S2C snapshot");
        check(snapshot.direction() == NetworkDirection.PLAY_TO_CLIENT,
                "ID 15 is a PLAY_TO_CLIENT snapshot");
        check(snapshot.rateLimitPolicy().isEmpty(),
                "S2C snapshot carries no C2S rate policy");
    }

    private static void assertC2s(
            Map<Integer, NetworkMessageSpec<?>> byId,
            int id,
            Class<?> expected
    ) {
        NetworkMessageSpec<?> spec = byId.get(id);
        check(spec != null && spec.messageClass() == expected,
                "ID " + id + " registers " + expected.getSimpleName());
        check(spec.direction() == NetworkDirection.PLAY_TO_SERVER,
                "ID " + id + " is a PLAY_TO_SERVER C2S message");
        check(spec.rateLimitPolicy().isPresent(),
                "ID " + id + " carries an explicit C2S rate policy");
        RateLimitPolicy policy = spec.rateLimitPolicy().orElseThrow();
        check(policy.capacity() >= 1 && policy.refillTokens() >= 1,
                "ID " + id + " rate policy is bounded and positive");
    }

    /** Collects the compiled ledger via the production registration path. */
    private static Map<Integer, NetworkMessageSpec<?>> collectLedger() {
        LedgerCollector collector = new LedgerCollector();
        NetworkProductionMessageTable.registerAll(collector);
        return collector.byId;
    }

    private static final class LedgerCollector implements NetworkMessageRegistration {
        private final Map<Integer, NetworkMessageSpec<?>> byId = new LinkedHashMap<>();

        @Override
        public <MSG> void register(NetworkMessageSpec<MSG> spec) {
            byId.put(spec.id(), spec);
        }
    }

    // ------------------------------------------------------------------
    // codec round trip and packet bounds (item-free packets)
    // ------------------------------------------------------------------

    private static void testCodecRoundTrip() {
        TradeRequestPacket request = roundTrip(
                new TradeRequestPacket(UUID_ALICE),
                TradeRequestPacket::encode,
                TradeRequestPacket::decode
        );
        check(request.target().equals(UUID_ALICE), "TradeRequestPacket round-trips the target");

        TradeRespondPacket respond = roundTrip(
                new TradeRespondPacket(7L, true),
                TradeRespondPacket::encode,
                TradeRespondPacket::decode
        );
        check(respond.sessionId() == 7L && respond.accept(),
                "TradeRespondPacket round-trips");

        TradeOfferMoneyPacket money = roundTrip(
                new TradeOfferMoneyPacket(7L, 1_250L),
                TradeOfferMoneyPacket::encode,
                TradeOfferMoneyPacket::decode
        );
        check(money.sessionId() == 7L && money.amount() == 1_250L,
                "TradeOfferMoneyPacket round-trips");

        TradeOfferItemPacket item = roundTrip(
                new TradeOfferItemPacket(7L, 2, 9),
                TradeOfferItemPacket::encode,
                TradeOfferItemPacket::decode
        );
        check(item.slot() == 2 && item.inventoryIndex() == 9,
                "TradeOfferItemPacket round-trips");

        TradeAgreePacket agree = roundTrip(
                new TradeAgreePacket(7L, true),
                TradeAgreePacket::encode,
                TradeAgreePacket::decode
        );
        check(agree.sessionId() == 7L && agree.agree(),
                "TradeAgreePacket round-trips");

        TradeCancelPacket cancel = roundTrip(
                new TradeCancelPacket(7L),
                TradeCancelPacket::encode,
                TradeCancelPacket::decode
        );
        check(cancel.sessionId() == 7L, "TradeCancelPacket round-trips");
    }

    private static void testPacketValueBounds() {
        expectThrows(IllegalArgumentException.class,
                () -> new TradeRequestPacket(null),
                "a null trade target is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new TradeOfferMoneyPacket(0L, 1L),
                "a non-positive session id is rejected for money offers");
        expectThrows(IllegalArgumentException.class,
                () -> new TradeOfferMoneyPacket(
                        1L, TradeOfferMoneyPacket.MAX_OFFER_AMOUNT + 1L),
                "an out-of-bound offer amount is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new TradeOfferItemPacket(1L, -1, 0),
                "a negative offer slot is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new TradeOfferItemPacket(
                        1L, 0, TradeOfferItemPacket.MAX_INVENTORY_INDEX + 1),
                "an out-of-bound inventory index is rejected");
        expectThrows(IllegalArgumentException.class,
                () -> new TradeCancelPacket(0L),
                "a non-positive cancel session id is rejected");
    }

    // ------------------------------------------------------------------
    // repository-level settlement harness
    // ------------------------------------------------------------------

    private static final class RepositoryHarness {
        private final EconomyRepository repository;

        private RepositoryHarness(long aliceBalance, long bobBalance) {
            InMemoryStore store = new InMemoryStore();
            store.data = seededStore(aliceBalance, bobBalance);
            this.repository = new EconomyRepository(
                    store, new EconomyNbtCodec(), EconomyLimits.DEFAULT
            );
        }

        static RepositoryHarness withBalances(long aliceBalance, long bobBalance) {
            return new RepositoryHarness(aliceBalance, bobBalance);
        }
    }

    private static CompoundTag seededStore(long aliceBalance, long bobBalance) {
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 1L);
        root.putLong("TreasuryBalance", 0L);
        root.putLong("NextTransactionId", 1L);
        CompoundTag accounts = new CompoundTag();
        accounts.put(ALICE_SUBJECT_UUID.toString(), accountTag(ALICE_SUBJECT_UUID, aliceBalance));
        accounts.put(BOB_SUBJECT_UUID.toString(), accountTag(BOB_SUBJECT_UUID, bobBalance));
        root.put("Accounts", accounts);
        root.put("Transactions", new CompoundTag());
        root.put("PendingNotifications", new CompoundTag());
        return root;
    }

    private static CompoundTag accountTag(UUID subjectUuid, long balance) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("AccountVersion", 1);
        tag.putUUID("SubjectId", subjectUuid);
        tag.putLong("Balance", balance);
        tag.putLong("AccountRevision", 1L);
        tag.putLong("CreatedAt", 1_000L);
        tag.putLong("LastTransactionId", 0L);
        tag.putBoolean("Frozen", false);
        return tag;
    }

    private static final class InMemoryStore implements EconomyStore {
        private CompoundTag data = new CompoundTag();

        @Override
        public CompoundTag load() {
            return data.copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            this.data = snapshot.copy();
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    EconomyRepository.MODULE_DATA_KEY,
                    1L,
                    0L,
                    ""
            );
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static final UUID UUID_ALICE =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID UUID_BOB =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static <T> T roundTrip(
            T message,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder
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
            Class<T> type, ThrowingRunnable action, String message
    ) {
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) {
                return type.cast(failure);
            }
            throw new AssertionError(
                    "Expected " + type.getName() + " but got "
                            + failure.getClass().getName() + ": " + message,
                    failure
            );
        }
        throw new AssertionError("Expected " + type.getName() + " to be thrown: " + message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
