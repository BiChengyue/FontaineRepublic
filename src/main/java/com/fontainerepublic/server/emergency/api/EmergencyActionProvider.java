package com.fontainerepublic.server.emergency.api;

/**
 * Business-side emergency action provider contract (FR-EMG-001-A §5 / §9).
 *
 * <p>Business modules implement this contract and register an immutable
 * descriptor plus a stable runtime resolver. The provider owns its data,
 * invariants, validation, revisions, persistence, and reversible rules.
 * Emergency authority never permits raw repository access by callers; the
 * provider receives only the typed envelope and returns a typed result.</p>
 */
public interface EmergencyActionProvider {

    /** Immutable provider identity (module/provider id). */
    String providerIdentity();

    /** Provider implementation digest/version (frozen in the descriptor). */
    String providerVersion();

    /**
     * Typed, side-effect-free validation producing an immutable plan. Must not
     * perform external side effects.
     */
    EmergencyPlan preview(EmergencyMutationEnvelope envelope);

    /**
     * Commits one owned business snapshot. Accepts only the typed plan plus
     * the confirmed request envelope. Success publishes business state +
     * authoritative success receipt + pending notification together and
     * increments the relevant revision exactly once; failure changes nothing.
     */
    EmergencyMutationResult apply(EmergencyPlan plan, EmergencyMutationEnvelope envelope);
}
