package com.fontainerepublic.server.landclaim.api;

import com.fontainerepublic.server.land.model.LandAccess;
import com.fontainerepublic.server.land.model.ZoneType;

import java.util.Objects;
import java.util.UUID;

/**
 * Result of a land claim (FR-LAND-CLAIM-001-A §3.2). Either a successful
 * {@code CLAIM_OK} carrying the created parcel summary, or a fail-closed
 * stable error {@code code} (bounded string, never a player-facing detail
 * beyond a stable code). Strictly validated: a successful receipt always
 * carries a non-null parcel id, region and zone; a failure always carries a
 * non-empty bounded code and no parcel id.
 */
public record ClaimReceipt(
        boolean success,
        String code,
        UUID parcelId,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        String zoneType,
        String access,
        long atMillis
) {

    /** Maximum length of a stable claim error code. */
    public static final int MAX_CODE = 64;

    // Successful status code.
    public static final String CODE_OK = "CLAIM_OK";

    // Fail-closed stable gate codes.
    public static final String CODE_ALREADY_OWNED = "CLAIM_ALREADY_OWNED";
    public static final String CODE_OVERLAP = "CLAIM_OVERLAP";
    public static final String CODE_NOT_PLAYER = "CLAIM_NOT_PLAYER";
    public static final String CODE_NOT_HOLDING = "CLAIM_NOT_HOLDING";
    public static final String CODE_WRONG_DIMENSION = "CLAIM_WRONG_DIMENSION";
    public static final String CODE_NOT_LOADED = "CLAIM_NOT_LOADED";
    public static final String CODE_OUT_OF_REACH = "CLAIM_OUT_OF_REACH";
    public static final String CODE_INVALID = "CLAIM_INVALID";
    public static final String CODE_NO_SUBJECT = "CLAIM_NO_SUBJECT";
    public static final String CODE_NO_PLAYER = "CLAIM_NO_PLAYER";
    public static final String CODE_CAPACITY = "CLAIM_CAPACITY";
    public static final String CODE_STORE = "CLAIM_STORE";
    public static final String CODE_UNAVAILABLE = "CLAIM_UNAVAILABLE";

    public ClaimReceipt {
        if (atMillis <= 0) {
            throw new IllegalArgumentException(
                    "Claim receipt timestamp must be positive: " + atMillis
            );
        }
        code = Objects.requireNonNull(code, "code");
        if (code.isEmpty() || code.length() > MAX_CODE) {
            throw new IllegalArgumentException(
                    "Claim code must be 1.." + MAX_CODE + " characters"
            );
        }
        if (success) {
            Objects.requireNonNull(parcelId, "parcelId of a success");
            Objects.requireNonNull(zoneType, "zoneType of a success");
            Objects.requireNonNull(access, "access of a success");
        } else {
            if (parcelId != null || zoneType != null || access != null) {
                throw new IllegalArgumentException(
                        "A failed claim must not carry parcel summary fields"
                );
            }
        }
    }

    /** A successful claim result. */
    public static ClaimReceipt ok(
            UUID parcelId,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            ZoneType zoneType,
            LandAccess access,
            long atMillis
    ) {
        return new ClaimReceipt(
                true,
                CODE_OK,
                Objects.requireNonNull(parcelId, "parcelId"),
                minX, minY, minZ, maxX, maxY, maxZ,
                zoneType.name(),
                access.name(),
                atMillis
        );
    }

    /** A fail-closed claim result with a stable bounded code. */
    public static ClaimReceipt failed(String code, long atMillis) {
        return new ClaimReceipt(
                false,
                code,
                null,
                0, 0, 0, 0, 0, 0,
                null,
                null,
                atMillis
        );
    }
}
