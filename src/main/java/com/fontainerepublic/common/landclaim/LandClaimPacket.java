package com.fontainerepublic.common.landclaim;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * C2S land claim request (FR-LAND-CLAIM-001-A §4, message ledger ID 25).
 *
 * <p>Asks the server to create a republic-owned parcel at the block and grant
 * the sending player a non-expiring usage right. The payload is fully bounded:
 * {@code dimension} is a ≤ {@value #MAX_DIMENSION} canonical resource key and
 * the coordinates are plain {@code int} block coordinates. Every authority
 * rule (online player, communicator gate, current dimension, loading, reach,
 * exact-point coverage, full-region overlap and the single atomic durable
 * commit) is re-validated server-side — a phone can never claim a remote,
 * other-dimension, or unloaded position.</p>
 */
public record LandClaimPacket(
        String dimension,
        int x,
        int y,
        int z
) {

    public static final int MAX_DIMENSION = 64;

    public LandClaimPacket {
        dimension = Objects.requireNonNull(dimension, "dimension");
        if (dimension.isEmpty() || dimension.length() > MAX_DIMENSION) {
            throw new NetworkPayloadException(
                    "Dimension must be 1.." + MAX_DIMENSION + " characters"
            );
        }
    }

    public static void encode(LandClaimPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        NetworkPayloadLimits.writeUtf(buffer, message.dimension(), MAX_DIMENSION);
        buffer.writeInt(message.x());
        buffer.writeInt(message.y());
        buffer.writeInt(message.z());
    }

    public static LandClaimPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        String dimension = NetworkPayloadLimits.readUtf(buffer, MAX_DIMENSION);
        int x = buffer.readInt();
        int y = buffer.readInt();
        int z = buffer.readInt();
        return new LandClaimPacket(dimension, x, y, z);
    }
}
