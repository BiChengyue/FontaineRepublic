package com.fontainerepublic.common.network;

/**
 * Indicates that an application payload violates a transport bound.
 */
public final class NetworkPayloadException extends IllegalArgumentException {
    public NetworkPayloadException(String message) {
        super(message);
    }
}
