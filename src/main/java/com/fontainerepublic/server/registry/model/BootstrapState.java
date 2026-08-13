package com.fontainerepublic.server.registry.model;

/**
 * Versioned bootstrap metadata of the registry snapshot (FR-ID-001-A §6.2,
 * FR-ID-001 implementation task §3.2).
 *
 * <p>It proves that both fixed numbers are reserved and that the office
 * subject is materialized. It deliberately carries no current Hydro Archon
 * holder UUID and no FR-EMG authority UUID.</p>
 *
 * <p>The original personal binding ({@code 10-000001-61} -> a designated
 * player UUID) requires a separately approved, audited bootstrap design and is
 * <strong>not implemented by this task</strong>. Snapshots that claim an
 * applied original personal binding are rejected fail-closed until that design
 * exists.</p>
 *
 * @param fixedReservationsEstablished  true once both fixed numbers are in the
 *                                      reservation index (required)
 * @param officeSubjectMaterialized     true once the office subject exists
 *                                      (required by this implementation)
 * @param originalPersonalBindingApplied whether the original personal binding
 *                                       was applied; must remain false (bootstrap
 *                                       is a separately approved future task)
 */
public record BootstrapState(
        boolean fixedReservationsEstablished,
        boolean officeSubjectMaterialized,
        boolean originalPersonalBindingApplied
) {

    /** State of a fresh snapshot before office materialization. */
    public static final BootstrapState INITIAL =
            new BootstrapState(true, false, false);

    /** State after the office subject has been materialized idempotently. */
    public static final BootstrapState OFFICE_MATERIALIZED =
            new BootstrapState(true, true, false);
}
