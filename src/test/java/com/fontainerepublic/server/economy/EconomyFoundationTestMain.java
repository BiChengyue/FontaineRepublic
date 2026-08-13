package com.fontainerepublic.server.economy;

import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.core.ModSavedData;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.server.economy.api.CurrencyPresentation;
import com.fontainerepublic.server.economy.api.EconomyPage;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.economy.api.SubjectDirectory;
import com.fontainerepublic.server.economy.api.TransferReceipt;
import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.model.EconomyTransaction;
import com.fontainerepublic.server.economy.model.NotificationSummary;
import com.fontainerepublic.server.economy.model.TransactionType;
import com.fontainerepublic.server.economy.persistence.EconomyLimits;
import com.fontainerepublic.server.economy.persistence.EconomyNbtCodec;
import com.fontainerepublic.server.economy.persistence.EconomyNbtException;
import com.fontainerepublic.server.economy.persistence.EconomyRepository;
import com.fontainerepublic.server.economy.persistence.EconomyStore;
import com.fontainerepublic.server.economy.persistence.EconomyStoreSnapshot;
import com.fontainerepublic.server.economy.persistence.EconomyUnavailableException;
import com.fontainerepublic.server.economy.service.DefaultEconomyService;
import com.fontainerepublic.server.audit.AuditModule;
import com.fontainerepublic.server.institutionaccess.InstitutionAccessModule;
import com.fontainerepublic.server.institutionaccess.api.FacilityReceipt;
import com.fontainerepublic.server.institutionaccess.api.FacilityRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.api.InstitutionAccessService;
import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.api.ValidationResult;
import com.fontainerepublic.server.institutionaccess.api.ZoneReceipt;
import com.fontainerepublic.server.institutionaccess.api.ZoneRegistrationRequest;
import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.Facility;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.InstitutionType;
import com.fontainerepublic.server.institutionaccess.model.WorkflowKind;
import com.fontainerepublic.server.institutionaccess.model.Zone;
import com.fontainerepublic.server.institutionaccess.model.ZoneId;
import com.fontainerepublic.server.institutionaccess.model.ZoneKind;
import com.fontainerepublic.server.institutionaccess.model.ZoneRegion;
import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.registry.api.PlayerPresence;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import com.fontainerepublic.server.registry.model.SubjectType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * Dependency-free validation entry point for FR-ECO-001 (economy module).
 * Exercises the FR-ECO-001-C §15.1 acceptance matrix with an injectable store
 * and SavedData-backed restart simulation: zero-balance lazy idempotent
 * provisioning, UUID online/offline target convergence, atomic transfer
 * snapshot, memo normalization, cooldown, stale-revision rejection, injected
 * save-failure with no publication, receipt/privacy and bounded pagination,
 * offline notification lifecycle, total-supply conservation and load
 * reconciliation, 100K-buffer pruning without authority loss, configurable
 * currency presentation without numeric mutation, forbidden-surface absence,
 * strict deterministic codec, and restart recovery.
 */
public final class EconomyFoundationTestMain {

    private static final String PROJECT_DIR_PROPERTY = "fontainerepublic.projectDir";

    /** Default test limits: no transfer cooldown so tests stay focused. */
    private static final EconomyLimits TEST_LIMITS = new EconomyLimits(
            10_000,
            100_000,
            128,
            EconomyLimits.DEFAULT_MAX_BALANCE,
            100,
            50,
            8 * 1024 * 1024,
            0L
    );

    private static final UUID ALPHA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CHARLIE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ALPHA_SUBJECT =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_SUBJECT =
            UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID CHARLIE_SUBJECT =
            UUID.fromString("10000000-0000-0000-0000-000000000003");

    private EconomyFoundationTestMain() {
    }

    public static void main(String[] args) throws Exception {
        testZeroBalanceLazyEnsureIdempotent();
        testEnsurePreconditionFailures();
        testTransferAtomicSingleSnapshot();
        testTransferValidationFailClosed();
        testStaleRevisionRejected();
        testCooldown();
        testMemoNormalizationAndBounds();
        testReceiptAndPaginationPrivacy();
        testOfflineNotificationLifecycle();
        testSupplyConservationAndLoadReconciliation();
        testPruningWithoutAuthorityLoss();
        testCurrencyPresentationDoesNotMutateValues();
        testForbiddenSurfacesAbsent();
        testStrictDeterministicCodec();
        testCorruptSnapshotFailClosed();
        testRestartPersistence();
        testCapacityFailClosed();
        testStoreFailureAtomicity();
        testSubjectNotActiveFailClosed();
        testBankOfficialDuties();
        testModuleContract();
        testNoEnumerationApi();
        System.out.println("[FR-ECO-001] Economy foundation validation passed");
    }

    // ------------------------------------------------------------------
    // acceptance: zero-balance lazy idempotent provisioning (§15.1)
    // ------------------------------------------------------------------

    private static void testZeroBalanceLazyEnsureIdempotent() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(1_000);
        EconomyRepository repository = repository(store);
        EconomyService service = service(repository, clock, presence(ALPHA_ID), directory(ALPHA_ID));

        EconomyAccount first = service.ensureAccountForPlayer(ALPHA_ID);
        require(first.balance() == 0L, "new account is created with exactly zero balance");
        require(first.accountRevision() == 1L, "new account starts at revision 1");
        require(first.createdAt() == 1_000L, "createdAt is the server clock");
        require(first.lastTransactionId() == 0L, "new account has no last transaction");
        require(first.subjectId().equals(SubjectId.of(ALPHA_SUBJECT)),
                "account is keyed by the FR-ID subject");
        require(repository.snapshot().storeRevision() == 1L,
                "provisioning commits exactly one store revision");
        require(repository.snapshot().totalSupply() == 0L,
                "zero-balance provisioning creates no money");

        EconomyAccount second = service.ensureAccountForPlayer(ALPHA_ID);
        require(second.equals(first), "ensure is idempotent");
        require(store.commitCount() == 1, "idempotent ensure commits nothing");
        require(repository.size() == 1, "one subject never yields a second account");

        // Already-resolved subject entry point is also idempotent.
        EconomyAccount direct = service.ensureAccount(SubjectId.of(ALPHA_SUBJECT));
        require(direct.equals(first), "ensureAccount(subjectId) converges on the same account");
        require(store.commitCount() == 1, "ensureAccount(subjectId) is a no-op when present");
    }

    private static void testEnsurePreconditionFailures() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(2_000);
        EconomyRepository repository = repository(store);

        // player-data unavailable
        FakePlayerPresence unavailable = new FakePlayerPresence();
        unavailable.setAvailable(false);
        EconomyService offlineDataService = service(
                repository, clock, unavailable, directory(ALPHA_ID)
        );
        EconomyUnavailableException dataFailure = expectThrows(
                EconomyUnavailableException.class,
                () -> offlineDataService.ensureAccountForPlayer(ALPHA_ID),
                "ensure fails closed when player-data is unavailable"
        );
        require(dataFailure.failureCode().equals(
                        EconomyUnavailableException.CODE_PLAYER_DATA_UNAVAILABLE),
                "player-data unavailability carries the stable code");

        // player not provisioned
        EconomyService noRecordService = service(
                repository, clock, presence(), directory(ALPHA_ID)
        );
        EconomyUnavailableException notProvisioned = expectThrows(
                EconomyUnavailableException.class,
                () -> noRecordService.ensureAccountForPlayer(ALPHA_ID),
                "ensure fails closed without a PlayerData record"
        );
        require(notProvisioned.failureCode().equals(
                        EconomyUnavailableException.CODE_PLAYER_NOT_PROVISIONED),
                "missing player record carries the stable code");

        // subject registry unavailable
        FakeSubjectDirectory offlineDirectory = directory(ALPHA_ID);
        offlineDirectory.setAvailable(false);
        EconomyService offlineSubjectService = service(
                repository, clock, presence(ALPHA_ID), offlineDirectory
        );
        EconomyUnavailableException subjectFailure = expectThrows(
                EconomyUnavailableException.class,
                () -> offlineSubjectService.ensureAccountForPlayer(ALPHA_ID),
                "ensure fails closed when the subject registry is unavailable"
        );
        require(subjectFailure.failureCode().equals(
                        EconomyUnavailableException.CODE_SUBJECT_REGISTRY_UNAVAILABLE),
                "subject registry unavailability carries the stable code");

        require(repository.size() == 0, "failed preconditions publish no account");
        require(repository.snapshot().storeRevision() == 0L,
                "failed preconditions advance no revision");
    }

    // ------------------------------------------------------------------
    // acceptance: atomic transfer snapshot (§15.1)
    // ------------------------------------------------------------------

    private static void testTransferAtomicSingleSnapshot() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(storeWithBalances(1_000L, 200L, 0L));
        MutableClock clock = new MutableClock(3_000);
        EconomyRepository repository = repository(store);
        EconomyService service = service(
                repository, clock, presence(ALPHA_ID, BRAVO_ID), directory(ALPHA_ID, BRAVO_ID)
        );
        SubjectId alpha = SubjectId.of(ALPHA_SUBJECT);
        SubjectId bravo = SubjectId.of(BRAVO_SUBJECT);

        int commitsBefore = store.commitCount();
        TransferReceipt receipt = service.transfer(alpha, bravo, 150L, "rent");
        require(receipt.transactionId() == 1L, "first transaction id is 1");
        require(receipt.applied(), "successful transfer is applied");
        require(receipt.amount() == 150L, "receipt carries the amount");
        require(receipt.timestamp() == 3_000L, "receipt carries the server clock");
        require(receipt.memo().equals("rent"), "receipt carries the normalized memo");

        EconomyAccount afterAlpha = service.getAccount(alpha).orElseThrow();
        EconomyAccount afterBravo = service.getAccount(bravo).orElseThrow();
        require(afterAlpha.balance() == 850L, "source is debited");
        require(afterBravo.balance() == 350L, "target is credited");
        require(afterAlpha.accountRevision() == 2L, "source revision +1 exactly once");
        require(afterBravo.accountRevision() == 2L, "target revision +1 exactly once");
        require(afterAlpha.lastTransactionId() == 1L, "source tracks the transaction");
        require(repository.snapshot().storeRevision() == 2L, "store revision +1 exactly once");
        require(store.commitCount() == commitsBefore + 1,
                "debit+credit+transaction+notification+revisions commit as ONE snapshot");
        require(repository.snapshot().nextTransactionId() == 2L, "next id advances");

        List<EconomyTransaction> alphaHistory =
                repository.participantTransactions(alpha, 0L, 10);
        require(alphaHistory.size() == 1, "participant projection shows the transfer");
        require(alphaHistory.get(0).type() == TransactionType.TRANSFER,
                "the only ordinary transaction kind is TRANSFER");
        require(alphaHistory.get(0).from().equals(alpha)
                        && alphaHistory.get(0).to().equals(bravo),
                "transaction participants are the SubjectIds");

        List<NotificationSummary> bravoNotifications = service.pendingNotifications(bravo);
        require(bravoNotifications.size() == 1, "recipient has one pending notification");
        require(bravoNotifications.get(0).amount() == 150L,
                "notification carries the received amount");
        require(bravoNotifications.get(0).from().equals(alpha),
                "notification identifies the sender subject");
        require(service.pendingNotifications(alpha).isEmpty(),
                "sender receives no incoming notification");
    }

    // ------------------------------------------------------------------
    // acceptance: transfer validation, lazy target, fail-closed (§15.1)
    // ------------------------------------------------------------------

    private static void testTransferValidationFailClosed() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        // charlie has no account (balance -1 = absent): the lazy-target case
        store.putRaw(storeWithBalances(1_000L, 0L, -1L));
        MutableClock clock = new MutableClock(4_000);
        EconomyRepository repository = repository(store);
        EconomyService service = service(
                repository, clock, presence(ALPHA_ID, BRAVO_ID, CHARLIE_ID),
                directory(ALPHA_ID, BRAVO_ID, CHARLIE_ID)
        );
        SubjectId alpha = SubjectId.of(ALPHA_SUBJECT);
        SubjectId bravo = SubjectId.of(BRAVO_SUBJECT);
        SubjectId charlie = SubjectId.of(CHARLIE_SUBJECT);

        // no source account
        EconomyUnavailableException noAccount = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(charlie, alpha, 10L, null),
                "transfer from a subject without an account is rejected"
        );
        require(noAccount.failureCode().equals(EconomyUnavailableException.CODE_NO_ACCOUNT),
                "no-account failure carries the stable code");

        // amount <= 0 / above the maximum
        for (long invalid : List.of(0L, -5L)) {
            EconomyUnavailableException amount = expectThrows(
                    EconomyUnavailableException.class,
                    () -> service.transfer(alpha, bravo, invalid, null),
                    "non-positive amounts are rejected"
            );
            require(amount.failureCode().equals(
                            EconomyUnavailableException.CODE_AMOUNT_INVALID),
                    "invalid amount carries the stable code");
        }
        EconomyUnavailableException tooLarge = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(alpha, bravo, EconomyLimits.DEFAULT.maxBalance() + 1, null),
                "amount above the maximum balance is rejected"
        );
        require(tooLarge.failureCode().equals(EconomyUnavailableException.CODE_AMOUNT_INVALID),
                "oversized amount carries the stable code");

        // self-transfer (subject and UUID routes)
        EconomyUnavailableException self = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(alpha, alpha, 10L, null),
                "self-transfer is rejected"
        );
        require(self.failureCode().equals(EconomyUnavailableException.CODE_SELF_TRANSFER),
                "self-transfer carries the stable code");
        EconomyUnavailableException selfUuid = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transferByPlayer(ALPHA_ID, ALPHA_ID, 10L, null),
                "self-transfer by UUID is rejected"
        );
        require(selfUuid.failureCode().equals(EconomyUnavailableException.CODE_SELF_TRANSFER),
                "self-transfer by UUID carries the stable code");

        // insufficient funds
        EconomyUnavailableException poor = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(alpha, bravo, 1_001L, null),
                "insufficient balance is rejected"
        );
        require(poor.failureCode().equals(EconomyUnavailableException.CODE_INSUFFICIENT_FUNDS),
                "insufficient funds carries the stable code");

        // overflow beyond the configured maximum balance
        EconomyLimits tight = new EconomyLimits(
                10_000, 100_000, 128, 500L, 100, 50, 8 * 1024 * 1024, 0L
        );
        SavedDataBackedTestStore tightStore = new SavedDataBackedTestStore();
        tightStore.putRaw(storeWithBalances(1_000L, 0L, 0L));
        EconomyService tightService = service(
                repository(tightStore, tight), clock,
                presence(ALPHA_ID, BRAVO_ID), directory(ALPHA_ID, BRAVO_ID)
        );
        EconomyUnavailableException overflow = expectThrows(
                EconomyUnavailableException.class,
                () -> tightService.transfer(alpha, bravo, 600L, null),
                "a credit above the maximum balance is rejected"
        );
        require(overflow.failureCode().equals(EconomyUnavailableException.CODE_OVERFLOW),
                "overflow carries the stable code");

        // failed validations publish nothing
        require(repository.snapshot().totalSupply() == 1_000L,
                "rejected operations conserve the total supply");
        require(repository.snapshot().storeRevision() == 1L,
                "rejected operations advance no store revision");

        // lazy zero-balance target creation in the same snapshot
        TransferReceipt lazy = service.transfer(alpha, charlie, 40L, null);
        require(lazy.transactionId() == 1L, "lazy-target transfer commits");
        EconomyAccount lazyTarget = service.getAccount(charlie).orElseThrow();
        require(lazyTarget.balance() == 40L, "target account was created and credited at zero");
        require(lazyTarget.createdAt() == 4_000L, "lazy account createdAt is server-assigned");
        require(repository.snapshot().totalSupply() == 1_000L,
                "lazy provisioning conserves the total supply");
    }

    // ------------------------------------------------------------------
    // acceptance: stale revision rejection (§15.1)
    // ------------------------------------------------------------------

    private static void testStaleRevisionRejected() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(storeWithBalances(1_000L, 0L, 0L));
        MutableClock clock = new MutableClock(5_000);
        EconomyRepository repository = repository(store);
        EconomyService service = service(
                repository, clock, presence(ALPHA_ID, BRAVO_ID), directory(ALPHA_ID, BRAVO_ID)
        );
        SubjectId alpha = SubjectId.of(ALPHA_SUBJECT);
        SubjectId bravo = SubjectId.of(BRAVO_SUBJECT);

        service.transfer(alpha, bravo, 100L, null); // alpha rev 2, store rev 2

        // A caller holding the pre-transfer revisions is rejected at the final
        // mutation boundary.
        EconomyUnavailableException stale = expectThrows(
                EconomyUnavailableException.class,
                () -> repository.transfer(
                        alpha, bravo, 10L, null,
                        1L, // expectedFromAccountRevision (stale)
                        1L, // expectedStoreRevision (stale)
                        5_000L
                ),
                "a stale expected revision rejects the operation"
        );
        require(stale.failureCode().equals(EconomyUnavailableException.CODE_STALE_REVISION),
                "stale revision carries the stable code");
        require(repository.requireAccount(alpha).balance() == 900L,
                "rejected stale transfer leaves the source unchanged");
        require(repository.requireAccount(bravo).balance() == 100L,
                "rejected stale transfer leaves the target unchanged");
        require(repository.snapshot().storeRevision() == 2L,
                "rejected stale transfer advances no revision");
        require(repository.snapshot().nextTransactionId() == 2L,
                "rejected stale transfer fabricates no transaction id");
    }

    // ------------------------------------------------------------------
    // acceptance: cooldown (§15.1)
    // ------------------------------------------------------------------

    private static void testCooldown() {
        EconomyLimits cooldownLimits = new EconomyLimits(
                10_000, 100_000, 128, EconomyLimits.DEFAULT_MAX_BALANCE, 100, 50,
                8 * 1024 * 1024, 1_000L
        );
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(storeWithBalances(1_000L, 0L, 0L));
        MutableClock clock = new MutableClock(6_000);
        EconomyRepository repository = repository(store, cooldownLimits);
        EconomyService service = service(
                repository, clock, presence(ALPHA_ID, BRAVO_ID), directory(ALPHA_ID, BRAVO_ID),
                cooldownLimits
        );
        SubjectId alpha = SubjectId.of(ALPHA_SUBJECT);
        SubjectId bravo = SubjectId.of(BRAVO_SUBJECT);

        service.transfer(alpha, bravo, 10L, null);
        EconomyUnavailableException cooldown = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(alpha, bravo, 20L, null),
                "a transfer within the server-owned cooldown is rejected"
        );
        require(cooldown.failureCode().equals(EconomyUnavailableException.CODE_COOLDOWN),
                "cooldown carries the stable code");
        require(repository.requireAccount(bravo).balance() == 10L,
                "cooldown rejection publishes no balance change");

        clock.set(6_999L);
        expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(alpha, bravo, 20L, null),
                "cooldown is still active before the interval elapses"
        );
        clock.set(7_000L);
        service.transfer(alpha, bravo, 20L, null);
        require(repository.requireAccount(bravo).balance() == 30L,
                "a transfer after the cooldown elapses succeeds");
        require(repository.snapshot().totalSupply() == 1_000L,
                "cooldown never alters supply authority");
    }

    // ------------------------------------------------------------------
    // acceptance: memo normalization and bounds (§15.1)
    // ------------------------------------------------------------------

    private static void testMemoNormalizationAndBounds() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(storeWithBalances(10_000L, 0L, 0L));
        MutableClock clock = new MutableClock(8_000);
        EconomyRepository repository = repository(store);
        EconomyService service = service(
                repository, clock, presence(ALPHA_ID, BRAVO_ID), directory(ALPHA_ID, BRAVO_ID)
        );
        SubjectId alpha = SubjectId.of(ALPHA_SUBJECT);
        SubjectId bravo = SubjectId.of(BRAVO_SUBJECT);

        // absence is distinct: null and blank both store null
        TransferReceipt noMemo = service.transfer(alpha, bravo, 1L, null);
        require(noMemo.memo() == null, "null memo stays absent");
        TransferReceipt blankMemo = service.transfer(alpha, bravo, 1L, "   ");
        require(blankMemo.memo() == null, "blank memo normalizes to absent");

        // trimming
        TransferReceipt padded = service.transfer(alpha, bravo, 1L, "  hello  ");
        require(padded.memo().equals("hello"), "memo is trimmed and normalized");

        // control / formatting abuse rejected
        for (String abusive : List.of("a\nb", "a\tb", "a\u0000b", "a\u00A7b")) {
            EconomyUnavailableException rejected = expectThrows(
                    EconomyUnavailableException.class,
                    () -> service.transfer(alpha, bravo, 1L, abusive),
                    "control/formatting memo abuse is rejected"
            );
            require(rejected.failureCode().equals(EconomyUnavailableException.CODE_MEMO_INVALID),
                    "memo abuse carries the stable code");
        }

        // exact bound accepted, one over rejected
        String atBound = "m".repeat(128);
        TransferReceipt bounded = service.transfer(alpha, bravo, 1L, atBound);
        require(bounded.memo().length() == 128, "a memo at the bound is accepted");
        EconomyUnavailableException tooLong = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(alpha, bravo, 1L, "m".repeat(129)),
                "a memo over the bound is rejected"
        );
        require(tooLong.failureCode().equals(EconomyUnavailableException.CODE_MEMO_INVALID),
                "oversized memo carries the stable code");

        // the stored transaction projects the normalized value
        List<EconomyTransaction> history = service.getRecentTransactions(
                alpha, 0L, 10).items();
        require(history.stream().anyMatch(tx -> "hello".equals(tx.memo())),
                "the transaction stores the normalized memo");
        require(history.stream().noneMatch(tx -> tx.memo() != null
                        && tx.memo().contains("  ")),
                "no transaction stores an un-normalized memo");
    }

    // ------------------------------------------------------------------
    // acceptance: receipt privacy and bounded pagination (§15.1)
    // ------------------------------------------------------------------

    private static void testReceiptAndPaginationPrivacy() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(storeWithBalances(1_000L, 1_000L, 1_000L));
        MutableClock clock = new MutableClock(9_000);
        EconomyRepository repository = repository(store);
        EconomyService service = service(
                repository, clock,
                presence(ALPHA_ID, BRAVO_ID, CHARLIE_ID),
                directory(ALPHA_ID, BRAVO_ID, CHARLIE_ID)
        );
        SubjectId alpha = SubjectId.of(ALPHA_SUBJECT);
        SubjectId bravo = SubjectId.of(BRAVO_SUBJECT);
        SubjectId charlie = SubjectId.of(CHARLIE_SUBJECT);

        TransferReceipt first = service.transfer(alpha, bravo, 100L, "a");
        TransferReceipt second = service.transfer(bravo, charlie, 50L, "b");
        require(first.transactionId() == 1L && second.transactionId() == 2L,
                "each successful transfer returns its unique id after commit");

        // participant projection: each subject sees only its own transactions
        List<EconomyTransaction> alphaHistory =
                service.getRecentTransactions(alpha, 0L, 10).items();
        require(alphaHistory.size() == 1 && alphaHistory.get(0).transactionId() == 1L,
                "alpha sees only its own transaction");
        List<EconomyTransaction> charlieHistory =
                service.getRecentTransactions(charlie, 0L, 10).items();
        require(charlieHistory.size() == 1 && charlieHistory.get(0).transactionId() == 2L,
                "charlie sees only its own transaction");
        require(service.getBalance(charlie) == 1_050L,
                "balance of the recipient reflects the incoming transfer");

        // bounded pagination with the afterId cursor
        EconomyPage<EconomyTransaction> pageOne =
                service.getRecentTransactions(bravo, 0L, 1);
        require(pageOne.items().size() == 1, "page size is bounded by the limit");
        require(pageOne.items().get(0).transactionId() == 1L, "first page starts at the cursor");
        require(pageOne.hasMore(), "first page reports more records");
        EconomyPage<EconomyTransaction> pageTwo =
                service.getRecentTransactions(bravo, pageOne.nextAfterId(), 1);
        require(pageTwo.items().size() == 1
                        && pageTwo.items().get(0).transactionId() == 2L,
                "second page continues after the cursor");
        require(!pageTwo.hasMore(), "last page reports no more records");
        EconomyPage<EconomyTransaction> pageThree =
                service.getRecentTransactions(bravo, pageTwo.nextAfterId(), 1);
        require(pageThree.items().isEmpty() && !pageThree.hasMore(),
                "past-the-end page is empty and terminal");

        // out-of-bounds page arguments are rejected
        expectThrows(
                IllegalArgumentException.class,
                () -> service.getRecentTransactions(bravo, 0L, 0),
                "a zero page limit is rejected"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> service.getRecentTransactions(bravo, 0L, 51),
                "a page limit above the configured bound is rejected"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> service.getRecentTransactions(bravo, -1L, 10),
                "a negative cursor is rejected"
        );

        // a failed transfer fabricates no receipt and no transaction id
        EconomyUnavailableException failure = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(alpha, bravo, 1_000_000L, null),
                "a failing transfer throws"
        );
        require(failure.failureCode().equals(EconomyUnavailableException.CODE_INSUFFICIENT_FUNDS),
                "the failure carries the stable code");
        require(repository.snapshot().nextTransactionId() == 3L,
                "the failed attempt reserved no transaction id");
    }

    // ------------------------------------------------------------------
    // acceptance: offline notification lifecycle (§15.1)
    // ------------------------------------------------------------------

    private static void testOfflineNotificationLifecycle() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(storeWithBalances(1_000L, 0L, 0L));
        MutableClock clock = new MutableClock(10_000);
        EconomyRepository repository = repository(store);
        EconomyService service = service(
                repository, clock, presence(ALPHA_ID, BRAVO_ID), directory(ALPHA_ID, BRAVO_ID)
        );
        SubjectId alpha = SubjectId.of(ALPHA_SUBJECT);
        SubjectId bravo = SubjectId.of(BRAVO_SUBJECT);

        service.transfer(alpha, bravo, 77L, "welcome");
        require(service.pendingNotifications(bravo).size() == 1,
                "a pending notification is committed with the transfer snapshot");

        // the notification survives a restart (committed with the snapshot)
        SavedDataBackedTestStore restartedStore = store.restart();
        EconomyRepository restarted = repository(restartedStore);
        List<NotificationSummary> afterRestart =
                restarted.pendingNotifications(bravo);
        require(afterRestart.size() == 1, "pending notification survives restart");
        require(afterRestart.get(0).amount() == 77L, "notification amount survives restart");
        require(afterRestart.get(0).transactionId() == 1L,
                "notification references the committed transaction");

        // acknowledgement is a later mutation; repeat is an idempotent no-op
        EconomyService restartedService = service(
                restarted, clock, presence(ALPHA_ID, BRAVO_ID), directory(ALPHA_ID, BRAVO_ID)
        );
        int commitsBefore = restartedStore.commitCount();
        restartedService.acknowledgeNotification(bravo, 1L);
        require(restarted.pendingNotifications(bravo).isEmpty(),
                "acknowledgement removes the notification");
        require(restartedStore.commitCount() == commitsBefore + 1,
                "acknowledgement commits exactly one snapshot");
        int commitsAfterAck = restartedStore.commitCount();
        restartedService.acknowledgeNotification(bravo, 1L);
        require(restartedStore.commitCount() == commitsAfterAck,
                "acknowledging an unknown notification is an idempotent no-op");
        require(restarted.snapshot().totalSupply() == 1_000L,
                "acknowledgement never changes balances or supply");
    }

    // ------------------------------------------------------------------
    // acceptance: total-supply conservation and load reconciliation (§15.1)
    // ------------------------------------------------------------------

    private static void testSupplyConservationAndLoadReconciliation() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(storeWithBalances(500L, 300L, 200L));
        MutableClock clock = new MutableClock(11_000);
        EconomyRepository repository = repository(store);
        EconomyService service = service(
                repository, clock,
                presence(ALPHA_ID, BRAVO_ID, CHARLIE_ID),
                directory(ALPHA_ID, BRAVO_ID, CHARLIE_ID)
        );
        SubjectId alpha = SubjectId.of(ALPHA_SUBJECT);
        SubjectId bravo = SubjectId.of(BRAVO_SUBJECT);
        SubjectId charlie = SubjectId.of(CHARLIE_SUBJECT);

        require(repository.snapshot().totalSupply() == 1_000L,
                "load-time supply reconciliation matches the account sum");

        service.transfer(alpha, bravo, 100L, null);
        service.transfer(bravo, charlie, 50L, null);
        service.transfer(charlie, alpha, 25L, null);
        require(repository.snapshot().totalSupply() == 1_000L,
                "ordinary transfers conserve the total supply exactly");
        require(service.getBalance(alpha) == 425L, "alpha balance is exact");
        require(service.getBalance(bravo) == 350L, "bravo balance is exact");
        require(service.getBalance(charlie) == 225L, "charlie balance is exact");

        // inconsistent snapshots are rejected at load
        SavedDataBackedTestStore negativeBalance = new SavedDataBackedTestStore();
        negativeBalance.putRaw(storeWithBalanceRaw(ALPHA_SUBJECT, -5L));
        expectThrows(
                EconomyNbtException.class,
                () -> repository(negativeBalance),
                "a negative balance rejects the load"
        );

        SavedDataBackedTestStore oversizedBalance = new SavedDataBackedTestStore();
        oversizedBalance.putRaw(storeWithBalanceRaw(ALPHA_SUBJECT, Long.MAX_VALUE / 2 + 1));
        expectThrows(
                EconomyNbtException.class,
                () -> repository(oversizedBalance),
                "a balance above the structural cap rejects the load"
        );

        SavedDataBackedTestStore negativeTreasury = new SavedDataBackedTestStore();
        CompoundTag negativeTreasuryRoot = storeWithBalances(0L, 0L, 0L);
        negativeTreasuryRoot.putLong("TreasuryBalance", -1L);
        negativeTreasury.putRaw(negativeTreasuryRoot);
        expectThrows(
                EconomyNbtException.class,
                () -> repository(negativeTreasury),
                "a negative treasury balance rejects the load"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: 100K-buffer pruning without authority loss (§15.1)
    // ------------------------------------------------------------------

    private static void testPruningWithoutAuthorityLoss() {
        EconomyLimits pruningLimits = new EconomyLimits(
                10_000, 5, 128, EconomyLimits.DEFAULT_MAX_BALANCE, 100, 50,
                8 * 1024 * 1024, 0L
        );
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(storeWithBalances(1_000L, 0L, 0L));
        MutableClock clock = new MutableClock(12_000);
        EconomyRepository repository = repository(store, pruningLimits);
        EconomyService service = service(
                repository, clock, presence(ALPHA_ID, BRAVO_ID), directory(ALPHA_ID, BRAVO_ID)
        );
        SubjectId alpha = SubjectId.of(ALPHA_SUBJECT);
        SubjectId bravo = SubjectId.of(BRAVO_SUBJECT);

        for (int round = 0; round < 3; round++) {
            service.transfer(alpha, bravo, 100L, null);
            service.transfer(bravo, alpha, 100L, null);
        }
        require(repository.snapshot().transactions().size() == 5,
                "the operational buffer is pruned to the configured bound");
        require(!repository.snapshot().transactions().containsKey(1L),
                "the oldest transaction is pruned");
        require(repository.snapshot().transactions().containsKey(6L),
                "the newest transaction is retained");
        require(repository.snapshot().nextTransactionId() == 7L,
                "transaction-id monotonicity is untouched by pruning");
        require(service.getBalance(alpha) == 1_000L, "balances survive pruning");
        require(service.getBalance(bravo) == 0L, "recipient balance survives pruning");
        require(repository.requireAccount(alpha).accountRevision() == 7L,
                "account revisions survive pruning");
        require(repository.requireAccount(alpha).lastTransactionId() == 6L,
                "lastTransactionId survives pruning");
        require(service.pendingNotifications(alpha).size() == 3
                        && service.pendingNotifications(bravo).size() == 3,
                "pending notification state survives pruning");
        require(repository.snapshot().totalSupply() == 1_000L,
                "total supply survives pruning");

        // pruning is durable across restart and never becomes monetary authority
        EconomyRepository restarted = restartRepository(store);
        require(restarted.snapshot().transactions().size() == 5,
                "pruned buffer state survives restart");
        require(restarted.requireAccount(alpha).balance() == 1_000L,
                "authoritative balances survive restart after pruning");
        require(restarted.snapshot().nextTransactionId() == 7L,
                "next id survives restart after pruning");
    }

    // ------------------------------------------------------------------
    // acceptance: configurable currency presentation without numeric mutation
    // ------------------------------------------------------------------

    private static void testCurrencyPresentationDoesNotMutateValues() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(storeWithBalances(1_000L, 0L, 0L));
        MutableClock clock = new MutableClock(13_000);
        EconomyRepository repository = repository(store);

        EconomyService mora = service(
                repository, clock, presence(ALPHA_ID), directory(ALPHA_ID),
                new CurrencyPresentation("Mora", "", true)
        );
        EconomyService primo = service(
                repository, clock, presence(ALPHA_ID), directory(ALPHA_ID),
                new CurrencyPresentation("Primogem", "\u20A5", false)
        );
        require(mora.formatBalance(1_234L).equals("1,234"),
                "grouped presentation renders thousands with separators");
        require(primo.formatBalance(1_234L).equals("1234 \u20A5"),
                "alternate presentation renders ungrouped with a symbol");
        require(mora.getBalance(SubjectId.of(ALPHA_SUBJECT))
                        == primo.getBalance(SubjectId.of(ALPHA_SUBJECT)),
                "presentation never changes the stored numeric value");

        // the persisted snapshot is byte-identical regardless of presentation
        EconomyNbtCodec codec = new EconomyNbtCodec();
        CompoundTag encoded = codec.encode(repository.snapshot());
        EconomyRepository second = restartRepository(store);
        require(java.util.Arrays.equals(
                        nbtBytes(codec.encode(second.snapshot())), nbtBytes(encoded)),
                "presentation configuration never reaches the stored snapshot");
    }

    // ------------------------------------------------------------------
    // acceptance: forbidden surfaces absent (§15.1)
    // ------------------------------------------------------------------

    private static void testForbiddenSurfacesAbsent() throws Exception {
        require(Set.of(TransactionType.values()).equals(Set.of(
                        TransactionType.TRANSFER,
                        TransactionType.DEPOSIT,
                        TransactionType.WITHDRAWAL,
                        TransactionType.ISSUE,
                        TransactionType.RECLAIM)),
                "TransactionType exposes exactly the approved player, official, "
                        + "and emergency (ISSUE/RECLAIM) types");

        for (Class<?> type : List.of(EconomyService.class, EconomyModule.class)) {
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                for (String forbidden : List.of(
                        "top", "leaderboard", "atm", "interest", "tax",
                        "cash", "gui", "screen", "hud", "packet", "c2s",
                        "setbalance"
                )) {
                    require(!name.contains(forbidden),
                            "no forbidden surface method in " + type.getSimpleName()
                                    + ": " + method.getName());
                }
            }
        }

        // Source-level scan: no leaderboard/ATM/cash/GUI/C2S/network surface
        // and no shared-emergency-infrastructure duplication exists in the
        // economy production sources (comments excluded). The authorized
        // emergency catalogue (ISSUE/RECLAIM provider) is expected and is not
        // a forbidden surface.
        Path projectDirectory = Path.of(
                System.getProperty(PROJECT_DIR_PROPERTY, ".")
        ).toAbsolutePath().normalize();
        Path economyDirectory = projectDirectory.resolve(
                "src/main/java/com/fontainerepublic/server/economy"
        );
        require(Files.isDirectory(economyDirectory),
                "Production economy source directory must exist");
        StringBuilder source = new StringBuilder();
        try (var paths = Files.walk(economyDirectory)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> source.append(read(path)).append('\n'));
        }
        String codeOnly = stripComments(source.toString());
        for (String forbidden : List.of(
                "top", "leaderboard", "atm", "interest", "tax", "cash",
                "gui", "screen", "hud", "packet", "NetworkMessage",
                "SimpleChannel", "c2s", "setBalance",
                "setOp", "isOp", "getPermission"
        )) {
            require(
                    !codeOnly.contains(forbidden),
                    "economy production code must not contain the forbidden surface: "
                            + forbidden
            );
        }
    }

    // ------------------------------------------------------------------
    // acceptance: strict deterministic codec (§15.1)
    // ------------------------------------------------------------------

    private static void testStrictDeterministicCodec() {
        EconomyNbtCodec codec = new EconomyNbtCodec();
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(storeWithBalances(1_000L, 200L, 0L));
        MutableClock clock = new MutableClock(14_000);
        EconomyRepository repository = repository(store);
        EconomyService service = service(
                repository, clock, presence(ALPHA_ID, BRAVO_ID), directory(ALPHA_ID, BRAVO_ID)
        );
        service.transfer(SubjectId.of(ALPHA_SUBJECT), SubjectId.of(BRAVO_SUBJECT),
                150L, "determinism");

        EconomyStoreSnapshot snapshot = repository.snapshot();
        CompoundTag first = codec.encode(snapshot);
        CompoundTag second = codec.encode(snapshot);
        require(java.util.Arrays.equals(nbtBytes(first), nbtBytes(second)),
                "same snapshot encodes to identical ordered bytes");
        require(codec.encodedSize(first) == codec.encodedSize(second),
                "same snapshot encodes to the same serialized size");
        require(codec.decode(second).equals(snapshot),
                "decode(encode(snapshot)) equals the snapshot");
        require(codec.decode(codec.encode(snapshot))
                        .equals(codec.decode(store.load())),
                "persisted form decodes equivalently to the in-memory snapshot");
        require(codec.encodedSize(first) > 0, "encoded snapshot carries real payload bytes");
    }

    private static void testCorruptSnapshotFailClosed() {
        // unknown store field
        SavedDataBackedTestStore unknownStoreField = new SavedDataBackedTestStore();
        CompoundTag root = storeWithBalances(0L, 0L, 0L);
        root.putString("Surprise", "x");
        unknownStoreField.putRaw(root);
        expectThrows(
                EconomyNbtException.class,
                () -> repository(unknownStoreField),
                "unknown store field rejects the load"
        );

        // unknown account field
        SavedDataBackedTestStore unknownAccountField = new SavedDataBackedTestStore();
        CompoundTag account = accountTag(ALPHA_SUBJECT, 100L);
        account.putString("Surprise", "x");
        unknownAccountField.putRaw(storeWith("Accounts", account));
        expectThrows(
                EconomyNbtException.class,
                () -> repository(unknownAccountField),
                "unknown account field rejects the load"
        );

        // newer store version
        SavedDataBackedTestStore newerStore = new SavedDataBackedTestStore();
        CompoundTag newerRoot = storeWithBalances(0L, 0L, 0L);
        newerRoot.putInt("StoreVersion", 99);
        newerStore.putRaw(newerRoot);
        expectThrows(
                EconomyNbtException.class,
                () -> repository(newerStore),
                "a newer store version is rejected fail-closed"
        );

        // newer account version
        SavedDataBackedTestStore newerAccount = new SavedDataBackedTestStore();
        CompoundTag newerAccountTag = accountTag(ALPHA_SUBJECT, 100L);
        newerAccountTag.putInt("AccountVersion", 99);
        newerAccount.putRaw(storeWith("Accounts", newerAccountTag));
        expectThrows(
                EconomyNbtException.class,
                () -> repository(newerAccount),
                "a newer account version is rejected fail-closed"
        );

        // key does not match the record subjectId
        SavedDataBackedTestStore mismatch = new SavedDataBackedTestStore();
        mismatch.putRaw(storeWith("Accounts", accountTag(BRAVO_SUBJECT, 100L)));
        expectThrows(
                EconomyNbtException.class,
                () -> repository(mismatch),
                "an accounts key not matching the record subjectId rejects the load"
        );

        // non-canonical subject key (uppercase hex letters)
        SavedDataBackedTestStore nonCanonical = new SavedDataBackedTestStore();
        nonCanonical.putRaw(storeWith(
                "ABCDEF00-0000-0000-0000-000000000001",
                accountTag(ALPHA_SUBJECT, 100L)
        ));
        expectThrows(
                EconomyNbtException.class,
                () -> repository(nonCanonical),
                "a non-canonical subject key rejects the load"
        );

        // account without a subject is structurally impossible (key/record
        // identity), but a missing subject field rejects the load too
        SavedDataBackedTestStore missingSubject = new SavedDataBackedTestStore();
        CompoundTag noSubject = accountTag(ALPHA_SUBJECT, 100L);
        noSubject.remove("SubjectId");
        missingSubject.putRaw(storeWith("Accounts", noSubject));
        expectThrows(
                EconomyNbtException.class,
                () -> repository(missingSubject),
                "an account without a subject id rejects the load"
        );

        // transaction id not matching the key
        SavedDataBackedTestStore txMismatch = new SavedDataBackedTestStore();
        txMismatch.putRaw(storeWithTransaction(1L,
                transactionTag(5L, ALPHA_SUBJECT, BRAVO_SUBJECT, 10L, "x")));
        expectThrows(
                EconomyNbtException.class,
                () -> repository(txMismatch),
                "a transaction key not matching its id rejects the load"
        );

        // nextTransactionId not greater than the largest transaction id
        SavedDataBackedTestStore nextTooSmall = new SavedDataBackedTestStore();
        CompoundTag nextRoot = storeWithTransaction(2L,
                transactionTag(2L, ALPHA_SUBJECT, BRAVO_SUBJECT, 10L, "x"));
        nextRoot.putLong("NextTransactionId", 2L);
        nextTooSmall.putRaw(nextRoot);
        expectThrows(
                EconomyNbtException.class,
                () -> repository(nextTooSmall),
                "a nextTransactionId below the largest stored id rejects the load"
        );

        // pending notification for a subject without an account
        SavedDataBackedTestStore orphanNotification = new SavedDataBackedTestStore();
        CompoundTag orphanRoot = storeWithBalances(0L, -1L, -1L); // only alpha has an account
        CompoundTag notifications = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag summary = new CompoundTag();
        summary.putInt("NotificationVersion", 1);
        summary.putLong("NotificationId", 1L);
        summary.putLong("TransactionId", 1L);
        summary.putLong("Timestamp", 1_000L);
        summary.putUUID("FromSubject", ALPHA_SUBJECT);
        summary.putLong("Amount", 10L);
        list.add(summary);
        notifications.put(BRAVO_SUBJECT.toString(), list);
        orphanRoot.put("PendingNotifications", notifications);
        orphanNotification.putRaw(orphanRoot);
        expectThrows(
                EconomyNbtException.class,
                () -> repository(orphanNotification),
                "a pending notification for a subject without an account rejects the load"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: restart recovery (§15.1)
    // ------------------------------------------------------------------

    private static void testRestartPersistence() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(storeWithBalances(1_000L, 0L, 0L));
        MutableClock clock = new MutableClock(15_000);
        EconomyRepository firstRepository = repository(store);
        EconomyService firstService = service(
                firstRepository, clock, presence(ALPHA_ID, BRAVO_ID),
                directory(ALPHA_ID, BRAVO_ID)
        );
        SubjectId alpha = SubjectId.of(ALPHA_SUBJECT);
        SubjectId bravo = SubjectId.of(BRAVO_SUBJECT);
        firstService.transfer(alpha, bravo, 300L, "persist");

        EconomyRepository restarted = restartRepository(store);
        require(restarted.requireAccount(alpha).balance() == 700L,
                "source balance is recovered after restart");
        require(restarted.requireAccount(bravo).balance() == 300L,
                "target balance is recovered after restart");
        require(restarted.snapshot().storeRevision()
                        == firstRepository.snapshot().storeRevision(),
                "store revision survives restart");
        require(restarted.snapshot().nextTransactionId() == 2L,
                "next transaction id survives restart");
        require(restarted.pendingNotifications(bravo).size() == 1,
                "pending notification survives restart");
        require(restarted.participantTransactions(bravo, 0L, 10).size() == 1,
                "transaction history survives restart");
        require(restarted.snapshot().totalSupply() == 1_000L,
                "total supply survives restart");
    }

    // ------------------------------------------------------------------
    // acceptance: capacity fail-closed (§15.1)
    // ------------------------------------------------------------------

    private static void testCapacityFailClosed() {
        EconomyLimits tight = new EconomyLimits(
                1, 100_000, 128, EconomyLimits.DEFAULT_MAX_BALANCE, 100, 50,
                8 * 1024 * 1024, 0L
        );
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(16_000);
        EconomyRepository repository = repository(store, tight);
        EconomyService service = service(
                repository, clock, presence(ALPHA_ID, BRAVO_ID),
                directory(ALPHA_ID, BRAVO_ID)
        );
        service.ensureAccountForPlayer(ALPHA_ID);

        EconomyUnavailableException failure = expectThrows(
                EconomyUnavailableException.class,
                () -> service.ensureAccountForPlayer(BRAVO_ID),
                "account capacity is enforced fail-closed"
        );
        require(failure.failureCode().equals(
                        EconomyUnavailableException.CODE_CAPACITY_EXCEEDED),
                "capacity failure carries the stable code");
        require(repository.size() == 1, "failed provisioning publishes nothing");
        require(repository.snapshot().storeRevision() == 1L,
                "failed provisioning advances no revision");
    }

    // ------------------------------------------------------------------
    // acceptance: injected save failure with no publication (§15.1)
    // ------------------------------------------------------------------

    private static void testStoreFailureAtomicity() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        // only alpha has an account: the failed transfer must not publish the
        // lazily-created target either
        store.putRaw(storeWithBalances(1_000L, -1L, -1L));
        MutableClock clock = new MutableClock(17_000);
        EconomyRepository repository = repository(store);
        EconomyService service = service(
                repository, clock, presence(ALPHA_ID, BRAVO_ID),
                directory(ALPHA_ID, BRAVO_ID)
        );
        SubjectId alpha = SubjectId.of(ALPHA_SUBJECT);
        SubjectId bravo = SubjectId.of(BRAVO_SUBJECT);

        // provisioning failure
        SavedDataBackedTestStore provisioningStore = new SavedDataBackedTestStore();
        EconomyRepository provisioningRepository = repository(provisioningStore);
        EconomyService provisioningService = service(
                provisioningRepository, clock, presence(ALPHA_ID), directory(ALPHA_ID)
        );
        provisioningStore.setCommitFailureCode("INJECTED_FAILURE");
        EconomyUnavailableException ensureFailure = expectThrows(
                EconomyUnavailableException.class,
                () -> provisioningService.ensureAccountForPlayer(ALPHA_ID),
                "a durable gate rejection fails closed for provisioning"
        );
        require(ensureFailure.failureCode().equals(
                        EconomyUnavailableException.CODE_STORE_FAILURE),
                "store failure carries the stable code");
        require(provisioningRepository.size() == 0,
                "a failed provisioning publishes no account");
        require(provisioningRepository.snapshot().storeRevision() == 0L,
                "a failed provisioning advances no revision");

        // transfer failure: no debit, credit, transaction, or notification
        store.setCommitFailureCode("INJECTED_FAILURE");
        EconomyUnavailableException transferFailure = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(alpha, bravo, 100L, "boom"),
                "a durable gate rejection fails closed for transfers"
        );
        require(transferFailure.failureCode().equals(
                        EconomyUnavailableException.CODE_STORE_FAILURE),
                "transfer store failure carries the stable code");
        require(repository.requireAccount(alpha).balance() == 1_000L,
                "a failed transfer publishes no debit");
        require(repository.findAccount(bravo).isEmpty(),
                "a failed transfer publishes no lazy target credit");
        require(repository.snapshot().transactions().isEmpty(),
                "a failed transfer publishes no transaction");
        require(repository.snapshot().nextTransactionId() == 1L,
                "a failed transfer fabricates no transaction id");
        require(repository.pendingNotifications(bravo).isEmpty(),
                "a failed transfer publishes no notification");
        require(repository.snapshot().storeRevision() == 1L,
                "a failed transfer advances no revision");
        require(repository.snapshot().totalSupply() == 1_000L,
                "a failed transfer conserves the total supply");
    }

    // ------------------------------------------------------------------
    // acceptance: subject status fail-closed at the mutation boundary (§15.1)
    // ------------------------------------------------------------------

    private static void testSubjectNotActiveFailClosed() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(storeWithBalances(1_000L, 0L, 0L));
        MutableClock clock = new MutableClock(18_000);
        EconomyRepository repository = repository(store);
        FakePlayerPresence presence = presence(ALPHA_ID, BRAVO_ID);
        FakeSubjectDirectory directory = directory(ALPHA_ID, BRAVO_ID);
        EconomyService service = service(repository, clock, presence, directory);
        SubjectId alpha = SubjectId.of(ALPHA_SUBJECT);
        SubjectId bravo = SubjectId.of(BRAVO_SUBJECT);

        // suspended source fails closed
        directory.setStatus(alpha, SubjectStatus.SUSPENDED);
        EconomyUnavailableException suspended = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(alpha, bravo, 10L, null),
                "a suspended subject cannot transfer"
        );
        require(suspended.failureCode().equals(
                        EconomyUnavailableException.CODE_SUBJECT_NOT_ACTIVE),
                "non-ACTIVE status carries the stable code");
        directory.setStatus(alpha, SubjectStatus.ACTIVE);

        // revoked target fails closed
        directory.setStatus(bravo, SubjectStatus.REVOKED);
        EconomyUnavailableException revoked = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(alpha, bravo, 10L, null),
                "a revoked subject cannot receive"
        );
        require(revoked.failureCode().equals(
                        EconomyUnavailableException.CODE_SUBJECT_NOT_ACTIVE),
                "revoked target carries the stable code");
        directory.setStatus(bravo, SubjectStatus.ACTIVE);

        // missing subject fails closed
        directory.remove(SubjectId.of(CHARLIE_SUBJECT));
        EconomyUnavailableException missing = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(alpha, SubjectId.of(CHARLIE_SUBJECT), 10L, null),
                "a missing subject cannot be a transfer target"
        );
        require(missing.failureCode().equals(EconomyUnavailableException.CODE_SUBJECT_MISSING),
                "missing subject carries the stable code");

        // registry unavailable fails closed
        directory.setAvailable(false);
        EconomyUnavailableException unavailable = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(alpha, bravo, 10L, null),
                "an unavailable subject registry fails closed"
        );
        require(unavailable.failureCode().equals(
                        EconomyUnavailableException.CODE_SUBJECT_REGISTRY_UNAVAILABLE),
                "registry unavailability carries the stable code");

        require(repository.snapshot().totalSupply() == 1_000L,
                "status failures publish no monetary change");
        require(repository.snapshot().storeRevision() == 1L,
                "status failures advance no revision");
    }

    // ------------------------------------------------------------------
    // acceptance: central-bank official duties (FR-ECO-002-A §5)
    // ------------------------------------------------------------------

    private static void testBankOfficialDuties() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(storeWithBalances(1_000L, 200L, 0L));
        MutableClock clock = new MutableClock(20_000);
        FakeInstitutionAccessService access = new FakeInstitutionAccessService();
        EconomyRepository repository = repository(store);
        EconomyService service = service(
                repository,
                clock,
                presence(ALPHA_ID, BRAVO_ID, CHARLIE_ID),
                directory(ALPHA_ID, BRAVO_ID, CHARLIE_ID),
                access
        );
        SubjectId alpha = SubjectId.of(ALPHA_SUBJECT);
        SubjectId bravo = SubjectId.of(BRAVO_SUBJECT);
        SubjectId charlie = SubjectId.of(CHARLIE_SUBJECT);

        // treasury total is a public read-only aggregate
        require(service.getTreasuryBalance() == 0L,
                "the treasury starts at zero and is readable publicly");
        require(repository.snapshot().treasuryBalance() == 0L,
                "the treasury read is the exact store value");

        // official withdrawal: player -> treasury, supply conserved
        EconomyTransaction withdrawal = service.withdraw(
                alpha, 100L, "treasury intake", bankContext(ALPHA_ID)
        );
        require(withdrawal.type() == TransactionType.WITHDRAWAL,
                "an official withdrawal records WITHDRAWAL");
        require(withdrawal.from().equals(alpha) && withdrawal.to() == null,
                "WITHDRAWAL has only the source participant (system sink)");
        require(repository.snapshot().treasuryBalance() == 100L,
                "the treasury holds the withdrawn amount");
        require(service.getBalance(alpha) == 900L,
                "the source balance is debited exactly");
        require(repository.snapshot().totalSupply() == 1_200L,
                "withdrawal conserves the total supply identity");

        // official issuance: treasury -> player, supply conserved
        EconomyTransaction deposit = service.deposit(
                bravo, 50L, "issuance", bankContext(ALPHA_ID)
        );
        require(deposit.type() == TransactionType.DEPOSIT,
                "an official issuance records DEPOSIT");
        require(deposit.from() == null && deposit.to().equals(bravo),
                "DEPOSIT has only the destination participant (system source)");
        require(repository.snapshot().treasuryBalance() == 50L,
                "the treasury is debited exactly");
        require(service.getBalance(bravo) == 250L,
                "the target balance is credited exactly");
        require(repository.snapshot().totalSupply() == 1_200L,
                "issuance conserves the total supply identity");

        // issuance beyond the treasury fails closed with no publication
        EconomyUnavailableException poorTreasury = expectThrows(
                EconomyUnavailableException.class,
                () -> service.deposit(bravo, 51L, "overdraw", bankContext(ALPHA_ID)),
                "issuance beyond the treasury is rejected"
        );
        require(poorTreasury.failureCode().equals(
                        EconomyUnavailableException.CODE_TREASURY_INSUFFICIENT),
                "treasury shortage carries the stable code");
        require(repository.snapshot().nextTransactionId() == 3L
                        && repository.snapshot().treasuryBalance() == 50L,
                "the failed issuance publishes nothing");

        // final on-site revalidation: null and non-VALID contexts fail closed
        expectThrows(
                EconomyUnavailableException.class,
                () -> service.deposit(bravo, 10L, "no context", null),
                "a null on-site context rejects the official duty"
        );
        access.setResult(ValidationResult.invalid(ValidationResult.REASON_EXPIRED));
        EconomyUnavailableException expired = expectThrows(
                EconomyUnavailableException.class,
                () -> service.withdraw(alpha, 10L, "expired", bankContext(ALPHA_ID)),
                "an expired on-site context rejects the official duty"
        );
        require(expired.failureCode().equals(
                        EconomyUnavailableException.CODE_ON_SITE_CONTEXT_INVALID),
                "invalid on-site context carries the stable code");
        access.setResult(ValidationResult.ok());

        // freeze/unfreeze are on-site official duties
        EconomyAccount frozenAccount = service.freeze(
                bravo, "court order", bankContext(ALPHA_ID)
        );
        require(frozenAccount.frozen(), "freeze flips the account flag");
        require(repository.requireAccount(bravo).frozen()
                        && repository.requireAccount(bravo).accountRevision() == 3L,
                "freeze is persisted and advances the account revision once");

        // a frozen account rejects transfer, withdrawal and issuance
        EconomyUnavailableException frozenTransfer = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(bravo, alpha, 1L, null),
                "a frozen account cannot transfer out"
        );
        require(frozenTransfer.failureCode().equals(
                        EconomyUnavailableException.CODE_FROZEN),
                "frozen transfer carries FROZEN");
        EconomyUnavailableException frozenInbound = expectThrows(
                EconomyUnavailableException.class,
                () -> service.transfer(alpha, bravo, 1L, null),
                "a frozen account cannot receive a transfer"
        );
        require(frozenInbound.failureCode().equals(
                        EconomyUnavailableException.CODE_FROZEN),
                "frozen inbound transfer carries FROZEN");
        EconomyUnavailableException frozenWithdraw = expectThrows(
                EconomyUnavailableException.class,
                () -> service.withdraw(bravo, 1L, "x", bankContext(ALPHA_ID)),
                "a frozen account cannot withdraw"
        );
        require(frozenWithdraw.failureCode().equals(
                        EconomyUnavailableException.CODE_FROZEN),
                "frozen withdrawal carries FROZEN");
        EconomyUnavailableException frozenDeposit = expectThrows(
                EconomyUnavailableException.class,
                () -> service.deposit(bravo, 1L, "x", bankContext(ALPHA_ID)),
                "a frozen account cannot receive issuance"
        );
        require(frozenDeposit.failureCode().equals(
                        EconomyUnavailableException.CODE_FROZEN),
                "frozen issuance carries FROZEN");
        require(repository.snapshot().totalSupply() == 1_200L
                        && repository.snapshot().nextTransactionId() == 3L,
                "frozen rejections publish no monetary change");

        // freezing twice is rejected; unfreezing restores participation
        EconomyUnavailableException doubleFreeze = expectThrows(
                EconomyUnavailableException.class,
                () -> service.freeze(bravo, "again", bankContext(ALPHA_ID)),
                "freezing an already-frozen account is rejected"
        );
        require(doubleFreeze.failureCode().equals(
                        EconomyUnavailableException.CODE_FROZEN),
                "double freeze carries FROZEN");
        EconomyAccount unfrozenAccount = service.unfreeze(
                bravo, "lifted", bankContext(ALPHA_ID)
        );
        require(!unfrozenAccount.frozen(), "unfreeze clears the account flag");
        EconomyUnavailableException doubleUnfreeze = expectThrows(
                EconomyUnavailableException.class,
                () -> service.unfreeze(bravo, "again", bankContext(ALPHA_ID)),
                "unfreezing an unfrozen account is rejected"
        );
        require(doubleUnfreeze.failureCode().equals(
                        EconomyUnavailableException.CODE_FROZEN),
                "double unfreeze carries FROZEN");
        service.transfer(alpha, bravo, 5L, "post-freeze");
        require(service.getBalance(bravo) == 255L,
                "an unfrozen account participates in ordinary transfers again");

        // freeze state and official transactions survive restart
        service.freeze(bravo, "persist freeze", bankContext(ALPHA_ID));
        require(repository.snapshot().transactions().size() == 3,
                "freeze/unfreeze never create monetary transactions");
        EconomyRepository restarted = restartRepository(store);
        require(restarted.requireAccount(bravo).frozen(),
                "freeze state survives restart");
        require(restarted.requireAccount(bravo).accountRevision() == 6L,
                "the revision sequence survives restart");
        require(restarted.snapshot().transactions().size() == 3
                        && restarted.snapshot().nextTransactionId() == 4L,
                "official transactions survive restart with their ids");
        require(restarted.snapshot().totalSupply() == 1_200L,
                "total supply survives restart after official duties");

        // issuance to a subject without an account lazily provisions it
        SavedDataBackedTestStore bare = new SavedDataBackedTestStore();
        CompoundTag bareRoot = storeWithBalances(-1L, -1L, -1L);
        bareRoot.putLong("TreasuryBalance", 10L);
        bare.putRaw(bareRoot);
        EconomyRepository bareRepository = repository(bare);
        EconomyService bareService = service(
                bareRepository,
                clock,
                presence(ALPHA_ID, CHARLIE_ID),
                directory(ALPHA_ID, CHARLIE_ID),
                new FakeInstitutionAccessService()
        );
        bareService.ensureAccountForPlayer(ALPHA_ID);
        EconomyUnavailableException seedWithdrawFailure = expectThrows(
                EconomyUnavailableException.class,
                () -> bareService.withdraw(alpha, 10L, "seed", bankContext(ALPHA_ID)),
                "withdraw without funds is rejected"
        );
        require(seedWithdrawFailure.failureCode().equals(
                        EconomyUnavailableException.CODE_INSUFFICIENT_FUNDS),
                "insufficient funds carries INSUFFICIENT_FUNDS");
        bareService.deposit(charlie, 10L, "seed", bankContext(ALPHA_ID));
        require(bareRepository.requireAccount(charlie).balance() == 10L,
                "issuance lazily provisions the zero-balance target account");
        require(bareRepository.snapshot().totalSupply() == 10L,
                "issuance into a fresh account conserves the supply identity");
    }

    // ------------------------------------------------------------------
    // module contract checks
    // ------------------------------------------------------------------

    private static void testModuleContract() {
        require(EconomyRepository.MODULE_DATA_KEY.equals("economy"),
                "economy owns exactly the approved namespace");
        require(EconomyModule.MODULE_ID.value().equals("economy"),
                "economy module id is 'economy'");
        ModuleDefinition definition = new ModuleDefinition(
                EconomyModule.MODULE_ID,
                new ModuleMetadata("Economy", "1.0.0", Optional.empty(), Optional.empty()),
                Set.of(
                        PlayerDataModule.MODULE_ID,
                        SubjectRegistryModule.MODULE_ID,
                        AuditModule.MODULE_ID,
                        InstitutionAccessModule.MODULE_ID
                ),
                Set.of(),
                60,
                EconomyModule::new
        );
        require(definition.requiredDependencies().equals(
                        Set.of(
                                PlayerDataModule.MODULE_ID,
                                SubjectRegistryModule.MODULE_ID,
                                AuditModule.MODULE_ID,
                                InstitutionAccessModule.MODULE_ID
                        )),
                "economy depends on player-data, subject-registry, audit, "
                        + "and institution-access only");
        for (ModuleId dependency : definition.requiredDependencies()) {
            String value = dependency.value();
            require(!value.contains("emg") && !value.contains("emergency")
                            && !value.contains("citizen") && !value.contains("land")
                            && !value.contains("government") && !value.contains("parliament")
                            && !value.contains("justice"),
                    "economy never depends on later-phase or parallel namespaces: " + value);
        }
    }

    // ------------------------------------------------------------------
    // acceptance: no bulk enumeration API (§15.1)
    // ------------------------------------------------------------------

    private static void testNoEnumerationApi() {
        for (Class<?> type : List.of(EconomyService.class, EconomyRepository.class)) {
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        || Modifier.isPrivate(method.getModifiers())) {
                    continue;
                }
                String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                // bounded participant projections and the bounded permanent
                // emergency receipt page are exempt: they are exact lookups
                // or bounded reconciliation pages, never a bulk listing
                if (name.equals("pendingnotifications")
                        || name.equals("participanttransactions")
                        || name.equals("receiptsafter")) {
                    continue;
                }
                Class<?> returnType = method.getReturnType();
                require(!Collection.class.isAssignableFrom(returnType)
                                && !Map.class.isAssignableFrom(returnType)
                                && !returnType.isArray()
                                && !Stream.class.isAssignableFrom(returnType),
                        "no bulk enumeration method in " + type.getSimpleName() + ": "
                                + method.getName());
                require(!name.contains("findall") && !name.contains("list")
                                && !name.contains("values") && !name.contains("all"),
                        "no bulk enumeration method name in " + type.getSimpleName() + ": "
                                + method.getName());
            }
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static EconomyRepository repository(EconomyStore store) {
        return new EconomyRepository(store, new EconomyNbtCodec(), EconomyLimits.DEFAULT);
    }

    private static EconomyRepository repository(EconomyStore store, EconomyLimits limits) {
        return new EconomyRepository(store, new EconomyNbtCodec(), limits);
    }

    private static EconomyRepository restartRepository(SavedDataBackedTestStore store) {
        return repository(store.restart());
    }

    private static EconomyService service(
            EconomyRepository repository,
            LongSupplier clock,
            PlayerPresence presence,
            SubjectDirectory directory
    ) {
        return service(repository, clock, presence, directory, TEST_LIMITS);
    }

    private static EconomyService service(
            EconomyRepository repository,
            LongSupplier clock,
            PlayerPresence presence,
            SubjectDirectory directory,
            EconomyLimits limits
    ) {
        return new DefaultEconomyService(
                repository,
                clock,
                presence,
                directory,
                limits,
                CurrencyPresentation.DEFAULT,
                new FakeInstitutionAccessService(),
                null
        );
    }

    private static EconomyService service(
            EconomyRepository repository,
            LongSupplier clock,
            PlayerPresence presence,
            SubjectDirectory directory,
            CurrencyPresentation presentation
    ) {
        return new DefaultEconomyService(
                repository,
                clock,
                presence,
                directory,
                TEST_LIMITS,
                presentation,
                new FakeInstitutionAccessService(),
                null
        );
    }

    /**
     * Binds a configurable institution-access double so the official-duty
     * tests can drive the final mutation boundary.
     */
    private static EconomyService service(
            EconomyRepository repository,
            LongSupplier clock,
            PlayerPresence presence,
            SubjectDirectory directory,
            FakeInstitutionAccessService access
    ) {
        return new DefaultEconomyService(
                repository,
                clock,
                presence,
                directory,
                TEST_LIMITS,
                CurrencyPresentation.DEFAULT,
                access,
                null
        );
    }

    private static FakePlayerPresence presence(UUID... players) {
        FakePlayerPresence presence = new FakePlayerPresence();
        for (UUID player : players) {
            presence.add(player);
        }
        return presence;
    }

    private static FakeSubjectDirectory directory(UUID... players) {
        FakeSubjectDirectory directory = new FakeSubjectDirectory();
        for (UUID player : players) {
            directory.add(player);
        }
        return directory;
    }

    private static SubjectRecord subject(UUID playerId) {
        UUID subjectUuid = switch (playerId.toString()) {
            case "00000000-0000-0000-0000-000000000001" -> ALPHA_SUBJECT;
            case "00000000-0000-0000-0000-000000000002" -> BRAVO_SUBJECT;
            default -> CHARLIE_SUBJECT;
        };
        return new SubjectRecord(
                SubjectRecord.CURRENT_SCHEMA_VERSION,
                SubjectId.of(subjectUuid),
                RegistryNumber.forTypeAndSerial(SubjectType.NATURAL_PERSON, 42),
                SubjectType.NATURAL_PERSON,
                OwnerReference.forPlayer(playerId),
                SubjectStatus.ACTIVE,
                1L,
                1_000L,
                1_000L
        );
    }

    private static List<Class<?>> economyTypes() {
        return List.of(
                EconomyModule.class,
                EconomyService.class,
                CurrencyPresentation.class,
                EconomyPage.class,
                TransferReceipt.class,
                SubjectDirectory.class,
                EconomyAccount.class,
                EconomyTransaction.class,
                NotificationSummary.class,
                TransactionType.class,
                EconomyNbtCodec.class,
                EconomyNbtException.class,
                EconomyUnavailableException.class,
                EconomyLimits.class,
                EconomyStore.class,
                EconomyStoreSnapshot.class,
                EconomyRepository.class,
                DefaultEconomyService.class
        );
    }

    /** Initial store with the three subjects carrying the given balances. */
    private static CompoundTag storeWithBalances(long alphaBalance, long bravoBalance,
                                                 long charlieBalance) {
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 1L);
        root.putLong("TreasuryBalance", 0L);
        root.putLong("NextTransactionId", 1L);
        CompoundTag accounts = new CompoundTag();
        if (alphaBalance >= 0) {
            accounts.put(ALPHA_SUBJECT.toString(), accountTag(ALPHA_SUBJECT, alphaBalance));
        }
        if (bravoBalance >= 0) {
            accounts.put(BRAVO_SUBJECT.toString(), accountTag(BRAVO_SUBJECT, bravoBalance));
        }
        if (charlieBalance >= 0) {
            accounts.put(CHARLIE_SUBJECT.toString(), accountTag(CHARLIE_SUBJECT, charlieBalance));
        }
        root.put("Accounts", accounts);
        root.put("Transactions", new CompoundTag());
        root.put("PendingNotifications", new CompoundTag());
        return root;
    }

    /** Single-account store (all others empty). */
    private static CompoundTag storeWithBalanceRaw(UUID subjectUuid, long balance) {
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 1L);
        root.putLong("TreasuryBalance", 0L);
        root.putLong("NextTransactionId", 1L);
        CompoundTag accounts = new CompoundTag();
        accounts.put(subjectUuid.toString(), accountTag(subjectUuid, balance));
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
        return tag;
    }

    /** Store whose Accounts compound contains exactly one key. */
    private static CompoundTag storeWith(String accountsKey, CompoundTag account) {
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 1L);
        root.putLong("TreasuryBalance", 0L);
        root.putLong("NextTransactionId", 2L);
        CompoundTag accounts = new CompoundTag();
        accounts.put(accountsKey, account);
        root.put("Accounts", accounts);
        root.put("Transactions", new CompoundTag());
        root.put("PendingNotifications", new CompoundTag());
        return root;
    }

    private static CompoundTag storeWithTransaction(long key, CompoundTag transaction) {
        CompoundTag root = new CompoundTag();
        root.putInt("StoreVersion", 1);
        root.putLong("StoreRevision", 1L);
        root.putLong("TreasuryBalance", 0L);
        root.putLong("NextTransactionId", key + 1);
        root.put("Accounts", new CompoundTag());
        CompoundTag transactions = new CompoundTag();
        transactions.put(Long.toString(key), transaction);
        root.put("Transactions", transactions);
        root.put("PendingNotifications", new CompoundTag());
        return root;
    }

    private static CompoundTag transactionTag(long id, UUID from, UUID to, long amount,
                                              String memo) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("TransactionVersion", 1);
        tag.putLong("TransactionId", id);
        tag.putLong("Timestamp", 1_000L);
        tag.putUUID("FromSubject", from);
        tag.putUUID("ToSubject", to);
        tag.putLong("Amount", amount);
        tag.putString("Type", "TRANSFER");
        if (memo != null) {
            tag.putString("Memo", memo);
        }
        return tag;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to read " + path, failure);
        }
    }

    private static byte[] nbtBytes(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeUnnamedTag(tag, new DataOutputStream(out));
            return out.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to serialize NBT", failure);
        }
    }

    /** Removes line and block comments while preserving string literals. */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;
        boolean inString = false;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (inString) {
                out.append(current);
                if (current == '\\' && index + 1 < source.length()) {
                    out.append(source.charAt(index + 1));
                    index += 2;
                    continue;
                }
                if (current == '"') {
                    inString = false;
                }
                index++;
                continue;
            }
            if (current == '"') {
                inString = true;
                out.append(current);
                index++;
                continue;
            }
            if (current == '/' && index + 1 < source.length()
                    && source.charAt(index + 1) == '/') {
                while (index < source.length() && source.charAt(index) != '\n') {
                    index++;
                }
                continue;
            }
            if (current == '/' && index + 1 < source.length()
                    && source.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < source.length()
                        && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                    index++;
                }
                index += 2;
                continue;
            }
            out.append(current);
            index++;
        }
        return out.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T extends Throwable> T expectThrows(
            Class<T> expected,
            Runnable action,
            String message
    ) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (expected.isInstance(throwable)) {
                return expected.cast(throwable);
            }
            throw new AssertionError(
                    message + ": expected " + expected.getSimpleName()
                            + ", got " + throwable.getClass().getSimpleName(),
                    throwable
            );
        }
        throw new AssertionError(message + ": expected " + expected.getSimpleName());
    }

    private static final class SavedDataBackedTestStore implements EconomyStore {
        private final ModSavedData savedData;
        private String commitFailureCode;
        private RuntimeException commitException;
        private int commitCount;

        private SavedDataBackedTestStore() {
            this(new ModSavedData());
        }

        private SavedDataBackedTestStore(ModSavedData savedData) {
            this.savedData = savedData;
        }

        @Override
        public CompoundTag load() {
            return savedData.getModuleData(EconomyRepository.MODULE_DATA_KEY).copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            commitCount++;
            if (commitException != null) {
                throw commitException;
            }
            if (commitFailureCode != null) {
                return new DurableCommitResult(
                        DurableCommitStatus.FAILED,
                        EconomyRepository.MODULE_DATA_KEY,
                        0L,
                        0L,
                        commitFailureCode
                );
            }
            savedData.putModuleData(
                    EconomyRepository.MODULE_DATA_KEY,
                    snapshot.copy()
            );
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    EconomyRepository.MODULE_DATA_KEY,
                    1L,
                    0L,
                    ""
            );
        }

        private void putRaw(CompoundTag raw) {
            savedData.putModuleData(EconomyRepository.MODULE_DATA_KEY, raw);
        }

        private void setCommitFailureCode(String failureCode) {
            this.commitFailureCode = failureCode;
        }

        private int commitCount() {
            return commitCount;
        }

        private SavedDataBackedTestStore restart() {
            CompoundTag root = savedData.save(new CompoundTag());
            return new SavedDataBackedTestStore(ModSavedData.load(root));
        }
    }

    private static final class MutableClock implements LongSupplier {
        private long now;

        private MutableClock(long now) {
            this.now = now;
        }

        @Override
        public long getAsLong() {
            return now;
        }

        private void set(long now) {
            this.now = now;
        }
    }

    private static final class FakePlayerPresence implements PlayerPresence {
        private final Set<UUID> players = new java.util.HashSet<>();
        private boolean available = true;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public boolean hasPlayerRecord(UUID playerId) {
            return players.contains(playerId);
        }

        private void add(UUID playerId) {
            players.add(playerId);
        }

        private void setAvailable(boolean available) {
            this.available = available;
        }
    }

    private static final class FakeSubjectDirectory implements SubjectDirectory {
        private final Map<UUID, SubjectRecord> subjects = new LinkedHashMap<>();
        private boolean available = true;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public SubjectRecord ensureSubject(UUID playerId) {
            if (!available) {
                throw new IllegalStateException("subject registry is not available");
            }
            SubjectRecord record = subjects.get(playerId);
            if (record == null) {
                throw new IllegalStateException("no subject provisioned for " + playerId);
            }
            return record;
        }

        @Override
        public boolean hasSubject(SubjectId subjectId) {
            if (!available) {
                return false;
            }
            return subjects.values().stream()
                    .anyMatch(record -> record.subjectId().equals(subjectId));
        }

        @Override
        public Optional<SubjectStatus> status(SubjectId subjectId) {
            if (!available) {
                return Optional.empty();
            }
            return subjects.values().stream()
                    .filter(record -> record.subjectId().equals(subjectId))
                    .map(SubjectRecord::status)
                    .findFirst();
        }

        private void add(UUID playerId) {
            subjects.put(playerId, subject(playerId));
        }

        private void remove(SubjectId subjectId) {
            subjects.entrySet().removeIf(entry -> entry.getValue().subjectId().equals(subjectId));
        }

        private void setStatus(SubjectId subjectId, SubjectStatus status) {
            subjects.replaceAll(
                    (playerId, record) -> record.subjectId().equals(subjectId)
                            ? record.withStatus(status, 2_000L)
                            : record
            );
        }

        private void setAvailable(boolean available) {
            this.available = available;
        }
    }

    /** Official-duty on-site context for the central-bank tests. */
    private static OnSiteContext bankContext(UUID playerId) {
        return new OnSiteContext(
                UUID.randomUUID(),
                playerId,
                InstitutionType.CENTRAL_BANK,
                FacilityId.of(UUID.randomUUID()),
                ZoneId.of(UUID.randomUUID()),
                WorkflowKind.OFFICIAL_ROUTINE,
                CapabilityClass.ONSITE_OFFICIAL_DUTY,
                1_000L,
                2_000L,
                1L,
                1L,
                "minecraft:overworld",
                10,
                20,
                30
        );
    }

    /**
     * Test double of the shared institution access boundary: records every
     * final mutation-boundary call and its capability, and returns a
     * configurable result. Every other operation is unsupported — the economy
     * module consumes the boundary at mutation time only.
     */
    private static final class FakeInstitutionAccessService
            implements InstitutionAccessService {
        private ValidationResult result = ValidationResult.ok();
        private int validateCalls;
        private CapabilityClass lastCapability;

        @Override
        public ValidationResult validateAtMutation(
                OnSiteContext context,
                CapabilityClass capability,
                long now,
                String dimension,
                int x,
                int y,
                int z
        ) {
            validateCalls++;
            lastCapability = capability;
            return result;
        }

        private void setResult(ValidationResult result) {
            this.result = result;
        }

        private int validateCalls() {
            return validateCalls;
        }

        private CapabilityClass lastCapability() {
            return lastCapability;
        }

        @Override
        public FacilityReceipt registerFacility(UUID actor, FacilityRegistrationRequest request) {
            throw unsupported();
        }

        @Override
        public FacilityReceipt suspendFacility(UUID actor, FacilityId facilityId) {
            throw unsupported();
        }

        @Override
        public FacilityReceipt activateFacility(UUID actor, FacilityId facilityId) {
            throw unsupported();
        }

        @Override
        public FacilityReceipt relocateFacility(UUID actor, FacilityId facilityId, ParcelId newParcelId) {
            throw unsupported();
        }

        @Override
        public FacilityReceipt disableFacility(UUID actor, FacilityId facilityId) {
            throw unsupported();
        }

        @Override
        public ZoneReceipt addZone(UUID actor, ZoneRegistrationRequest request) {
            throw unsupported();
        }

        @Override
        public ZoneReceipt removeZone(UUID actor, ZoneId zoneId) {
            throw unsupported();
        }

        @Override
        public ZoneReceipt resizeZone(UUID actor, ZoneId zoneId, ZoneRegion newRegion) {
            throw unsupported();
        }

        @Override
        public ZoneReceipt setZoneKind(UUID actor, ZoneId zoneId, ZoneKind newKind) {
            throw unsupported();
        }

        @Override
        public ZoneReceipt suspendZone(UUID actor, ZoneId zoneId) {
            throw unsupported();
        }

        @Override
        public ZoneReceipt activateZone(UUID actor, ZoneId zoneId) {
            throw unsupported();
        }

        @Override
        public OnSiteContext issueOnSiteContext(
                UUID playerId,
                ZoneId zoneId,
                CapabilityClass capability,
                String playerDimension,
                int x,
                int y,
                int z
        ) {
            throw unsupported();
        }

        @Override
        public void consume(OnSiteContext context) {
            // no-op test double
        }

        @Override
        public void invalidateOnLeave(UUID playerId) {
            // no-op test double
        }

        @Override
        public java.util.Optional<Facility> getFacility(FacilityId facilityId) {
            throw unsupported();
        }

        @Override
        public java.util.Optional<Zone> getZone(ZoneId zoneId) {
            throw unsupported();
        }

        private UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException(
                    "not part of the economy test double"
            );
        }
    }
}
