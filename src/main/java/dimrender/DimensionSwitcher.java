package dimrender;

import java.util.EnumMap;
import java.util.Map;

/**
 * The per-dimension logic, kept free of game classes so it can be tested on its own.
 *
 * Each client tick it is told which dimension the player is in. For the render and the simulation distance alike:
 * <ul>
 *   <li>on entering a dimension with a saved value, that value is applied;</li>
 *   <li>on entering a dimension with no saved value, the current value is kept (and remembered for it,
 *       if "remember" is on);</li>
 *   <li>if the value changes while staying in a dimension (the player moved the slider in Video Settings),
 *       the new value is saved for that dimension, if "remember" is on.</li>
 * </ul>
 */
public final class DimensionSwitcher {
    /** What the switcher needs from the game. */
    public interface Game {
        int get(Setting setting);

        void set(Setting setting, int chunks);

        int min(Setting setting);

        int max(Setting setting);

        /** @param render the new render distance, or null if it did not change; same for simulation */
        void announce(String dimension, Integer render, Integer simulation);
    }

    private String current;
    private final Map<Setting, Integer> lastSeen = new EnumMap<>(Setting.class);

    public String currentDimension() {
        return current;
    }

    public void tick(String dimension, Game game, DimRenderConfig config) {
        if (dimension == null) {
            current = null;
            lastSeen.clear();
            return;
        }
        if (!dimension.equals(current)) {
            current = dimension;
            boolean save = config.markSeen(dimension);
            Map<Setting, Integer> applied = new EnumMap<>(Setting.class);
            if (config.enabled) {
                for (Setting s : Setting.values()) {
                    Integer target = config.distance(s, dimension);
                    if (target != null) {
                        Integer changed = apply(s, target, game);
                        if (changed != null) applied.put(s, changed);
                    } else if (config.remember) {
                        config.setDistance(s, dimension, game.get(s));
                        save = true;
                    }
                }
                if (config.notify && !applied.isEmpty()) {
                    game.announce(dimension, applied.get(Setting.RENDER), applied.get(Setting.SIMULATION));
                }
            }
            if (save) config.save();
            for (Setting s : Setting.values()) lastSeen.put(s, game.get(s));
            return;
        }
        boolean save = false;
        for (Setting s : Setting.values()) {
            int now = game.get(s);
            Integer before = lastSeen.put(s, now);
            if (before != null && before != now && config.enabled && config.remember) {
                config.setDistance(s, dimension, now);
                save = true;
            }
        }
        if (save) config.save();
    }

    /** Re-applies the saved values of the current dimension, e.g. after the settings screen was closed. */
    public void reapply(Game game, DimRenderConfig config) {
        if (current == null) return;
        if (config.enabled) {
            for (Setting s : Setting.values()) {
                Integer target = config.distance(s, current);
                if (target != null) apply(s, target, game);
            }
        }
        for (Setting s : Setting.values()) lastSeen.put(s, game.get(s));
    }

    /** @return the value that was set, or null if it already had that value */
    private static Integer apply(Setting setting, int target, Game game) {
        int clamped = Math.max(game.min(setting), Math.min(game.max(setting), target));
        if (clamped == game.get(setting)) return null;
        game.set(setting, clamped);
        return clamped;
    }
}
