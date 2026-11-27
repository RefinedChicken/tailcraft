package io.github.refinedchicken.tailcraft.mixin;

import io.github.refinedchicken.tailcraft.TailcraftAddress;
import io.github.refinedchicken.tailcraft.client.ClientTunnels;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Both joining (ConnectScreen) and server-list pings (ServerStatusPinger) resolve addresses here,
 * off the render thread. Tailcraft addresses resolve to a local tunnel. The raw host is read via
 * {@link ServerAddressAccessor} because {@code ServerAddress.getHost()} IDN-encodes and returns ""
 * for Tailcraft addresses.
 */
@Mixin(ServerNameResolver.class)
abstract class TailcraftResolveMixin {
    @Inject(method = "resolveAddress", at = @At("HEAD"), cancellable = true)
    private void tailcraft$resolveThroughTunnel(ServerAddress address, CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {
        String host = ((ServerAddressAccessor) (Object) address).tailcraft$getHostAndPort().getHost();
        if (TailcraftAddress.isAddress(host)) {
            cir.setReturnValue(ClientTunnels.INSTANCE.resolve(host.trim()).map(ResolvedServerAddress::from));
        }
    }
}
