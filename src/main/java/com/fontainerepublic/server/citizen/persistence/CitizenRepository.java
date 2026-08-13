package com.fontainerepublic.server.citizen.persistence;

import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.citizen.model.CitizenRank;
import com.fontainerepublic.server.citizen.model.CitizenRecord;
import com.fontainerepublic.server.citizen.model.CitizenStatus;
import com.fontainerepublic.server.registry.model.SubjectId;
import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Single-writer repository owning the {@code "citizen"} NBT namespace
 * (FR-CIT-001-A §3.3/§4).
 *
 * <p>This class is the sole production owner of
 * {@link DataManager#getModuleData(String)} /
 * {@link DataManager#commitModuleData(String, CompoundTag)} for the
 * {@code "citizen"} key. All mutations run on the logical server owner
 * thread; every mutation builds one complete immutable replacement snapshot,
 * increments {@code StoreRevision} once and each changed record revision once,
 * and publishes the new in-memory state only after the FR-CORE-002 durable
 * gate reports {@code COMMITTED}. A failed write has no side effects: no
 * citizen, status/rank, or revision change, and no downstream event.</p>
 *
 * <p>On an empty namespace the repository starts with a fresh, empty store
 * (revision 0). A present namespace is decoded strictly and fail-closed: a
 * record without a valid subject, unknown fields, or newer versions reject the
 * whole load. Exact lookups only — no bulk enumeration API is exposed.</p>
 */
public final class CitizenRepository {

    /** Reserved module-data key for the citizen namespace. */
    public static final String MODULE_DATA_KEY = "citizen";

    private final CitizenStore store;
    private final CitizenNbtCodec codec;
    private final CitizenLimits limits;
    private final Thread ownerThread;

    private final LinkedHashMap<UUID, CitizenRecord> citizens = new LinkedHashMap<>();
    private long storeRevision;

    /**
     * Creates a repository backed by the production {@link DataManager} store.
     */
    public static CitizenRepository createProduction(CitizenNbtCodec codec) {
        Objects.requireNonNull(codec, "codec");
        return new CitizenRepository(
                new DataManagerCitizenStore(),
                codec,
                CitizenLimits.DEFAULT
        );
    }

    public CitizenRepository(
            CitizenStore store,
            CitizenNbtCodec codec,
            CitizenLimits limits
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.ownerThread = Thread.currentThread();

        CompoundTag loaded = store.load().copy();
        if (loaded.isEmpty()) {
            this.storeRevision = 0L;
        } else {
            CitizenStoreSnapshot snapshot = codec.decode(loaded);
            enforceLoadedCapacity(snapshot);
            publish(snapshot);
        }
    }

    // ------------------------------------------------------------------
    // read surface (exact lookups only; no enumeration API)
    // ------------------------------------------------------------------

    public Optional<CitizenRecord> findByPlayer(UUID playerId) {
        requireOwnerThread();
        return Optional.ofNullable(
                citizens.get(Objects.requireNonNull(playerId, "playerId"))
        );
    }

    public CitizenRecord requireCitizen(UUID playerId) {
        return findByPlayer(playerId).orElseThrow(
                () -> new CitizenUnavailableException(
                        CitizenUnavailableException.CODE_NO_CITIZEN_RECORD,
                        "No citizen record for player " + playerId
                )
        );
    }

    public int size() {
        requireOwnerThread();
        return citizens.size();
    }

    public CitizenStoreSnapshot snapshot() {
        requireOwnerThread();
        return new CitizenStoreSnapshot(
                CitizenStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision,
                citizens
        );
    }

    // ------------------------------------------------------------------
    // write surface
    // ------------------------------------------------------------------

    /**
     * Lazy, idempotent provisioning of the citizen record: creates it with
     * the default {@code status = CITIZEN}, {@code rank = CITIZEN}, revision 1
     * and the given first-citizenship timestamp; returns the existing record
     * unchanged on repeat calls. The bound subject must already exist (the
     * service chain guarantees it). Fails closed on capacity exhaustion or a
     * store rejection — nothing is published on failure.
     */
    public CitizenRecord ensure(UUID playerId, SubjectId subjectId, long timestamp) {
        requireOwnerThread();
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(subjectId, "subjectId");

        CitizenRecord existing = citizens.get(playerId);
        if (existing != null) {
            if (!existing.subjectId().equals(subjectId)) {
                throw new IllegalStateException(
                        "Citizen " + playerId + " is bound to subject "
                                + existing.subjectId() + ", not " + subjectId
                );
            }
            return existing;
        }
        if (citizens.size() >= limits.maxCitizens()) {
            throw new CitizenUnavailableException(
                    CitizenUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Citizen count would exceed the budget of " + limits.maxCitizens()
            );
        }
        requireStoreRevisionSpace();

        CitizenRecord record = new CitizenRecord(
                CitizenRecord.CURRENT_SCHEMA_VERSION,
                playerId,
                subjectId,
                CitizenStatus.CITIZEN,
                CitizenRank.CITIZEN,
                timestamp,
                1
        );
        LinkedHashMap<UUID, CitizenRecord> next = new LinkedHashMap<>(citizens);
        next.put(playerId, record);
        commitAndPublish(
                new CitizenStoreSnapshot(
                        CitizenStoreSnapshot.CURRENT_STORE_VERSION,
                        storeRevision + 1,
                        next
                )
        );
        return record;
    }

    /**
     * Replaces the rank of an existing citizen: one complete replacement
     * snapshot, record and store revision +1 exactly once. Same-rank requests
     * are idempotent no-ops (nothing committed).
     */
    public CitizenRecord updateRank(UUID playerId, CitizenRank rank) {
        requireOwnerThread();
        Objects.requireNonNull(rank, "rank");
        CitizenRecord current = requireCitizen(playerId);
        if (current.rank() == rank) {
            return current;
        }
        requireStoreRevisionSpace();
        replaceAndPublish(current.withRank(rank));
        return requireCitizen(playerId);
    }

    /**
     * Replaces the status of an existing citizen: one complete replacement
     * snapshot, record and store revision +1 exactly once. Same-status
     * requests are idempotent no-ops (nothing committed).
     */
    public CitizenRecord updateStatus(UUID playerId, CitizenStatus status) {
        requireOwnerThread();
        Objects.requireNonNull(status, "status");
        CitizenRecord current = requireCitizen(playerId);
        if (current.status() == status) {
            return current;
        }
        requireStoreRevisionSpace();
        replaceAndPublish(current.withStatus(status));
        return requireCitizen(playerId);
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private void replaceAndPublish(CitizenRecord updated) {
        LinkedHashMap<UUID, CitizenRecord> next = new LinkedHashMap<>(citizens);
        next.put(updated.playerId(), updated);
        commitAndPublish(
                new CitizenStoreSnapshot(
                        CitizenStoreSnapshot.CURRENT_STORE_VERSION,
                        storeRevision + 1,
                        next
                )
        );
    }

    private void requireStoreRevisionSpace() {
        if (storeRevision == Long.MAX_VALUE) {
            throw new CitizenUnavailableException(
                    CitizenUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Citizen store revision space exhausted"
            );
        }
    }

    private void commitAndPublish(CitizenStoreSnapshot candidate) {
        CompoundTag encoded = codec.encode(candidate);
        int bytes = codec.encodedSize(encoded);
        if (bytes > limits.maxTotalBytes()) {
            throw new CitizenUnavailableException(
                    CitizenUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Citizen namespace would exceed the byte budget (" + bytes
                            + " > " + limits.maxTotalBytes() + ")"
            );
        }

        DurableCommitResult result;
        try {
            result = store.commit(encoded);
        } catch (RuntimeException failure) {
            throw new CitizenUnavailableException(
                    CitizenUnavailableException.CODE_STORE_FAILURE,
                    "Citizen store rejected a commit: " + failure.getMessage(),
                    failure
            );
        }
        if (result.status() != DurableCommitStatus.COMMITTED) {
            throw new CitizenUnavailableException(
                    CitizenUnavailableException.CODE_STORE_FAILURE,
                    "Durable commit of citizen failed (status="
                            + result.status() + ", code=" + result.failureCode() + ")"
            );
        }
        publish(candidate);
    }

    /** Swaps in a store-accepted candidate; only reachable on COMMITTED. */
    private void publish(CitizenStoreSnapshot snapshot) {
        citizens.clear();
        citizens.putAll(snapshot.citizens());
        storeRevision = snapshot.storeRevision();
    }

    private void enforceLoadedCapacity(CitizenStoreSnapshot snapshot) {
        if (snapshot.citizens().size() > limits.maxCitizens()) {
            throw new CitizenUnavailableException(
                    CitizenUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Loaded citizen count " + snapshot.citizens().size()
                            + " exceeds the budget of " + limits.maxCitizens()
            );
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "CitizenRepository may only be accessed from its owning server thread"
            );
        }
    }

    // ------------------------------------------------------------------
    // production store
    // ------------------------------------------------------------------

    private static final class DataManagerCitizenStore implements CitizenStore {
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
