package com.fontainerepublic.server.registry.model;

/**
 * Entity-kind abstraction used by {@link BootstrapConsoleClassifier} so the
 * classification matrix is testable without a running Minecraft server.
 */
public enum BootstrapEntityKind {

    /** No command source entity (console/RCON/function). */
    NONE,

    /** A player entity. */
    PLAYER,

    /** A command block entity. */
    COMMAND_BLOCK,

    /** A minecart command block entity. */
    MINECART_COMMAND_BLOCK,

    /** Any other entity (defensive; rejected). */
    OTHER
}
