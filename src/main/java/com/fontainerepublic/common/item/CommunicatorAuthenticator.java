package com.fontainerepublic.common.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-authoritative carrier authenticator (FR-ITEM-002-A §4.2/§4.3).
 *
 * <p>Every authoritative carrier check runs through exactly one authenticator:
 * the item id must be the vanilla {@code minecraft:clock} carrier, the
 * {@code FontaineRepublicDevice} compound must decode under the bounded
 * schema, and the HMAC-SHA-256 {@code Signature} must verify over the
 * canonical byte sequence. HMAC-SHA-256 over a server-only key is the
 * signature primitive: the issuing secret never leaves the server and
 * verification is a symmetric recompute (FR-ITEM-002-A §4.1 encodes the
 * contract as exactly 32 raw HMAC-SHA-256 bytes).</p>
 *
 * <p>The key map is bounded and server-only. Exactly one key is active for
 * issuance; older keys may remain verification-only. Removing a key
 * invalidates every device signed by it and therefore requires an audited
 * reissue plan (FR-ITEM-002-A §4.2). This class holds no link to any
 * {@code client/} code and no {@code DeferredRegister<Item>}, so an
 * FR-absent Forge client is unaffected.</p>
 */
public final class CommunicatorAuthenticator {

    private final Map<String, byte[]> verificationKeys = new LinkedHashMap<>();
    private String activeKeyId;

    /**
     * Bootstraps the server key map. When {@code seed} is empty a fresh random
     * active key is generated under {@code "active-1"}; otherwise {@code seed}
     * is used as the raw HMAC key material for the {@code "active-1"} key
     * (tests may pass a fixed seed). Raw key bytes are never written to any
     * stack or sent to a client.
     */
    public CommunicatorAuthenticator(byte[] seed) {
        byte[] keyMaterial = seed != null && seed.length > 0 ? seed.clone() : randomKey();
        registerKey("active-1", keyMaterial);
        activeKeyId = "active-1";
    }

    /** True when the stack is the vanilla carrier item (empty-safe). */
    public static boolean isCarrierItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ITEMS
                .getKey(stack.getItem());
        return key != null && FRItemIds.CARRIER_ITEM_REGISTRY_NAME.equals(key.toString());
    }

    /**
     * Fully authenticates a held stack as this player's device. Fail-closed:
     * any malformed compound, unknown schema, wrong carrier, bad owner binding
     * or invalid signature returns {@code false}. A plain clock (no device
     * compound) also returns {@code false}.
     *
     * @param stack the held stack
     * @param ownerUuid the authenticated player UUID (null disables owner binding)
     */
    public boolean authenticate(ItemStack stack, UUID ownerUuid) {
        if (!isCarrierItem(stack)) {
            return false;
        }
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag == null) {
            return false;
        }
        CommunicatorCarrierCodec.DeviceFields fields;
        try {
            fields = CommunicatorCarrierCodec.decodeDevice(tag);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        if (fields == null) {
            return false;
        }
        if (ownerUuid != null && !ownerUuid.equals(fields.ownerUuid())) {
            return false;
        }
        try {
            byte[] computed = sign(
                    fields, verificationKey(fields.keyId()), FRItemIds.CARRIER_ITEM_REGISTRY_NAME);
            if (!MessageDigest.isEqual(fields.signature(), computed)) {
                return false;
            }
        } catch (RuntimeException missingKey) {
            return false;
        }
        return true;
    }

    /**
     * Issues (signs) a device for the given owner, returning the
     * {@code DeviceFields} that carry the computed signature. Server-only.
     */
    public CommunicatorCarrierCodec.DeviceFields sign(
            UUID deviceId,
            UUID ownerUuid,
            long issuedAt,
            String keyId
    ) {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        byte[] key = verificationKey(keyId);
        CommunicatorCarrierCodec.DeviceFields unsigned = new CommunicatorCarrierCodec.DeviceFields(
                FRItemIds.SCHEMA_VERSION,
                FRItemIds.DEVICE_KIND,
                deviceId,
                ownerUuid,
                issuedAt,
                keyId,
                null
        );
        byte[] signature = sign(unsigned, key, FRItemIds.CARRIER_ITEM_REGISTRY_NAME);
        return new CommunicatorCarrierCodec.DeviceFields(
                FRItemIds.SCHEMA_VERSION,
                FRItemIds.DEVICE_KIND,
                deviceId,
                ownerUuid,
                issuedAt,
                keyId,
                signature
        );
    }

    /**
     * Verifies a decoded device's signature against its key (pure, no item,
     * no owner binding). Used by the item-level authenticator and testable
     * without a live registry.
     */
    public boolean verify(CommunicatorCarrierCodec.DeviceFields fields) {
        Objects.requireNonNull(fields, "fields");
        try {
            byte[] computed = sign(
                    fields, verificationKey(fields.keyId()), FRItemIds.CARRIER_ITEM_REGISTRY_NAME);
            return MessageDigest.isEqual(fields.signature(), computed);
        } catch (RuntimeException missingKey) {
            return false;
        }
    }

    /** The current active issuance key id. */
    public String activeKeyId() {
        return activeKeyId;
    }

    /** Adds a verification key (validated at sign/authenticate time). */
    void registerKey(String keyId, byte[] rawKeyBytes) {
        CommunicatorCarrierCodec.requireValidKeyId(keyId);
        Objects.requireNonNull(rawKeyBytes, "rawKeyBytes");
        verificationKeys.put(keyId, rawKeyBytes.clone());
    }

    private byte[] verificationKey(String keyId) {
        byte[] key = verificationKeys.get(keyId);
        if (key == null) {
            throw new IllegalStateException("Unknown or disabled device key id: " + keyId);
        }
        return key;
    }

    private static byte[] sign(
            CommunicatorCarrierCodec.DeviceFields fields,
            byte[] key,
            String carrierRegistryName
    ) {
        byte[] message = CommunicatorCarrierCodec.signingBytes(fields, carrierRegistryName);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("HMAC-SHA-256 unavailable", failure);
        }
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    /** SHA-256 digest of the active key (for logging only, never the key). */
    public String activeKeyDigest() {
        byte[] key = verificationKeys.get(activeKeyId);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
