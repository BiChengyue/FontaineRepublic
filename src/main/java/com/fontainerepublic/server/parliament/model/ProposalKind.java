package com.fontainerepublic.server.parliament.model;

/**
 * Closed kind of a legislative proposal (FR-PAR-002-A §1/§3).
 *
 * <p>{@link #LEGISLATION} is the ordinary legislative pipeline
 * (FR-PAR-001); {@link #AMENDMENT} is the first-layer constitutional
 * amendment pipeline — proposal -> court review -> parliament 4/5 ->
 * referendum -> water-god constitutional consent (FR-PAR-002-A §2/§3). The
 * kind is fixed at submission and can never change.</p>
 */
public enum ProposalKind {
    LEGISLATION,
    AMENDMENT
}
