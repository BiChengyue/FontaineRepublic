package com.fontainerepublic.server.mail.api;

import com.fontainerepublic.server.registry.model.SubjectId;

import java.util.UUID;

/**
 * Institution mailbox access policy of the mail subsystem (FR-MAIL-001-A
 * §6.1/§6.4): who may read/list/delete an institution mailbox and who may act
 * as an institution sender (broadcast or institution direct mail). Resolved
 * server-side and fail closed — the client never decides.
 *
 * <p>Policy: a ministry mailbox is accessible to any current holder of any
 * position in that ministry; the government/parliament/court/bank mailboxes are
 * accessible to the configured mailbox manager for that institution (default:
 * the Hydro Archon and the local console). Nothing is ever implied to be
 * authorized by virtue of being a citizen.</p>
 */
public interface MailAuthority {

    /**
     * Whether the actor (player UUID, or the local console represented by a
     * sentinel subject) may read/list/delete the given institution mailbox.
     */
    boolean canReadMailbox(UUID actor, MailboxKey mailbox);

    /**
     * Whether the actor may send mail acting as the given institution mailbox
     * (the institution's outgoing identity; institution sends are free).
     */
    boolean canActAsInstitution(UUID actor, MailboxKey institutionMailbox);

    /**
     * Resolves the actor's acting institution mailbox for a send to
     * {@code recipient}, or empty when the actor has no institution authority.
     */
    java.util.Optional<MailboxKey> actingInstitution(UUID actor, MailboxKey recipient);

    /** Sentinel subject representing the local server console (an
     *  institution-authoritative actor for institution mailboxes). */
    SubjectId consoleSubject();
}
