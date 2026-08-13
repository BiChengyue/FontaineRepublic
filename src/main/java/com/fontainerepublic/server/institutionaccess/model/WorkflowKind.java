package com.fontainerepublic.server.institutionaccess.model;

/**
 * The three institutional workflows with server-configurable parameters
 * (FR-INST-001-B §3).
 *
 * <ul>
 *   <li>{@link #PUBLIC} — citizen public workflow near a registered public
 *       terminal: 6 blocks, 2 minutes, single-use.</li>
 *   <li>{@link #OFFICIAL_ROUTINE} — official routine duties inside a
 *       registered internal work zone: 10-minute idle timeout, 60-minute hard
 *       session limit.</li>
 *   <li>{@link #HIGH_RISK} — state-authority actions at a registered secure
 *       terminal: 6 blocks, 30 seconds, single-use, requires a valid official
 *       routine session.</li>
 * </ul>
 */
public enum WorkflowKind {
    PUBLIC,
    OFFICIAL_ROUTINE,
    HIGH_RISK
}
