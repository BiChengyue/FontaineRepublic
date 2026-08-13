package com.fontainerepublic.server.land.api;

import com.fontainerepublic.server.land.model.ParcelId;
import com.fontainerepublic.server.registry.model.OwnerReference;

import java.util.Objects;

/**
 * Authoritative violation-report draft (FR-LAND-001-A §3.4/§4).
 *
 * <p>Reports are created against an existing parcel by a typed reporter
 * holder; the description is bounded and the report is read-only once
 * created (adjudication belongs to the future Justice module).</p>
 *
 * @param parcelId    the parcel the report is about
 * @param reporter    the reporting holder
 * @param description bounded human description of the violation
 */
public record ViolationDraft(
        ParcelId parcelId,
        OwnerReference reporter,
        String description
) {

    public ViolationDraft {
        parcelId = Objects.requireNonNull(parcelId, "parcelId");
        reporter = Objects.requireNonNull(reporter, "reporter");
        description = Objects.requireNonNull(description, "description");
    }
}
