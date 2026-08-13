package com.fontainerepublic.server.registry.persistence;

import com.fontainerepublic.server.registry.model.BootstrapAttemptRecord;
import com.fontainerepublic.server.registry.model.BootstrapAttemptResult;
import com.fontainerepublic.server.registry.model.BootstrapDigests;
import com.fontainerepublic.server.registry.model.BootstrapPhase;
import com.fontainerepublic.server.registry.model.BootstrapSourceClassification;
import com.fontainerepublic.server.registry.model.BootstrapState;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.Reservation;
import com.fontainerepublic.server.registry.model.ReservationKind;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectStatus;
import com.fontainerepublic.server.registry.model.SubjectType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Strict, versioned, deterministic NBT codec for the {@code "subject-registry"}
 * namespace (FR-ID-001-A §6.3, FR-ID-BOOTSTRAP-001-A §4).
 *
 * <p>Encoding writes every index in deterministic lexical key order so the
 * same immutable snapshot always produces an equivalent ordered NBT.
 * Decoding accepts only declared fields with exact NBT types, canonical UUIDs
 * and numbers, valid checksums, agreed types, positive timestamps/statuses/
 * revisions, and constructs the validating
 * {@link SubjectRegistryStoreSnapshot} which enforces the bidirectional index,
 * fixed-reservation, and bootstrap-trail invariants. Unknown newer versions
 * are rejected; nothing is ever auto-repaired.</p>
 */
public final class SubjectRegistryNbtCodec {

    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String SUBJECTS = "Subjects";
    private static final String NUMBERS = "Numbers";
    private static final String OWNERS = "Owners";
    private static final String RESERVATIONS = "Reservations";
    private static final String BOOTSTRAP_STATE = "BootstrapState";
    private static final String BOOTSTRAP_ATTEMPTS = "BootstrapAttempts";

    private static final String RECORD_VERSION = "RecordVersion";
    private static final String SUBJECT_ID = "SubjectId";
    private static final String REGISTRY_NUMBER = "RegistryNumber";
    private static final String SUBJECT_TYPE = "SubjectType";
    private static final String OWNER_REFERENCE = "OwnerReference";
    private static final String STATUS = "Status";
    private static final String REVISION = "Revision";
    private static final String CREATED_AT = "CreatedAt";
    private static final String UPDATED_AT = "UpdatedAt";

    private static final String OWNER_KIND = "Kind";
    private static final String OWNER_ID = "OwnerId";

    private static final String RESERVATION_NUMBER = "RegistryNumber";
    private static final String RESERVATION_KIND = "Kind";
    private static final String RESERVATION_BOUND_SUBJECT = "BoundSubjectId";

    private static final String BS_FIXED_RESERVATIONS_ESTABLISHED =
            "FixedReservationsEstablished";
    private static final String BS_OFFICE_SUBJECT_MATERIALIZED =
            "OfficeSubjectMaterialized";
    private static final String BS_ORIGINAL_PERSONAL_BINDING_APPLIED =
            "OriginalPersonalBindingApplied";
    private static final String BS_PHASE = "Phase";
    private static final String BS_BOUND_UUID_DIGEST = "BoundUuidDigest";
    private static final String BS_BOUND_AT = "BoundAt";
    private static final String BS_TRAIL_HEAD_DIGEST = "TrailHeadDigest";

    private static final String ATTEMPT_ID = "AttemptId";
    private static final String ATTEMPT_AT = "At";
    private static final String ATTEMPT_SOURCE = "SourceClassification";
    private static final String ATTEMPT_RESULT = "ResultCode";
    private static final String ATTEMPT_UUID_DIGEST = "UuidDigest";
    private static final String ATTEMPT_REASON_DIGEST = "ReasonDigest";
    private static final String ATTEMPT_PREV_DIGEST = "PrevDigest";
    private static final String ATTEMPT_SELF_DIGEST = "SelfDigest";
    private static final String ATTEMPT_IDEMPOTENCY_KEY = "IdempotencyKey";

    /** Hard structural cap, independent of the configurable budget. */
    private static final int HARD_MAX_SUBJECTS = 100_000;

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION,
            STORE_REVISION,
            SUBJECTS,
            NUMBERS,
            OWNERS,
            RESERVATIONS,
            BOOTSTRAP_STATE,
            BOOTSTRAP_ATTEMPTS
    );
    private static final Set<String> RECORD_KEYS = Set.of(
            RECORD_VERSION,
            SUBJECT_ID,
            REGISTRY_NUMBER,
            SUBJECT_TYPE,
            OWNER_REFERENCE,
            STATUS,
            REVISION,
            CREATED_AT,
            UPDATED_AT
    );
    private static final Set<String> OWNER_KEYS = Set.of(OWNER_KIND, OWNER_ID);
    private static final Set<String> RESERVATION_KEYS = Set.of(
            RESERVATION_NUMBER,
            RESERVATION_KIND,
            RESERVATION_BOUND_SUBJECT
    );
    private static final Set<String> BOOTSTRAP_KEYS = Set.of(
            BS_FIXED_RESERVATIONS_ESTABLISHED,
            BS_OFFICE_SUBJECT_MATERIALIZED,
            BS_ORIGINAL_PERSONAL_BINDING_APPLIED,
            BS_PHASE,
            BS_BOUND_UUID_DIGEST,
            BS_BOUND_AT,
            BS_TRAIL_HEAD_DIGEST
    );
    private static final Set<String> ATTEMPT_KEYS = Set.of(
            ATTEMPT_ID,
            ATTEMPT_AT,
            ATTEMPT_SOURCE,
            ATTEMPT_RESULT,
            ATTEMPT_UUID_DIGEST,
            ATTEMPT_REASON_DIGEST,
            ATTEMPT_PREV_DIGEST,
            ATTEMPT_SELF_DIGEST,
            ATTEMPT_IDEMPOTENCY_KEY
    );

    // ------------------------------------------------------------------
    // decode
    // ------------------------------------------------------------------

    /**
     * Decodes and fully validates a namespace snapshot. Empty input is
     * rejected: the registry requires explicit initialization of the fixed
     * reservations and the office subject.
     */
    public SubjectRegistryStoreSnapshot decode(CompoundTag root) {
        if (root == null || root.isEmpty()) {
            throw invalid(
                    "subject-registry namespace is empty; it must be initialized with "
                            + "the fixed reservations and the office subject"
            );
        }

        requireOnlyKeys(root, STORE_KEYS, "subject-registry");
        requireType(root, STORE_VERSION, Tag.TAG_INT, "subject-registry");
        requireType(root, STORE_REVISION, Tag.TAG_LONG, "subject-registry");
        requireType(root, SUBJECTS, Tag.TAG_COMPOUND, "subject-registry");
        requireType(root, NUMBERS, Tag.TAG_COMPOUND, "subject-registry");
        requireType(root, OWNERS, Tag.TAG_COMPOUND, "subject-registry");
        requireType(root, RESERVATIONS, Tag.TAG_COMPOUND, "subject-registry");
        requireType(root, BOOTSTRAP_STATE, Tag.TAG_COMPOUND, "subject-registry");

        int storeVersion = root.getInt(STORE_VERSION);
        if (storeVersion != SubjectRegistryStoreSnapshot.CURRENT_STORE_VERSION) {
            throw invalid(
                    "Unsupported subject-registry store version: " + storeVersion
            );
        }
        long storeRevision = root.getLong(STORE_REVISION);
        if (storeRevision < 0) {
            throw invalid("StoreRevision must not be negative");
        }

        Map<SubjectId, SubjectRecord> subjects = decodeSubjects(
                root.getCompound(SUBJECTS)
        );
        Map<RegistryNumber, SubjectId> numbers = decodeNumbers(
                root.getCompound(NUMBERS)
        );
        Map<OwnerReference, SubjectId> owners = decodeOwners(
                root.getCompound(OWNERS)
        );
        Map<RegistryNumber, Reservation> reservations = decodeReservations(
                root.getCompound(RESERVATIONS)
        );
        BootstrapState bootstrapState = decodeBootstrapState(
                root.getCompound(BOOTSTRAP_STATE)
        );
        Map<String, BootstrapAttemptRecord> bootstrapAttempts = decodeBootstrapAttempts(
                root.contains(BOOTSTRAP_ATTEMPTS)
                        ? root.getCompound(BOOTSTRAP_ATTEMPTS)
                        : new CompoundTag()
        );

        return new SubjectRegistryStoreSnapshot(
                storeVersion,
                storeRevision,
                subjects,
                numbers,
                owners,
                reservations,
                bootstrapState,
                bootstrapAttempts
        );
    }

    private Map<SubjectId, SubjectRecord> decodeSubjects(CompoundTag tag) {
        if (tag.getAllKeys().size() > HARD_MAX_SUBJECTS) {
            throw invalid("Subject count exceeds " + HARD_MAX_SUBJECTS);
        }
        Map<SubjectId, SubjectRecord> subjects = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            SubjectId subjectId = parseCanonicalSubjectId(key);
            requireType(tag, key, Tag.TAG_COMPOUND, "Subjects");
            SubjectRecord record = decodeRecord(
                    tag.getCompound(key),
                    subjectId
            );
            if (subjects.put(subjectId, record) != null) {
                throw invalid("Duplicate subject id: " + subjectId);
            }
        }
        return subjects;
    }

    private SubjectRecord decodeRecord(CompoundTag tag, SubjectId expectedSubjectId) {
        requireOnlyKeys(tag, RECORD_KEYS, "subject " + expectedSubjectId);
        requireType(tag, RECORD_VERSION, Tag.TAG_INT, "subject " + expectedSubjectId);
        requireType(tag, SUBJECT_ID, Tag.TAG_INT_ARRAY, "subject " + expectedSubjectId);
        requireType(tag, REGISTRY_NUMBER, Tag.TAG_STRING, "subject " + expectedSubjectId);
        requireType(tag, SUBJECT_TYPE, Tag.TAG_STRING, "subject " + expectedSubjectId);
        requireType(tag, OWNER_REFERENCE, Tag.TAG_COMPOUND, "subject " + expectedSubjectId);
        requireType(tag, STATUS, Tag.TAG_STRING, "subject " + expectedSubjectId);
        requireType(tag, REVISION, Tag.TAG_LONG, "subject " + expectedSubjectId);
        requireType(tag, CREATED_AT, Tag.TAG_LONG, "subject " + expectedSubjectId);
        requireType(tag, UPDATED_AT, Tag.TAG_LONG, "subject " + expectedSubjectId);

        int recordVersion = tag.getInt(RECORD_VERSION);
        if (recordVersion != SubjectRecord.CURRENT_SCHEMA_VERSION) {
            throw invalid(
                    "Unsupported subject record version for " + expectedSubjectId
                            + ": " + recordVersion
            );
        }

        UUID storedUuid = tag.getUUID(SUBJECT_ID);
        if (!expectedSubjectId.equals(SubjectId.of(storedUuid))) {
            throw invalid(
                    "Subjects key " + expectedSubjectId
                            + " does not match record identity " + storedUuid
            );
        }

        RegistryNumber number;
        try {
            number = RegistryNumber.parse(tag.getString(REGISTRY_NUMBER));
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid RegistryNumber for " + expectedSubjectId + ": "
                            + failure.getMessage(),
                    failure
            );
        }

        SubjectType subjectType = enumValue(
                SubjectType.class,
                tag.getString(SUBJECT_TYPE),
                "SubjectType for " + expectedSubjectId
        );
        SubjectStatus status = enumValue(
                SubjectStatus.class,
                tag.getString(STATUS),
                "Status for " + expectedSubjectId
        );
        OwnerReference owner = decodeOwner(
                tag.getCompound(OWNER_REFERENCE),
                expectedSubjectId
        );

        try {
            return new SubjectRecord(
                    recordVersion,
                    expectedSubjectId,
                    number,
                    subjectType,
                    owner,
                    status,
                    tag.getLong(REVISION),
                    tag.getLong(CREATED_AT),
                    tag.getLong(UPDATED_AT)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid subject record " + expectedSubjectId + ": "
                            + failure.getMessage(),
                    failure
            );
        }
    }

    private OwnerReference decodeOwner(CompoundTag tag, SubjectId subjectId) {
        requireOnlyKeys(tag, OWNER_KEYS, "owner of " + subjectId);
        requireType(tag, OWNER_KIND, Tag.TAG_STRING, "owner of " + subjectId);
        requireType(tag, OWNER_ID, Tag.TAG_STRING, "owner of " + subjectId);
        try {
            return OwnerReference.parseKey(
                    tag.getString(OWNER_KIND) + ":" + tag.getString(OWNER_ID)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid owner reference for " + subjectId + ": "
                            + failure.getMessage(),
                    failure
            );
        }
    }

    private Map<RegistryNumber, SubjectId> decodeNumbers(CompoundTag tag) {
        Map<RegistryNumber, SubjectId> numbers = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            requireType(tag, key, Tag.TAG_STRING, "Numbers");
            RegistryNumber number;
            try {
                number = RegistryNumber.parse(key);
            } catch (IllegalArgumentException failure) {
                throw invalid("Invalid number index key " + key, failure);
            }
            SubjectId subjectId = parseCanonicalSubjectId(tag.getString(key));
            if (numbers.put(number, subjectId) != null) {
                throw invalid("Duplicate number index: " + number);
            }
        }
        return numbers;
    }

    private Map<OwnerReference, SubjectId> decodeOwners(CompoundTag tag) {
        Map<OwnerReference, SubjectId> owners = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            requireType(tag, key, Tag.TAG_STRING, "Owners");
            OwnerReference owner;
            try {
                owner = OwnerReference.parseKey(key);
            } catch (IllegalArgumentException failure) {
                throw invalid("Invalid owner index key " + key, failure);
            }
            SubjectId subjectId = parseCanonicalSubjectId(tag.getString(key));
            if (owners.put(owner, subjectId) != null) {
                throw invalid("Duplicate owner index: " + owner.key());
            }
        }
        return owners;
    }

    private Map<RegistryNumber, Reservation> decodeReservations(CompoundTag tag) {
        Map<RegistryNumber, Reservation> reservations = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            requireType(tag, key, Tag.TAG_COMPOUND, "Reservations");
            RegistryNumber number;
            try {
                number = RegistryNumber.parse(key);
            } catch (IllegalArgumentException failure) {
                throw invalid("Invalid reservation key " + key, failure);
            }
            CompoundTag reservationTag = tag.getCompound(key);
            requireOnlyKeys(reservationTag, RESERVATION_KEYS, "reservation " + key);
            requireType(reservationTag, RESERVATION_NUMBER, Tag.TAG_STRING, "reservation " + key);
            requireType(reservationTag, RESERVATION_KIND, Tag.TAG_STRING, "reservation " + key);
            requireOptionalType(
                    reservationTag,
                    RESERVATION_BOUND_SUBJECT,
                    Tag.TAG_INT_ARRAY,
                    "reservation " + key
            );

            if (!reservationTag.getString(RESERVATION_NUMBER).equals(number.canonical())) {
                throw invalid(
                        "Reservation key " + number + " does not match its stored number "
                                + reservationTag.getString(RESERVATION_NUMBER)
                );
            }
            ReservationKind kind = enumValue(
                    ReservationKind.class,
                    reservationTag.getString(RESERVATION_KIND),
                    "Reservation kind for " + number
            );
            Optional<SubjectId> bound = reservationTag.contains(RESERVATION_BOUND_SUBJECT)
                    ? Optional.of(
                            SubjectId.of(reservationTag.getUUID(RESERVATION_BOUND_SUBJECT))
                    )
                    : Optional.empty();
            Reservation reservation = new Reservation(number, kind, bound);
            if (reservations.put(number, reservation) != null) {
                throw invalid("Duplicate reservation: " + number);
            }
        }
        return reservations;
    }

    private BootstrapState decodeBootstrapState(CompoundTag tag) {
        requireOnlyKeys(tag, BOOTSTRAP_KEYS, "BootstrapState");
        requireType(tag, BS_FIXED_RESERVATIONS_ESTABLISHED, Tag.TAG_BYTE, "BootstrapState");
        requireType(tag, BS_OFFICE_SUBJECT_MATERIALIZED, Tag.TAG_BYTE, "BootstrapState");
        requireType(tag, BS_ORIGINAL_PERSONAL_BINDING_APPLIED, Tag.TAG_BYTE, "BootstrapState");

        boolean fixed = tag.getBoolean(BS_FIXED_RESERVATIONS_ESTABLISHED);
        boolean office = tag.getBoolean(BS_OFFICE_SUBJECT_MATERIALIZED);
        boolean applied = tag.getBoolean(BS_ORIGINAL_PERSONAL_BINDING_APPLIED);

        // The original-person binding fields are optional for backward
        // compatibility with FR-ID-001 snapshots; when present they must be
        // consistent (enforced by the BootstrapState constructor).
        BootstrapPhase phase = tag.contains(BS_PHASE)
                ? enumValue(BootstrapPhase.class, tag.getString(BS_PHASE), "BootstrapState phase")
                : BootstrapPhase.UNBOUND;
        byte[] boundUuidDigest = tag.contains(BS_BOUND_UUID_DIGEST)
                ? requireDigest(tag, BS_BOUND_UUID_DIGEST, "BootstrapState")
                : null;
        long boundAt = tag.getLong(BS_BOUND_AT);
        byte[] trailHeadDigest = tag.contains(BS_TRAIL_HEAD_DIGEST)
                ? requireDigest(tag, BS_TRAIL_HEAD_DIGEST, "BootstrapState")
                : null;

        try {
            return new BootstrapState(
                    fixed,
                    office,
                    applied,
                    phase,
                    boundUuidDigest,
                    boundAt,
                    trailHeadDigest
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid BootstrapState: " + failure.getMessage(),
                    failure
            );
        }
    }

    private Map<String, BootstrapAttemptRecord> decodeBootstrapAttempts(CompoundTag tag) {
        Map<String, BootstrapAttemptRecord> attempts = new LinkedHashMap<>();
        for (String key : tag.getAllKeys().stream().sorted().toList()) {
            if (!key.equals(key.toLowerCase(java.util.Locale.ROOT))) {
                throw invalid("BootstrapAttempts key is not canonical: " + key);
            }
            requireType(tag, key, Tag.TAG_COMPOUND, "BootstrapAttempts");
            BootstrapAttemptRecord record = decodeAttempt(
                    tag.getCompound(key),
                    key
            );
            if (attempts.put(key, record) != null) {
                throw invalid("Duplicate bootstrap attempt id: " + key);
            }
        }
        return attempts;
    }

    private BootstrapAttemptRecord decodeAttempt(CompoundTag tag, String expectedKey) {
        requireOnlyKeys(tag, ATTEMPT_KEYS, "bootstrap attempt " + expectedKey);
        requireType(tag, ATTEMPT_ID, Tag.TAG_INT_ARRAY, "bootstrap attempt " + expectedKey);
        requireType(tag, ATTEMPT_AT, Tag.TAG_LONG, "bootstrap attempt " + expectedKey);
        requireType(tag, ATTEMPT_SOURCE, Tag.TAG_STRING, "bootstrap attempt " + expectedKey);
        requireType(tag, ATTEMPT_RESULT, Tag.TAG_STRING, "bootstrap attempt " + expectedKey);
        requireType(tag, ATTEMPT_UUID_DIGEST, Tag.TAG_BYTE_ARRAY, "bootstrap attempt " + expectedKey);
        requireType(tag, ATTEMPT_REASON_DIGEST, Tag.TAG_BYTE_ARRAY, "bootstrap attempt " + expectedKey);
        requireType(tag, ATTEMPT_PREV_DIGEST, Tag.TAG_BYTE_ARRAY, "bootstrap attempt " + expectedKey);
        requireType(tag, ATTEMPT_SELF_DIGEST, Tag.TAG_BYTE_ARRAY, "bootstrap attempt " + expectedKey);
        requireType(tag, ATTEMPT_IDEMPOTENCY_KEY, Tag.TAG_STRING, "bootstrap attempt " + expectedKey);

        UUID attemptId = tag.getUUID(ATTEMPT_ID);
        if (!expectedKey.equals(attemptId.toString())) {
            throw invalid(
                    "BootstrapAttempts key " + expectedKey
                            + " does not match record attemptId " + attemptId
            );
        }
        BootstrapSourceClassification source = enumValue(
                BootstrapSourceClassification.class,
                tag.getString(ATTEMPT_SOURCE),
                "SourceClassification for " + expectedKey
        );
        BootstrapAttemptResult result = enumValue(
                BootstrapAttemptResult.class,
                tag.getString(ATTEMPT_RESULT),
                "ResultCode for " + expectedKey
        );
        byte[] uuidDigest = requireDigest(tag, ATTEMPT_UUID_DIGEST, "bootstrap attempt " + expectedKey);
        byte[] reasonDigest = requireDigest(tag, ATTEMPT_REASON_DIGEST, "bootstrap attempt " + expectedKey);
        byte[] prevDigest = requireDigest(tag, ATTEMPT_PREV_DIGEST, "bootstrap attempt " + expectedKey);
        byte[] storedSelfDigest = requireDigest(tag, ATTEMPT_SELF_DIGEST, "bootstrap attempt " + expectedKey);

        try {
            return new BootstrapAttemptRecord(
                    attemptId,
                    tag.getLong(ATTEMPT_AT),
                    source,
                    result,
                    uuidDigest,
                    reasonDigest,
                    prevDigest,
                    storedSelfDigest,
                    tag.getString(ATTEMPT_IDEMPOTENCY_KEY)
            );
        } catch (IllegalArgumentException failure) {
            throw invalid(
                    "Invalid bootstrap attempt " + expectedKey + ": "
                            + failure.getMessage(),
                    failure
            );
        }
    }

    private static byte[] requireDigest(CompoundTag tag, String key, String path) {
        byte[] digest = tag.getByteArray(key);
        if (digest.length != BootstrapDigests.DIGEST_LENGTH) {
            throw invalid(
                    path + " field " + key + " must be exactly "
                            + BootstrapDigests.DIGEST_LENGTH + " bytes"
            );
        }
        return digest;
    }

    // ------------------------------------------------------------------
    // encode
    // ------------------------------------------------------------------

    /**
     * Encodes a validated snapshot deterministically: every index is written
     * in sorted lexical key order.
     */
    public CompoundTag encode(SubjectRegistryStoreSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(STORE_VERSION, snapshot.storeVersion());
        root.putLong(STORE_REVISION, snapshot.storeRevision());

        CompoundTag subjectsTag = new CompoundTag();
        TreeMap<String, SubjectRecord> orderedSubjects = new TreeMap<>();
        snapshot.subjects().forEach(
                (subjectId, record) -> orderedSubjects.put(subjectId.canonicalKey(), record)
        );
        orderedSubjects.forEach(
                (key, record) -> subjectsTag.put(key, encodeRecord(record))
        );
        root.put(SUBJECTS, subjectsTag);

        CompoundTag numbersTag = new CompoundTag();
        TreeMap<String, SubjectId> orderedNumbers = new TreeMap<>();
        snapshot.numbers().forEach(
                (number, subjectId) -> orderedNumbers.put(number.canonical(), subjectId)
        );
        orderedNumbers.forEach(
                (number, subjectId) -> numbersTag.putString(number, subjectId.canonicalKey())
        );
        root.put(NUMBERS, numbersTag);

        CompoundTag ownersTag = new CompoundTag();
        TreeMap<String, SubjectId> orderedOwners = new TreeMap<>();
        snapshot.owners().forEach(
                (owner, subjectId) -> orderedOwners.put(owner.key(), subjectId)
        );
        orderedOwners.forEach(
                (key, subjectId) -> ownersTag.putString(key, subjectId.canonicalKey())
        );
        root.put(OWNERS, ownersTag);

        CompoundTag reservationsTag = new CompoundTag();
        TreeMap<String, Reservation> orderedReservations = new TreeMap<>();
        snapshot.reservations().forEach(
                (number, reservation) -> orderedReservations.put(number.canonical(), reservation)
        );
        orderedReservations.forEach(
                (number, reservation) -> reservationsTag.put(
                        number,
                        encodeReservation(reservation)
                )
        );
        root.put(RESERVATIONS, reservationsTag);

        root.put(BOOTSTRAP_STATE, encodeBootstrapState(snapshot.bootstrapState()));

        CompoundTag attemptsTag = new CompoundTag();
        TreeMap<String, BootstrapAttemptRecord> orderedAttempts = new TreeMap<>();
        snapshot.bootstrapAttempts().forEach(
                (attemptId, record) -> orderedAttempts.put(attemptId, record)
        );
        orderedAttempts.forEach(
                (attemptId, record) -> attemptsTag.put(attemptId, encodeAttempt(record))
        );
        root.put(BOOTSTRAP_ATTEMPTS, attemptsTag);
        return root;
    }

    private CompoundTag encodeRecord(SubjectRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(RECORD_VERSION, record.schemaVersion());
        tag.putUUID(SUBJECT_ID, record.subjectId().value());
        tag.putString(REGISTRY_NUMBER, record.registryNumber().canonical());
        tag.putString(SUBJECT_TYPE, record.subjectType().name());
        tag.put(OWNER_REFERENCE, encodeOwner(record.ownerReference()));
        tag.putString(STATUS, record.status().name());
        tag.putLong(REVISION, record.revision());
        tag.putLong(CREATED_AT, record.createdAt());
        tag.putLong(UPDATED_AT, record.updatedAt());
        return tag;
    }

    private CompoundTag encodeOwner(OwnerReference owner) {
        CompoundTag tag = new CompoundTag();
        tag.putString(OWNER_KIND, owner.kind().name());
        tag.putString(OWNER_ID, owner.ownerId());
        return tag;
    }

    private CompoundTag encodeReservation(Reservation reservation) {
        CompoundTag tag = new CompoundTag();
        tag.putString(RESERVATION_NUMBER, reservation.number().canonical());
        tag.putString(RESERVATION_KIND, reservation.kind().name());
        reservation.boundSubjectId().ifPresent(
                subjectId -> tag.putUUID(RESERVATION_BOUND_SUBJECT, subjectId.value())
        );
        return tag;
    }

    private CompoundTag encodeBootstrapState(BootstrapState state) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(BS_FIXED_RESERVATIONS_ESTABLISHED, state.fixedReservationsEstablished());
        tag.putBoolean(BS_OFFICE_SUBJECT_MATERIALIZED, state.officeSubjectMaterialized());
        tag.putBoolean(
                BS_ORIGINAL_PERSONAL_BINDING_APPLIED,
                state.originalPersonalBindingApplied()
        );
        tag.putString(BS_PHASE, state.phase().name());
        if (state.boundUuidDigest() != null) {
            tag.putByteArray(BS_BOUND_UUID_DIGEST, state.boundUuidDigest());
        }
        if (state.boundAt() != 0) {
            tag.putLong(BS_BOUND_AT, state.boundAt());
        }
        if (state.trailHeadDigest() != null) {
            tag.putByteArray(BS_TRAIL_HEAD_DIGEST, state.trailHeadDigest());
        }
        return tag;
    }

    private CompoundTag encodeAttempt(BootstrapAttemptRecord attempt) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ATTEMPT_ID, attempt.attemptId());
        tag.putLong(ATTEMPT_AT, attempt.at());
        tag.putString(ATTEMPT_SOURCE, attempt.source().name());
        tag.putString(ATTEMPT_RESULT, attempt.resultCode().name());
        tag.putByteArray(ATTEMPT_UUID_DIGEST, attempt.uuidDigest());
        tag.putByteArray(ATTEMPT_REASON_DIGEST, attempt.reasonDigest());
        tag.putByteArray(ATTEMPT_PREV_DIGEST, attempt.prevDigest());
        tag.putByteArray(ATTEMPT_SELF_DIGEST, attempt.selfDigest());
        tag.putString(ATTEMPT_IDEMPOTENCY_KEY, attempt.idempotencyKey());
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

    private SubjectId parseCanonicalSubjectId(String value) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw invalid("Subject id key is not canonical: " + value);
            }
            return SubjectId.of(parsed);
        } catch (IllegalArgumentException failure) {
            if (failure instanceof SubjectRegistryNbtException nbtFailure) {
                throw nbtFailure;
            }
            throw invalid("Invalid subject id key: " + value, failure);
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

    private static void requireOptionalType(CompoundTag tag, String key, int type, String path) {
        if (tag.contains(key) && !tag.contains(key, type)) {
            throw invalid(path + " field " + key + " has the wrong NBT type");
        }
    }

    private static void requireOnlyKeys(CompoundTag tag, Set<String> allowed, String path) {
        for (String key : tag.getAllKeys()) {
            if (!allowed.contains(key)) {
                throw invalid(path + " contains unsupported field " + key);
            }
        }
    }

    private static SubjectRegistryNbtException invalid(String message) {
        return new SubjectRegistryNbtException(message);
    }

    private static SubjectRegistryNbtException invalid(String message, Throwable cause) {
        return new SubjectRegistryNbtException(message, cause);
    }
}
