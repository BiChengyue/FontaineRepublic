package com.fontainerepublic.server.institutionaccess.service;

import com.fontainerepublic.server.institutionaccess.api.OnSiteContext;
import com.fontainerepublic.server.institutionaccess.model.FacilityId;
import com.fontainerepublic.server.institutionaccess.model.TerminalId;
import com.fontainerepublic.server.institutionaccess.model.WorkflowKind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded server-runtime registry of on-site contexts (FR-INST-001-A §7.4,
 * FR-INST-002-A §4).
 *
 * <p>The registry is server-run scoped, short-lived, and cleared on shutdown;
 * it is never a second authoritative database and never a static global
 * player cache. At most one context per player per workflow is active —
 * issuing a new one invalidates the previous one of the same workflow
 * (returning never restores a context). A hard global cap bounds memory.
 * Consumption and invalidation are tracked here; the context records
 * themselves are immutable.</p>
 */
final class OnSiteContextRegistry {

    private final int maxTotalContexts;
    private final Map<UUID, EnumMap<WorkflowKind, Entry>> byPlayer = new HashMap<>();
    private final Map<UUID, Entry> byId = new LinkedHashMap<>();
    private int total;

    OnSiteContextRegistry(int maxTotalContexts) {
        if (maxTotalContexts <= 0) {
            throw new IllegalArgumentException("maxTotalContexts must be positive");
        }
        this.maxTotalContexts = maxTotalContexts;
    }

    enum Status {
        ACTIVE,
        CONSUMED,
        INVALIDATED
    }

    static final class Entry {
        final OnSiteContext context;
        Status status;
        long lastActivityAt;

        Entry(OnSiteContext context, long now) {
            this.context = context;
            this.status = Status.ACTIVE;
            this.lastActivityAt = now;
        }
    }

    /**
     * Issues a context, invalidating any previous context of the same player
     * and workflow. Fails closed when the global cap would be exceeded.
     * Tracking entries of dead contexts are purged so the registry stays
     * bounded (FR-INST-001-A §7.4).
     */
    OnSiteContext issue(OnSiteContext context, long now) {
        Objects.requireNonNull(context, "context");
        if (total >= maxTotalContexts) {
            throw new com.fontainerepublic.server.institutionaccess.persistence
                    .InstitutionAccessUnavailableException(
                    com.fontainerepublic.server.institutionaccess.persistence
                            .InstitutionAccessUnavailableException.CODE_CAPACITY_EXCEEDED,
                    "Active on-site contexts would exceed the budget of "
                            + maxTotalContexts
            );
        }
        EnumMap<WorkflowKind, Entry> byWorkflow = byPlayer.computeIfAbsent(
                context.playerId(), ignored -> new EnumMap<>(WorkflowKind.class)
        );
        Entry previous = byWorkflow.get(context.workflowKind());
        if (previous != null) {
            previous.status = Status.INVALIDATED;
            total--;
        }
        Entry entry = new Entry(context, now);
        byWorkflow.put(context.workflowKind(), entry);
        byId.put(context.contextId(), entry);
        total++;
        maybePurge();
        return context;
    }

    /** Drops tracking of dead contexts when the id index grows too large. */
    private void maybePurge() {
        if (byId.size() < maxTotalContexts * 4L) {
            return;
        }
        byId.values().removeIf(entry -> entry.status != Status.ACTIVE);
    }

    /** The active context of a player for a workflow, if any. */
    Optional<OnSiteContext> findActive(UUID playerId, WorkflowKind workflowKind) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(workflowKind, "workflowKind");
        Entry entry = byPlayer.getOrDefault(
                playerId, new EnumMap<>(WorkflowKind.class)
        ).get(workflowKind);
        if (entry == null || entry.status != Status.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(entry.context);
    }

    /** All players that currently hold at least one active context. */
    Set<UUID> playersWithActiveContexts() {
        java.util.LinkedHashSet<UUID> players = new java.util.LinkedHashSet<>();
        byPlayer.forEach((playerId, byWorkflow) -> {
            boolean active = byWorkflow.values().stream()
                    .anyMatch(entry -> entry.status == Status.ACTIVE);
            if (active) {
                players.add(playerId);
            }
        });
        return players;
    }

    /** Every active context of a player (across workflows). */
    List<OnSiteContext> activeContextsOf(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        EnumMap<WorkflowKind, Entry> byWorkflow = byPlayer.get(playerId);
        if (byWorkflow == null) {
            return List.of();
        }
        ArrayList<OnSiteContext> active = new ArrayList<>();
        byWorkflow.values().forEach(entry -> {
            if (entry.status == Status.ACTIVE) {
                active.add(entry.context);
            }
        });
        return List.copyOf(active);
    }

    /** Whether the given context id is still ACTIVE in the registry. */
    boolean isActive(UUID contextId) {
        Entry entry = byId.get(Objects.requireNonNull(contextId, "contextId"));
        return entry != null && entry.status == Status.ACTIVE;
    }

    /** The tracked status of a context id (empty when unknown). */
    Optional<Status> statusOf(UUID contextId) {
        Entry entry = byId.get(Objects.requireNonNull(contextId, "contextId"));
        return entry == null ? Optional.empty() : Optional.of(entry.status);
    }

    /** Marks a context consumed (single-use workflows). Idempotent. */
    void consume(UUID contextId) {
        Entry entry = byId.get(Objects.requireNonNull(contextId, "contextId"));
        if (entry != null && entry.status == Status.ACTIVE) {
            entry.status = Status.CONSUMED;
            total--;
        }
    }

    /** Marks a context invalidated. Idempotent. */
    void invalidate(UUID contextId) {
        Entry entry = byId.get(Objects.requireNonNull(contextId, "contextId"));
        if (entry != null && entry.status == Status.ACTIVE) {
            entry.status = Status.INVALIDATED;
            total--;
        }
    }

    /** Invalidates every context of a player. */
    void invalidateAll(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        EnumMap<WorkflowKind, Entry> byWorkflow = byPlayer.get(playerId);
        if (byWorkflow == null) {
            return;
        }
        byWorkflow.values().forEach(entry -> {
            if (entry.status == Status.ACTIVE) {
                entry.status = Status.INVALIDATED;
                total--;
            }
        });
    }

    /** Invalidates every context anchored to a facility. */
    void invalidateByFacility(FacilityId facilityId) {
        Objects.requireNonNull(facilityId, "facilityId");
        byId.values().forEach(entry -> {
            if (entry.status == Status.ACTIVE
                    && entry.context.facilityId().equals(facilityId)) {
                entry.status = Status.INVALIDATED;
                total--;
            }
        });
    }

    /** Invalidates every context anchored to a terminal. */
    void invalidateByTerminal(TerminalId terminalId) {
        Objects.requireNonNull(terminalId, "terminalId");
        byId.values().forEach(entry -> {
            if (entry.status == Status.ACTIVE
                    && entry.context.terminalId().equals(terminalId)) {
                entry.status = Status.INVALIDATED;
                total--;
            }
        });
    }

    /** Refreshes the activity clock of a context (official idle refresh). */
    void refreshActivity(UUID contextId, long now) {
        Entry entry = byId.get(Objects.requireNonNull(contextId, "contextId"));
        if (entry != null && entry.status == Status.ACTIVE) {
            entry.lastActivityAt = now;
        }
    }

    /** Last activity time of a context (official idle evaluation). */
    Optional<Long> lastActivityOf(UUID contextId) {
        Entry entry = byId.get(Objects.requireNonNull(contextId, "contextId"));
        return entry == null ? Optional.empty() : Optional.of(entry.lastActivityAt);
    }

    /** Clears every context (server shutdown). */
    void clear() {
        byPlayer.clear();
        byId.clear();
        total = 0;
    }

    int size() {
        return total;
    }
}
