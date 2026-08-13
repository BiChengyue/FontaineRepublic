package com.fontainerepublic.server.registry.persistence;

import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.registry.model.BootstrapState;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.Reservation;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import com.fontainerepublic.server.registry.model.SubjectType;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Single-writer repository owning the {@code "subject-registry"} NBT namespace
 * (FR-ID-001-A §6.4).
 *
 * <p>This class is the sole production owner of
 * {@link DataManager#getModuleData(String)} /
 * {@link DataManager#commitModuleData(String, CompoundTag)} for the
 * {@code "subject-registry"} key. All mutations run on the logical server
 * owner thread; every mutation builds one complete immutable replacement
 * snapshot, increments {@code StoreRevision} once and each changed record
 * revision once, and publishes the new in-memory state only after the
 * FR-CORE-002 durable gate reports {@code COMMITTED}. A failed write has no
 * side effects: no subject, number, index, or revision change, and no
 * downstream event.</p>
 *
 * <p>On an empty namespace the repository initializes the first valid
 * snapshot: both Human-fixed numbers become {@link ReservationKind#FIXED}
 * reservations and the Hydro Archon office subject is materialized idempotently
 * from its constant {@code OFFICE_ID:HYDRO_ARCHON} owner reference. The
 * original personal subject ({@code 10-000001-61}) is <em>not</em> bound —
 * that requires the separately approved audited bootstrap design and any
 * snapshot claiming it is rejected fail-closed.</p>
 */
public final class SubjectRegistryRepository {

    /** Reserved module-data key for the subject-registry namespace. */
    public static final String MODULE_DATA_KEY = "subject-registry";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final SubjectRegistryStore store;
    private final SubjectRegistryNbtCodec codec;
    private final SubjectRegistryLimits limits;
    private final RegistrySerialGenerator serialGenerator;
    private final SubjectIdSource subjectIdSource;
    private final LongSupplier clock;
    private final Thread ownerThread;

    private final LinkedHashMap<SubjectId, SubjectRecord> subjects = new LinkedHashMap<>();
    private final LinkedHashMap<RegistryNumber, SubjectId> numbers = new LinkedHashMap<>();
    private final LinkedHashMap<OwnerReference, SubjectId> owners = new LinkedHashMap<>();
    private final LinkedHashMap<RegistryNumber, Reservation> reservations =
            new LinkedHashMap<>();

    private long storeRevision;
    private BootstrapState bootstrapState;

    /**
     * Creates a repository backed by the production {@link DataManager} store.
     */
    public static SubjectRegistryRepository createProduction(SubjectRegistryNbtCodec codec) {
        Objects.requireNonNull(codec, "codec");
        return new SubjectRegistryRepository(
                new DataManagerSubjectRegistryStore(),
                codec,
                SubjectRegistryLimits.DEFAULT,
                new SecureRandomRegistrySerialGenerator(),
                UUID::randomUUID,
                System::currentTimeMillis
        );
    }

    public SubjectRegistryRepository(
            SubjectRegistryStore store,
            SubjectRegistryNbtCodec codec,
            SubjectRegistryLimits limits,
            RegistrySerialGenerator serialGenerator,
            SubjectIdSource subjectIdSource,
            LongSupplier clock
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.serialGenerator = Objects.requireNonNull(serialGenerator, "serialGenerator");
        this.subjectIdSource = Objects.requireNonNull(subjectIdSource, "subjectIdSource");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ownerThread = Thread.currentThread();

        CompoundTag loaded = store.load().copy();
        if (loaded.isEmpty()) {
            initializeFresh();
        } else {
            SubjectRegistryStoreSnapshot snapshot = codec.decode(loaded);
            enforceLoadedCapacity(snapshot);
            publish(snapshot);
        }
    }

    // ------------------------------------------------------------------
    // read surface (exact lookups only; no enumeration API)
    // ------------------------------------------------------------------

    public Optional<SubjectRecord> findBySubjectId(SubjectId subjectId) {
        requireOwnerThread();
        return Optional.ofNullable(
                subjects.get(Objects.requireNonNull(subjectId, "subjectId"))
        );
    }

    public Optional<SubjectRecord> findByNumber(RegistryNumber number) {
        requireOwnerThread();
        Objects.requireNonNull(number, "number");
        SubjectId subjectId = numbers.get(number);
        return subjectId == null
                ? Optional.empty()
                : Optional.ofNullable(subjects.get(subjectId));
    }

    public Optional<SubjectId> findSubjectIdByNumber(RegistryNumber number) {
        requireOwnerThread();
        return Optional.ofNullable(
                numbers.get(Objects.requireNonNull(number, "number"))
        );
    }

    public Optional<SubjectRecord> findByOwner(OwnerReference owner) {
        requireOwnerThread();
        Objects.requireNonNull(owner, "owner");
        SubjectId subjectId = owners.get(owner);
        return subjectId == null
                ? Optional.empty()
                : Optional.ofNullable(subjects.get(subjectId));
    }

    public SubjectRecord requireSubject(SubjectId subjectId) {
        return findBySubjectId(subjectId).orElseThrow(
                () -> new IllegalArgumentException("Unknown subject id: " + subjectId)
        );
    }

    public int size() {
        requireOwnerThread();
        return subjects.size();
    }

    public SubjectRegistryStoreSnapshot snapshot() {
        requireOwnerThread();
        return new SubjectRegistryStoreSnapshot(
                SubjectRegistryStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision,
                subjects,
                numbers,
                owners,
                reservations,
                bootstrapState
        );
    }

    // ------------------------------------------------------------------
    // write surface
    // ------------------------------------------------------------------

    /**
     * Registers a new subject of the given owner/type with a freshly allocated
     * permanent number. Fails closed on capacity exhaustion, allocation
     * exhaustion, or a store rejection — nothing is published on failure.
     */
    public SubjectRecord provision(OwnerReference owner, SubjectType type, long timestamp) {
        requireOwnerThread();
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        if (findByOwner(owner).isPresent()) {
            throw new IllegalStateException(
                    "Owner already has a subject: " + owner.key()
            );
        }
        if (subjects.size() >= limits.maxSubjects()) {
            throw new SubjectRegistryUnavailableException(
                    SubjectRegistryUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Subject count would exceed the budget of "
                            + limits.maxSubjects()
            );
        }
        if (storeRevision == Long.MAX_VALUE) {
            throw new SubjectRegistryUnavailableException(
                    SubjectRegistryUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Subject-registry store revision space exhausted"
            );
        }

        SubjectId subjectId = allocateSubjectId();
        RegistryNumber number = allocateNumber(type);
        SubjectRecord record = new SubjectRecord(
                SubjectRecord.CURRENT_SCHEMA_VERSION,
                subjectId,
                number,
                type,
                owner,
                SubjectStatus.ACTIVE,
                1,
                timestamp,
                timestamp
        );

        LinkedHashMap<SubjectId, SubjectRecord> nextSubjects = new LinkedHashMap<>(subjects);
        nextSubjects.put(subjectId, record);
        LinkedHashMap<RegistryNumber, SubjectId> nextNumbers = new LinkedHashMap<>(numbers);
        nextNumbers.put(number, subjectId);
        LinkedHashMap<OwnerReference, SubjectId> nextOwners = new LinkedHashMap<>(owners);
        nextOwners.put(owner, subjectId);

        SubjectRegistryStoreSnapshot candidate = new SubjectRegistryStoreSnapshot(
                SubjectRegistryStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1,
                nextSubjects,
                nextNumbers,
                nextOwners,
                reservations,
                bootstrapState
        );
        commitAndPublish(candidate);
        return record;
    }

    /**
     * Replaces the status of an existing subject: same identity and number,
     * record and store revision +1 exactly once. Same-status requests are
     * idempotent no-ops.
     */
    public SubjectRecord updateStatus(
            SubjectId subjectId,
            SubjectStatus newStatus,
            long timestamp
    ) {
        requireOwnerThread();
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(newStatus, "newStatus");
        SubjectRecord current = requireSubject(subjectId);
        if (current.status() == newStatus) {
            return current;
        }
        if (storeRevision == Long.MAX_VALUE) {
            throw new SubjectRegistryUnavailableException(
                    SubjectRegistryUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Subject-registry store revision space exhausted"
            );
        }

        SubjectRecord updated = current.withStatus(newStatus, timestamp);
        LinkedHashMap<SubjectId, SubjectRecord> nextSubjects = new LinkedHashMap<>(subjects);
        nextSubjects.put(subjectId, updated);

        SubjectRegistryStoreSnapshot candidate = new SubjectRegistryStoreSnapshot(
                SubjectRegistryStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1,
                nextSubjects,
                numbers,
                owners,
                reservations,
                bootstrapState
        );
        commitAndPublish(candidate);
        return updated;
    }

    // ------------------------------------------------------------------
    // initialization
    // ------------------------------------------------------------------

    /**
     * Builds and durably commits the first valid snapshot: both fixed
     * reservations plus the materialized Hydro Archon office subject.
     */
    private void initializeFresh() {
        long now = now();
        SubjectId officeId = allocateSubjectId();
        SubjectRecord office = new SubjectRecord(
                SubjectRecord.CURRENT_SCHEMA_VERSION,
                officeId,
                RegistryNumber.FIXED_OFFICE,
                SubjectType.HYDRO_ARCHON_OFFICE,
                OwnerReference.HYDRO_ARCHON_OFFICE,
                SubjectStatus.ACTIVE,
                1,
                now,
                now
        );

        LinkedHashMap<SubjectId, SubjectRecord> initialSubjects = new LinkedHashMap<>();
        initialSubjects.put(officeId, office);
        LinkedHashMap<RegistryNumber, SubjectId> initialNumbers = new LinkedHashMap<>();
        initialNumbers.put(RegistryNumber.FIXED_OFFICE, officeId);
        LinkedHashMap<OwnerReference, SubjectId> initialOwners = new LinkedHashMap<>();
        initialOwners.put(OwnerReference.HYDRO_ARCHON_OFFICE, officeId);
        LinkedHashMap<RegistryNumber, Reservation> initialReservations =
                new LinkedHashMap<>();
        initialReservations.put(
                RegistryNumber.FIXED_PERSONAL,
                Reservation.unboundFixed(RegistryNumber.FIXED_PERSONAL)
        );
        initialReservations.put(
                RegistryNumber.FIXED_OFFICE,
                Reservation.fixedTo(RegistryNumber.FIXED_OFFICE, officeId)
        );

        SubjectRegistryStoreSnapshot initial = new SubjectRegistryStoreSnapshot(
                SubjectRegistryStoreSnapshot.CURRENT_STORE_VERSION,
                1L,
                initialSubjects,
                initialNumbers,
                initialOwners,
                initialReservations,
                BootstrapState.OFFICE_MATERIALIZED
        );
        commitAndPublish(initial);
        LOGGER.info(
                "[SubjectRegistry] Initialized first snapshot (storeRevision=1, officeSubject={})",
                officeId
        );
    }

    private void enforceLoadedCapacity(SubjectRegistryStoreSnapshot snapshot) {
        if (snapshot.subjects().size() > limits.maxSubjects()) {
            throw new SubjectRegistryUnavailableException(
                    SubjectRegistryUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Loaded subject count " + snapshot.subjects().size()
                            + " exceeds the budget of " + limits.maxSubjects()
            );
        }
    }

    // ------------------------------------------------------------------
    // allocation internals
    // ------------------------------------------------------------------

    private SubjectId allocateSubjectId() {
        for (int attempt = 0; attempt < limits.maxAllocationAttempts(); attempt++) {
            SubjectId candidate = SubjectId.of(subjectIdSource.nextUuid());
            if (!subjects.containsKey(candidate)) {
                return candidate;
            }
        }
        throw new SubjectRegistryUnavailableException(
                SubjectRegistryUnavailableException.CODE_SUBJECT_ID_EXHAUSTED,
                "SubjectId collision retries exhausted"
        );
    }

    private RegistryNumber allocateNumber(SubjectType type) {
        if (!type.isAllocatable()) {
            throw new SubjectRegistryUnavailableException(
                    SubjectRegistryUnavailableException.CODE_ALLOCATION_EXHAUSTED,
                    "Type " + type + " has no ordinary allocation pool"
            );
        }
        for (int attempt = 0; attempt < limits.maxAllocationAttempts(); attempt++) {
            int serial = serialGenerator.nextSerial();
            if (serial < 0 || serial > RegistrySerialGenerator.MAX_SERIAL) {
                throw new SubjectRegistryUnavailableException(
                        SubjectRegistryUnavailableException.CODE_ALLOCATION_EXHAUSTED,
                        "Serial generator is exhausted"
                );
            }
            // Type 10 permanently removes serial 000001 (the fixed personal
            // number) from its ordinary pool, independent of the reservation
            // index, so ordinary allocation can never produce or commit it.
            if (type == SubjectType.NATURAL_PERSON && serial == 1) {
                continue;
            }
            RegistryNumber candidate = RegistryNumber.forTypeAndSerial(type, serial);
            if (reservations.containsKey(candidate)) {
                continue;
            }
            if (numbers.containsKey(candidate)) {
                continue;
            }
            return candidate;
        }
        throw new SubjectRegistryUnavailableException(
                SubjectRegistryUnavailableException.CODE_ALLOCATION_RETRIES_EXHAUSTED,
                "Bounded allocation retries exhausted after "
                        + limits.maxAllocationAttempts() + " attempts"
        );
    }

    // ------------------------------------------------------------------
    // commit + publish
    // ------------------------------------------------------------------

    private void commitAndPublish(SubjectRegistryStoreSnapshot candidate) {
        CompoundTag encoded = codec.encode(candidate);
        int bytes = codec.encodedSize(encoded);
        if (bytes > limits.maxTotalBytes()) {
            throw new SubjectRegistryUnavailableException(
                    SubjectRegistryUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Registry namespace would exceed the byte budget (" + bytes
                            + " > " + limits.maxTotalBytes() + ")"
            );
        }

        DurableCommitResult result;
        try {
            result = store.commit(encoded);
        } catch (RuntimeException failure) {
            throw new SubjectRegistryUnavailableException(
                    SubjectRegistryUnavailableException.CODE_STORE_FAILURE,
                    "Subject-registry store rejected a commit: " + failure.getMessage(),
                    failure
            );
        }
        if (result.status() != DurableCommitStatus.COMMITTED) {
            throw new SubjectRegistryUnavailableException(
                    SubjectRegistryUnavailableException.CODE_STORE_FAILURE,
                    "Durable commit of subject-registry failed (status="
                            + result.status() + ", code=" + result.failureCode() + ")"
            );
        }
        publish(candidate);
    }

    /** Swaps in a store-accepted candidate; only reachable on COMMITTED. */
    private void publish(SubjectRegistryStoreSnapshot snapshot) {
        subjects.clear();
        subjects.putAll(snapshot.subjects());
        numbers.clear();
        snapshot.numbers().forEach((number, subjectId) -> numbers.put(number, subjectId));
        owners.clear();
        snapshot.owners().forEach((owner, subjectId) -> owners.put(owner, subjectId));
        reservations.clear();
        snapshot.reservations().forEach(
                (number, reservation) -> reservations.put(number, reservation)
        );
        storeRevision = snapshot.storeRevision();
        bootstrapState = snapshot.bootstrapState();
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private long now() {
        long value = clock.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("clock returned a negative timestamp");
        }
        return value;
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "SubjectRegistryRepository may only be accessed from its owning server thread"
            );
        }
    }

    // ------------------------------------------------------------------
    // production store
    // ------------------------------------------------------------------

    private static final class DataManagerSubjectRegistryStore implements SubjectRegistryStore {
        @Override
        public CompoundTag load() {
            return DataManager.getModuleData(MODULE_DATA_KEY).copy();
        }

        @Override
        public DurableCommitResult commit(CompoundTag snapshot) {
            return DataManager.commitModuleData(
                    MODULE_DATA_KEY,
                    Objects.requireNonNull(snapshot, "snapshot").copy()
            );
        }
    }
}
