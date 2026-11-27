package io.github.refinedchicken.tailcraft.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.refinedchicken.tailcraft.client.LanHost;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.WorldOptionsScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a Tailcraft row to the Multiplayer section of the World Options screen (the 26.x
 * replacement for the Open to LAN screen): a "Tailcraft: ON/OFF" toggle and a "New Address" button
 * that is grayed until the toggle is on. Like the other controls, both take effect on Apply Changes,
 * and Cancel discards them. The toggle works even when LAN is off; turning it on publishes the world
 * to LAN on Apply Changes. The row joins the vanilla grid so it lines up with Command Access / Force
 * Game Mode without an extra gap.
 */
@Mixin(WorldOptionsScreen.class)
abstract class LanScreenMixin {
    @Shadow private MinecraftServer.MultiplayerScope wantedMultiplayerScope;

    @Shadow protected abstract void updateApplyChangesActiveState();

    @Unique private CycleButton<Boolean> tailcraft$toggle;
    @Unique private Button tailcraft$generate;
    @Unique private boolean tailcraft$share;
    @Unique private boolean tailcraft$initialShare;
    /** New Address was clicked; the address is replaced on Apply Changes, kept on Cancel. */
    @Unique private boolean tailcraft$newAddress;

    @Inject(method = "multiplayerOptions", at = @At("TAIL"))
    private void tailcraft$addRow(LinearLayout section, IntegratedServer server, CallbackInfo ci,
                                  @Local GridLayout.RowHelper rowHelper) {
        tailcraft$initialShare = LanHost.INSTANCE.isSharing();
        tailcraft$share = tailcraft$initialShare;
        tailcraft$toggle = CycleButton.onOffBuilder(tailcraft$share)
                .withTooltip(value -> Tooltip.create(Component.translatable("tailcraft.lan.tooltip")))
                .create(0, 0, 150, 20, Component.translatable("tailcraft.lan.toggle"), (button, share) -> {
                    tailcraft$share = share;
                    tailcraft$updateGenerate();
                    updateApplyChangesActiveState();
                });
        tailcraft$generate = Button.builder(Component.translatable("tailcraft.lan.new_address"),
                        button -> {
                            tailcraft$newAddress = true;
                            tailcraft$updateGenerate();
                            updateApplyChangesActiveState();
                        })
                .size(150, 20)
                .tooltip(Tooltip.create(Component.translatable("tailcraft.lan.new_address_tooltip")))
                .build();
        rowHelper.addChild(tailcraft$toggle);
        rowHelper.addChild(tailcraft$generate);
        tailcraft$updateGenerate();
    }

    // Count a Tailcraft toggle or a New Address click as a pending change, so Apply Changes un-grays
    // like it does for the other controls.
    @Inject(method = "hasChanges", at = @At("RETURN"), cancellable = true)
    private void tailcraft$hasChanges(CallbackInfoReturnable<Boolean> cir) {
        if (tailcraft$share != tailcraft$initialShare || tailcraft$newAddress) cir.setReturnValue(true);
    }

    // Tailcraft needs the world published to LAN; turning it on enables LAN automatically on apply.
    @Inject(method = "applyChanges", at = @At("HEAD"))
    private void tailcraft$autoEnableLan(IntegratedServer server, CallbackInfo ci) {
        if (tailcraft$share && wantedMultiplayerScope != MinecraftServer.MultiplayerScope.LAN) {
            wantedMultiplayerScope = MinecraftServer.MultiplayerScope.LAN;
        }
    }

    @Inject(method = "applyChanges", at = @At("TAIL"))
    private void tailcraft$apply(IntegratedServer server, CallbackInfo ci) {
        if (tailcraft$share && server.isPublished()) {
            // New Address: drop the current helper so share() starts one with a fresh address.
            if (tailcraft$newAddress) LanHost.INSTANCE.stop();
            LanHost.INSTANCE.share(server);
        } else {
            LanHost.INSTANCE.stop();
        }
        tailcraft$initialShare = tailcraft$share;
        tailcraft$newAddress = false;
        tailcraft$updateGenerate();
    }

    @Unique
    private void tailcraft$updateGenerate() {
        if (tailcraft$generate != null) {
            tailcraft$generate.active = tailcraft$share && !tailcraft$newAddress;
        }
    }
}
