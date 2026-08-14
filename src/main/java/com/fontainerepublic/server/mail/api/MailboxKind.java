package com.fontainerepublic.server.mail.api;

/**
 * Kind of a mail recipient mailbox (FR-MAIL-001-A §2.2).
 */
public enum MailboxKind {
    /** A natural person (player UUID). */
    PERSON,
    /** A bounded whitelist institution mailbox. */
    INSTITUTION
}
