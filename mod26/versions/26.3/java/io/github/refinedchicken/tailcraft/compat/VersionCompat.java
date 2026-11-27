package io.github.refinedchicken.tailcraft.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.function.Predicate;

/**
 * Minecraft APIs that differ between the supported 26.x ranges. Every folder under mod26/versions/
 * has its own copy of this class with the same methods; this one is for 26.3.
 */
public final class VersionCompat {
    private VersionCompat() {}

    /** Clicking copies {@code value} to the clipboard; hovering shows {@code hover}. */
    public static Style copyToClipboard(Style style, String value, Component hover) {
        return style.withClickEvent(new ClickEvent.CopyToClipboard(value))
                .withHoverEvent(new HoverEvent.ShowText(hover));
    }

    /** Command requirement for server owners (permission level 4) and the console. */
    public static Predicate<CommandSourceStack> ownersOnly() {
        return Commands.hasPermission(Commands.LEVEL_OWNERS);
    }

    /** The client's toast manager (on Gui from 26.2, on Minecraft before). */
    public static ToastManager toastManager(Minecraft client) {
        return client.gui.toastManager();
    }
}
