package com.fontainerepublic.server.registry.model;

/**
 * Server-authoritative lifecycle state of a subject record
 * (FR-ID-001-A §3.5).
 *
 * <p>There is no automatic expiry. Numbers remain reserved in every
 * non-{@code ACTIVE} state. This infrastructure enum does not decide who may
 * change a state or the political/judicial/administrative/commercial grounds;
 * consumers receive the status and fail closed where their policy is
 * unresolved.</p>
 */
public enum SubjectStatus {

    /** Registry record is usable subject to consumer business checks. */
    ACTIVE,

    /** Temporarily non-active; the number remains reserved. */
    SUSPENDED,

    /** Registration permanently revoked; record and number retained. */
    REVOKED,

    /** Non-person subject ended; record and number retained. */
    DISSOLVED
}
