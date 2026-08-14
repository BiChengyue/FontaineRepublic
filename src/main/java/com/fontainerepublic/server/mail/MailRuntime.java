package com.fontainerepublic.server.mail;

import com.fontainerepublic.server.mail.api.MailService;

import java.util.Objects;
import java.util.Optional;

/**
 * Server-runtime holder of the current ACTIVE communicator mail service.
 * Follows the module service-routing precedent ({@code TradeRuntime},
 * {@code NetworkRuntimeResolver}): the C2S handlers in the common package hold
 * no state and resolve the active {@link MailService} per invocation. The
 * module binds the service at runtime start and unbinds it on shutdown.
 */
public final class MailRuntime {

    private static volatile MailService service;

    private MailRuntime() {
    }

    public static void bind(MailService mailService) {
        service = Objects.requireNonNull(mailService, "mailService");
    }

    public static void unbind() {
        service = null;
    }

    public static Optional<MailService> resolve() {
        return Optional.ofNullable(service);
    }
}
