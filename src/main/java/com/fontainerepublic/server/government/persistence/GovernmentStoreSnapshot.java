package com.fontainerepublic.server.government.persistence;

import com.fontainerepublic.server.government.model.GovernmentPosition;
import com.fontainerepublic.server.government.model.Ministry;
import com.fontainerepublic.server.government.model.MinistryId;
import com.fontainerepublic.server.government.model.Office;
import com.fontainerepublic.server.government.model.PositionId;
import com.fontainerepublic.server.government.model.PositionState;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, fully validated representation of the complete
 * {@code "government"} namespace (FR-GOV-001-A §3.4).
 *
 * <p>The constructor enforces the authoritative invariants — no partially
 * consistent snapshot can exist:</p>
 * <ul>
 *   <li>every map key matches its record's canonical id;</li>
 *   <li>every position references an existing ministry (referential
 *       integrity);</li>
 *   <li>a position is {@link PositionState#FILLED} if and only if it carries
 *       a holder (enforced by {@link GovernmentPosition});</li>
 *   <li>at most one office record per position, bound to an existing
 *       position;</li>
 *   <li>a FILLED position has a current (not revoked) office whose holder
 *       matches the position holder; a non-FILLED position has no current
 *       office.</li>
 * </ul>
 * <p>Duplicates, mismatches, dangling references, or state/office
 * inconsistencies reject the whole snapshot (fail closed).</p>
 */
public record GovernmentStoreSnapshot(
        int storeVersion,
        long storeRevision,
        Map<MinistryId, Ministry> ministries,
        Map<PositionId, GovernmentPosition> positions,
        Map<PositionId, Office> offices
) {

    public static final int CURRENT_STORE_VERSION = 1;

    public GovernmentStoreSnapshot {
        if (storeVersion != CURRENT_STORE_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported government store version: " + storeVersion
            );
        }
        if (storeRevision < 0) {
            throw new IllegalArgumentException("storeRevision must not be negative");
        }
        ministries = Map.copyOf(Objects.requireNonNull(ministries, "ministries"));
        positions = Map.copyOf(Objects.requireNonNull(positions, "positions"));
        offices = Map.copyOf(Objects.requireNonNull(offices, "offices"));

        for (Map.Entry<MinistryId, Ministry> entry : ministries.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().ministryId())) {
                throw invalid(
                        "Ministries key " + entry.getKey()
                                + " does not match record ministryId "
                                + entry.getValue().ministryId()
                );
            }
        }
        for (Map.Entry<PositionId, GovernmentPosition> entry : positions.entrySet()) {
            PositionId key = entry.getKey();
            GovernmentPosition position = entry.getValue();
            if (!key.equals(position.positionId())) {
                throw invalid(
                        "Positions key " + key + " does not match record positionId "
                                + position.positionId()
                );
            }
            if (!ministries.containsKey(position.ministryId())) {
                throw invalid(
                        "Position " + key + " references missing ministry "
                                + position.ministryId()
                );
            }
        }
        for (Map.Entry<PositionId, Office> entry : offices.entrySet()) {
            PositionId key = entry.getKey();
            Office office = entry.getValue();
            if (!key.equals(office.positionId())) {
                throw invalid(
                        "Offices key " + key + " does not match record positionId "
                                + office.positionId()
                );
            }
            GovernmentPosition position = positions.get(key);
            if (position == null) {
                throw invalid(
                        "Office " + office.officeId() + " references missing position " + key
                );
            }
            boolean filled = position.state() == PositionState.FILLED;
            if (filled) {
                if (!office.current()) {
                    throw invalid(
                            "FILLED position " + key + " has no current office"
                    );
                }
                if (!office.holderRef().equals(position.holderRef().orElse(null))) {
                    throw invalid(
                            "Office holder of " + key + " does not match the position holder"
                    );
                }
            } else if (office.current()) {
                throw invalid(
                        "Non-FILLED position " + key + " has a current office"
                );
            }
        }
    }

    private static GovernmentNbtException invalid(String message) {
        return new GovernmentNbtException(message);
    }
}
