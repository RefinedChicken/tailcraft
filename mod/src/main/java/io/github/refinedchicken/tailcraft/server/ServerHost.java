package io.github.refinedchicken.tailcraft.server;

import io.github.refinedchicken.tailcraft.HelperEvent;
import io.github.refinedchicken.tailcraft.HelperProcess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.github.refinedchicken.tailcraft.Tailcraft.LOG;

/**
 * Keeps the dedicated server reachable at its permanent Tailcraft address. The helper is
 * restarted with backoff if it fails (for example while DERP is unreachable), so the server
 * recovers on its own. Tailcraft failures never affect the server's normal port.
 */
final class ServerHost {
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration MIN_RETRY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY = Duration.ofMinutes(5);

    private final Path keyFile;
    private final ServerConfig config;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("tailcraft-server").factory());

    private String target;
    private HelperProcess process;
    /** Bumped whenever the current helper is replaced, so late events from old helpers are ignored. */
    private int generation;
    private boolean stopped = true;
    private Duration retryDelay = MIN_RETRY;
    private volatile String address;
    private CompletableFuture<String> nextReady = new CompletableFuture<>();

    ServerHost(Path keyFile, ServerConfig config) {
        this.keyFile = keyFile;
        this.config = config;
    }

    String address() {
        return address;
    }

    synchronized void start(String target) {
        this.target = target;
        stopped = false;
        launch();
    }

    synchronized void stop() {
        stopped = true;
        replaceProcess();
        scheduler.shutdownNow();
    }

    /** Deletes the identity and starts over with a new address. Completes with the new address. */
    synchronized CompletableFuture<String> regenerate() {
        if (stopped) return CompletableFuture.failedFuture(new IllegalStateException("Tailcraft is not running"));
        replaceProcess();
        address = null;
        try {
            Files.deleteIfExists(keyFile);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        CompletableFuture<String> ready = pendingReady();
        retryDelay = MIN_RETRY;
        launch();
        return ready;
    }

    private CompletableFuture<String> pendingReady() {
        if (nextReady.isDone()) nextReady = new CompletableFuture<>();
        return nextReady;
    }

    private void launch() {
        if (stopped) return;
        int gen = ++generation;
        HelperProcess started;
        try {
            started = HelperProcess.start(List.of("host", "--target", target, "--key-file", keyFile.toString()),
                    event -> onEvent(gen, event));
        } catch (IOException | RuntimeException e) {
            // Missing platform binary or a host that blocks running executables; retrying won't help.
            LOG.error("Tailcraft could not start its network helper: {}. The server is still reachable on its normal port.", e.getMessage());
            pendingReady().completeExceptionally(e);
            return;
        }
        process = started;
        started.ready(STARTUP_TIMEOUT).whenComplete((event, error) -> {
            if (error != null) fail(gen, rootMessage(error));
            else onReady(gen, event);
        });
    }

    private synchronized void onReady(int gen, HelperEvent event) {
        if (gen != generation || stopped) return;
        retryDelay = MIN_RETRY;
        address = event.address();
        try {
            config.writeAddress(address);
        } catch (IOException e) {
            LOG.warn("Could not write the address to config/tailcraft.json: {}", e.getMessage());
        }
        LOG.info("================================================================");
        LOG.info("Tailcraft is ready. Players with the Tailcraft mod can join by pasting this");
        LOG.info("address into Add Server or Direct Connect:");
        LOG.info("{}", address);
        LOG.info("Anyone with this address can reach the server; whitelist and online-mode still");
        LOG.info("apply. It is also saved in config/tailcraft.json. Make a new one with");
        LOG.info("/tailcraft regenerate.");
        LOG.info("================================================================");
        pendingReady().complete(address);
    }

    private void onEvent(int gen, HelperEvent event) {
        if (event.type().equals("error")) fail(gen, event.code() + ": " + event.message());
    }

    private synchronized void fail(int gen, String reason) {
        if (gen != generation || stopped) return;
        replaceProcess();
        address = null;
        Duration delay = retryDelay;
        retryDelay = retryDelay.multipliedBy(2).compareTo(MAX_RETRY) > 0 ? MAX_RETRY : retryDelay.multipliedBy(2);
        LOG.warn("Tailcraft lost its connection ({}). Retrying in {} seconds. The server is still reachable on its normal port.",
                reason, delay.toSeconds());
        pendingReady().completeExceptionally(new IOException(reason));
        int expected = generation;
        scheduler.schedule(() -> {
            synchronized (this) {
                // Skip if a regenerate (or stop) already replaced the helper meanwhile.
                if (generation == expected) launch();
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void replaceProcess() {
        generation++;
        if (process != null) {
            process.close();
            process = null;
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
