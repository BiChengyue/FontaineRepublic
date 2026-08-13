package com.fontainerepublic.server.parliament.model;

/**
 * Closed normative hierarchy of the republic (宪法 v3.0 §11; FR-BL-003 §1-2;
 * FR-PAR-001-A §2/§3.1).
 *
 * <p>The hierarchy determines the required passage threshold: ordinary laws
 * and administrative rules pass by a simple majority (1/2), organic laws by
 * 2/3, and constitutional basic laws by 3/4 — each computed against the
 * frozen citizen roster at vote open, rounded up (FR-PAR-001-A §5). The
 * level is validated at submission and can never be downgraded by a bill.</p>
 */
public enum NormLevel {
    CONSTITUTION_BASIC,
    ORGANIC,
    ORDINARY,
    ADMINISTRATIVE
}
