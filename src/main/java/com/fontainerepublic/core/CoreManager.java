package com.fontainerepublic.core;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public class CoreManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TreeMap<Integer, IModule> modules = new TreeMap<>();
    private static final List<IModule> initOrder = new ArrayList<>();

    public static void register(IModule module) {
        modules.put(module.getPriority(), module);
        LOGGER.info("[CoreManager] Registered module: {} (priority={})", module.getName(), module.getPriority());
    }

    public static void initModules() {
        LOGGER.info("[CoreManager] Initializing modules");
        initOrder.clear();
        for (IModule module : modules.values()) {
            module.init();
            initOrder.add(module);
            LOGGER.info("[CoreManager] Module initialized: {}", module.getName());
        }
        LOGGER.info("[CoreManager] All modules initialized");
    }

    public static void shutdownModules() {
        LOGGER.info("[CoreManager] Shutting down modules");
        for (int i = initOrder.size() - 1; i >= 0; i--) {
            IModule module = initOrder.get(i);
            module.shutdown();
            LOGGER.info("[CoreManager] Module shut down: {}", module.getName());
        }
        LOGGER.info("[CoreManager] All modules shut down");
    }
}
