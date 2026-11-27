package io.github.refinedchicken.tailcraft.mixin;

import io.github.refinedchicken.tailcraft.client.LanHost;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.OpenToLanScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the shared copy for 1.19 to 1.19.2, where the screen has no port field (the toggle goes
 * under the game mode and cheats buttons instead) and tooltips are lists of text lines.
 *
 * Adds a "Tailcraft: ON/OFF" toggle. The toggle arms LanHost; leaving the
 * screen disarms it, so a later /publish doesn't share by surprise.
 */
@Mixin(OpenToLanScreen.class)
abstract class OpenToLanScreenMixin extends Screen {
    @Unique private boolean tailcraft$share;

    private OpenToLanScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void tailcraft$addToggle(CallbackInfo ci) {
        LanHost.INSTANCE.setShareOnOpen(tailcraft$share);
        addDrawableChild(CyclingButtonWidget.onOffBuilder(tailcraft$share)
                .tooltip(value -> MinecraftClient.getInstance().textRenderer.wrapLines(Text.translatable("tailcraft.lan.tooltip"), 200))
                .build(width / 2 - 75, 130, 150, 20, Text.translatable("tailcraft.lan.toggle"), (button, share) -> {
                    tailcraft$share = share;
                    LanHost.INSTANCE.setShareOnOpen(share);
                }));
    }

    /**
     * Overrides Screen.removed(): OpenToLanScreen doesn't declare it in any supported version
     * (and declares close() only from 1.20.5), so this runs on Start, Cancel and Escape alike.
     * Start calls setScreen(null), which lands here, before openToLan in the same click, so the
     * disarm waits for the next client tick; openToLan has consumed the armed toggle by then.
     */
    @Override
    public void removed() {
        super.removed();
        LanHost.INSTANCE.disarmAtNextTick();
    }
}
