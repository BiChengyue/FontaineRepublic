package com.fontainerepublic.common.landclaim;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * C2S land inspect request (FR-LAND-CLAIM-001-A §4, message ledger ID 23).
 *
 * <p>Asks the server whether the block at the given dimension/coordinates is
 * claimable (unowned) or covered by an existing parcel. The payload is fully
 * bounded: {@code dimension} is a ≤ {@value #MAX_DIMENSION} canonical resource
 * key and the coordinates are plain {@code int} block coordinates. The server
 * re-validates the dimension against the player's current world, loading,
 * reach and communicator gate — none of the client's claims are authoritative.</p>
 */
public record LandInspectPacket(
        String dimension,
        int x,
        int y,
        int z
) {

    public static final int MAX_DIMENSION = 64;

    public LandInspectPacket {
        dimension = Objects.requireNonNull(dimension, "dimension");
        if (dimension.isEmpty() || dimension.length() > MAX_DIMENSION) {
            throw new NetworkPayloadException(
                    "Dimension must be 1.." + MAX_DIMENSION + " characters"
            );
        }
    }

    public static void encode(LandInspectPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        NetworkPayloadLimits.writeUtf(buffer, message.dimension(), MAX_DIMENSION);
        buffer.writeInt(message.x());
        buffer.writeInt(message.y());
        buffer.writeInt(message.z());
    }

    public static LandInspectPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        String dimension = NetworkPayloadLimits.readUtf(buffer, MAX_DIMENSION);
        int x = buffer.readInt();
        int y = buffer.readInt();
        int z = buffer.readInt();
        return new LandInspectPacket(dimension, x, y, z);
    }
}
