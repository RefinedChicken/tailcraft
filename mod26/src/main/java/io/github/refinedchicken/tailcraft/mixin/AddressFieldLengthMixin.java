package io.github.refinedchicken.tailcraft.mixin;

import io.github.refinedchicken.tailcraft.TailcraftAddress;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.ManageServerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Vanilla caps server address fields at 128 characters; Tailcraft addresses are longer. */
@Mixin({ManageServerScreen.class, DirectJoinServerScreen.class})
abstract class AddressFieldLengthMixin {
    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/EditBox;setMaxLength(I)V"))
    private int tailcraft$allowTailcraftAddresses(int maxLength) {
        return Math.max(maxLength, TailcraftAddress.MAX_LENGTH);
    }
}
