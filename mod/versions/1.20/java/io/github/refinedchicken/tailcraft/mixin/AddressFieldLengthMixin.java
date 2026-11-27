package io.github.refinedchicken.tailcraft.mixin;

import io.github.refinedchicken.tailcraft.TailcraftAddress;
import net.minecraft.client.gui.screen.AddServerScreen;
import net.minecraft.client.gui.screen.DirectConnectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Vanilla caps server address fields at 128 characters; Tailcraft addresses are longer.
 * Replaces the shared copy because Yarn only moved these screens into the multiplayer package in
 * 1.20.3; the runtime classes are the same.
 */
@Mixin({AddServerScreen.class, DirectConnectScreen.class})
abstract class AddressFieldLengthMixin {
    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;setMaxLength(I)V"))
    private int tailcraft$allowTailcraftAddresses(int maxLength) {
        return Math.max(maxLength, TailcraftAddress.MAX_LENGTH);
    }
}
