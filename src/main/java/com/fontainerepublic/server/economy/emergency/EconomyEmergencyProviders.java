package com.fontainerepublic.server.economy.emergency;

import com.fontainerepublic.server.emergency.api.EmergencyActionProvider;

import java.util.Objects;
import java.util.Optional;

/**
 * Server-runtime holder of the current ACTIVE Economy emergency provider.
 *
 * <p>The frozen {@link com.fontainerepublic.server.emergency.api.EmergencyActionDescriptor}
 * holds only this stateless static resolver ({@link #resolve}); the provider
 * instance itself is bound by {@code EconomyModule} when its runtime starts
 * and cleared on shutdown, so descriptors never capture a per-server module,
 * Service, repository, or provider instance (FR-EMG-001-A 搂5).</p>
 */
public final class EconomyEmergencyProviders {

    private static volatile EmergencyActionProvider issue;
    private static volatile EmergencyActionProvider reclaim;

    private EconomyEmergencyProviders() {
    }

    public static void bind(
            EmergencyActionProvider issueProvider,
            EmergencyActionProvider reclaimProvider
    ) {
        issue = Objects.requireNonNull(issueProvider, "issueProvider");
        reclaim = Objects.requireNonNull(reclaimProvider, "reclaimProvider");
    }

    public static void unbind() {
        issue = null;
        reclaim = null;
    }

    /** Stateless runtime resolver of the ISSUE provider (frozen descriptor). */
    public static Optional<EmergencyActionProvider> resolveIssue() {
        return Optional.ofNullable(issue);
    }

    /** Stateless runtime resolver of the RECLAIM provider (frozen descriptor). */
    public static Optional<EmergencyActionProvider> resolveReclaim() {
        return Optional.ofNullable(reclaim);
    }
}
