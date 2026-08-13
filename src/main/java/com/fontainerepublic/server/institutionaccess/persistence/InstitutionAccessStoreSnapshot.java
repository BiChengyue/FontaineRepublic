package com.fontainerepublic.server.institutionaccess.persistence;

import com.fontainerepublic.server.institutionaccess.model.Facility;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.Terminal;
import com.fontainerepublic.server.institutionaccess.model.TerminalId;
import com.fontainerepublic.server.land.model.ParcelId;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable, fully validated representation of the complete
 * {@code institution-access} namespace (FR-INST-002-A §3).
 *
 * <p>The constructor enforces the authoritative invariants — no partially
 * consistent snapshot can exist:</p>
 * <ul>
 *   <li>every {@code Facilities} key matches its facility's canonical id;</li>
 *   <li>every {@code Terminals} key matches its terminal's canonical id;</li>
 *   <li>every terminal references an existing facility;</li>
 *   <li>no duplicate facility or terminal id;</li>
 *   <li>no two facilities share the same FR-LAND parcel (single binding);</li>
 *   <li>no two terminals share the same anchored position (anti-clone).</li>
 * </ul>
 * <p>Violations reject the whole snapshot (fail closed).</p>
 */
public record InstitutionAccessStoreSnapshot(
        int storeVersion,
        long storeRevision,
        Map<FacilityId, Facility> facilities,
        Map<TerminalId, Terminal> terminals
) {

    public static final int CURRENT_STORE_VERSION = 1;

    public InstitutionAccessStoreSnapshot {
        if (storeVersion != CURRENT_STORE_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported institution-access store version: " + storeVersion
            );
        }
        if (storeRevision < 0) {
            throw new IllegalArgumentException("storeRevision must not be negative");
        }
        facilities = Map.copyOf(Objects.requireNonNull(facilities, "facilities"));
        terminals = Map.copyOf(Objects.requireNonNull(terminals, "terminals"));

        Map<ParcelId, FacilityId> parcelBindings = new java.util.HashMap<>();
        for (Map.Entry<FacilityId, Facility> entry : facilities.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().facilityId())) {
                throw invalid(
                        "Facilities key " + entry.getKey()
                                + " does not match facility id "
                                + entry.getValue().facilityId()
                );
            }
            FacilityId previous = parcelBindings.put(
                    entry.getValue().parcelId(),
                    entry.getKey()
            );
            if (previous != null) {
                throw invalid(
                        "Parcel " + entry.getValue().parcelId()
                                + " is bound to multiple facilities ("
                                + previous + " and " + entry.getKey() + ")"
                );
            }
        }
        Map<String, TerminalId> anchoredPositions = new java.util.HashMap<>();
        for (Map.Entry<TerminalId, Terminal> entry : terminals.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().terminalId())) {
                throw invalid(
                        "Terminals key " + entry.getKey()
                                + " does not match terminal id "
                                + entry.getValue().terminalId()
                );
            }
            if (!facilities.containsKey(entry.getValue().facilityId())) {
                throw invalid(
                        "Terminal " + entry.getKey() + " references unknown facility "
                                + entry.getValue().facilityId()
                );
            }
            String anchor = entry.getValue().position().dimension()
                    + '|' + entry.getValue().position().x()
                    + '|' + entry.getValue().position().y()
                    + '|' + entry.getValue().position().z();
            TerminalId previous = anchoredPositions.put(anchor, entry.getKey());
            if (previous != null) {
                throw invalid(
                        "Terminals " + previous + " and " + entry.getKey()
                                + " share the same anchored position"
                );
            }
        }
    }

    private static InstitutionAccessNbtException invalid(String message) {
        return new InstitutionAccessNbtException(message);
    }
}
