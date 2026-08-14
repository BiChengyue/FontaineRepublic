package com.fontainerepublic.server.mail.api;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable identity of a mail recipient mailbox (FR-MAIL-001-A §2.2/§6.1).
 *
 * <p>A mailbox is owned by either a natural person (player UUID) or a bounded
 * institution. Institution mailboxes are a fixed whitelist plus any
 * {@code gov:ministry:<id>} resolved through the government service:
 * {@code gov:government}, {@code gov:ministry:<id>}, {@code parliament},
 * {@code court}, {@code bank}. The canonical string key ({@link #key()}) is
 * what the store, the network projection and the recipient-resolution fail
 * closed on.</p>
 */
public record MailboxKey(MailboxKind kind, String subject) {

    private static final String PERSON_PREFIX = "person:";
    private static final String INSTITUTION_PREFIX = "institution:";
    private static final Pattern INSTITUTION_PATTERN =
            Pattern.compile("^(gov:government|gov:ministry:[0-9A-Za-z:_-]{1,48}|parliament|court|bank)$");

    public MailboxKey {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subject, "subject");
        String trimmed = subject.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("mailbox subject must not be empty");
        }
        if (kind == MailboxKind.PERSON) {
            if (!trimmed.equals(trimmed.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("person subject must be lowercase canonical");
            }
            if (!trimmed.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
                throw new IllegalArgumentException("person subject is not a canonical UUID: " + trimmed);
            }
        } else if (!INSTITUTION_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("unknown institution mailbox: " + trimmed);
        }
        subject = trimmed;
    }

    /** Personal mailbox of a player UUID. */
    public static MailboxKey person(java.util.UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return new MailboxKey(MailboxKind.PERSON, playerId.toString());
    }

    /** Institution mailbox from the bounded whitelist form. */
    public static MailboxKey institution(String institutionKey) {
        return new MailboxKey(MailboxKind.INSTITUTION, institutionKey.trim());
    }

    /** The canonical string key used throughout the store and projections. */
    public String key() {
        return (kind == MailboxKind.PERSON ? PERSON_PREFIX : INSTITUTION_PREFIX) + subject;
    }

    /** Parses canonical key form back into a mailbox identity; {@code null}
     *  on unknown form (fail closed). */
    public static MailboxKey parse(String key) {
        Objects.requireNonNull(key, "key");
        if (key.startsWith(PERSON_PREFIX)) {
            return new MailboxKey(
                    MailboxKind.PERSON,
                    key.substring(PERSON_PREFIX.length())
            );
        }
        if (key.startsWith(INSTITUTION_PREFIX)) {
            return new MailboxKey(
                    MailboxKind.INSTITUTION,
                    key.substring(INSTITUTION_PREFIX.length())
            );
        }
        return null;
    }

    /** True when this is the fixed {@code gov:government} mailbox. */
    public boolean isGovernment() {
        return kind == MailboxKind.INSTITUTION
                && subject.equals("gov:government");
    }

    /** True when this is a {@code gov:ministry:<id>} mailbox. */
    public boolean isMinistry() {
        return kind == MailboxKind.INSTITUTION
                && subject.startsWith("gov:ministry:");
    }

    /** Ministry id for a {@code gov:ministry:<id>} mailbox, else null. */
    public String ministryId() {
        return isMinistry() ? subject.substring("gov:ministry:".length()) : null;
    }

    /** The UUID representation for a PERSON mailbox, else null. */
    public java.util.UUID subjectUuid() {
        if (kind != MailboxKind.PERSON) {
            return null;
        }
        try {
            return java.util.UUID.fromString(subject);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
