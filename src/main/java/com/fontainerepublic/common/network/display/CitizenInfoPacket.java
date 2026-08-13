package com.fontainerepublic.common.network.display;

import com.fontainerepublic.common.network.NetworkPayloadException;
import com.fontainerepublic.common.network.NetworkPayloadLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/**
 * S2C presentation payload: one citizen-identity snapshot of the receiver's
 * own record (FR-CLIENT-001-A §4.2, message ledger ID 3; FR-CLIENT-001-IMPL-B2).
 *
 * <p>Carries display data only — never an authority decision. The registry
 * number, status and rank are bounded display strings; {@code firstCitizenAt}
 * is the server-assigned first-citizenship timestamp and {@code at} the
 * snapshot time. All bounds are enforced at construction and at decode so the
 * wire never carries an out-of-range value.</p>
 */
public record CitizenInfoPacket(
        String registryNumber,
        String citizenStatus,
        String citizenRank,
        long firstCitizenAt,
        long at
) {

    public static final int MAX_REGISTRY_NUMBER = 32;
    public static final int MAX_CITIZEN_STATUS = 32;
    public static final int MAX_CITIZEN_RANK = 32;

    public CitizenInfoPacket {
        registryNumber = Objects.requireNonNull(registryNumber, "registryNumber");
        if (registryNumber.isEmpty() || registryNumber.length() > MAX_REGISTRY_NUMBER) {
            throw new NetworkPayloadException(
                    "registryNumber must be 1.." + MAX_REGISTRY_NUMBER + " characters"
            );
        }
        citizenStatus = Objects.requireNonNull(citizenStatus, "citizenStatus");
        if (citizenStatus.isEmpty() || citizenStatus.length() > MAX_CITIZEN_STATUS) {
            throw new NetworkPayloadException(
                    "citizenStatus must be 1.." + MAX_CITIZEN_STATUS + " characters"
            );
        }
        citizenRank = Objects.requireNonNull(citizenRank, "citizenRank");
        if (citizenRank.isEmpty() || citizenRank.length() > MAX_CITIZEN_RANK) {
            throw new NetworkPayloadException(
                    "citizenRank must be 1.." + MAX_CITIZEN_RANK + " characters"
            );
        }
        if (firstCitizenAt <= 0) {
            throw new NetworkPayloadException(
                    "firstCitizenAt must be a positive timestamp: " + firstCitizenAt
            );
        }
        if (at <= 0) {
            throw new NetworkPayloadException("at must be a positive timestamp: " + at);
        }
    }

    public static void encode(CitizenInfoPacket message, FriendlyByteBuf buffer) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(buffer, "buffer");
        NetworkPayloadLimits.writeUtf(buffer, message.registryNumber(), MAX_REGISTRY_NUMBER);
        NetworkPayloadLimits.writeUtf(buffer, message.citizenStatus(), MAX_CITIZEN_STATUS);
        NetworkPayloadLimits.writeUtf(buffer, message.citizenRank(), MAX_CITIZEN_RANK);
        buffer.writeLong(message.firstCitizenAt());
        buffer.writeLong(message.at());
    }

    public static CitizenInfoPacket decode(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return new CitizenInfoPacket(
                NetworkPayloadLimits.readUtf(buffer, MAX_REGISTRY_NUMBER),
                NetworkPayloadLimits.readUtf(buffer, MAX_CITIZEN_STATUS),
                NetworkPayloadLimits.readUtf(buffer, MAX_CITIZEN_RANK),
                buffer.readLong(),
                buffer.readLong()
        );
    }
}
