package com.fontainerepublic.common.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Executable upper bounds for all future FontaineRepublic wire codecs.
 */
public final class NetworkPayloadLimits {
    public static final int MAX_PAYLOAD_BYTES = 32 * 1024;
    public static final int MAX_UTF_CHARACTERS = 4_096;
    public static final int MAX_COLLECTION_ELEMENTS = 1_024;
    public static final int MAX_BYTE_ARRAY_LENGTH = 32 * 1024;

    private NetworkPayloadLimits() {
    }

    public static void validateIncomingPayload(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        int readable = buffer.readableBytes();
        if (readable > MAX_PAYLOAD_BYTES) {
            throw new NetworkPayloadException(
                    "Payload exceeds " + MAX_PAYLOAD_BYTES + " bytes: " + readable
            );
        }
    }

    public static String readUtf(FriendlyByteBuf buffer, int maximumCharacters) {
        Objects.requireNonNull(buffer, "buffer");
        int limit = boundedLimit(
                maximumCharacters,
                MAX_UTF_CHARACTERS,
                "UTF character limit"
        );
        return buffer.readUtf(limit);
    }

    public static void writeUtf(
            FriendlyByteBuf buffer,
            String value,
            int maximumCharacters
    ) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(value, "value");
        int limit = boundedLimit(
                maximumCharacters,
                MAX_UTF_CHARACTERS,
                "UTF character limit"
        );
        if (value.length() > limit) {
            throw new NetworkPayloadException(
                    "UTF value exceeds " + limit + " characters: " + value.length()
            );
        }
        buffer.writeUtf(value, limit);
    }

    public static byte[] readByteArray(FriendlyByteBuf buffer, int maximumLength) {
        Objects.requireNonNull(buffer, "buffer");
        int limit = boundedLimit(
                maximumLength,
                MAX_BYTE_ARRAY_LENGTH,
                "byte-array limit"
        );
        int length = readBoundedLength(buffer, limit, "byte array");
        requireReadable(buffer, length, "byte array");
        byte[] value = new byte[length];
        buffer.readBytes(value);
        return value;
    }

    public static void writeByteArray(
            FriendlyByteBuf buffer,
            byte[] value,
            int maximumLength
    ) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(value, "value");
        int limit = boundedLimit(
                maximumLength,
                MAX_BYTE_ARRAY_LENGTH,
                "byte-array limit"
        );
        if (value.length > limit) {
            throw new NetworkPayloadException(
                    "Byte array exceeds " + limit + " bytes: " + value.length
            );
        }
        buffer.writeVarInt(value.length);
        buffer.writeBytes(value);
    }

    public static <T> List<T> readList(
            FriendlyByteBuf buffer,
            int maximumElements,
            Function<FriendlyByteBuf, T> elementDecoder
    ) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(elementDecoder, "elementDecoder");
        int limit = boundedLimit(
                maximumElements,
                MAX_COLLECTION_ELEMENTS,
                "collection limit"
        );
        int count = readBoundedLength(buffer, limit, "collection");
        ArrayList<T> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(Objects.requireNonNull(
                    elementDecoder.apply(buffer),
                    "Decoded collection element"
            ));
        }
        return List.copyOf(values);
    }

    public static <T> void writeCollection(
            FriendlyByteBuf buffer,
            Collection<T> values,
            int maximumElements,
            BiConsumer<FriendlyByteBuf, T> elementEncoder
    ) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(elementEncoder, "elementEncoder");
        int limit = boundedLimit(
                maximumElements,
                MAX_COLLECTION_ELEMENTS,
                "collection limit"
        );
        if (values.size() > limit) {
            throw new NetworkPayloadException(
                    "Collection exceeds " + limit + " elements: " + values.size()
            );
        }
        buffer.writeVarInt(values.size());
        for (T value : values) {
            elementEncoder.accept(buffer, Objects.requireNonNull(value, "collection element"));
        }
    }

    static void validateEncodedLength(int encodedLength) {
        if (encodedLength < 0 || encodedLength > MAX_PAYLOAD_BYTES) {
            throw new NetworkPayloadException(
                    "Encoded payload exceeds " + MAX_PAYLOAD_BYTES
                            + " bytes: " + encodedLength
            );
        }
    }

    private static int readBoundedLength(
            FriendlyByteBuf buffer,
            int maximumLength,
            String valueName
    ) {
        int length = buffer.readVarInt();
        if (length < 0) {
            throw new NetworkPayloadException(
                    "Negative " + valueName + " length: " + length
            );
        }
        if (length > maximumLength) {
            throw new NetworkPayloadException(
                    valueName + " length exceeds " + maximumLength + ": " + length
            );
        }
        return length;
    }

    private static void requireReadable(
            FriendlyByteBuf buffer,
            int requiredBytes,
            String valueName
    ) {
        if (buffer.readableBytes() < requiredBytes) {
            throw new NetworkPayloadException(
                    "Missing " + valueName + " bytes: required " + requiredBytes
                            + ", available " + buffer.readableBytes()
            );
        }
    }

    private static int boundedLimit(int requested, int ceiling, String name) {
        if (requested < 0 || requested > ceiling) {
            throw new IllegalArgumentException(
                    name + " must be between 0 and " + ceiling + ": " + requested
            );
        }
        return requested;
    }
}
