package io.github.refinedchicken.tailcraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client entrypoint. Joining needs no UI: Tailcraft addresses typed into the normal server
 * address boxes are routed through {@link ClientTunnels} by mixins. Hosting a singleplayer
 * world lives in the Open to LAN screen ({@link LanHost}).
 */
public final class TailcraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LanHost.INSTANCE.tick(client);
            ClientTunnels.INSTANCE.tick(client);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LanHost.INSTANCE.stop();
            ClientTunnels.INSTANCE.close();
        });
    }
}
