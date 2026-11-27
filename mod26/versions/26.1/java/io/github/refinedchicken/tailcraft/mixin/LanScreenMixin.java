package io.github.refinedchicken.tailcraft.mixin;

import io.github.refinedchicken.tailcraft.client.LanHost;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "Tailcraft: ON/OFF" toggle under the port field of the Share to LAN screen (26.1 to
 * 26.1.2; 26.2 replaced it with Multiplayer Options). If it is on when the world is published,
 * the world is shared with a Tailcraft address.
 */
@Mixin(ShareToLanScreen.class)
abstract class LanScreenMixin extends Screen {
    @Unique private boolean tailcraft$share;

    private LanScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void tailcraft$addToggle(CallbackInfo ci) {
        addRenderableWidget(CycleButton.onOffBuilder(tailcraft$share)
                .withTooltip(value -> Tooltip.create(Component.translatable("tailcraft.lan.tooltip")))
                .create(width / 2 - 75, 186, 150, 20, Component.translatable("tailcraft.lan.toggle"),
                        (button, share) -> tailcraft$share = share));
    }

    // The Start LAN World button's handler: it publishes the world, then we share it if asked.
    @Inject(method = "lambda$init$2", at = @At("TAIL"))
    private void tailcraft$shareAfterPublish(IntegratedServer server, Button button, CallbackInfo ci) {
        if (tailcraft$share && server.isPublished()) LanHost.INSTANCE.share(server);
    }
}
