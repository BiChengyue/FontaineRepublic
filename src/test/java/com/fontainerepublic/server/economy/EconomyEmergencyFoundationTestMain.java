package com.fontainerepublic.server.economy;

import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.economy.emergency.EconomyEmergencyProvider;
import com.fontainerepublic.server.economy.emergency.EconomyEmergencyProviders;
import com.fontainerepublic.server.economy.emergency.EconomyEmergencyReceiptProvider;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyEmergencyReceipt;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.economy.model.TransactionType;
import com.fontainerepublic.server.economy.persistence.EconomyLimits;
import com.fontainerepublic.server.economy.persistence.EconomyNbtCodec;
import com.fontainerepublic.server.economy.persistence.EconomyRepository;
import com.fontainerepublic.server.economy.persistence.EconomyStore;
import com.fontainerepublic.server.economy.persistence.EconomyStoreSnapshot;
import com.fontainerepublic.server.emergency.api.ConfirmResult;
import com.fontainerepublic.server.emergency.api.EmergencyActionDescriptor;
import com.fontainerepublic.server.emergency.api.EmergencyActionProvider;
import com.fontainerepublic.server.emergency.api.EmergencyActionRegistry;
import com.fontainerepublic.server.emergency.api.EmergencyReceiptProvider;
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
import com.fontainerepublic.server.registry.model.SubjectId;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Dependency-free FR-EMG-ECO-001 validation (Level 1-2).
 *
 * <p>Covers the Economy emergency catalogue: descriptor metadata, side-effect
 * free preview, the full preview/confirm path through the shared FR-EMG
 * service, single-snapshot issue/reclaim mutation with supply conservation,
 * permanent success receipts with digest-chain links, injected store failure
 * with no publication, restart recovery, bounded redacted receipt-provider
 * reads, and absence of shared-authority duplication in the provider.</p>
 */
public final class EconomyEmergencyFoundationTestMain {

    private static final UUID HYDRO_UUID =
            UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET_UUID =
            UUID.fromString("00000000-0000-0000-0000-0000000000bb");
    private static final SubjectId TARGET = SubjectId.of(TARGET_UUID);
    private static final long START = 1_000L;

    private EconomyEmergencyFoundationTestMain() {
    }

    public static void main(String[] args) {
        testDescriptorRegistration();
        testProviderResolution();
        testIssuePreviewIsSideEffectFree();
        testIssueConfirmFullPath();
        testReclaimConfirm();
        testReclaimInsufficientRejected();
        testReclaimNoAccountRejected();
        testInvalidAmountRejected();
        testOverflowRejected();
        testStaleRevisionRejectedAtConfirm();
        testStoreFailureNoPublication();
        testRestartRecovery();
        testReceiptProviderContract();
        testNoSharedAuthorityDuplication();
        System.out.println("[FR-EMG-ECO-001] Economy emergency foundation validation passed");
    }

    // ------------------------------------------------------------------
    // 1. descriptor metadata
    // ------------------------------------------------------------------

    private static void testDescriptorRegistration() {
        // Duplicate (module, action, version) is rejected before freeze.
        EmergencyActionRegistry duplicateProbe =
                new DefaultEmergencyActionRegistry();
        duplicateProbe.register(descriptor("issue"));
        expectThrows(IllegalArgumentException.class,
                () -> duplicateProbe.register(descriptor("issue")),
                "duplicate key registration is rejected before freeze");

        EmergencyActionRegistry registry = new DefaultEmergencyActionRegistry();
        registerDescriptors(registry);
        registry.freeze();
        Optional<EmergencyActionDescriptor> issue =
                registry.find("economy", "issue", "1.0.0");
        require(issue.isPresent(), "economy.issue descriptor registered");
        require(issue.get().targetType() == EmergencyTargetType.PLAYER_UUID,
                "issue targets player UUIDs");
        require(issue.get().parameterKeys().equals(Set.of("amount")),
                "issue schema is exactly the amount parameter");
        require(issue.get().offlineSafe(), "issue is offline safe");
        require(issue.get().allowedCategories().contains(EmergencyCategory.DEBUG)
                        && issue.get().allowedCategories().size() == 5,
                "issue allows the five confirmed categories");
        require("economy/emergency/issue".equals(issue.get().providerIdentity()),
                "issue provider identity is stable");

        Optional<EmergencyActionDescriptor> reclaim =
                registry.find("economy", "reclaim", "1.0.0");
        require(reclaim.isPresent(), "economy.reclaim descriptor registered");
        require(reclaim.get().parameterKeys().equals(Set.of("amount"))
                        && "economy/emergency/reclaim".equals(
                        reclaim.get().providerIdentity()),
                "reclaim schema and provider identity are stable");
    }

    // ------------------------------------------------------------------
    // 2. provider runtime resolution (never captured)
    // ------------------------------------------------------------------

    private static void testProviderResolution() {
        Harness h = harness(1L);
        EconomyEmergencyProviders.unbind();
        require(EconomyEmergencyProviders.resolveIssue().isEmpty()
                        && EconomyEmergencyProviders.resolveReclaim().isEmpty(),
                "unbound provider resolves empty (fail closed)");
        EconomyEmergencyProviders.bind(
                new EconomyEmergencyProvider(
                        h.economyRepository,
                        EconomyEmergencyProvider.PROVIDER_IDENTITY_ISSUE
                ),
                new EconomyEmergencyProvider(
                        h.economyRepository,
                        EconomyEmergencyProvider.PROVIDER_IDENTITY_RECLAIM
                )
        );
        Optional<EmergencyActionProvider> resolved =
                EconomyEmergencyProviders.resolveIssue();
        require(resolved.isPresent()
                        && "economy/emergency/issue".equals(
                        resolved.get().providerIdentity()),
                "bound provider resolves with the frozen identity");
        EconomyEmergencyProviders.unbind();
    }

    // ------------------------------------------------------------------
    // 3. preview is side-effect free
    // ------------------------------------------------------------------

    private static void testIssuePreviewIsSideEffectFree() {
        Harness h = harness(1L);
        PreviewResult result = h.service.preview(issueRequest("100"), console());
        require(result.accepted(), "issue preview accepted: " + result.failureCode());
        require(result.token() != null && !result.token().isBlank(),
                "accepted preview issues a plaintext token");
        EconomyStoreSnapshot before = h.economyRepository.snapshot();
        require(before.accounts().isEmpty(), "preview creates no account");
        require(before.emergencyReceipts().isEmpty(), "preview creates no receipt");
        require(before.totalSupply() == 0L, "preview changes no supply");
        require(before.storeRevision() == 0L, "preview changes no revision");
    }

    // ------------------------------------------------------------------
    // 4. issue full path: single snapshot + supply + receipt + notification
    // ------------------------------------------------------------------

    private static void testIssueConfirmFullPath() {
        Harness h = harness(1L);
        PreviewResult preview = h.service.preview(issueRequest("100"), console());
        require(preview.accepted(), "issue preview accepted");
        ConfirmResult confirm = h.service.confirm(preview.token(), console());
        require(confirm.success(), "issue confirm success: " + confirm.failureCode());

        EconomyStoreSnapshot snapshot = h.economyRepository.snapshot();
        Optional<EconomyAccount> account = h.economyRepository.findAccount(TARGET);
        require(account.isPresent(), "target account exists after issue");
        require(account.get().balance() == 100L, "balance credited by exactly 100");
        require(snapshot.totalSupply() == 100L, "supply increased by exactly 100");
        require(snapshot.storeRevision() == 1L, "store revision incremented once");

        List<EconomyTransaction> issueTxs = snapshot.transactions().values().stream()
                .filter(tx -> tx.type() == TransactionType.ISSUE)
                .toList();
        require(issueTxs.size() == 1 && issueTxs.get(0).amount() == 100L
                        && issueTxs.get(0).from() == null
                        && TARGET.equals(issueTxs.get(0).to()),
                "exactly one ISSUE transaction with system source");

        List<NotificationSummary> notifications =
                snapshot.pendingNotifications().get(TARGET);
        require(notifications != null && notifications.size() == 1
                        && notifications.get(0).from() == null
                        && notifications.get(0).amount() == 100L,
                "pending system notification committed in the same snapshot");

        Map<Long, EconomyEmergencyReceipt> receipts = snapshot.emergencyReceipts();
        require(receipts.size() == 1, "one permanent success receipt");
        EconomyEmergencyReceipt receipt = receipts.get(1L);
        require(receipt != null && receipt.sequence() == 1L
                        && receipt.attemptId() == preview.attemptId()
                        && receipt.transactionId() == issueTxs.get(0).transactionId()
                        && receipt.actionId().equals("issue")
                        && receipt.balanceBefore() == 0L
                        && receipt.balanceAfter() == 100L
                        && receipt.supplyBefore() == 0L
                        && receipt.supplyAfter() == 100L,
                "receipt links the shared attempt and records before/after state");
        require(EconomyEmergencyReceipt.selfDigest(
                        receipt.schemaVersion(),
                        receipt.sequence(),
                        receipt.attemptId(),
                        receipt.actionId(),
                        receipt.actionVersion(),
                        receipt.providerIdentity(),
                        receipt.transactionId(),
                        receipt.target(),
                        receipt.amount(),
                        receipt.category(),
                        receipt.reason(),
                        receipt.balanceBefore(),
                        receipt.balanceAfter(),
                        receipt.supplyBefore(),
                        receipt.supplyAfter(),
                        receipt.accountRevisionBefore(),
                        receipt.accountRevisionAfter(),
                        receipt.storeRevisionBefore(),
                        receipt.storeRevisionAfter(),
                        receipt.at(),
                        receipt.envelopeDigest(),
                        receipt.prevDigest()
                ).equals(receipt.selfDigest()),
                "receipt self-digest matches its canonical encoding");
    }

    // ------------------------------------------------------------------
    // 5. reclaim full path
    // ------------------------------------------------------------------

    private static void testReclaimConfirm() {
        Harness h = harness(1L);
        issue(h, "100");
        PreviewResult preview = h.service.preview(reclaimRequest("40"), console());
        require(preview.accepted(), "reclaim preview accepted");
        ConfirmResult confirm = h.service.confirm(preview.token(), console());
        require(confirm.success(), "reclaim confirm success: " + confirm.failureCode());

        EconomyStoreSnapshot snapshot = h.economyRepository.snapshot();
        require(h.economyRepository.findAccount(TARGET).get().balance() == 60L,
                "reclaim debits exactly 40");
        require(snapshot.totalSupply() == 60L, "supply decreases by exactly 40");
        List<EconomyTransaction> reclaimTxs = snapshot.transactions().values().stream()
                .filter(tx -> tx.type() == TransactionType.RECLAIM)
                .toList();
        require(reclaimTxs.size() == 1 && reclaimTxs.get(0).amount() == 40L
                        && TARGET.equals(reclaimTxs.get(0).from())
                        && reclaimTxs.get(0).to() == null,
                "exactly one RECLAIM transaction with system sink");
        EconomyEmergencyReceipt second = snapshot.emergencyReceipts().get(2L);
        require(second != null, "second receipt sequence is 2");
        EconomyEmergencyReceipt first = snapshot.emergencyReceipts().get(1L);
        require(second.prevDigest().equals(first.selfDigest()),
                "receipt chain links prev digest to the previous self digest");
    }

    // ------------------------------------------------------------------
    // 6. reclaim rejection paths
    // ------------------------------------------------------------------

    private static void testReclaimInsufficientRejected() {
        Harness h = harness(1L);
        issue(h, "10");
        PreviewResult preview = h.service.preview(reclaimRequest("999"), console());
        require(!preview.accepted()
                        && "INSUFFICIENT_FUNDS".equals(preview.failureCode()),
                "reclaim exceeding the balance is rejected at preview");
        require(h.economyRepository.findAccount(TARGET).get().balance() == 10L,
                "rejected reclaim changes no balance");
    }

    private static void testReclaimNoAccountRejected() {
        Harness h = harness(1L);
        PreviewResult preview = h.service.preview(reclaimRequest("10"), console());
        require(!preview.accepted() && "NO_ACCOUNT".equals(preview.failureCode()),
                "reclaim without an account is rejected at preview");
    }

    private static void testInvalidAmountRejected() {
        Harness h = harness(1L);
        PreviewResult zero = h.service.preview(
                request("issue", Map.of("amount", "0")), console());
        require(!zero.accepted() && "INVALID_REQUEST".equals(zero.failureCode()),
                "zero amount rejected");
        PreviewResult negative = h.service.preview(
                request("issue", Map.of("amount", "-5")), console());
        require(!negative.accepted(), "negative amount rejected");
        PreviewResult nonNumeric = h.service.preview(
                request("issue", Map.of("amount", "abc")), console());
        require(!nonNumeric.accepted(), "non-numeric amount rejected");
        PreviewResult missing = h.service.preview(
                request("issue", Map.of()), console());
        require(!missing.accepted(), "missing amount rejected");
    }

    private static void testOverflowRejected() {
        Harness h = harness(1L);
        issue(h, "100");
        PreviewResult preview = h.service.preview(
                request("issue", Map.of("amount", Long.toString(Long.MAX_VALUE))),
                console());
        require(!preview.accepted(), "overflowing issue is rejected");
        require(h.economyRepository.findAccount(TARGET).get().balance() == 100L,
                "rejected overflow changes no balance");
    }

    // ------------------------------------------------------------------
    // 7. stale revision rejection at confirm
    // ------------------------------------------------------------------

    private static void testStaleRevisionRejectedAtConfirm() {
        Harness h = harness(1L);
        PreviewResult preview = h.service.preview(issueRequest("100"), console());
        require(preview.accepted(), "preview accepted");
        // State changes between preview and confirm (lazy provisioning).
        h.economyRepository.ensure(TARGET, START + 50L);
        ConfirmResult confirm = h.service.confirm(preview.token(), console());
        require(!confirm.success()
                        && "REJECTED_REVISION".equals(confirm.failureCode()),
                "changed revision rejects at confirm: " + confirm.failureCode());
        require(h.economyRepository.findAccount(TARGET).get().balance() == 0L,
                "rejected confirm publishes no issue");
    }

    // ------------------------------------------------------------------
    // 8. injected store failure -> no publication
    // ------------------------------------------------------------------

    private static void testStoreFailureNoPublication() {
        Harness h = harness(1L);
        PreviewResult preview = h.service.preview(issueRequest("100"), console());
        require(preview.accepted(), "preview accepted");
        h.economyStore.failNext(true);
        ConfirmResult confirm = h.service.confirm(preview.token(), console());
        require(!confirm.success(), "store failure rejects the confirm");
        EconomyStoreSnapshot snapshot = h.economyRepository.snapshot();
        require(snapshot.accounts().isEmpty(), "no account published on failure");
        require(snapshot.emergencyReceipts().isEmpty(), "no receipt published on failure");
        require(snapshot.totalSupply() == 0L, "no supply published on failure");
        require(snapshot.storeRevision() == 0L, "no revision published on failure");
    }

    // ------------------------------------------------------------------
    // 9. restart recovery
    // ------------------------------------------------------------------

    private static void testRestartRecovery() {
        SavedDataBackedEconomyStore store = new SavedDataBackedEconomyStore();
        EconomyRepository first = new EconomyRepository(
                store, new EconomyNbtCodec(), EconomyLimits.DEFAULT);
        Harness h = harness(1L, first, store);
        issue(h, "50");

        EconomyRepository restarted = new EconomyRepository(
                store, new EconomyNbtCodec(), EconomyLimits.DEFAULT);
        require(restarted.findAccount(TARGET).get().balance() == 50L,
                "balance survives restart");
        require(restarted.snapshot().totalSupply() == 50L, "supply survives restart");
        require(restarted.highestReceiptSequence() == 1L,
                "receipt watermark survives restart");
        require(restarted.snapshot().emergencyReceipts().size() == 1,
                "permanent receipt survives restart");
    }

    // ------------------------------------------------------------------
    // 10. receipt provider contract
    // ------------------------------------------------------------------

    private static void testReceiptProviderContract() {
        Harness h = harness(1L);
        issue(h, "100");
        issue(h, "50");

        EmergencyReceiptProvider.EmergencyReceiptWatermarkResult watermark =
                h.receiptProvider.watermark();
        require(watermark.highestSequence() == 2L, "watermark reflects both receipts");
        require(!watermark.segmentDigest().isEmpty(), "watermark carries a segment digest");

        EmergencyReceiptProvider.EmergencyReceiptPage page =
                h.receiptProvider.readPage(0L, 1);
        require(page.envelopes().size() == 1
                        && page.envelopes().get(0).sequence() == 1L
                        && page.hasMore(),
                "page is bounded and ascending with hasMore");
        require(!h.receiptProvider.readPage(0L, 500).hasMore(),
                "full read reports no more after the tail");

        EconomyEmergencyReceipt first =
                h.economyRepository.snapshot().emergencyReceipts().get(1L);
        Optional<Boolean> verified = h.receiptProvider.verify(
                first.actionId(), first.envelopeDigest());
        require(verified.isPresent() && verified.get(),
                "verify succeeds for a stored receipt");
        require(h.receiptProvider.verify("issue", "deadbeef").isEmpty(),
                "verify returns empty for an unknown digest");
    }

    // ------------------------------------------------------------------
    // 11. no shared-authority duplication
    // ------------------------------------------------------------------

    private static void testNoSharedAuthorityDuplication() {
        Harness h = harness(1L);
        // Player actor not configured as the Hydro Archon: rejected by the
        // shared service, not by the provider.
        PreviewResult player = h.service.preview(
                issueRequest("100"),
                new EmergencyActorSource(
                        EmergencyActorType.HYDRO_ARCHON,
                        TARGET_UUID,
                        EmergencySourceClassification.PLAYER
                )
        );
        require(!player.accepted() && "REJECTED_SOURCE".equals(player.failureCode()),
                "unconfigured player actor rejected by the shared service");
        require(h.economyRepository.snapshot().accounts().isEmpty(),
                "rejected source publishes no economy state");

        // RCON / command-block / function / integrated sources cannot even be
        // represented as an authorized actor: the shared model requires the
        // strict LOCAL_CONSOLE classification for SERVER_CONSOLE, and a
        // HYDRO_ARCHON source must carry the configured UUID. The provider
        // itself has no actor/console logic (FR-EMG owns the boundary).
        require(EmergencySourceClassification.RCON
                        != EmergencySourceClassification.LOCAL_CONSOLE,
                "RCON classification is distinct from the local console");
    }

    // ------------------------------------------------------------------
    // harness
    // ------------------------------------------------------------------

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
            return candidate.map(
                    uuid -> com.fontainerepublic.server.emergency.model.EmergencyDigests
                            .uuidDigest(uuid)
            );
        }

        @Override
        public Optional<UUID> candidateUuid() {
            return candidate;
        }
    }

    private static final class SavedDataBackedEconomyStore implements EconomyStore {
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
                        DurableCommitStatus.FAILED, "economy", 0L, 0L, "INJECTED");
            }
            raw = snapshot.copy();
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED, "economy", 0L, 0L, "");
        }

        void failNext(boolean value) {
            failNext.set(value);
        }
    }

    private static final class SavedDataBackedEmergencyStore implements EmergencyStore {
        private CompoundTag raw = new CompoundTag();

        @Override
        public CompoundTag load() {
            return raw.copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            raw = snapshot.copy();
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED, "emergency", 0L, 0L, "");
        }
    }

    private static final class Harness {
        final SavedDataBackedEconomyStore economyStore;
        final EconomyRepository economyRepository;
        final EmergencyService service;
        final EmergencyReceiptProvider receiptProvider;

        Harness(long epoch) {
            this(epoch, null, null);
        }

        Harness(
                long epoch,
                EconomyRepository existingEconomy,
                SavedDataBackedEconomyStore existingStore
        ) {
            economyStore = existingStore == null
                    ? new SavedDataBackedEconomyStore()
                    : existingStore;
            economyRepository = existingEconomy == null
                    ? new EconomyRepository(
                            economyStore, new EconomyNbtCodec(), EconomyLimits.DEFAULT)
                    : existingEconomy;

            EmergencyRepository emergencyRepository = new EmergencyRepository(
                    new SavedDataBackedEmergencyStore(),
                    new EmergencyNbtCodec(),
                    EmergencyLimits.DEFAULT
            );
            DefaultEmergencyActionRegistry registry = new DefaultEmergencyActionRegistry();
            registerDescriptors(registry);
            registry.freeze();
            EconomyEmergencyProviders.bind(
                    new EconomyEmergencyProvider(
                            economyRepository,
                            EconomyEmergencyProvider.PROVIDER_IDENTITY_ISSUE
                    ),
                    new EconomyEmergencyProvider(
                            economyRepository,
                            EconomyEmergencyProvider.PROVIDER_IDENTITY_RECLAIM
                    )
            );
            long positiveEpoch = Math.max(epoch, 1L);
            EmergencyTokenTable table = new EmergencyTokenTable(positiveEpoch);
            DefaultEmergencyService emergencyService = new DefaultEmergencyService(
                    emergencyRepository,
                    registry,
                    table,
                    new MutableClock(START),
                    positiveEpoch,
                    new FakeAuthorityConfigSource(),
                    Map.of()
            );
            emergencyService.bootstrapAuthority(
                    HYDRO_UUID, "first", console());
            service = emergencyService;
            receiptProvider = new EconomyEmergencyReceiptProvider(economyRepository);
            emergencyService.bindReceiptProvider(receiptProvider);
        }
    }

    private static void registerDescriptors(EmergencyActionRegistry registry) {
        registry.register(descriptor("issue"));
        registry.register(descriptor("reclaim"));
    }

    private static EmergencyActionDescriptor descriptor(String action) {
        return new EmergencyActionDescriptor(
                "economy",
                action,
                "1.0.0",
                EmergencyTargetType.PLAYER_UUID,
                Set.of(
                        EmergencyCategory.DEBUG,
                        EmergencyCategory.CORRECTION,
                        EmergencyCategory.COMPENSATION,
                        EmergencyCategory.DISASTER_RELIEF,
                        EmergencyCategory.EMERGENCY_RESPONSE
                ),
                Set.of("amount"),
                "economy/emergency/" + action,
                "1",
                true,
                "issue".equals(action)
                        ? EconomyEmergencyProviders::resolveIssue
                        : EconomyEmergencyProviders::resolveReclaim
        );
    }

    private static EmergencyRequest issueRequest(String amount) {
        return request("issue", Map.of("amount", amount));
    }

    private static EmergencyRequest reclaimRequest(String amount) {
        return request("reclaim", Map.of("amount", amount));
    }

    private static EmergencyRequest request(
            String action, Map<String, String> parameters
    ) {
        return new EmergencyRequest(
                "economy",
                action,
                "1.0.0",
                EmergencyTargetType.PLAYER_UUID,
                TARGET_UUID.toString(),
                EmergencyCategory.DEBUG,
                "test reason",
                parameters
        );
    }

    private static void issue(Harness h, String amount) {
        PreviewResult preview = h.service.preview(issueRequest(amount), console());
        require(preview.accepted(), "issue preview accepted");
        ConfirmResult confirm = h.service.confirm(preview.token(), console());
        require(confirm.success(), "issue confirm success: " + confirm.failureCode());
    }

    private static EmergencyActorSource console() {
        return new EmergencyActorSource(
                EmergencyActorType.SERVER_CONSOLE,
                null,
                EmergencySourceClassification.LOCAL_CONSOLE
        );
    }

    private static Harness harness(long epoch) {
        return new Harness(epoch);
    }

    private static Harness harness(
            long epoch,
            EconomyRepository existingEconomy,
            SavedDataBackedEconomyStore existingStore
    ) {
        return new Harness(epoch, existingEconomy, existingStore);
    }

    private static void require(boolean condition, String message) {
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
