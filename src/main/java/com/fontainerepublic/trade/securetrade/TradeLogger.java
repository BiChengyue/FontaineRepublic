package com.fontainerepublic.trade.securetrade;

import com.fontainerepublic.trade.securetrade.platform.Services;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Verbatim port of Navielon/SecureTrade's {@code TradeLogger} (MIT,
 * Copyright (c) 2026 Secure Trade Mod Authors; upstream commit
 * {@code add98b377ffc39e5d73789a874a08c5e369790ce}).
 */
public class TradeLogger {
    private static final Path LOG_FILE = Paths.get("logs", "securetrade.log");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Object LOCK = new Object();
    private static ExecutorService executor = createExecutor();

    public static void log(String message) {
        if (!Services.PLATFORM.isLoggingEnabled()) return;

        String time = LocalDateTime.now().format(FORMATTER);
        String logEntry = String.format("[%s] %s%n", time, message);

        synchronized (LOCK) {
            if (executor == null || executor.isShutdown() || executor.isTerminated()) {
                executor = createExecutor();
            }
            executor.execute(() -> writeLogEntry(logEntry));
        }
    }

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "SecureTrade-Logger");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void writeLogEntry(String logEntry) {
        try {
            if (!Files.exists(LOG_FILE.getParent())) {
                Files.createDirectories(LOG_FILE.getParent());
            }
            Files.writeString(LOG_FILE, logEntry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void shutdown() {
        ExecutorService toShutdown;
        synchronized (LOCK) {
            toShutdown = executor;
            executor = null;
        }

        if (toShutdown == null) {
            return;
        }

        toShutdown.shutdown();
        try {
            if (!toShutdown.awaitTermination(5, TimeUnit.SECONDS)) {
                toShutdown.shutdownNow();
            }
        } catch (InterruptedException e) {
            toShutdown.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
