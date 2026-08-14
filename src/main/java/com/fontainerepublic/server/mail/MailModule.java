package com.fontainerepublic.server.mail;

import com.fontainerepublic.core.IModule;
import com.fontainerepublic.core.module.ModuleDefinition;
import com.fontainerepublic.core.module.ModuleId;
import com.fontainerepublic.core.module.ModuleMetadata;
import com.fontainerepublic.core.module.ModuleRegistry;
import com.fontainerepublic.server.citizen.CitizenModule;
import com.fontainerepublic.server.citizen.api.CitizenService;
import com.fontainerepublic.server.economy.EconomyModule;
import com.fontainerepublic.server.economy.api.EconomyService;
import com.fontainerepublic.server.government.GovernmentModule;
import com.fontainerepublic.server.government.api.GovernmentService;
import com.fontainerepublic.server.mail.api.MailAuthority;
import com.fontainerepublic.server.mail.api.MailConfig;
import com.fontainerepublic.server.mail.api.MailService;
import com.fontainerepublic.server.mail.persistence.MailNbtCodec;
import com.fontainerepublic.server.mail.persistence.MailRepository;
import com.fontainerepublic.server.mail.service.DefaultMailService;
import com.fontainerepublic.server.mail.service.MailServerPlayerAccess;
import com.fontainerepublic.server.mail.service.ServerMailAuthority;
import com.fontainerepublic.server.network.NetworkRuntimeModule;
import com.fontainerepublic.server.network.NetworkSendService;
import com.fontainerepublic.server.playerdata.PlayerDataModule;
import com.fontainerepublic.server.registry.SubjectRegistryModule;
import com.fontainerepublic.server.registry.api.SubjectRegistryService;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Communicator mail module (FR-MAIL-001-A): the server-authoritative,
 * persistent email subsystem bound to one server runtime. Depends on the
 * economy (postage/attachment settlement), the subject registry + player
 * directory (recipient resolution), the citizen service (broadcast range),
 * the government service (institution access) and the network runtime (S2C
 * sync/alert). Binds the {@link MailService} at runtime start and unbinds on
 * shutdown.
 */
public final class MailModule implements IModule {

    public static final ModuleId MODULE_ID = new ModuleId("mail");
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Sentinel subject value representing the local console. */
    private static final UUID CONSOLE_SUBJECT =
            UUID.nameUUIDFromBytes("fontainerepublic:mail:console".getBytes());

    private final LongSupplier clock;
    private MailRepository repository;
    private MailService service;

    public MailModule() {
        this(System::currentTimeMillis);
    }

    MailModule(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static void register(ModuleRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        ModuleDefinition definition = new ModuleDefinition(
                MODULE_ID,
                new ModuleMetadata(
                        "Communicator Mail",
                        "1.0.0",
                        Optional.of("Server-authoritative persistent email subsystem"),
                        Optional.of("FontaineRepublic")
                ),
                Set.of(
                        PlayerDataModule.MODULE_ID,
                        SubjectRegistryModule.MODULE_ID,
                        EconomyModule.MODULE_ID,
                        CitizenModule.MODULE_ID,
                        GovernmentModule.MODULE_ID,
                        NetworkRuntimeModule.MODULE_ID
                ),
                Set.of(),
                75,
                MailModule::new
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
        repository = MailRepository.createProduction(new MailNbtCodec());
        LOGGER.info(
                "[Mail] Runtime initialized (revision={}, mailboxes={}, messages={})",
                repository.snapshot().storeRevision(),
                repository.snapshot().mailboxes().size(),
                repository.messageCount()
        );
    }

    /**
     * Binds the authoritative services after the runtime start and builds the
     * runtime mail service. Until bound, every C2S handler resolves no service
     * and silently drops.
     */
    public void bindServices(
            EconomyService economy,
            SubjectRegistryService subjectRegistry,
            com.fontainerepublic.server.playerdata.api.PlayerDirectoryService playerDirectory,
            CitizenService citizen,
            GovernmentService government,
            NetworkSendService sendService
    ) {
        MailAuthority authority = new ServerMailAuthority(
                government,
                parseHydroArchon(),
                CONSOLE_SUBJECT
        );
        this.service = new DefaultMailService(
                repository,
                Objects.requireNonNull(economy, "economy"),
                Objects.requireNonNull(subjectRegistry, "subjectRegistry"),
                Objects.requireNonNull(playerDirectory, "playerDirectory"),
                Objects.requireNonNull(citizen, "citizen"),
                Objects.requireNonNull(government, "government"),
                Objects.requireNonNull(sendService, "sendService"),
                authority,
                new MailServerPlayerAccess(),
                clock,
                new MailConfig(
                        com.fontainerepublic.core.ConfigManager.mailPostageFee(),
                        com.fontainerepublic.core.ConfigManager.mailAttachmentFee(),
                        com.fontainerepublic.core.ConfigManager.mailBroadcastFee(),
                        com.fontainerepublic.core.ConfigManager.mailBroadcastCooldownMillis(),
                        parseHydroArchon()
                )
        );
        MailRuntime.bind(service);
        LOGGER.info("[Mail] Runtime bound (postageFee={}, attachmentFee={})",
                com.fontainerepublic.core.ConfigManager.mailPostageFee(),
                com.fontainerepublic.core.ConfigManager.mailAttachmentFee());
    }

    private static UUID parseHydroArchon() {
        String raw = com.fontainerepublic.core.ConfigManager.emergencyHydroArchonUuid();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    @Override
    public void shutdown() {
        if (service != null) {
            service = null;
        }
        MailRuntime.unbind();
        repository = null;
        LOGGER.info("[Mail] Runtime closed");
    }

    public MailService service() {
        if (service == null) {
            throw new IllegalStateException("Mail service is not active");
        }
        return service;
    }
}
