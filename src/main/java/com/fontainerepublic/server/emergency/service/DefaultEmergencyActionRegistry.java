package com.fontainerepublic.server.emergency.service;

import com.fontainerepublic.server.emergency.api.EmergencyActionDescriptor;
import com.fontainerepublic.server.emergency.api.EmergencyActionRegistry;
import com.fontainerepublic.server.emergency.api.EmergencyProviderResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Mod-lifetime OPEN-to-FROZEN registry for immutable emergency-action
 * descriptors (FR-EMG-001-A §5).
 *
 * <p>Registration rejects duplicate {@code (moduleId, actionId, version)},
 * duplicate provider identity, and invalid/unbounded metadata. The frozen
 * snapshot is an unmodifiable, deterministically ordered list. Registry
 * acceptance is not policy approval.</p>
 */
public final class DefaultEmergencyActionRegistry implements EmergencyActionRegistry {

    private static final int MAX_ID_LENGTH = 64;
    private static final Pattern VALID_ID =
            Pattern.compile("[a-z][a-z0-9_-]{0," + (MAX_ID_LENGTH - 1) + "}");
    private static final Pattern VALID_VERSION =
            Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+");

    private final LinkedHashMap<String, EmergencyActionDescriptor> descriptors =
            new LinkedHashMap<>();
    private final LinkedHashMap<String, String> providerKeys = new LinkedHashMap<>();
    private List<EmergencyActionDescriptor> frozenSnapshot;

    @Override
    public synchronized void register(EmergencyActionDescriptor descriptor) {
        if (frozenSnapshot != null) {
            throw new IllegalStateException(
                    "Emergency action registry is already frozen"
            );
        }
        Objects.requireNonNull(descriptor, "descriptor");
        validateId(descriptor.moduleId(), "moduleId");
        validateId(descriptor.actionId(), "actionId");
        if (!VALID_VERSION.matcher(descriptor.actionVersion()).matches()) {
            throw new IllegalArgumentException(
                    "actionVersion must be semantic (major.minor.patch): "
                            + descriptor.actionVersion()
            );
        }
        String key = descriptor.key();
        if (descriptors.containsKey(key)) {
            throw new IllegalArgumentException(
                    "Duplicate emergency action key: " + key
            );
        }
        String providerKey = descriptor.providerIdentity() + "/" + descriptor.providerVersion();
        String existing = providerKeys.putIfAbsent(providerKey, key);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Duplicate provider identity/version " + providerKey
                            + " already registered for " + existing
            );
        }
        descriptors.put(key, descriptor);
    }

    @Override
    public synchronized void freeze() {
        if (frozenSnapshot != null) {
            throw new IllegalStateException(
                    "Emergency action registry has already been frozen"
            );
        }
        ArrayList<EmergencyActionDescriptor> ordered =
                new ArrayList<>(descriptors.values());
        ordered.sort((left, right) -> left.key().compareTo(right.key()));
        frozenSnapshot = Collections.unmodifiableList(ordered);
    }

    @Override
    public synchronized Optional<EmergencyActionDescriptor> find(
            String moduleId,
            String actionId,
            String actionVersion
    ) {
        if (frozenSnapshot == null) {
            throw new IllegalStateException(
                    "Emergency action registry is not frozen"
            );
        }
        return Optional.ofNullable(descriptors.get(
                moduleId + "/" + actionId + "/" + actionVersion
        ));
    }

    @Override
    public synchronized List<EmergencyActionDescriptor> requireFrozenSnapshot() {
        if (frozenSnapshot == null) {
            throw new IllegalStateException(
                    "Emergency action registry is not frozen"
            );
        }
        return frozenSnapshot;
    }

    @Override
    public synchronized boolean isFrozen() {
        return frozenSnapshot != null;
    }

    private static void validateId(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " must match [a-z][a-z0-9_-]{0,"
                            + (MAX_ID_LENGTH - 1) + "}: " + value
            );
        }
    }
}
