package com.fontainerepublic.server.emergency.model;

/**
 * Strict command-source classification of the shared emergency authority
 * boundary (FR-EMG-001-A §4), aligned with the FR-ID-BOOTSTRAP console
 * classifier.
 *
 * <p>Only {@link #LOCAL_CONSOLE} (the real local Dedicated Server console)
 * is accepted as {@code SERVER_CONSOLE}. RCON, command blocks, functions,
 * players, integrated hosts, and other entity-less sources are rejected.</p>
 */
public enum EmergencySourceClassification {
    LOCAL_CONSOLE,
    RCON,
    COMMAND_BLOCK,
    MINECART_COMMAND_BLOCK,
    PLAYER,
    INTEGRATED_HOST,
    FUNCTION_OR_OTHER
}
