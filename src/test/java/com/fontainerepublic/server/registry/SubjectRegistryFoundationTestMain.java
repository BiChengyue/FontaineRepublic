package com.fontainerepublic.server.registry;

import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.core.ModSavedData;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.registry.api.PlayerPresence;
import com.fontainerepublic.server.registry.api.PublicRoutingResult;
import com.fontainerepublic.server.registry.api.RoutingStatus;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.fontainerepublic.server.registry.model.BootstrapState;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.Reservation;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import com.fontainerepublic.server.registry.model.SubjectType;
import com.fontainerepublic.server.registry.persistence.RegistrySerialGenerator;
import com.fontainerepublic.server.registry.persistence.SubjectIdSource;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryLimits;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryNbtCodec;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryNbtException;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryRepository;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryStore;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryStoreSnapshot;
import com.fontainerepublic.server.registry.persistence.SubjectRegistryUnavailableException;
import com.fontainerepublic.server.registry.service.DefaultSubjectRegistryService;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Dependency-free validation entry point for FR-ID-001 (unified digital
 * subject registry core). Exercises the FR-ID-001-A §19 acceptance matrix
 * (excluding the bootstrap-bound rows, which are BLOCKED on a separately
 * approved design) with an injectable store and SavedData-backed restart
 * simulation.
 */
public final class SubjectRegistryFoundationTestMain {

    private static final UUID ALPHA_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BRAVO_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private SubjectRegistryFoundationTestMain() {
    }

    public static void main(String[] args) {
        testFixedNumberChecksums();
        testNumberGenerationAndCheckDigits();
        testNumberParsingCanonicalAndDisplay();
        testMalformedNumberRejection();
        testType00ClosedException();
        testFixedReservationsFromFirstSnapshot();
        testOfficeSubjectMaterialized();
        testCollisionRetry();
        testBoundedRetryExhaustion();
        testTrueAllocationExhaustion();
        testType10FixedSerialExclusion();
        testPermanentNonReuse();
        testOwnerUniquenessAndIdempotentEnsure();
        testLazyProvisioningOrder();
        testSaveFailureAtomicity();
        testCorruptSnapshotFailClosed();
        testDuplicateNumberCorruption();
        testDuplicateOwnerCorruption();
        testIndexMismatchOrphan();
        testFixedOfficeOwnerCorruption();
        testBootstrapBlockedState();
        testStatusMutation();
        testExactLookupNoEnumeration();
        testDeterministicCodec();
        testRestartPersistence();
        testCapacityFailClosed();
        testNoEnumerationApi();
        testModuleContract();
        System.out.println("[FR-ID-001] Subject registry foundation validation passed");
    }

    // ------------------------------------------------------------------
    // acceptance: number contract (§19: valid generation / checksums /
    // parse / malformed / type-00 closed)
    // ------------------------------------------------------------------

    private static void testFixedNumberChecksums() {
        require(
                RegistryNumber.mod97(RegistryNumber.FIXED_PERSONAL_CANONICAL) == 1,
                "fixed personal number satisfies numeric mod 97 = 1"
        );
        require(
                RegistryNumber.mod97(RegistryNumber.FIXED_OFFICE_CANONICAL) == 1,
                "fixed office number satisfies numeric mod 97 = 1"
        );
        require(
                RegistryNumber.parse("1000000161").equals(RegistryNumber.FIXED_PERSONAL),
                "fixed personal canonical parses"
        );
        require(
                RegistryNumber.parse("0000000195").equals(RegistryNumber.FIXED_OFFICE),
                "fixed office canonical parses"
        );
    }

    private static void testNumberGenerationAndCheckDigits() {
        RegistryNumber number = RegistryNumber.forTypeAndSerial(SubjectType.NATURAL_PERSON, 42);
        require(number.canonical().length() == 10, "generated number is 10 digits");
        require(number.typeCode().equals("10"), "generated number keeps the type code");
        require(number.serial().equals("000042"), "serial is zero-padded to six digits");
        require(number.display().equals("10-000042-" + number.checkDigits()),
                "display form is TT-NNNNNN-CC");
        require(RegistryNumber.mod97(number.canonical()) == 1, "generated number passes MOD 97");
        require(
                RegistryNumber.parse(number.display()).equals(number),
                "display and canonical forms normalize to the same value"
        );
        require(
                RegistryNumber.forTypeAndSerial(SubjectType.NATURAL_PERSON, 0).serial()
                        .equals("000000"),
                "serial 000000 is representable"
        );
        require(
                RegistryNumber.forTypeAndSerial(SubjectType.NATURAL_PERSON, 999_999).serial()
                        .equals("999999"),
                "serial 999999 is representable"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> RegistryNumber.forTypeAndSerial(SubjectType.NATURAL_PERSON, 1_000_000),
                "serial out of range is rejected"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> RegistryNumber.forTypeAndSerial(SubjectType.NATURAL_PERSON, -1),
                "negative serial is rejected"
        );
    }

    private static void testNumberParsingCanonicalAndDisplay() {
        require(
                RegistryNumber.parse("10-000001-61").equals(RegistryNumber.FIXED_PERSONAL),
                "display form parses to the fixed personal canonical"
        );
        require(
                RegistryNumber.parse("00-000001-95").equals(RegistryNumber.FIXED_OFFICE),
                "display form parses to the fixed office canonical"
        );
        require(
                RegistryNumber.parse("1000000161").display().equals("10-000001-61"),
                "canonical form displays as TT-NNNNNN-CC"
        );
        // An altered digit fails the checksum.
        expectThrows(
                IllegalArgumentException.class,
                () -> RegistryNumber.parse("1000000162"),
                "altered check digit is rejected"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> RegistryNumber.parse("1000001161"),
                "altered serial digit is rejected"
        );
    }

    private static void testMalformedNumberRejection() {
        String[] malformed = {
                "", " ", " 1000000161", "1000000161 ", "100000016",
                "10000001611", "10-0000016", "10-000001-6", "10-00000-161",
                "10-00000161", "10000001-61", "10--000001-61", "10-000001-6a",
                "١٠٠٠٠٠٠١٦١", "１００００００１６１", "10-000001-61 ",
                "10_000001_61", "10.000001.61", "10000001a1", "ABCDEFGHIJ"
        };
        for (String input : malformed) {
            expectThrows(
                    IllegalArgumentException.class,
                    () -> RegistryNumber.parse(input),
                    "malformed input is rejected: '" + input + "'"
            );
        }
    }

    private static void testType00ClosedException() {
        // 00-000002-92 is MOD 97 valid (292 mod 97 == 1) but must be rejected:
        // type 00 accepts only the exact fixed office number.
        require(RegistryNumber.mod97("0000000292") == 1, "sanity: 00-000002-92 checksum is valid");
        expectThrows(
                IllegalArgumentException.class,
                () -> RegistryNumber.parse("0000000292"),
                "non-fixed type-00 number is rejected"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> RegistryNumber.parse("00-000002-92"),
                "non-fixed type-00 display form is rejected"
        );
        expectThrows(
                IllegalArgumentException.class,
                () -> RegistryNumber.forTypeAndSerial(
                        SubjectType.HYDRO_ARCHON_OFFICE,
                        2
                ),
                "type 00 has no ordinary allocation pool"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: fixed reservations from first snapshot + office subject
    // ------------------------------------------------------------------

    private static void testFixedReservationsFromFirstSnapshot() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        SubjectRegistryRepository repository = repository(store);
        SubjectRegistryStoreSnapshot snapshot = repository.snapshot();

        Reservation personal = snapshot.reservations().get(RegistryNumber.FIXED_PERSONAL);
        require(personal != null, "fixed personal number is reserved from the first snapshot");
        require(personal.boundSubjectId().isEmpty(),
                "fixed personal reservation is unbound (bootstrap BLOCKED)");
        Reservation office = snapshot.reservations().get(RegistryNumber.FIXED_OFFICE);
        require(office != null, "fixed office number is reserved from the first snapshot");
        require(office.boundSubjectId().isPresent(),
                "fixed office reservation is bound to the office subject");
        require(snapshot.bootstrapState().fixedReservationsEstablished(),
                "bootstrap state confirms fixed reservations");
    }

    private static void testOfficeSubjectMaterialized() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        SubjectRegistryRepository repository = repository(store);

        Optional<SubjectRecord> office = repository.findByNumber(RegistryNumber.FIXED_OFFICE);
        require(office.isPresent(), "office subject is materialized");
        SubjectRecord record = office.orElseThrow();
        require(record.subjectType() == SubjectType.HYDRO_ARCHON_OFFICE,
                "office subject type is HYDRO_ARCHON_OFFICE");
        require(record.ownerReference().equals(OwnerReference.HYDRO_ARCHON_OFFICE),
                "office subject is owned by the constant office reference");
        require(record.status() == SubjectStatus.ACTIVE, "office subject starts ACTIVE");
        require(record.revision() == 1, "office subject starts at revision 1");
        require(repository.snapshot().storeRevision() == 1L,
                "first snapshot commits exactly once");
    }

    // ------------------------------------------------------------------
    // acceptance: allocation (collision retry / bounded exhaustion /
    // true exhaustion / type-10 fixed serial exclusion / non-reuse)
    // ------------------------------------------------------------------

    private static void testCollisionRetry() {
        // serial 1 is the fixed personal serial and must be skipped; serial 42
        // is then allocated. The skipped candidate has no side effect.
        ScriptedSerialGenerator serials = new ScriptedSerialGenerator(1, 42);
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(2_000);
        SubjectRegistryRepository repository =
                repository(store, serials, UUID::randomUUID, clock);

        SubjectRecord record = provision(repository, ALPHA_ID, clock, 100);
        require(record.registryNumber().serial().equals("000042"),
                "collision candidate is skipped and the free candidate is allocated");
        require(repository.snapshot().storeRevision() == 2L,
                "skipped candidate does not consume a store revision");
    }

    private static void testBoundedRetryExhaustion() {
        // Always returns the fixed personal serial (1): bounded attempts run
        // out and the failure is explicit, with nothing published.
        ScriptedSerialGenerator serials = new ScriptedSerialGenerator(true, 1);
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(3_000);
        SubjectRegistryRepository repository =
                repository(store, serials, UUID::randomUUID, clock);

        SubjectRegistryUnavailableException failure = expectThrows(
                SubjectRegistryUnavailableException.class,
                () -> provision(repository, ALPHA_ID, clock, 100),
                "bounded retry exhaustion is explicit"
        );
        require(
                failure.failureCode().equals(
                        SubjectRegistryUnavailableException.CODE_ALLOCATION_RETRIES_EXHAUSTED
                ),
                "bounded exhaustion carries the stable code"
        );
        require(repository.size() == 1, "no subject is published on exhaustion");
        require(repository.snapshot().storeRevision() == 1L,
                "no revision change on exhaustion");
    }

    private static void testTrueAllocationExhaustion() {
        ScriptedSerialGenerator serials = new ScriptedSerialGenerator(-1);
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(4_000);
        SubjectRegistryRepository repository =
                repository(store, serials, UUID::randomUUID, clock);

        SubjectRegistryUnavailableException failure = expectThrows(
                SubjectRegistryUnavailableException.class,
                () -> provision(repository, ALPHA_ID, clock, 100),
                "generator exhaustion is an explicit failure"
        );
        require(
                failure.failureCode().equals(
                        SubjectRegistryUnavailableException.CODE_ALLOCATION_EXHAUSTED
                ),
                "generator exhaustion carries the stable code"
        );
        require(repository.size() == 1, "no subject is published on true exhaustion");
    }

    private static void testType10FixedSerialExclusion() {
        // Serial 000001 is permanently removed from the type-10 ordinary pool,
        // independent of the reservation index.
        ScriptedSerialGenerator serials = new ScriptedSerialGenerator(1, 1, 1, 7);
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(5_000);
        SubjectRegistryRepository repository =
                repository(store, serials, UUID::randomUUID, clock);

        SubjectRecord record = provision(repository, ALPHA_ID, clock, 100);
        require(
                !record.registryNumber().canonical().equals(
                        RegistryNumber.FIXED_PERSONAL_CANONICAL
                ),
                "type-10 ordinary allocation never returns/commits serial 000001"
        );
        require(record.registryNumber().serial().equals("000007"),
                "the first non-excluded candidate is used");
    }

    private static void testPermanentNonReuse() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(6_000);
        SubjectRegistryRepository repository =
                repository(store, new ScriptedSerialGenerator(101), UUID::randomUUID, clock);

        SubjectRecord first = provision(repository, ALPHA_ID, clock, 100);
        RegistryNumber revokedNumber = first.registryNumber();
        repository.updateStatus(first.subjectId(), SubjectStatus.REVOKED, clock.now());

        // A second allocation that would collide with the revoked number must
        // skip it and pick the next candidate.
        ScriptedSerialGenerator serials =
                new ScriptedSerialGenerator(101, 202);
        SubjectRegistryRepository secondRepo = restartRepository(
                store, serials, UUID::randomUUID, clock
        );
        SubjectRecord second = provision(secondRepo, BRAVO_ID, clock, 100);
        require(
                !second.registryNumber().equals(revokedNumber),
                "a revoked/dissolved number is never allocated again"
        );
        require(
                secondRepo.findByNumber(revokedNumber).isPresent(),
                "the revoked record remains as its own tombstone"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: one UUID one subject / idempotent ensure / lazy order
    // ------------------------------------------------------------------

    private static void testOwnerUniquenessAndIdempotentEnsure() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(7_000);
        SubjectRegistryRepository repository = repository(store);
        SubjectRegistryService service = service(repository, clock, presence(ALPHA_ID, BRAVO_ID));

        SubjectRecord first = service.ensurePlayerSubject(ALPHA_ID);
        clock.advance(250);
        SubjectRecord second = service.ensurePlayerSubject(ALPHA_ID);
        require(second.equals(first), "repeated ensure returns the same subject unchanged");
        require(second.revision() == first.revision(), "idempotent ensure changes no revision");
        require(repository.size() == 2, "one UUID maps to exactly one subject (plus office)");

        SubjectRecord bravo = service.ensurePlayerSubject(BRAVO_ID);
        require(!bravo.subjectId().equals(first.subjectId()),
                "different UUIDs get distinct subjects");
        require(repository.size() == 3, "two UUIDs create two subjects (plus office)");
        require(
                service.findSubjectForPlayer(ALPHA_ID).orElseThrow().equals(first),
                "player lookup returns the same subject"
        );
    }

    private static void testLazyProvisioningOrder() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(8_000);
        SubjectRegistryRepository repository = repository(store);
        FakePresence presence = presence(); // no players yet

        DefaultSubjectRegistryService service = new DefaultSubjectRegistryService(
                repository, clock, presence
        );
        SubjectRegistryUnavailableException absent = expectThrows(
                SubjectRegistryUnavailableException.class,
                () -> service.ensurePlayerSubject(ALPHA_ID),
                "provisioning requires an existing PlayerData record"
        );
        require(
                absent.failureCode().equals(
                        SubjectRegistryUnavailableException.CODE_PLAYER_NOT_PROVISIONED
                ),
                "absent player carries PLAYER_NOT_PROVISIONED"
        );
        require(repository.size() == 1, "no subject is created for an unprovisioned player");

        // PlayerData present, subject absent -> idempotent ensure succeeds once.
        presence.add(ALPHA_ID);
        clock.advance(100);
        SubjectRecord created = service.ensurePlayerSubject(ALPHA_ID);
        require(created.subjectType() == SubjectType.NATURAL_PERSON,
                "ensure creates a natural-person subject");
        require(repository.size() == 2, "subject is created when PlayerData is present");
        SubjectRecord again = service.ensurePlayerSubject(ALPHA_ID);
        require(again.equals(created), "repeated ensure after creation is idempotent");
    }

    // ------------------------------------------------------------------
    // acceptance: save-failure atomicity (durable gate)
    // ------------------------------------------------------------------

    private static void testSaveFailureAtomicity() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(9_000);
        SubjectRegistryRepository repository = repository(store);
        SubjectRegistryService service = service(repository, clock, presence(ALPHA_ID));

        SubjectRegistryStoreSnapshot before = repository.snapshot();
        store.setCommitFailureCode("INJECTED_FAILURE");
        SubjectRegistryUnavailableException failure = expectThrows(
                SubjectRegistryUnavailableException.class,
                () -> service.ensurePlayerSubject(ALPHA_ID),
                "durable-gate failure is surfaced"
        );
        require(
                failure.failureCode().equals(
                        SubjectRegistryUnavailableException.CODE_STORE_FAILURE
                ),
                "store failure carries the stable code"
        );
        SubjectRegistryStoreSnapshot after = repository.snapshot();
        require(after.equals(before), "failed commit publishes no subject or revision");
        require(repository.findByOwner(OwnerReference.forPlayer(ALPHA_ID)).isEmpty(),
                "failed commit leaves no owner index entry");

        // Injected store exception: same no-side-effect contract.
        store.setCommitFailureCode(null);
        store.setCommitException(new RuntimeException("injected store exception"));
        expectThrows(
                SubjectRegistryUnavailableException.class,
                () -> service.ensurePlayerSubject(ALPHA_ID),
                "store exception is surfaced as unavailability"
        );
        require(repository.snapshot().equals(before), "store exception leaves state unchanged");

        // Recovery: the same owner provisions once after the failure is lifted.
        store.setCommitException(null);
        clock.advance(100);
        SubjectRecord recovered = service.ensurePlayerSubject(ALPHA_ID);
        require(recovered.revision() == 1, "recovered provisioning is a fresh subject");
        require(repository.snapshot().storeRevision() == before.storeRevision() + 1,
                "revision advances only on success");
        SubjectRecord again = service.ensurePlayerSubject(ALPHA_ID);
        require(again.equals(recovered), "no duplicate subject after recovery");
    }

    // ------------------------------------------------------------------
    // acceptance: fail-closed load (corrupt / duplicate / orphan / fixed
    // office owner / bootstrap state)
    // ------------------------------------------------------------------

    private static void testCorruptSnapshotFailClosed() {
        CompoundTag corrupted = new CompoundTag();
        corrupted.putInt("StoreVersion", 1);
        corrupted.putLong("StoreRevision", 1);
        corrupted.put("Subjects", new CompoundTag());
        corrupted.put("Numbers", new CompoundTag());
        corrupted.put("Owners", new CompoundTag());
        corrupted.put("Reservations", new CompoundTag());
        corrupted.put("BootstrapState", new CompoundTag());
        // Missing StoreRevision types / missing fixed reservations -> rejected.
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        store.putRaw(corrupted);
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> repository(store),
                "a corrupt snapshot rejects the whole registry (fail closed)"
        );

        CompoundTag unknownField = corrupted.copy();
        unknownField.putString("Balance", "0");
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(unknownField),
                "unsupported fields are rejected"
        );

        // Unknown newer store version.
        CompoundTag futureVersion = corrupted.copy();
        futureVersion.putInt("StoreVersion", 99);
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(futureVersion),
                "unknown newer versions are rejected"
        );
    }

    private static void testDuplicateNumberCorruption() {
        CompoundTag encoded = initialEncoded();
        // Force two Subjects entries to claim the same number index by giving
        // the office record a second Subject entry under a fresh key.
        CompoundTag subjects = encoded.getCompound("Subjects");
        CompoundTag office = subjects.getCompound(
                encoded.getCompound("Numbers").getString("0000000195")
        ).copy();
        office.putUUID("SubjectId", UUID.fromString("11111111-1111-1111-1111-111111111111"));
        subjects.put("11111111-1111-1111-1111-111111111111", office);
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(encoded),
                "duplicate number claims are rejected"
        );
    }

    private static void testDuplicateOwnerCorruption() {
        CompoundTag encoded = initialEncoded();
        CompoundTag subjects = encoded.getCompound("Subjects");
        CompoundTag office = subjects.getCompound(
                encoded.getCompound("Numbers").getString("0000000195")
        ).copy();
        office.putUUID("SubjectId", UUID.fromString("22222222-2222-2222-2222-222222222222"));
        subjects.put("22222222-2222-2222-2222-222222222222", office);
        // The owners index still points at the original office subject id; the
        // new record's owner has no index entry -> orphan subject rejected.
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(encoded),
                "duplicate owner / missing index claims are rejected"
        );
    }

    private static void testIndexMismatchOrphan() {
        // Orphan number index: points to a missing subject.
        CompoundTag orphanNumber = initialEncoded();
        orphanNumber.getCompound("Numbers").putString(
                RegistryNumber.forTypeAndSerial(SubjectType.NATURAL_PERSON, 42).canonical(),
                UUID.randomUUID().toString()
        );
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(orphanNumber),
                "orphan number index is rejected"
        );

        // Orphan owner index: points to a missing subject.
        CompoundTag orphanOwner = initialEncoded();
        orphanOwner.getCompound("Owners").putString(
                OwnerReference.forPlayer(UUID.fromString("33333333-3333-3333-3333-333333333333")).key(),
                UUID.randomUUID().toString()
        );
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(orphanOwner),
                "orphan owner index is rejected"
        );

        // Mismatch: owner index key disagrees with the record's owner.
        CompoundTag mismatch = initialEncoded();
        String officeKey = mismatch.getCompound("Numbers").getString("0000000195");
        mismatch.getCompound("Owners").putString(
                OwnerReference.forPlayer(UUID.fromString("44444444-4444-4444-4444-444444444444")).key(),
                officeKey
        );
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(mismatch),
                "owner index key disagreement is rejected"
        );
    }

    private static void testFixedOfficeOwnerCorruption() {
        CompoundTag encoded = initialEncoded();
        String officeKey = encoded.getCompound("Numbers").getString("0000000195");
        CompoundTag office = encoded.getCompound("Subjects").getCompound(officeKey);
        office.getCompound("OwnerReference").putString("Kind", "PLAYER_UUID");
        office.getCompound("OwnerReference").putString("OwnerId", ALPHA_ID.toString());
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(encoded),
                "a fixed office number with a foreign owner is fatal corruption"
        );
    }

    private static void testBootstrapBlockedState() {
        // Original personal reservation bound to a subject -> BLOCKED state.
        CompoundTag boundPersonal = initialEncoded();
        CompoundTag reservations = boundPersonal.getCompound("Reservations");
        reservations.getCompound("1000000161").putUUID(
                "BoundSubjectId", UUID.randomUUID()
        );
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(boundPersonal),
                "an applied original personal binding is rejected (bootstrap BLOCKED)"
        );

        // BootstrapState claiming an applied original personal binding.
        CompoundTag claimed = initialEncoded();
        claimed.getCompound("BootstrapState").putBoolean(
                "OriginalPersonalBindingApplied", true
        );
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(claimed),
                "bootstrap state claiming the personal binding is rejected"
        );

        // A subject claiming the fixed personal number is fatal.
        CompoundTag claimedNumber = initialEncoded();
        claimedNumber.getCompound("Numbers").putString("1000000161", UUID.randomUUID().toString());
        expectThrows(
                SubjectRegistryNbtException.class,
                () -> new SubjectRegistryNbtCodec().decode(claimedNumber),
                "a subject claiming the fixed personal number is rejected"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: status mutation
    // ------------------------------------------------------------------

    private static void testStatusMutation() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(10_000);
        SubjectRegistryRepository repository = repository(store);
        SubjectRegistryService service = service(repository, clock, presence(ALPHA_ID));

        SubjectRecord created = service.ensurePlayerSubject(ALPHA_ID);
        long storeRevisionBefore = repository.snapshot().storeRevision();
        clock.advance(100);
        SubjectRecord revoked = service.updateStatus(
                created.subjectId(), SubjectStatus.REVOKED
        );
        require(revoked.revision() == created.revision() + 1,
                "status mutation increments the record revision exactly once");
        require(
                repository.snapshot().storeRevision() == storeRevisionBefore + 1,
                "status mutation increments the store revision exactly once"
        );
        require(revoked.subjectId().equals(created.subjectId()),
                "status mutation keeps the subject id");
        require(revoked.registryNumber().equals(created.registryNumber()),
                "status mutation keeps the number");
        require(revoked.ownerReference().equals(created.ownerReference()),
                "status mutation keeps the owner");
        require(revoked.status() == SubjectStatus.REVOKED, "status is replaced");

        // Same-status request is an idempotent no-op.
        SubjectRecord noOp = service.updateStatus(created.subjectId(), SubjectStatus.REVOKED);
        require(noOp.equals(revoked), "same-status request is a no-op");
        require(repository.snapshot().storeRevision() == storeRevisionBefore + 1,
                "same-status request changes no revision");

        // Non-active number resolves as KNOWN_NON_ACTIVE without private reason.
        PublicRoutingResult result = service.resolveExactRegistryNumber(
                created.registryNumber()
        );
        require(result.status() == RoutingStatus.KNOWN_NON_ACTIVE,
                "non-active number resolves as KNOWN_NON_ACTIVE");
        require(result.subject().isEmpty(), "no projection is disclosed for non-active");
    }

    // ------------------------------------------------------------------
    // acceptance: exact lookup / no enumeration
    // ------------------------------------------------------------------

    private static void testExactLookupNoEnumeration() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(11_000);
        SubjectRegistryRepository repository = repository(store);
        SubjectRegistryService service = service(repository, clock, presence(ALPHA_ID, BRAVO_ID));

        SubjectRecord alpha = service.ensurePlayerSubject(ALPHA_ID);

        PublicRoutingResult office = service.resolveExactRegistryNumber(
                RegistryNumber.FIXED_OFFICE
        );
        require(office.status() == RoutingStatus.ROUTABLE_ACTIVE,
                "fixed office number routes as active");
        require(office.subject().isPresent(), "routable result carries a projection");
        require(
                office.subject().orElseThrow().registryNumber().equals(RegistryNumber.FIXED_OFFICE),
                "projection carries the exact public number"
        );
        require(
                office.subject().orElseThrow().subjectType() == SubjectType.HYDRO_ARCHON_OFFICE,
                "projection carries the subject type"
        );

        RegistryNumber unknown = RegistryNumber.forTypeAndSerial(
                SubjectType.NATURAL_PERSON, 999_998
        );
        require(
                service.resolveExactRegistryNumber(unknown).status()
                        == RoutingStatus.UNKNOWN_OR_INVALID,
                "unknown exact number resolves as UNKNOWN_OR_INVALID"
        );
        require(
                service.resolveExactRegistryNumber(alpha.registryNumber()).status()
                        == RoutingStatus.ROUTABLE_ACTIVE,
                "provisioned number routes as active"
        );
        require(
                service.status(alpha.subjectId()).orElseThrow() == SubjectStatus.ACTIVE,
                "status lookup returns the current status"
        );
        require(
                service.findBySubjectId(alpha.subjectId()).orElseThrow().equals(alpha),
                "exact SubjectId lookup returns the record"
        );
    }

    private static void testNoEnumerationApi() {
        for (Method method : SubjectRegistryService.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            if (name.contains("list") || name.contains("all") || name.contains("prefix")
                    || name.contains("range") || name.contains("enumerate")
                    || name.contains("export") || name.contains("iterate")) {
                throw new AssertionError(
                        "SubjectRegistryService exposes a forbidden enumeration method "
                                + method.getName()
                );
            }
        }
        for (Method method : SubjectRegistryRepository.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            if (name.contains("list") || name.contains("prefix") || name.contains("range")
                    || name.contains("enumerate") || name.contains("export")
                    || name.contains("iterate")) {
                throw new AssertionError(
                        "SubjectRegistryRepository exposes a forbidden enumeration method "
                                + method.getName()
                );
            }
        }
    }

    // ------------------------------------------------------------------
    // acceptance: deterministic codec
    // ------------------------------------------------------------------

    private static void testDeterministicCodec() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(12_000);
        SubjectRegistryRepository repository = repository(store);
        SubjectRegistryService service = service(repository, clock, presence(ALPHA_ID));
        service.ensurePlayerSubject(ALPHA_ID);

        SubjectRegistryNbtCodec codec = new SubjectRegistryNbtCodec();
        SubjectRegistryStoreSnapshot snapshot = repository.snapshot();
        CompoundTag first = codec.encode(snapshot);
        CompoundTag second = codec.encode(snapshot);
        require(
                codec.encodedSize(first) == codec.encodedSize(second),
                "same snapshot encodes to the same serialized size"
        );
        require(
                codec.decode(second).equals(snapshot),
                "decode(encode(snapshot)) equals the snapshot"
        );
        require(
                codec.decode(codec.encode(snapshot)).equals(codec.decode(store.load())),
                "persisted form decodes equivalently to the in-memory snapshot"
        );
        require(
                codec.encodedSize(first) > 0,
                "encoded snapshot carries real payload bytes"
        );
    }

    // ------------------------------------------------------------------
    // acceptance: restart persistence
    // ------------------------------------------------------------------

    private static void testRestartPersistence() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock firstClock = new MutableClock(13_000);
        SubjectRegistryRepository firstRepo = repository(store);
        SubjectRegistryService firstService = service(
                firstRepo, firstClock, presence(ALPHA_ID)
        );
        SubjectRecord created = firstService.ensurePlayerSubject(ALPHA_ID);

        SubjectRegistryRepository restartedRepo = restartRepository(
                store, new ScriptedSerialGenerator(1), UUID::randomUUID, firstClock
        );
        SubjectRegistryService restartedService = service(
                restartedRepo, firstClock, presence(ALPHA_ID)
        );
        SubjectRecord reloaded = restartedService.findSubjectForPlayer(ALPHA_ID).orElseThrow();
        require(reloaded.equals(created),
                "same owner resolves the same subject and number after restart");
        require(restartedRepo.snapshot().storeRevision() == firstRepo.snapshot().storeRevision(),
                "restart preserves the store revision");
        require(
                restartedRepo.findByNumber(created.registryNumber()).isPresent(),
                "restart preserves the number index"
        );

        // The office subject survives restart and stays correctly bound.
        SubjectRecord office = restartedRepo.findByNumber(RegistryNumber.FIXED_OFFICE)
                .orElseThrow();
        require(office.ownerReference().equals(OwnerReference.HYDRO_ARCHON_OFFICE),
                "restart keeps the office owner binding");
    }

    // ------------------------------------------------------------------
    // acceptance: capacity fail closed
    // ------------------------------------------------------------------

    private static void testCapacityFailClosed() {
        SubjectRegistryLimits tight = new SubjectRegistryLimits(1, 8 * 1024 * 1024, 64);
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        MutableClock clock = new MutableClock(14_000);
        SubjectRegistryRepository repository = repository(
                store, tight, new ScriptedSerialGenerator(11), UUID::randomUUID, clock
        );
        // The office subject occupies the single budget slot.
        SubjectRegistryUnavailableException failure = expectThrows(
                SubjectRegistryUnavailableException.class,
                () -> provision(repository, ALPHA_ID, clock, 100),
                "subject capacity is enforced fail-closed"
        );
        require(
                failure.failureCode().equals(
                        SubjectRegistryUnavailableException.CODE_CAPACITY_EXCEEDED
                ),
                "capacity failure carries the stable code"
        );
        require(repository.size() == 1, "failed capacity provisioning publishes nothing");
    }

    // ------------------------------------------------------------------
    // module contract checks
    // ------------------------------------------------------------------

    private static void testModuleContract() {
        require(SubjectRegistryRepository.MODULE_DATA_KEY.equals("subject-registry"),
                "subject-registry owns exactly the approved namespace");
        require(SubjectRegistryModule.MODULE_ID.value().equals("subject-registry"),
                "subject-registry module id is 'subject-registry'");
        ModuleDefinition definition = new ModuleDefinition(
                SubjectRegistryModule.MODULE_ID,
                new ModuleMetadata("Subject Registry", "1.0.0", Optional.empty(), Optional.empty()),
                Set.of(PlayerDataModule.MODULE_ID),
                Set.of(),
                40,
                SubjectRegistryModule::new
        );
        require(
                definition.requiredDependencies().equals(Set.of(PlayerDataModule.MODULE_ID)),
                "subject-registry depends only on player-data"
        );
        for (ModuleId dependency : definition.requiredDependencies()) {
            String value = dependency.value();
            require(!value.contains("emg") && !value.contains("emergency"),
                    "subject-registry never depends on the FR-EMG namespace");
        }
        require(BootstrapState.OFFICE_MATERIALIZED.officeSubjectMaterialized(),
                "initial bootstrap state materializes the office subject");
        require(!BootstrapState.OFFICE_MATERIALIZED.originalPersonalBindingApplied(),
                "initial bootstrap state never claims the original personal binding");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static SubjectRegistryRepository repository(SubjectRegistryStore store) {
        return new SubjectRegistryRepository(
                store,
                new SubjectRegistryNbtCodec(),
                SubjectRegistryLimits.DEFAULT,
                new IncrementingSerialGenerator(42),
                UUID::randomUUID,
                new MutableClock(1_000)
        );
    }

    private static SubjectRegistryRepository repository(
            SubjectRegistryStore store,
            RegistrySerialGenerator serials,
            SubjectIdSource subjectIds,
            LongSupplier clock
    ) {
        return new SubjectRegistryRepository(
                store,
                new SubjectRegistryNbtCodec(),
                SubjectRegistryLimits.DEFAULT,
                serials,
                subjectIds,
                clock
        );
    }

    private static SubjectRegistryRepository repository(
            SubjectRegistryStore store,
            SubjectRegistryLimits limits,
            RegistrySerialGenerator serials,
            SubjectIdSource subjectIds,
            LongSupplier clock
    ) {
        return new SubjectRegistryRepository(
                store,
                new SubjectRegistryNbtCodec(),
                limits,
                serials,
                subjectIds,
                clock
        );
    }

    private static SubjectRegistryRepository restartRepository(
            SavedDataBackedTestStore store,
            RegistrySerialGenerator serials,
            SubjectIdSource subjectIds,
            LongSupplier clock
    ) {
        return repository(store.restart(), serials, subjectIds, clock);
    }

    private static SubjectRegistryService service(
            SubjectRegistryRepository repository,
            LongSupplier clock,
            PlayerPresence presence
    ) {
        return new DefaultSubjectRegistryService(repository, clock, presence);
    }

    private static SubjectRecord provision(
            SubjectRegistryRepository repository,
            UUID playerId,
            MutableClock clock,
            long advance
    ) {
        return repository.provision(
                OwnerReference.forPlayer(playerId),
                SubjectType.NATURAL_PERSON,
                clock.advanceAndGet(advance)
        );
    }

    private static FakePresence presence(UUID... players) {
        FakePresence presence = new FakePresence();
        for (UUID player : players) {
            presence.add(player);
        }
        return presence;
    }

    /** Encodes a freshly initialized valid snapshot for corruption tests. */
    private static CompoundTag initialEncoded() {
        SavedDataBackedTestStore store = new SavedDataBackedTestStore();
        repository(store);
        return store.load();
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

    private static final class SavedDataBackedTestStore implements SubjectRegistryStore {
        private ModSavedData savedData;
        private String commitFailureCode;
        private RuntimeException commitException;

        private SavedDataBackedTestStore() {
            this(new ModSavedData());
        }

        private SavedDataBackedTestStore(ModSavedData savedData) {
            this.savedData = savedData;
        }

        @Override
        public CompoundTag load() {
            return savedData.getModuleData(
                    SubjectRegistryRepository.MODULE_DATA_KEY
            ).copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            if (commitException != null) {
                throw commitException;
            }
            if (commitFailureCode != null) {
                return new DurableCommitResult(
                        DurableCommitStatus.FAILED,
                        SubjectRegistryRepository.MODULE_DATA_KEY,
                        0L,
                        0L,
                        commitFailureCode
                );
            }
            savedData.putModuleData(
                    SubjectRegistryRepository.MODULE_DATA_KEY,
                    snapshot.copy()
            );
            return new DurableCommitResult(
                    DurableCommitStatus.COMMITTED,
                    SubjectRegistryRepository.MODULE_DATA_KEY,
                    1L,
                    0L,
                    ""
            );
        }

        private void putRaw(CompoundTag raw) {
            savedData.putModuleData(SubjectRegistryRepository.MODULE_DATA_KEY, raw);
        }

        private void setCommitFailureCode(String failureCode) {
            this.commitFailureCode = failureCode;
        }

        private void setCommitException(RuntimeException exception) {
            this.commitException = exception;
        }

        private SavedDataBackedTestStore restart() {
            CompoundTag root = savedData.save(new CompoundTag());
            return new SavedDataBackedTestStore(ModSavedData.load(root));
        }
    }

    private static final class ScriptedSerialGenerator implements RegistrySerialGenerator {
        private final int[] serials;
        private final boolean repeatLast;
        private int index;

        private ScriptedSerialGenerator(int... serials) {
            this(false, serials);
        }

        private ScriptedSerialGenerator(boolean repeatLast, int... serials) {
            this.serials = serials;
            this.repeatLast = repeatLast;
        }

        @Override
        public int nextSerial() {
            if (index >= serials.length) {
                if (repeatLast && serials.length > 0) {
                    return serials[serials.length - 1];
                }
                return -1; // exhausted
            }
            return serials[index++];
        }
    }

    private static final class IncrementingSerialGenerator implements RegistrySerialGenerator {
        private int next;

        private IncrementingSerialGenerator(int start) {
            this.next = start;
        }

        @Override
        public int nextSerial() {
            return next++;
        }
    }

    private static final class MutableClock implements LongSupplier {
        private long now;

        private MutableClock(long now) {
            this.now = now;
        }

        private void advance(long milliseconds) {
            now += milliseconds;
        }

        private long advanceAndGet(long milliseconds) {
            now += milliseconds;
            return now;
        }

        private long now() {
            return now;
        }

        @Override
        public long getAsLong() {
            return now;
        }
    }

    private static final class FakePresence implements PlayerPresence {
        private final Set<UUID> players = new HashSet<>();
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
    }
}
