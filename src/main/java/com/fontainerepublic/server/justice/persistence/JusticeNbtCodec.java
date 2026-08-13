package com.fontainerepublic.server.justice.persistence;

import com.fontainerepublic.server.justice.model.Case;
import com.fontainerepublic.server.justice.model.CaseId;
import com.fontainerepublic.server.justice.model.CaseState;
import com.fontainerepublic.server.justice.model.CaseTransitionTrigger;
import com.fontainerepublic.server.justice.model.CaseType;
import com.fontainerepublic.server.justice.model.CourtLevel;
import com.fontainerepublic.server.justice.model.Evidence;
import com.fontainerepublic.server.justice.model.EvidenceId;
import com.fontainerepublic.server.justice.model.EvidenceState;
import com.fontainerepublic.server.justice.model.TransitionRecord;
import com.fontainerepublic.server.justice.model.Verdict;
import com.fontainerepublic.server.justice.model.VerdictId;
import com.fontainerepublic.server.justice.model.VerdictOutcome;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Strict, versioned, deterministic NBT codec for the {@code "justice"}
 * namespace (FR-JUS-001-A §3.4).
 *
 * <p>Encoding writes every index in deterministic lexical key order and the
 * transition ledger in append order, so the same immutable snapshot always
 * produces an equivalent ordered NBT. Decoding accepts only declared fields
 * with exact NBT types, canonical UUIDs, valid enums, bounded text, positive
 * timestamps and revisions, and constructs the validating
 * {@link JusticeStoreSnapshot} which enforces key/record identity and
 * referential integrity. Unknown newer versions are rejected; nothing is
 * ever auto-repaired.</p>
 */
public final class JusticeNbtCodec {

    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String CASES = "Cases";
    private static final String EVIDENCE = "Evidence";
    private static final String VERDICTS = "Verdicts";
    private static final String TRANSITIONS = "Transitions";

    private static final String CASE_VERSION = "CaseVersion";
    private static final String CASE_ID = "CaseId";
    private static final String CASE_SEQ = "CaseSeq";
    private static final String CASE_TYPE = "CaseType";
    private static final String TITLE = "Title";
    private static final String DESCRIPTION = "Description";
    private static final String PLAINTIFF_REF = "PlaintiffRef";
    private static final String DEFENDANT_REF = "DefendantRef";
    private static final String SOURCE_REPORT_ID = "SourceReportId";
    private static final String STATE = "State";
    private static final String COURT_LEVEL = "CourtLevel";
    private static final String CREATED_AT = "CreatedAt";
    private static final String RECORD_REVISION = "RecordRevision";

    private static final String EVIDENCE_VERSION = "EvidenceVersion";
    private static final String EVIDENCE_ID = "EvidenceId";
    private static final String EVIDENCE_SEQ = "EvidenceSeq";
    private static final String SUBMITTED_BY_REF = "SubmittedByRef";
    private static final String INTEGRITY_DIGEST = "IntegrityDigest";
    private static final String SUBMITTED_AT = "SubmittedAt";
    private static final String ADMITTED_AT = "AdmittedAt";

    private static final String VERDICT_VERSION = "VerdictVersion";
    private static final String VERDICT_ID = "VerdictId";
    private static final String OUTCOME = "Outcome";
    private static final String REASONING = "Reasoning";
    private static final String JUDGE_REF = "JudgeRef";
    private static final String LEVEL = "Level";
    private static final String ISSUED_AT = "IssuedAt";

    private static final String TRANSITION_VERSION = "TransitionVersion";
    private static final String ACTOR = "Actor";
    private static final String AT_MILLIS = "AtMillis";
    private static final String TRIGGER = "Trigger";
    private static final String BEFORE = "Before";
    private static final String AFTER = "After";

    /** Hard structural caps, independent of the configurable budget. */
    private static final int HARD_MAX_CASES = 100_000;
    private static final int HARD_MAX_EVIDENCE = 500_000;
    private static final int HARD_MAX_VERDICTS = 100_000;
    private static final int HARD_MAX_TRANSITIONS = 1_000_000;

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION,
            STORE_REVISION,
            CASES,
            EVIDENCE,
            VERDICTS,
            TRANSITIONS
    );
    private static final Set<String> CASE_KEYS = Set.of(
            CASE_VERSION,
            CASE_ID,
            CASE_SEQ,
            CASE_TYPE,
            TITLE,
            DESCRIPTION,
            PLAINTIFF_REF,
            DEFENDANT_REF,
            SOURCE_REPORT_ID,
            STATE,
            COURT_LEVEL,
            CREATED_AT,
            RECORD_REVISION
    );
    private static final Set<String> EVIDENCE_KEYS = Set.of(
            EVIDENCE_VERSION,
            EVIDENCE_ID,
            CASE_ID,
            EVIDENCE_SEQ,
            SUBMITTED_BY_REF,
            DESCRIPTION,
            INTEGRITY_DIGEST,
            STATE,
            SUBMITTED_AT,
            ADMITTED_AT,
            RECORD_REVISION
    );
    private static final Set<String> VERDICT_KEYS = Set.of(
            VERDICT_VERSION,
            VERDICT_ID,
            CASE_ID,
            OUTCOME,
            REASONING,
            JUDGE_REF,
            LEVEL,
            ISSUED_AT,
            RECORD_REVISION
    );
    private static final Set<String> TRANSITION_KEYS = Set.of(
            TRANSITION_VERSION,
            CASE_ID,
            ACTOR,
            AT_MILLIS,
            TRIGGER,
            BEFORE,
            AFTER,
            RECORD_REVISION
    );

    // ------------------------------------------------------------------
    // decode
    // ------------------------------------------------------------------

    /**
     * Decodes and fully validates a namespace snapshot. Empty input is
     * rejected: a present justice namespace must be a valid, initialized
     * store.
     */
    public JusticeStoreSnapshot decode(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            throw invalid(
                    "justice namespace is empty; it must be a valid initialized store"
            );
        }

        requireOnlyKeys(root, STORE_KEYS, "justice");
        requireType(root, STORE_VERSION, Tag.TAG_INT, "justice");
        requireType(root, STORE_REVISION, Tag.TAG_LONG, "justice");
        requireType(root, CASES, Tag.TAG_COMPOUND, "justice");
        requireType(root, EVIDENCE, Tag.TAG_COMPOUND, "justice");
        requireType(root, VERDICTS, Tag.TAG_COMPOUND, "justice");
        requireType(root, TRANSITIONS, Tag.TAG_LIST, "justice");

        int storeVersion = root.getInt(STORE_VERSION);
        if (storeVersion != JusticeStoreSnapshot.CURRENT_STORE_VERSION) {
            throw invalid("Unsupported justice store version: " + storeVersion);
        }
        long storeRevision = root.getLong(STORE_REVISION);
        if (storeRevision < 0) {
            throw invalid("StoreRevision must not be negative");
        }

        Map<CaseId, Case> cases = decodeCases(root.getCompound(CASES));
        Map<EvidenceId, Evidence> evidence = decodeEvidence(
                root.getCompound(EVIDENCE),
                cases
        );
        Map<VerdictId, Verdict> verdicts = decodeVerdicts(
                root.getCompound(VERDICTS),
                cases
        );
        List<TransitionRecord> transitions = decodeTransitions(
                root.getList(TRANSITIONS, Tag.TAG_COMPOUND),
                cases
        );
        return new JusticeStoreSnapshot(
                storeVersion,
                storeRevision,
                cases,
                evidence,
                verdicts,
                transitions
        );
    }

    private Map<CaseId, Case> decodeCases(CompoundTag tag) {
        if (tag.getAllKeys().size() > HARD_MAX_CASES) {
            throw invalid("Case count exceeds " + HARD_MAX_CASES);
        }
        Map<CaseId, Case> cases = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            CaseId caseId = parseCanonicalId(key, CASES);
            requireType(tag, key, Tag.TAG_COMPOUND, CASES);
            Case aCase = decodeCase(tag.getCompound(key), caseId);
            if (cases.put(caseId, aCase) != null) {
                throw invalid("Duplicate case id: " + caseId);
            }
        }
        return cases;
    }

    private Case decodeCase(CompoundTag tag, CaseId expectedId) {
        requireOnlyKeys(tag, CASE_KEYS, "case " + expectedId);
        requireType(tag, CASE_VERSION, Tag.TAG_INT, "case " + expectedId);
        requireType(tag, CASE_ID, Tag.TAG_INT_ARRAY, "case " + expectedId);
        requireType(tag, CASE_SEQ, Tag.TAG_LONG, "case " + expectedId);
        requireType(tag, CASE_TYPE, Tag.TAG_STRING, "case " + expectedId);
        requireType(tag, TITLE, Tag.TAG_STRING, "case " + expectedId);
        requireType(tag, DESCRIPTION, Tag.TAG_STRING, "case " + expectedId);
        requireType(tag, PLAINTIFF_REF, Tag.TAG_INT_ARRAY, "case " + expectedId);
        requireType(tag, STATE, Tag.TAG_STRING, "case " + expectedId);
        requireType(tag, COURT_LEVEL, Tag.TAG_STRING, "case " + expectedId);
        requireType(tag, CREATED_AT, Tag.TAG_LONG, "case " + expectedId);
        requireType(tag, RECORD_REVISION, Tag.TAG_LONG, "case " + expectedId);

        int version = tag.getInt(CASE_VERSION);
        if (version != Case.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported case schema version for " + expectedId + ": " + version
            );
        }
        UUID storedId = tag.getUUID(CASE_ID);
        if (!expectedId.value().equals(storedId)) {
            throw invalid(
                    "Cases key " + expectedId
                            + " does not match record caseId " + storedId
            );
        }
        try {
            Optional<UUID> defendantRef = tag.contains(DEFENDANT_REF, Tag.TAG_INT_ARRAY)
                    ? Optional.of(tag.getUUID(DEFENDANT_REF))
                    : Optional.empty();
            Optional<Long> sourceReportId =
                    tag.contains(SOURCE_REPORT_ID, Tag.TAG_LONG)
                            ? Optional.of(tag.getLong(SOURCE_REPORT_ID))
                            : Optional.empty();
            return new Case(
                    version,
                    expectedId,
                    tag.getLong(CASE_SEQ),
                    enumValue(CaseType.class, tag.getString(CASE_TYPE),
                            "CaseType for " + expectedId),
                    tag.getString(TITLE),
                    tag.getString(DESCRIPTION),
                    tag.getUUID(PLAINTIFF_REF),
                    defendantRef,
                    sourceReportId,
                    enumValue(CaseState.class, tag.getString(STATE),
                            "State for " + expectedId),
                    enumValue(CourtLevel.class, tag.getString(COURT_LEVEL),
                            "CourtLevel for " + expectedId),
                    tag.getLong(CREATED_AT),
                    tag.getLong(RECORD_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid case " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private Map<EvidenceId, Evidence> decodeEvidence(
            CompoundTag tag,
            Map<CaseId, Case> cases
    ) {
        if (tag.getAllKeys().size() > HARD_MAX_EVIDENCE) {
            throw invalid("Evidence count exceeds " + HARD_MAX_EVIDENCE);
        }
        Map<EvidenceId, Evidence> evidence = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            EvidenceId evidenceId = parseEvidenceId(key);
            requireType(tag, key, Tag.TAG_COMPOUND, EVIDENCE);
            Evidence item = decodeEvidenceEntry(tag.getCompound(key), evidenceId, cases);
            if (evidence.put(evidenceId, item) != null) {
                throw invalid("Duplicate evidence id: " + evidenceId);
            }
        }
        return evidence;
    }

    private Evidence decodeEvidenceEntry(
            CompoundTag tag,
            EvidenceId expectedId,
            Map<CaseId, Case> cases
    ) {
        requireOnlyKeys(tag, EVIDENCE_KEYS, "evidence " + expectedId);
        requireType(tag, EVIDENCE_VERSION, Tag.TAG_INT, "evidence " + expectedId);
        requireType(tag, EVIDENCE_ID, Tag.TAG_INT_ARRAY, "evidence " + expectedId);
        requireType(tag, CASE_ID, Tag.TAG_INT_ARRAY, "evidence " + expectedId);
        requireType(tag, EVIDENCE_SEQ, Tag.TAG_LONG, "evidence " + expectedId);
        requireType(tag, SUBMITTED_BY_REF, Tag.TAG_INT_ARRAY, "evidence " + expectedId);
        requireType(tag, DESCRIPTION, Tag.TAG_STRING, "evidence " + expectedId);
        requireType(tag, STATE, Tag.TAG_STRING, "evidence " + expectedId);
        requireType(tag, SUBMITTED_AT, Tag.TAG_LONG, "evidence " + expectedId);
        requireType(tag, RECORD_REVISION, Tag.TAG_LONG, "evidence " + expectedId);

        int version = tag.getInt(EVIDENCE_VERSION);
        if (version != Evidence.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported evidence schema version for " + expectedId
                            + ": " + version
            );
        }
        UUID storedId = tag.getUUID(EVIDENCE_ID);
        if (!expectedId.value().equals(storedId)) {
            throw invalid(
                    "Evidence key " + expectedId
                            + " does not match record evidenceId " + storedId
            );
        }
        CaseId caseId = parseCaseId(tag, CASE_ID, "evidence " + expectedId);
        if (!cases.containsKey(caseId)) {
            throw invalid(
                    "Evidence " + expectedId + " references missing case " + caseId
            );
        }
        try {
            Optional<String> digest = tag.contains(INTEGRITY_DIGEST, Tag.TAG_STRING)
                    ? Optional.of(tag.getString(INTEGRITY_DIGEST))
                    : Optional.empty();
            Optional<Long> admittedAt = tag.contains(ADMITTED_AT, Tag.TAG_LONG)
                    ? Optional.of(tag.getLong(ADMITTED_AT))
                    : Optional.empty();
            return new Evidence(
                    version,
                    expectedId,
                    caseId,
                    tag.getLong(EVIDENCE_SEQ),
                    tag.getUUID(SUBMITTED_BY_REF),
                    tag.getString(DESCRIPTION),
                    digest,
                    enumValue(EvidenceState.class, tag.getString(STATE),
                            "State for " + expectedId),
                    tag.getLong(SUBMITTED_AT),
                    admittedAt,
                    tag.getLong(RECORD_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid evidence " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private Map<VerdictId, Verdict> decodeVerdicts(
            CompoundTag tag,
            Map<CaseId, Case> cases
    ) {
        if (tag.getAllKeys().size() > HARD_MAX_VERDICTS) {
            throw invalid("Verdict count exceeds " + HARD_MAX_VERDICTS);
        }
        Map<VerdictId, Verdict> verdicts = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            VerdictId verdictId = parseVerdictId(key);
            requireType(tag, key, Tag.TAG_COMPOUND, VERDICTS);
            Verdict verdict = decodeVerdict(tag.getCompound(key), verdictId, cases);
            if (verdicts.put(verdictId, verdict) != null) {
                throw invalid("Duplicate verdict id: " + verdictId);
            }
        }
        return verdicts;
    }

    private Verdict decodeVerdict(
            CompoundTag tag,
            VerdictId expectedId,
            Map<CaseId, Case> cases
    ) {
        requireOnlyKeys(tag, VERDICT_KEYS, "verdict " + expectedId);
        requireType(tag, VERDICT_VERSION, Tag.TAG_INT, "verdict " + expectedId);
        requireType(tag, VERDICT_ID, Tag.TAG_INT_ARRAY, "verdict " + expectedId);
        requireType(tag, CASE_ID, Tag.TAG_INT_ARRAY, "verdict " + expectedId);
        requireType(tag, OUTCOME, Tag.TAG_STRING, "verdict " + expectedId);
        requireType(tag, REASONING, Tag.TAG_STRING, "verdict " + expectedId);
        requireType(tag, JUDGE_REF, Tag.TAG_INT_ARRAY, "verdict " + expectedId);
        requireType(tag, LEVEL, Tag.TAG_STRING, "verdict " + expectedId);
        requireType(tag, ISSUED_AT, Tag.TAG_LONG, "verdict " + expectedId);
        requireType(tag, RECORD_REVISION, Tag.TAG_LONG, "verdict " + expectedId);

        int version = tag.getInt(VERDICT_VERSION);
        if (version != Verdict.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported verdict schema version for " + expectedId + ": " + version
            );
        }
        UUID storedId = tag.getUUID(VERDICT_ID);
        if (!expectedId.value().equals(storedId)) {
            throw invalid(
                    "Verdicts key " + expectedId
                            + " does not match record verdictId " + storedId
            );
        }
        CaseId caseId = parseCaseId(tag, CASE_ID, "verdict " + expectedId);
        if (!cases.containsKey(caseId)) {
            throw invalid(
                    "Verdict " + expectedId + " references missing case " + caseId
            );
        }
        try {
            return new Verdict(
                    version,
                    expectedId,
                    caseId,
                    enumValue(VerdictOutcome.class, tag.getString(OUTCOME),
                            "Outcome for " + expectedId),
                    tag.getString(REASONING),
                    tag.getUUID(JUDGE_REF),
                    enumValue(CourtLevel.class, tag.getString(LEVEL),
                            "Level for " + expectedId),
                    tag.getLong(ISSUED_AT),
                    tag.getLong(RECORD_REVISION)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid verdict " + expectedId + ": " + failure.getMessage(),
                    failure
            );
        }
    }

    private List<TransitionRecord> decodeTransitions(
            ListTag list,
            Map<CaseId, Case> cases
    ) {
        if (list.size() > HARD_MAX_TRANSITIONS) {
            throw invalid("Transition count exceeds " + HARD_MAX_TRANSITIONS);
        }
        List<TransitionRecord> transitions = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            CompoundTag tag = list.getCompound(index);
            requireOnlyKeys(tag, TRANSITION_KEYS, "transition " + index);
            requireType(tag, TRANSITION_VERSION, Tag.TAG_INT, "transition " + index);
            requireType(tag, CASE_ID, Tag.TAG_INT_ARRAY, "transition " + index);
            requireType(tag, ACTOR, Tag.TAG_INT_ARRAY, "transition " + index);
            requireType(tag, AT_MILLIS, Tag.TAG_LONG, "transition " + index);
            requireType(tag, TRIGGER, Tag.TAG_STRING, "transition " + index);
            requireType(tag, AFTER, Tag.TAG_STRING, "transition " + index);
            requireType(tag, RECORD_REVISION, Tag.TAG_LONG, "transition " + index);

            int version = tag.getInt(TRANSITION_VERSION);
            if (version != TransitionRecord.CURRENT_SCHEMA_VERSION) {
                throw invalid(
                        "Unsupported transition schema version at " + index + ": " + version
                );
            }
            CaseId caseId = parseCaseId(tag, CASE_ID, "transition " + index);
            if (!cases.containsKey(caseId)) {
                throw invalid(
                        "Transition at " + index
                                + " references missing case " + caseId
                );
            }
            Optional<CaseState> before = tag.contains(BEFORE, Tag.TAG_STRING)
                    ? Optional.of(enumValue(CaseState.class, tag.getString(BEFORE),
                            "Before at " + index))
                    : Optional.empty();
            try {
                transitions.add(new TransitionRecord(
                        version,
                        caseId,
                        tag.getUUID(ACTOR),
                        tag.getLong(AT_MILLIS),
                        enumValue(CaseTransitionTrigger.class, tag.getString(TRIGGER),
                                "Trigger at " + index),
                        before,
                        enumValue(CaseState.class, tag.getString(AFTER),
                                "After at " + index),
                        tag.getLong(RECORD_REVISION)
                ));
            } catch (IllegalArgumentException failure) {
                throw invalid(
                        "Invalid transition at " + index + ": " + failure.getMessage(),
                        failure
                );
            }
        }
        return transitions;
    }

    // ------------------------------------------------------------------
    // encode
    // ------------------------------------------------------------------

    /**
     * Encodes a validated snapshot deterministically: every index is written
     * in sorted lexical key order; the transition ledger keeps its order.
     */
    public CompoundTag encode(JusticeStoreSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(STORE_VERSION, snapshot.storeVersion());
        root.putLong(STORE_REVISION, snapshot.storeRevision());

        CompoundTag cases = new CompoundTag();
        TreeMap<String, Case> orderedCases = new TreeMap<>();
        snapshot.cases().forEach(
                (id, aCase) -> orderedCases.put(id.canonicalKey(), aCase)
        );
        orderedCases.forEach(
                (key, aCase) -> cases.put(key, encodeCase(aCase))
        );
        root.put(CASES, cases);

        CompoundTag evidence = new CompoundTag();
        TreeMap<String, Evidence> orderedEvidence = new TreeMap<>();
        snapshot.evidence().forEach(
                (id, item) -> orderedEvidence.put(id.canonicalKey(), item)
        );
        orderedEvidence.forEach(
                (key, item) -> evidence.put(key, encodeEvidence(item))
        );
        root.put(EVIDENCE, evidence);

        CompoundTag verdicts = new CompoundTag();
        TreeMap<String, Verdict> orderedVerdicts = new TreeMap<>();
        snapshot.verdicts().forEach(
                (id, verdict) -> orderedVerdicts.put(id.canonicalKey(), verdict)
        );
        orderedVerdicts.forEach(
                (key, verdict) -> verdicts.put(key, encodeVerdict(verdict))
        );
        root.put(VERDICTS, verdicts);

        ListTag transitions = new ListTag();
        for (TransitionRecord transition : snapshot.transitions()) {
            transitions.add(encodeTransition(transition));
        }
        root.put(TRANSITIONS, transitions);
        return root;
    }

    private CompoundTag encodeCase(Case aCase) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(CASE_VERSION, aCase.schemaVersion());
        tag.putUUID(CASE_ID, aCase.caseId().value());
        tag.putLong(CASE_SEQ, aCase.caseSeq());
        tag.putString(CASE_TYPE, aCase.caseType().name());
        tag.putString(TITLE, aCase.title());
        tag.putString(DESCRIPTION, aCase.description());
        tag.putUUID(PLAINTIFF_REF, aCase.plaintiffRef());
        aCase.defendantRef().ifPresent(ref -> tag.putUUID(DEFENDANT_REF, ref));
        aCase.sourceReportId().ifPresent(id -> tag.putLong(SOURCE_REPORT_ID, id));
        tag.putString(STATE, aCase.state().name());
        tag.putString(COURT_LEVEL, aCase.courtLevel().name());
        tag.putLong(CREATED_AT, aCase.createdAt());
        tag.putLong(RECORD_REVISION, aCase.recordRevision());
        return tag;
    }

    private CompoundTag encodeEvidence(Evidence item) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(EVIDENCE_VERSION, item.schemaVersion());
        tag.putUUID(EVIDENCE_ID, item.evidenceId().value());
        tag.putUUID(CASE_ID, item.caseId().value());
        tag.putLong(EVIDENCE_SEQ, item.evidenceSeq());
        tag.putUUID(SUBMITTED_BY_REF, item.submittedByRef());
        tag.putString(DESCRIPTION, item.description());
        item.integrityDigest().ifPresent(digest -> tag.putString(INTEGRITY_DIGEST, digest));
        tag.putString(STATE, item.state().name());
        tag.putLong(SUBMITTED_AT, item.submittedAt());
        item.admittedAt().ifPresent(at -> tag.putLong(ADMITTED_AT, at));
        tag.putLong(RECORD_REVISION, item.recordRevision());
        return tag;
    }

    private CompoundTag encodeVerdict(Verdict verdict) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(VERDICT_VERSION, verdict.schemaVersion());
        tag.putUUID(VERDICT_ID, verdict.verdictId().value());
        tag.putUUID(CASE_ID, verdict.caseId().value());
        tag.putString(OUTCOME, verdict.outcome().name());
        tag.putString(REASONING, verdict.reasoning());
        tag.putUUID(JUDGE_REF, verdict.judgeRef());
        tag.putString(LEVEL, verdict.level().name());
        tag.putLong(ISSUED_AT, verdict.issuedAt());
        tag.putLong(RECORD_REVISION, verdict.recordRevision());
        return tag;
    }

    private CompoundTag encodeTransition(TransitionRecord transition) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TRANSITION_VERSION, transition.schemaVersion());
        tag.putUUID(CASE_ID, transition.caseId().value());
        tag.putUUID(ACTOR, transition.actor());
        tag.putLong(AT_MILLIS, transition.atMillis());
        tag.putString(TRIGGER, transition.trigger().name());
        transition.before().ifPresent(state -> tag.putString(BEFORE, state.name()));
        tag.putString(AFTER, transition.after().name());
        tag.putLong(RECORD_REVISION, transition.recordRevision());
        return tag;
    }

    // ------------------------------------------------------------------
    // size
    // ------------------------------------------------------------------

    /** Serialized (uncompressed) size of an encoded namespace snapshot. */
    public int encodedSize(CompoundTag snapshot) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeUnnamedTag(snapshot, new DataOutputStream(out));
            return out.size();
        } catch (IOException failure) {
            return Integer.MAX_VALUE;
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private CaseId parseCaseId(CompoundTag tag, String key, String path) {
        return CaseId.of(tag.getUUID(key));
    }

    private CaseId parseCanonicalId(String value, String index) {
        return CaseId.of(parseCanonicalUuid(value, index));
    }

    private EvidenceId parseEvidenceId(String value) {
        return EvidenceId.of(parseCanonicalUuid(value, EVIDENCE));
    }

    private VerdictId parseVerdictId(String value) {
        return VerdictId.of(parseCanonicalUuid(value, VERDICTS));
    }

    private UUID parseCanonicalUuid(String value, String path) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw invalid(path + " key is not canonical: " + value);
            }
            return parsed;
        } catch (IllegalArgumentException failure) {
            if (failure instanceof JusticeNbtException nbtFailure) {
                throw nbtFailure;
            }
            throw invalid("Invalid " + path + " key: " + value, failure);
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException failure) {
            throw invalid("Unsupported " + field + " value: " + value);
        }
    }

    private static void requireType(CompoundTag tag, String key, int type, String path) {
        if (!tag.contains(key, type)) {
            throw invalid(
                    path + " is missing required field " + key + " or has the wrong type"
            );
        }
    }

    private static void requireOnlyKeys(CompoundTag tag, Set<String> allowed, String path) {
        for (String key : tag.getAllKeys()) {
            if (!allowed.contains(key)) {
                throw invalid(path + " contains unsupported field " + key);
            }
        }
    }

    private static JusticeNbtException invalid(String message) {
        return new JusticeNbtException(message);
    }

    private static JusticeNbtException invalid(String message, Throwable cause) {
        return new JusticeNbtException(message, cause);
    }
}
