package com.fontainerepublic.server.land.persistence;

/**
 * Strict decode failure of the {@code "land"} namespace (FR-LAND-001-A §3.5).
 *
 * <p>Thrown by {@link LandNbtCodec} when a stored snapshot violates the
 * declared schema: unknown fields, wrong NBT types, unsupported versions,
 * non-canonical keys, mismatched keys, invalid bounds, or an inconsistent
 * holder index. Loading never auto-repairs; the whole snapshot is rejected
 * fail-closed.</p>
 */
public final class LandNbtException extends IllegalArgumentException {

    public LandNbtException(String message) {
        super(message);
    }

    public LandNbtException(String message, Throwable cause) {
        super(message, cause);
    }
}
