package name.vantaclient.client.gui;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.feature.Category;
import name.vantaclient.client.feature.Feature;
import name.vantaclient.client.feature.FeatureRegistry;
import name.vantaclient.client.feature.FarViewFeature;
import name.vantaclient.client.util.FarViewController;
import name.vantaclient.client.util.FarTerrainRenderer;
import name.vantaclient.client.init.Keybinds;
import name.vantaclient.client.init.FeatureKeybinds;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import name.vantaclient.client.gui.components.settings.BooleanSetting;
import name.vantaclient.client.gui.components.settings.ChoiceSetting;
import name.vantaclient.client.gui.components.settings.Setting;
import name.vantaclient.client.gui.components.settings.SettingRegistry;
import name.vantaclient.client.gui.components.settings.SliderSetting;
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
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Locale;

/** The live, retained-mode settings workspace for Vanta Client. */
public final class VantaSettingsScreen extends Screen {
    private enum Page { OVERVIEW, HUD, VISUAL, COMBAT, MISC, APPEARANCE, CONTROLS }

    private final Screen parent;
    private VantaUiRuntime runtime;
    private Page page = Page.OVERVIEW;
    private Feature selectedFeature;
    private String searchQuery = "";
    private String pendingSearch = "";
    private KeyMapping pendingKeyCapture;
    private int capturedKeyCode = GLFW.GLFW_KEY_UNKNOWN;
    private int capturedMouseButton = -1;

    public VantaSettingsScreen(Screen parent) {
        super(Component.literal("Vanta Client Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rebuildUi();
    }

    private void rebuildUi() {
        if (runtime != null) runtime.dispose();
        runtime = new VantaUiRuntime(buildRoot());
        runtime.setScale((float) VantaClientPreferences.get().uiScale);
        runtime.layout(font, width, height);
    }

    void setPageForScreenshot(int targetPage) {
        page = switch (targetPage) {
            case 0 -> Page.OVERVIEW;
            case 1 -> Page.HUD;
            case 2 -> Page.VISUAL;
            case 3 -> Page.COMBAT;
            case 4 -> Page.MISC;
            case 5 -> Page.APPEARANCE;
            case 6 -> Page.VISUAL;
            case 7 -> Page.CONTROLS;
            default -> throw new IllegalArgumentException("Unknown settings page: " + targetPage);
        };
        pendingKeyCapture = null;
        capturedKeyCode = GLFW.GLFW_KEY_UNKNOWN;
        capturedMouseButton = -1;
        Keybinds.setCaptureActive(false);
        selectedFeature = targetPage == 6
                ? FeatureRegistry.FEATURES.stream()
                        .filter(feature -> !SettingRegistry.getSettings(feature).isEmpty())
                        .findFirst().orElse(null)
                : null;
        searchQuery = "";
        pendingSearch = "";
        rebuildUi();
    }

    private VantaUiNode buildRoot() {
        boolean compact = width < 760 || VantaClientPreferences.get().compactMode;
        VantaUiNode root = VantaUiNode.column(10, 12).background(VantaUiTheme.CANVAS);
        VantaUiNode header = VantaUiNode.row(12, 12)
                .height(compact ? 42 : 48)
                .background(VantaUiTheme.PANEL)
                .align(VantaUiNode.Alignment.CENTER);
        VantaUiNode brand = VantaUiNode.row(9, 0).align(VantaUiNode.Alignment.CENTER);
        brand.add(VantaUiNode.image(Identifier.fromNamespaceAndPath(
                "dropperutils", "textures/gui/vanta_logo_transparent.png")).size(34, 24));
        VantaUiNode brandLabels = VantaUiNode.column(1, 0);
        brandLabels.add(VantaUiNode.title("VANTA"));
        brandLabels.add(VantaUiNode.eyebrow("CLIENT SETTINGS"));
        brand.add(brandLabels);
        header.add(brand);
        header.add(VantaUiNode.spacer(1).flex(1));
        if (!compact) {
            header.add(VantaUiNode.dynamicText(() -> Minecraft.getInstance().getFps() + " FPS",
                    VantaUiTheme.TEXT_MUTED));
        } else {
            header.add(VantaUiNode.button("MODS", () -> Minecraft.getInstance().setScreen(
                    new VantaIntegrationsScreen(this))).height(30));
        }
        header.add(VantaUiNode.button("CLOSE", this::onClose)
                .height(30).tooltip("Close settings"));
        root.add(header);

        VantaUiNode body = compact
                ? VantaUiNode.column(8, 0).flex(1)
                : VantaUiNode.row(12, 0).flex(1);
        VantaUiNode navigation;
        if (compact) {
            navigation = VantaUiNode.row(4, 4).height(38).background(VantaUiTheme.PANEL)
                    .justify(VantaUiNode.Justification.CENTER);
            navigation.add(compactNav(Page.OVERVIEW, VantaUiNode.Icon.OVERVIEW));
            navigation.add(compactNav(Page.HUD, VantaUiNode.Icon.HUD));
            navigation.add(compactNav(Page.VISUAL, VantaUiNode.Icon.VISUALS));
            navigation.add(compactNav(Page.COMBAT, VantaUiNode.Icon.CROSSHAIR));
            navigation.add(compactNav(Page.MISC, VantaUiNode.Icon.SPARK));
            navigation.add(compactNav(Page.APPEARANCE, VantaUiNode.Icon.DISPLAY));
            navigation.add(compactNav(Page.CONTROLS, VantaUiNode.Icon.KEYBOARD));
        } else {
            navigation = VantaUiNode.column(5, 10).width(154).background(VantaUiTheme.PANEL);
            navigation.add(VantaUiNode.eyebrow("WORKSPACE"));
            navigation.add(nav("Overview", Page.OVERVIEW, VantaUiNode.Icon.OVERVIEW));
            navigation.add(nav("HUD", Page.HUD, VantaUiNode.Icon.HUD));
            navigation.add(nav("Visual", Page.VISUAL, VantaUiNode.Icon.VISUALS));
            navigation.add(nav("Combat", Page.COMBAT, VantaUiNode.Icon.CROSSHAIR));
            navigation.add(nav("Miscellaneous", Page.MISC, VantaUiNode.Icon.SPARK));
            navigation.add(nav("Appearance", Page.APPEARANCE, VantaUiNode.Icon.DISPLAY));
            navigation.add(nav("Controls", Page.CONTROLS, VantaUiNode.Icon.KEYBOARD));
            navigation.add(VantaUiNode.spacer(1).flex(1));
            navigation.add(VantaUiNode.eyebrow("TOOLS"));
            navigation.add(VantaUiNode.button("Mod library", VantaUiNode.Icon.OVERVIEW,
                    () -> Minecraft.getInstance().setScreen(new VantaIntegrationsScreen(this)))
                    .height(34).tooltip("Open installed mods and integrations"));
            navigation.add(VantaUiNode.text("LOCAL CONFIGURATION", VantaUiTheme.TEXT_MUTED));
        }

        VantaUiNode content = VantaUiNode.column(12, compact ? 12 : 18)
                .flex(1).background(VantaUiTheme.PANEL).scrollable();
        addPageContent(content);
        body.add(navigation);
        body.add(content);
        root.add(body);
        return root;
    }

    private VantaUiNode compactNav(Page target, VantaUiNode.Icon icon) {
        String label = switch (target) {
            case OVERVIEW -> "Overview";
            case HUD -> "HUD";
            case VISUAL -> "Visual";
            case COMBAT -> "Combat";
            case MISC -> "Miscellaneous";
            case APPEARANCE -> "Appearance";
            case CONTROLS -> "Controls";
        };
        return VantaUiNode.button("", icon, () -> {
            page = target;
            selectedFeature = null;
            rebuildUi();
        }).size(42, 32).selected(page == target)
                .tooltip("Open " + label.toLowerCase(Locale.ROOT) + " settings");
    }

    private VantaUiNode nav(String label, Page target, VantaUiNode.Icon icon) {
        return VantaUiNode.button(label, icon, () -> {
            page = target;
            selectedFeature = null;
            rebuildUi();
        }).height(34).selected(page == target)
                .tooltip("Open " + label.toLowerCase(Locale.ROOT) + " settings");
    }

    private void addPageContent(VantaUiNode content) {
        if (selectedFeature != null) {
            addFeatureSettings(content, selectedFeature);
            return;
        }
        switch (page) {
            case OVERVIEW -> addOverview(content);
            case APPEARANCE -> addAppearance(content);
            case CONTROLS -> addControls(content);
            case HUD, VISUAL, COMBAT, MISC -> addFeatureCategory(content);
        }
    }

    private void addOverview(VantaUiNode content) {
        content.add(VantaUiNode.eyebrow("VANTA CLIENT / WORKSPACE"));
        content.add(VantaUiNode.title("Settings"));
        content.add(VantaUiNode.paragraph(
                "A focused control center for your client features, HUD and interface preferences.",
                VantaUiTheme.TEXT_SECONDARY, 2));

        VantaUiNode summary = VantaUiNode.row(10, 14).background(VantaUiTheme.PANEL_RAISED);
        summary.add(metric("FEATURES", () -> Integer.toString(FeatureRegistry.FEATURES.size())));
        summary.add(metric("ENABLED", () -> Long.toString(FeatureRegistry.FEATURES.stream()
                .filter(Feature::isEnabled).count())));
        summary.add(metric("ACCENT", () -> VantaClientPreferences.get().accent.name()));
        content.add(summary);

        content.add(sectionTitle(VantaUiNode.Icon.HUD, "Feature categories",
                "Choose a category to configure its modules"));
        VantaUiNode categories = VantaUiNode.column(4, 8).background(VantaUiTheme.PANEL_RAISED);
        categories.add(categoryShortcut("HUD", "Overlays and on-screen information", Page.HUD,
                VantaUiNode.Icon.HUD));
        categories.add(divider());
        categories.add(categoryShortcut("Visual", "Rendering and visual effects", Page.VISUAL,
                VantaUiNode.Icon.VISUALS));
        categories.add(divider());
        categories.add(categoryShortcut("Combat", "Combat-related utilities", Page.COMBAT,
                VantaUiNode.Icon.CROSSHAIR));
        categories.add(divider());
        categories.add(categoryShortcut("Miscellaneous", "Other client features", Page.MISC,
                VantaUiNode.Icon.SPARK));
        content.add(categories);

        content.add(sectionTitle(VantaUiNode.Icon.DISPLAY, "Interface", "Appearance and accessibility"));
        VantaUiNode appearance = VantaUiNode.row(8, 10).background(VantaUiTheme.PANEL_RAISED)
                .align(VantaUiNode.Alignment.CENTER);
        appearance.add(VantaUiNode.text("Customize colors, spacing and motion", VantaUiTheme.TEXT_SECONDARY)
                .flex(1));
        appearance.add(VantaUiNode.button("Customize", VantaUiNode.Icon.DISPLAY, () -> {
            page = Page.APPEARANCE;
            rebuildUi();
        }));
        content.add(appearance);
    }

    private VantaUiNode metric(String label, java.util.function.Supplier<String> value) {
        VantaUiNode item = VantaUiNode.column(4, 0).flex(1);
        item.add(VantaUiNode.eyebrow(label));
        item.add(VantaUiNode.dynamicText(value, VantaUiTheme.TEXT_PRIMARY));
        return item;
    }

    private VantaUiNode categoryShortcut(String label, String detail, Page target,
                                         VantaUiNode.Icon icon) {
        VantaUiNode row = VantaUiNode.row(10, 5).align(VantaUiNode.Alignment.CENTER);
        row.add(VantaUiNode.icon(icon).iconColor(VantaUiTheme.TEXT_SECONDARY));
        VantaUiNode labels = VantaUiNode.column(2, 0).flex(1);
        labels.add(VantaUiNode.text(label));
        labels.add(VantaUiNode.text(detail, VantaUiTheme.TEXT_MUTED));
        row.add(labels);
        row.add(VantaUiNode.button("Open", () -> {
            page = target;
            rebuildUi();
        }).height(28));
        return row;
    }

    private void addFeatureCategory(VantaUiNode content) {
        if (page == Page.HUD) {
            content.add(VantaUiNode.row(8, 0).align(VantaUiNode.Alignment.CENTER)
                    .add(VantaUiNode.button("Open HUD editor", VantaUiNode.Icon.HUD,
                            () -> Minecraft.getInstance().setScreen(
                                    new name.vantaclient.client.hudeditor.HudEditorScreen(this)))
                            .height(32)));
        }
        Category category = toCategory(page);
        List<Feature> features = FeatureRegistry.FEATURES.stream()
                .filter(feature -> feature.getCategory() == category)
                .filter(feature -> matchesSearch(feature))
                .toList();
        content.add(VantaUiNode.eyebrow("FEATURES / " + category.name()));
        content.add(VantaUiNode.title(categoryTitle(category)));
        content.add(VantaUiNode.paragraph(categoryDescription(category),
                VantaUiTheme.TEXT_SECONDARY, 2));

        VantaUiNode search = VantaUiNode.row(8, 8).height(40)
                .background(VantaUiTheme.PANEL_RAISED).align(VantaUiNode.Alignment.CENTER);
        search.add(VantaUiNode.icon(VantaUiNode.Icon.SEARCH));
        search.add(VantaUiNode.textField("Filter features...", () -> pendingSearch,
                value -> pendingSearch = value, 64).flex(1));
        if (!pendingSearch.isBlank() || !searchQuery.isBlank()) {
            search.add(VantaUiNode.button("Clear", () -> {
                pendingSearch = "";
                searchQuery = "";
                rebuildUi();
            }).height(28).tooltip("Clear the feature filter"));
        }
        search.add(VantaUiNode.button("Apply", VantaUiNode.Icon.SEARCH, () -> {
            searchQuery = pendingSearch.trim().toLowerCase(Locale.ROOT);
            rebuildUi();
        }).height(28));
        content.add(search);
        content.add(VantaUiNode.text("Searches feature names, descriptions and available setting labels.",
                VantaUiTheme.TEXT_MUTED));

        if (features.isEmpty()) {
            String emptyMessage = searchQuery.isBlank()
                    ? "No features are assigned to this category yet."
                    : "No features match this filter.";
            content.add(VantaUiNode.paragraph(emptyMessage, VantaUiTheme.TEXT_MUTED, 1));
            return;
        }
        VantaUiNode list = VantaUiNode.column(1, 8).background(VantaUiTheme.PANEL_RAISED);
        for (Feature feature : features) {
            VantaUiNode row = VantaUiNode.row(10, 7).align(VantaUiNode.Alignment.CENTER);
            VantaUiNode labels = VantaUiNode.column(3, 0).flex(1);
            labels.add(VantaUiNode.text(feature.getName()));
            if (VantaClientPreferences.get().showDescriptions
                    && feature.getDescription() != null && !feature.getDescription().isBlank()) {
                labels.add(VantaUiNode.paragraph(feature.getDescription(),
                        VantaUiTheme.TEXT_MUTED, 2));
            }
            row.add(labels);
            if (!SettingRegistry.getSettings(feature).isEmpty()) {
                row.add(VantaUiNode.button("Options", () -> {
                    selectedFeature = feature;
                    rebuildUi();
                }).height(28).tooltip("Configure " + feature.getName()));
            }
            row.add(VantaUiNode.toggle("", feature::isEnabled, enabled -> {
                if (feature.isEnabled() != enabled) feature.toggle();
                feature.saveConfig();
                DropperUtilsConfig.save();
            }).width(48).tooltip("Enable or disable " + feature.getName()));
            list.add(row);
            list.add(divider());
        }
        content.add(list);
        long totalInCategory = FeatureRegistry.FEATURES.stream()
                .filter(feature -> feature.getCategory() == category).count();
        String resultSummary = searchQuery.isBlank()
                ? totalInCategory + " feature(s) in this category"
                : "Showing " + features.size() + " of " + totalInCategory + " feature(s)";
        content.add(VantaUiNode.text(resultSummary, VantaUiTheme.TEXT_MUTED));
    }

    private boolean matchesSearch(Feature feature) {
        if (searchQuery.isBlank()) return true;
        if (feature.getName().toLowerCase(Locale.ROOT).contains(searchQuery)
                || (feature.getDescription() != null
                && feature.getDescription().toLowerCase(Locale.ROOT).contains(searchQuery))) {
            return true;
        }
        return SettingRegistry.getSettings(feature).stream().anyMatch(setting -> {
            String name = setting instanceof SliderSetting slider ? slider.getName()
                    : setting instanceof BooleanSetting toggle ? toggle.getName()
                    : setting instanceof ChoiceSetting choice ? choice.getName() : "";
            return name.toLowerCase(Locale.ROOT).contains(searchQuery);
        });
    }

    private void addFeatureSettings(VantaUiNode content, Feature feature) {
        content.add(VantaUiNode.button("Back to " + categoryTitle(feature.getCategory()),
                VantaUiNode.Icon.OVERVIEW, () -> {
                    selectedFeature = null;
                    rebuildUi();
                }).height(30));
        content.add(VantaUiNode.eyebrow("FEATURE / " + feature.getCategory().name()));
        content.add(VantaUiNode.title(feature.getName()));
        if (VantaClientPreferences.get().showDescriptions) {
            content.add(VantaUiNode.paragraph(feature.getDescription(),
                    VantaUiTheme.TEXT_SECONDARY, 2));
        }
        if (feature == FarViewFeature.INSTANCE) {
            content.add(VantaUiNode.dynamicText(FarViewController::getRendererStatus,
                    VantaUiTheme.TEXT_MUTED));
            content.add(VantaUiNode.dynamicText(
                    () -> "Vanilla detail distance: " + FarViewController.getEffectiveRenderDistance()
                            + " chunks",
                    VantaUiTheme.TEXT_SECONDARY));
            content.add(VantaUiNode.dynamicText(FarTerrainRenderer::getStatusLine,
                    VantaUiTheme.TEXT_SECONDARY));
        }
        content.add(VantaUiNode.toggle("Enabled", feature::isEnabled, enabled -> {
            if (feature.isEnabled() != enabled) feature.toggle();
            feature.saveConfig();
            DropperUtilsConfig.save();
        }));
        List<Setting> settings = SettingRegistry.getSettings(feature);
        if (settings.isEmpty()) {
            content.add(VantaUiNode.text("This feature has no additional options.",
                    VantaUiTheme.TEXT_MUTED));
            return;
        }
        VantaUiNode panel = VantaUiNode.column(8, 12).background(VantaUiTheme.PANEL_RAISED);
        for (Setting setting : settings) {
            if (setting instanceof SliderSetting slider) {
                panel.add(VantaUiNode.slider(slider.getName(), slider.getMin(), slider.getMax(),
                        Math.max(0.0, (slider.getMax() - slider.getMin()) / 100.0),
                        () -> safeNumber(slider), slider.getSetter()::accept));
            } else if (setting instanceof BooleanSetting toggle) {
                panel.add(VantaUiNode.toggle(toggle.getName(),
                        () -> safeBoolean(toggle), toggle.getSetter()::accept));
            } else if (setting instanceof ChoiceSetting choice) {
                panel.add(VantaUiNode.dropdown(choice.getName(), choice.getOptions(),
                        choice.getGetter(), choice.getSetter()::accept));
            }
        }
        content.add(panel);
    }

    private double safeNumber(SliderSetting setting) {
        try {
            Double value = setting.getGetter().get();
            return value == null || !Double.isFinite(value) ? setting.getMin() : value;
        } catch (RuntimeException exception) {
            return setting.getMin();
        }
    }

    private boolean safeBoolean(BooleanSetting setting) {
        try {
            return Boolean.TRUE.equals(setting.getGetter().get());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void addControls(VantaUiNode content) {
        content.add(VantaUiNode.eyebrow("INPUT / KEYBINDINGS"));
        content.add(VantaUiNode.title("Controls"));
        content.add(VantaUiNode.paragraph(
                "Customize Vanta's built-in shortcuts. Vanilla Minecraft bindings remain in Options → Controls. "
                        + "Choose Change, then press a key or click a mouse button. Escape cancels; Backspace unbinds.",
                VantaUiTheme.TEXT_SECONDARY, 3));

        VantaUiNode panel = VantaUiNode.column(8, 12)
                .background(VantaUiTheme.PANEL_RAISED)
                .padding(12);
        addKeybindRow(panel, "Open Vanta settings",
                "Open the Vanta settings workspace.", Keybinds.guiKey);
        panel.add(divider());
        addKeybindRow(panel, "Fullbright",
                "Toggle the Fullbright feature.", Keybinds.fullbrightKey);
        panel.add(divider());
        addKeybindRow(panel, "Zoom",
                "Hold to zoom the camera.", Keybinds.zoomKey);
        panel.add(divider());
        addKeybindRow(panel, "Toggle sprint",
                "Toggle the Vanta sprint state.", Keybinds.sprintToggleKey);
        panel.add(divider());
        addKeybindRow(panel, "Toggle sneak",
                "Toggle the Vanta sneak state.", Keybinds.sneakToggleKey);
        content.add(panel);

        VantaUiNode featurePanel = VantaUiNode.column(6, 12)
                .background(VantaUiTheme.PANEL_RAISED)
                .padding(12);
        featurePanel.add(VantaUiNode.eyebrow("FEATURE SHORTCUTS"));
        featurePanel.add(VantaUiNode.text(
                "Optional shortcuts that toggle individual Vanta features. They are unbound by default.",
                VantaUiTheme.TEXT_SECONDARY));
        for (FeatureKeybinds.Binding binding : FeatureKeybinds.bindings()) {
            featurePanel.add(divider());
            addKeybindRow(featurePanel, binding.feature().getName(),
                    binding.feature().getDescription(), binding.mapping());
        }
        content.add(featurePanel);

        if (pendingKeyCapture != null) {
            VantaUiNode waiting = VantaUiNode.row(8, 10)
                    .height(42)
                    .background(VantaUiTheme.SURFACE_SELECTED)
                    .align(VantaUiNode.Alignment.CENTER);
            waiting.add(VantaUiNode.dynamicText(
                    () -> "Waiting for input: " + pendingKeyCapture.getTranslatedKeyMessage().getString(),
                    VantaUiTheme.TEXT_PRIMARY).flex(1));
            waiting.add(VantaUiNode.button("Cancel", this::cancelKeyCapture).height(28));
            content.add(waiting);
        }
        content.add(VantaUiNode.text(
                "Feature shortcuts are also available here; all are unbound by default and saved with Minecraft's normal controls.",
                VantaUiTheme.TEXT_MUTED));
    }

    private void addKeybindRow(VantaUiNode panel, String label,
                               String description, KeyMapping mapping) {
        VantaUiNode row = VantaUiNode.row(10, 6)
                .height(42)
                .align(VantaUiNode.Alignment.CENTER);
        VantaUiNode labels = VantaUiNode.column(3, 0).flex(1);
        labels.add(VantaUiNode.text(label));
        labels.add(VantaUiNode.text(description, VantaUiTheme.TEXT_MUTED));
        row.add(labels);
        row.add(VantaUiNode.dynamicText(
                () -> mapping.getTranslatedKeyMessage().getString(),
                VantaUiTheme.TEXT_SECONDARY));
        if (hasBindingConflict(mapping)) {
            row.add(VantaUiNode.text("CONFLICT", 0xFFFFC978)
                    .tooltip("This shortcut is also assigned to another Minecraft or Vanta action."));
        }
        row.add(VantaUiNode.button(pendingKeyCapture == mapping ? "WAITING…" : "Change",
                () -> beginKeyCapture(mapping))
                .size(88, 30).enabled(pendingKeyCapture == null));
        row.add(VantaUiNode.button("Reset", () -> resetKeyMapping(mapping))
                .size(62, 30).enabled(pendingKeyCapture == null));
        panel.add(row);
    }

    boolean hasBindingConflict(KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) return false;
        String binding = mapping.saveString();
        for (KeyMapping candidate : Minecraft.getInstance().options.keyMappings) {
            if (candidate != mapping && !candidate.isUnbound()
                    && candidate.saveString().equals(binding)) {
                return true;
            }
        }
        return false;
    }

    void beginKeyCapture(KeyMapping mapping) {
        if (mapping == null || pendingKeyCapture != null) return;
        pendingKeyCapture = mapping;
        capturedKeyCode = GLFW.GLFW_KEY_UNKNOWN;
        capturedMouseButton = -1;
        Keybinds.setCaptureActive(true);
        rebuildUi();
    }

    private void cancelKeyCapture() {
        pendingKeyCapture = null;
        capturedKeyCode = GLFW.GLFW_KEY_UNKNOWN;
        capturedMouseButton = -1;
        Keybinds.setCaptureActive(false);
        rebuildUi();
    }

    private void completeKeyCapture(InputConstants.Key key, int keyboardKey, int mouseButton) {
        KeyMapping mapping = pendingKeyCapture;
        if (mapping == null || key == null) return;
        mapping.setKey(key);
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        pendingKeyCapture = null;
        capturedKeyCode = keyboardKey;
        capturedMouseButton = mouseButton;
        Keybinds.setCaptureActive(
                keyboardKey != GLFW.GLFW_KEY_UNKNOWN || mouseButton >= 0);
        rebuildUi();
    }

    private void resetKeyMapping(KeyMapping mapping) {
        if (mapping == null || pendingKeyCapture != null) return;
        mapping.setKey(mapping.getDefaultKey());
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
        rebuildUi();
    }

    private void addAppearance(VantaUiNode content) {
        VantaClientPreferences preferences = VantaClientPreferences.get();
        content.add(VantaUiNode.eyebrow("PREFERENCES / INTERFACE"));
        content.add(VantaUiNode.title("Appearance"));
        content.add(VantaUiNode.paragraph(
                "A restrained visual system shared by settings and the HUD editor.",
                VantaUiTheme.TEXT_SECONDARY, 2));

        VantaUiNode panel = VantaUiNode.column(8, 12).background(VantaUiTheme.PANEL_RAISED);
        panel.add(VantaUiNode.dropdown("Accent color",
                List.of("BLUE", "VIOLET", "EMERALD", "CYAN"),
                () -> VantaClientPreferences.get().accent.name(), value -> {
                    VantaClientPreferences.get().accent = VantaClientPreferences.Accent.valueOf(value);
                    VantaClientPreferences.save();
                }));
        panel.add(divider());
        panel.add(VantaUiNode.toggle("Interface animations", () -> VantaClientPreferences.get().animations,
                value -> { VantaClientPreferences.get().animations = value; VantaClientPreferences.save(); }));
        panel.add(divider());
        panel.add(VantaUiNode.toggle("Compact layout", () -> VantaClientPreferences.get().compactMode,
                value -> {
                    VantaClientPreferences.get().compactMode = value;
                    VantaClientPreferences.save();
                    rebuildUi();
                }));
        panel.add(divider());
        panel.add(VantaUiNode.toggle("Setting descriptions", () -> VantaClientPreferences.get().showDescriptions,
                value -> {
                    VantaClientPreferences.get().showDescriptions = value;
                    VantaClientPreferences.save();
                    rebuildUi();
                }));
        panel.add(divider());
        panel.add(VantaUiNode.slider("Interface scale", 0.8, 1.2, 0.05,
                () -> VantaClientPreferences.get().uiScale,
                value -> { VantaClientPreferences.get().uiScale = value; VantaClientPreferences.save(); }));
        panel.add(divider());
        panel.add(VantaUiNode.slider("Panel opacity", 0.72, 0.98, 0.02,
                () -> VantaClientPreferences.get().panelOpacity,
                value -> { VantaClientPreferences.get().panelOpacity = value; VantaClientPreferences.save(); }));
        content.add(panel);
        content.add(VantaUiNode.text("Changes are saved to config/vanta-client.json.",
                VantaUiTheme.TEXT_MUTED));
    }

    private void addPageContentHud(VantaUiNode content) {
        content.add(VantaUiNode.title("HUD"));
    }

    private VantaUiNode sectionTitle(VantaUiNode.Icon icon, String title, String detail) {
        VantaUiNode row = VantaUiNode.row(8, 0).align(VantaUiNode.Alignment.CENTER);
        row.add(VantaUiNode.icon(icon));
        VantaUiNode labels = VantaUiNode.column(2, 0);
        labels.add(VantaUiNode.text(title));
        labels.add(VantaUiNode.text(detail, VantaUiTheme.TEXT_MUTED));
        row.add(labels);
        return row;
    }

    private VantaUiNode divider() {
        return VantaUiNode.spacer(1).height(1).background(VantaUiTheme.BORDER_SUBTLE);
    }

    private static Category toCategory(Page page) {
        return switch (page) {
            case HUD -> Category.HUD;
            case VISUAL -> Category.VISUAL;
            case COMBAT -> Category.COMBAT;
            default -> Category.MISC;
        };
    }

    private static String categoryTitle(Category category) {
        return switch (category) {
            case HUD -> "HUD features";
            case VISUAL -> "Visual features";
            case COMBAT -> "Combat features";
            case MISC -> "Miscellaneous features";
        };
    }

    private static String categoryDescription(Category category) {
        return switch (category) {
            case HUD -> "Configure overlays and information displayed in-game.";
            case VISUAL -> "Control rendering effects and visual adjustments.";
            case COMBAT -> "Configure combat utilities and related behavior.";
            case MISC -> "Additional client features and utilities.";
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (runtime != null) runtime.tick();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, VantaUiTheme.CANVAS);
        if (runtime != null) {
            runtime.setScale((float) VantaClientPreferences.get().uiScale);
            runtime.render(graphics, font, width, height, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (pendingKeyCapture != null) {
            completeKeyCapture(InputConstants.Type.MOUSE.getOrCreate(event.button()),
                    GLFW.GLFW_KEY_UNKNOWN, event.button());
            return true;
        }
        if (runtime != null && runtime.mouseClicked(event.x(), event.y(), event.button(), font)) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (capturedMouseButton == event.button()) {
            capturedMouseButton = -1;
            Keybinds.setCaptureActive(false);
            return true;
        }
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
    public boolean keyPressed(KeyEvent event) {
        if (pendingKeyCapture != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                capturedKeyCode = GLFW.GLFW_KEY_ESCAPE;
                pendingKeyCapture = null;
                Keybinds.setCaptureActive(true);
                rebuildUi();
            } else if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                completeKeyCapture(
                        InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_UNKNOWN),
                        GLFW.GLFW_KEY_BACKSPACE, -1);
            } else {
                completeKeyCapture(InputConstants.getKey(event), event.key(), -1);
            }
            return true;
        }
        boolean shiftDown = (event.modifiers() & 1) != 0;
        if (runtime != null && runtime.keyPressed(event.key(), shiftDown)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (capturedKeyCode == event.key()) {
            capturedKeyCode = GLFW.GLFW_KEY_UNKNOWN;
            Keybinds.setCaptureActive(false);
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (runtime != null && runtime.charTyped(event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public void removed() {
        pendingKeyCapture = null;
        capturedKeyCode = GLFW.GLFW_KEY_UNKNOWN;
        capturedMouseButton = -1;
        Keybinds.setCaptureActive(false);
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
