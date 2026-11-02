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

    void setPageForScreenshot(int targetPage) {
        if (targetPage < PAGE_OVERVIEW || targetPage > PAGE_CONTROLS) {
            throw new IllegalArgumentException("Unknown prototype page: " + targetPage);
        }
        page = targetPage;
        rebuildUi();
    }

    private VantaUiNode buildRoot() {
        boolean compact = width < 760;
        int accent = VantaClientPreferences.get().accent.color();
        VantaUiNode root = VantaUiNode.column(10, compact ? 9 : 14)
                .background(0xFF090B10);

        VantaUiNode header = VantaUiNode.row(10, 10)
                .height(42)
                .background(0xFF121722);
        VantaUiNode brand = VantaUiNode.row(7, 0).width(compact ? 100 : 150);
        brand.add(VantaUiNode.text("V", accent).width(14));
        brand.add(VantaUiNode.text("VANTA CLIENT", VantaUiTheme.TEXT_PRIMARY));
        header.add(brand);
        header.add(VantaUiNode.spacer(1).flex(1));
        if (!compact) {
            header.add(VantaUiNode.text("BUILD  /  UI PREVIEW", VantaUiTheme.TEXT_MUTED).width(132));
            header.add(VantaUiNode.dynamicText(
                    () -> Minecraft.getInstance().getFps() + " FPS",
                    VantaUiTheme.TEXT_SECONDARY).width(58));
        }
        header.add(VantaUiNode.button("EXIT", this::onClose).width(54));
        root.add(header);

        VantaUiNode body = compact
                ? VantaUiNode.column(8, 0).flex(1)
                : VantaUiNode.row(10, 0).flex(1);

        VantaUiNode navigation;
        if (compact) {
            navigation = VantaUiNode.row(5, 5).height(36).background(0xFF121722);
            int tabWidth = Math.max(58, (width - 55) / 3);
            navigation.add(navButton("HOME", PAGE_OVERVIEW).width(tabWidth));
            navigation.add(navButton("VISUALS", PAGE_VISUALS).width(tabWidth));
            navigation.add(navButton("INPUT", PAGE_CONTROLS).width(tabWidth));
        } else {
            navigation = VantaUiNode.column(7, 10).width(142).background(0xFF121722);
            navigation.add(VantaUiNode.text("MENU", VantaUiTheme.TEXT_MUTED));
            navigation.add(navButton("Overview", PAGE_OVERVIEW));
            navigation.add(navButton("Visuals", PAGE_VISUALS));
            navigation.add(navButton("Controls", PAGE_CONTROLS));
            navigation.add(VantaUiNode.spacer(8));
            navigation.add(VantaUiNode.text("YOUR CLIENT", VantaUiTheme.TEXT_MUTED));
            navigation.add(VantaUiNode.text("Fabric 1.21.11", VantaUiTheme.TEXT_SECONDARY));
            navigation.add(VantaUiNode.text("Local profile", VantaUiTheme.TEXT_MUTED));
            navigation.add(VantaUiNode.spacer(1).flex(1));
            navigation.add(VantaUiNode.text("VANTA  /  PREVIEW", accent));
            navigation.add(VantaUiNode.text("TAB  Navigate", VantaUiTheme.TEXT_MUTED));
            navigation.add(VantaUiNode.text("ENTER  Select", VantaUiTheme.TEXT_MUTED));
        }

        VantaUiNode content = VantaUiNode.column(11, compact ? 10 : 16)
                .flex(1)
                .background(0xFF141A25)
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
        int accent = VantaClientPreferences.get().accent.color();
        content.add(VantaUiNode.text("YOUR CLIENT", VantaUiTheme.TEXT_MUTED));
        content.add(VantaUiNode.text("Welcome to Vanta.", VantaUiTheme.TEXT_PRIMARY).height(22));
        content.add(VantaUiNode.paragraph(
                "A cleaner, faster control center for your Minecraft experience.",
                VantaUiTheme.TEXT_SECONDARY, 2));

        VantaUiNode hero = VantaUiNode.column(7, 12).background(0xFF1B2433);
        hero.add(VantaUiNode.text("VANTA  /  CLIENT OVERVIEW", accent));
        hero.add(VantaUiNode.text("Make the game yours.", VantaUiTheme.TEXT_PRIMARY).height(20));
        hero.add(VantaUiNode.paragraph(
                "Tune your visual setup, interaction preferences and client behavior from one focused workspace.",
                VantaUiTheme.TEXT_SECONDARY, 3));
        hero.add(VantaUiNode.row(8, 0)
                .add(VantaUiNode.button("EXPLORE VISUALS", () -> { page = PAGE_VISUALS; rebuildUi(); }).flex(1))
                .add(VantaUiNode.button("INPUT SETTINGS", () -> { page = PAGE_CONTROLS; rebuildUi(); }).flex(1)));
        content.add(hero);

        VantaUiNode metrics = VantaUiNode.row(8, 0).height(70);
        VantaUiNode fpsCard = metricCard("LIVE FPS",
                () -> Integer.toString(Minecraft.getInstance().getFps()),
                "CURRENT SESSION", accent);
        VantaUiNode uiCard = metricCard("UI CPU",
                () -> runtime == null ? "—" : (runtime.averageUiRenderNanos() / 1_000) + " μs",
                "COMMAND SUBMISSION", VantaUiTheme.TEXT_PRIMARY);
        metrics.add(fpsCard.flex(1));
        metrics.add(uiCard.flex(1));
        content.add(metrics);

        content.add(sectionHeading("QUICK SETTINGS", "Frequently used client preferences"));
        VantaUiNode quick = VantaUiNode.column(2, 8).background(0xFF171E2A);
        quick.add(VantaUiNode.toggle("Show FPS counter", () -> showFps, value -> showFps = value));
        quick.add(VantaUiNode.toggle("Performance hints", () -> showPerformanceHints,
                value -> showPerformanceHints = value));
        quick.add(VantaUiNode.toggle("Reduce interface animations",
                () -> !VantaClientPreferences.get().animations,
                reduced -> {
                    VantaClientPreferences.get().animations = !reduced;
                    VantaClientPreferences.save();
                }));
        content.add(quick);

        content.add(sectionHeading("GAMEPLAY PREVIEW", "Prototype values do not change live game settings"));
        VantaUiNode settings = VantaUiNode.column(7, 10).background(0xFF171E2A);
        settings.add(VantaUiNode.slider("Render distance preview", 2, 32, 1,
                () -> renderDistancePreview, value -> renderDistancePreview = value));
        settings.add(VantaUiNode.dropdown("Update strategy",
                List.of("Balanced", "Low latency", "Power saver"),
                () -> updateStrategy, value -> updateStrategy = value));
        content.add(settings);

        if (showPerformanceHints) {
            content.add(VantaUiNode.paragraph(
                    "PREVIEW MODE  •  Controls are isolated unless explicitly connected to an Appearance preference.",
                    VantaUiTheme.TEXT_MUTED, 2));
        }
    }

    private VantaUiNode metricCard(String label, java.util.function.Supplier<String> value,
                                   String detail, int valueColor) {
        VantaUiNode card = VantaUiNode.column(4, 9).background(0xFF0B111A);
        card.add(VantaUiNode.text(label, VantaUiTheme.TEXT_MUTED));
        card.add(VantaUiNode.dynamicText(value, valueColor));
        card.add(VantaUiNode.text(detail, VantaUiTheme.TEXT_MUTED));
        return card;
    }

    private VantaUiNode sectionHeading(String title, String subtitle) {
        VantaUiNode heading = VantaUiNode.column(3, 0);
        heading.add(VantaUiNode.text(title, VantaUiTheme.TEXT_PRIMARY));
        heading.add(VantaUiNode.text(subtitle, VantaUiTheme.TEXT_MUTED));
        return heading;
    }

    private void addVisualsPage(VantaUiNode content) {
        int accent = VantaClientPreferences.get().accent.color();
        content.add(VantaUiNode.text("APPEARANCE", VantaUiTheme.TEXT_MUTED));
        content.add(VantaUiNode.text("Visual experience", VantaUiTheme.TEXT_PRIMARY).height(22));
        content.add(VantaUiNode.paragraph(
                "Shape the information on screen. These controls preview the future Vanta HUD editor.",
                VantaUiTheme.TEXT_SECONDARY, 2));

        VantaUiNode crosshair = VantaUiNode.column(7, 10).background(0xFF171E2A);
        crosshair.add(sectionHeading("CROSSHAIR", "Style and scale"));
        crosshair.add(VantaUiNode.toggle("Enable custom crosshair", () -> customCrosshair,
                value -> customCrosshair = value));
        crosshair.add(VantaUiNode.dropdown("Preset",
                List.of("Classic", "Dot", "Minimal", "Dynamic", "Circle", "T-Cross", "Outline", "Thin"),
                () -> crosshairStyle, value -> crosshairStyle = value));
        crosshair.add(VantaUiNode.slider("Crosshair scale", 0.5, 2.0, 0.05,
                () -> crosshairSize, value -> crosshairSize = value));
        crosshair.add(VantaUiNode.text("ACTIVE PRESET  /  " + crosshairStyle.toUpperCase(),
                accent));
        content.add(crosshair);

        VantaUiNode hud = VantaUiNode.column(7, 10).background(0xFF171E2A);
        hud.add(sectionHeading("HUD LAYOUT", "Alignment and sizing"));
        hud.add(VantaUiNode.toggle("Show alignment grid", () -> hudGrid,
                value -> hudGrid = value));
        hud.add(VantaUiNode.slider("HUD scale", 0.5, 1.5, 0.05,
                () -> hudScale, value -> hudScale = value));
        hud.add(VantaUiNode.text("GRID  /  " + (hudGrid ? "ENABLED" : "DISABLED"),
                VantaUiTheme.TEXT_SECONDARY));
        content.add(hud);

        content.add(VantaUiNode.paragraph(
                "The current prototype validates controls and layout only; it does not yet draw a live HUD canvas.",
                VantaUiTheme.TEXT_MUTED, 2));
    }

    private void addControlsPage(VantaUiNode content) {
        content.add(VantaUiNode.text("INTERACTION", VantaUiTheme.TEXT_MUTED));
        content.add(VantaUiNode.text("Controls & behavior", VantaUiTheme.TEXT_PRIMARY).height(22));
        content.add(VantaUiNode.paragraph(
                "Search, configure input behavior and validate keyboard navigation.",
                VantaUiTheme.TEXT_SECONDARY, 2));

        VantaUiNode search = VantaUiNode.column(5, 10).background(0xFF171E2A);
        search.add(sectionHeading("FEATURE SEARCH", "Prototype search field"));
        search.add(VantaUiNode.textField("Search features...", () -> searchQuery,
                value -> searchQuery = value, 64));
        search.add(VantaUiNode.dynamicText(
                () -> searchQuery.isBlank() ? "No active filter" : "FILTER  /  " + searchQuery,
                VantaUiTheme.TEXT_SECONDARY));
        content.add(search);

        VantaUiNode input = VantaUiNode.column(7, 10).background(0xFF171E2A);
        input.add(sectionHeading("KEYBIND BEHAVIOR", "How feature activation should work"));
        input.add(VantaUiNode.dropdown("Activation mode",
                List.of("Hold", "Toggle", "Press once"),
                () -> keybindMode, value -> keybindMode = value));
        input.add(VantaUiNode.toggle("Show FPS overlay preview", () -> showFps,
                value -> showFps = value));
        input.add(VantaUiNode.slider("Input response", 0.0, 1.0, 0.05,
                () -> crosshairSize / 2.0, value -> crosshairSize = value * 2.0));
        content.add(input);

        VantaUiNode actions = VantaUiNode.row(7, 0);
        actions.add(VantaUiNode.button("RESET DEMO", this::resetPrototypeState).flex(1));
        actions.add(VantaUiNode.button("BACK TO HOME", () -> {
            page = PAGE_OVERVIEW;
            rebuildUi();
        }).flex(1));
        content.add(actions);
        content.add(VantaUiNode.paragraph(
                "KEYBOARD  /  TAB to move focus  •  ENTER to activate  •  ARROWS to adjust  •  ESC to close menus",
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
        if (runtime != null && runtime.mouseClicked(event.x(), event.y(), event.button(), font)) return true;
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
