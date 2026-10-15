package dimrender.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * The few game calls that differ between the Minecraft versions this mod is built for.
 * This copy is for 26.2 (screens are managed by Minecraft.gui, entity types live in EntityTypes); src/mc26.1 has the
 * same class for 26.1. Everything else is shared.
 */
public final class Compat {
    private Compat() {
    }

    /** The screen that is open, or null in game. */
    public static @Nullable Screen screen(Minecraft mc) {
        return mc.gui.screen();
    }

    public static void setScreen(Minecraft mc, @Nullable Screen screen) {
        mc.gui.setScreen(screen);
    }
}
