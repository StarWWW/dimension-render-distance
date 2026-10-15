package dimrender.client;

import dimrender.DimRenderConfig;
import dimrender.DimRenderMod;
import dimrender.Setting;
import dimrender.client.ui.SettingsList;
import dimrender.client.ui.SettingsScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * General options, then one row per dimension with a render distance and a simulation distance slider.
 * Scrolls when there are more dimensions than fit (servers can have many).
 */
public class DimRenderScreen extends SettingsScreen {
    private static final WidgetSprites FORGET_SPRITES = new WidgetSprites(
        Identifier.withDefaultNamespace("widget/cross_button"),
        Identifier.withDefaultNamespace("widget/cross_button_highlighted"));

    private final DimRenderConfig config = DimRenderConfig.get();

    public DimRenderScreen(@Nullable Screen parent) {
        super(Component.translatable("dimrender.screen.title"), parent);
    }

    @Override
    protected void buildList(SettingsList list) {
        list.header(Component.translatable("dimrender.section.general"));
        list.row(
            list.checkbox(Component.translatable("dimrender.screen.enabled"), config.enabled, v -> config.enabled = v,
                Component.translatable("dimrender.screen.enabled.tooltip")),
            list.checkbox(Component.translatable("dimrender.screen.notify"), config.notify, v -> config.notify = v,
                Component.translatable("dimrender.screen.notify.tooltip")));
        list.row(list.checkbox(Component.translatable("dimrender.screen.remember"), config.remember,
            v -> config.remember = v, Component.translatable("dimrender.screen.remember.tooltip")));

        list.header(Component.translatable("dimrender.section.dimensions"));
        String current = DimRenderMod.SWITCHER.currentDimension();
        for (String dimension : config.dimensions()) {
            list.add(new DimensionRow(dimension, dimension.equals(current)));
        }
    }

    /** Where the player is now, and what the server allows. */
    @Override
    protected List<Component> infoLines() {
        List<Component> lines = new ArrayList<>();
        Minecraft mc = this.minecraft;
        String current = DimRenderMod.SWITCHER.currentDimension();
        if (current == null || mc.level == null) {
            lines.add(Component.translatable("dimrender.info.not_in_world"));
            return lines;
        }
        int requested = mc.options.renderDistance().get();
        lines.add(Component.translatable("dimrender.info.current", DimRenderMod.dimensionName(current), requested,
            mc.options.simulationDistance().get()));
        int effective = mc.options.getEffectiveRenderDistance();
        if (effective < requested) {
            lines.add(Component.translatable("dimrender.info.server_limit", effective).withStyle(ChatFormatting.GOLD));
        }
        if (!mc.hasSingleplayerServer()) {
            lines.add(Component.translatable("dimrender.info.server_simulation", mc.level.getServerSimulationDistance()));
        }
        return lines;
    }

    @Override
    protected void save() {
        config.save();
    }

    @Override
    protected Component resetLabel() {
        return Component.translatable("dimrender.screen.reset_all");
    }

    @Override
    protected Component resetQuestion() {
        return Component.translatable("dimrender.screen.reset_all.title");
    }

    @Override
    protected Component resetWarning() {
        return Component.translatable("dimrender.screen.reset_all.message");
    }

    @Override
    protected void reset() {
        config.resetAll();
    }

    /** The saved values of the dimension you are in are applied when the screen closes. */
    @Override
    protected void afterClose() {
        DimRenderMod.SWITCHER.reapply(DimRenderMod.GAME, config);
    }

    /**
     * ▶ Nether                         [x]
     * [Render: 8 chunks] [Simulation: 6 chunks]
     */
    private final class DimensionRow extends SettingsList.Row {
        private final String dimension;
        private final boolean current;
        private final DistanceSlider render;
        private final DistanceSlider simulation;
        private final @Nullable ImageButton forget;

        DimensionRow(String dimension, boolean current) {
            this.dimension = dimension;
            this.current = current;
            this.render = new DistanceSlider(dimension, Setting.RENDER);
            this.simulation = new DistanceSlider(dimension, Setting.SIMULATION);
            if (DimRenderConfig.isVanilla(dimension) || current) {
                this.forget = null;
            } else {
                Component tip = Component.translatable("dimrender.screen.forget");
                this.forget = new ImageButton(14, 14, FORGET_SPRITES, b -> {
                    config.forget(dimension);
                    if (list != null) list.rebuild();
                }, tip);
                this.forget.setTooltip(Tooltip.create(tip));
            }
        }

        @Override
        protected int height() {
            return 46;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
            int x = getContentX();
            int y = getContentY();
            MutableComponent name = DimRenderMod.dimensionName(dimension).copy();
            if (current) name = Component.literal("▶ ").append(name).withStyle(ChatFormatting.YELLOW);
            g.text(font, name, x + 1, y + 3, 0xFFFFFFFF);
            if (forget != null) {
                forget.setPosition(getContentRight() - 14, y);
                forget.extractRenderState(g, mouseX, mouseY, a);
            }
            int half = (getContentWidth() - 4) / 2;
            render.setPosition(x, y + 15);
            render.setWidth(half);
            render.extractRenderState(g, mouseX, mouseY, a);
            simulation.setPosition(x + half + 4, y + 15);
            simulation.setWidth(half);
            simulation.extractRenderState(g, mouseX, mouseY, a);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return forget == null ? List.of(render, simulation) : List.of(render, simulation, forget);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return forget == null ? List.of(render, simulation) : List.of(render, simulation, forget);
        }
    }

    /** "Don't change" at the far left, then min..max chunks. */
    private final class DistanceSlider extends AbstractSliderButton {
        private final String dimension;
        private final Setting setting;
        private final int min;
        private final int max;

        DistanceSlider(String dimension, Setting setting) {
            super(0, 0, 150, 20, Component.empty(), 0);
            this.dimension = dimension;
            this.setting = setting;
            this.min = DimRenderMod.GAME.min(setting);
            this.max = DimRenderMod.GAME.max(setting);
            this.value = toSlider(config.distance(setting, dimension));
            updateMessage();
            String tooltip = setting == Setting.RENDER ? "dimrender.screen.render.tooltip" : "dimrender.screen.simulation.tooltip";
            setTooltip(Tooltip.create(Component.translatable(tooltip, min, max)));
        }

        /** Slider positions: 0 = not managed, 1..n = min..max chunks. */
        private double toSlider(@Nullable Integer chunks) {
            if (chunks == null) return 0.0;
            int steps = max - min + 1;
            int index = Math.max(min, Math.min(max, chunks)) - min + 1;
            return index / (double) steps;
        }

        private @Nullable Integer fromSlider() {
            int steps = max - min + 1;
            int index = (int) Math.round(this.value * steps);
            return index <= 0 ? null : min + Math.min(steps, index) - 1;
        }

        @Override
        protected void updateMessage() {
            Integer chunks = fromSlider();
            Component value = chunks == null
                ? Component.translatable("dimrender.screen.unmanaged").withStyle(ChatFormatting.GRAY)
                : Component.translatable("dimrender.screen.chunks", chunks);
            String label = setting == Setting.RENDER ? "dimrender.screen.render" : "dimrender.screen.simulation";
            setMessage(Component.translatable(label, value));
        }

        @Override
        protected void applyValue() {
            config.setDistance(setting, dimension, fromSlider());
        }
    }
}
