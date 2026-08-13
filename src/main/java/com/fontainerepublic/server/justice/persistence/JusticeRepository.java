package com.fontainerepublic.server.justice.persistence;

import com.fontainerepublic.core.DataManager;
import com.fontainerepublic.core.DurableCommitResult;
import com.fontainerepublic.core.DurableCommitStatus;
import com.fontainerepublic.server.justice.model.Case;
import com.fontainerepublic.server.justice.model.CaseId;
import com.fontainerepublic.server.justice.model.Evidence;
import com.fontainerepublic.server.justice.model.EvidenceId;
import com.fontainerepublic.server.justice.model.TransitionRecord;
import com.fontainerepublic.server.justice.model.Verdict;
import com.fontainerepublic.server.justice.model.VerdictId;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Single-writer repository owning the {@code "justice"} NBT namespace
 * (FR-JUS-001-A §3.4).
 *
 * <p>This class is the sole production owner of
 * {@link DataManager#getModuleData(String)} /
 * {@link DataManager#commitModuleData(String, CompoundTag)} for the
 * {@code "justice"} key. All mutations run on the logical server owner
 * thread. The repository exposes exact lookups and two ordered, bounded
 * projections ({@link #casesAfter} and {@link #evidenceAfter}) — no full
 * enumeration API.</p>
 *
 * <p>Every authoritative mutation is a complete immutable replacement
 * snapshot ({@link #commit(JusticeStoreSnapshot)}): the caller builds the
 * next state, the repository encodes it, checks the byte budget, commits it
 * through the FR-CORE-002 durable gate, and only then publishes the new
 * in-memory state. A failed write has no side effects — no case, evidence,
 * verdict, transition, or revision change, and no downstream event. Store
 * revision increments exactly once per committed mutation.</p>
 *
 * <p>On an empty namespace the repository starts fresh (revision 0); a
 * present namespace is decoded strictly and fail-closed: unknown fields,
 * wrong types, newer versions, non-canonical ids, or broken referential
 * integrity reject the whole load and disable the namespace.</p>
 */
public final class JusticeRepository {

    /** Reserved module-data key for the justice namespace. */
    public static final String MODULE_DATA_KEY = "justice";

    private final JusticeStore store;
    private final JusticeNbtCodec codec;
    private final JusticeLimits limits;
    private final Thread ownerThread;

    private final LinkedHashMap<CaseId, Case> cases = new LinkedHashMap<>();
    private final LinkedHashMap<EvidenceId, Evidence> evidence = new LinkedHashMap<>();
    private final LinkedHashMap<VerdictId, Verdict> verdicts = new LinkedHashMap<>();
    private final List<TransitionRecord> transitions = new ArrayList<>();
    private long storeRevision;

    /**
     * Creates a repository backed by the production {@link DataManager} store.
     */
    public static JusticeRepository createProduction(JusticeNbtCodec codec) {
        Objects.requireNonNull(codec, "codec");
        return new JusticeRepository(
                new DataManagerJusticeStore(),
                codec,
                JusticeLimits.DEFAULT
        );
    }

    public JusticeRepository(
            JusticeStore store,
            JusticeNbtCodec codec,
            JusticeLimits limits
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.ownerThread = Thread.currentThread();

        CompoundTag loaded = store.load().copy();
        if (loaded.isEmpty()) {
            this.storeRevision = 0L;
        } else {
            JusticeStoreSnapshot snapshot = codec.decode(loaded);
            checkLoadedCapacity(snapshot);
            publish(snapshot);
        }
    }

    // ------------------------------------------------------------------
    // read surface (exact lookups; two bounded, ordered projections)
    // ------------------------------------------------------------------

    public Optional<Case> caseById(CaseId caseId) {
        requireOwnerThread();
        return Optional.ofNullable(
                cases.get(Objects.requireNonNull(caseId, "caseId"))
        );
    }

    public Optional<Evidence> evidenceById(EvidenceId evidenceId) {
        requireOwnerThread();
        return Optional.ofNullable(
                evidence.get(Objects.requireNonNull(evidenceId, "evidenceId"))
        );
    }

    public Optional<Verdict> verdictById(VerdictId verdictId) {
        requireOwnerThread();
        return Optional.ofNullable(
                verdicts.get(Objects.requireNonNull(verdictId, "verdictId"))
        );
    }

    /**
     * Ordered, bounded projection of cases by ascending sequence, strictly
     * after {@code afterSeq}, at most {@code limit} records ({@code limit}
     * must be positive). Never returns more than {@code limit}; no unbounded
     * enumeration exists.
     */
    public List<Case> casesAfter(long afterSeq, int limit) {
        requireOwnerThread();
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return cases.values().stream()
                .filter(aCase -> aCase.caseSeq() > afterSeq)
                .sorted(Comparator.comparingLong(Case::caseSeq))
                .limit(limit)
                .toList();
    }

    /**
     * Ordered, bounded projection of the evidence of one case by ascending
     * per-case sequence, strictly after {@code afterSeq}, at most
     * {@code limit} records. Never returns more than {@code limit}.
     */
    public List<Evidence> evidenceAfter(CaseId caseId, long afterSeq, int limit) {
        requireOwnerThread();
        Objects.requireNonNull(caseId, "caseId");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return evidence.values().stream()
                .filter(item -> item.caseId().equals(caseId))
                .filter(item -> item.evidenceSeq() > afterSeq)
                .sorted(Comparator.comparingLong(Evidence::evidenceSeq))
                .limit(limit)
                .toList();
    }

    /** Number of evidence records of one case (admissibility boundary). */
    public long evidenceCountFor(CaseId caseId) {
        requireOwnerThread();
        Objects.requireNonNull(caseId, "caseId");
        return evidence.values().stream()
                .filter(item -> item.caseId().equals(caseId))
                .count();
    }

    /** Current immutable namespace snapshot. */
    public JusticeStoreSnapshot snapshot() {
        requireOwnerThread();
        return new JusticeStoreSnapshot(
                JusticeStoreSnapshot.CURRENT_STORE_VERSION,
                storeRevision,
                cases,
                evidence,
                verdicts,
                transitions
        );
    }

    // ------------------------------------------------------------------
    // write surface
    // ------------------------------------------------------------------

    /**
     * Commits one complete replacement snapshot. The caller (service layer)
     * is responsible for the semantic delta and for idempotency (a
     * same-state candidate must never be committed); this method is the
     * single mutation boundary: capacity, revision space, encoding, byte
     * budget, and the durable gate are all enforced here. On any failure
     * nothing is published and no revision is consumed.
     */
    public void commit(JusticeStoreSnapshot candidate) {
        requireOwnerThread();
        Objects.requireNonNull(candidate, "candidate");
        if (candidate.storeRevision() != storeRevision + 1) {
            throw new IllegalStateException(
                    "Candidate storeRevision " + candidate.storeRevision()
                            + " must be current + 1 (" + (storeRevision + 1) + ")"
            );
        }
        checkCandidateCapacity(candidate);
        requireStoreRevisionSpace();

        CompoundTag encoded = codec.encode(candidate);
        int bytes = codec.encodedSize(encoded);
        if (bytes > limits.maxTotalBytes()) {
            throw new JusticeUnavailableException(
                    JusticeUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Justice namespace would exceed the byte budget (" + bytes
                            + " > " + limits.maxTotalBytes() + ")"
            );
        }

        DurableCommitResult result;
        try {
            result = store.commit(encoded);
        } catch (RuntimeException failure) {
            throw new JusticeUnavailableException(
                    JusticeUnavailableException.CODE_STORE_FAILURE,
                    "Justice store rejected a commit: " + failure.getMessage(),
                    failure
            );
        }
        if (result.status() != DurableCommitStatus.COMMITTED) {
            throw new JusticeUnavailableException(
                    JusticeUnavailableException.CODE_STORE_FAILURE,
                    "Durable commit of justice failed (status="
                            + result.status() + ", code=" + result.failureCode() + ")"
            );
        }
        publish(candidate);
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    /** Swaps in a store-accepted candidate; only reachable on COMMITTED. */
    private void publish(JusticeStoreSnapshot snapshot) {
        cases.clear();
        cases.putAll(snapshot.cases());
        evidence.clear();
        evidence.putAll(snapshot.evidence());
        verdicts.clear();
        verdicts.putAll(snapshot.verdicts());
        transitions.clear();
        transitions.addAll(snapshot.transitions());
        storeRevision = snapshot.storeRevision();
    }

    private void requireStoreRevisionSpace() {
        if (storeRevision == Long.MAX_VALUE) {
            throw new JusticeUnavailableException(
                    JusticeUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Justice store revision space exhausted"
            );
        }
    }

    private void checkCandidateCapacity(JusticeStoreSnapshot candidate) {
        if (candidate.cases().size() > limits.maxCases()) {
            throw capacity("case count " + candidate.cases().size()
                    + " exceeds the budget of " + limits.maxCases());
        }
        if (candidate.evidence().size() > limits.maxEvidence()) {
            throw capacity("evidence count " + candidate.evidence().size()
                    + " exceeds the budget of " + limits.maxEvidence());
        }
        if (candidate.verdicts().size() > limits.maxVerdicts()) {
            throw capacity("verdict count " + candidate.verdicts().size()
                    + " exceeds the budget of " + limits.maxVerdicts());
        }
        if (candidate.transitions().size() > limits.maxTransitions()) {
            throw capacity("transition count " + candidate.transitions().size()
                    + " exceeds the budget of " + limits.maxTransitions());
        }
        for (CaseId caseId : candidate.cases().keySet()) {
            long perCase = candidate.evidence().values().stream()
                    .filter(item -> item.caseId().equals(caseId))
                    .count();
            if (perCase > limits.maxEvidencePerCase()) {
                throw capacity("evidence of case " + caseId
                        + " exceeds the per-case budget of "
                        + limits.maxEvidencePerCase());
            }
        }
    }

    private void checkLoadedCapacity(JusticeStoreSnapshot snapshot) {
        if (snapshot.cases().size() > limits.maxCases()
                || snapshot.evidence().size() > limits.maxEvidence()
                || snapshot.verdicts().size() > limits.maxVerdicts()
                || snapshot.transitions().size() > limits.maxTransitions()) {
            throw new JusticeUnavailableException(
                    JusticeUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Loaded justice namespace exceeds the configured budget"
            );
        }
    }

    private JusticeUnavailableException capacity(String detail) {
        return new JusticeUnavailableException(
                JusticeUnavailableException.CODE_CAPACITY_EXCEEDED,
                "Justice " + detail
        );
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "JusticeRepository may only be accessed from its owning server thread"
            );
        }
    }

    // ------------------------------------------------------------------
    // production store
    // ------------------------------------------------------------------

    private static final class DataManagerJusticeStore implements JusticeStore {
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
