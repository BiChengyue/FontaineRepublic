package com.fontainerepublic.server.emergency.persistence;

import com.fontainerepublic.server.emergency.model.EmergencyConfigState;
import com.fontainerepublic.server.emergency.model.EmergencyJournalRecord;
import com.fontainerepublic.server.emergency.model.EmergencyReceiptWatermark;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable snapshot of the authoritative {@code "emergency"} namespace
 * (implementation task §3.2).
 *
 * <pre>
 * emergency
 * ├── StoreVersion / StoreRevision
 * ├── Config: authority UUID digest, revision, phase (UNSET/STAGED/ACTIVE)
 * ├── Attempts: segmented append-only journal (PrevDigest/SelfDigest)
 * └── ReceiptIndex: provider watermarks (COMPLETE_THROUGH/INCOMPLETE)
 * </pre>
 */
public record EmergencyStoreSnapshot(
        int storeVersion,
        long storeRevision,
        long nextRecordId,
        EmergencyConfigState config,
        List<EmergencyJournalRecord> records,
        Map<String, EmergencyReceiptWatermark> receiptWatermarks
) {

    public static final int CURRENT_STORE_VERSION = 1;

    public EmergencyStoreSnapshot {
        if (storeVersion != CURRENT_STORE_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported emergency store version: " + storeVersion
            );
        }
        if (storeRevision < 0) {
            throw new IllegalArgumentException("storeRevision must not be negative");
        }
        if (nextRecordId <= 0) {
            throw new IllegalArgumentException("nextRecordId must be positive");
        }
        config = Objects.requireNonNull(config, "config");
        records = List.copyOf(records);
        receiptWatermarks = Map.copyOf(receiptWatermarks);
    }

    public static EmergencyStoreSnapshot empty() {
        return new EmergencyStoreSnapshot(
                CURRENT_STORE_VERSION,
                0L,
                1L,
                EmergencyConfigState.INITIAL,
                List.of(),
                Map.of()
        );
    }
}
