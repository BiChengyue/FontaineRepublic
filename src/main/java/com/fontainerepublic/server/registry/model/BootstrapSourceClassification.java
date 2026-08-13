package com.fontainerepublic.server.registry.model;

/**
 * Classified invocation source of a bootstrap attempt
 * (FR-ID-BOOTSTRAP-001-A §3, implementation task §3.1).
 *
 * <p>Only {@link #LOCAL_CONSOLE} (real local Dedicated Server console) is
 * accepted. Every other source is rejected with {@code REJECTED_SOURCE} and
 * the classification is durably recorded on the attempt trail.</p>
 *
 * <p>Known limitation: a Minecraft function invoked from the console carries
 * an entity-less source whose name is {@code "Server"}, which is
 * indistinguishable from the console at the {@code CommandSourceStack} level
 * — the final mutation boundary re-validates the classification, and the
 * remaining function-name-spoofing vector is accepted by design review (the
 * console operator is the only trusted actor in this infrastructure).</p>
 */
public enum BootstrapSourceClassification {

    /** The real local Dedicated Server console (the only accepted source). */
    LOCAL_CONSOLE,

    /** Remote console (RCON): {@code "Rcon"} named source. */
    RCON,

    /** Command block entity source. */
    COMMAND_BLOCK,

    /** Minecart command block entity source. */
    MINECART_COMMAND_BLOCK,

    /** Any player entity source (including ordinary OPs). */
    PLAYER,

    /** Integrated (single-player host) server, never a dedicated console. */
    INTEGRATED_HOST,

    /** Entity-less source that is neither Server nor Rcon (functions, …). */
    FUNCTION_OR_OTHER
}
