package io.github.refinedchicken.tailcraft.mixin;

import io.github.refinedchicken.tailcraft.client.LanHost;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
abstract class IntegratedServerMixin {
    @Inject(method = "openToLan", at = @At("RETURN"))
    private void tailcraft$shareAfterLanOpens(@Nullable GameMode gameMode, boolean cheatsAllowed, int port,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) LanHost.INSTANCE.onLanOpened((IntegratedServer) (Object) this, port);
    }
}
