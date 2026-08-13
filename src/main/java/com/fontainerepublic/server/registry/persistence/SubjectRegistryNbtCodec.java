package com.fontainerepublic.server.registry.persistence;

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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Strict, versioned, deterministic NBT codec for the {@code "subject-registry"}
 * namespace (FR-ID-001-A §6.3).
 *
 * <p>Encoding writes every index in deterministic lexical key order so the
 * same immutable snapshot always produces an equivalent ordered NBT.
 * Decoding accepts only declared fields with exact NBT types, canonical UUIDs
 * and numbers, valid checksums, agreed types, positive timestamps/statuses/
 * revisions, and constructs the validating
 * {@link SubjectRegistryStoreSnapshot} which enforces the bidirectional index
 * and fixed-reservation invariants. Unknown newer versions are rejected;
 * nothing is ever auto-repaired.</p>
 */
public final class SubjectRegistryNbtCodec {

    private static final String STORE_VERSION = "StoreVersion";
    private static final String STORE_REVISION = "StoreRevision";
    private static final String SUBJECTS = "Subjects";
    private static final String NUMBERS = "Numbers";
    private static final String OWNERS = "Owners";
    private static final String RESERVATIONS = "Reservations";
    private static final String BOOTSTRAP_STATE = "BootstrapState";

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

    /** Hard structural cap, independent of the configurable budget. */
    private static final int HARD_MAX_SUBJECTS = 100_000;

    private static final Set<String> STORE_KEYS = Set.of(
            STORE_VERSION,
            STORE_REVISION,
            SUBJECTS,
            NUMBERS,
            OWNERS,
            RESERVATIONS,
            BOOTSTRAP_STATE
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
            BS_ORIGINAL_PERSONAL_BINDING_APPLIED
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

        return new SubjectRegistryStoreSnapshot(
                storeVersion,
                storeRevision,
                subjects,
                numbers,
                owners,
                reservations,
                bootstrapState
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
        return new BootstrapState(
                tag.getBoolean(BS_FIXED_RESERVATIONS_ESTABLISHED),
                tag.getBoolean(BS_OFFICE_SUBJECT_MATERIALIZED),
                tag.getBoolean(BS_ORIGINAL_PERSONAL_BINDING_APPLIED)
        );
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
        tag.putBoolean(BS_ORIGINAL_PERSONAL_BINDING_APPLIED, state.originalPersonalBindingApplied());
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
