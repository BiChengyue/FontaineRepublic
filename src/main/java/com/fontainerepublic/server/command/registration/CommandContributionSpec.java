package com.fontainerepublic.server.command.registration;

import com.fontainerepublic.server.command.api.CommandTreeFactory;

import java.util.Objects;

/**
 * Immutable Mod-lifetime definition for one future top-level command contribution.
 */
public record CommandContributionSpec(
        String topLevelLiteral,
        CommandTreeFactory treeFactory
) {
    public CommandContributionSpec {
        treeFactory = Objects.requireNonNull(treeFactory, "treeFactory");
    }
}
