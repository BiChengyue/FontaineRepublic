package com.fontainerepublic.client.gui.money;

/**
 * Pure command-string composer of the transfer form (FR-CLIENT-001-IMPL-B).
 *
 * <p>The form is an input-forwarding surface only: this class assembles the
 * {@code /fr money pay <target> <amount> [memo]} command string that is
 * submitted through {@code player.connection.sendCommand(...)}. It performs
 * the client-side display bounds only (bounded target, amount and memo per
 * the task contract); the server re-resolves the target and re-validates
 * amount, memo and every authority rule before any mutation. A rejected
 * input here is rejected before the wire; a server-side rejection surfaces
 * through the server feedback channel (chat) and is never optimistically
 * applied locally.</p>
 *
 * <p>This class has no Minecraft imports so the dependency-free validation
 * main can exercise it directly.</p>
 */
public final class TransferFormComposer {

    /** Bounded target input length (display bound; server re-resolves). */
    public static final int MAX_TARGET = 64;

    /** Minimum transfer amount (mirrors the server longArg(1) floor). */
    public static final long MIN_AMOUNT = 1L;

    /** Bounded transfer amount ceiling (display bound; server validates). */
    public static final long MAX_AMOUNT = 9_000_000_000_000_000L;

    /** Bounded memo length (matches the economy memo contract). */
    public static final int MAX_MEMO = 128;

    /** The server command path this form submits through. */
    public static final String COMMAND_ROOT = "fr money pay";

    private TransferFormComposer() {
    }

    /**
     * Composes the command string (without a leading slash, as expected by
     * {@code ClientPacketListener.sendCommand}).
     *
     * @param target     target input: canonical UUID, registry number, or
     *                   game name (no whitespace allowed)
     * @param amountText decimal amount input (ASCII digits only)
     * @param memo       optional memo; blank is treated as absent
     * @return the assembled command string
     * @throws IllegalArgumentException on any out-of-bounds or malformed input
     */
    public static String compose(String target, String amountText, String memo) {
        String targetValue = validateTarget(target);
        long amount = validateAmount(amountText);
        String memoValue = normalizeMemo(memo);
        StringBuilder command = new StringBuilder(COMMAND_ROOT)
                .append(' ').append(targetValue)
                .append(' ').append(amount);
        if (memoValue != null) {
            command.append(' ').append(memoValue);
        }
        return command.toString();
    }

    /** Returns the normalized target or throws a bounded error. */
    public static String validateTarget(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Target must not be empty.");
        }
        String trimmed = target.trim();
        if (trimmed.length() > MAX_TARGET) {
            throw new IllegalArgumentException(
                    "Target must be at most " + MAX_TARGET + " characters."
            );
        }
        for (int index = 0; index < trimmed.length(); index++) {
            if (Character.isWhitespace(trimmed.charAt(index))) {
                throw new IllegalArgumentException(
                        "Target must not contain whitespace."
                );
            }
        }
        return trimmed;
    }

    /** Parses and bounds the amount or throws a bounded error. */
    public static long validateAmount(String amountText) {
        if (amountText == null || amountText.isBlank()) {
            throw new IllegalArgumentException("Amount must not be empty.");
        }
        String trimmed = amountText.trim();
        for (int index = 0; index < trimmed.length(); index++) {
            char current = trimmed.charAt(index);
            if (current < '0' || current > '9') {
                throw new IllegalArgumentException(
                        "Amount must be a positive whole number."
                );
            }
        }
        long amount;
        try {
            amount = Long.parseLong(trimmed);
        } catch (NumberFormatException overflow) {
            throw new IllegalArgumentException(
                    "Amount is out of range (" + MIN_AMOUNT + ".." + MAX_AMOUNT + ")."
            );
        }
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException(
                    "Amount must be between " + MIN_AMOUNT + " and "
                            + MAX_AMOUNT + "."
            );
        }
        return amount;
    }

    /** Normalizes the optional memo; blank becomes absent. */
    public static String normalizeMemo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        String trimmed = memo.trim();
        if (trimmed.length() > MAX_MEMO) {
            throw new IllegalArgumentException(
                    "Memo must be at most " + MAX_MEMO + " characters."
            );
        }
        return trimmed;
    }
}
