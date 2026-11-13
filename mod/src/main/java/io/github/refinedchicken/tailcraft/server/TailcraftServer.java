package io.github.refinedchicken.tailcraft.server;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;

import static io.github.refinedchicken.tailcraft.Tailcraft.LOG;
import static net.minecraft.server.command.CommandManager.literal;

/** Dedicated-server entrypoint: gives the server a permanent Tailcraft address. */
public final class TailcraftServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        ServerConfig config;
        try {
            config = ServerConfig.load(configDir.resolve("tailcraft.json"));
        } catch (IOException e) {
            LOG.error("Tailcraft is disabled: {}", e.getMessage());
            return;
        }
        if (!config.enabled()) {
            LOG.info("Tailcraft is disabled in config/tailcraft.json");
            return;
        }
        ServerHost host = new ServerHost(configDir.resolve("tailcraft").resolve("server.key"), config);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                host.start(target(server));
            } catch (UnknownHostException e) {
                LOG.error("Tailcraft could not resolve server-ip {}: {}", server.getServerIp(), e.getMessage());
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> host.stop());
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> register(dispatcher, host));
    }

    /**
     * Where the helper forwards players: loopback unless the admin bound the server to a specific
     * address with server-ip, in which case that address.
     */
    static String target(MinecraftServer server) throws UnknownHostException {
        String ip = server.getServerIp();
        InetAddress address = ip == null || ip.isBlank() ? null : InetAddress.getByName(ip);
        if (address == null || address.isAnyLocalAddress()) address = InetAddress.getByName("127.0.0.1");
        String host = address.getHostAddress();
        int zone = host.indexOf('%');
        if (zone >= 0) host = host.substring(0, zone);
        return (address instanceof Inet6Address ? "[" + host + "]" : host) + ":" + server.getServerPort();
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher, ServerHost host) {
        dispatcher.register(literal("tailcraft")
                .requires(source -> source.hasPermissionLevel(4))
                .then(literal("address").executes(ctx -> {
                    String address = host.address();
                    if (address == null) {
                        ctx.getSource().sendError(Text.literal("Tailcraft isn't connected right now. Check the server log for the reason."));
                        return 0;
                    }
                    ctx.getSource().sendFeedback(() -> addressText(address), false);
                    return 1;
                }))
                .then(literal("regenerate")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal(
                                    "This replaces the server's Tailcraft address. The current address stops working and every "
                                            + "player has to update their saved server. Run /tailcraft regenerate confirm to continue.")
                                    .formatted(Formatting.YELLOW), false);
                            return 1;
                        })
                        .then(literal("confirm").executes(ctx -> {
                            ServerCommandSource source = ctx.getSource();
                            source.sendFeedback(() -> Text.literal("Creating a new Tailcraft address..."), true);
                            host.regenerate().whenComplete((address, error) -> source.getServer().execute(() -> {
                                if (error != null) {
                                    source.sendError(Text.literal("Tailcraft couldn't connect with the new address yet ("
                                            + error.getMessage() + "). It will keep retrying; check the server log."));
                                } else {
                                    source.sendFeedback(() -> Text.literal("New Tailcraft address (the old one no longer works): ")
                                            .append(addressText(address)), true);
                                }
                            }));
                            return 1;
                        }))));
    }

    private static Text addressText(String address) {
        return Text.literal(address).styled(style -> style
                .withUnderline(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, address))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to copy"))));
    }
}
