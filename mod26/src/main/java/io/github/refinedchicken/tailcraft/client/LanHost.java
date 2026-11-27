package io.github.refinedchicken.tailcraft.client;

import io.github.refinedchicken.tailcraft.HelperEvent;
import io.github.refinedchicken.tailcraft.HelperProcess;
import io.github.refinedchicken.tailcraft.compat.VersionCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

import java.time.Duration;
import java.util.List;

import static io.github.refinedchicken.tailcraft.Tailcraft.LOG;

/**
 * Shares a singleplayer world that is open to LAN with a temporary Tailcraft address. Each range's
 * LanScreenMixin drives this: {@code share} when the world is published with the Tailcraft toggle
 * on, {@code stop} otherwise (26.2+). A fresh address (New Address, 26.2+) is {@code stop} then
 * {@code share}. A new address is made each time, like vanilla's random LAN port, and it dies
 * with the world.
 */
public final class LanHost {
    public static final LanHost INSTANCE = new LanHost();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);

    private Session session;

    private LanHost() {}

    public synchronized boolean isSharing() {
        return session != null;
    }

    /** Starts sharing the given published integrated server, unless it is already being shared. */
    public synchronized void share(IntegratedServer server) {
        if (session != null && session.server == server) return;
        stop();
        start(server);
    }

    private void start(IntegratedServer server) {
        boolean previousAuth = server.usesAuthentication();
        enableDevelopmentOfflineAuth(server);
        Minecraft client = Minecraft.getInstance();
        HelperProcess process;
        try {
            process = HelperProcess.start(List.of("host", "--target", "127.0.0.1:" + server.getPort()), event -> onEvent(client, event));
        } catch (Exception e) {
            server.setUsesAuthentication(previousAuth);
            showFailure(client, e.getMessage());
            return;
        }
        Session started = new Session(process, server, previousAuth);
        session = started;
        process.ready(STARTUP_TIMEOUT).whenComplete((event, error) -> {
            if (error != null) {
                synchronized (this) {
                    if (session == started) stop();
                }
                showFailure(client, rootMessage(error));
            } else {
                client.execute(() -> announce(client, event));
            }
        });
    }

    public synchronized void tick(Minecraft client) {
        if (session == null) return;
        IntegratedServer server = client.getSingleplayerServer();
        if (!session.process.isAlive() || server != session.server || !server.isRunning() || !server.isPublished()) stop();
    }

    public synchronized void stop() {
        if (session == null) return;
        Session stopped = session;
        session = null;
        stopped.process.close();
        stopped.server.setUsesAuthentication(stopped.previousAuth);
    }

    private void onEvent(Minecraft client, HelperEvent event) {
        if (!event.type().equals("error")) return;
        boolean wasRunning;
        synchronized (this) {
            wasRunning = session != null;
            stop();
        }
        if (wasRunning) showFailure(client, event.message());
    }

    private static void announce(Minecraft client, HelperEvent event) {
        String address = event.address();
        Component copy = Component.translatable("tailcraft.lan.copy").withStyle(style -> VersionCompat.copyToClipboard(
                style.withColor(ChatFormatting.GREEN), address, Component.translatable("tailcraft.lan.copy_hover")));
        if (client.player != null) {
            client.player.sendSystemMessage(Component.translatable("tailcraft.lan.ready", copy));
        }
    }

    private static void showFailure(Minecraft client, String reason) {
        LOG.warn("Tailcraft sharing failed: {}", reason);
        client.execute(() -> SystemToast.add(VersionCompat.toastManager(client), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("tailcraft.lan.failed"), Component.literal(String.valueOf(reason))));
    }

    /** Lets a second offline dev client join; only in Fabric's development environment. */
    private static void enableDevelopmentOfflineAuth(IntegratedServer server) {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()
                && "1".equals(System.getenv("TAILCRAFT_DEV_OFFLINE_AUTH"))) {
            server.setUsesAuthentication(false);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private record Session(HelperProcess process, IntegratedServer server, boolean previousAuth) {}
}
