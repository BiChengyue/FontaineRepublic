package com.fontainerepublic.server.land.api;

import com.fontainerepublic.server.land.model.ViolationReport;

/**
 * Outcome of a violation-report creation (FR-LAND-001-A §3.4).
 *
 * <p>A receipt is returned only after the report was durably committed. The
 * report is read-only: FR-LAND has no update/delete/close entry; adjudication
 * is a later Justice integration.</p>
 *
 * @param report   the created read-only report
 * @param applied  always {@code true} for a committed creation
 * @param atMillis server-assigned timestamp of the receipt
 */
public record ViolationReceipt(
        ViolationReport report,
        boolean applied,
        long atMillis
) {
}
