package com.fontainerepublic.server.playerdata;

import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.playerdata.api.PlayerDataService;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataNbtCodec;
import com.fontainerepublic.server.playerdata.persistence.PlayerDataRepository;
import com.fontainerepublic.server.playerdata.service.DefaultPlayerDataService;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Infrastructure module binding player-data persistence to one server runtime.
 */
public final class PlayerDataModule implements IModule {
    public static final ModuleId MODULE_ID = new ModuleId("player-data");
    private static final Logger LOGGER = LogUtils.getLogger();

    private PlayerDataRepository repository;
    private PlayerDataService service;

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Player Data",
                        "1.0.0",
                        Optional.of("Authoritative base player-data infrastructure"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(),
                Set.of(),
                50,
                PlayerDataModule::new
        );
        if (!registry.register(definition)) {
            throw new IllegalStateException("Unable to register module " + MODULE_ID);
        }
    }

    @Override
    public String getName() {
        return MODULE_ID.value();
    }

    @Override
    public void init() {
        repository = PlayerDataRepository.createProduction(
                new PlayerDataNbtCodec()
        );
        service = new DefaultPlayerDataService(repository, System::currentTimeMillis);
        LOGGER.info(
                "[PlayerData] Runtime initialized with {} player records",
                repository.size()
        );
    }

    @Override
    public void shutdown() {
        service = null;
        repository = null;
        LOGGER.info("[PlayerData] Runtime closed");
    }

    public PlayerDataService service() {
        if (service == null) {
            throw new IllegalStateException("Player-data service is not active");
        }
        return service;
    }
}
