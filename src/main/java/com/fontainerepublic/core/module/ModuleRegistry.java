package com.fontainerepublic.core.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Authoritative registry of immutable module definitions.
 *
 * <p>The registry preserves insertion order for the future dependency resolver, but does not
 * interpret dependencies or priority. Definitions remain registered when a server lifecycle
 * stops; the registration window is tied to the mod lifecycle.</p>
 */
public final class ModuleRegistry {
    private static final System.Logger LOGGER = System.getLogger(ModuleRegistry.class.getName());

    private final LinkedHashMap<ModuleId, ModuleDefinition> definitions = new LinkedHashMap<>();
    private boolean registrationOpen = true;

    /**
     * Registers a definition while the registration window is open.
     *
     * @return {@code true} when the definition was stored; {@code false} when registration is
     *         closed or the ID is already registered
     */
    public synchronized boolean register(ModuleDefinition definition) {
        Objects.requireNonNull(definition, "definition");

        if (!registrationOpen) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Rejected module registration after window closed: {0}",
                    definition.id()
            );
            return false;
        }

        if (definitions.containsKey(definition.id())) {
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Rejected duplicate module ID: {0}",
                    definition.id()
            );
            return false;
        }

        definitions.put(definition.id(), definition);
        return true;
    }

    /**
     * Permanently closes registration for this registry instance.
     */
    public synchronized void closeRegistration() {
        registrationOpen = false;
    }

    public synchronized boolean isRegistrationOpen() {
        return registrationOpen;
    }

    public synchronized boolean hasModule(ModuleId id) {
        return definitions.containsKey(Objects.requireNonNull(id, "id"));
    }

    public synchronized Optional<ModuleDefinition> getModuleDefinition(ModuleId id) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(id, "id")));
    }

    /**
     * Returns an immutable snapshot in stable registration order.
     */
    public synchronized List<ModuleDefinition> getDefinitionsInRegistrationOrder() {
        return List.copyOf(definitions.values());
    }

    /**
     * Returns an immutable insertion-ordered snapshot keyed by ModuleId.
     */
    public synchronized Map<ModuleId, ModuleDefinition> getDefinitionsById() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public synchronized int size() {
        return definitions.size();
    }
}
