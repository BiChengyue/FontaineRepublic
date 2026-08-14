package com.fontainerepublic.common.landrights;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import com.fontainerepublic.server.land.model.UsageType;
import com.fontainerepublic.server.land.model.ZoneType;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * S2C bounded page of the caller's own current usage rights (FR-LAND-002-A §7,
 * message ledger ID 28).
 *
 * <p>Carries display data only, for the authenticated player's own rights. A
 * closed status carries zero entries and no cursor/hasMore projection. The
 * {@code status} is one of the closed set
 * {@code OK / RESET_REQUIRED / INVALID_REQUEST / UNAVAILABLE}. The entry count
 * is validated (<b>before</b> any allocation) to be at most
 * {@value #MAX_ENTRIES}; the total encoded payload is bounded by
 * {@link NetworkPayloadLimits#MAX_PAYLOAD_BYTES} at the transport. Every field
 * (canonical parcel UUID, dimension, region bounds, non-negative
 * timestamps/revisions, bounded enum-name strings) is validated at construction
 * and decode, and excess/trailing data is rejected by the transport.</p>
 */
public record MyLandRightsPagePacket(
        String status,
        int requestId,
        long storeRevision,
        long generatedAt,
        List<Entry> entries,
        Optional<UUID> nextAfterParcelId,
        boolean hasMore
) {

    public static final int MAX_ENTRIES = 32;
    public static final int MAX_STATUS = 32;
    public static final int MAX_DIMENSION = 64;
    public static final int MAX_ENUM = 32;

    private static final java.util.Set<String> CLOSED_STATUSES = java.util.Set.of(
            "OK", "RESET_REQUIRED", "INVALID_REQUEST", "UNAVAILABLE"
    );

    /** Canonical Minecraft dimension keys are resource locations (matches the authoritative land model). */
    private static final Pattern DIMENSION_PATTERN =
            Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");

    /** The closed planning-designation enum names the wire may carry. */
    private static final java.util.Set<String> ZONE_TYPE_NAMES = enumNames(ZoneType.class);

    /** The closed usage-right enum names the wire may carry. */
    private static final java.util.Set<String> USAGE_TYPE_NAMES = enumNames(UsageType.class);

    private static java.util.Set<String> enumNames(Class<? extends Enum<?>> type) {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Enum<?> constant : type.getEnumConstants()) {
            names.add(constant.name());
        }
        return java.util.Set.copyOf(names);
    }

    public MyLandRightsPagePacket {
        status = Objects.requireNonNull(status, "status");
        if (!CLOSED_STATUSES.contains(status)) {
            throw new NetworkPayloadException("Unknown status: " + status);
        }
        if (requestId <= 0) {
            throw new NetworkPayloadException("requestId must be positive: " + requestId);
        }
        if (storeRevision < 0) {
            throw new NetworkPayloadException(
                    "storeRevision must not be negative: " + storeRevision
            );
        }
        if (generatedAt <= 0) {
            throw new NetworkPayloadException(
                    "generatedAt must be a positive timestamp: " + generatedAt
            );
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.size() > MAX_ENTRIES) {
            throw new NetworkPayloadException(
                    "Entry count exceeds " + MAX_ENTRIES + ": " + entries.size()
            );
        }
        nextAfterParcelId =
                Objects.requireNonNull(nextAfterParcelId, "nextAfterParcelId");
        nextAfterParcelId.ifPresent(MyLandRightsPagePacket::requireCanonicalUuid);
        if (!"OK".equals(status)
                && (!entries.isEmpty() || nextAfterParcelId.isPresent() || hasMore)) {
            throw new NetworkPayloadException(
                    "A non-OK page must carry zero entries and no cursor/hasMore projection"
            );
        }
    }

    public static void encode(MyLandRightsPagePacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        NetworkPayloadLimits.writeUtf(buffer, message.status(), MAX_STATUS);
        buffer.writeVarInt(message.requestId());
        buffer.writeLong(message.storeRevision());
        buffer.writeLong(message.generatedAt());
        NetworkPayloadLimits.writeCollection(
                buffer,
                message.entries(),
                MAX_ENTRIES,
                (target, entry) -> Entry.encode(entry, target)
        );
        buffer.writeBoolean(message.nextAfterParcelId().isPresent());
        message.nextAfterParcelId().ifPresent(buffer::writeUUID);
        buffer.writeBoolean(message.hasMore());
    }

    public static MyLandRightsPagePacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        String status = NetworkPayloadLimits.readUtf(buffer, MAX_STATUS);
        int requestId = buffer.readVarInt();
        long storeRevision = buffer.readLong();
        long generatedAt = buffer.readLong();
        List<Entry> entries = NetworkPayloadLimits.readList(
                buffer,
                MAX_ENTRIES,
                Entry::decode
        );
        Optional<UUID> nextAfterParcelId =
                buffer.readBoolean()
                        ? Optional.of(buffer.readUUID())
                        : Optional.empty();
        boolean hasMore = buffer.readBoolean();
        return new MyLandRightsPagePacket(
                status,
                requestId,
                storeRevision,
                generatedAt,
                entries,
                nextAfterParcelId,
                hasMore
        );
    }

    private static void requireCanonicalUuid(UUID value) {
        Objects.requireNonNull(value, "nextAfterParcelId");
        if (!value.toString().equals(value.toString().toLowerCase(java.util.Locale.ROOT))) {
            throw new NetworkPayloadException(
                    "nextAfterParcelId must be a canonical UUID: " + value
            );
        }
    }

    /**
     * One projected own right (FR-LAND-002-A §3.1): parcel id, dimension,
     * inclusive region, zone/usage enum names, grant/expiry, revisions. Purely
     * display; never carries other-holder/audit/internal data.
     */
    public record Entry(
            UUID parcelId,
            String dimension,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            String zoneType,
            String usageType,
            long grantedAt,
            long expiresAt,
            long rightRevision,
            long parcelRevision
    ) {
        public Entry {
            Objects.requireNonNull(parcelId, "parcelId");
            dimension = Objects.requireNonNull(dimension, "dimension");
            if (dimension.isEmpty() || dimension.length() > MAX_DIMENSION) {
                throw new NetworkPayloadException(
                        "Dimension must be 1.." + MAX_DIMENSION + " characters"
                );
            }
            if (!DIMENSION_PATTERN.matcher(dimension).matches()) {
                throw new NetworkPayloadException(
                        "Dimension must be a canonical resource location: " + dimension
                );
            }
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new NetworkPayloadException(
                        "Region min must not exceed max on any axis"
                );
            }
            zoneType = Objects.requireNonNull(zoneType, "zoneType");
            if (zoneType.isEmpty() || zoneType.length() > MAX_ENUM
                    || !ZONE_TYPE_NAMES.contains(zoneType)) {
                throw new NetworkPayloadException(
                        "zoneType must be a closed ZoneType enum name: " + zoneType
                );
            }
            usageType = Objects.requireNonNull(usageType, "usageType");
            if (usageType.isEmpty() || usageType.length() > MAX_ENUM
                    || !USAGE_TYPE_NAMES.contains(usageType)) {
                throw new NetworkPayloadException(
                        "usageType must be a closed UsageType enum name: " + usageType
                );
            }
            if (grantedAt <= 0) {
                throw new NetworkPayloadException(
                        "grantedAt must be a positive timestamp: " + grantedAt
                );
            }
            if (expiresAt != 0 && expiresAt <= grantedAt) {
                throw new NetworkPayloadException(
                        "expiresAt must be 0 (no expiry) or after grantedAt"
                );
            }
            if (rightRevision <= 0) {
                throw new NetworkPayloadException(
                        "rightRevision must be positive: " + rightRevision
                );
            }
            if (parcelRevision <= 0) {
                throw new NetworkPayloadException(
                        "parcelRevision must be positive: " + parcelRevision
                );
            }
        }

        static void encode(Entry entry, FriendlyByteBuf buffer) {
            buffer.writeUUID(entry.parcelId());
            NetworkPayloadLimits.writeUtf(buffer, entry.dimension(), MAX_DIMENSION);
            buffer.writeInt(entry.minX());
            buffer.writeInt(entry.minY());
            buffer.writeInt(entry.minZ());
            buffer.writeInt(entry.maxX());
            buffer.writeInt(entry.maxY());
            buffer.writeInt(entry.maxZ());
            NetworkPayloadLimits.writeUtf(buffer, entry.zoneType(), MAX_ENUM);
            NetworkPayloadLimits.writeUtf(buffer, entry.usageType(), MAX_ENUM);
            buffer.writeLong(entry.grantedAt());
            buffer.writeLong(entry.expiresAt());
            buffer.writeLong(entry.rightRevision());
            buffer.writeLong(entry.parcelRevision());
        }

        static Entry decode(FriendlyByteBuf buffer) {
            UUID parcelId = buffer.readUUID();
            String dimension = NetworkPayloadLimits.readUtf(buffer, MAX_DIMENSION);
            int minX = buffer.readInt();
            int minY = buffer.readInt();
            int minZ = buffer.readInt();
            int maxX = buffer.readInt();
            int maxY = buffer.readInt();
            int maxZ = buffer.readInt();
            String zoneType = NetworkPayloadLimits.readUtf(buffer, MAX_ENUM);
            String usageType = NetworkPayloadLimits.readUtf(buffer, MAX_ENUM);
            long grantedAt = buffer.readLong();
            long expiresAt = buffer.readLong();
            long rightRevision = buffer.readLong();
            long parcelRevision = buffer.readLong();
            return new Entry(
                    parcelId, dimension,
                    minX, minY, minZ, maxX, maxY, maxZ,
                    zoneType, usageType,
                    grantedAt, expiresAt, rightRevision, parcelRevision
            );
        }
    }
}
