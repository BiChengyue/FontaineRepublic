package com.fontainerepublic.core.module.runtime;

/**
 * Runtime lifecycle states for one module container in one server lifecycle.
 */
public enum ModuleState {
    REGISTERED,
    INITIALIZING,
    ACTIVE,
    INIT_FAILURE,
    DEPENDENCY_FAILURE,
    STOPPING,
    STOPPED,
    CLEANUP,
    TERMINATED
}
