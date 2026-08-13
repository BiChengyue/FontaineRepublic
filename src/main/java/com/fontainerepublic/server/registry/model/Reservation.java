package com.fontainerepublic.server.registry.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Permanent reservation projection of a number (FR-ID-001-A §6.2).
 *
 * <p>The two Human-fixed numbers carry a reservation from the first valid
 * snapshot, even before their subject records materialize. A reservation is
 * immutable once written: deletion, reassignment, rotation, transfer, and
 * manual correction of a number are forbidden.</p>
 *
 * @param number         the reserved canonical number
 * @param kind           reservation kind
 * @param boundSubjectId subject that materialized this number, if any
 */
public record Reservation(
        RegistryNumber number,
        ReservationKind kind,
        Optional<SubjectId> boundSubjectId
) {

    public Reservation {
        number = Objects.requireNonNull(number, "number");
        kind = Objects.requireNonNull(kind, "kind");
        boundSubjectId = Objects.requireNonNull(boundSubjectId, "boundSubjectId");
    }

    public static Reservation unboundFixed(RegistryNumber number) {
        return new Reservation(number, ReservationKind.FIXED, Optional.empty());
    }

    public static Reservation fixedTo(RegistryNumber number, SubjectId subjectId) {
        return new Reservation(
                number,
                ReservationKind.FIXED,
                Optional.of(Objects.requireNonNull(subjectId, "subjectId"))
        );
    }
}
