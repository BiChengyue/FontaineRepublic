package com.fontainerepublic.server.playerdata.model;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Exact-name validation and canonicalization (FR-DATA-003-A §3).
 *
 * <p>The canonical lookup key is the validated ASCII input converted to
 * lowercase with locale-independent {@link Locale#ROOT} rules. Unicode is not
 * accepted, no Unicode normalization is performed, and input is never
 * trimmed: an input requiring trimming is invalid.</p>
 */
public final class GameNameNormalizer {
    private static final Pattern GAME_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private GameNameNormalizer() {
    }

    /**
     * Returns the canonical ASCII-lowercase key when {@code input} matches the
     * exact syntax contract {@code [A-Za-z0-9_]{1,16}} exactly as received
     * (no trimming), otherwise {@link Optional#empty()}.
     */
    public static Optional<String> normalize(String input) {
        if (input == null || !GAME_NAME.matcher(input).matches()) {
            return Optional.empty();
        }
        return Optional.of(input.toLowerCase(Locale.ROOT));
    }
}
