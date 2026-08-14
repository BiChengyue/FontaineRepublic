package com.fontainerepublic.server.mail.service;

import com.fontainerepublic.core.ConfigManager;
import com.fontainerepublic.server.government.api.GovernmentService;
import com.fontainerepublic.server.government.model.MinistryId;
import com.fontainerepublic.server.mail.api.MailAuthority;
import com.fontainerepublic.server.mail.api.MailboxKey;
import com.fontainerepublic.server.registry.model.OwnerReference;
import com.fontainerepublic.server.registry.model.SubjectId;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Production institution mailbox access policy (FR-MAIL-001-A §6.1/§6.4).
 *
 * <p>Ministry mailboxes are accessible/actable by any current holder of any
 * position in that ministry (resolved through {@link GovernmentService});
 * the government/parliament/court/bank mailboxes are accessible by the
 * configured mailbox manager for that institution, defaulting to the Hydro
 * Archon (and the local console). A player is never implicitly authorized by
 * being a citizen. Fail closed on unknown/unauthorized access.</p>
 */
public final class ServerMailAuthority implements MailAuthority {

    private static final String PARLIAMENT = "parliament";
    private static final String COURT = "court";
    private static final String BANK = "bank";
    private static final String GOV_GOVERNMENT = "gov:government";

    private final GovernmentService government;
    private final UUID hydroArchon;
    private final UUID consoleSubjectValue;
    private final Set<UUID> parliamentManagers = new LinkedHashSet<>();
    private final Set<UUID> courtManagers = new LinkedHashSet<>();
    private final Set<UUID> bankManagers = new LinkedHashSet<>();
    private final Set<UUID> governmentManagers = new LinkedHashSet<>();

    public ServerMailAuthority(
            GovernmentService government,
            UUID hydroArchonUuid,
            UUID consoleSubjectValue
    ) {
        this.government = Objects.requireNonNull(government, "government");
        this.hydroArchon = hydroArchonUuid;
        this.consoleSubjectValue = Objects.requireNonNull(consoleSubjectValue, "consoleSubjectValue");
        addAll(parliamentManagers, ConfigManager.mailManagerParliament());
        addAll(courtManagers, ConfigManager.mailManagerCourt());
        addAll(bankManagers, ConfigManager.mailManagerBank());
        addAll(governmentManagers, ConfigManager.mailManagerGovernment());
    }

    private static void addAll(Set<UUID> into, String commaSeparated) {
        for (String part : commaSeparated.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                into.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ignored) {
                // malformed configured uuid is ignored (fail-safe to default)
            }
        }
    }

    /** A configured manager (or the Hydro Archon when none configured). */
    boolean isManager(Set<UUID> managers, UUID actor) {
        if (managers.isEmpty()) {
            return isHydroArchon(actor);
        }
        return managers.contains(actor);
    }

    private boolean isHydroArchon(UUID actor) {
        return hydroArchon != null && hydroArchon.equals(actor);
    }

    /** The local console is always an institution authority. */
    private boolean isConsole(UUID actor) {
        return consoleSubjectValue.equals(actor);
    }

    @Override
    public boolean canReadMailbox(UUID actor, MailboxKey mailbox) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(mailbox, "mailbox");
        if (mailbox.isMinistry()) {
            return isMinistryHolder(actor, mailbox) || isConsole(actor);
        }
        return canManage(actor, mailbox.subject()) || isConsole(actor);
    }

    @Override
    public boolean canActAsInstitution(UUID actor, MailboxKey institutionMailbox) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(institutionMailbox, "institutionMailbox");
        return canReadMailbox(actor, institutionMailbox);
    }

    @Override
    public Optional<MailboxKey> actingInstitution(UUID actor, MailboxKey recipient) {
        Objects.requireNonNull(actor, "actor");
        if (isConsole(actor)) {
            return Optional.of(MailboxKey.institution(GOV_GOVERNMENT));
        }
        // A recipient ministry mailbox that the actor holds takes precedence.
        if (recipient != null && recipient.isMinistry()
                && isMinistryHolder(actor, recipient)) {
            return Optional.of(recipient);
        }
        // The actor's own ministry holder identity.
        Optional<MailboxKey> ministry = firstHeldMinistry(actor);
        if (ministry.isPresent()) {
            return ministry;
        }
        // Mailbox managers / Hydro Archon.
        if (isManager(parliamentManagers, actor)) {
            return Optional.of(MailboxKey.institution(PARLIAMENT));
        }
        if (isManager(courtManagers, actor)) {
            return Optional.of(MailboxKey.institution(COURT));
        }
        if (isManager(bankManagers, actor)) {
            return Optional.of(MailboxKey.institution(BANK));
        }
        if (isManager(governmentManagers, actor) || isHydroArchon(actor)) {
            return Optional.of(MailboxKey.institution(GOV_GOVERNMENT));
        }
        return Optional.empty();
    }

    @Override
    public SubjectId consoleSubject() {
        return SubjectId.of(consoleSubjectValue);
    }

    private Optional<MailboxKey> firstHeldMinistry(UUID actor) {
        if (government == null) {
            return Optional.empty();
        }
        for (var projection : government.ministries()) {
            MinistryId ministryId = projection.ministryId();
            if (isMinistryHolder(actor, MailboxKey.institution("gov:ministry:" + ministryId))) {
                return Optional.of(MailboxKey.institution("gov:ministry:" + ministryId));
            }
        }
        return Optional.empty();
    }

    private boolean isMinistryHolder(UUID actor, MailboxKey ministryMailbox) {
        if (government == null) {
            return false;
        }
        String ministryId = ministryMailbox.ministryId();
        if (ministryId == null) {
            return false;
        }
        try {
            MinistryId id = MinistryId.of(UUID.fromString(ministryId));
            if (government.getMinistry(id).isEmpty()) {
                return false;
            }
            OwnerReference holder = OwnerReference.forPlayer(actor);
            for (var position : government.positionsByMinistry(id)) {
                if (position.holderRef().isPresent()
                        && position.holderRef().get().equals(holder)) {
                    return true;
                }
            }
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        return false;
    }

    private boolean canManage(UUID actor, String institutionKey) {
        switch (institutionKey) {
            case PARLIAMENT:
                return isManager(parliamentManagers, actor);
            case COURT:
                return isManager(courtManagers, actor);
            case BANK:
                return isManager(bankManagers, actor);
            case GOV_GOVERNMENT:
                return isManager(governmentManagers, actor) || isHydroArchon(actor);
            default:
                return false;
        }
    }
}
