package com.fontainerepublic.server.land.model;

import com.fontainerepublic.server.registry.model.OwnerReference;

import java.util.Objects;

/**
 * Immutable, value-style violation report entry (FR-LAND-001-A §3.4).
 *
 * <p>{@code reportId} is server-assigned and immutable; {@code reporter} is a
 * typed holder reference; {@code description} is bounded (validated at the
 * service/repository boundary and capped by a hard structural limit at
 * decode). A report is created {@code OPEN} and is read-only in FR-LAND —
 * there is no update/delete/close API; adjudication belongs to Justice.</p>
 */
public record ViolationReport(
        int schemaVersion,
        long reportId,
        ParcelId parcelId,
        OwnerReference reporter,
        String description,
        long reportedAt,
        ViolationStatus status
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ViolationReport {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported violation report schema version: " + schemaVersion
            );
        }
        if (reportId <= 0) {
            throw new IllegalArgumentException("reportId must be positive");
        }
        parcelId = Objects.requireNonNull(parcelId, "parcelId");
        reporter = Objects.requireNonNull(reporter, "reporter");
        description = Objects.requireNonNull(description, "description");
        if (description.isEmpty()) {
            throw new IllegalArgumentException("description must not be empty");
        }
        if (reportedAt <= 0) {
            throw new IllegalArgumentException(
                    "reportedAt must be a positive epoch millisecond"
            );
        }
        status = Objects.requireNonNull(status, "status");
    }
}
