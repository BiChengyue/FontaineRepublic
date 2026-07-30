package com.fontainerepublic.server.command.api;

import com.fontainerepublic.server.command.registration.CommandContributionSpec;

/**
 * Registration-only surface for compiled command contributions.
 */
@FunctionalInterface
public interface CommandContributionRegistrar {
    void register(CommandContributionSpec specification);
}
