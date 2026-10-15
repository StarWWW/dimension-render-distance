package dimrender;

import dimrender.client.DimRenderScreen;
import dimrender.compat.Compat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DimRenderMod implements ClientModInitializer {
    public static final String MOD_ID = "dimrender";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final KeyMapping.Category KEY_CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "keybindings"));
    /** Unbound by default so it never clashes with another mod; set it in Controls. */
    public static final KeyMapping SETTINGS_KEY = new KeyMapping("key.dimrender.settings", -1, KEY_CATEGORY);

    public static final DimensionSwitcher SWITCHER = new DimensionSwitcher();
    public static final DimensionSwitcher.Game GAME = new GameAccess();

    @Override
    public void onInitializeClient() {
        DimRenderConfig.get();
        KeyMappingHelper.registerKeyMapping(SETTINGS_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (SETTINGS_KEY.consumeClick()) {
                Compat.setScreen(client, new DimRenderScreen(Compat.screen(client)));
            }
            try {
                String dimension = client.level == null ? null : client.level.dimension().identifier().toString();
                SWITCHER.tick(dimension, GAME, DimRenderConfig.get());
                // the player object can be briefly missing while changing dimension; tell the server once it exists
                if (pendingBroadcast && client.player != null) {
                    pendingBroadcast = false;
                    client.options.broadcastOptions();
                }
            } catch (Throwable t) {
                logOnce(t);
            }
        });
    }

    private static boolean logged;
    private static boolean pendingBroadcast;

    private static void logOnce(Throwable t) {
        if (!logged) {
            logged = true;
            LOGGER.error("Dimension render distance switch failed (further errors are not logged)", t);
        }
    }

    /** Display name of a dimension: translated for the vanilla ones, the id otherwise. */
    public static Component dimensionName(String id) {
        return switch (id) {
            case DimRenderConfig.OVERWORLD -> Component.translatable("dimrender.dimension.overworld");
            case DimRenderConfig.NETHER -> Component.translatable("dimrender.dimension.the_nether");
            case DimRenderConfig.END -> Component.translatable("dimrender.dimension.the_end");
            default -> Component.literal(id.startsWith("minecraft:") ? id.substring(10) : id);
        };
    }

    /** Talks to the real game options. */
    private static final class GameAccess implements DimensionSwitcher.Game {
        private static OptionInstance<Integer> option(Setting setting) {
            var options = Minecraft.getInstance().options;
            return setting == Setting.RENDER ? options.renderDistance() : options.simulationDistance();
        }

        @Override
        public int get(Setting setting) {
            return option(setting).get();
        }

        @Override
        public void set(Setting setting, int chunks) {
            Minecraft mc = Minecraft.getInstance();
            option(setting).set(chunks);
            // the singleplayer server reads both values from the options by itself every tick;
            // a multiplayer server is told about the new render distance right away
            if (setting == Setting.RENDER) {
                if (mc.player != null) {
                    mc.options.broadcastOptions();
                } else {
                    pendingBroadcast = true;
                }
            }
        }

        @Override
        public int min(Setting setting) {
            return option(setting).values() instanceof OptionInstance.IntRange range ? range.minInclusive() : 2;
        }

        @Override
        public int max(Setting setting) {
            return option(setting).values() instanceof OptionInstance.IntRange range ? range.maxInclusive() : 32;
        }

        @Override
        public void announce(String dimension, Integer render, Integer simulation) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            MutableComponent details = Component.empty();
            if (render != null) details.append(Component.translatable("dimrender.switched.render", render));
            if (render != null && simulation != null) details.append(" · ");
            if (simulation != null) details.append(Component.translatable("dimrender.switched.simulation", simulation));
            mc.player.sendOverlayMessage(Component.translatable("dimrender.switched", dimensionName(dimension), details));
        }
    }
}
