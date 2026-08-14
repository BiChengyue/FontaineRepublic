package com.fontainerepublic.common.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Strict, versioned codec for the Message Water Mirror carrier NBT
 * (FR-ITEM-002-A §4.1/§4.2).
 *
 * <p>The authoritative device lives under one namespaced compound
 * {@code FontaineRepublicDevice} on a vanilla {@code minecraft:clock} stack:
 *
 * <pre>
 * FontaineRepublicDevice:
 *   Schema:      int
 *   Kind:        "message_water_mirror"
 *   DeviceId:    canonical lowercase UUID string
 *   OwnerUuid:   canonical lowercase UUID string
 *   IssuedAt:    long
 *   KeyId:       bounded printable-ASCII string (1..32 bytes)
 *   Signature:   byte[32] HMAC-SHA-256
 * </pre>
 *
 * {@code CustomModelData}, {@code display.Name} and {@code display.Lore} are
 * presentation-only and never contribute to authority.</p>
 *
 * <p>Decoding is fail-closed: unsupported schema versions, unknown fields,
 * wrong types, non-canonical UUIDs, out-of-bound values and oversized or
 * duplicate semantic forms all raise {@link IllegalArgumentException}. The
 * {@link #signingBytes} canonical byte sequence is the exact HMAC input,
 * computed in a fixed field order with a domain separator, fixed-width
 * big-endian integers and fixed-width length prefixes for variable-length
 * UTF-8 fields (FR-ITEM-002-A §4.2 step 4).</p>
 */
public final class CommunicatorCarrierCodec {

    /** Fixed domain separator prepended to the signed byte sequence. */
    private static final byte[] DOMAIN_SEPARATOR = "FONTAINEREPUBLIC_DEVICE_V1"
            .getBytes(StandardCharsets.US_ASCII);

    private static final String KEY_SCHEMA = "Schema";
    private static final String KEY_KIND = "Kind";
    private static final String KEY_DEVICE_ID = "DeviceId";
    private static final String KEY_OWNER_UUID = "OwnerUuid";
    private static final String KEY_ISSUED_AT = "IssuedAt";
    private static final String KEY_KEY_ID = "KeyId";
    private static final String KEY_SIGNATURE = "Signature";

    private static final Set<String> DEVICE_KEYS = Set.of(
            KEY_SCHEMA, KEY_KIND, KEY_DEVICE_ID, KEY_OWNER_UUID,
            KEY_ISSUED_AT, KEY_KEY_ID, KEY_SIGNATURE
    );

    private CommunicatorCarrierCodec() {
    }

    /**
     * Parsed authoritative device fields. Immutable; the {@code signature}
     * byte array is defensively copied by the codec on both encode and decode.
     */
    public static final class DeviceFields {
        private final int schema;
        private final String kind;
        private final UUID deviceId;
        private final UUID ownerUuid;
        private final long issuedAt;
        private final String keyId;
        private final byte[] signature;

        DeviceFields(
                int schema,
                String kind,
                UUID deviceId,
                UUID ownerUuid,
                long issuedAt,
                String keyId,
                byte[] signature
        ) {
            this.schema = schema;
            this.kind = kind;
            this.deviceId = deviceId;
            this.ownerUuid = ownerUuid;
            this.issuedAt = issuedAt;
            this.keyId = keyId;
            this.signature = signature;
        }

        public int schema() {
            return schema;
        }

        public String kind() {
            return kind;
        }

        public UUID deviceId() {
            return deviceId;
        }

        public UUID ownerUuid() {
            return ownerUuid;
        }

        public long issuedAt() {
            return issuedAt;
        }

        public String keyId() {
            return keyId;
        }

        /** Defensive copy of the raw 32-byte HMAC signature. */
        public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    /**
     * Writes the authoritative device compound onto {@code root} (which must
     * already carry the {@value FRItemIds#DEVICE_TAG} key chosen by the
     * issuer). The signature bytes are copied, never aliased.
     */
    public static CompoundTag encodeDevice(CompoundTag root, DeviceFields fields) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(fields, "fields");
        requireValidFields(fields);
        CompoundTag device = new CompoundTag();
        device.putInt(KEY_SCHEMA, fields.schema());
        device.putString(KEY_KIND, fields.kind());
        device.putString(KEY_DEVICE_ID, fields.deviceId().toString());
        device.putString(KEY_OWNER_UUID, fields.ownerUuid().toString());
        device.putLong(KEY_ISSUED_AT, fields.issuedAt());
        device.putString(KEY_KEY_ID, fields.keyId());
        device.putByteArray(KEY_SIGNATURE, fields.signature().clone());
        root.put(FRItemIds.DEVICE_TAG, device);
        return root;
    }

    /**
     * Reads and validates the authoritative device compound. Returns
     * {@code null} when the stack carries no device compound (an ordinary
     * clock), and throws {@link IllegalArgumentException} when a device
     * compound is present but malformed (fail-closed, FR-ITEM-002-A §10).
     */
    public static DeviceFields decodeDevice(CompoundTag root) {
        if (root == null || !root.contains(FRItemIds.DEVICE_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag device = root.getCompound(FRItemIds.DEVICE_TAG);
        requireOnlyKeys(device, DEVICE_KEYS, FRItemIds.DEVICE_TAG);
        requireType(device, KEY_SCHEMA, Tag.TAG_INT, FRItemIds.DEVICE_TAG);
        requireType(device, KEY_KIND, Tag.TAG_STRING, FRItemIds.DEVICE_TAG);
        requireType(device, KEY_DEVICE_ID, Tag.TAG_STRING, FRItemIds.DEVICE_TAG);
        requireType(device, KEY_OWNER_UUID, Tag.TAG_STRING, FRItemIds.DEVICE_TAG);
        requireType(device, KEY_ISSUED_AT, Tag.TAG_LONG, FRItemIds.DEVICE_TAG);
        requireType(device, KEY_KEY_ID, Tag.TAG_STRING, FRItemIds.DEVICE_TAG);
        requireType(device, KEY_SIGNATURE, Tag.TAG_BYTE_ARRAY, FRItemIds.DEVICE_TAG);

        int schema = device.getInt(KEY_SCHEMA);
        if (schema != FRItemIds.SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported " + FRItemIds.DEVICE_TAG + " schema: " + schema);
        }
        String kind = device.getString(KEY_KIND);
        if (!FRItemIds.DEVICE_KIND.equals(kind)) {
            throw new IllegalArgumentException("Unknown device kind: " + kind);
        }
        UUID deviceId = requireCanonicalUuid(device.getString(KEY_DEVICE_ID), KEY_DEVICE_ID);
        UUID ownerUuid = requireCanonicalUuid(device.getString(KEY_OWNER_UUID), KEY_OWNER_UUID);
        long issuedAt = device.getLong(KEY_ISSUED_AT);
        String keyId = device.getString(KEY_KEY_ID);
        requireValidKeyId(keyId);
        byte[] signature = device.getByteArray(KEY_SIGNATURE).clone();
        if (signature.length != FRItemIds.SIGNATURE_LENGTH) {
            throw new IllegalArgumentException(
                    "Device signature must be exactly " + FRItemIds.SIGNATURE_LENGTH
                            + " bytes, got " + signature.length);
        }
        return new DeviceFields(
                schema, kind, deviceId, ownerUuid, issuedAt, keyId, signature);
    }

    /**
     * Builds the exact canonical byte sequence HMAC'ed over a device. The
     * signature is <em>excluded</em>: this is the input the verifier re-signs
     * and compares against {@link DeviceFields#signature}.
     */
    public static byte[] signingBytes(DeviceFields fields, String carrierRegistryName) {
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(carrierRegistryName, "carrierRegistryName");
        requireValidFields(fields);
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);
            DataOutputStream out = new DataOutputStream(buffer);
            out.write(DOMAIN_SEPARATOR);
            writeLengthPrefixedAscii(out, carrierRegistryName);
            out.writeInt(fields.schema());
            writeLengthPrefixedAscii(out, fields.kind());
            writeUuid(out, fields.deviceId());
            writeUuid(out, fields.ownerUuid());
            out.writeLong(fields.issuedAt());
            writeLengthPrefixedAscii(out, fields.keyId());
            out.flush();
            return buffer.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException("In-memory signing stream failed", impossible);
        }
    }

    static void requireValidFields(DeviceFields fields) {
        if (fields.schema() != FRItemIds.SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported " + FRItemIds.DEVICE_TAG + " schema: " + fields.schema());
        }
        if (!FRItemIds.DEVICE_KIND.equals(fields.kind())) {
            throw new IllegalArgumentException("Unknown device kind: " + fields.kind());
        }
        Objects.requireNonNull(fields.deviceId(), "deviceId");
        Objects.requireNonNull(fields.ownerUuid(), "ownerUuid");
        requireValidKeyId(fields.keyId());
        if (fields.signature() != null
                && fields.signature().length != FRItemIds.SIGNATURE_LENGTH) {
            throw new IllegalArgumentException(
                    "Device signature must be exactly " + FRItemIds.SIGNATURE_LENGTH + " bytes");
        }
    }

    static void requireValidKeyId(String keyId) {
        if (keyId == null || keyId.isEmpty()) {
            throw new IllegalArgumentException("Device KeyId must not be empty");
        }
        byte[] bytes = keyId.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > FRItemIds.MAX_KEY_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Device KeyId must be at most " + FRItemIds.MAX_KEY_ID_LENGTH + " bytes");
        }
        if (!keyId.equals(new String(bytes, StandardCharsets.US_ASCII))) {
            throw new IllegalArgumentException("Device KeyId must be printable ASCII");
        }
        for (byte b : bytes) {
            if (b < 0x20 || b > 0x7E) {
                throw new IllegalArgumentException("Device KeyId must be printable ASCII");
            }
        }
    }

    /**
     * Validates that a UUID string is the canonical lowercase hyphenated form;
     * anything else is rejected rather than accepted as a second form.
     */
    static UUID requireCanonicalUuid(String value, String fieldName) {
        if (value == null || !value.equals(value.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(
                    "Device " + fieldName + " must be lowercase canonical UUID");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "Device " + fieldName + " must be lowercase canonical UUID", invalid);
        }
    }

    private static void writeLengthPrefixedAscii(DataOutputStream out, String value)
            throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static void requireOnlyKeys(CompoundTag tag, Set<String> allowed, String name) {
        for (String key : tag.getAllKeys()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException(
                        "Unknown field in " + name + ": " + key);
            }
        }
    }

    private static void requireType(CompoundTag tag, String key, int expected, String name) {
        if (tag.getTagType(key) != expected) {
            throw new IllegalArgumentException(
                    "Field " + name + "." + key + " must have NBT type " + expected);
        }
    }
}
