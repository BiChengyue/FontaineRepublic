package com.fontainerepublic.server.emergency.api;

import java.util.List;
import java.util.Optional;

/**
 * Mod-lifetime OPEN-to-FROZEN registry for immutable emergency-action
 * descriptors (FR-EMG-001-A §5, modeled on the command contribution
 * contract).
 *
 * <p>Registration rejects duplicate {@code (moduleId, actionId, version)},
 * duplicate provider identity, invalid schemas, unbounded fields, and
 * providers whose declared owner does not match the business module. Registry
 * acceptance is not policy approval: every contributed action still requires
 * its own independently reviewed emergency-action catalogue entry.</p>
 */
public interface EmergencyActionRegistry {

    /**
     * Registers a descriptor while the registry is OPEN. Throws on duplicate
     * keys, reserved values, or invalid metadata.
     */
    void register(EmergencyActionDescriptor descriptor);

    /** Freezes the registry; further registration is rejected. */
    void freeze();

    /**
     * Resolves a descriptor by stable key (moduleId/actionId/version) from the
     * frozen snapshot.
     */
    Optional<EmergencyActionDescriptor> find(String moduleId, String actionId,
                                             String actionVersion);

    /** Immutable ordered snapshot of the frozen descriptors. */
    List<EmergencyActionDescriptor> requireFrozenSnapshot();

    /** Whether the registry is frozen. */
    boolean isFrozen();
}
