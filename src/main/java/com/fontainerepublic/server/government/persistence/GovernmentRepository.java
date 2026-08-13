package com.fontainerepublic.server.government.persistence;

import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.government.model.GovernmentPosition;
import com.fontainerepublic.server.government.model.Ministry;
import com.fontainerepublic.server.government.model.MinistryId;
import com.fontainerepublic.server.government.model.MinistryState;
import com.fontainerepublic.server.government.model.Office;
import com.fontainerepublic.server.government.model.PositionId;
import com.fontainerepublic.server.government.model.PositionState;
import com.fontainerepublic.server.registry.model.OwnerReference;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Single-writer repository owning the {@code "government"} NBT namespace
 * (FR-GOV-001-A §3.4/§4).
 *
 * <p>This class is the sole production owner of
 * {@link DataManager#getModuleData(String)} /
 * {@link DataManager#commitModuleData(String, CompoundTag)} for the
 * {@code "government"} key. All mutations run on the logical server owner
 * thread; every mutation builds one complete immutable replacement snapshot,
 * increments {@code StoreRevision} once and each changed record revision once,
 * and publishes the new in-memory state only after the FR-CORE-002 durable
 * gate reports {@code COMMITTED}. A failed write has no side effects: no
 * ministry, position, office, holder change, or revision, and no downstream
 * event.</p>
 *
 * <p>On an empty namespace the repository starts with a fresh, empty store
 * (revision 0). A present namespace is decoded strictly and fail-closed:
 * unknown fields, newer versions, dangling ministry references, or
 * holder/office inconsistencies reject the whole load. Exact lookups and
 * bounded projections only — no bulk enumeration API is exposed.</p>
 */
public final class GovernmentRepository {

    /** Reserved module-data key for the government namespace. */
    public static final String MODULE_DATA_KEY = "government";

    private final GovernmentStore store;
    private final GovernmentNbtCodec codec;
    private final GovernmentLimits limits;
    private final GovernmentIdSource idSource;
    private final Thread ownerThread;

    private final LinkedHashMap<MinistryId, Ministry> ministries = new LinkedHashMap<>();
    private final LinkedHashMap<PositionId, GovernmentPosition> positions = new LinkedHashMap<>();
    private final LinkedHashMap<PositionId, Office> offices = new LinkedHashMap<>();
    private long storeRevision;

    /**
     * Creates a repository backed by the production {@link DataManager} store.
     */
    public static GovernmentRepository createProduction(GovernmentNbtCodec codec) {
        Objects.requireNonNull(codec, "codec");
        return new GovernmentRepository(
                new DataManagerGovernmentStore(),
                codec,
                GovernmentLimits.DEFAULT,
                new UuidGovernmentIdSource()
        );
    }

    public GovernmentRepository(
            GovernmentStore store,
            GovernmentNbtCodec codec,
            GovernmentLimits limits,
            GovernmentIdSource idSource
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.idSource = Objects.requireNonNull(idSource, "idSource");
        this.ownerThread = Thread.currentThread();

        CompoundTag loaded = store.load().copy();
        if (loaded.isEmpty()) {
            this.storeRevision = 0L;
        } else {
            GovernmentStoreSnapshot snapshot = codec.decode(loaded);
            enforceLoadedCapacity(snapshot);
            publish(snapshot);
        }
    }

    // ------------------------------------------------------------------
    // read surface (exact lookups and bounded projections only)
    // ------------------------------------------------------------------

    public Optional<Ministry> findMinistry(MinistryId ministryId) {
        requireOwnerThread();
        return Optional.ofNullable(
                ministries.get(Objects.requireNonNull(ministryId, "ministryId"))
        );
    }

    public Ministry requireMinistry(MinistryId ministryId) {
        return findMinistry(ministryId).orElseThrow(
                () -> new GovernmentUnavailableException(
                        GovernmentUnavailableException.CODE_MINISTRY_NOT_FOUND,
                        "No ministry " + ministryId
                )
        );
    }

    public Optional<GovernmentPosition> findPosition(PositionId positionId) {
        requireOwnerThread();
        return Optional.ofNullable(
                positions.get(Objects.requireNonNull(positionId, "positionId"))
        );
    }

    public GovernmentPosition requirePosition(PositionId positionId) {
        return findPosition(positionId).orElseThrow(
                () -> new GovernmentUnavailableException(
                        GovernmentUnavailableException.CODE_POSITION_NOT_FOUND,
                        "No position " + positionId
                )
        );
    }

    public Optional<Office> findOfficeByPosition(PositionId positionId) {
        requireOwnerThread();
        return Optional.ofNullable(
                offices.get(Objects.requireNonNull(positionId, "positionId"))
        );
    }

    public int ministryCount() {
        requireOwnerThread();
        return ministries.size();
    }

    public int positionCount() {
        requireOwnerThread();
        return positions.size();
    }

    public int officeCount() {
        requireOwnerThread();
        return offices.size();
    }

    public GovernmentStoreSnapshot snapshot() {
        requireOwnerThread();
        return new GovernmentStoreSnapshot(
                GovernmentStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision,
                ministries,
                positions,
                offices
        );
    }

    // ------------------------------------------------------------------
    // write surface (one complete snapshot per mutation)
    // ------------------------------------------------------------------

    /**
     * Creates a ministry in {@link MinistryState#ACTIVE} with revision 1 and
     * a server-assigned immutable id: one complete replacement snapshot,
     * store revision +1 exactly once. Fails closed on capacity exhaustion or
     * a store rejection — nothing is published on failure.
     */
    public Ministry createMinistry(String name) {
        requireOwnerThread();
        Objects.requireNonNull(name, "name");
        if (ministries.size() >= limits.maxMinistries()) {
            throw capacity("Ministry count would exceed the budget of "
                    + limits.maxMinistries());
        }
        requireStoreRevisionSpace();

        Ministry ministry = new Ministry(
                Ministry.CURRENT_SCHEMA_VERSION,
                MinistryId.of(idSource.nextUuid()),
                name,
                MinistryState.ACTIVE,
                1
        );
        LinkedHashMap<MinistryId, Ministry> next = new LinkedHashMap<>(ministries);
        next.put(ministry.ministryId(), ministry);
        commitAndPublish(
                new GovernmentStoreSnapshot(
                        GovernmentStoreSnapshot.CURRENT_STORE_VERSION,
                        storeRevision + 1,
                        next,
                        positions,
                        offices
                )
        );
        return ministry;
    }

    /**
     * Creates a VACANT position bound to an existing ministry with revision 1
     * and a server-assigned immutable id: one complete replacement snapshot,
     * store revision +1 exactly once. The ministry must exist (caller
     * enforces); fails closed on capacity exhaustion or a store rejection.
     */
    public GovernmentPosition createPosition(MinistryId ministryId, String title) {
        requireOwnerThread();
        Objects.requireNonNull(ministryId, "ministryId");
        Objects.requireNonNull(title, "title");
        if (positions.size() >= limits.maxPositions()) {
            throw capacity("Position count would exceed the budget of "
                    + limits.maxPositions());
        }
        requireStoreRevisionSpace();

        GovernmentPosition position = new GovernmentPosition(
                GovernmentPosition.CURRENT_SCHEMA_VERSION,
                PositionId.of(idSource.nextUuid()),
                ministryId,
                title,
                PositionState.VACANT,
                Optional.empty(),
                1
        );
        LinkedHashMap<PositionId, GovernmentPosition> next =
                new LinkedHashMap<>(positions);
        next.put(position.positionId(), position);
        commitAndPublish(
                new GovernmentStoreSnapshot(
                        GovernmentStoreSnapshot.CURRENT_STORE_VERSION,
                        storeRevision + 1,
                        ministries,
                        next,
                        offices
                )
        );
        return position;
    }

    /**
     * Appoints a holder to a VACANT position: the position becomes FILLED
     * with the holder, and a fresh current office is created — one complete
     * replacement snapshot, position/office and store revision +1 exactly
     * once. Replaces any revoked office record of the position.
     */
    public Office appoint(
            PositionId positionId,
            OwnerReference holder,
            UUID officeId,
            long assignedAt
    ) {
        requireOwnerThread();
        GovernmentPosition current = requirePosition(positionId);
        if (offices.size() >= limits.maxOffices()) {
            throw capacity("Office count would exceed the budget of "
                    + limits.maxOffices());
        }
        requireStoreRevisionSpace();

        GovernmentPosition filled = current.withHolder(holder);
        Office office = new Office(
                Office.CURRENT_SCHEMA_VERSION,
                Objects.requireNonNull(officeId, "officeId"),
                positionId,
                holder,
                assignedAt,
                Optional.empty(),
                1
        );
        LinkedHashMap<PositionId, GovernmentPosition> nextPositions =
                new LinkedHashMap<>(positions);
        nextPositions.put(positionId, filled);
        LinkedHashMap<PositionId, Office> nextOffices =
                new LinkedHashMap<>(offices);
        nextOffices.put(positionId, office);
        commitAndPublish(
                new GovernmentStoreSnapshot(
                        GovernmentStoreSnapshot.CURRENT_STORE_VERSION,
                        storeRevision + 1,
                        ministries,
                        nextPositions,
                        nextOffices
                )
        );
        return office;
    }

    /**
     * Dismisses the holder of a FILLED position: the position becomes VACANT
     * and its current office is marked revoked — one complete replacement
     * snapshot, position/office and store revision +1 exactly once.
     */
    public Office dismiss(PositionId positionId, long revokedAt) {
        requireOwnerThread();
        GovernmentPosition current = requirePosition(positionId);
        Office currentOffice = offices.get(positionId);
        if (currentOffice == null) {
            throw new IllegalStateException(
                    "FILLED position " + positionId + " has no office record"
            );
        }
        requireStoreRevisionSpace();

        GovernmentPosition vacated = current.withVacated();
        Office revoked = currentOffice.withRevoked(revokedAt);
        LinkedHashMap<PositionId, GovernmentPosition> nextPositions =
                new LinkedHashMap<>(positions);
        nextPositions.put(positionId, vacated);
        LinkedHashMap<PositionId, Office> nextOffices =
                new LinkedHashMap<>(offices);
        nextOffices.put(positionId, revoked);
        commitAndPublish(
                new GovernmentStoreSnapshot(
                        GovernmentStoreSnapshot.CURRENT_STORE_VERSION,
                        storeRevision + 1,
                        ministries,
                        nextPositions,
                        nextOffices
                )
        );
        return revoked;
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private void requireStoreRevisionSpace() {
        if (storeRevision == Long.MAX_VALUE) {
            throw capacity("Government store revision space exhausted");
        }
    }

    private GovernmentUnavailableException capacity(String message) {
        return new GovernmentUnavailableException(
                GovernmentUnavailableException.CODE_CAPACITY_EXCEEDED,
                message
        );
    }

    private void commitAndPublish(GovernmentStoreSnapshot candidate) {
        CompoundTag encoded = codec.encode(candidate);
        int bytes = codec.encodedSize(encoded);
        if (bytes > limits.maxTotalBytes()) {
            throw capacity(
                    "Government namespace would exceed the byte budget (" + bytes
                            + " > " + limits.maxTotalBytes() + ")"
            );
        }

        DurableCommitResult result;
        try {
            result = store.commit(encoded);
        } catch (RuntimeException failure) {
            throw new GovernmentUnavailableException(
                    GovernmentUnavailableException.CODE_STORE_FAILURE,
                    "Government store rejected a commit: " + failure.getMessage(),
                    failure
            );
        }
        if (result.status() != DurableCommitStatus.COMMITTED) {
            throw new GovernmentUnavailableException(
                    GovernmentUnavailableException.CODE_STORE_FAILURE,
                    "Durable commit of government failed (status="
                            + result.status() + ", code=" + result.failureCode() + ")"
            );
        }
        publish(candidate);
    }

    /** Swaps in a store-accepted candidate; only reachable on COMMITTED. */
    private void publish(GovernmentStoreSnapshot snapshot) {
        ministries.clear();
        ministries.putAll(snapshot.ministries());
        positions.clear();
        positions.putAll(snapshot.positions());
        offices.clear();
        offices.putAll(snapshot.offices());
        storeRevision = snapshot.storeRevision();
    }

    private void enforceLoadedCapacity(GovernmentStoreSnapshot snapshot) {
        if (snapshot.ministries().size() > limits.maxMinistries()) {
            throw capacity("Loaded ministry count " + snapshot.ministries().size()
                    + " exceeds the budget of " + limits.maxMinistries());
        }
        if (snapshot.positions().size() > limits.maxPositions()) {
            throw capacity("Loaded position count " + snapshot.positions().size()
                    + " exceeds the budget of " + limits.maxPositions());
        }
        if (snapshot.offices().size() > limits.maxOffices()) {
            throw capacity("Loaded office count " + snapshot.offices().size()
                    + " exceeds the budget of " + limits.maxOffices());
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "GovernmentRepository may only be accessed from its owning server thread"
            );
        }
    }

    // ------------------------------------------------------------------
    // production store
    // ------------------------------------------------------------------

    private static final class DataManagerGovernmentStore implements GovernmentStore {
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

    private static final class UuidGovernmentIdSource implements GovernmentIdSource {
        @Override
        public UUID nextUuid() {
            return UUID.randomUUID();
        }
    }
}
