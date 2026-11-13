package io.github.refinedchicken.tailcraft.client;

import io.github.refinedchicken.tailcraft.HelperEvent;
import io.github.refinedchicken.tailcraft.HelperProcess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.github.refinedchicken.tailcraft.Tailcraft.LOG;

/**
 * One join helper per Tailcraft address, shared by server-list pings and the real connection.
 * Minecraft is pointed at the helper's loopback listener. Tunnels close after sitting unused.
 */
public final class ClientTunnels {
    public static final ClientTunnels INSTANCE = new ClientTunnels();

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(20);
    private static final long IDLE_NANOS = TimeUnit.SECONDS.toNanos(60);

    private final Map<String, Tunnel> tunnels = new HashMap<>();

    private ClientTunnels() {}

    /**
     * Returns the loopback address to connect to for a Tailcraft address, starting its tunnel if
     * needed. Blocks; Minecraft calls this from its connector and pinger threads.
     */
    public Optional<InetSocketAddress> resolve(String address) {
        Tunnel tunnel;
        synchronized (this) {
            tunnel = tunnels.get(address);
            if (tunnel == null || !tunnel.process.isAlive()) {
                if (tunnel != null) tunnel.process.close();
                try {
                    tunnel = new Tunnel(HelperProcess.start(List.of("join", "--address", address), event -> {}));
                } catch (Exception e) {
                    reportFailure(e.getMessage());
                    return Optional.empty();
                }
                tunnels.put(address, tunnel);
            }
            tunnel.lastUsed = System.nanoTime();
        }
        try {
            HelperEvent ready = tunnel.process.ready(STARTUP_TIMEOUT).get();
            String local = ready.address();
            int port = Integer.parseInt(local.substring(local.lastIndexOf(':') + 1));
            tunnel.port = port;
            return Optional.of(new InetSocketAddress("127.0.0.1", port));
        } catch (Exception e) {
            synchronized (this) {
                if (tunnels.get(address) == tunnel) tunnels.remove(address);
            }
            tunnel.process.close();
            reportFailure(rootMessage(e));
            return Optional.empty();
        }
    }

    /** Closes tunnels that are idle and not carrying the current game connection. */
    public synchronized void tick(MinecraftClient client) {
        int activePort = activeLoopbackPort(client);
        long now = System.nanoTime();
        for (Iterator<Tunnel> it = tunnels.values().iterator(); it.hasNext(); ) {
            Tunnel tunnel = it.next();
            if (tunnel.port != 0 && tunnel.port == activePort) tunnel.lastUsed = now;
            if (now - tunnel.lastUsed > IDLE_NANOS || !tunnel.process.isAlive()) {
                tunnel.process.close();
                it.remove();
            }
        }
    }

    public synchronized void close() {
        tunnels.values().forEach(tunnel -> tunnel.process.close());
        tunnels.clear();
    }

    private static int activeLoopbackPort(MinecraftClient client) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return -1;
        SocketAddress remote = handler.getConnection().getAddress();
        if (remote instanceof InetSocketAddress inet && inet.getAddress() != null && inet.getAddress().isLoopbackAddress()) {
            return inet.getPort();
        }
        return -1;
    }

    private static void reportFailure(String reason) {
        LOG.warn("Tailcraft connection failed: {}", reason);
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                Text.translatable("tailcraft.connect.failed"), Text.literal(reason)));
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static final class Tunnel {
        final HelperProcess process;
        volatile long lastUsed = System.nanoTime();
        volatile int port;

        Tunnel(HelperProcess process) {
            this.process = process;
        }
    }
}
