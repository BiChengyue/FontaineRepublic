package com.fontainerepublic.server.registry.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Permanent public subject number (FR-ID-001-A §4).
 *
 * <p>Canonical form is exactly ten ASCII digits {@code TTNNNNNNCC}; the
 * presentation form is {@code TT-NNNNNN-CC}. Parsers accept exactly the
 * canonical or the display form and nothing else (no whitespace, Unicode
 * digits, arbitrary punctuation, or formatting codes). The check digits follow
 * the ISO-style MOD 97-10 rule computed digit by digit, without floating point
 * or locale-dependent formatting.</p>
 *
 * <p>Type rules enforced at construction:</p>
 * <ul>
 *   <li>type {@code 00} accepts only the exact Human-fixed office number
 *       {@code 00-000001-95}; every other {@code 00} number is invalid;</li>
 *   <li>undefined type codes {@code 01}–{@code 09} are rejected;</li>
 *   <li>all other codes ({@code 10}–{@code 99}) are format-valid; ordinary
 *       allocation is possible only for enabled concrete types.</li>
 * </ul>
 *
 * <p>A valid checksum proves correct transcription only, never authority.</p>
 */
public record RegistryNumber(String canonical) {

    public static final int CANONICAL_LENGTH = 10;
    public static final int BODY_LENGTH = 8;
    public static final int DISPLAY_LENGTH = 12; // TT-NNNNNN-CC

    /** Human-fixed number of the original Hydro Archon natural person. */
    public static final String FIXED_PERSONAL_CANONICAL = "1000000161";

    /** Human-fixed number of the permanent Hydro Archon office subject. */
    public static final String FIXED_OFFICE_CANONICAL = "0000000195";

    public static final RegistryNumber FIXED_PERSONAL =
            new RegistryNumber(FIXED_PERSONAL_CANONICAL);
    public static final RegistryNumber FIXED_OFFICE =
            new RegistryNumber(FIXED_OFFICE_CANONICAL);

    public RegistryNumber {
        Objects.requireNonNull(canonical, "canonical");
        if (canonical.length() != CANONICAL_LENGTH) {
            throw new IllegalArgumentException(
                    "Registry number must be exactly " + CANONICAL_LENGTH + " ASCII digits"
            );
        }
        for (int index = 0; index < canonical.length(); index++) {
            char c = canonical.charAt(index);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException(
                        "Registry number must be exactly " + CANONICAL_LENGTH + " ASCII digits"
                );
            }
        }
        if (mod97(canonical) != 1) {
            throw new IllegalArgumentException(
                    "Registry number fails the MOD 97 check: " + canonical
            );
        }
        String typeCode = canonical.substring(0, 2);
        if (typeCode.equals("00") && !canonical.equals(FIXED_OFFICE_CANONICAL)) {
            throw new IllegalArgumentException(
                    "Type 00 accepts only the exact fixed office number "
                            + FIXED_OFFICE_CANONICAL + "; got " + canonical
            );
        }
        if (typeCode.startsWith("0") && !typeCode.equals("00")) {
            throw new IllegalArgumentException(
                    "Unsupported registry type code: " + typeCode
            );
        }
    }

    /**
     * Parses exactly the canonical ten-digit form or the {@code TT-NNNNNN-CC}
     * display form. Any other input is rejected.
     */
    public static RegistryNumber parse(String input) {
        Objects.requireNonNull(input, "input");
        String canonical;
        if (input.length() == CANONICAL_LENGTH) {
            canonical = input;
        } else if (input.length() == DISPLAY_LENGTH
                && input.charAt(2) == '-'
                && input.charAt(9) == '-') {
            canonical = input.substring(0, 2)
                    + input.substring(3, 9)
                    + input.substring(10, 12);
        } else {
            throw new IllegalArgumentException(
                    "Registry number must be 10 digits or TT-NNNNNN-CC, got: '" + input + "'"
            );
        }
        return new RegistryNumber(canonical);
    }

    /**
     * Builds the number for an enabled concrete type and a six-digit serial
     * ({@code 000000}–{@code 999999}) with computed check digits. Rejects
     * types that have no ordinary allocation pool.
     */
    public static RegistryNumber forTypeAndSerial(SubjectType subjectType, int serial) {
        Objects.requireNonNull(subjectType, "subjectType");
        if (serial < 0 || serial > 999_999) {
            throw new IllegalArgumentException("Serial out of range: " + serial);
        }
        if (!subjectType.isAllocatable()) {
            throw new IllegalArgumentException(
                    "No ordinary allocation exists for type " + subjectType
            );
        }
        String body = subjectType.typeCode() + String.format(Locale.ROOT, "%06d", serial);
        return new RegistryNumber(body + computeCheckDigits(body));
    }

    /**
     * MOD 97 remainder of a digit string, accumulated digit by digit
     * (normative method; no floating point, no locale).
     */
    public static int mod97(String digits) {
        int remainder = 0;
        for (int index = 0; index < digits.length(); index++) {
            char c = digits.charAt(index);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("MOD 97 input must be ASCII digits");
            }
            remainder = (remainder * 10 + (c - '0')) % 97;
        }
        return remainder;
    }

    /**
     * Computes the two check digits for an eight-digit body
     * {@code TTNNNNNN}: {@code CC = 98 - ((B * 100) mod 97)}.
     */
    static String computeCheckDigits(String body) {
        if (body == null || body.length() != BODY_LENGTH) {
            throw new IllegalArgumentException(
                    "Check-digit body must be exactly " + BODY_LENGTH + " digits"
            );
        }
        int bodyRemainder = mod97(body);
        int check = 98 - ((bodyRemainder * 100) % 97);
        return String.format(Locale.ROOT, "%02d", check);
    }

    /** Canonical ten-digit storage/digest form. */
    public String canonical() {
        return canonical;
    }

    /** Presentation form {@code TT-NNNNNN-CC}. */
    public String display() {
        return canonical.substring(0, 2)
                + "-"
                + canonical.substring(2, 8)
                + "-"
                + canonical.substring(8, 10);
    }

    public String typeCode() {
        return canonical.substring(0, 2);
    }

    public String serial() {
        return canonical.substring(2, 8);
    }

    public String checkDigits() {
        return canonical.substring(8, 10);
    }

    @Override
    public String toString() {
        return canonical;
    }
}
