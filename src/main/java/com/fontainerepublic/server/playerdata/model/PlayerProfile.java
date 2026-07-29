package com.fontainerepublic.server.playerdata.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Optional, non-authoritative presentation data.
 */
public record PlayerProfile(
        Optional<String> displayName,
        Optional<String> locale,
        Optional<String> description,
        long updatedAt
) {
    private static final int MAX_DISPLAY_NAME_CODE_POINTS = 32;
    private static final int MAX_DESCRIPTION_CODE_POINTS = 256;
    private static final int MAX_LOCALE_LENGTH = 16;
    private static final Pattern LANGUAGE_TAG = Pattern.compile(
            "(?i)[a-z]{2,8}(?:-[a-z0-9]{1,8})*"
    );

    public PlayerProfile {
        displayName = normalizeText(
                displayName,
                "displayName",
                MAX_DISPLAY_NAME_CODE_POINTS
        );
        locale = normalizeLocale(locale);
        description = normalizeText(
                description,
                "description",
                MAX_DESCRIPTION_CODE_POINTS
        );
        if (updatedAt < 0) {
            throw new IllegalArgumentException("updatedAt must not be negative");
        }
    }

    public static PlayerProfile empty(long updatedAt) {
        return new PlayerProfile(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                updatedAt
        );
    }

    private static Optional<String> normalizeText(
            Optional<String> value,
            String fieldName,
            int maximumCodePoints
    ) {
        Objects.requireNonNull(value, fieldName);
        return value.map(String::trim)
                .filter(text -> !text.isEmpty())
                .map(text -> validateText(text, fieldName, maximumCodePoints));
    }

    private static String validateText(
            String value,
            String fieldName,
            int maximumCodePoints
    ) {
        if (value.codePointCount(0, value.length()) > maximumCodePoints) {
            throw new IllegalArgumentException(
                    fieldName + " exceeds " + maximumCodePoints + " Unicode code points"
            );
        }
        if (value.indexOf('\u00A7') >= 0
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(
                    fieldName + " contains formatting or control characters"
            );
        }
        return value;
    }

    private static Optional<String> normalizeLocale(Optional<String> value) {
        Objects.requireNonNull(value, "locale");
        return value.map(String::trim)
                .filter(text -> !text.isEmpty())
                .map(text -> {
                    if (text.length() > MAX_LOCALE_LENGTH
                            || !LANGUAGE_TAG.matcher(text).matches()) {
                        throw new IllegalArgumentException("locale is not a valid language tag");
                    }
                    String normalized = Locale.forLanguageTag(text).toLanguageTag();
                    if ("und".equalsIgnoreCase(normalized)
                            || normalized.length() > MAX_LOCALE_LENGTH) {
                        throw new IllegalArgumentException("locale is not a valid language tag");
                    }
                    return normalized;
                });
    }
}
