package com.fontainerepublic.server.mail.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative public API of the mail subsystem (FR-MAIL-001-A).
 * All mutations run on the logical server owner thread and publish only after
 * the FR-CORE-002 durable gate; the sender's communicator gate and the
 * recipient/access resolution are enforced here (fail closed), never by the
 * client.
 *
 * <p>Recipients are either a natural person (by exact name / UUID / registry
 * number, resolved through PlayerData + subject registry) or a bounded
 * institution mailbox ({@code gov:government}, {@code gov:ministry:<id>},
 * {@code parliament}, {@code court}, {@code bank}; ministry ids resolved
 * through the government service). Institution senders/readers are gated by
 * the position-holder / mailbox-manager access policy.</p>
 */
public interface MailService {

    /**
     * Sends one direct mail from a player who must hold the communicator.
     *
     * @return a bounded result; a failure is reported in chat by the caller.
     */
    MailSendResult send(
            UUID actor,
            String recipient,
            String subject,
            String body,
            long moneyAttachment,
            List<Integer> itemSlots
    );

    /** Bounded list projection of the caller's mailbox (their own direct
     *  inbox + derived unread broadcasts), with the exact unread count. */
    MailboxView mailbox(UUID actor);

    /** Marks one mail as read and, for a direct mail with unclaimed
     *  attachments, atomically claims them (money credited, items moved into
     *  the receiver's inventory as far as space allows). */
    MailClaimResult read(UUID actor, long mailId);

    /** Deletes one mail from the caller's mailbox (or suppresses a broadcast
     *  for them). */
    void delete(UUID actor, long mailId);

    /** Institution-authorized broadcast to every citizen. Only an institution
     *  sender that passes the access policy may broadcast; ordinary players
     *  are refused. Bounded by a cooldown. */
    MailSendResult broadcast(UUID actor, String subject, String body);

    /** True when the actor may read the given mailbox. */
    boolean canAccessMailbox(UUID actor, MailboxKey mailbox);

    /** Resolves a textual recipient (name/UUID/registry/institution key)
     *  into a mailbox identity, or empty (fail closed). */
    Optional<MailboxKey> resolveRecipient(String recipient);

    /** Computes and pushes the mailbox sync (and unread alert) to the actor,
     *  used at login and on the C2S list request. Best-effort: no client /
     *  absent channel is a no-op. */
    void requestSync(java.util.UUID actor);
}
