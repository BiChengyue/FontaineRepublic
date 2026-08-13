package com.fontainerepublic.common.network.display;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Objects;

/**
 * S2C presentation payload: one public summary snapshot of the government
 * ministries (FR-CLIENT-001-A §4.2, message ledger ID 5; FR-CLIENT-001-IMPL-B3a).
 *
 * <p>Sent once on login (institution data changes rarely; live change pushes
 * are a later stage). Carries display data only — never an authority
 * decision. Each ministry entry is bounded ({@code id} ≤ 64, {@code name}
 * ≤ 64, {@code positionCount} ≥ 0), the list is capped at
 * {@value #MAX_MINISTRIES} entries and {@code at} is the snapshot time. All
 * bounds are enforced at construction and at decode so the wire never carries
 * an out-of-range value.</p>
 */
public record GovernmentInfoPacket(
        List<MinistryEntry> ministries,
        long at
) {

    public static final int MAX_MINISTRIES = 64;

    public GovernmentInfoPacket {
        ministries = Objects.requireNonNull(ministries, "ministries");
        if (ministries.size() > MAX_MINISTRIES) {
            throw new NetworkPayloadException(
                    "Ministries exceed " + MAX_MINISTRIES + ": " + ministries.size()
            );
        }
        ministries = List.copyOf(ministries);
        for (MinistryEntry entry : ministries) {
            Objects.requireNonNull(entry, "ministry entry");
        }
        if (at <= 0) {
            throw new NetworkPayloadException("at must be a positive timestamp: " + at);
        }
    }

    public int count() {
        return ministries.size();
    }

    public static void encode(GovernmentInfoPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        NetworkPayloadLimits.writeCollection(
                buffer,
                message.ministries(),
                MAX_MINISTRIES,
                (buf, entry) -> MinistryEntry.encode(entry, buf)
        );
        buffer.writeLong(message.at());
    }

    public static GovernmentInfoPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        List<MinistryEntry> ministries =
                NetworkPayloadLimits.readList(buffer, MAX_MINISTRIES, MinistryEntry::decode);
        long at = buffer.readLong();
        return new GovernmentInfoPacket(ministries, at);
    }

    /**
     * One ministry summary entry (internal id digest, display name, count of
     * positions published by the government module).
     */
    public record MinistryEntry(
            String id,
            String name,
            int positionCount
    ) {

        public static final int MAX_ID = 64;
        public static final int MAX_NAME = 64;

        public MinistryEntry {
            id = Objects.requireNonNull(id, "id");
            if (id.isEmpty() || id.length() > MAX_ID) {
                throw new NetworkPayloadException(
                        "Ministry id must be 1.." + MAX_ID + " characters"
                );
            }
            name = Objects.requireNonNull(name, "name");
            if (name.isEmpty() || name.length() > MAX_NAME) {
                throw new NetworkPayloadException(
                        "Ministry name must be 1.." + MAX_NAME + " characters"
                );
            }
            if (positionCount < 0) {
                throw new NetworkPayloadException(
                        "positionCount must not be negative: " + positionCount
                );
            }
        }

        public static void encode(MinistryEntry entry, FriendlyByteBuf buffer) {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(buffer, "buffer");
            NetworkPayloadLimits.writeUtf(buffer, entry.id(), MAX_ID);
            NetworkPayloadLimits.writeUtf(buffer, entry.name(), MAX_NAME);
            buffer.writeVarInt(entry.positionCount());
        }

        public static MinistryEntry decode(FriendlyByteBuf buffer) {
            Objects.requireNonNull(buffer, "buffer");
            String id = NetworkPayloadLimits.readUtf(buffer, MAX_ID);
            String name = NetworkPayloadLimits.readUtf(buffer, MAX_NAME);
            int positionCount = buffer.readVarInt();
            return new MinistryEntry(id, name, positionCount);
        }
    }
}
