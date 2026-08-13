package com.fontainerepublic.server.economy.api;

import java.util.Locale;
import java.util.Objects;

/**
 * Configurable currency presentation (FR-ECO-001-C §7).
 *
 * <p>The server may configure the display name, symbol and thousands
 * grouping. Presentation is projection only: it never changes stored numeric
 * values, transaction semantics, total supply, or authority. One unit is one
 * indivisible currency unit (FR-ECO-001-A §13.1); amounts are rendered as
 * plain non-negative integers.</p>
 */
public record CurrencyPresentation(
        String displayName,
        String symbol,
        boolean grouping
) {

    public static final CurrencyPresentation DEFAULT =
            new CurrencyPresentation("Mora", "", true);

    public CurrencyPresentation {
        displayName = Objects.requireNonNull(displayName, "displayName");
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be empty");
        }
        if (displayName.length() > 32) {
            throw new IllegalArgumentException("displayName is bounded to 32 characters");
        }
        symbol = Objects.requireNonNull(symbol, "symbol");
        if (symbol.length() > 8) {
            throw new IllegalArgumentException("symbol is bounded to 8 characters");
        }
    }

    /**
     * Renders a non-negative amount with the configured grouping and symbol.
     * Pure presentation: the numeric value is never altered.
     */
    public String format(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Cannot format a negative amount");
        }
        String digits = Long.toString(amount);
        StringBuilder rendered = new StringBuilder();
        if (grouping && digits.length() > 3) {
            int firstGroup = digits.length() % 3;
            if (firstGroup == 0) {
                firstGroup = 3;
            }
            rendered.append(digits, 0, firstGroup);
            for (int index = firstGroup; index < digits.length(); index += 3) {
                rendered.append(',');
                rendered.append(digits, index, index + 3);
            }
        } else {
            rendered.append(digits);
        }
        if (!symbol.isEmpty()) {
            rendered.append(' ').append(symbol);
        }
        return rendered.toString();
    }

    @Override
    public String toString() {
        return "CurrencyPresentation{displayName=" + displayName
                + ", symbol=" + symbol
                + ", grouping=" + grouping
                + ", locale=" + Locale.ROOT + '}';
    }
}
