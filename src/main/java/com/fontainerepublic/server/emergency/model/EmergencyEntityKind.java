package com.fontainerepublic.server.emergency.model;

/**
 * Command-source entity classification for emergency authority (FR-EMG-001-A
 * §4); mirrors the bootstrap classifier entity model.
 */
public enum EmergencyEntityKind {
    NONE,
    PLAYER,
    MINECART_COMMAND_BLOCK,
    OTHER
}
