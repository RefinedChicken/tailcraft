package io.github.refinedchicken.tailcraft.compat;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.function.Predicate;

/**
 * Minecraft APIs that differ between the supported version ranges. Every folder under
 * mod/versions/ has its own copy of this class with the same methods; this one is for 1.21.1 to 1.21.4.
 */
public final class VersionCompat {
    private VersionCompat() {}

    /** Clicking copies {@code value} to the clipboard; hovering shows {@code hover}. */
    public static Style copyToClipboard(Style style, String value, Text hover) {
        return style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
    }

    /** Command requirement for server owners (permission level 4) and the console. */
    public static Predicate<ServerCommandSource> ownersOnly() {
        return source -> source.hasPermissionLevel(4);
    }
}
