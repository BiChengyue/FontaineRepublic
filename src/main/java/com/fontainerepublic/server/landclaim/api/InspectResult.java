package com.fontainerepublic.server.landclaim.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Bounded result of a land <em>inspect</em> (FR-LAND-CLAIM-001-A §3.1/§4).
 * A single checked position: either the position is claimable ({@code
 * claimable == true}, no parcel covers it) or it is covered by an existing
 * parcel whose summary is carried, or a gate failure is reported through a
 * stable {@code code}. Strictly validated and bounded; never a parcel list and
 * never another player's private detail.
 */
public record InspectResult(
        boolean claimable,
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

    /** Maximum length of a stable inspect/claim code. */
    public static final int MAX_CODE = 64;

    public static final String CODE_OK = "INSPECT_OK";
    public static final String CODE_NOT_PLAYER = "CLAIM_NOT_PLAYER";
    public static final String CODE_NOT_HOLDING = "CLAIM_NOT_HOLDING";
    public static final String CODE_WRONG_DIMENSION = "CLAIM_WRONG_DIMENSION";
    public static final String CODE_NOT_LOADED = "CLAIM_NOT_LOADED";
    public static final String CODE_OUT_OF_REACH = "CLAIM_OUT_OF_REACH";
    public static final String CODE_INVALID = "CLAIM_INVALID";
    public static final String CODE_NO_PLAYER = "CLAIM_NO_PLAYER";
    public static final String CODE_UNAVAILABLE = "CLAIM_UNAVAILABLE";
    public static final String CODE_OVERLAP = "CLAIM_OVERLAP";

    public InspectResult {
        if (atMillis <= 0) {
            throw new IllegalArgumentException(
                    "Inspect result timestamp must be positive: " + atMillis
            );
        }
        code = Objects.requireNonNull(code, "code");
        if (code.isEmpty() || code.length() > MAX_CODE) {
            throw new IllegalArgumentException(
                    "Inspect code must be 1.." + MAX_CODE + " characters"
            );
        }
        if (claimable) {
            if (parcelId != null || zoneType != null || access != null) {
                throw new IllegalArgumentException(
                        "A claimable position must not carry parcel summary fields"
                );
            }
        }
    }

    /** A claimable (unowned) position. */
    public static InspectResult claimable(long atMillis) {
        return new InspectResult(
                true,
                CODE_OK,
                null,
                0, 0, 0, 0, 0, 0,
                null,
                null,
                atMillis
        );
    }

    /** A position already covered by an existing parcel. */
    public static InspectResult owned(
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
        return new InspectResult(
                false,
                CODE_OK,
                Objects.requireNonNull(parcelId, "parcelId"),
                minX, minY, minZ, maxX, maxY, maxZ,
                zoneType,
                access,
                atMillis
        );
    }

    /** A gate failure result with a stable bounded code. */
    public static InspectResult failed(String code, long atMillis) {
        return new InspectResult(
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
