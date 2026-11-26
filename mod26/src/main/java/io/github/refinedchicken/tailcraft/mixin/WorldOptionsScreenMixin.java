package io.github.refinedchicken.tailcraft.mixin;

import io.github.refinedchicken.tailcraft.client.LanHost;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
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

/**
 * Adds a Tailcraft row to the Multiplayer section of the World Options screen (the 26.x
 * replacement for the Open to LAN screen): a "Tailcraft: ON/OFF" toggle and a "New Address" button
 * that is grayed until the toggle is on. Sharing starts/stops in {@code applyChanges}, alongside
 * vanilla's LAN publish, because the helper can only forward once the world is published to LAN.
 */
@Mixin(WorldOptionsScreen.class)
abstract class WorldOptionsScreenMixin {
    @Shadow private MinecraftServer.MultiplayerScope wantedMultiplayerScope;

    @Unique private CycleButton<Boolean> tailcraft$toggle;
    @Unique private Button tailcraft$generate;
    @Unique private boolean tailcraft$share;

    @Inject(method = "multiplayerOptions", at = @At("TAIL"))
    private void tailcraft$addRow(LinearLayout section, IntegratedServer server, CallbackInfo ci) {
        tailcraft$share = LanHost.INSTANCE.isSharing();
        LinearLayout row = LinearLayout.horizontal().spacing(8);
        tailcraft$toggle = CycleButton.onOffBuilder(tailcraft$share)
                .withTooltip(value -> Tooltip.create(Component.translatable("tailcraft.lan.tooltip")))
                .create(0, 0, 150, 20, Component.translatable("tailcraft.lan.toggle"), (button, share) -> {
                    tailcraft$share = share;
                    tailcraft$updateGenerate();
                });
        tailcraft$generate = Button.builder(Component.translatable("tailcraft.lan.new_address"),
                        button -> LanHost.INSTANCE.regenerate())
                .size(150, 20)
                .tooltip(Tooltip.create(Component.translatable("tailcraft.lan.new_address_tooltip")))
                .build();
        row.addChild(tailcraft$toggle);
        row.addChild(tailcraft$generate);
        section.addChild(row);
        tailcraft$updateStates();
    }

    // Vanilla calls this whenever the LAN scope or permissions change; keep our row in step.
    @Inject(method = "updatePermissionDependentButtons", at = @At("TAIL"))
    private void tailcraft$sync(IntegratedServer server, boolean canUseCommands, boolean lanOpen, CallbackInfo ci) {
        tailcraft$updateStates();
    }

    @Inject(method = "applyChanges", at = @At("TAIL"))
    private void tailcraft$apply(IntegratedServer server, CallbackInfo ci) {
        if (tailcraft$share && server.isPublished()) {
            LanHost.INSTANCE.share(server);
        } else {
            LanHost.INSTANCE.stop();
        }
    }

    @Unique
    private void tailcraft$updateStates() {
        if (tailcraft$toggle != null) {
            tailcraft$toggle.active = wantedMultiplayerScope == MinecraftServer.MultiplayerScope.LAN;
        }
        tailcraft$updateGenerate();
    }

    @Unique
    private void tailcraft$updateGenerate() {
        if (tailcraft$generate != null) {
            tailcraft$generate.active = tailcraft$toggle != null && tailcraft$toggle.active && tailcraft$share;
        }
    }
}
