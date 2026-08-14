package com.fontainerepublic.common.landrights;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.server.land.api.MyUsageRightsQueryLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * C2S self-only bounded my-usage-rights page request (FR-LAND-002-A §7,
 * message ledger ID 27).
 *
 * <p>Strictly carries only the transport/correlation fields and the self-only
 * query controls: a positive {@code requestId}, an optional canonical
 * {@code ParcelId} exclusive cursor (its UUID representation), the
 * {@code expectedStoreRevision} whose mismatch forces a fresh first page, and a
 * bounded {@code limit}. There is intentionally <b>no target identity</b> —
 * no target UUID, subject id, name, or {@code OwnerReference} — so a client can
 * never query another player's rights.</p>
 *
 * <p>The cursor and store revision are paired (FR-LAND-002-A §4.2): a first
 * page carries no cursor and {@code expectedStoreRevision == 0}, while every
 * continuation carries a cursor and the returned positive store revision. An
 * invalid pairing (cursor with revision 0, or no cursor with a non-zero
 * revision) is rejected at construction, and therefore also at decode which
 * funnels through the same constructor. Every bound is enforced at construction
 * and decode (positive requestId, canonical cursor UUID, otherwise invalid).
 * Note {@code afterParcelId} is the canonical lowercase-hyphenated UUID form of
 * the {@code ParcelId}.</p>
 */
public record MyLandRightsRequestPacket(
        int requestId,
        Optional<UUID> afterParcelId,
        long expectedStoreRevision,
        int limit
) {

    public static final int MAX_LIMIT = 32;

    public MyLandRightsRequestPacket {
        if (requestId <= 0) {
            throw new NetworkPayloadException(
                    "requestId must be positive: " + requestId
            );
        }
        if (expectedStoreRevision < 0) {
            throw new NetworkPayloadException(
                    "expectedStoreRevision must not be negative: "
                            + expectedStoreRevision
            );
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new NetworkPayloadException(
                    "limit must be within [1, " + MAX_LIMIT + "]: " + limit
            );
        }
        afterParcelId = Objects.requireNonNull(afterParcelId, "afterParcelId");
        afterParcelId.ifPresent(MyLandRightsRequestPacket::requireCanonicalUuid);
        // Cursor/revision pairing: first page (no cursor) must use revision 0;
        // a continuation (cursor) must use the returned positive revision.
        // Rejected here so both construction and decode reject the invalid form.
        if (!MyUsageRightsQueryLimits.isValidCursorRevision(
                afterParcelId.isPresent(), expectedStoreRevision)) {
            throw new NetworkPayloadException(
                    "a cursor requires its returned positive store revision, and "
                            + "a first page must carry no cursor and revision 0"
            );
        }
    }

    public static void encode(
            MyLandRightsRequestPacket message,
            FriendlyByteBuf buffer
    ) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(message.requestId());
        buffer.writeBoolean(message.afterParcelId().isPresent());
        message.afterParcelId().ifPresent(buffer::writeUUID);
        buffer.writeLong(message.expectedStoreRevision());
        buffer.writeVarInt(message.limit());
    }

    public static MyLandRightsRequestPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        int requestId = buffer.readVarInt();
        Optional<UUID> afterParcelId =
                buffer.readBoolean()
                        ? Optional.of(buffer.readUUID())
                        : Optional.empty();
        long expectedStoreRevision = buffer.readLong();
        int limit = buffer.readVarInt();
        return new MyLandRightsRequestPacket(
                requestId,
                afterParcelId,
                expectedStoreRevision,
                limit
        );
    }

    private static void requireCanonicalUuid(UUID value) {
        Objects.requireNonNull(value, "afterParcelId");
        // UUID.toString() is always lowercase canonical; reject any hand-rolled
        // non-canonical representation eagerly.
        if (!value.toString().equals(value.toString().toLowerCase(java.util.Locale.ROOT))) {
            throw new NetworkPayloadException(
                    "afterParcelId must be a canonical UUID: " + value
            );
        }
    }
}
