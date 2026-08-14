package com.fontainerepublic.common.landclaim;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.UUID;

/**
 * S2C result of a land inspect (FR-LAND-CLAIM-001-A §4, message ledger ID 24).
 *
 * <p>Carries display data only — never an authority decision: either
 * {@code claimable == true} (unowned, the client may offer a claim step), or a
 * summary of the parcel already covering the checked block
 * ({@code parcelId} + inclusive region + zone + access), or a stable bounded
 * {@code code} describing a fail-closed gate (wrong dimension, not loaded,
 * out of reach, ...). Every bound is enforced at construction and decode so
 * the wire never carries an out-of-range value.</p>
 *
 * <p>FR-LAND-CLAIM-001-FIX-01 F3: the packet echoes the canonical request
 * target {@code dimension + x + y + z} so the {@code LandLocationScreen}
 * can ignore any late result for a different screen; the echoed target is
 * strictly validated (dimension bounded ≤ {@value #MAX_DIMENSION} and the
 * coordinates are plain ints).</p>
 */
public record LandInspectResultPacket(
        String dimension,
        int x,
        int y,
        int z,
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
        long at
) {

    public static final int MAX_CODE = 64;
    public static final int MAX_ZONE = 32;
    public static final int MAX_ACCESS = 32;
    public static final int MAX_DIMENSION = 64;

    public LandInspectResultPacket {
        dimension = Objects.requireNonNull(dimension, "dimension");
        if (dimension.isEmpty() || dimension.length() > MAX_DIMENSION) {
            throw new NetworkPayloadException(
                    "Dimension must be 1.." + MAX_DIMENSION + " characters"
            );
        }
        code = Objects.requireNonNull(code, "code");
        if (code.isEmpty() || code.length() > MAX_CODE) {
            throw new NetworkPayloadException(
                    "Code must be 1.." + MAX_CODE + " characters"
            );
        }
        if (claimable && (parcelId != null || zoneType != null || access != null)) {
            throw new NetworkPayloadException(
                    "A claimable result must not carry a parcel summary"
            );
        }
        if (zoneType != null
                && (zoneType.isEmpty() || zoneType.length() > MAX_ZONE)) {
            throw new NetworkPayloadException(
                    "Zone must be 1.." + MAX_ZONE + " characters"
            );
        }
        if (access != null
                && (access.isEmpty() || access.length() > MAX_ACCESS)) {
            throw new NetworkPayloadException(
                    "Access must be 1.." + MAX_ACCESS + " characters"
            );
        }
        if (at <= 0) {
            throw new NetworkPayloadException("at must be a positive timestamp: " + at);
        }
    }

    public static void encode(LandInspectResultPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        NetworkPayloadLimits.writeUtf(buffer, message.dimension(), MAX_DIMENSION);
        buffer.writeInt(message.x());
        buffer.writeInt(message.y());
        buffer.writeInt(message.z());
        NetworkPayloadLimits.writeUtf(buffer, message.code(), MAX_CODE);
        buffer.writeBoolean(message.claimable());
        buffer.writeBoolean(message.parcelId() != null);
        if (message.parcelId() != null) {
            buffer.writeUUID(message.parcelId());
            buffer.writeInt(message.minX());
            buffer.writeInt(message.minY());
            buffer.writeInt(message.minZ());
            buffer.writeInt(message.maxX());
            buffer.writeInt(message.maxY());
            buffer.writeInt(message.maxZ());
            NetworkPayloadLimits.writeUtf(buffer, message.zoneType(), MAX_ZONE);
            NetworkPayloadLimits.writeUtf(buffer, message.access(), MAX_ACCESS);
        }
        buffer.writeLong(message.at());
    }

    public static LandInspectResultPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        String dimension = NetworkPayloadLimits.readUtf(buffer, MAX_DIMENSION);
        int x = buffer.readInt();
        int y = buffer.readInt();
        int z = buffer.readInt();
        String code = NetworkPayloadLimits.readUtf(buffer, MAX_CODE);
        boolean claimable = buffer.readBoolean();
        boolean hasParcel = buffer.readBoolean();
        UUID parcelId = null;
        int minX = 0;
        int minY = 0;
        int minZ = 0;
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        String zoneType = null;
        String access = null;
        if (hasParcel) {
            parcelId = buffer.readUUID();
            minX = buffer.readInt();
            minY = buffer.readInt();
            minZ = buffer.readInt();
            maxX = buffer.readInt();
            maxY = buffer.readInt();
            maxZ = buffer.readInt();
            zoneType = NetworkPayloadLimits.readUtf(buffer, MAX_ZONE);
            access = NetworkPayloadLimits.readUtf(buffer, MAX_ACCESS);
        }
        long at = buffer.readLong();
        return new LandInspectResultPacket(
                dimension, x, y, z,
                claimable, code, parcelId,
                minX, minY, minZ, maxX, maxY, maxZ,
                zoneType, access, at
        );
    }
}
