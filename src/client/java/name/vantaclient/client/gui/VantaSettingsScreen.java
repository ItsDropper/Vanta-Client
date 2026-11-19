package name.vantaclient.client.gui;

import name.vantaclient.client.gui.compose.VantaCompose;
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
public final class VantaSettingsScreen extends Screen implements VantaCompose.Screen {
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
        boolean compact = VantaUiRuntime.designWidth(width, height) < 1040.0f
                || VantaUiRuntime.designHeight(width, height) < 620.0f
                || VantaClientPreferences.get().compactMode;

        VantaUiNode root = VantaUiNode.column(12, 12);

        VantaUiNode header = VantaUiNode.row(12, 14)
                .height(compact ? 42 : 50)
                .background(VantaUiTheme.PANEL)
                .align(VantaUiNode.Alignment.CENTER);
        header.add(VantaUiNode.image(Identifier.fromNamespaceAndPath(
                "dropperutils", "textures/gui/vanta_logo_transparent.png"))
                .size(30, 22));
        VantaUiNode brand = VantaUiNode.column(1, 0);
        brand.add(VantaUiNode.title("VANTA"));
        brand.add(VantaUiNode.eyebrow("CLIENT CONTROL"));
        header.add(brand);
        header.add(VantaUiNode.spacer(1).flex(1));
        if (!compact) {
            header.add(VantaUiNode.row(6, 9).height(28)
                    .background(VantaUiTheme.SURFACE)
                    .align(VantaUiNode.Alignment.CENTER)
                    .add(VantaUiNode.spacer(1).size(6, 6)
                            .background(VantaUiTheme.STATUS_SUCCESS))
                    .add(VantaUiNode.dynamicText(
                            () -> Minecraft.getInstance().getFps() + " FPS",
                            VantaUiTheme.TEXT_SECONDARY)));
            header.add(VantaUiNode.text("LOCAL PROFILE", VantaUiTheme.TEXT_MUTED));
        }
        header.add(VantaUiNode.button("×", this::onClose)
                .size(30, 28).tooltip("Close Vanta settings"));
        root.add(header);

        VantaUiNode body = compact
                ? VantaUiNode.column(8, 0).flex(1)
                : VantaUiNode.row(12, 0).flex(1);
        VantaUiNode navigation;
        if (compact) {
            navigation = VantaUiNode.row(3, 3).height(34)
                    .background(VantaUiTheme.PANEL)
                    .justify(VantaUiNode.Justification.CENTER);
            navigation.add(compactNav(Page.OVERVIEW, VantaUiNode.Icon.OVERVIEW));
            navigation.add(compactNav(Page.HUD, VantaUiNode.Icon.HUD));
            navigation.add(compactNav(Page.VISUAL, VantaUiNode.Icon.VISUALS));
            navigation.add(compactNav(Page.COMBAT, VantaUiNode.Icon.CROSSHAIR));
            navigation.add(compactNav(Page.MISC, VantaUiNode.Icon.SPARK));
            navigation.add(compactNav(Page.APPEARANCE, VantaUiNode.Icon.DISPLAY));
            navigation.add(compactNav(Page.CONTROLS, VantaUiNode.Icon.KEYBOARD));
        } else {
            navigation = VantaCompose.surface(VantaCompose.Material.PANEL, 5, 12)
                    .width(190);
            navigation.add(VantaUiNode.eyebrow("WORKSPACE"));
            navigation.add(nav("Overview", Page.OVERVIEW, VantaUiNode.Icon.OVERVIEW));
            navigation.add(nav("HUD editor", Page.HUD, VantaUiNode.Icon.HUD));
            navigation.add(nav("Visuals", Page.VISUAL, VantaUiNode.Icon.VISUALS));
            navigation.add(nav("Combat", Page.COMBAT, VantaUiNode.Icon.CROSSHAIR));
            navigation.add(nav("Utilities", Page.MISC, VantaUiNode.Icon.SPARK));
            navigation.add(VantaUiNode.spacer(1).flex(1));
            navigation.add(divider());
            navigation.add(VantaUiNode.eyebrow("PREFERENCES"));
            navigation.add(nav("Appearance", Page.APPEARANCE, VantaUiNode.Icon.DISPLAY));
            navigation.add(nav("Keybinds", Page.CONTROLS, VantaUiNode.Icon.KEYBOARD));
            navigation.add(VantaUiNode.spacer(1).flex(1));
            navigation.add(VantaUiNode.button("Mod library", VantaUiNode.Icon.OVERVIEW,
                    () -> Minecraft.getInstance().setScreen(new VantaIntegrationsScreen(this)))
                    .height(36).tooltip("Installed mods and integrations"));
            navigation.add(VantaUiNode.text("Stored on this device", VantaUiTheme.TEXT_MUTED));
        }

        VantaUiNode content = VantaCompose.surface(VantaCompose.Material.PANEL,
                        compact ? 10 : 16, compact ? 12 : 22)
                .flex(1).scrollable();
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
        }).size(38, 28).selected(page == target)
                .tooltip("Open " + label.toLowerCase(Locale.ROOT) + " settings");
    }

    private VantaUiNode nav(String label, Page target, VantaUiNode.Icon icon) {
        return VantaCompose.navigationItem(label, icon, page == target, () -> {
            page = target;
            selectedFeature = null;
            rebuildUi();
        }).tooltip("Open " + label.toLowerCase(Locale.ROOT) + " settings");
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
        boolean compact = VantaUiRuntime.designWidth(width, height) < 1040.0f
                || VantaUiRuntime.designHeight(width, height) < 620.0f;
        long enabledCount = FeatureRegistry.FEATURES.stream()
                .filter(Feature::isEnabled).count();
        int totalCount = FeatureRegistry.FEATURES.size();

        content.add(VantaCompose.sectionHeader("VANTA  /  OVERVIEW", "Control center",
                "Your client, your layout. Jump straight to the systems you use."));

        VantaUiNode overview = VantaUiNode.row(compact ? 0 : 14, 0);
        if (compact) overview = VantaUiNode.column(10, 0);

        VantaUiNode lead = VantaCompose.surface(VantaCompose.Material.ELEVATED, 10,
                        compact ? 12 : 20)
                .flex(compact ? 1 : 2);
        lead.add(VantaUiNode.eyebrow("YOUR CONFIGURATION"));
        lead.add(VantaUiNode.title("Built around the way you play."));
        lead.add(VantaUiNode.paragraph(
                "Shape your HUD, tune rendering and keep the tools you need within reach. "
                        + "Changes are applied locally.",
                VantaUiTheme.TEXT_SECONDARY, 2));
        VantaUiNode actions = VantaUiNode.row(8, 0)
                .align(VantaUiNode.Alignment.CENTER);
        actions.add(VantaUiNode.button("Open HUD editor", VantaUiNode.Icon.HUD, () ->
                Minecraft.getInstance().setScreen(
                        new name.vantaclient.client.hudeditor.HudEditorScreen(this)))
                .height(34).background(VantaUiTheme.SURFACE_SELECTED));
        actions.add(VantaUiNode.button("Browse modules", VantaUiNode.Icon.OVERVIEW, () -> {
            page = Page.HUD;
            selectedFeature = null;
            rebuildUi();
        }).height(34));
        lead.add(actions);
        overview.add(lead);

        VantaUiNode health = VantaCompose.surface(VantaCompose.Material.ELEVATED, 10, 16)
                .flex(1);
        health.add(VantaUiNode.eyebrow("CLIENT SNAPSHOT"));
        VantaUiNode metric = VantaUiNode.row(8, 0)
                .align(VantaUiNode.Alignment.END);
        metric.add(VantaUiNode.title(Long.toString(enabledCount)));
        metric.add(VantaUiNode.text(" / " + totalCount + " active modules",
                VantaUiTheme.TEXT_SECONDARY));
        health.add(metric);
        int activeWidth = totalCount == 0 ? 0
                : (int) Math.round(100.0 * enabledCount / totalCount);
        VantaUiNode track = VantaUiNode.row(0, 0).height(5)
                .background(VantaUiTheme.TRACK);
        track.add(VantaUiNode.spacer(1).flex(Math.max(1, activeWidth))
                .background(VantaClientPreferences.get().accent.color()));
        track.add(VantaUiNode.spacer(1).flex(Math.max(1, 100 - activeWidth))
                .background(VantaUiTheme.TRACK));
        health.add(track);
        health.add(divider());
        health.add(VantaUiNode.eyebrow("INTERFACE ACCENT"));
        health.add(VantaUiNode.row(8, 0).align(VantaUiNode.Alignment.CENTER)
                .add(VantaUiNode.spacer(1).size(10, 10)
                        .background(VantaClientPreferences.get().accent.color()))
                .add(VantaUiNode.dynamicText(
                        () -> VantaClientPreferences.get().accent.name(),
                        VantaUiTheme.TEXT_PRIMARY))
                .add(VantaUiNode.spacer(1).flex(1))
                .add(VantaUiNode.button("Change", () -> {
                    page = Page.APPEARANCE;
                    selectedFeature = null;
                    rebuildUi();
                }).height(27)));
        overview.add(health);
        content.add(overview);

        content.add(VantaCompose.sectionHeader("WORKSPACES", "Choose a system", null));
        VantaUiNode workspaceList = VantaCompose.surface(VantaCompose.Material.ELEVATED, 1, 0);
        addWorkspaceRow(workspaceList, "HUD editor",
                "Position overlays, counters and the vanilla HUD",
                Page.HUD, VantaUiNode.Icon.HUD);
        addWorkspaceRow(workspaceList, "Visual engine",
                "Rendering, lighting and on-screen effects",
                Page.VISUAL, VantaUiNode.Icon.VISUALS);
        addWorkspaceRow(workspaceList, "Combat tools",
                "Combat utilities and their configuration",
                Page.COMBAT, VantaUiNode.Icon.CROSSHAIR);
        addWorkspaceRow(workspaceList, "Utilities",
                "Quality-of-life features and client tools",
                Page.MISC, VantaUiNode.Icon.SPARK);
        content.add(workspaceList);

        content.add(VantaCompose.sectionHeader("QUICK ACCESS", "Enabled modules", null));
        VantaUiNode activePanel = VantaCompose.surface(VantaCompose.Material.ELEVATED, 1, 8);
        List<Feature> activeFeatures = FeatureRegistry.FEATURES.stream()
                .filter(Feature::isEnabled).limit(compact ? 3 : 5).toList();
        if (activeFeatures.isEmpty()) {
            activePanel.add(VantaUiNode.text(
                    "Nothing is enabled yet. Open a workspace to configure your first module.",
                    VantaUiTheme.TEXT_MUTED));
        } else {
            for (Feature feature : activeFeatures) {
                VantaUiNode activeRow = VantaUiNode.row(10, 7)
                        .align(VantaUiNode.Alignment.CENTER);
                VantaUiNode identity = VantaUiNode.column(2, 0).flex(1);
                identity.add(VantaUiNode.text(feature.getName()));
                if (VantaClientPreferences.get().showDescriptions
                        && feature.getDescription() != null && !feature.getDescription().isBlank()) {
                    identity.add(VantaUiNode.paragraph(feature.getDescription(),
                            VantaUiTheme.TEXT_MUTED, 1));
                }
                activeRow.add(identity);
                activeRow.add(VantaUiNode.spacer(1).size(6, 6)
                        .background(VantaUiTheme.STATUS_SUCCESS));
                if (!SettingRegistry.getSettings(feature).isEmpty()) {
                    activeRow.add(VantaUiNode.button("Configure", () -> {
                        selectedFeature = feature;
                        page = switch (feature.getCategory()) {
                            case HUD -> Page.HUD;
                            case VISUAL -> Page.VISUAL;
                            case COMBAT -> Page.COMBAT;
                            case MISC -> Page.MISC;
                        };
                        rebuildUi();
                    }).height(27));
                }
                activeRow.add(VantaUiNode.toggle("", feature::isEnabled, enabled -> {
                    if (feature.isEnabled() != enabled) feature.toggle();
                    feature.saveConfig();
                    DropperUtilsConfig.save();
                    rebuildUi();
                }).width(44).tooltip("Enable or disable " + feature.getName()));
                activePanel.add(activeRow);
                activePanel.add(divider());
            }
        }
        content.add(activePanel);
    }

    private void addWorkspaceRow(VantaUiNode list, String title, String detail,
                                 Page target, VantaUiNode.Icon icon) {
        list.add(VantaCompose.workspaceRow(title, detail, icon, () -> {
            page = target;
            selectedFeature = null;
            rebuildUi();
        }));
        list.add(divider());
    }

    private VantaUiNode categoryCard(String label, String detail, Page target,
                                      VantaUiNode.Icon icon) {
        List<Feature> categoryFeatures = FeatureRegistry.FEATURES.stream()
                .filter(feature -> feature.getCategory() == toCategory(target)).toList();
        VantaUiNode card = VantaUiNode.column(7, 10)
                .flex(1).height(112).background(VantaUiTheme.PANEL_RAISED).padding(10);
        VantaUiNode identity = VantaUiNode.row(8, 0).align(VantaUiNode.Alignment.CENTER);
        identity.add(VantaUiNode.icon(icon).size(18, 18)
                .iconColor(VantaClientPreferences.get().accent.color()));
        identity.add(VantaUiNode.text(label));
        identity.add(VantaUiNode.spacer(1).flex(1));
        identity.add(VantaUiNode.dynamicText(
                () -> categoryFeatures.stream().filter(Feature::isEnabled).count()
                        + " / " + categoryFeatures.size(),
                VantaUiTheme.TEXT_SECONDARY));
        card.add(identity);
        card.add(VantaUiNode.paragraph(detail, VantaUiTheme.TEXT_MUTED, 2));
        card.add(VantaUiNode.button("Open " + label, () -> {
            page = target;
            selectedFeature = null;
            rebuildUi();
        }).height(27));
        return card;
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
        float availableWidth = VantaUiRuntime.designWidth(width, height);
        boolean compact = availableWidth < 760.0f || VantaClientPreferences.get().compactMode;
        Category category = toCategory(page);
        List<Feature> features = FeatureRegistry.FEATURES.stream()
                .filter(feature -> feature.getCategory() == category)
                .filter(this::matchesSearch)
                .toList();
        List<Feature> categoryFeatures = FeatureRegistry.FEATURES.stream()
                .filter(feature -> feature.getCategory() == category)
                .toList();
        VantaUiNode.Icon categoryIcon = switch (category) {
            case HUD -> VantaUiNode.Icon.HUD;
            case VISUAL -> VantaUiNode.Icon.VISUALS;
            case COMBAT -> VantaUiNode.Icon.CROSSHAIR;
            case MISC -> VantaUiNode.Icon.SPARK;
        };
        long enabledCount = categoryFeatures.stream().filter(Feature::isEnabled).count();

        VantaUiNode heading = VantaUiNode.row(12, compact ? 9 : 14)
                .background(VantaUiTheme.PANEL_RAISED)
                .align(VantaUiNode.Alignment.CENTER);
        heading.add(VantaUiNode.icon(categoryIcon).size(28, 28)
                .iconColor(VantaClientPreferences.get().accent.color()));
        VantaUiNode identity = VantaUiNode.column(3, 0).flex(1);
        identity.add(VantaUiNode.eyebrow("FEATURE WORKSPACE"));
        identity.add(VantaUiNode.title(categoryTitle(category)));
        identity.add(VantaUiNode.paragraph(categoryDescription(category),
                VantaUiTheme.TEXT_SECONDARY, compact ? 1 : 2));
        heading.add(identity);
        VantaUiNode count = VantaUiNode.column(2, 0)
                .align(VantaUiNode.Alignment.END);
        count.add(VantaUiNode.dynamicText(
                () -> categoryFeatures.stream().filter(Feature::isEnabled).count() + " enabled",
                VantaUiTheme.TEXT_PRIMARY));
        count.add(VantaUiNode.text(categoryFeatures.size() + " modules",
                VantaUiTheme.TEXT_MUTED));
        heading.add(count);
        if (page == Page.HUD) {
            heading.add(VantaUiNode.button("Edit HUD", VantaUiNode.Icon.HUD,
                    () -> Minecraft.getInstance().setScreen(
                            new name.vantaclient.client.hudeditor.HudEditorScreen(this)))
                    .height(32).background(VantaUiTheme.SURFACE_SELECTED));
        }
        content.add(heading);

        VantaUiNode toolbar = VantaUiNode.row(8, 8)
                .height(compact ? 36 : 40)
                .background(VantaUiTheme.PANEL_RAISED)
                .align(VantaUiNode.Alignment.CENTER);
        toolbar.add(VantaUiNode.icon(VantaUiNode.Icon.SEARCH));
        toolbar.add(VantaUiNode.textField("Search modules and settings…", () -> pendingSearch,
                value -> pendingSearch = value, 64).flex(1));
        if (!pendingSearch.isBlank() || !searchQuery.isBlank()) {
            toolbar.add(VantaUiNode.button("Clear", () -> {
                pendingSearch = "";
                searchQuery = "";
                rebuildUi();
            }).height(28).tooltip("Clear the feature filter"));
        }
        toolbar.add(VantaUiNode.button("Search", VantaUiNode.Icon.SEARCH, () -> {
            searchQuery = pendingSearch.trim().toLowerCase(Locale.ROOT);
            rebuildUi();
        }).height(28));
        content.add(toolbar);

        VantaUiNode resultsHeader = VantaUiNode.row(8, 0)
                .align(VantaUiNode.Alignment.CENTER);
        resultsHeader.add(VantaUiNode.title(searchQuery.isBlank()
                ? "All modules" : "Search results"));
        resultsHeader.add(VantaUiNode.spacer(1).flex(1));
        resultsHeader.add(VantaUiNode.text(
                features.size() + " of " + categoryFeatures.size() + " modules",
                VantaUiTheme.TEXT_MUTED));
        content.add(resultsHeader);

        if (features.isEmpty()) {
            VantaUiNode empty = VantaCompose.surface(VantaCompose.Material.ELEVATED, 8, 18);
            empty.add(VantaUiNode.title(searchQuery.isBlank()
                    ? "Nothing here yet" : "No matching modules"));
            empty.add(VantaUiNode.paragraph(searchQuery.isBlank()
                    ? "This workspace has no registered features."
                    : "Try another name or setting keyword.",
                    VantaUiTheme.TEXT_SECONDARY, 2));
            if (!searchQuery.isBlank()) {
                empty.add(VantaUiNode.button("Clear search", () -> {
                    pendingSearch = "";
                    searchQuery = "";
                    rebuildUi();
                }).height(30));
            }
            content.add(empty);
            return;
        }

        int columns = featureGridColumns(availableWidth);
        int cardHeight = compact ? 142 : 166;
        VantaUiNode grid = VantaUiNode.column(compact ? 8 : 12, 0);
        for (int offset = 0; offset < features.size(); offset += columns) {
            VantaUiNode gridRow = VantaUiNode.row(compact ? 8 : 12, 0)
                    .height(cardHeight);
            int rowEnd = Math.min(features.size(), offset + columns);
            for (int index = offset; index < rowEnd; index++) {
                gridRow.add(featureCard(features.get(index), categoryIcon, compact).flex(1));
            }
            if (rowEnd - offset < columns) {
                gridRow.add(VantaUiNode.spacer(1).flex(columns - (rowEnd - offset)));
            }
            grid.add(gridRow);
        }
        content.add(grid);
        content.add(VantaUiNode.text(
                enabledCount + " enabled · " + (categoryFeatures.size() - enabledCount) + " disabled",
                VantaUiTheme.TEXT_MUTED));
    }

    static int featureGridColumns(float availableWidth) {
        if (availableWidth >= 1480.0f) return 3;
        if (availableWidth >= 900.0f) return 2;
        return 1;
    }

    private VantaUiNode featureCard(Feature feature, VantaUiNode.Icon categoryIcon,
                                    boolean compact) {
        boolean hasSettings = !SettingRegistry.getSettings(feature).isEmpty();
        String description = VantaClientPreferences.get().showDescriptions
                ? feature.getDescription() : null;
        return VantaCompose.moduleCard(
                feature.getName(),
                description,
                categoryIcon,
                () -> feature.isEnabled() ? "ENABLED" : "DISABLED",
                VantaClientPreferences.get().accent.color(),
                compact ? 142 : 166,
                hasSettings,
                () -> {
                    selectedFeature = feature;
                    rebuildUi();
                },
                feature::isEnabled,
                enabled -> {
                    if (feature.isEnabled() != enabled) feature.toggle();
                    feature.saveConfig();
                    DropperUtilsConfig.save();
                });
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
        content.add(VantaUiNode.text(categoryTitle(feature.getCategory()), VantaUiTheme.TEXT_MUTED));
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
        content.add(VantaUiNode.toggle("Enable feature", feature::isEnabled, enabled -> {
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
        content.add(VantaUiNode.text("Input preferences", VantaUiTheme.TEXT_MUTED));
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
        featurePanel.add(VantaUiNode.text("Feature shortcuts", VantaUiTheme.TEXT_MUTED));
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
        content.add(VantaUiNode.text("Interface preferences", VantaUiTheme.TEXT_MUTED));
        content.add(VantaUiNode.title("Appearance"));
        content.add(VantaUiNode.paragraph(
                "A restrained visual system shared by settings and the HUD editor.",
                VantaUiTheme.TEXT_SECONDARY, 2));

        VantaUiNode panel = VantaUiNode.column(8, 12).background(VantaUiTheme.PANEL_RAISED);
        panel.add(VantaUiNode.text("Accent color", VantaUiTheme.TEXT_SECONDARY));
        VantaUiNode accents = VantaUiNode.row(8, 8)
                .height(34)
                .align(VantaUiNode.Alignment.CENTER);
        for (VantaClientPreferences.Accent accent : VantaClientPreferences.Accent.values()) {
            String label = accent.name().charAt(0)
                    + accent.name().substring(1).toLowerCase(Locale.ROOT);
            accents.add(VantaUiNode.button(label, () -> {
                VantaClientPreferences.get().accent = accent;
                VantaClientPreferences.save();
                rebuildUi();
            }).height(30).background(accent.color())
                    .selected(VantaClientPreferences.get().accent == accent)
                    .tooltip("Use " + label.toLowerCase(Locale.ROOT) + " as the interface accent"));
        }
        panel.add(accents);
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
        content.add(VantaUiNode.text("Changes are saved to your local Vanta configuration.",
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
        VantaCompose.renderScrim(graphics, width, height);
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
