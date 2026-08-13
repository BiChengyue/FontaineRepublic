package com.fontainerepublic.server.institutionaccess.model;

/**
 * Terminal lifecycle state (FR-INST-001-A §6.2).
 *
 * <ul>
 *   <li>{@link #ACTIVE} — the terminal may anchor on-site contexts.</li>
 *   <li>{@link #SUSPENDED} — administratively paused; contexts invalidated,
 *       re-activation is not a terminal operation in this task.</li>
 *   <li>{@link #DISABLED} — withdrawn; terminal state.</li>
 * </ul>
 */
public enum TerminalState {
    ACTIVE,
    SUSPENDED,
    DISABLED
}
