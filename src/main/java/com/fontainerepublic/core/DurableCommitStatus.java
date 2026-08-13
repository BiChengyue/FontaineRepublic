package com.fontainerepublic.core;

/**
 * Outcome of a durable commit request (FR-CORE-002).
 *
 * <p>{@link #COMMITTED} is returned only after the new root file has been
 * fsync'd and atomically renamed into place and the in-memory module map has
 * been swapped. {@link #FAILED} carries a stable, non-secret
 * {@link DurableCommitResult#failureCode()}; {@link #UNINITIALIZED} and
 * {@link #STOPPING} reject the call without any side effect.</p>
 */
public enum DurableCommitStatus {
    COMMITTED,
    FAILED,
    UNINITIALIZED,
    STOPPING
}
