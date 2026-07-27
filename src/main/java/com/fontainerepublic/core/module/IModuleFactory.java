package com.fontainerepublic.core.module;

import com.fontainerepublic.core.IModule;

/**
 * Creation-only contract for producing a fresh module instance for one server lifecycle.
 *
 * <p>Implementations must not cache or reuse module instances and must not perform runtime
 * initialization as part of object creation.</p>
 */
@FunctionalInterface
public interface IModuleFactory {
    IModule createInstance();
}
