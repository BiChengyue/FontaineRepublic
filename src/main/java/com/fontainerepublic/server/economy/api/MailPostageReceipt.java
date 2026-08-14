package com.fontainerepublic.server.economy.api;

import com.fontainerepublic.server.registry.model.SubjectId;

import java.util.Objects;

/**
 * Receipt of one atomic mail postage/attachment fee charge
 * (FR-MAIL-001-A §6.3).
 *
 * <p>The charge runs inside a single replacement economy snapshot: exactly
 * {@code total} is debited from the payer's personal account and credited to
 * the treasury in the same atomic unit, appended as one {@code TAX}
 * transaction leg ({@code to == null}). Total supply
 * {@code = sum(accounts) + treasury} is conserved exactly. The receipt's
 * transaction id is visible only after the FR-CORE-002 durable gate reported
 * {@code COMMITTED}; a failed charge publishes nothing. Institution senders
 * are {@code free} — no charge is invoked on their behalf.</p>
 */
public record MailPostageReceipt(
        long timestamp,
        SubjectId payer,
        long postageFee,
        long attachmentFee,
        long total,
        long transactionId,
        boolean applied
) {

    public MailPostageReceipt {
        if (timestamp <= 0) {
            throw new IllegalArgumentException(
                    "timestamp must be a positive epoch millisecond"
            );
        }
        payer = Objects.requireNonNull(payer, "payer");
        if (postageFee < 0 || attachmentFee < 0) {
            throw new IllegalArgumentException("Fees must not be negative");
        }
        long sum;
        try {
            sum = Math.addExact(postageFee, attachmentFee);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Fees would overflow", overflow);
        }
        if (sum != total) {
            throw new IllegalArgumentException(
                    "total must equal postageFee + attachmentFee"
            );
        }
        if (transactionId <= 0) {
            throw new IllegalArgumentException("transactionId must be positive");
        }
    }
}
