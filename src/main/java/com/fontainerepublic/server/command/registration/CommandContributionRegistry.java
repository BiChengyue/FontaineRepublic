package com.fontainerepublic.server.command.registration;

import com.fontainerepublic.server.command.api.CommandContributionRegistrar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Mod-lifetime OPEN-to-FROZEN registry for immutable command specifications.
 */
public final class CommandContributionRegistry implements CommandContributionRegistrar {
    private static final int MAX_LITERAL_LENGTH = 32;
    private static final Pattern VALID_LITERAL =
            Pattern.compile("[a-z][a-z0-9_-]{0," + (MAX_LITERAL_LENGTH - 1) + "}");
    private static final Set<String> RESERVED_LITERALS = Set.of("admin", "fr", "help");

    private final LinkedHashMap<String, CommandContributionSpec> specifications =
            new LinkedHashMap<>();
    private List<CommandContributionSpec> frozenSnapshot;

    @Override
    public synchronized void register(CommandContributionSpec specification) {
        if (frozenSnapshot != null) {
            throw new CommandRegistrationException(
                    "Command contribution registry is already frozen"
            );
        }
        if (specification == null) {
            throw new CommandRegistrationException("Command specification must not be null");
        }

        String literal = validateLiteral(specification.topLevelLiteral());
        if (RESERVED_LITERALS.contains(literal)) {
            throw new CommandRegistrationException(
                    "Reserved command literal cannot be contributed: " + literal
            );
        }
        if (specifications.containsKey(literal)) {
            throw new CommandRegistrationException(
                    "Duplicate command contribution literal: " + literal
            );
        }
        specifications.put(
                literal,
                new CommandContributionSpec(literal, specification.treeFactory())
        );
    }

    public synchronized List<CommandContributionSpec> freeze() {
        if (frozenSnapshot != null) {
            throw new CommandRegistrationException(
                    "Command contribution registry has already been frozen"
            );
        }
        ArrayList<CommandContributionSpec> ordered =
                new ArrayList<>(specifications.values());
        ordered.sort((left, right) ->
                left.topLevelLiteral().compareTo(right.topLevelLiteral()));
        frozenSnapshot = Collections.unmodifiableList(ordered);
        return frozenSnapshot;
    }

    public synchronized List<CommandContributionSpec> requireFrozenSnapshot() {
        if (frozenSnapshot == null) {
            throw new CommandRegistrationException(
                    "Command contribution registry is not frozen"
            );
        }
        return frozenSnapshot;
    }

    public synchronized boolean isFrozen() {
        return frozenSnapshot != null;
    }

    private String validateLiteral(String literal) {
        if (literal == null) {
            throw new CommandRegistrationException(
                    "Command contribution literal must not be null"
            );
        }
        if (!literal.equals(literal.trim())) {
            throw new CommandRegistrationException(
                    "Command contribution literal must not contain surrounding whitespace"
            );
        }
        if (!VALID_LITERAL.matcher(literal).matches()) {
            throw new CommandRegistrationException(
                    "Invalid command contribution literal: " + literal
            );
        }
        return literal;
    }
}
