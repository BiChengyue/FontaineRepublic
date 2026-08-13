package com.fontainerepublic.common.network.display;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Objects;

/**
 * S2C presentation payload: one public summary snapshot of the republic's
 * land parcels (FR-CLIENT-001-A §4.2, message ledger ID 8;
 * FR-CLIENT-001-IMPL-B3b).
 *
 * <p>Sent once on login (institution data changes rarely; live change pushes
 * are a later stage). Carries aggregate display data only — a single bounded
 * overview (parcel count, total area, per-zone distribution, store revision)
 * — never an authority decision and never a parcel list. The zone list is
 * capped at {@value #MAX_ZONES} entries (each {@code zone} ≤ 32,
 * {@code count} ≥ 0, {@code area} ≥ 0), the counters are non-negative and
 * {@code at} is the snapshot time. All bounds are enforced at construction and
 * at decode so the wire never carries an out-of-range value.</p>
 */
public record LandInfoPacket(
        int parcelCount,
        long totalArea,
        List<ZoneEntry> zones,
        long storeRevision,
        long at
) {

    public static final int MAX_ZONES = 16;

    public LandInfoPacket {
        if (parcelCount < 0) {
            throw new NetworkPayloadException(
                    "parcelCount must not be negative: " + parcelCount
            );
        }
        if (totalArea < 0) {
            throw new NetworkPayloadException(
                    "totalArea must not be negative: " + totalArea
            );
        }
        if (storeRevision < 0) {
            throw new NetworkPayloadException(
                    "storeRevision must not be negative: " + storeRevision
            );
        }
        zones = Objects.requireNonNull(zones, "zones");
        if (zones.size() > MAX_ZONES) {
            throw new NetworkPayloadException(
                    "Zones exceed " + MAX_ZONES + ": " + zones.size()
            );
        }
        zones = List.copyOf(zones);
        for (ZoneEntry entry : zones) {
            Objects.requireNonNull(entry, "zone entry");
        }
        if (at <= 0) {
            throw new NetworkPayloadException("at must be a positive timestamp: " + at);
        }
    }

    public int zoneCount() {
        return zones.size();
    }

    public static void encode(LandInfoPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(message.parcelCount());
        buffer.writeLong(message.totalArea());
        NetworkPayloadLimits.writeCollection(
                buffer,
                message.zones(),
                MAX_ZONES,
                (buf, entry) -> ZoneEntry.encode(entry, buf)
        );
        buffer.writeLong(message.storeRevision());
        buffer.writeLong(message.at());
    }

    public static LandInfoPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        int parcelCount = buffer.readVarInt();
        long totalArea = buffer.readLong();
        List<ZoneEntry> zones =
                NetworkPayloadLimits.readList(buffer, MAX_ZONES, ZoneEntry::decode);
        long storeRevision = buffer.readLong();
        long at = buffer.readLong();
        return new LandInfoPacket(parcelCount, totalArea, zones, storeRevision, at);
    }

    /**
     * One zone-distribution entry (zone designation, parcel count, aggregate
     * area).
     */
    public record ZoneEntry(
            String zone,
            int count,
            long area
    ) {

        public static final int MAX_ZONE = 32;

        public ZoneEntry {
            zone = Objects.requireNonNull(zone, "zone");
            if (zone.isEmpty() || zone.length() > MAX_ZONE) {
                throw new NetworkPayloadException(
                        "Zone must be 1.." + MAX_ZONE + " characters"
                );
            }
            if (count < 0) {
                throw new NetworkPayloadException(
                        "Zone count must not be negative: " + count
                );
            }
            if (area < 0) {
                throw new NetworkPayloadException(
                        "Zone area must not be negative: " + area
                );
            }
        }

        public static void encode(ZoneEntry entry, FriendlyByteBuf buffer) {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(buffer, "buffer");
            NetworkPayloadLimits.writeUtf(buffer, entry.zone(), MAX_ZONE);
            buffer.writeVarInt(entry.count());
            buffer.writeLong(entry.area());
        }

        public static ZoneEntry decode(FriendlyByteBuf buffer) {
            Objects.requireNonNull(buffer, "buffer");
            String zone = NetworkPayloadLimits.readUtf(buffer, MAX_ZONE);
            int count = buffer.readVarInt();
            long area = buffer.readLong();
            return new ZoneEntry(zone, count, area);
        }
    }
}
