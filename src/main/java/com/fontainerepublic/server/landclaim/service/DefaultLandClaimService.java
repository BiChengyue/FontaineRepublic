package com.fontainerepublic.server.landclaim.service;

import com.fontainerepublic.server.land.api.CreateParcelRequest;
import com.fontainerepublic.server.land.api.LandService;
import com.fontainerepublic.server.land.api.UsageReceipt;
import com.fontainerepublic.server.land.model.LandAccess;
import com.fontainerepublic.server.land.model.LandParcel;
import com.fontainerepublic.server.land.model.ParcelRegion;
import com.fontainerepublic.server.land.model.ZoneType;
import com.fontainerepublic.server.land.persistence.LandUnavailableException;
import com.fontainerepublic.server.landclaim.api.ClaimReceipt;
import com.fontainerepublic.server.landclaim.api.InspectResult;
import com.fontainerepublic.server.landclaim.api.LandClaimService;
import com.fontainerepublic.server.landclaim.api.ServerLandClaimPlayerAccess;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Server-authoritative implementation of the communicator land claim
 * (FR-LAND-CLAIM-001-A §3.2/§3.3): one shared fail-closed gate and one single
 * atomic durable commit.
 *
 * <p>Both the C2S handlers and the no-client {@code /fr land inspect|claim}
 * commands funnel through this service, so a phone message and a command are
 * indistinguishable to the server and no OP bypass or reach/loading/dimension
 * loophole exists. The default claimed parcel is a {@code 3x3} surface (the
 * configured half-width, clamped to its bounds) spanning
 * {@code [y, y + height - 1]} blocks, zone {@code RESIDENTIAL} (config,
 * invalid falls back) and access {@code PRIVATE} — a valid usage right is
 * granted in the same single snapshot, so the claimant can build immediately.</p>
 */
public final class DefaultLandClaimService implements LandClaimService {

    /** Valid resource location for world dimensions. */
    private static final java.util.regex.Pattern DIMENSION_PATTERN =
            java.util.regex.Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    private static final int MAX_HALF_WIDTH = 16;
    private static final int MIN_HEIGHT = 1;
    private static final int MAX_HEIGHT = 64;

    private final LandService land;
    private final ServerLandClaimPlayerAccess playerAccess;
    private final LongSupplier clock;
    private final int halfWidth;
    private final int height;
    private final ZoneType zoneType;

    public DefaultLandClaimService(
            LandService land,
            ServerLandClaimPlayerAccess playerAccess,
            LongSupplier clock,
            int halfWidth,
            int height,
            String zoneTypeName
    ) {
        this.land = Objects.requireNonNull(land, "land");
        this.playerAccess = Objects.requireNonNull(playerAccess, "playerAccess");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.halfWidth = boundHalfWidth(halfWidth);
        this.height = boundHeight(height);
        this.zoneType = resolveZoneType(zoneTypeName);
    }

    @Override
    public InspectResult inspect(UUID actor, String dimension, int x, int y, int z) {
        Objects.requireNonNull(actor, "actor");
        long at = now();
        Gate gate = gate(actor, dimension, x, y, z);
        if (gate != Gate.OK) {
            return InspectResult.failed(gate.code(), at);
        }
        Optional<LandParcel> parcel = land.parcelAt(dimension, x, y, z);
        if (parcel.isPresent()) {
            LandParcel existing = parcel.get();
            ParcelRegion region = existing.region();
            return InspectResult.owned(
                    existing.parcelId().value(),
                    region.minX(), region.minY(), region.minZ(),
                    region.maxX(), region.maxY(), region.maxZ(),
                    existing.zoneType().name(),
                    existing.access().name(),
                    at
            );
        }
        // The clicked block itself is unowned, but the default parcel region a
        // claim would build may still overlap an adjacent parcel. Recompute the
        // exact same clamped region as claim() so the inspect promise is
        // truthful: CLAIM_OVERLAP / CLAIM_INVALID instead of a misleading
        // claimable=true. This is presentation only — never authoritative; the
        // authoritative overlap decision stays inside createParcelWithUsage.
        ParcelRegion planned = claimRegion(actor, x, y, z);
        if (planned == null) {
            return InspectResult.failed(InspectResult.CODE_INVALID, at);
        }
        if (land.overlaps(dimension, planned)) {
            return InspectResult.failed(InspectResult.CODE_OVERLAP, at);
        }
        return InspectResult.claimable(at);
    }

    @Override
    public ClaimReceipt claim(UUID actor, String dimension, int x, int y, int z) {
        Objects.requireNonNull(actor, "actor");
        long at = now();
        Gate gate = gate(actor, dimension, x, y, z);
        if (gate != Gate.OK) {
            return ClaimReceipt.failed(gate.code(), at);
        }
        if (land.parcelAt(dimension, x, y, z).isPresent()) {
            return ClaimReceipt.failed(ClaimReceipt.CODE_ALREADY_OWNED, at);
        }
        ParcelRegion region = claimRegion(actor, x, y, z);
        if (region == null) {
            return ClaimReceipt.failed(ClaimReceipt.CODE_INVALID, at);
        }
        CreateParcelRequest request = new CreateParcelRequest(
                dimension,
                region,
                zoneType,
                LandAccess.PRIVATE
        );
        OwnerReference holder = OwnerReference.forPlayer(actor);
        try {
            UsageReceipt receipt = land.createParcelWithUsage(actor, request, holder, 0);
            LandParcel parcel = receipt.parcel();
            return ClaimReceipt.ok(
                    parcel.parcelId().value(),
                    parcel.region().minX(), parcel.region().minY(), parcel.region().minZ(),
                    parcel.region().maxX(), parcel.region().maxY(), parcel.region().maxZ(),
                    parcel.zoneType(),
                    parcel.access(),
                    at
            );
        } catch (LandUnavailableException failure) {
            return ClaimReceipt.failed(claimCode(failure), at);
        }
    }

    // ------------------------------------------------------------------
    // internals: the single shared fail-closed gate
    // ------------------------------------------------------------------

    private enum Gate {
        OK(null),
        NOT_PLAYER(ClaimReceipt.CODE_NOT_PLAYER),
        WRONG_DIMENSION(ClaimReceipt.CODE_WRONG_DIMENSION),
        NOT_HOLDING(ClaimReceipt.CODE_NOT_HOLDING),
        NOT_LOADED(ClaimReceipt.CODE_NOT_LOADED),
        OUT_OF_REACH(ClaimReceipt.CODE_OUT_OF_REACH),
        INVALID(ClaimReceipt.CODE_INVALID);

        private final String code;

        Gate(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }
    }

    private Gate gate(UUID actor, String dimension, int x, int y, int z) {
        if (!playerAccess.isOnline(actor)) {
            return Gate.NOT_PLAYER;
        }
        if (!isCanonicalDimension(dimension)) {
            return Gate.INVALID;
        }
        if (!dimension.equals(playerAccess.currentDimension(actor))) {
            return Gate.WRONG_DIMENSION;
        }
        if (!playerAccess.holdsCommunicator(actor)) {
            return Gate.NOT_HOLDING;
        }
        if (!playerAccess.isBlockLoaded(actor, x, y, z)) {
            return Gate.NOT_LOADED;
        }
        if (!playerAccess.withinBlockReach(actor, x, y, z)) {
            return Gate.OUT_OF_REACH;
        }
        return Gate.OK;
    }

    /**
     * Builds the default claimed region centered on the clicked block with
     * the configured half-width and height, clamped into the player's current
     * world build bounds (FR-LAND-CLAIM-001-A §3.2). Returns null when the
     * clamped region would be empty (invalid world bounds).
     */
    private ParcelRegion claimRegion(UUID actor, int x, int y, int z) {
        int worldMinY = playerAccess.worldMinY(actor);
        int worldMaxY = playerAccess.worldMaxY(actor);
        int minY = Math.max(y, worldMinY);
        long rawMaxY = (long) y + height - 1L;
        // Both operands are already int-bounded: the min picks the smaller of a
        // long sum and an int worldMaxY, so the check result always fits in an int.
        int maxY = toIntWithinBounds(Math.min(rawMaxY, worldMaxY));
        if (maxY < minY) {
            return null;
        }
        int minX = x - halfWidth;
        int maxX = x + halfWidth;
        int minZ = z - halfWidth;
        int maxZ = z + halfWidth;
        try {
            return new ParcelRegion(minX, minY, minZ, maxX, (int) maxY, maxZ);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    /** Maps a land-service failure to a stable claim code. */
    private static String claimCode(LandUnavailableException failure) {
        return switch (failure.failureCode()) {
            case LandUnavailableException.CODE_OVERLAP -> ClaimReceipt.CODE_OVERLAP;
            case LandUnavailableException.CODE_CAPACITY_EXCEEDED -> ClaimReceipt.CODE_CAPACITY;
            case LandUnavailableException.CODE_STORE_FAILURE -> ClaimReceipt.CODE_STORE;
            case LandUnavailableException.CODE_INVALID_REQUEST -> ClaimReceipt.CODE_INVALID;
            case LandUnavailableException.CODE_PLAYER_NOT_PROVISIONED,
                    LandUnavailableException.CODE_INVALID_HOLDER,
                    LandUnavailableException.CODE_HOLDER_DIRECTORY_UNAVAILABLE,
                    LandUnavailableException.CODE_PLAYER_DATA_UNAVAILABLE,
                    LandUnavailableException.CODE_SUBJECT_REGISTRY_UNAVAILABLE ->
                    ClaimReceipt.CODE_NO_SUBJECT;
            case LandUnavailableException.CODE_DUPLICATE_GRANT -> ClaimReceipt.CODE_ALREADY_OWNED;
            default -> ClaimReceipt.CODE_UNAVAILABLE;
        };
    }

    private static boolean isCanonicalDimension(String dimension) {
        return dimension != null && DIMENSION_PATTERN.matcher(dimension).matches();
    }

    /**
     * Checked, safe conversion of a long that is guaranteed by its call site to
     * already lie within int bounds. Throws rather than silently truncating if
     * the invariant is ever violated.
     */
    private static int toIntWithinBounds(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalStateException("computed Y coordinate exceeds int bounds: " + value);
        }
        return (int) value;
    }

    private static int boundHalfWidth(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, MAX_HALF_WIDTH);
    }

    private static int boundHeight(int value) {
        if (value < MIN_HEIGHT) {
            return MIN_HEIGHT;
        }
        return Math.min(value, MAX_HEIGHT);
    }

    /** Resolves a zone-type enum name, falling back to RESIDENTIAL. */
    private static ZoneType resolveZoneType(String name) {
        if (name == null || name.isBlank()) {
            return ZoneType.RESIDENTIAL;
        }
        try {
            return ZoneType.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return ZoneType.RESIDENTIAL;
        }
    }

    private long now() {
        long value = clock.getAsLong();
        if (value <= 0) {
            throw new IllegalStateException("clock returned a non-positive timestamp");
        }
        return value;
    }
}
