package com.fontainerepublic.server.audit.model;

/**
 * Every audit entry is classified; no unclassified entries exist
 * (FR-AUD-001-A §3.1).
 *
 * <ul>
 *   <li>{@link #PUBLIC} — safe for ordinary read/projection.</li>
 *   <li>{@link #AUTHORIZED_SUMMARY} — projection is an authorized summary.</li>
 *   <li>{@link #SECRET_DIGEST_ONLY} — secret plaintext is never persisted;
 *       only the canonical payload digest is stored (FR-AUD-001-A §3.1).</li>
 * </ul>
 */
public enum AuditClassification {
    PUBLIC,
    AUTHORIZED_SUMMARY,
    SECRET_DIGEST_ONLY
}
