package com.fontainerepublic.server.registry.model;

import java.util.Objects;

/**
 * Immutable, value-style authoritative subject record (FR-ID-001-A §3.1).
 *
 * <p>{@code subjectId}, {@code registryNumber}, {@code subjectType}, and
 * {@code ownerReference} never change after creation. Status changes replace
 * the record and increment {@code revision} exactly once; {@code updatedAt} is
 * server-assigned and never before creation time.</p>
 */
public record SubjectRecord(
        int schemaVersion,
        SubjectId subjectId,
        RegistryNumber registryNumber,
        SubjectType subjectType,
        OwnerReference ownerReference,
        SubjectStatus status,
        long revision,
        long createdAt,
        long updatedAt
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public SubjectRecord {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported subject record schema version: " + schemaVersion
            );
        }
        subjectId = Objects.requireNonNull(subjectId, "subjectId");
        registryNumber = Objects.requireNonNull(registryNumber, "registryNumber");
        subjectType = Objects.requireNonNull(subjectType, "subjectType");
        ownerReference = Objects.requireNonNull(ownerReference, "ownerReference");
        status = Objects.requireNonNull(status, "status");
        if (revision <= 0) {
            throw new IllegalArgumentException("Subject revision must be positive");
        }
        if (createdAt <= 0) {
            throw new IllegalArgumentException("createdAt must be a positive epoch millisecond");
        }
        if (updatedAt < createdAt) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (!registryNumber.typeCode().equals(subjectType.typeCode())) {
            throw new IllegalArgumentException(
                    "Subject type " + subjectType + " does not agree with number type code "
                            + registryNumber.typeCode()
            );
        }
        requireOwnerTypeRelation(subjectType, ownerReference);
    }

    /**
     * Replacement record with a new status: same identity and number, revision
     * incremented exactly once, updatedAt server-assigned.
     */
    public SubjectRecord withStatus(SubjectStatus newStatus, long newUpdatedAt) {
        Objects.requireNonNull(newStatus, "newStatus");
        return new SubjectRecord(
                schemaVersion,
                subjectId,
                registryNumber,
                subjectType,
                ownerReference,
                newStatus,
                revision + 1,
                createdAt,
                newUpdatedAt
        );
    }

    private static void requireOwnerTypeRelation(SubjectType subjectType, OwnerReference owner) {
        OwnerReferenceKind expected = switch (subjectType) {
            case NATURAL_PERSON -> OwnerReferenceKind.PLAYER_UUID;
            case HYDRO_ARCHON_OFFICE -> OwnerReferenceKind.OFFICE_ID;
        };
        if (owner.kind() != expected) {
            throw new IllegalArgumentException(
                    "Subject type " + subjectType + " requires a " + expected
                            + " owner, got " + owner.kind()
            );
        }
    }
}
