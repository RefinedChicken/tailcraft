package io.github.refinedchicken.tailcraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Client entrypoint. Joining needs no UI: Tailcraft addresses typed into the normal server
 * address boxes are routed through {@link ClientTunnels} by mixins.
 *
 * <p>Singleplayer "Open to LAN" sharing is not ported to 26.x yet: that screen moved into
 * {@code WorldOptionsScreen} and needs its own mixin. Joining and dedicated-server hosting work.
 */
public final class TailcraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTunnels.INSTANCE::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ClientTunnels.INSTANCE.close());
    }
}
