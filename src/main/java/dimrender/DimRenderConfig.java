package dimrender;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Settings, stored at config/dimrender.properties.
 * A dimension with no saved value for a setting is left alone (the game's normal value is used).
 */
public final class DimRenderConfig {
    public static final String OVERWORLD = "minecraft:overworld";
    public static final String NETHER = "minecraft:the_nether";
    public static final String END = "minecraft:the_end";
    private static final List<String> VANILLA = List.of(OVERWORLD, NETHER, END);

    public boolean enabled = true;
    /** When a distance is changed in the game's video settings, save it for the current dimension. */
    public boolean remember = true;
    /** Show a short message above the hotbar when a distance is switched. */
    public boolean notify = true;

    private final Map<Setting, Map<String, Integer>> distances = new EnumMap<>(Setting.class);
    /** Dimensions that have been visited, so modded/server dimensions also show up in the settings. */
    private final Set<String> seen = new LinkedHashSet<>(VANILLA);

    private static DimRenderConfig instance;

    public DimRenderConfig() {
        for (Setting s : Setting.values()) distances.put(s, new LinkedHashMap<>());
    }

    public static DimRenderConfig get() {
        if (instance == null) {
            instance = new DimRenderConfig();
            instance.load();
        }
        return instance;
    }

    public static boolean isVanilla(String dimension) {
        return VANILLA.contains(dimension);
    }

    /** Saved value for a dimension, or null if it should not be changed. */
    public Integer distance(Setting setting, String dimension) {
        return distances.get(setting).get(dimension);
    }

    /** @param chunks a distance, or null to stop managing this setting in this dimension */
    public void setDistance(Setting setting, String dimension, Integer chunks) {
        if (chunks == null) {
            distances.get(setting).remove(dimension);
        } else {
            distances.get(setting).put(dimension, chunks);
        }
        seen.add(dimension);
    }

    /** @return true if the dimension was not known before */
    public boolean markSeen(String dimension) {
        return seen.add(dimension);
    }

    /** Removes a modded or server dimension from the list, with its saved values. Vanilla ones always stay. */
    public void forget(String dimension) {
        if (isVanilla(dimension)) return;
        seen.remove(dimension);
        for (Setting s : Setting.values()) distances.get(s).remove(dimension);
    }

    /** Every option back to its default, every saved distance removed, the list back to the vanilla dimensions. */
    public void resetAll() {
        enabled = true;
        remember = true;
        notify = true;
        for (Map<String, Integer> map : distances.values()) map.clear();
        seen.clear();
        seen.addAll(VANILLA);
    }

    /** Overworld, Nether, End first, then every other dimension that was visited or configured, A–Z. */
    public List<String> dimensions() {
        Set<String> others = new LinkedHashSet<>();
        for (String d : seen) {
            if (!isVanilla(d)) others.add(d);
        }
        for (Map<String, Integer> map : distances.values()) {
            for (String d : map.keySet()) {
                if (!isVanilla(d)) others.add(d);
            }
        }
        List<String> sorted = new ArrayList<>(others);
        sorted.sort(Comparator.naturalOrder());
        List<String> all = new ArrayList<>(VANILLA);
        all.addAll(sorted);
        return all;
    }

    /** Only for tests: config folder to use instead of the game's. */
    static Path testConfigDir;

    private static Path file() {
        Path dir = testConfigDir != null ? testConfigDir : FabricLoader.getInstance().getConfigDir();
        return dir.resolve("dimrender.properties");
    }

    public void load() {
        Path path = file();
        if (!Files.exists(path)) return;
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            p.load(reader);
        } catch (IOException e) {
            DimRenderMod.LOGGER.warn("Could not read {}", path, e);
            return;
        }
        enabled = Boolean.parseBoolean(p.getProperty("enabled", "true").trim());
        remember = Boolean.parseBoolean(p.getProperty("remember", "true").trim());
        notify = Boolean.parseBoolean(p.getProperty("notify", "true").trim());
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith("seen.")) {
                seen.add(key.substring(5));
                continue;
            }
            for (Setting s : Setting.values()) {
                if (!key.startsWith(s.configPrefix)) continue;
                try {
                    int chunks = Integer.parseInt(p.getProperty(key).trim());
                    if (chunks > 0) distances.get(s).put(key.substring(s.configPrefix.length()), chunks);
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("remember", Boolean.toString(remember));
        p.setProperty("notify", Boolean.toString(notify));
        distances.forEach((s, map) -> map.forEach((d, v) -> p.setProperty(s.configPrefix + d, Integer.toString(v))));
        for (String d : seen) {
            if (!isVanilla(d)) p.setProperty("seen." + d, "true");
        }
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                p.store(writer, "Dimension Render Distance");
            }
        } catch (IOException e) {
            DimRenderMod.LOGGER.warn("Could not write {}", path, e);
        }
    }
}
