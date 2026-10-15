package dimrender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static dimrender.Setting.RENDER;
import static dimrender.Setting.SIMULATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionSwitcherTest {
    private static final String OW = DimRenderConfig.OVERWORLD;
    private static final String NE = DimRenderConfig.NETHER;
    private static final String EN = DimRenderConfig.END;

    /** Stand-in for the game: two distance values and their limits. */
    static final class FakeGame implements DimensionSwitcher.Game {
        final Map<Setting, Integer> values = new EnumMap<>(Map.of(RENDER, 12, SIMULATION, 10));
        int maxRender = 32;
        final List<String> messages = new ArrayList<>();

        public int get(Setting s) { return values.get(s); }
        public void set(Setting s, int chunks) { values.put(s, chunks); }
        public int min(Setting s) { return s == RENDER ? 2 : 5; }
        public int max(Setting s) { return s == RENDER ? maxRender : 32; }
        public void announce(String dimension, Integer render, Integer simulation) {
            messages.add(dimension + " render=" + render + " sim=" + simulation);
        }

        int render() { return values.get(RENDER); }
        int sim() { return values.get(SIMULATION); }
    }

    @TempDir
    Path configDir;

    DimRenderConfig config;
    DimensionSwitcher switcher;
    FakeGame game;

    @BeforeEach
    void setUp() {
        DimRenderConfig.testConfigDir = configDir;
        config = new DimRenderConfig();
        switcher = new DimensionSwitcher();
        game = new FakeGame();
    }

    @Test
    void remembersAndAppliesPerDimension() {
        switcher.tick(OW, game, config);
        assertEquals(12, config.distance(RENDER, OW), "first visit remembers the current value");
        assertEquals(10, config.distance(SIMULATION, OW));

        switcher.tick(NE, game, config);
        assertEquals(12, game.render(), "an unvisited dimension keeps the current distance");

        game.set(RENDER, 6);
        switcher.tick(NE, game, config);
        assertEquals(6, config.distance(RENDER, NE), "a manual change is saved for the current dimension");
        assertEquals(10, config.distance(SIMULATION, NE));

        switcher.tick(OW, game, config);
        assertEquals(12, game.render());
        assertEquals("minecraft:overworld render=12 sim=null", game.messages.getLast());

        switcher.tick(NE, game, config);
        assertEquals(6, game.render());

        config.setDistance(RENDER, EN, 4);
        switcher.tick(EN, game, config);
        assertEquals(4, game.render());
    }

    @Test
    void simulationDistanceIsSeparatePerDimension() {
        config.setDistance(SIMULATION, OW, 12);
        config.setDistance(SIMULATION, NE, 6);
        switcher.tick(OW, game, config);
        assertEquals(12, game.sim());
        switcher.tick(NE, game, config);
        assertEquals(6, game.sim());
        assertEquals("minecraft:the_nether render=null sim=6", game.messages.getLast());

        game.set(SIMULATION, 8);
        switcher.tick(NE, game, config);
        assertEquals(8, config.distance(SIMULATION, NE), "a manual change is saved");
        assertEquals(12, config.distance(SIMULATION, OW));

        config.setDistance(RENDER, OW, 20);
        switcher.tick(OW, game, config);
        assertEquals("minecraft:overworld render=20 sim=12", game.messages.getLast(), "one message for both");
    }

    @Test
    void rememberOffDoesNotSaveManualChanges() {
        switcher.tick(OW, game, config);
        config.setDistance(RENDER, NE, 6);
        config.remember = false;
        game.set(RENDER, 20);
        game.set(SIMULATION, 20);
        switcher.tick(OW, game, config);
        assertEquals(12, config.distance(RENDER, OW));
        assertEquals(10, config.distance(SIMULATION, OW));
        switcher.tick(NE, game, config);
        switcher.tick(OW, game, config);
        assertEquals(12, game.render());
        assertNull(config.distance(SIMULATION, NE), "not remembered for a new dimension either");
    }

    @Test
    void clampsToTheAllowedRange() {
        config.setDistance(RENDER, OW, 40);
        config.setDistance(SIMULATION, OW, 2);
        switcher.tick(OW, game, config);
        assertEquals(32, game.render());
        assertEquals(5, game.sim());
        game.maxRender = 16; // low-memory setups only allow 16
        switcher.tick(NE, game, config);
        switcher.tick(OW, game, config);
        assertEquals(16, game.render());
    }

    @Test
    void disabledChangesNothing() {
        config.setDistance(RENDER, NE, 4);
        config.setDistance(SIMULATION, NE, 5);
        config.enabled = false;
        switcher.tick(NE, game, config);
        assertEquals(12, game.render());
        assertEquals(10, game.sim());
        game.set(RENDER, 3);
        switcher.tick(NE, game, config);
        assertEquals(4, config.distance(RENDER, NE), "manual changes are not saved while disabled");
    }

    @Test
    void noMessageWhenNothingChangesOrWhenTurnedOff() {
        config.setDistance(RENDER, OW, 12);
        config.setDistance(SIMULATION, OW, 10);
        switcher.tick(OW, game, config);
        assertTrue(game.messages.isEmpty(), "already at the saved values");
        config.setDistance(RENDER, NE, 4);
        config.notify = false;
        switcher.tick(NE, game, config);
        assertEquals(4, game.render());
        assertTrue(game.messages.isEmpty());
    }

    @Test
    void rejoiningReappliesAndReapplyIsNotAManualChange() {
        config.setDistance(RENDER, NE, 6);
        switcher.tick(NE, game, config);
        switcher.tick(null, game, config); // back in the menu
        game.set(RENDER, 30);
        switcher.tick(NE, game, config);
        assertEquals(6, game.render());

        config.setDistance(RENDER, NE, 10);
        config.setDistance(SIMULATION, NE, 7);
        int messages = game.messages.size();
        switcher.reapply(game, config);
        switcher.tick(NE, game, config);
        assertEquals(10, game.render());
        assertEquals(7, game.sim());
        assertEquals(10, config.distance(RENDER, NE));
        assertEquals(messages, game.messages.size(), "no message when applying from the settings screen");
    }

    @Test
    void forgetRemovesOnlyNonVanillaDimensions() {
        switcher.tick("donut:spawn_world", game, config);
        assertTrue(config.dimensions().contains("donut:spawn_world"));
        config.forget("donut:spawn_world");
        config.forget(NE);
        assertFalse(config.dimensions().contains("donut:spawn_world"));
        assertNull(config.distance(RENDER, "donut:spawn_world"));
        assertTrue(config.dimensions().contains(NE));
    }

    @Test
    void resetAllClearsEverything() {
        config.setDistance(RENDER, NE, 6);
        config.setDistance(SIMULATION, OW, 8);
        config.markSeen("donut:spawn_world");
        config.enabled = false;
        config.notify = false;
        config.resetAll();
        assertTrue(config.enabled);
        assertTrue(config.notify);
        assertNull(config.distance(RENDER, NE));
        assertNull(config.distance(SIMULATION, OW));
        assertEquals(List.of(OW, NE, EN), config.dimensions());
    }

    @Test
    void savesAndLoadsIncludingServerDimensions() {
        config.setDistance(RENDER, OW, 12);
        config.setDistance(RENDER, NE, 10);
        config.setDistance(SIMULATION, NE, 6);
        config.remember = false;
        switcher.tick("donut:spawn_world", game, config);
        config.save();

        DimRenderConfig loaded = new DimRenderConfig();
        loaded.load();
        assertEquals(10, loaded.distance(RENDER, NE));
        assertEquals(12, loaded.distance(RENDER, OW));
        assertEquals(6, loaded.distance(SIMULATION, NE));
        assertNull(loaded.distance(SIMULATION, OW));
        assertFalse(loaded.remember);
        assertTrue(loaded.dimensions().contains("donut:spawn_world"), "a visited dimension is saved even without values");
        assertEquals(List.of(OW, NE, EN), loaded.dimensions().subList(0, 3));
    }
}
