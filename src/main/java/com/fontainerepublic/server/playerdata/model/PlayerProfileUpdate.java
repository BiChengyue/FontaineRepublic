package com.fontainerepublic.server.playerdata.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Requested replacement values for the optional profile fields.
 */
public record PlayerProfileUpdate(
        Optional<String> displayName,
        Optional<String> locale,
        Optional<String> description
) {
    public PlayerProfileUpdate {
        displayName = Objects.requireNonNull(displayName, "displayName");
        locale = Objects.requireNonNull(locale, "locale");
        description = Objects.requireNonNull(description, "description");
    }

    public PlayerProfile toProfile(long updatedAt) {
        return new PlayerProfile(displayName, locale, description, updatedAt);
    }
}
