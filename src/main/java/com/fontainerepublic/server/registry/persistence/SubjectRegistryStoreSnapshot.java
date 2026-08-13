package com.fontainerepublic.server.registry.persistence;

import com.fontainerepublic.server.registry.model.BootstrapState;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.OwnerReferenceKind;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.Reservation;
import com.fontainerepublic.server.registry.model.ReservationKind;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectType;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable, fully validated representation of the complete
 * {@code "subject-registry"} namespace (FR-ID-001-A §6).
 *
 * <p>The constructor enforces the authoritative bidirectional invariants — no
 * partially consistent snapshot can exist:</p>
 * <ul>
 *   <li>every {@code Subjects} key matches its record's {@code SubjectId};</li>
 *   <li>every {@code Numbers} entry points to an existing subject whose number
 *       equals the index key;</li>
 *   <li>every {@code Owners} entry points to an existing subject whose typed
 *       owner key equals the index key and whose owner kind agrees with its
 *       subject type;</li>
 *   <li>both Human-fixed numbers carry a {@link ReservationKind#FIXED}
 *       reservation; the office number is bound to the materialized
 *       {@code OFFICE_ID:HYDRO_ARCHON} office subject; the original personal
 *       number stays unbound (bootstrap is a separately approved future task)
 *       and no other record may claim it.</li>
 * </ul>
 * <p>Duplicates, orphans, mismatches, or bootstrap state this implementation
 * does not support reject the whole snapshot (fail closed).</p>
 */
public record SubjectRegistryStoreSnapshot(
        int storeVersion,
        long storeRevision,
        Map<SubjectId, SubjectRecord> subjects,
        Map<RegistryNumber, SubjectId> numbers,
        Map<OwnerReference, SubjectId> owners,
        Map<RegistryNumber, Reservation> reservations,
        BootstrapState bootstrapState
) {

    public static final int CURRENT_STORE_VERSION = 1;

    public SubjectRegistryStoreSnapshot {
        if (storeVersion != CURRENT_STORE_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported subject-registry store version: " + storeVersion
            );
        }
        if (storeRevision < 0) {
            throw new IllegalArgumentException("storeRevision must not be negative");
        }
        subjects = Map.copyOf(Objects.requireNonNull(subjects, "subjects"));
        numbers = Map.copyOf(Objects.requireNonNull(numbers, "numbers"));
        owners = Map.copyOf(Objects.requireNonNull(owners, "owners"));
        reservations = Map.copyOf(Objects.requireNonNull(reservations, "reservations"));
        bootstrapState = Objects.requireNonNull(bootstrapState, "bootstrapState");

        validateBidirectionalIndexes(subjects, numbers, owners);
        validateFixedReservations(subjects, numbers, reservations, bootstrapState);
    }

    // ------------------------------------------------------------------
    // bidirectional index validation (FR-ID-001-A §6.2)
    // ------------------------------------------------------------------

    private static void validateBidirectionalIndexes(
            Map<SubjectId, SubjectRecord> subjects,
            Map<RegistryNumber, SubjectId> numbers,
            Map<OwnerReference, SubjectId> owners
    ) {
        for (Map.Entry<SubjectId, SubjectRecord> entry : subjects.entrySet()) {
            SubjectId key = entry.getKey();
            SubjectRecord record = entry.getValue();
            if (!key.equals(record.subjectId())) {
                throw invalid(
                        "Subjects key " + key + " does not match record subjectId "
                                + record.subjectId()
                );
            }
            if (!numbers.containsKey(record.registryNumber())) {
                throw invalid(
                        "Subject " + key + " has no matching number index for "
                                + record.registryNumber()
                );
            }
            if (!numbers.get(record.registryNumber()).equals(key)) {
                throw invalid(
                        "Number index " + record.registryNumber() + " does not point back to "
                                + key
                );
            }
            if (!owners.containsKey(record.ownerReference())) {
                throw invalid(
                        "Subject " + key + " has no matching owner index for "
                                + record.ownerReference().key()
                );
            }
            if (!owners.get(record.ownerReference()).equals(key)) {
                throw invalid(
                        "Owner index " + record.ownerReference().key()
                                + " does not point back to " + key
                );
            }
        }

        for (Map.Entry<RegistryNumber, SubjectId> entry : numbers.entrySet()) {
            RegistryNumber number = entry.getKey();
            SubjectRecord record = subjects.get(entry.getValue());
            if (record == null) {
                throw invalid(
                        "Number index " + number + " points to a missing subject "
                                + entry.getValue()
                );
            }
            if (!record.registryNumber().equals(number)) {
                throw invalid(
                        "Number index " + number + " does not match subject number "
                                + record.registryNumber()
                );
            }
        }

        for (Map.Entry<OwnerReference, SubjectId> entry : owners.entrySet()) {
            OwnerReference owner = entry.getKey();
            SubjectRecord record = subjects.get(entry.getValue());
            if (record == null) {
                throw invalid(
                        "Owner index " + owner.key() + " points to a missing subject "
                                + entry.getValue()
                );
            }
            if (!record.ownerReference().key().equals(owner.key())) {
                throw invalid(
                        "Owner index " + owner.key() + " does not match subject owner "
                                + record.ownerReference().key()
                );
            }
            requireOwnerTypeRelation(record.subjectType(), record.ownerReference());
        }

        for (Map.Entry<SubjectId, SubjectRecord> entry : subjects.entrySet()) {
            SubjectRecord record = entry.getValue();
            requireOwnerTypeRelation(record.subjectType(), record.ownerReference());
        }
    }

    private static void requireOwnerTypeRelation(SubjectType subjectType, OwnerReference owner) {
        OwnerReferenceKind expected = switch (subjectType) {
            case NATURAL_PERSON -> OwnerReferenceKind.PLAYER_UUID;
            case HYDRO_ARCHON_OFFICE -> OwnerReferenceKind.OFFICE_ID;
        };
        if (owner.kind() != expected) {
            throw invalid(
                    "Subject type " + subjectType + " requires a " + expected
                            + " owner, got " + owner.kind()
            );
        }
    }

    // ------------------------------------------------------------------
    // fixed reservations (FR-ID-001-A §4.4/§4.5/§17.1)
    // ------------------------------------------------------------------

    private static void validateFixedReservations(
            Map<SubjectId, SubjectRecord> subjects,
            Map<RegistryNumber, SubjectId> numbers,
            Map<RegistryNumber, Reservation> reservations,
            BootstrapState bootstrapState
    ) {
        Reservation personal = reservations.get(RegistryNumber.FIXED_PERSONAL);
        if (personal == null || personal.kind() != ReservationKind.FIXED) {
            throw invalid(
                    "Registry must reserve the fixed personal number "
                            + RegistryNumber.FIXED_PERSONAL.display()
            );
        }
        Reservation office = reservations.get(RegistryNumber.FIXED_OFFICE);
        if (office == null || office.kind() != ReservationKind.FIXED) {
            throw invalid(
                    "Registry must reserve the fixed office number "
                            + RegistryNumber.FIXED_OFFICE.display()
            );
        }

        // Original personal subject (10-000001-61) requires the separately
        // approved audited bootstrap; this implementation must not see one.
        if (personal.boundSubjectId().isPresent()) {
            throw invalid(
                    "Original personal binding is not supported by this implementation "
                            + "(requires the approved bootstrap design); number "
                            + RegistryNumber.FIXED_PERSONAL.display() + " is already bound"
            );
        }
        if (numbers.containsKey(RegistryNumber.FIXED_PERSONAL)) {
            throw invalid(
                    "Number " + RegistryNumber.FIXED_PERSONAL.display()
                            + " is claimed by a subject but the original personal binding "
                            + "is not implemented"
            );
        }

        // The office subject must be materialized idempotently from its
        // constant owner reference.
        SubjectId officeId = office.boundSubjectId().orElseThrow(
                () -> invalid(
                        "Fixed office number " + RegistryNumber.FIXED_OFFICE.display()
                                + " must be bound to its materialized office subject"
                )
        );
        SubjectRecord officeRecord = subjects.get(officeId);
        if (officeRecord == null) {
            throw invalid("Office reservation points to a missing subject " + officeId);
        }
        if (officeRecord.subjectType() != SubjectType.HYDRO_ARCHON_OFFICE) {
            throw invalid(
                    "Office subject " + officeId + " must be type HYDRO_ARCHON_OFFICE"
            );
        }
        if (!officeRecord.ownerReference().equals(OwnerReference.HYDRO_ARCHON_OFFICE)) {
            throw invalid(
                    "Office subject " + officeId + " must be owned by "
                            + OwnerReference.HYDRO_ARCHON_OFFICE.key()
            );
        }
        if (!officeRecord.registryNumber().equals(RegistryNumber.FIXED_OFFICE)) {
            throw invalid(
                    "Office subject " + officeId + " must carry number "
                            + RegistryNumber.FIXED_OFFICE.display()
            );
        }
        SubjectId indexedOffice = numbers.get(RegistryNumber.FIXED_OFFICE);
        if (!officeId.equals(indexedOffice)) {
            throw invalid(
                    "Number index for " + RegistryNumber.FIXED_OFFICE.display()
                            + " must point to the office subject " + officeId
            );
        }

        if (!bootstrapState.fixedReservationsEstablished()) {
            throw invalid("BootstrapState must confirm fixed reservations are established");
        }
        if (!bootstrapState.officeSubjectMaterialized()) {
            throw invalid("BootstrapState must confirm the office subject is materialized");
        }
        if (bootstrapState.originalPersonalBindingApplied()) {
            throw invalid(
                    "BootstrapState claims an applied original personal binding, "
                            + "which is not supported by this implementation "
                            + "(requires the approved bootstrap design)"
            );
        }
    }

    private static SubjectRegistryNbtException invalid(String message) {
        return new SubjectRegistryNbtException(message);
    }
}
