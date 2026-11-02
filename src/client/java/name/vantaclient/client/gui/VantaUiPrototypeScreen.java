package name.vantaclient.client.gui;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Isolated in-game validation surface for the Vanta-native UI foundation.
 *
 * Settings other than the live FPS readout intentionally use local prototype
 * state. This prevents an architecture experiment from mutating live game
 * options or user configuration before the control adapters are validated.
 */
public final class VantaUiPrototypeScreen extends Screen {
    private static final int PAGE_OVERVIEW = 0;
    private static final int PAGE_VISUALS = 1;
    private static final int PAGE_CONTROLS = 2;

    private final Screen parent;
    private VantaUiRuntime runtime;
    private int page = PAGE_OVERVIEW;

    private boolean showFps = true;
    private boolean showPerformanceHints = true;
    private boolean customCrosshair;
    private boolean hudGrid = true;
    private String updateStrategy = "Balanced";
    private double renderDistancePreview = 12.0;
    private double crosshairSize = 1.0;
    private double hudScale = 1.0;
    private String crosshairStyle = "Classic";
    private String keybindMode = "Hold";
    private String searchQuery = "";

    public VantaUiPrototypeScreen(Screen parent) {
        super(Component.literal("Vanta UI Prototype"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildUi();
    }

    private void rebuildUi() {
        if (runtime != null) runtime.dispose();
        runtime = new VantaUiRuntime(buildRoot());
        runtime.layout(font, width, height);
    }

    private VantaUiNode buildRoot() {
        boolean compact = width < 760;
        VantaUiNode root = VantaUiNode.column(12, 16)
                .background(VantaUiTheme.CANVAS);

        VantaUiNode header = VantaUiNode.row(12, 10)
                .height(48)
                .background(VantaUiTheme.PANEL);
        header.add(VantaUiNode.text("VANTA  /  UI LAB", VantaUiTheme.TEXT_PRIMARY).width(116));
        header.add(VantaUiNode.spacer(1).flex(1));
        if (!compact) {
            header.add(VantaUiNode.dynamicText(
                    () -> "LIVE FPS  " + Minecraft.getInstance().getFps(),
                    VantaUiTheme.TEXT_SECONDARY).width(92));
            header.add(VantaUiNode.button(
                    "ACCENT  " + VantaClientPreferences.get().accent.name(),
                    this::cycleAccent).width(142)
                    .tooltip("Cycles the same accent preference used by the main Vanta interface."));
        }
        header.add(VantaUiNode.button("CLOSE", this::onClose).width(72));
        root.add(header);

        VantaUiNode body = compact
                ? VantaUiNode.column(10, 0).flex(1)
                : VantaUiNode.row(12, 0).flex(1);

        VantaUiNode navigation;
        if (compact) {
            navigation = VantaUiNode.row(8, 8)
                    .height(44)
                    .background(VantaUiTheme.PANEL);
            int tabWidth = Math.max(60, Math.min(116, (width - 120) / 3));
            navigation.add(navButton("Overview", PAGE_OVERVIEW).width(tabWidth));
            navigation.add(navButton("Visuals", PAGE_VISUALS).width(tabWidth));
            navigation.add(navButton("Controls", PAGE_CONTROLS).width(tabWidth));
        } else {
            navigation = VantaUiNode.column(8, 12)
                    .width(156)
                    .background(VantaUiTheme.PANEL);
            navigation.add(VantaUiNode.text("WORKSPACE", VantaUiTheme.TEXT_MUTED));
            navigation.add(navButton("Overview", PAGE_OVERVIEW));
            navigation.add(navButton("Visuals", PAGE_VISUALS));
            navigation.add(navButton("Controls", PAGE_CONTROLS));
            navigation.add(VantaUiNode.spacer(10));
            navigation.add(VantaUiNode.text("PROTOTYPE", VantaUiTheme.TEXT_MUTED));
            navigation.add(VantaUiNode.text("Native Minecraft renderer", VantaUiTheme.TEXT_SECONDARY));
            navigation.add(VantaUiNode.text("Local demo state", VantaUiTheme.TEXT_MUTED));
            navigation.add(VantaUiNode.spacer(1).flex(1));
            navigation.add(VantaUiNode.text("TAB  Focus", VantaUiTheme.TEXT_MUTED));
            navigation.add(VantaUiNode.text("ENTER  Activate", VantaUiTheme.TEXT_MUTED));
            navigation.add(VantaUiNode.text("ARROWS  Adjust", VantaUiTheme.TEXT_MUTED));
        }

        VantaUiNode content = VantaUiNode.column(12, 18)
                .flex(1)
                .background(VantaUiTheme.PANEL)
                .scrollable();
        addPageContent(content);

        body.add(navigation);
        body.add(content);
        root.add(body);
        return root;
    }

    private VantaUiNode navButton(String label, int targetPage) {
        return VantaUiNode.button(label, () -> {
            page = targetPage;
            rebuildUi();
        }).selected(page == targetPage)
                .tooltip("Open the " + label.toLowerCase() + " prototype section.");
    }

    private void addPageContent(VantaUiNode content) {
        switch (page) {
            case PAGE_VISUALS -> addVisualsPage(content);
            case PAGE_CONTROLS -> addControlsPage(content);
            default -> addOverviewPage(content);
        }
    }

    private void addOverviewPage(VantaUiNode content) {
        content.add(VantaUiNode.text("Overview", VantaUiTheme.TEXT_PRIMARY).height(20));
        content.add(VantaUiNode.paragraph(
                "A real Minecraft screen, rendered through GuiGraphics with one retained component tree.",
                VantaUiTheme.TEXT_SECONDARY, 2));
        content.add(VantaUiNode.spacer(2));
        if (width < 760) {
            content.add(VantaUiNode.button(
                    "Accent: " + VantaClientPreferences.get().accent.name(),
                    this::cycleAccent).width(180)
                    .tooltip("Cycles the global Vanta Appearance accent preference."));
        }

        VantaUiNode metrics = VantaUiNode.row(10, 0).height(76);
        VantaUiNode fpsCard = VantaUiNode.column(7, 12)
                .flex(1).background(VantaUiTheme.PANEL_RAISED);
        fpsCard.add(VantaUiNode.text("FRAME RATE", VantaUiTheme.TEXT_MUTED));
        fpsCard.add(VantaUiNode.dynamicText(
                () -> Minecraft.getInstance().getFps() + " FPS",
                VantaUiTheme.TEXT_PRIMARY));
        fpsCard.add(VantaUiNode.text("Live Minecraft value", VantaUiTheme.TEXT_SECONDARY));

        VantaUiNode stateCard = VantaUiNode.column(7, 12)
                .flex(1).background(VantaUiTheme.PANEL_RAISED);
        stateCard.add(VantaUiNode.text("UI STATE", VantaUiTheme.TEXT_MUTED));
        stateCard.add(VantaUiNode.text("Screen scoped", VantaUiTheme.TEXT_PRIMARY));
        stateCard.add(VantaUiNode.text("Disposed when closed", VantaUiTheme.TEXT_SECONDARY));

        metrics.add(fpsCard);
        metrics.add(stateCard);
        content.add(metrics);

        content.add(VantaUiNode.text("Display options", VantaUiTheme.TEXT_PRIMARY).height(20));
        content.add(VantaUiNode.toggle("FPS display preview", () -> showFps,
                value -> showFps = value)
                .tooltip("Prototype-only toggle. The FPS metric above remains visible for validation."));
        content.add(VantaUiNode.toggle("Performance hints", () -> showPerformanceHints,
                value -> showPerformanceHints = value)
                .tooltip("Prototype-only presentation preference."));
        content.add(VantaUiNode.toggle("Reduced UI animation",
                () -> !VantaClientPreferences.get().animations,
                reduced -> {
                    VantaClientPreferences.get().animations = !reduced;
                    VantaClientPreferences.save();
                }).tooltip("Writes the existing Appearance preference; the framework reads it centrally."));
        content.add(VantaUiNode.slider("Render distance preview", 2, 32, 1,
                () -> renderDistancePreview, value -> renderDistancePreview = value)
                .tooltip("Local prototype value only; it does not change Minecraft video settings."));
        content.add(VantaUiNode.dropdown("Update strategy", List.of("Balanced", "Low latency", "Power saver"),
                () -> updateStrategy, value -> updateStrategy = value)
                .tooltip("Dropdown interaction test. Use click or the left/right arrow keys."));
        if (showPerformanceHints) {
            content.add(VantaUiNode.paragraph(
                    "Prototype note: settings are deliberately isolated from live gameplay configuration.",
                    VantaUiTheme.TEXT_MUTED, 2));
        }
    }

    private void addVisualsPage(VantaUiNode content) {
        content.add(VantaUiNode.text("Visuals", VantaUiTheme.TEXT_PRIMARY).height(20));
        content.add(VantaUiNode.paragraph(
                "Control sizing, crosshair presentation and HUD layout behavior.",
                VantaUiTheme.TEXT_SECONDARY, 2));
        content.add(VantaUiNode.spacer(4));

        content.add(VantaUiNode.toggle("Custom crosshair preview", () -> customCrosshair,
                value -> customCrosshair = value)
                .tooltip("Prototype toggle. A real crosshair adapter can replace this local state later."));
        content.add(VantaUiNode.dropdown("Crosshair preset",
                List.of("Classic", "Dot", "Minimal", "Dynamic", "Circle", "T-Cross", "Outline", "Thin"),
                () -> crosshairStyle, value -> crosshairStyle = value)
                .tooltip("Cycles through available prototype presets."));
        content.add(VantaUiNode.slider("Crosshair scale", 0.5, 2.0, 0.05,
                () -> crosshairSize, value -> crosshairSize = value)
                .tooltip("Tests decimal slider values and step snapping."));
        content.add(VantaUiNode.toggle("HUD alignment grid", () -> hudGrid,
                value -> hudGrid = value)
                .tooltip("Local state for a future HUD editor adapter."));
        content.add(VantaUiNode.slider("HUD scale", 0.5, 1.5, 0.05,
                () -> hudScale, value -> hudScale = value)
                .tooltip("Prototype slider for HUD scaling."));
        content.add(VantaUiNode.text("Preview state", VantaUiTheme.TEXT_PRIMARY).height(18));
        content.add(VantaUiNode.text("Crosshair: " + crosshairStyle + "  ·  " +
                (customCrosshair ? "customized" : "default"), VantaUiTheme.TEXT_SECONDARY));
        content.add(VantaUiNode.text("HUD grid: " + (hudGrid ? "visible" : "hidden"),
                VantaUiTheme.TEXT_SECONDARY));
    }

    private void addControlsPage(VantaUiNode content) {
        content.add(VantaUiNode.text("Controls", VantaUiTheme.TEXT_PRIMARY).height(20));
        content.add(VantaUiNode.paragraph(
                "Keyboard focus, activation, directional adjustment and pointer capture.",
                VantaUiTheme.TEXT_SECONDARY, 2));
        content.add(VantaUiNode.spacer(4));

        content.add(VantaUiNode.textField("Search features...", () -> searchQuery,
                value -> searchQuery = value, 64)
                .tooltip("Tests focused text input, character events, cursor movement and deletion."));
        content.add(VantaUiNode.dynamicText(
                () -> searchQuery.isBlank() ? "No search filter" : "Filter: " + searchQuery,
                VantaUiTheme.TEXT_SECONDARY));
        content.add(VantaUiNode.dropdown("Keybind interaction", List.of("Hold", "Toggle", "Press once"),
                () -> keybindMode, value -> keybindMode = value)
                .tooltip("Prototype-only keybind mode selection."));
        content.add(VantaUiNode.toggle("Show FPS overlay preview", () -> showFps,
                value -> showFps = value));
        content.add(VantaUiNode.slider("Input response", 0.0, 1.0, 0.05,
                () -> crosshairSize / 2.0, value -> crosshairSize = value * 2.0)
                .tooltip("Use arrow keys while focused to adjust in fixed steps."));
        content.add(VantaUiNode.button("Reset prototype state", this::resetPrototypeState)
                .tooltip("Restores only this screen's local demo values."));
        content.add(VantaUiNode.button("Return to overview", () -> {
            page = PAGE_OVERVIEW;
            rebuildUi();
        }));
        content.add(VantaUiNode.paragraph(
                "Input contract: mouse events are routed through the runtime; Tab cycles focus; Enter/Space activate controls; arrows adjust sliders and dropdowns.",
                VantaUiTheme.TEXT_MUTED, 3));
    }

    private void cycleAccent() {
        VantaClientPreferences preferences = VantaClientPreferences.get();
        VantaClientPreferences.Accent[] accents = VantaClientPreferences.Accent.values();
        preferences.accent = accents[(preferences.accent.ordinal() + 1) % accents.length];
        VantaClientPreferences.save();
        rebuildUi();
    }

    private void resetPrototypeState() {
        showFps = true;
        showPerformanceHints = true;
        customCrosshair = false;
        hudGrid = true;
        updateStrategy = "Balanced";
        renderDistancePreview = 12.0;
        crosshairSize = 1.0;
        hudScale = 1.0;
        crosshairStyle = "Classic";
        keybindMode = "Hold";
        searchQuery = "";
        rebuildUi();
    }

    @Override
    public void tick() {
        super.tick();
        if (runtime != null) runtime.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB9080C12);
        if (runtime != null) {
            runtime.render(graphics, font, width, height, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (runtime != null && runtime.mouseClicked(event.x(), event.y(), event.button())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = runtime != null && runtime.mouseReleased(event.button());
        return handled || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (runtime != null && runtime.mouseDragged(event.x(), event.y(), event.button())) return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (runtime != null && runtime.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (runtime != null && runtime.charTyped(event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean shiftDown = (event.modifiers() & 1) != 0;
        if (runtime != null && runtime.keyPressed(event.key(), shiftDown)) return true;
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        if (runtime != null) runtime.dispose();
        super.removed();
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
        else super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
