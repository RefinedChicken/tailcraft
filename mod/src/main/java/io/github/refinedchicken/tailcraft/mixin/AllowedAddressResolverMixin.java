package io.github.refinedchicken.tailcraft.mixin;

import io.github.refinedchicken.tailcraft.TailcraftAddress;
import io.github.refinedchicken.tailcraft.client.ClientTunnels;
import net.minecraft.client.network.Address;
import net.minecraft.client.network.AllowedAddressResolver;
import net.minecraft.client.network.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Both joining (ConnectScreen) and server-list pings (MultiplayerServerListPinger) resolve
 * addresses here, off the render thread. Tailcraft addresses resolve to a local tunnel.
 */
@Mixin(AllowedAddressResolver.class)
abstract class AllowedAddressResolverMixin {
    @Inject(method = "resolve", at = @At("HEAD"), cancellable = true)
    private void tailcraft$resolveThroughTunnel(ServerAddress address, CallbackInfoReturnable<Optional<Address>> cir) {
        String host = ((ServerAddressAccessor) (Object) address).tailcraft$getHostAndPort().getHost();
        if (TailcraftAddress.isAddress(host)) {
            cir.setReturnValue(ClientTunnels.INSTANCE.resolve(host.trim()).map(Address::create));
        }
    }
}
