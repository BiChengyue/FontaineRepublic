package com.fontainerepublic.server.government.api;

/**
 * The aspect an {@link AppointmentReceipt} reports on.
 */
public enum AppointmentChangeKind {
    /** A holder was appointed to a VACANT position. */
    APPOINTED,

    /** An appointed holder was dismissed from a FILLED position. */
    DISMISSED
}
