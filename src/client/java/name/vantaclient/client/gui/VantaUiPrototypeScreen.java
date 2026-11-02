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
 * Isolated in-game validation surface for Vanta's native UI foundation.
 * Demo controls intentionally avoid mutating live gameplay settings.
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
        boolean compact = width < 700;
        int accent = VantaClientPreferences.get().accent.color();
        VantaUiNode root = VantaUiNode.column(compact ? 8 : 12, compact ? 8 : 14)
                .background(VantaUiTheme.CANVAS);

        VantaUiNode header = VantaUiNode.row(10, compact ? 8 : 12)
                .height(compact ? 36 : 42)
                .background(VantaUiTheme.PANEL);
        VantaUiNode brand = VantaUiNode.row(8, 0).width(compact ? 116 : 156)
                .align(VantaUiNode.Alignment.CENTER);
        brand.add(VantaUiNode.icon(VantaUiNode.Icon.BRAND).iconColor(accent));
        VantaUiNode brandText = VantaUiNode.column(1, 0);
        brandText.add(VantaUiNode.eyebrow("VANTA CLIENT"));
        brandText.add(VantaUiNode.text("IN-GAME WORKSPACE", VantaUiTheme.TEXT_SECONDARY));
        brand.add(brandText);
        header.add(brand);
        header.add(VantaUiNode.spacer(1).flex(1));
        if (!compact) {
            VantaUiNode buildMeta = VantaUiNode.row(6, 0).align(VantaUiNode.Alignment.CENTER);
            buildMeta.add(VantaUiNode.icon(VantaUiNode.Icon.DISPLAY));
            buildMeta.add(VantaUiNode.text("FABRIC 1.21.11", VantaUiTheme.TEXT_MUTED));
            header.add(buildMeta);
            VantaUiNode fps = VantaUiNode.row(5, 7).height(26).background(VantaUiTheme.SURFACE)
                    .align(VantaUiNode.Alignment.CENTER);
            fps.add(VantaUiNode.icon(VantaUiNode.Icon.DISPLAY).iconColor(accent));
            fps.add(VantaUiNode.dynamicText(() -> Minecraft.getInstance().getFps() + " FPS",
                    VantaUiTheme.TEXT_PRIMARY));
            header.add(fps);
        }
        header.add(VantaUiNode.button("CLOSE", this::onClose).height(compact ? 28 : 30));
        root.add(header);

        VantaUiNode body = compact
                ? VantaUiNode.column(8, 0).flex(1)
                : VantaUiNode.row(12, 0).flex(1);

        VantaUiNode navigation;
        if (compact) {
            navigation = VantaUiNode.row(5, 4).height(36).background(VantaUiTheme.PANEL);
            navigation.add(navButton("Overview", PAGE_OVERVIEW, VantaUiNode.Icon.OVERVIEW).flex(1));
            navigation.add(navButton("Visuals", PAGE_VISUALS, VantaUiNode.Icon.VISUALS).flex(1));
            navigation.add(navButton("Controls", PAGE_CONTROLS, VantaUiNode.Icon.CONTROLS).flex(1));
        } else {
            navigation = VantaUiNode.column(6, 10).width(150).background(VantaUiTheme.PANEL);
            navigation.add(VantaUiNode.eyebrow("WORKSPACE"));
            navigation.add(navButton("Overview", PAGE_OVERVIEW, VantaUiNode.Icon.OVERVIEW));
            navigation.add(navButton("Visuals", PAGE_VISUALS, VantaUiNode.Icon.VISUALS));
            navigation.add(navButton("Controls", PAGE_CONTROLS, VantaUiNode.Icon.CONTROLS));
            navigation.add(VantaUiNode.spacer(1).height(1).background(VantaUiTheme.BORDER_SUBTLE));
            navigation.add(VantaUiNode.eyebrow("PROFILE"));
            navigation.add(VantaUiNode.text("Local profile", VantaUiTheme.TEXT_SECONDARY));
            navigation.add(VantaUiNode.text("Preview environment", VantaUiTheme.TEXT_MUTED));
            navigation.add(VantaUiNode.spacer(1).flex(1));
            VantaUiNode footer = VantaUiNode.row(6, 0).align(VantaUiNode.Alignment.CENTER);
            footer.add(VantaUiNode.icon(VantaUiNode.Icon.SPARK).iconColor(accent));
            footer.add(VantaUiNode.eyebrow("VANTA / PREVIEW"));
            navigation.add(footer);
            navigation.add(VantaUiNode.text("TAB  Navigate", VantaUiTheme.TEXT_MUTED));
            navigation.add(VantaUiNode.text("ENTER  Select", VantaUiTheme.TEXT_MUTED));
        }

        VantaUiNode content = VantaUiNode.column(14, compact ? 12 : 18)
                .flex(1)
                .background(VantaUiTheme.PANEL)
                .scrollable();
        addPageContent(content);
        body.add(navigation);
        body.add(content);
        root.add(body);
        return root;
    }

    private VantaUiNode navButton(String label, int targetPage, VantaUiNode.Icon icon) {
        return VantaUiNode.button(label, icon, () -> {
            page = targetPage;
            rebuildUi();
        }).height(34)
                .selected(page == targetPage)
                .tooltip("Open the " + label.toLowerCase() + " section.");
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
        addPageHeading(content, "HOME / OVERVIEW", "Your client, at a glance",
                "A focused space for the details you actually use.");

        VantaUiNode hero = VantaUiNode.column(8, 16).background(0xFF1A2732);
        VantaUiNode heroTop = VantaUiNode.row(7, 0).align(VantaUiNode.Alignment.CENTER);
        heroTop.add(VantaUiNode.icon(VantaUiNode.Icon.SPARK).iconColor(accent));
        heroTop.add(VantaUiNode.eyebrow("THE VANTA WORKSPACE"));
        heroTop.add(VantaUiNode.spacer(1).flex(1));
        heroTop.add(VantaUiNode.text("LOCAL PREVIEW", accent));
        hero.add(heroTop);
        hero.add(VantaUiNode.title("Make the game yours."));
        hero.add(VantaUiNode.paragraph(
                "Shape your visual setup and interaction preferences without burying every option in another panel.",
                VantaUiTheme.TEXT_SECONDARY, 2));
        VantaUiNode heroActions = VantaUiNode.row(8, 0);
        heroActions.add(VantaUiNode.button("Explore visuals", VantaUiNode.Icon.VISUALS,
                () -> changePage(PAGE_VISUALS)).flex(1).height(32));
        heroActions.add(VantaUiNode.button("Input settings", VantaUiNode.Icon.KEYBOARD,
                () -> changePage(PAGE_CONTROLS)).flex(1).height(32));
        hero.add(heroActions);
        content.add(hero);

        VantaUiNode metrics = VantaUiNode.row(9, 0).height(64);
        metrics.add(metricCard(VantaUiNode.Icon.DISPLAY, "LIVE PERFORMANCE",
                () -> Integer.toString(Minecraft.getInstance().getFps()), "frames per second", accent).flex(1));
        metrics.add(metricCard(VantaUiNode.Icon.CONTROLS, "UI RENDER COST",
                () -> runtime == null ? "—" : (runtime.averageUiRenderNanos() / 1_000) + " μs",
                "CPU command submission", VantaUiTheme.TEXT_PRIMARY).flex(1));
        content.add(metrics);

        content.add(sectionHeading(VantaUiNode.Icon.CONTROLS, "Quick settings",
                "A few useful preferences"));
        VantaUiNode quick = VantaUiNode.column(2, 10).background(VantaUiTheme.PANEL_RAISED);
        quick.add(VantaUiNode.toggle("Show FPS counter preview", () -> showFps, value -> showFps = value));
        quick.add(divider());
        quick.add(VantaUiNode.toggle("Performance hints", () -> showPerformanceHints,
                value -> showPerformanceHints = value));
        quick.add(divider());
        quick.add(VantaUiNode.toggle("Reduce interface animations",
                () -> !VantaClientPreferences.get().animations, reduced -> {
                    VantaClientPreferences.get().animations = !reduced;
                    VantaClientPreferences.save();
                }));
        content.add(quick);

        content.add(sectionHeading(VantaUiNode.Icon.DISPLAY, "Gameplay preview",
                "Prototype values do not alter Minecraft settings"));
        VantaUiNode preview = VantaUiNode.column(5, 12).background(VantaUiTheme.PANEL_RAISED);
        preview.add(VantaUiNode.slider("Render distance preview", 2, 32, 1,
                () -> renderDistancePreview, value -> renderDistancePreview = value));
        preview.add(divider());
        preview.add(VantaUiNode.dropdown("Update strategy",
                List.of("Balanced", "Low latency", "Power saver"),
                () -> updateStrategy, value -> updateStrategy = value));
        content.add(preview);

        if (showPerformanceHints) {
            content.add(VantaUiNode.paragraph(
                    "PREVIEW MODE  ·  Only the animation preference is connected to Appearance settings. Other controls use isolated demo state.",
                    VantaUiTheme.TEXT_MUTED, 2));
        }
    }

    private VantaUiNode metricCard(VantaUiNode.Icon icon, String label,
                                   java.util.function.Supplier<String> value, String detail, int valueColor) {
        VantaUiNode card = VantaUiNode.column(4, 10).background(VantaUiTheme.PANEL_RAISED);
        VantaUiNode heading = VantaUiNode.row(6, 0).align(VantaUiNode.Alignment.CENTER);
        heading.add(VantaUiNode.icon(icon));
        heading.add(VantaUiNode.eyebrow(label));
        card.add(heading);
        card.add(VantaUiNode.dynamicText(value, valueColor));
        card.add(VantaUiNode.text(detail, VantaUiTheme.TEXT_MUTED));
        return card;
    }

    private void addVisualsPage(VantaUiNode content) {
        int accent = VantaClientPreferences.get().accent.color();
        addPageHeading(content, "APPEARANCE / HUD", "Visual experience",
                "Shape the information on screen and preview how the Vanta HUD should behave.");

        VantaUiNode preview = VantaUiNode.row(14, 14).height(78).background(0xFF1A2732)
                .align(VantaUiNode.Alignment.CENTER);
        VantaUiNode previewIcon = VantaUiNode.icon(VantaUiNode.Icon.CROSSHAIR)
                .size(28, 28).iconColor(accent);
        preview.add(previewIcon);
        VantaUiNode previewText = VantaUiNode.column(4, 0).flex(1);
        previewText.add(VantaUiNode.eyebrow("LIVE PRESET PREVIEW"));
        previewText.add(VantaUiNode.text(crosshairStyle + " crosshair", VantaUiTheme.TEXT_PRIMARY));
        previewText.add(VantaUiNode.text("Scale " + String.format(java.util.Locale.ROOT, "%.2f", crosshairSize),
                VantaUiTheme.TEXT_SECONDARY));
        preview.add(previewText);
        preview.add(VantaUiNode.text(customCrosshair ? "ON" : "OFF", accent));
        content.add(preview);

        content.add(sectionHeading(VantaUiNode.Icon.CROSSHAIR, "Crosshair",
                "Style, preset and scale"));
        VantaUiNode crosshair = VantaUiNode.column(5, 12).background(VantaUiTheme.PANEL_RAISED);
        crosshair.add(VantaUiNode.toggle("Enable custom crosshair", () -> customCrosshair,
                value -> customCrosshair = value));
        crosshair.add(divider());
        crosshair.add(VantaUiNode.dropdown("Preset",
                List.of("Classic", "Dot", "Minimal", "Dynamic", "Circle", "T-Cross", "Outline", "Thin"),
                () -> crosshairStyle, value -> crosshairStyle = value));
        crosshair.add(divider());
        crosshair.add(VantaUiNode.slider("Crosshair scale", 0.5, 2.0, 0.05,
                () -> crosshairSize, value -> crosshairSize = value));
        content.add(crosshair);

        content.add(sectionHeading(VantaUiNode.Icon.HUD, "HUD layout",
                "Alignment and sizing"));
        VantaUiNode hud = VantaUiNode.column(5, 12).background(VantaUiTheme.PANEL_RAISED);
        hud.add(VantaUiNode.toggle("Show alignment grid", () -> hudGrid,
                value -> hudGrid = value));
        hud.add(divider());
        hud.add(VantaUiNode.slider("HUD scale", 0.5, 1.5, 0.05,
                () -> hudScale, value -> hudScale = value));
        hud.add(VantaUiNode.text("GRID  /  " + (hudGrid ? "ENABLED" : "DISABLED"),
                VantaUiTheme.TEXT_MUTED));
        content.add(hud);

        content.add(VantaUiNode.paragraph(
                "The HUD canvas is not implemented in this prototype yet. These controls validate visual settings and component behavior only.",
                VantaUiTheme.TEXT_MUTED, 2));
    }

    private void addControlsPage(VantaUiNode content) {
        addPageHeading(content, "INTERACTION / INPUT", "Controls & behavior",
                "Find settings quickly and define how feature activation should feel.");

        VantaUiNode search = VantaUiNode.row(9, 10).height(48).background(VantaUiTheme.PANEL_RAISED)
                .align(VantaUiNode.Alignment.CENTER);
        search.add(VantaUiNode.icon(VantaUiNode.Icon.SEARCH));
        search.add(VantaUiNode.textField("Search features...", () -> searchQuery,
                value -> searchQuery = value, 64).flex(1));
        content.add(search);
        content.add(VantaUiNode.dynamicText(
                () -> searchQuery.isBlank() ? "BROWSE  /  ALL PREVIEW SETTINGS"
                        : "FILTER QUERY  /  " + searchQuery,
                VantaUiTheme.TEXT_MUTED));

        content.add(sectionHeading(VantaUiNode.Icon.KEYBOARD, "Keybind behavior",
                "How features respond to your input"));
        VantaUiNode input = VantaUiNode.column(5, 12).background(VantaUiTheme.PANEL_RAISED);
        input.add(VantaUiNode.dropdown("Activation mode",
                List.of("Hold", "Toggle", "Press once"),
                () -> keybindMode, value -> keybindMode = value));
        input.add(divider());
        input.add(VantaUiNode.toggle("Show FPS overlay preview", () -> showFps,
                value -> showFps = value));
        input.add(divider());
        input.add(VantaUiNode.slider("Input response", 0.0, 1.0, 0.05,
                () -> crosshairSize / 2.0, value -> crosshairSize = value * 2.0));
        content.add(input);

        VantaUiNode actions = VantaUiNode.row(8, 0);
        actions.add(VantaUiNode.button("Reset demo", VantaUiNode.Icon.RESET,
                this::resetPrototypeState).flex(1).height(34));
        actions.add(VantaUiNode.button("Back to overview", VantaUiNode.Icon.OVERVIEW,
                () -> changePage(PAGE_OVERVIEW)).flex(1).height(34));
        content.add(actions);
        content.add(VantaUiNode.paragraph(
                "KEYBOARD  /  TAB moves focus  ·  ENTER activates  ·  ARROWS adjust values  ·  ESC closes menus",
                VantaUiTheme.TEXT_MUTED, 2));
    }

    private void addPageHeading(VantaUiNode content, String eyebrow, String title, String subtitle) {
        content.add(VantaUiNode.eyebrow(eyebrow));
        content.add(VantaUiNode.title(title));
        content.add(VantaUiNode.paragraph(subtitle, VantaUiTheme.TEXT_SECONDARY, 2));
    }

    private VantaUiNode sectionHeading(VantaUiNode.Icon icon, String title, String subtitle) {
        VantaUiNode heading = VantaUiNode.row(8, 0).align(VantaUiNode.Alignment.CENTER);
        heading.add(VantaUiNode.icon(icon));
        VantaUiNode labels = VantaUiNode.column(2, 0);
        labels.add(VantaUiNode.text(title, VantaUiTheme.TEXT_PRIMARY));
        labels.add(VantaUiNode.text(subtitle, VantaUiTheme.TEXT_MUTED));
        heading.add(labels);
        return heading;
    }

    private VantaUiNode divider() {
        return VantaUiNode.spacer(1).height(1).background(VantaUiTheme.BORDER_SUBTLE);
    }

    private void changePage(int targetPage) {
        page = targetPage;
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
        graphics.fill(0, 0, width, height, 0xD90A0D12);
        if (runtime != null) runtime.render(graphics, font, width, height, mouseX, mouseY);
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
