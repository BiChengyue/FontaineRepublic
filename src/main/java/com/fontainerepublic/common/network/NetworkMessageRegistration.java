package com.fontainerepublic.common.network;

/**
 * Registration-only surface exposed to future approved modules.
 */
public interface NetworkMessageRegistration {
    <MSG> void register(NetworkMessageSpec<MSG> specification);
}
