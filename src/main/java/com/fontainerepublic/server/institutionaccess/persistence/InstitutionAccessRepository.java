package com.fontainerepublic.server.institutionaccess.persistence;

import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.institutionaccess.model.Facility;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.FacilityState;
import com.fontainerepublic.server.institutionaccess.model.InstitutionType;
import com.fontainerepublic.server.institutionaccess.model.Terminal;
import com.fontainerepublic.server.institutionaccess.model.TerminalId;
import com.fontainerepublic.server.institutionaccess.model.TerminalPosition;
import com.fontainerepublic.server.institutionaccess.model.TerminalState;
import com.fontainerepublic.server.land.model.ParcelId;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Single-writer repository owning the {@code "institution-access"} NBT
 * namespace (FR-INST-002-A §3).
 *
 * <p>This class is the sole production owner of
 * {@link DataManager#getModuleData(String)} /
 * {@link DataManager#commitModuleData(String, CompoundTag)} for the
 * {@code "institution-access"} key. All mutations run on the logical server
 * owner thread; every mutation builds one complete immutable replacement
 * snapshot, increments {@code StoreRevision} once and each changed record
 * revision once, and publishes the new in-memory state only after the
 * FR-CORE-002 durable gate reports {@code COMMITTED}. A failed write has no
 * side effects: no facility, no terminal, no revision change, and no
 * downstream event.</p>
 *
 * <p>On an empty namespace the repository starts with a fresh, empty store
 * (revision 0). A present namespace is decoded strictly and fail-closed:
 * unknown fields, inconsistent ids, shared parcels or anchored positions, or
 * newer versions reject the whole load. Exact lookups only — no bulk
 * enumeration API is exposed.</p>
 */
public final class InstitutionAccessRepository {

    /** Reserved module-data key for the institution-access namespace. */
    public static final String MODULE_DATA_KEY = "institution-access";

    private static final int MAX_FACILITY_ID_ATTEMPTS = 8;
    private static final int MAX_TERMINAL_ID_ATTEMPTS = 8;

    private final InstitutionAccessStore store;
    private final InstitutionAccessNbtCodec codec;
    private final InstitutionAccessLimits limits;
    private final Supplier<UUID> facilityIdSource;
    private final Supplier<UUID> terminalIdSource;
    private final Thread ownerThread;

    private final LinkedHashMap<FacilityId, Facility> facilities = new LinkedHashMap<>();
    private final LinkedHashMap<TerminalId, Terminal> terminals = new LinkedHashMap<>();
    private long storeRevision;

    /**
     * Creates a repository backed by the production {@link DataManager} store.
     */
    public static InstitutionAccessRepository createProduction(
            InstitutionAccessNbtCodec codec
    ) {
        Objects.requireNonNull(codec, "codec");
        return new InstitutionAccessRepository(
                new DataManagerInstitutionAccessStore(),
                codec,
                InstitutionAccessLimits.DEFAULT,
                UUID::randomUUID,
                UUID::randomUUID
        );
    }

    public InstitutionAccessRepository(
            InstitutionAccessStore store,
            InstitutionAccessNbtCodec codec,
            InstitutionAccessLimits limits,
            Supplier<UUID> facilityIdSource,
            Supplier<UUID> terminalIdSource
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.facilityIdSource = Objects.requireNonNull(facilityIdSource, "facilityIdSource");
        this.terminalIdSource = Objects.requireNonNull(terminalIdSource, "terminalIdSource");
        this.ownerThread = Thread.currentThread();

        CompoundTag loaded = store.load().copy();
        if (loaded.isEmpty()) {
            this.storeRevision = 0L;
        } else {
            InstitutionAccessStoreSnapshot snapshot = codec.decode(loaded);
            enforceLoadedCapacity(snapshot);
            publish(snapshot);
        }
    }

    // ------------------------------------------------------------------
    // read surface (exact lookups only; no enumeration API)
    // ------------------------------------------------------------------

    public Optional<Facility> findByFacilityId(FacilityId facilityId) {
        requireOwnerThread();
        return Optional.ofNullable(
                facilities.get(Objects.requireNonNull(facilityId, "facilityId"))
        );
    }

    public Facility requireFacility(FacilityId facilityId) {
        return findByFacilityId(facilityId).orElseThrow(
                () -> new InstitutionAccessUnavailableException(
                        InstitutionAccessUnavailableException.CODE_FACILITY_NOT_FOUND,
                        "No facility exists for " + facilityId
                )
        );
    }

    public Optional<Terminal> findByTerminalId(TerminalId terminalId) {
        requireOwnerThread();
        return Optional.ofNullable(
                terminals.get(Objects.requireNonNull(terminalId, "terminalId"))
        );
    }

    public Terminal requireTerminal(TerminalId terminalId) {
        return findByTerminalId(terminalId).orElseThrow(
                () -> new InstitutionAccessUnavailableException(
                        InstitutionAccessUnavailableException.CODE_TERMINAL_NOT_FOUND,
                        "No terminal exists for " + terminalId
                )
        );
    }

    /** The facility currently bound to a parcel, if any. */
    public Optional<Facility> findByParcelId(ParcelId parcelId) {
        requireOwnerThread();
        Objects.requireNonNull(parcelId, "parcelId");
        return facilities.values().stream()
                .filter(facility -> facility.parcelId().equals(parcelId))
                .findFirst();
    }

    /** The terminal anchored at the given position, if any (anti-clone). */
    public Optional<Terminal> findByPosition(String dimension, int x, int y, int z) {
        requireOwnerThread();
        Objects.requireNonNull(dimension, "dimension");
        return terminals.values().stream()
                .filter(terminal -> terminal.position().dimension().equals(dimension)
                        && terminal.position().x() == x
                        && terminal.position().y() == y
                        && terminal.position().z() == z)
                .findFirst();
    }

    public int size() {
        requireOwnerThread();
        return facilities.size();
    }

    public int terminalCount() {
        requireOwnerThread();
        return terminals.size();
    }

    public int terminalCountOf(FacilityId facilityId) {
        requireOwnerThread();
        Objects.requireNonNull(facilityId, "facilityId");
        return (int) terminals.values().stream()
                .filter(terminal -> terminal.facilityId().equals(facilityId))
                .count();
    }

    public InstitutionAccessStoreSnapshot snapshot() {
        requireOwnerThread();
        return new InstitutionAccessStoreSnapshot(
                InstitutionAccessStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision,
                facilities,
                terminals
        );
    }

    // ------------------------------------------------------------------
    // write surface
    // ------------------------------------------------------------------

    /**
     * Registers a facility bound to a FR-LAND parcel: server-assigned
     * immutable id, state ACTIVE, revision 1. One complete replacement
     * snapshot; committed only after the gate. The caller must have verified
     * the parcel exists and is unbound.
     */
    public Facility registerFacility(
            InstitutionType institutionType,
            ParcelId parcelId
    ) {
        requireOwnerThread();
        Objects.requireNonNull(institutionType, "institutionType");
        Objects.requireNonNull(parcelId, "parcelId");
        if (facilities.size() >= limits.maxFacilities()) {
            throw capacity("Facility count would exceed the budget of "
                    + limits.maxFacilities());
        }
        requireStoreRevisionSpace();

        FacilityId facilityId = assignFacilityId();
        Facility facility = new Facility(
                Facility.CURRENT_SCHEMA_VERSION,
                facilityId,
                institutionType,
                parcelId,
                FacilityState.ACTIVE,
                1
        );
        LinkedHashMap<FacilityId, Facility> next = new LinkedHashMap<>(facilities);
        next.put(facilityId, facility);
        commitAndPublish(new InstitutionAccessStoreSnapshot(
                InstitutionAccessStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1,
                next,
                terminals
        ));
        return facility;
    }

    /**
     * Suspends a facility: {@code ACTIVE}/{@code RELOCATING} become
     * {@code SUSPENDED} (no-op when already suspended; {@code DISABLED} is
     * rejected). Revision +1 exactly once on change.
     */
    public Facility suspendFacility(FacilityId facilityId) {
        requireOwnerThread();
        Facility current = requireFacility(facilityId);
        if (current.state() == FacilityState.SUSPENDED) {
            return current;
        }
        rejectDisabled(current);
        return replaceAndPublish(current.withState(FacilityState.SUSPENDED));
    }

    /**
     * Activates a facility: {@code SUSPENDED}/{@code RELOCATING} become
     * {@code ACTIVE} (no-op when already active; {@code DISABLED} is
     * rejected). Revision +1 exactly once on change.
     */
    public Facility activateFacility(FacilityId facilityId) {
        requireOwnerThread();
        Facility current = requireFacility(facilityId);
        if (current.state() == FacilityState.ACTIVE) {
            return current;
        }
        rejectDisabled(current);
        return replaceAndPublish(current.withState(FacilityState.ACTIVE));
    }

    /**
     * Relocates a facility onto a new FR-LAND parcel: the facility enters
     * {@code RELOCATING} with the new parcel in one commit (controlled
     * relocation, FR-INST-001-A §6.1). {@code DISABLED} is rejected. The
     * caller must have verified the new parcel exists and is unbound.
     * Revision +1 exactly once.
     */
    public Facility relocateFacility(FacilityId facilityId, ParcelId newParcelId) {
        requireOwnerThread();
        Objects.requireNonNull(newParcelId, "newParcelId");
        Facility current = requireFacility(facilityId);
        rejectDisabled(current);
        Facility relocated = current.relocateTo(newParcelId);
        return replaceAndPublish(relocated);
    }

    /**
     * Disables a facility (terminal state; idempotent when already disabled).
     * Revision +1 exactly once on change. All anchored contexts are
     * invalidated by the caller on revision change.
     */
    public Facility disableFacility(FacilityId facilityId) {
        requireOwnerThread();
        Facility current = requireFacility(facilityId);
        if (current.state() == FacilityState.DISABLED) {
            return current;
        }
        return replaceAndPublish(current.withState(FacilityState.DISABLED));
    }

    /**
     * Registers a terminal anchored to a facility: server-assigned immutable
     * id, state ACTIVE, revision 1, integrity bound to the anchored position.
     * One complete replacement snapshot; committed only after the gate. The
     * caller must have verified facility existence/type/region and position
     * uniqueness.
     */
    public Terminal registerTerminal(
            FacilityId facilityId,
            InstitutionType institutionType,
            TerminalPosition position,
            Set<com.fontainerepublic.server.institutionaccess.model.CapabilityClass> capabilitySet,
            boolean secure
    ) {
        requireOwnerThread();
        Objects.requireNonNull(facilityId, "facilityId");
        Objects.requireNonNull(institutionType, "institutionType");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(capabilitySet, "capabilitySet");
        if (terminals.size() >= limits.maxTerminals()) {
            throw capacity("Terminal count would exceed the budget of "
                    + limits.maxTerminals());
        }
        if (terminalCountOf(facilityId) >= limits.maxTerminalsPerFacility()) {
            throw capacity("Terminal count of " + facilityId
                    + " would exceed the budget of "
                    + limits.maxTerminalsPerFacility());
        }
        requireStoreRevisionSpace();

        TerminalId terminalId = assignTerminalId();
        Terminal terminal = new Terminal(
                Terminal.CURRENT_SCHEMA_VERSION,
                terminalId,
                facilityId,
                institutionType,
                position,
                capabilitySet,
                TerminalState.ACTIVE,
                secure,
                position.integrityDigest(),
                1
        );
        LinkedHashMap<TerminalId, Terminal> next = new LinkedHashMap<>(terminals);
        next.put(terminalId, terminal);
        commitAndPublish(new InstitutionAccessStoreSnapshot(
                InstitutionAccessStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1,
                facilities,
                next
        ));
        return terminal;
    }

    /**
     * Suspends a terminal: {@code ACTIVE} becomes {@code SUSPENDED} (no-op
     * when already suspended; {@code DISABLED} is rejected). Revision +1
     * exactly once on change.
     */
    public Terminal suspendTerminal(TerminalId terminalId) {
        requireOwnerThread();
        Terminal current = requireTerminal(terminalId);
        if (current.state() == TerminalState.SUSPENDED) {
            return current;
        }
        if (current.state() == TerminalState.DISABLED) {
            throw invalidTransition(
                    "Cannot suspend a disabled terminal " + terminalId
            );
        }
        return replaceTerminalAndPublish(current.withState(TerminalState.SUSPENDED));
    }

    /**
     * Disables a terminal (terminal state; idempotent when already disabled).
     * Revision +1 exactly once on change. Anchored contexts are invalidated
     * by the caller on revision change.
     */
    public Terminal disableTerminal(TerminalId terminalId) {
        requireOwnerThread();
        Terminal current = requireTerminal(terminalId);
        if (current.state() == TerminalState.DISABLED) {
            return current;
        }
        return replaceTerminalAndPublish(current.withState(TerminalState.DISABLED));
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private Facility replaceAndPublish(Facility updated) {
        LinkedHashMap<FacilityId, Facility> next = new LinkedHashMap<>(facilities);
        next.put(updated.facilityId(), updated);
        commitAndPublish(new InstitutionAccessStoreSnapshot(
                InstitutionAccessStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1,
                next,
                terminals
        ));
        return updated;
    }

    private Terminal replaceTerminalAndPublish(Terminal updated) {
        LinkedHashMap<TerminalId, Terminal> next = new LinkedHashMap<>(terminals);
        next.put(updated.terminalId(), updated);
        commitAndPublish(new InstitutionAccessStoreSnapshot(
                InstitutionAccessStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision + 1,
                facilities,
                next
        ));
        return updated;
    }

    private void rejectDisabled(Facility facility) {
        if (facility.state() == FacilityState.DISABLED) {
            throw invalidTransition(
                    "Facility " + facility.facilityId()
                            + " is disabled and cannot be changed"
            );
        }
    }

    private FacilityId assignFacilityId() {
        for (int attempt = 0; attempt < MAX_FACILITY_ID_ATTEMPTS; attempt++) {
            UUID candidate = facilityIdSource.get();
            if (candidate == null) {
                throw new IllegalStateException("FacilityId source returned null");
            }
            FacilityId facilityId = FacilityId.of(candidate);
            if (!facilities.containsKey(facilityId)) {
                return facilityId;
            }
        }
        throw new InstitutionAccessUnavailableException(
                InstitutionAccessUnavailableException.CODE_CAPACITY_EXCEEDED,
                "Unable to allocate a fresh facility id after "
                        + MAX_FACILITY_ID_ATTEMPTS + " attempts"
        );
    }

    private TerminalId assignTerminalId() {
        for (int attempt = 0; attempt < MAX_TERMINAL_ID_ATTEMPTS; attempt++) {
            UUID candidate = terminalIdSource.get();
            if (candidate == null) {
                throw new IllegalStateException("TerminalId source returned null");
            }
            TerminalId terminalId = TerminalId.of(candidate);
            if (!terminals.containsKey(terminalId)) {
                return terminalId;
            }
        }
        throw new InstitutionAccessUnavailableException(
                InstitutionAccessUnavailableException.CODE_CAPACITY_EXCEEDED,
                "Unable to allocate a fresh terminal id after "
                        + MAX_TERMINAL_ID_ATTEMPTS + " attempts"
        );
    }

    private void requireStoreRevisionSpace() {
        if (storeRevision == Long.MAX_VALUE) {
            throw capacity("Institution-access store revision space exhausted");
        }
    }

    private InstitutionAccessUnavailableException capacity(String message) {
        return new InstitutionAccessUnavailableException(
                InstitutionAccessUnavailableException.CODE_CAPACITY_EXCEEDED,
                message
        );
    }

    private InstitutionAccessUnavailableException invalidTransition(String message) {
        return new InstitutionAccessUnavailableException(
                InstitutionAccessUnavailableException.CODE_INVALID_STATE_TRANSITION,
                message
        );
    }

    private void commitAndPublish(InstitutionAccessStoreSnapshot candidate) {
        CompoundTag encoded = codec.encode(candidate);
        int bytes = codec.encodedSize(encoded);
        if (bytes > limits.maxTotalBytes()) {
            throw capacity("Institution-access namespace would exceed the byte budget ("
                    + bytes + " > " + limits.maxTotalBytes() + ")");
        }

        DurableCommitResult result;
        try {
            result = store.commit(encoded);
        } catch (RuntimeException failure) {
            throw new InstitutionAccessUnavailableException(
                    InstitutionAccessUnavailableException.CODE_STORE_FAILURE,
                    "Institution-access store rejected a commit: "
                            + failure.getMessage(),
                    failure
            );
        }
        if (result.status() != DurableCommitStatus.COMMITTED) {
            throw new InstitutionAccessUnavailableException(
                    InstitutionAccessUnavailableException.CODE_STORE_FAILURE,
                    "Durable commit of institution-access failed (status="
                            + result.status() + ", code=" + result.failureCode() + ")"
            );
        }
        publish(candidate);
    }

    /** Swaps in a store-accepted candidate; only reachable on COMMITTED. */
    private void publish(InstitutionAccessStoreSnapshot snapshot) {
        facilities.clear();
        facilities.putAll(snapshot.facilities());
        terminals.clear();
        terminals.putAll(snapshot.terminals());
        storeRevision = snapshot.storeRevision();
    }

    private void enforceLoadedCapacity(InstitutionAccessStoreSnapshot snapshot) {
        if (snapshot.facilities().size() > limits.maxFacilities()) {
            throw capacity("Loaded facility count " + snapshot.facilities().size()
                    + " exceeds the budget of " + limits.maxFacilities());
        }
        if (snapshot.terminals().size() > limits.maxTerminals()) {
            throw capacity("Loaded terminal count " + snapshot.terminals().size()
                    + " exceeds the budget of " + limits.maxTerminals());
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "InstitutionAccessRepository may only be accessed from "
                            + "its owning server thread"
            );
        }
    }

    // ------------------------------------------------------------------
    // production store
    // ------------------------------------------------------------------

    private static final class DataManagerInstitutionAccessStore
            implements InstitutionAccessStore {
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
