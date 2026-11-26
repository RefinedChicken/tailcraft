package io.github.refinedchicken.tailcraft.client;

import io.github.refinedchicken.tailcraft.HelperEvent;
import io.github.refinedchicken.tailcraft.HelperProcess;
import io.github.refinedchicken.tailcraft.compat.VersionCompat;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Duration;
import java.util.List;

import static io.github.refinedchicken.tailcraft.Tailcraft.LOG;

/**
 * Shares a singleplayer world opened to LAN with a temporary Tailcraft address. A new address is
 * made every time, like vanilla's random LAN port, and it dies with the world.
 */
public final class LanHost {
    public static final LanHost INSTANCE = new LanHost();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);

    /** Set by the Open to LAN screen's toggle; consumed by the next successful openToLan. */
    private boolean shareOnOpen;
    private Session session;

    private LanHost() {}

    public synchronized void setShareOnOpen(boolean share) {
        shareOnOpen = share;
    }

    /** Called right after the integrated server is published to LAN on {@code port}. */
    public synchronized void onLanOpened(IntegratedServer server, int port) {
        if (!shareOnOpen) return;
        shareOnOpen = false;
        stop();
        boolean previousOnlineMode = server.isOnlineMode();
        enableDevelopmentOfflineAuth(server);
        MinecraftClient client = MinecraftClient.getInstance();
        HelperProcess process;
        try {
            process = HelperProcess.start(List.of("host", "--target", "127.0.0.1:" + port), event -> onEvent(client, event));
        } catch (Exception e) {
            server.setOnlineMode(previousOnlineMode);
            showFailure(client, e.getMessage());
            return;
        }
        Session started = new Session(process, server, previousOnlineMode);
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

    public synchronized void tick(MinecraftClient client) {
        if (session == null) return;
        IntegratedServer server = client.getServer();
        if (!session.process.isAlive() || server != session.server || !server.isRunning()) stop();
    }

    public synchronized void stop() {
        if (session == null) return;
        Session stopped = session;
        session = null;
        stopped.process.close();
        stopped.server.setOnlineMode(stopped.previousOnlineMode);
    }

    private void onEvent(MinecraftClient client, HelperEvent event) {
        if (!event.type().equals("error")) return;
        boolean wasRunning;
        synchronized (this) {
            wasRunning = session != null;
            stop();
        }
        if (wasRunning) showFailure(client, event.message());
    }

    private static void announce(MinecraftClient client, HelperEvent event) {
        String address = event.address();
        Text copy = Text.translatable("tailcraft.lan.copy").styled(style -> VersionCompat.copyToClipboard(
                style.withColor(Formatting.GREEN), address, Text.translatable("tailcraft.lan.copy_hover")));
        client.inGameHud.getChatHud().addMessage(Text.translatable("tailcraft.lan.ready", copy));
    }

    private static void showFailure(MinecraftClient client, String reason) {
        LOG.warn("Tailcraft sharing failed: {}", reason);
        client.execute(() -> SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                Text.translatable("tailcraft.lan.failed"), Text.literal(String.valueOf(reason))));
    }

    /** Lets a second offline dev client join; only in Fabric's development environment. */
    private static void enableDevelopmentOfflineAuth(IntegratedServer server) {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()
                && "1".equals(System.getenv("TAILCRAFT_DEV_OFFLINE_AUTH"))) {
            server.setOnlineMode(false);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private record Session(HelperProcess process, IntegratedServer server, boolean previousOnlineMode) {}
}
