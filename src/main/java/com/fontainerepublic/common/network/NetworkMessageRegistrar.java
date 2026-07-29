package com.fontainerepublic.common.network;

import net.minecraftforge.network.NetworkDirection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Deterministic, single-use registration ledger.
 */
public final class NetworkMessageRegistrar implements NetworkMessageRegistration {
    private final RegistrationSink registrationSink;
    private final TreeMap<Integer, NetworkMessageSpec<?>> specificationsById =
            new TreeMap<>();
    private final Map<Class<?>, Integer> idsByClass = new HashMap<>();

    private boolean frozen;

    NetworkMessageRegistrar(RegistrationSink registrationSink) {
        this.registrationSink = Objects.requireNonNull(registrationSink, "registrationSink");
    }

    @Override
    public synchronized <MSG> void register(NetworkMessageSpec<MSG> specification) {
        Objects.requireNonNull(specification, "specification");
        String identity = identity(specification);

        if (frozen) {
            throw new NetworkRegistrationException(
                    "Registration is frozen; rejected " + identity
            );
        }
        if (specification.id() < 0) {
            throw new NetworkRegistrationException(
                    "Negative message ID in " + identity
            );
        }
        if (specification.direction() == null) {
            throw new NetworkRegistrationException(
                    "Missing direction in " + identity
            );
        }
        if (specification.direction() != NetworkDirection.PLAY_TO_SERVER
                && specification.direction() != NetworkDirection.PLAY_TO_CLIENT) {
            throw new NetworkRegistrationException(
                    "Unsupported direction " + specification.direction() + " in " + identity
            );
        }
        if (specification.direction() == NetworkDirection.PLAY_TO_SERVER
                && specification.rateLimitPolicy().isEmpty()) {
            throw new NetworkRegistrationException(
                    "C2S message requires an explicit rate policy: " + identity
            );
        }
        if (specification.direction() == NetworkDirection.PLAY_TO_CLIENT
                && specification.rateLimitPolicy().isPresent()) {
            throw new NetworkRegistrationException(
                    "S2C message cannot declare a C2S rate policy: " + identity
            );
        }

        NetworkMessageSpec<?> duplicateId = specificationsById.get(specification.id());
        if (duplicateId != null) {
            throw new NetworkRegistrationException(
                    "Duplicate message ID " + specification.id()
                            + " for " + specification.messageClass().getName()
                            + "; already assigned to " + duplicateId.messageClass().getName()
            );
        }
        Integer duplicateClassId = idsByClass.get(specification.messageClass());
        if (duplicateClassId != null) {
            throw new NetworkRegistrationException(
                    "Duplicate message class " + specification.messageClass().getName()
                            + " for ID " + specification.id()
                            + "; already assigned to ID " + duplicateClassId
            );
        }

        specificationsById.put(specification.id(), specification);
        idsByClass.put(specification.messageClass(), specification.id());
    }

    public synchronized int freeze() {
        if (frozen) {
            throw new NetworkRegistrationException(
                    "Message registration is already frozen"
            );
        }
        frozen = true;
        for (NetworkMessageSpec<?> specification : specificationsById.values()) {
            try {
                registrationSink.register(specification);
            } catch (RuntimeException failure) {
                throw new NetworkRegistrationException(
                        "Failed to bind " + identity(specification),
                        failure
                );
            }
        }
        return specificationsById.size();
    }

    public synchronized boolean isFrozen() {
        return frozen;
    }

    public synchronized int size() {
        return specificationsById.size();
    }

    public synchronized List<LedgerEntry> ledger() {
        ArrayList<LedgerEntry> entries = new ArrayList<>(specificationsById.size());
        specificationsById.values().forEach(specification -> entries.add(
                new LedgerEntry(
                        specification.id(),
                        specification.messageClass().getName(),
                        specification.direction()
                )
        ));
        return List.copyOf(entries);
    }

    private String identity(NetworkMessageSpec<?> specification) {
        return "message ID " + specification.id()
                + " class " + specification.messageClass().getName();
    }

    @FunctionalInterface
    interface RegistrationSink {
        void register(NetworkMessageSpec<?> specification);
    }

    public record LedgerEntry(
            int id,
            String messageClassName,
            NetworkDirection direction
    ) {
        public LedgerEntry {
            Objects.requireNonNull(messageClassName, "messageClassName");
            Objects.requireNonNull(direction, "direction");
        }
    }
}
