package com.fontainerepublic.server.institutionaccess.api;

import com.fontainerepublic.server.institutionaccess.model.CapabilityClass;
import com.fontainerepublic.server.institutionaccess.model.Facility;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.Terminal;
import com.fontainerepublic.server.institutionaccess.model.TerminalId;
import com.fontainerepublic.server.land.model.ParcelId;

import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative shared institution access boundary
 * (FR-INST-002-A §4/§5).
 *
 * <p>The service owns the facility/terminal directory (consuming FR-LAND
 * spatial data read-only through {@code LandService} — never copied, never
 * hard-coded), the server-runtime on-site contexts, and the mandatory final
 * mutation-time revalidation for the four institutions. Business modules must
 * never read facility/terminal NBT directly; they call
 * {@link #validateAtMutation(OnSiteContext, CapabilityClass, long, String, int, int, int)}
 * at their final mutation boundary. Institution roles/permissions are owned
 * by future module designs, emergency recovery by FR-EMG — neither is
 * implemented here.</p>
 *
 * <p>All directory mutations run on the logical server owner thread, publish
 * only after the FR-CORE-002 durable gate reports {@code COMMITTED}, and are
 * audited through the audit service. On-site contexts are short-lived
 * runtime state, cleared on shutdown, and never persisted.</p>
 */
public interface InstitutionAccessService {

    // ------------------------------------------------------------------
    // facility directory (authoritative, gated, audited)
    // ------------------------------------------------------------------

    /**
     * Registers a facility bound to an existing, unbound FR-LAND parcel.
     *
     * @throws com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessUnavailableException
     *         when the actor is not resolvable, the parcel does not exist or
     *         is already bound, capacity is exhausted, or the durable store
     *         rejected the snapshot
     */
    FacilityReceipt registerFacility(UUID actor, FacilityRegistrationRequest request);

    /** Suspends an ACTIVE/RELOCATING facility (idempotent when suspended). */
    FacilityReceipt suspendFacility(UUID actor, FacilityId facilityId);

    /** Activates a SUSPENDED/RELOCATING facility (idempotent when active). */
    FacilityReceipt activateFacility(UUID actor, FacilityId facilityId);

    /**
     * Relocates a facility onto a new unbound FR-LAND parcel: the facility
     * enters RELOCATING with the new parcel in one commit.
     */
    FacilityReceipt relocateFacility(UUID actor, FacilityId facilityId, ParcelId newParcelId);

    /** Disables a facility (terminal state; idempotent). */
    FacilityReceipt disableFacility(UUID actor, FacilityId facilityId);

    // ------------------------------------------------------------------
    // terminal directory (authoritative, gated, audited)
    // ------------------------------------------------------------------

    /**
     * Registers a terminal anchored to an ACTIVE facility: institution type
     * must match, the position must lie inside the facility's parcel region
     * and be unclaimed, and the capability set must be non-empty.
     */
    TerminalReceipt registerTerminal(UUID actor, TerminalRegistrationRequest request);

    /** Suspends an ACTIVE terminal (idempotent when suspended). */
    TerminalReceipt suspendTerminal(UUID actor, TerminalId terminalId);

    /** Disables a terminal (terminal state; idempotent). */
    TerminalReceipt disableTerminal(UUID actor, TerminalId terminalId);

    // ------------------------------------------------------------------
    // on-site contexts (server-runtime only)
    // ------------------------------------------------------------------

    /**
     * Issues a fresh on-site context after the caller observed the player
     * physically interacting with a registered terminal (FR-INST-001-A
     * §6.3). Verifies facility ACTIVE, terminal ACTIVE, institution type
     * match, terminal still inside the facility region, the player in the
     * terminal dimension and within workflow range, and the capability
     * allowed by the terminal. A high-risk authorization additionally
     * requires a secure terminal and an already-valid official routine
     * session (FR-INST-001-B §3.3). Workflow parameters come from server
     * configuration; the caller cannot extend them.
     *
     * @throws com.fontainerepublic.server.institutionaccess.persistence.InstitutionAccessUnavailableException
     *         on any failed verification (no context is issued)
     */
    OnSiteContext issueOnSiteContext(
            UUID playerId,
            TerminalId terminalId,
            CapabilityClass capability,
            String playerDimension,
            int x,
            int y,
            int z
    );

    /**
     * Mandatory final mutation-time revalidation (FR-INST-001-A §7.3,
     * FR-INST-001-B §4; cannot be disabled). Re-checks presence, range,
     * facility/terminal state and revision binding, and single-use
     * consumption. For {@code HIGH_RISK} contexts a VALID result consumes the
     * single-use authorization at this boundary (whether the business
     * mutation later succeeds or fails); for {@code PUBLIC} contexts the
     * caller consumes on successful submission via {@link #consume}; for
     * {@code OFFICIAL_ROUTINE} a VALID result refreshes the idle clock of
     * the session. The player position must be supplied by the caller from
     * the authoritative server player state.
     */
    ValidationResult validateAtMutation(
            OnSiteContext context,
            CapabilityClass capability,
            long now,
            String dimension,
            int x,
            int y,
            int z
    );

    /**
     * Consumes a context after a successful business submission. Only
     * single-use workflows are affected: {@code PUBLIC} becomes consumed;
     * {@code HIGH_RISK} is already consumed at validation; an
     * {@code OFFICIAL_ROUTINE} session is a no-op. Idempotent and safe on
     * already-consumed/invalidated contexts.
     */
    void consume(OnSiteContext context);

    /**
     * Invalidates every context of a player (leave range, dimension change,
     * logout, death; FR-INST-001-A §7.3). Returning never restores a
     * context — a new terminal interaction is required.
     */
    void invalidateOnLeave(UUID playerId);

    // ------------------------------------------------------------------
    // exact reads (bounded; no enumeration API)
    // ------------------------------------------------------------------

    /** Exact lookup: the facility with the given id, if any. */
    Optional<Facility> getFacility(FacilityId facilityId);

    /** Exact lookup: the terminal with the given id, if any. */
    Optional<Terminal> getTerminal(TerminalId terminalId);
}
