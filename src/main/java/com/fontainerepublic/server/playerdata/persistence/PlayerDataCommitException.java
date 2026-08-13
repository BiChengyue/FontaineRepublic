package com.fontainerepublic.server.playerdata.persistence;

import com.fontainerepublic.core.DurableCommitStatus;

import java.util.Objects;

/**
 * Raised when the storage adapter does not acknowledge a proposed
 * player-data snapshot (FR-DATA-003-A §13.2). The repository guarantees that
 * a failed commit changes no player record, directory entry, revision, or
 * published result.
 */
public final class PlayerDataCommitException extends RuntimeException {
    private final DurableCommitStatus status;
    private final String failureCode;

    public PlayerDataCommitException(DurableCommitStatus status, String failureCode) {
        super("player-data commit rejected: status=" + status
                + (failureCode == null || failureCode.isEmpty() ? ""
                : " code=" + failureCode));
        this.status = Objects.requireNonNull(status, "status");
        this.failureCode = failureCode == null ? "" : failureCode;
    }

    public DurableCommitStatus status() {
        return status;
    }

    /** Stable, non-secret failure code from the storage adapter. */
    public String failureCode() {
        return failureCode;
    }
}
