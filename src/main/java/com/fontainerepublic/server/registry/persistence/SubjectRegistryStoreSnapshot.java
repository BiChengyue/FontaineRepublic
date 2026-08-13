package com.fontainerepublic.server.registry.persistence;

import com.fontainerepublic.server.registry.model.BootstrapAttemptRecord;
import com.fontainerepublic.server.registry.model.BootstrapAttemptResult;
import com.fontainerepublic.server.registry.model.BootstrapDigests;
import com.fontainerepublic.server.registry.model.BootstrapPhase;
import com.fontainerepublic.server.registry.model.BootstrapState;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.OwnerReferenceKind;
import com.fontainerepublic.server.registry.model.RegistryNumber;
import com.fontainerepublic.server.registry.model.Reservation;
import com.fontainerepublic.server.registry.model.ReservationKind;
import com.fontainerepublic.server.registry.model.SubjectId;
import com.fontainerepublic.server.registry.model.SubjectRecord;
import com.fontainerepublic.server.registry.model.SubjectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, fully validated representation of the complete
 * {@code "subject-registry"} namespace (FR-ID-001-A §6,
 * FR-ID-BOOTSTRAP-001-A §4).
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
 *       number stays unbound until the approved bootstrap commits (then it is
 *       bound to its natural-person subject);</li>
 *   <li>the bootstrap attempt trail is a valid digest chain whose head agrees
 *       with {@code BootstrapState.trailHeadDigest}, and a BOUND state agrees
 *       with a committed SUCCESS trail record (restart reconciliation,
 *       FR-ID-BOOTSTRAP-001-A §4/§5).</li>
 * </ul>
 * <p>Duplicates, orphans, mismatches, broken or tampered trails, or bootstrap
 * state the registry does not support reject the whole snapshot (fail
 * closed).</p>
 */
public record SubjectRegistryStoreSnapshot(
        int storeVersion,
        long storeRevision,
        Map<SubjectId, SubjectRecord> subjects,
        Map<RegistryNumber, SubjectId> numbers,
        Map<OwnerReference, SubjectId> owners,
        Map<RegistryNumber, Reservation> reservations,
        BootstrapState bootstrapState,
        Map<String, BootstrapAttemptRecord> bootstrapAttempts
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
        bootstrapAttempts = Map.copyOf(
                Objects.requireNonNull(bootstrapAttempts, "bootstrapAttempts")
        );

        validateBidirectionalIndexes(subjects, numbers, owners);
        validateFixedReservations(subjects, numbers, reservations, bootstrapState);
        validateBootstrapTrail(bootstrapAttempts, bootstrapState);
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
    // fixed reservations (FR-ID-001-A §4.4/§4.5/§17.1) + original-person
    // bootstrap (FR-ID-BOOTSTRAP-001-A §5)
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

        if (bootstrapState.phase() == BootstrapPhase.UNBOUND) {
            // Before the original-person bootstrap commits, the personal
            // reservation must stay unbound and no subject may claim the number.
            if (personal.boundSubjectId().isPresent()) {
                throw invalid(
                        "Original personal binding is not committed (phase UNBOUND) "
                                + "but number " + RegistryNumber.FIXED_PERSONAL.display()
                                + " is already bound"
                );
            }
            if (numbers.containsKey(RegistryNumber.FIXED_PERSONAL)) {
                throw invalid(
                        "Number " + RegistryNumber.FIXED_PERSONAL.display()
                                + " is claimed by a subject but the bootstrap "
                                + "phase is UNBOUND"
                );
            }
        } else {
            // BOUND: the personal number must be materialized as a natural
            // person bound to the digest in BootstrapState.
            SubjectId boundId = personal.boundSubjectId().orElseThrow(
                    () -> invalid(
                            "Bootstrap phase is BOUND but the fixed personal number "
                                    + RegistryNumber.FIXED_PERSONAL.display()
                                    + " has no bound subject"
                    )
            );
            SubjectRecord personalSubject = subjects.get(boundId);
            if (personalSubject == null) {
                throw invalid(
                        "Bootstrap phase is BOUND but the personal reservation points "
                                + "to a missing subject " + boundId
                );
            }
            if (personalSubject.subjectType() != SubjectType.NATURAL_PERSON) {
                throw invalid(
                        "Bound original personal subject " + boundId
                                + " must be type NATURAL_PERSON"
                );
            }
            if (!personalSubject.registryNumber().equals(RegistryNumber.FIXED_PERSONAL)) {
                throw invalid(
                        "Bound original personal subject " + boundId
                                + " must carry number "
                                + RegistryNumber.FIXED_PERSONAL.display()
                );
            }
            if (personalSubject.ownerReference().kind() != OwnerReferenceKind.PLAYER_UUID) {
                throw invalid(
                        "Bound original personal subject " + boundId
                                + " must be owned by a player UUID"
                );
            }
            SubjectId indexedPersonal = numbers.get(RegistryNumber.FIXED_PERSONAL);
            if (!boundId.equals(indexedPersonal)) {
                throw invalid(
                        "Number index for " + RegistryNumber.FIXED_PERSONAL.display()
                                + " must point to the bound personal subject " + boundId
                );
            }
            byte[] ownerDigest = BootstrapDigests.uuidDigest(
                    java.util.UUID.fromString(personalSubject.ownerReference().ownerId())
            );
            if (!Arrays.equals(ownerDigest, bootstrapState.boundUuidDigest())) {
                throw invalid(
                        "Bound personal subject owner does not match "
                                + "BootstrapState.boundUuidDigest"
                );
            }
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
    }

    // ------------------------------------------------------------------
    // bootstrap attempt trail validation (FR-ID-BOOTSTRAP-001-A §4/§5:
    // digest chain + restart reconciliation)
    // ------------------------------------------------------------------

    private static void validateBootstrapTrail(
            Map<String, BootstrapAttemptRecord> attempts,
            BootstrapState bootstrapState
    ) {
        if (attempts.isEmpty()) {
            if (bootstrapState.trailHeadDigest() != null) {
                throw invalid(
                        "BootstrapState carries a trail-head digest but the attempt "
                                + "trail is empty"
                );
            }
            if (bootstrapState.phase() == BootstrapPhase.BOUND) {
                throw invalid(
                        "Bootstrap phase is BOUND but the attempt trail is empty "
                                + "(missing SUCCESS record)"
                );
            }
            return;
        }

        // Order the trail by the digest chain: exactly one root whose
        // prevDigest is all-zero; every record links to exactly one successor.
        Map<String, BootstrapAttemptRecord> byId = new HashMap<>(attempts);
        List<BootstrapAttemptRecord> chain = new ArrayList<>(attempts.size());
        BootstrapAttemptRecord current = null;
        for (BootstrapAttemptRecord attempt : attempts.values()) {
            if (Arrays.equals(attempt.prevDigest(), BootstrapDigests.ZERO_DIGEST)) {
                if (current != null) {
                    throw invalid("Bootstrap attempt trail has multiple chain roots");
                }
                current = attempt;
            }
        }
        if (current == null) {
            throw invalid("Bootstrap attempt trail has no chain root (prevDigest all-zero)");
        }
        while (current != null) {
            chain.add(current);
            String key = current.attemptId().toString();
            if (!byId.containsKey(key)) {
                throw invalid("Bootstrap attempt trail contains a non-canonical key");
            }
            byId.remove(key);
            BootstrapAttemptRecord successor = null;
            for (BootstrapAttemptRecord candidate : byId.values()) {
                if (Arrays.equals(candidate.prevDigest(), current.selfDigest())) {
                    if (successor != null) {
                        throw invalid("Bootstrap attempt trail forks at " + current.attemptId());
                    }
                    successor = candidate;
                }
            }
            current = successor;
        }
        if (!byId.isEmpty()) {
            throw invalid(
                    "Bootstrap attempt trail contains records unreachable from the chain root"
            );
        }
        if (chain.size() != attempts.size()) {
            throw invalid("Bootstrap attempt trail chain length mismatch");
        }

        // Trail head must agree with BootstrapState (restart reconciliation).
        BootstrapAttemptRecord head = chain.get(chain.size() - 1);
        if (!Arrays.equals(bootstrapState.trailHeadDigest(), head.selfDigest())) {
            throw invalid(
                    "BootstrapState.trailHeadDigest does not match the last attempt "
                            + "record (tampered or inconsistent trail)"
            );
        }

        // A BOUND state requires a committed SUCCESS record whose target
        // digest matches the bound UUID digest; an UNBOUND state must not
        // contain a SUCCESS record.
        boolean hasSuccess = chain.stream()
                .anyMatch(attempt -> attempt.resultCode() == BootstrapAttemptResult.SUCCESS);
        if (bootstrapState.phase() == BootstrapPhase.BOUND) {
            if (!hasSuccess) {
                throw invalid(
                        "Bootstrap phase is BOUND but the attempt trail contains "
                                + "no SUCCESS record"
                );
            }
            boolean successMatches = chain.stream()
                    .filter(attempt -> attempt.resultCode() == BootstrapAttemptResult.SUCCESS)
                    .allMatch(attempt -> Arrays.equals(
                            attempt.uuidDigest(),
                            bootstrapState.boundUuidDigest()
                    ));
            if (!successMatches) {
                throw invalid(
                        "Bootstrap SUCCESS record target does not match "
                                + "BootstrapState.boundUuidDigest"
                );
            }
        } else {
            if (hasSuccess) {
                throw invalid(
                        "Bootstrap phase is UNBOUND but the attempt trail contains "
                                + "a SUCCESS record (tampered trail)"
                );
            }
        }
    }

    private static SubjectRegistryNbtException invalid(String message) {
        return new SubjectRegistryNbtException(message);
    }
}
