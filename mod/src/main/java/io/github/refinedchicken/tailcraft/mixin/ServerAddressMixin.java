package io.github.refinedchicken.tailcraft.mixin;

import io.github.refinedchicken.tailcraft.TailcraftAddress;
import net.minecraft.client.network.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla rejects Tailcraft addresses as hostnames (too long for DNS); accept them. */
@Mixin(ServerAddress.class)
abstract class ServerAddressMixin {
    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private static void tailcraft$acceptTailcraftAddress(String address, CallbackInfoReturnable<Boolean> cir) {
        if (TailcraftAddress.isAddress(address)) cir.setReturnValue(true);
    }
}
