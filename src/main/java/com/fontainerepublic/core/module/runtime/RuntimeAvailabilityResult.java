package com.fontainerepublic.core.module.runtime;

import com.fontainerepublic.core.module.ModuleId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable runtime-availability snapshot for one server lifecycle.
 */
public final class RuntimeAvailabilityResult {
    private final Map<ModuleId, ModuleAvailabilityRecord> records;

    public RuntimeAvailabilityResult(Map<ModuleId, ModuleAvailabilityRecord> records) {
        Objects.requireNonNull(records, "records");

        LinkedHashMap<ModuleId, ModuleAvailabilityRecord> copy = new LinkedHashMap<>();
        records.forEach((moduleId, record) -> {
            Objects.requireNonNull(moduleId, "records key");
            Objects.requireNonNull(record, "records value");
            if (!moduleId.equals(record.moduleId())) {
                throw new IllegalArgumentException(
                        "Availability record key does not match module ID: " + moduleId
                );
            }
            if (record.status() == AvailabilityStatus.PENDING) {
                throw new IllegalArgumentException(
                        "Published availability snapshot cannot contain PENDING record: " + moduleId
                );
            }
            copy.put(moduleId, record);
        });
        this.records = Collections.unmodifiableMap(copy);
    }

    public Map<ModuleId, ModuleAvailabilityRecord> records() {
        return records;
    }

    public Optional<ModuleAvailabilityRecord> getAvailability(ModuleId moduleId) {
        return Optional.ofNullable(records.get(Objects.requireNonNull(moduleId, "moduleId")));
    }

    public boolean isAvailable(ModuleId moduleId) {
        return getAvailability(moduleId)
                .map(record -> record.status() == AvailabilityStatus.AVAILABLE)
                .orElse(false);
    }

    public Set<ModuleId> unavailableModules() {
        LinkedHashSet<ModuleId> unavailable = new LinkedHashSet<>();
        records.forEach((moduleId, record) -> {
            if (record.status() != AvailabilityStatus.AVAILABLE) {
                unavailable.add(moduleId);
            }
        });
        return Collections.unmodifiableSet(unavailable);
    }
}
