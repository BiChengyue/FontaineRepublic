package com.fontainerepublic.server.citizen.model;

/**
 * Infrastructure political classification of a citizen (FR-CIT-001-A §3.2).
 *
 * <p>Ranks are descriptive political classifications, never technical
 * permissions: no code may use a rank to gate technical commands, grant OP,
 * bypass validation, or substitute for the future Permission system.
 * {@link #GOD} is a constitutional water-deity/creator political rank — it is
 * deliberately <strong>not</strong> OP.</p>
 */
public enum CitizenRank {
    /** Water-deity/creator political rank (constitutional office; NOT technical OP). */
    GOD,

    /** Reserved for future council/parliamentary leadership designs. */
    COUNCIL,

    /** Ordinary citizen (default for every provisioned player). */
    CITIZEN
}
