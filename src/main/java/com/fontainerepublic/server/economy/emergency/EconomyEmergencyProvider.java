package com.fontainerepublic.server.economy.emergency;

import com.fontainerepublic.server.economy.model.EconomyAccount;
import com.fontainerepublic.server.economy.persistence.EconomyRepository;
import com.fontainerepublic.server.economy.persistence.EconomyUnavailableException;
import com.fontainerepublic.server.emergency.api.EmergencyActionProvider;
import com.fontainerepublic.server.emergency.api.EmergencyMutationEnvelope;
import com.fontainerepublic.server.emergency.api.EmergencyMutationResult;
import com.fontainerepublic.server.emergency.api.EmergencyPlan;
import com.fontainerepublic.server.emergency.model.EmergencyCategory;
import com.fontainerepublic.server.emergency.model.EmergencyDigests;
import com.fontainerepublic.server.emergency.model.EmergencyTargetType;
import com.fontainerepublic.server.registry.model.SubjectId;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Economy-owned emergency action provider for exactly {@code economy.issue}
 * and {@code economy.reclaim} (FR-ECO-001-C 搂9-12).
 *
 * <p>The provider owns business validation and mutation only: it never
 * reimplements actor/console checks, confirmation tokens, the attempt
 * journal, generic inspection, or the shared audit index (FR-EMG-001-A).
 * {@link #preview} is side-effect-free and returns an immutable plan whose
 * {@code revisionLabel} deterministically encodes the affected account and
 * store revisions so the shared service can detect any state change between
 * preview and confirm. {@link #apply} re-reads the current revisions at the
 * final mutation boundary and submits one durable replacement snapshot
 * (balance + supply + transaction + success receipt + pending notification).</p>
 */
public final class EconomyEmergencyProvider implements EmergencyActionProvider {

    /** Stable provider identity of the ISSUE provider (module/provider id). */
    public static final String PROVIDER_IDENTITY_ISSUE = "economy/emergency/issue";

    /** Stable provider identity of the RECLAIM provider. */
    public static final String PROVIDER_IDENTITY_RECLAIM = "economy/emergency/reclaim";

    /** Provider implementation digest/version. */
    public static final String PROVIDER_VERSION = "1";

    public static final String ACTION_ISSUE = "issue";
    public static final String ACTION_RECLAIM = "reclaim";
    public static final String PARAM_AMOUNT = "amount";

    /** Allowed categories per FR-ECO-001-C 搂9. */
    private static final Set<EmergencyCategory> ALLOWED_CATEGORIES = Set.of(
            EmergencyCategory.DEBUG,
            EmergencyCategory.CORRECTION,
            EmergencyCategory.COMPENSATION,
            EmergencyCategory.DISASTER_RELIEF,
            EmergencyCategory.EMERGENCY_RESPONSE
    );

    private final EconomyRepository repository;
    private final String providerIdentity;

    public EconomyEmergencyProvider(
            EconomyRepository repository, String providerIdentity
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.providerIdentity = Objects.requireNonNull(
                providerIdentity, "providerIdentity"
        );
    }

    @Override
    public String providerIdentity() {
        return providerIdentity;
    }

    @Override
    public String providerVersion() {
        return PROVIDER_VERSION;
    }

    @Override
    public EmergencyPlan preview(EmergencyMutationEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        try {
            validateAction(envelope);
            long amount = parseAmount(envelope.parameters());
            SubjectId target = subjectId(envelope);
            Optional<EconomyAccount> account = repository.findAccount(target);
            String revisionLabel = revisionLabel(target, account);
            long balanceBefore = account.isEmpty() ? 0L : account.get().balance();
            if (ACTION_RECLAIM.equals(envelope.actionId())) {
                if (account.isEmpty()) {
                    return EmergencyPlan.rejected("NO_ACCOUNT", revisionLabel);
                }
                if (account.get().balance() < amount) {
                    return EmergencyPlan.rejected(
                            "INSUFFICIENT_FUNDS",
                            revisionLabel
                    );
                }
                if (repository.snapshot().totalSupply() < amount) {
                    return EmergencyPlan.rejected(
                            "INSUFFICIENT_FUNDS",
                            revisionLabel
                    );
                }
            } else {
                long newBalance;
                try {
                    newBalance = Math.addExact(balanceBefore, amount);
                } catch (ArithmeticException overflow) {
                    return EmergencyPlan.rejected("OVERFLOW", revisionLabel);
                }
                if (newBalance > repository.maxBalance()) {
                    return EmergencyPlan.rejected("OVERFLOW", revisionLabel);
                }
                long supplyBefore = repository.snapshot().totalSupply();
                long supplyAfter;
                try {
                    supplyAfter = Math.addExact(supplyBefore, amount);
                } catch (ArithmeticException overflow) {
                    return EmergencyPlan.rejected("OVERFLOW", revisionLabel);
                }
                if (supplyAfter < 0 || supplyAfter > repository.maxBalance()) {
                    return EmergencyPlan.rejected("OVERFLOW", revisionLabel);
                }
            }
            return EmergencyPlan.accepted(
                    summary(envelope, amount),
                    revisionLabel
            );
        } catch (IllegalArgumentException invalid) {
            return EmergencyPlan.rejected(
                    "INVALID_REQUEST",
                    storeLabel()
            );
        } catch (EconomyUnavailableException unavailable) {
            return EmergencyPlan.rejected(
                    unavailable.failureCode(),
                    storeLabel()
            );
        }
    }

    @Override
    public EmergencyMutationResult apply(
            EmergencyPlan plan,
            EmergencyMutationEnvelope envelope
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(envelope, "envelope");
        try {
            validateAction(envelope);
            long amount = parseAmount(envelope.parameters());
            SubjectId target = subjectId(envelope);
            long storeRevision = repository.storeRevision();
            String digest = envelopeDigest(envelope);
            if (ACTION_ISSUE.equals(envelope.actionId())) {
                repository.emergencyIssue(
                        target,
                        amount,
                        envelope.actionId(),
                        envelope.actionVersion(),
                        providerIdentity,
                        envelope.category(),
                        envelope.reason(),
                        envelope.attemptId(),
                        envelope.at(),
                        storeRevision,
                        digest
                );
            } else {
                repository.emergencyReclaim(
                        target,
                        amount,
                        envelope.actionId(),
                        envelope.actionVersion(),
                        providerIdentity,
                        envelope.category(),
                        envelope.reason(),
                        envelope.attemptId(),
                        envelope.at(),
                        storeRevision,
                        digest
                );
            }
            return EmergencyMutationResult.applied(
                    summary(envelope, amount),
                    plan.revisionLabel()
            );
        } catch (RuntimeException failure) {
            return EmergencyMutationResult.failed(
                    failureCode(failure),
                    plan.revisionLabel()
            );
        }
    }

    // ------------------------------------------------------------------
    // validation and helpers
    // ------------------------------------------------------------------

    private void validateAction(EmergencyMutationEnvelope envelope) {
        if (!"economy".equals(envelope.moduleId())) {
            throw new IllegalArgumentException("Module is not economy");
        }
        if (!ACTION_ISSUE.equals(envelope.actionId())
                && !ACTION_RECLAIM.equals(envelope.actionId())) {
            throw new IllegalArgumentException("Unknown economy emergency action");
        }
        if (!"1.0.0".equals(envelope.actionVersion())) {
            throw new IllegalArgumentException("Unsupported action version");
        }
        if (!EmergencyTargetType.PLAYER_UUID.name().equals(envelope.targetType())) {
            throw new IllegalArgumentException(
                    "Economy emergency targets are player UUIDs only"
            );
        }
        EmergencyCategory category;
        try {
            category = EmergencyCategory.valueOf(envelope.category());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Unknown emergency category");
        }
        if (!ALLOWED_CATEGORIES.contains(category)) {
            throw new IllegalArgumentException(
                    "Category is not allowed for economy emergency actions"
            );
        }
    }

    private static long parseAmount(Map<String, String> parameters) {
        String raw = parameters.get(PARAM_AMOUNT);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing amount parameter");
        }
        long amount;
        try {
            amount = Long.parseLong(raw);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Amount must be a long integer");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        return amount;
    }

    private static SubjectId subjectId(EmergencyMutationEnvelope envelope) {
        UUID uuid;
        try {
            uuid = UUID.fromString(envelope.targetId());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "Player target must be a canonical UUID"
            );
        }
        return SubjectId.of(uuid);
    }

    private String revisionLabel(SubjectId target, Optional<EconomyAccount> account) {
        return storeLabel()
                + "|economy/account:" + target + ":"
                + (account.isEmpty() ? "absent" : account.get().accountRevision());
    }

    private String storeLabel() {
        return "economy/store:" + repository.storeRevision();
    }

    private static String summary(EmergencyMutationEnvelope envelope, long amount) {
        return envelope.actionId()
                + " +" + amount
                + " to " + envelope.targetId()
                + " (" + envelope.category() + ")";
    }

    /**
     * Canonical request digest identical to the shared service's journal
     * digest (moduleId|actionId|actionVersion|targetType|targetId|category|
     * reason|sorted key=value pairs), hex-encoded for the receipt.
     */
    static String envelopeDigest(EmergencyMutationEnvelope envelope) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(envelope.moduleId()).append('|');
        canonical.append(envelope.actionId()).append('|');
        canonical.append(envelope.actionVersion()).append('|');
        canonical.append(envelope.targetType()).append('|');
        canonical.append(envelope.targetId()).append('|');
        canonical.append(envelope.category()).append('|');
        canonical.append(envelope.reason()).append('|');
        envelope.parameters().keySet().stream().sorted().forEach(key ->
                canonical.append(key).append('=')
                        .append(envelope.parameters().get(key)).append(';')
        );
        return EmergencyDigests.toHex(EmergencyDigests.sha256(
                canonical.toString().getBytes(StandardCharsets.UTF_8)
        ));
    }

    private static String failureCode(RuntimeException failure) {
        if (failure instanceof EconomyUnavailableException unavailable) {
            return unavailable.failureCode();
        }
        return "PROVIDER_FAILURE";
    }
}
