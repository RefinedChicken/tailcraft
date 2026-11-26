package io.github.refinedchicken.tailcraft.compat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.function.Predicate;

/** Minecraft APIs that read differently across versions, isolated here as in the 1.21.x build. */
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
}
