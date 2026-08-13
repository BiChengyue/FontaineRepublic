package com.fontainerepublic.server.emergency.service;

import com.fontainerepublic.server.emergency.api.ConfigureResult;
import com.fontainerepublic.server.emergency.api.ConfirmResult;
import com.fontainerepublic.server.emergency.api.EmergencyActionDescriptor;
import com.fontainerepublic.server.emergency.api.EmergencyActionProvider;
import com.fontainerepublic.server.emergency.api.EmergencyActionRegistry;
import com.fontainerepublic.server.emergency.api.EmergencyInspection;
import com.fontainerepublic.server.emergency.api.EmergencyMutationEnvelope;
import com.fontainerepublic.server.emergency.api.EmergencyMutationResult;
import com.fontainerepublic.server.emergency.api.EmergencyPlan;
import com.fontainerepublic.server.emergency.api.EmergencyReceiptProvider;
import com.fontainerepublic.server.emergency.api.EmergencyRequest;
import com.fontainerepublic.server.emergency.api.EmergencyService;
import com.fontainerepublic.server.emergency.api.EmergencyStatus;
import com.fontainerepublic.server.emergency.api.PreviewResult;
import com.fontainerepublic.server.emergency.api.ReconciliationResult;
import com.fontainerepublic.server.emergency.model.EmergencyActorSource;
import com.fontainerepublic.server.emergency.model.EmergencyActorType;
import com.fontainerepublic.server.emergency.model.EmergencyAttemptResult;
import com.fontainerepublic.server.emergency.model.EmergencyConfigPhase;
import com.fontainerepublic.server.emergency.model.EmergencyConfigResult;
import com.fontainerepublic.server.emergency.model.EmergencyConfigState;
import com.fontainerepublic.server.emergency.model.EmergencyDigests;
import com.fontainerepublic.server.emergency.model.EmergencyJournalKind;
import com.fontainerepublic.server.emergency.model.EmergencyJournalRecord;
import com.fontainerepublic.server.emergency.model.EmergencyReceiptWatermark;
import com.fontainerepublic.server.emergency.model.EmergencyReconciliationStatus;
import com.fontainerepublic.server.emergency.model.EmergencySourceClassification;
import com.fontainerepublic.server.emergency.model.EmergencyTokenBinding;
import com.fontainerepublic.server.emergency.model.EmergencyTokenBindingDigests;
import com.fontainerepublic.server.emergency.model.EmergencyTokenEntry;
import com.fontainerepublic.server.emergency.persistence.EmergencyRepository;
import com.fontainerepublic.server.emergency.persistence.EmergencyUnavailableException;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Runtime implementation of the shared emergency authority service
 * (FR-EMG-001-A §3 / §4 / §8 / §10; implementation task §3.1).
 *
 * <p>Execution is owner-thread-scoped through the single-writer
 * {@link EmergencyRepository}. Authority configuration is immutable during a
 * runtime: bootstrap (console only) establishes the first UUID; a controlled
 * change stages the next UUID; startup acceptance validates digest and
 * revision; drift fails closed (no player UUID authority) and the console may
 * recover. Tokens are server-runtime memory with ISSUED -> CLAIMED -> CONSUMED
 * semantics; the claim happens before final validation, and every terminal
 * outcome consumes the token.</p>
 *
 * <p>The shared journal is authoritative for attempts and configuration
 * events. Every preview/confirm first appends a PENDING attempt-intent record
 * through the durable gate, then a terminal record; a successful business
 * mutation is never claimed without its provider result. Failure closes
 * whenever authority, target, mutation, or audit integrity cannot be
 * established.</p>
 */
public final class DefaultEmergencyService implements EmergencyService {

    public static final String CODE_UNKNOWN_ACTION = "UNKNOWN_ACTION";
    public static final String CODE_ACTION_UNAVAILABLE = "ACTION_UNAVAILABLE";
    public static final String CODE_INVALID_INPUT = "INVALID_INPUT";
    public static final String CODE_REJECTED_SOURCE = "REJECTED_SOURCE";
    public static final String CODE_REJECTED_TOKEN = "REJECTED_TOKEN";
    public static final String CODE_REJECTED_REVISION = "REJECTED_REVISION";
    public static final String CODE_PROVIDER_FAILURE = "PROVIDER_FAILURE";
    public static final String CODE_COMMIT_FAILURE = "COMMIT_FAILURE";
    public static final String CODE_DRIFT = "DRIFT";
    public static final String CODE_UNAVAILABLE = "UNAVAILABLE";
    public static final String CODE_STOPPING = "STOPPING";
    public static final String CODE_CONFIG_REJECTED = "CONFIG_REJECTED";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final EmergencyRepository repository;
    private final EmergencyActionRegistry actionRegistry;
    private final EmergencyTokenTable tokenTable;
    private final LongSupplier clock;
    private final long epoch;
    private final Map<String, EmergencyReceiptProvider> receiptProviders;
    private final Thread ownerThread;
    private final EmergencyAuthorityConfigSource authorityConfig;

    private boolean stopping;
    private boolean driftDetected;
    private EmergencyConfigState config;

    public DefaultEmergencyService(
            EmergencyRepository repository,
            EmergencyActionRegistry actionRegistry,
            EmergencyTokenTable tokenTable,
            LongSupplier clock,
            long epoch,
            EmergencyAuthorityConfigSource authorityConfig,
            Map<String, EmergencyReceiptProvider> receiptProviders
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.actionRegistry = Objects.requireNonNull(actionRegistry, "actionRegistry");
        this.tokenTable = Objects.requireNonNull(tokenTable, "tokenTable");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (epoch <= 0) {
            throw new IllegalArgumentException("epoch must be positive");
        }
        this.epoch = epoch;
        this.authorityConfig = Objects.requireNonNull(authorityConfig, "authorityConfig");
        this.receiptProviders = new LinkedHashMap<>(
                Objects.requireNonNull(receiptProviders, "receiptProviders")
        );
        this.ownerThread = Thread.currentThread();
        this.config = repository.config();
    }

    // ------------------------------------------------------------------
    // preview / confirm
    // ------------------------------------------------------------------

    @Override
    public PreviewResult preview(EmergencyRequest request, EmergencyActorSource source) {
        requireOwnerThread();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(source, "source");

        if (stopping) {
            return PreviewResult.rejected(CODE_STOPPING, "The server is stopping.");
        }
        // 1. Actor verification (server-authority boundary).
        if (!isAuthorizedActor(source)) {
            return PreviewResult.rejected(
                    CODE_REJECTED_SOURCE,
                    "Only the configured Hydro Archon UUID or the real local "
                            + "server console may preview emergency actions."
            );
        }
        long now = now();
        long attemptId = repository.nextRecordId();
        byte[] requestDigest = requestDigest(request);
        // 2. Durable attempt-intent before any further resolution.
        if (!appendAttempt(source, attemptId,
                EmergencyAttemptResult.PENDING, requestDigest, now)) {
            return PreviewResult.rejected(
                    CODE_UNAVAILABLE, "The emergency journal could not record the attempt."
            );
        }
        // 3. Action resolution from the frozen registry.
        Optional<EmergencyActionDescriptor> descriptor = actionRegistry.find(
                request.moduleId(), request.actionId(), request.actionVersion()
        );
        if (descriptor.isEmpty()) {
            appendTerminal(source, attemptId,
                    EmergencyAttemptResult.REJECTED_UNKNOWN_ACTION,
                    requestDigest, now);
            return PreviewResult.rejected(
                    CODE_UNKNOWN_ACTION,
                    "Unknown emergency action " + request.moduleId() + "/"
                            + request.actionId() + " v" + request.actionVersion() + "."
            );
        }
        EmergencyActionDescriptor action = descriptor.get();
        // 4. Category / target / parameter schema validation.
        if (!action.allowedCategories().contains(request.category())) {
            appendTerminal(source, attemptId,
                    EmergencyAttemptResult.REJECTED_PARAMETERS, requestDigest, now);
            return PreviewResult.rejected(
                    CODE_INVALID_INPUT,
                    "Category " + request.category()
                            + " is not allowed for this action."
            );
        }
        if (action.targetType() != request.targetType()) {
            appendTerminal(source, attemptId,
                    EmergencyAttemptResult.REJECTED_PARAMETERS, requestDigest, now);
            return PreviewResult.rejected(
                    CODE_INVALID_INPUT,
                    "Target type " + request.targetType()
                            + " is not supported by this action."
            );
        }
        for (String key : request.parameterKeys()) {
            if (!action.parameterKeys().contains(key)) {
                appendTerminal(source, attemptId,
                        EmergencyAttemptResult.REJECTED_PARAMETERS, requestDigest, now);
                return PreviewResult.rejected(
                        CODE_INVALID_INPUT,
                        "Unknown parameter '" + key + "' for this action."
                );
            }
        }
        // 5. Runtime provider resolution (fresh at every invocation).
        Optional<EmergencyActionProvider> provider = action.providerResolver().resolve();
        if (provider.isEmpty()) {
            appendTerminal(source, attemptId,
                    EmergencyAttemptResult.REJECTED_ACTION_UNAVAILABLE, requestDigest, now);
            return PreviewResult.rejected(
                    CODE_ACTION_UNAVAILABLE,
                    "The business provider for this action is not ACTIVE."
            );
        }
        if (!provider.get().providerIdentity().equals(action.providerIdentity())
                || !provider.get().providerVersion().equals(action.providerVersion())) {
            appendTerminal(source, attemptId,
                    EmergencyAttemptResult.REJECTED_ACTION_UNAVAILABLE, requestDigest, now);
            return PreviewResult.rejected(
                    CODE_ACTION_UNAVAILABLE,
                    "The resolved provider does not match the frozen descriptor."
            );
        }
        // 6. Typed side-effect-free preview from the business provider.
        EmergencyPlan plan = provider.get().preview(envelope(
                request, action, attemptId, now
        ));
        if (!plan.accepted()) {
            appendTerminal(source, attemptId,
                    EmergencyAttemptResult.REJECTED_PARAMETERS, requestDigest, now);
            return PreviewResult.rejected(
                    plan.failureCode(),
                    "The business provider rejected the request."
            );
        }
        // 7. Bind token: actor, action, target, category, reason, parameters,
        //    revision.
        byte[] revisionDigest = EmergencyTokenBindingDigests.revisionDigest(
                List.of(plan.revisionLabel())
        );
        EmergencyTokenBinding binding = source.bind(
                request.moduleId(),
                request.actionId(),
                request.actionVersion(),
                request.targetType(),
                request.targetId(),
                request.category(),
                request.reason(),
                request.parameters(),
                revisionDigest,
                action.offlineSafe()
        );
        EmergencyTokenTable.TokenIssue issue = tokenTable.issue(binding, attemptId, now);
        return PreviewResult.accepted(
                issue.token(), issue.expiresAt(), attemptId, plan.summary()
        );
    }

    @Override
    public ConfirmResult confirm(String token, EmergencyActorSource source) {
        requireOwnerThread();
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(source, "source");

        if (stopping) {
            return ConfirmResult.failure(0L, CODE_STOPPING, "The server is stopping.");
        }
        // 1. Atomic claim before any validation or business call.
        EmergencyTokenEntry claimed = tokenTable.claim(token, now());
        if (claimed == null) {
            return ConfirmResult.failure(
                    0L, CODE_REJECTED_TOKEN,
                    "Unknown, expired, or already-used confirmation token."
            );
        }
        long attemptId = claimed.attemptId();
        EmergencyTokenBinding binding = claimed.binding();
        byte[] requestDigest = requestDigestOf(binding);
        long now = now();
        // 2. Revalidate trusted actor/source and epoch.
        if (!matchesActor(binding, source)) {
            tokenTable.consume(claimed);
            appendTerminal(source, attemptId,
                    EmergencyAttemptResult.REJECTED_SOURCE, requestDigest, now);
            return ConfirmResult.failure(
                    attemptId, CODE_REJECTED_SOURCE,
                    "The confirming source does not match the preview actor."
            );
        }
        // 3. Revalidate action availability and provider identity/version.
        Optional<EmergencyActionDescriptor> descriptor = actionRegistry.find(
                binding.moduleId(), binding.actionId(), binding.actionVersion()
        );
        if (descriptor.isEmpty()) {
            tokenTable.consume(claimed);
            appendTerminal(source, attemptId,
                    EmergencyAttemptResult.REJECTED_UNKNOWN_ACTION, requestDigest, now);
            return ConfirmResult.failure(
                    attemptId, CODE_UNKNOWN_ACTION,
                    "The action is no longer registered."
            );
        }
        EmergencyActionDescriptor action = descriptor.get();
        Optional<EmergencyActionProvider> provider = action.providerResolver().resolve();
        if (provider.isEmpty()
                || !provider.get().providerIdentity().equals(action.providerIdentity())
                || !provider.get().providerVersion().equals(action.providerVersion())) {
            tokenTable.consume(claimed);
            appendTerminal(source, attemptId,
                    EmergencyAttemptResult.REJECTED_ACTION_UNAVAILABLE, requestDigest, now);
            return ConfirmResult.failure(
                    attemptId, CODE_ACTION_UNAVAILABLE,
                    "The business provider is not ACTIVE or changed identity."
            );
        }
        EmergencyMutationEnvelope envelope = envelope(binding, action, attemptId, now);
        // 4. Revalidate the business revision (changed revision requires a
        //    new preview).
        EmergencyPlan revalidated = provider.get().preview(envelope);
        if (!revalidated.accepted()) {
            tokenTable.consume(claimed);
            appendTerminal(source, attemptId,
                    EmergencyAttemptResult.REJECTED_REVISION, requestDigest, now);
            return ConfirmResult.failure(
                    attemptId, CODE_REJECTED_REVISION,
                    "The business revision changed; a new preview is required."
            );
        }
        byte[] currentRevisionDigest = EmergencyTokenBindingDigests.revisionDigest(
                List.of(revalidated.revisionLabel())
        );
        if (!Arrays.equals(currentRevisionDigest, binding.revisionDigest())) {
            tokenTable.consume(claimed);
            appendTerminal(source, attemptId,
                    EmergencyAttemptResult.REJECTED_REVISION, requestDigest, now);
            return ConfirmResult.failure(
                    attemptId, CODE_REJECTED_REVISION,
                    "The business revision changed; a new preview is required."
            );
        }
        // 5. Business mutation through the typed provider.
        EmergencyMutationResult mutation;
        try {
            mutation = provider.get().apply(revalidated, envelope);
        } catch (RuntimeException failure) {
            LOGGER.error("[Emergency] Provider mutation failed for attempt {}",
                    attemptId, failure);
            tokenTable.consume(claimed);
            appendTerminal(source, attemptId,
                    EmergencyAttemptResult.PROVIDER_FAILURE, requestDigest, now);
            return ConfirmResult.failure(
                    attemptId, CODE_PROVIDER_FAILURE,
                    "The business provider failed to apply the mutation."
            );
        }
        if (!mutation.applied()) {
            tokenTable.consume(claimed);
            appendTerminal(source, attemptId,
                    EmergencyAttemptResult.PROVIDER_FAILURE, requestDigest, now);
            return ConfirmResult.failure(
                    attemptId, CODE_PROVIDER_FAILURE, mutation.summary()
            );
        }
        tokenTable.consume(claimed);
        appendTerminal(source, attemptId,
                EmergencyAttemptResult.SUCCESS, requestDigest, now);
        return ConfirmResult.success(attemptId, mutation.summary());
    }

    // ------------------------------------------------------------------
    // inspect / status
    // ------------------------------------------------------------------

    @Override
    public Optional<EmergencyInspection> inspect(long attemptId) {
        requireOwnerThread();
        if (attemptId <= 0) {
            return Optional.empty();
        }
        return repository.findRecord(attemptId).map(record -> new EmergencyInspection(
                record.recordId(),
                record.at(),
                record.actorType().name(),
                record.kind() == EmergencyJournalKind.ATTEMPT
                        ? "emergency.attempt" : "emergency.config-event",
                EmergencyJournalRecord.resultName(
                        record.attemptResult(), record.configResult()
                ),
                record.attemptId(),
                record.requestDigest() == null
                        ? "" : EmergencyDigests.toHex(record.requestDigest())
        ));
    }

    @Override
    public EmergencyStatus status() {
        requireOwnerThread();
        Map<String, String> watermarks = new LinkedHashMap<>();
        repository.snapshot().receiptWatermarks().forEach((providerId, watermark) ->
                watermarks.put(providerId, watermark.status().name())
        );
        return new EmergencyStatus(
                config.phase(),
                config.activeUuidDigest() == null
                        ? null : EmergencyDigests.toHex(config.activeUuidDigest()),
                config.configRevision(),
                driftDetected,
                repository.recordCount(),
                config.journalHeadDigest() == null
                        ? null : EmergencyDigests.toHex(config.journalHeadDigest()),
                watermarks
        );
    }

    // ------------------------------------------------------------------
    // authority configuration lifecycle
    // ------------------------------------------------------------------

    @Override
    public ConfigureResult bootstrapAuthority(
            UUID hydroArchonUuid,
            String reason,
            EmergencyActorSource source
    ) {
        requireOwnerThread();
        if (stopping) {
            return ConfigureResult.rejected(CODE_STOPPING, "The server is stopping.");
        }
        if (hydroArchonUuid == null || !isCanonical(hydroArchonUuid)
                || reason == null || reason.isBlank()
                || reason.length() > 200) {
            return ConfigureResult.rejected(
                    CODE_CONFIG_REJECTED, "Invalid bootstrap input."
            );
        }
        if (!isLocalConsole(source)) {
            return ConfigureResult.rejected(
                    CODE_REJECTED_SOURCE,
                    "Only the real local server console may bootstrap authority."
            );
        }
        EmergencyConfigState current = config;
        if (current.phase() == EmergencyConfigPhase.ACTIVE && !driftDetected) {
            return ConfigureResult.rejected(
                    CODE_CONFIG_REJECTED,
                    "Authority is already ACTIVE; use the staged-change path."
            );
        }
        long now = now();
        EmergencyConfigState next = EmergencyConfigState.active(
                EmergencyDigests.uuidDigest(hydroArchonUuid),
                now,
                current.configRevision() + 1L,
                repository.tailDigest()
        );
        if (!commitConfigState(next, source, EmergencyConfigResult.CONFIG_BOOTSTRAPPED,
                now)) {
            return ConfigureResult.rejected(
                    CODE_COMMIT_FAILURE, "The authority could not be committed."
            );
        }
        driftDetected = false;
        LOGGER.info("[Emergency] Authority bootstrapped at config revision {}",
                next.configRevision());
        return ConfigureResult.accepted(
                "Authority bootstrapped for the next runtime.",
                next.configRevision()
        );
    }

    @Override
    public ConfigureResult stageAuthority(
            UUID nextHydroArchonUuid,
            String reason,
            EmergencyActorSource source
    ) {
        requireOwnerThread();
        if (stopping) {
            return ConfigureResult.rejected(CODE_STOPPING, "The server is stopping.");
        }
        if (nextHydroArchonUuid == null || !isCanonical(nextHydroArchonUuid)
                || reason == null || reason.isBlank()
                || reason.length() > 200) {
            return ConfigureResult.rejected(
                    CODE_CONFIG_REJECTED, "Invalid staged-change input."
            );
        }
        if (!isLocalConsole(source) && !isAuthorizedActor(source)) {
            return ConfigureResult.rejected(
                    CODE_REJECTED_SOURCE,
                    "Only the configured Hydro Archon or the real local "
                            + "console may stage a change."
            );
        }
        EmergencyConfigState current = config;
        if (current.phase() != EmergencyConfigPhase.ACTIVE || driftDetected) {
            return ConfigureResult.rejected(
                    CODE_DRIFT,
                    "Authority is not ACTIVE; bootstrap or recover first."
            );
        }
        long now = now();
        EmergencyConfigState staged = EmergencyConfigState.staged(
                EmergencyDigests.uuidDigest(nextHydroArchonUuid),
                now,
                current.configRevision() + 1L,
                repository.tailDigest()
        );
        if (!commitConfigState(staged, source,
                EmergencyConfigResult.CONFIG_STAGED, now)) {
            return ConfigureResult.rejected(
                    CODE_COMMIT_FAILURE, "The staged change could not be committed."
            );
        }
        LOGGER.info("[Emergency] Authority change staged at config revision {}",
                staged.configRevision());
        return ConfigureResult.accepted(
                "Change staged; it will be accepted at the next server start.",
                staged.configRevision()
        );
    }

    @Override
    public boolean acceptStagedAtStartup() {
        requireOwnerThread();
        EmergencyConfigState current = config;
        if (current.phase() != EmergencyConfigPhase.STAGED) {
            return false;
        }
        byte[] stagedDigest = current.stagedUuidDigest();
        Optional<byte[]> candidate = authorityConfig.candidateUuidDigest();
        if (candidate.isEmpty()
                || !Arrays.equals(candidate.get(), stagedDigest)) {
            driftDetected = true;
            LOGGER.error(
                    "[Emergency] Staged authority digest mismatch at startup; "
                            + "failing closed (no player UUID authority)."
            );
            return false;
        }
        long now = now();
        EmergencyConfigState next = EmergencyConfigState.active(
                stagedDigest,
                now,
                current.stagedRevision(),
                repository.tailDigest()
        );
        if (!commitConfigState(next, consoleActorSource(),
                EmergencyConfigResult.CONFIG_ACCEPTED, now)) {
            driftDetected = true;
            return false;
        }
        driftDetected = false;
        LOGGER.info("[Emergency] Staged authority accepted at startup (revision {})",
                next.configRevision());
        return true;
    }

    @Override
    public ConfigureResult recoverAuthority(
            UUID hydroArchonUuid,
            String reason,
            EmergencyActorSource source
    ) {
        requireOwnerThread();
        if (stopping) {
            return ConfigureResult.rejected(CODE_STOPPING, "The server is stopping.");
        }
        if (hydroArchonUuid == null || !isCanonical(hydroArchonUuid)
                || reason == null || reason.isBlank()
                || reason.length() > 200) {
            return ConfigureResult.rejected(
                    CODE_CONFIG_REJECTED, "Invalid recovery input."
            );
        }
        if (!isLocalConsole(source)) {
            return ConfigureResult.rejected(
                    CODE_REJECTED_SOURCE,
                    "Only the real local server console may recover authority."
            );
        }
        EmergencyConfigState current = config;
        long now = now();
        EmergencyConfigState next = EmergencyConfigState.active(
                EmergencyDigests.uuidDigest(hydroArchonUuid),
                now,
                current.configRevision() + 1L,
                repository.tailDigest()
        );
        if (!commitConfigState(next, source,
                EmergencyConfigResult.CONFIG_RECOVERED, now)) {
            return ConfigureResult.rejected(
                    CODE_COMMIT_FAILURE, "The recovery could not be committed."
            );
        }
        driftDetected = false;
        LOGGER.info("[Emergency] Authority recovered at config revision {}",
                next.configRevision());
        return ConfigureResult.accepted(
                "Authority recovered; the configured UUID is active again.",
                next.configRevision()
        );
    }

    @Override
    public ReconciliationResult reconcile(String providerId) {
        requireOwnerThread();
        Objects.requireNonNull(providerId, "providerId");
        EmergencyReceiptProvider provider = receiptProviders.get(providerId);
        if (provider == null) {
            return new ReconciliationResult(
                    providerId, false, 0L, "", "UNKNOWN_PROVIDER"
            );
        }
        try {
            EmergencyReceiptProvider.EmergencyReceiptWatermarkResult watermark =
                    provider.watermark();
            long highest = watermark.highestSequence();
            EmergencyReceiptWatermark current = repository.watermark(providerId)
                    .orElseGet(() -> EmergencyReceiptWatermark.empty(
                            providerId, watermark.receiptSchema()
                    ));
            long after = Math.max(0L, current.highestSequence());
            long lastImported = after;
            while (true) {
                EmergencyReceiptProvider.EmergencyReceiptPage page =
                        provider.readPage(after, 100);
                for (EmergencyReceiptProvider.EmergencyReceiptEnvelope envelope :
                        page.envelopes()) {
                    if (envelope.sequence() <= after) {
                        return new ReconciliationResult(
                                providerId, false, after, "", "OUT_OF_ORDER"
                        );
                    }
                    lastImported = envelope.sequence();
                }
                if (!page.hasMore() || page.envelopes().isEmpty()) {
                    break;
                }
                after = lastImported;
            }
            boolean complete = lastImported >= highest;
            EmergencyReconciliationStatus status = complete
                    ? EmergencyReconciliationStatus.COMPLETE_THROUGH
                    : EmergencyReconciliationStatus.INCOMPLETE;
            EmergencyReceiptWatermark next = new EmergencyReceiptWatermark(
                    providerId,
                    watermark.receiptSchema(),
                    lastImported,
                    watermark.segmentDigest(),
                    status
            );
            repository.updateWatermark(next);
            return new ReconciliationResult(
                    providerId, complete, lastImported,
                    watermark.segmentDigest(), ""
            );
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "[Emergency] Reconciliation of provider {} failed: {}",
                    providerId, failure.getMessage()
            );
            EmergencyReceiptWatermark incomplete =
                    repository.watermark(providerId).orElseGet(() ->
                            EmergencyReceiptWatermark.empty(providerId, "unknown")
                    );
            return new ReconciliationResult(
                    providerId, false, incomplete.highestSequence(),
                    incomplete.segmentDigest(), "RECONCILIATION_FAILED"
            );
        }
    }

    @Override
    public void invalidateTokens() {
        requireOwnerThread();
        tokenTable.invalidateAll();
        stopping = true;
    }

    /** Binds a read-only receipt provider for reconciliation (runtime). */
    public void bindReceiptProvider(EmergencyReceiptProvider provider) {
        requireOwnerThread();
        Objects.requireNonNull(provider, "provider");
        receiptProviders.put(provider.providerId(), provider);
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private boolean isAuthorizedActor(EmergencyActorSource source) {
        if (source.actorType() == EmergencyActorType.SERVER_CONSOLE) {
            return isLocalConsole(source);
        }
        if (config.phase() != EmergencyConfigPhase.ACTIVE || driftDetected) {
            return false;
        }
        byte[] digest = EmergencyDigests.uuidDigest(source.actorUuid());
        return Arrays.equals(digest, config.activeUuidDigest());
    }

    private boolean matchesActor(EmergencyTokenBinding binding,
                                 EmergencyActorSource source) {
        if (binding.actorType() != source.actorType()) {
            return false;
        }
        if (binding.actorType() == EmergencyActorType.HYDRO_ARCHON) {
            return binding.actorUuid().equals(source.actorUuid())
                    && isAuthorizedActor(source);
        }
        return isLocalConsole(source);
    }

    private boolean isLocalConsole(EmergencyActorSource source) {
        return EmergencyConsoleClassifier.isLocalConsole(source.classification());
    }

    private boolean appendAttempt(
            EmergencyActorSource source,
            long attemptId,
            EmergencyAttemptResult result,
            byte[] requestDigest,
            long now
    ) {
        long recordId = repository.nextRecordId();
        EmergencyJournalRecord record = EmergencyJournalRecord.attempt(
                recordId,
                now,
                source.actorType(),
                source.actorUuid() == null ? null : EmergencyDigests.uuidDigest(source.actorUuid()),
                source.classification(),
                attemptId,
                result,
                requestDigest,
                repository.tailDigest()
        );
        try {
            return repository.appendRecord(record);
        } catch (EmergencyUnavailableException failure) {
            LOGGER.error("[Emergency] Journal append failed: {}", failure.getMessage());
            return false;
        }
    }

    private void appendTerminal(
            EmergencyActorSource source,
            long attemptId,
            EmergencyAttemptResult result,
            byte[] requestDigest,
            long now
    ) {
        if (attemptId <= 0) {
            return;
        }
        long recordId = repository.nextRecordId();
        EmergencyJournalRecord record = EmergencyJournalRecord.attempt(
                recordId,
                now,
                source.actorType(),
                source.actorUuid() == null ? null : EmergencyDigests.uuidDigest(source.actorUuid()),
                source.classification(),
                attemptId,
                result,
                requestDigest,
                repository.tailDigest()
        );
        try {
            repository.appendRecord(record);
        } catch (EmergencyUnavailableException failure) {
            LOGGER.error(
                    "[Emergency] Terminal journal record {} failed: {}",
                    attemptId, failure.getMessage()
            );
        }
    }

    private boolean commitConfigState(
            EmergencyConfigState next,
            EmergencyActorSource source,
            EmergencyConfigResult result,
            long now
    ) {
        long recordId = repository.nextRecordId();
        EmergencyJournalRecord record = EmergencyJournalRecord.configEvent(
                recordId,
                now,
                source.actorType(),
                source.actorUuid() == null ? null : EmergencyDigests.uuidDigest(source.actorUuid()),
                source.classification(),
                result,
                next.configRevision(),
                repository.tailDigest()
        );
        try {
            boolean committed = repository.commitConfig(next, record);
            if (committed) {
                this.config = next;
            }
            return committed;
        } catch (EmergencyUnavailableException failure) {
            LOGGER.error("[Emergency] Config commit failed: {}", failure.getMessage());
            return false;
        }
    }

    private EmergencyMutationEnvelope envelope(
            EmergencyRequest request,
            EmergencyActionDescriptor action,
            long attemptId,
            long now
    ) {
        return new EmergencyMutationEnvelope(
                request.moduleId(),
                request.actionId(),
                request.actionVersion(),
                request.targetType().name(),
                request.targetId(),
                request.category().name(),
                request.reason(),
                request.parameters(),
                attemptId,
                now
        );
    }

    private EmergencyMutationEnvelope envelope(
            EmergencyTokenBinding binding,
            EmergencyActionDescriptor action,
            long attemptId,
            long now
    ) {
        return new EmergencyMutationEnvelope(
                binding.moduleId(),
                binding.actionId(),
                binding.actionVersion(),
                binding.targetType().name(),
                binding.targetId(),
                binding.category().name(),
                binding.reason(),
                binding.parameters(),
                attemptId,
                now
        );
    }

    private byte[] requestDigest(EmergencyRequest request) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(request.moduleId()).append('|');
        canonical.append(request.actionId()).append('|');
        canonical.append(request.actionVersion()).append('|');
        canonical.append(request.targetType().name()).append('|');
        canonical.append(request.targetId()).append('|');
        canonical.append(request.category().name()).append('|');
        canonical.append(request.reason()).append('|');
        request.parameterKeys().stream().sorted().forEach(key ->
                canonical.append(key).append('=').append(request.parameters().get(key))
                        .append(';')
        );
        return EmergencyDigests.sha256(
                canonical.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    private byte[] requestDigestOf(EmergencyTokenBinding binding) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(binding.moduleId()).append('|');
        canonical.append(binding.actionId()).append('|');
        canonical.append(binding.actionVersion()).append('|');
        canonical.append(binding.targetType().name()).append('|');
        canonical.append(binding.targetId()).append('|');
        canonical.append(binding.category().name()).append('|');
        canonical.append(binding.reason()).append('|');
        binding.parameters().keySet().stream().sorted().forEach(key ->
                canonical.append(key).append('=').append(binding.parameters().get(key))
                        .append(';')
        );
        return EmergencyDigests.sha256(
                canonical.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    private EmergencyActorSource consoleActorSource() {
        return new EmergencyActorSource(
                EmergencyActorType.SERVER_CONSOLE,
                null,
                EmergencySourceClassification.LOCAL_CONSOLE
        );
    }

    private long now() {
        long value = clock.getAsLong();
        if (value < 0) {
            throw new IllegalStateException("clock returned a negative timestamp");
        }
        return value;
    }

    private static boolean isCanonical(UUID uuid) {
        return uuid.toString().equals(uuid.toString().toLowerCase(java.util.Locale.ROOT));
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "DefaultEmergencyService may only be accessed from its "
                            + "owning server thread"
            );
        }
    }
}
