package com.fontainerepublic.common.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;
import java.util.UUID;

/**
 * Builds the signed {@code minecraft:clock} Water Mirror carrier
 * (FR-ITEM-002-A §5).
 *
 * <p>Issuance creates one vanilla clock stack carrying the authoritative
 * {@code FontaineRepublicDevice} compound plus presentation-only
 * {@code display.Name}/{@code display.Lore} and {@code CustomModelData}. The
 * signature is computed by the {@link CommunicatorAuthenticator}; the raw
 * signing key never leaves the server. A copy of a signed stack for a
 * different player fails owner binding; presentation fields are never
 * authority (FR-ITEM-002-A §4.3).</p>
 */
public final class CommunicatorIssuer {

    private CommunicatorIssuer() {
    }

    /**
     * Issues a signed Water Mirror carrier for {@code ownerUuid}. The device
     * id is a fresh random UUID; {@code issuedAt} uses
     * {@link System#currentTimeMillis()}. Presentation name is
     * {@value FRItemIds#DISPLAY_NAME}, with a one-line lore describing the
     * device and a {@code CustomModelData} of
     * {@value FRItemIds#CUSTOM_MODEL_DATA}.
     */
    public static ItemStack issue(
            CommunicatorAuthenticator authenticator,
            UUID ownerUuid
    ) {
        return issue(authenticator, ownerUuid, UUID.randomUUID(), System.currentTimeMillis());
    }

    /** Testable issuance with an explicit device id and timestamp. */
    public static ItemStack issue(
            CommunicatorAuthenticator authenticator,
            UUID ownerUuid,
            UUID deviceId,
            long issuedAt
    ) {
        Objects.requireNonNull(authenticator, "authenticator");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(deviceId, "deviceId");
        String keyId = authenticator.activeKeyId();
        CommunicatorCarrierCodec.DeviceFields signed = authenticator.sign(
                deviceId, ownerUuid, issuedAt, keyId);

        ItemStack stack = new ItemStack(Items.CLOCK);
        CompoundTag root = stack.getOrCreateTag();
        CommunicatorCarrierCodec.encodeDevice(root, signed);
        root.putInt("CustomModelData", FRItemIds.CUSTOM_MODEL_DATA);

        CompoundTag display = new CompoundTag();
        display.putString("Name", ComponentJson.text(FRItemIds.DISPLAY_NAME));
        ListTag lore = new ListTag();
        lore.add(StringTag.valueOf(ComponentJson.text(
                "Message Water Mirror — device " + signed.keyId())));
        display.put("Lore", lore);
        root.put("display", display);
        return stack;
    }

    /**
     * Minimal JSON text-component wrapper for item display name/lore. Only a
     * single plain-text string is produced; no rich/structured components are
     * used for the carrier.
     */
    static final class ComponentJson {
        private ComponentJson() {
        }

        static String text(String value) {
            StringBuilder json = new StringBuilder("{"text":"");
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\\' -> json.append("\\\\");
                    case '"' -> json.append("\\\"");
                    default -> json.append(c);
                }
            }
            json.append(""}");
            return json.toString();
        }
    }
}
