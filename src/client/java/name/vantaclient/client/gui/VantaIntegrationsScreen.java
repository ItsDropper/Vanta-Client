package name.vantaclient.client.gui;

import name.vantaclient.api.ModDescriptor;
import name.vantaclient.api.VantaClientApi;
import name.vantaclient.api.setting.SettingDefinition;
import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.feature.Category;
import name.vantaclient.client.feature.Feature;
import name.vantaclient.client.feature.FeatureRegistry;
import name.vantaclient.client.integration.ModConfigDiscovery;
import name.vantaclient.client.integration.ExperimentalModHotLoader;
import name.vantaclient.client.gui.components.VantaPanelButton;
import name.vantaclient.client.gui.components.VantaToggleSwitch;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiRuntime;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarFile;

/** Unified Vanta Client settings and installed-mod browser. */
public class VantaIntegrationsScreen extends Screen {
    private static final int SIDEBAR_WIDTH = 190;
    private static final int ROW_HEIGHT = 38;
    private static final int CONTENT_TOP = 118;

    private enum Section { SETTINGS, MODS }
    private enum Selection { NONE, BUILTIN_CATEGORY, BUILTIN_SETTINGS, INSTALLED_MOD }

    private final Screen parent;
    private final List<ExperimentalModHotLoader.AvailableMod> previewMods;
    private VantaUiRuntime runtime;
    private Section section = Section.MODS;
    private Selection selection = Selection.NONE;
    private Category selectedCategory = Category.HUD;
    private Feature selectedFeature;
    private String selectedModId;
    private List<ModContainer> installedMods = List.of();
    private List<ExperimentalModHotLoader.AvailableMod> availableMods = List.of();
    private final Map<String, Identifier> modIconTextures = new HashMap<>();
    private Map<String, Object> discoveredConfigFactories = Map.of();
    private List<ModDescriptor> integrations = List.of();
    private int sidebarScroll;
    private int firstSetting;
    private float openProgress;
    private EditBox modSearch;
    private String modSearchQuery = "";
    private boolean favoritesOnly;
    private boolean studioOpen;
    private boolean modListInitialized;
    private final Map<String, Float> modCardHover = new HashMap<>();
    private VantaClientPreferences preferences;
    private String hotLoadStatus = "Experimental best-effort execution. Only try JARs you trust; failed initializers may leave changes until Minecraft restarts.";

    public VantaIntegrationsScreen(Screen parent) {
        this(parent, null);
    }

    VantaIntegrationsScreen(Screen parent,
                            List<ExperimentalModHotLoader.AvailableMod> previewMods) {
        super(Component.literal("Vanta Client"));
        this.parent = parent;
        this.previewMods = previewMods == null ? null : List.copyOf(previewMods);
    }

    @Override
    protected void init() {
        if (runtime != null) runtime.dispose();
        runtime = null;
        preferences = VantaClientPreferences.get();
        openProgress = preferences.animations ? 0.0f : 1.0f;
        if (previewMods != null && !modListInitialized) {
            availableMods = previewMods;
            modListInitialized = true;
        } else if (!modListInitialized) {
            refreshModList();
            modListInitialized = true;
        } else if (modIconTextures.isEmpty() && !availableMods.isEmpty()) {
            // Returning from a child screen reinitializes this screen after removed() released GPU textures.
            for (ExperimentalModHotLoader.AvailableMod mod : availableMods) {
                loadModIcon(mod.id(), mod.path(), mod.iconPath());
            }
        }
        if (section == Section.MODS
                && (selection == Selection.NONE || selection == Selection.INSTALLED_MOD)) {
            rebuildRetainedUi();
            return;
        }
        if (section == Section.MODS && selection == Selection.NONE) {
            modSearch = new EditBox(font, VantaPanelButton.layoutX(650),
                    VantaPanelButton.layoutY(25), VantaPanelButton.layoutSize(190),
                    VantaPanelButton.layoutSize(24), Component.literal("Search mods"));
            modSearch.setMaxLength(64);
            modSearch.setBordered(false);
            modSearch.setTextColor(0xFFE0E6F0);
            modSearch.setHint(Component.literal("Search mods..."));
            modSearch.setValue(modSearchQuery);
            modSearch.setResponder(value -> {
                modSearchQuery = value;
                sidebarScroll = 0;
                rebuildWidgets();
                if (modSearch != null) modSearch.setFocused(true);
            });
            addRenderableWidget(modSearch);
        }

        addPanelButton("Settings", 34, 132, SIDEBAR_WIDTH - 24, 25,
                () -> minecraft.setScreen(new VantaSettingsScreen(this)),
                () -> section == Section.SETTINGS);
        addPanelButton("Mods", 34, 78, SIDEBAR_WIDTH - 24, 25,
                () -> {
                    section = Section.MODS;
                    selection = Selection.NONE;
                    sidebarScroll = 0;
                    rebuildWidgets();
                }, () -> section == Section.MODS);

        if (section == Section.MODS) {
            addModNavigation();
            if (selection != Selection.NONE) {
                addPanelButton("‹", contentX() - 28, 22, 22, 24, () -> {
                    selection = Selection.NONE;
                    selectedFeature = null;
                    selectedModId = null;
                    firstSetting = 0;
                    rebuildWidgets();
                });
            }
            if (selection == Selection.NONE) {
                int filterY = CONTENT_TOP + 42;
                addPanelButton("All", contentX(), filterY, 44, 26, () -> {
                    favoritesOnly = false;
                    selection = Selection.NONE;
                    sidebarScroll = 0;
                    rebuildWidgets();
                }, () -> !favoritesOnly && selection == Selection.NONE);
                addPanelButton("Favorites", contentX() + 50, filterY, 80, 26, () -> {
                    favoritesOnly = true;
                    selection = Selection.NONE;
                    sidebarScroll = 0;
                    rebuildWidgets();
                }, () -> favoritesOnly && selection == Selection.NONE);
                addPanelButton("Scan mods", contentX() + contentWidth() - 154,
                        filterY, 154, 26, () -> {
                    ExperimentalModHotLoader.Result result = ExperimentalModHotLoader.scanAndLoad();
                    hotLoadStatus = result.summary();
                    notifyHotLoadResult(result);
                    List<ExperimentalModHotLoader.HotLoadedMod> hotLoaded =
                            ExperimentalModHotLoader.getHotLoadedMods();
                    if (!hotLoaded.isEmpty()) {
                        String names = hotLoaded.stream()
                                .map(mod -> mod.name() + (mod.version().isBlank()
                                        ? "" : " " + mod.version()))
                                .limit(5)
                                .collect(java.util.stream.Collectors.joining(", "));
                        hotLoadStatus += " Session mods: " + names + ".";
                    }
                    refreshModList();
                    rebuildWidgets();
                });
                addInstalledModGrid();
            }
            if (selection == Selection.BUILTIN_CATEGORY) {
                addBuiltInFeatures();
            } else if (selection == Selection.BUILTIN_SETTINGS) {
                addBuiltInFeatureSettings();
            } else if (selection == Selection.INSTALLED_MOD) {
                addInstalledModContent();
            }
        } else {
            addClientSettings();
        }
        if (section == Section.MODS) {
            addPanelButton("Studio", 846, 25, 56, 24, () -> {
                studioOpen = !studioOpen;
                rebuildWidgets();
            }, () -> studioOpen);
            if (studioOpen) addStudioControls();
        }
        addPanelButton("×", 906, 25, 24, 24, this::onClose);
    }

    private void addStudioControls() {
        int x = 744;
        addPanelButton("Opacity −", x + 10, 108, 78, 22, () -> {
            preferences.panelOpacity = Math.max(0.85, Math.round((preferences.panelOpacity - 0.04) * 100.0) / 100.0);
            VantaClientPreferences.save();
            rebuildWidgets();
        });
        addPanelButton("Opacity +", x + 96, 108, 78, 22, () -> {
            preferences.panelOpacity = Math.min(0.98, Math.round((preferences.panelOpacity + 0.04) * 100.0) / 100.0);
            VantaClientPreferences.save();
            rebuildWidgets();
        });
        addPanelButton("Scale −", x + 10, 138, 78, 22, () -> {
            preferences.uiScale = Math.max(0.8, Math.round((preferences.uiScale - 0.05) * 100.0) / 100.0);
            VantaClientPreferences.save();
            rebuildWidgets();
        });
        addPanelButton("Scale +", x + 96, 138, 78, 22, () -> {
            preferences.uiScale = Math.min(1.2, Math.round((preferences.uiScale + 0.05) * 100.0) / 100.0);
            VantaClientPreferences.save();
            rebuildWidgets();
        });
        addPanelButton(preferences.animations ? "Animations: On" : "Animations: Off",
                x + 10, 168, 164, 22, () -> {
                    preferences.animations = !preferences.animations;
                    VantaClientPreferences.save();
                    rebuildWidgets();
                });
        addPanelButton("Close studio", x + 10, 198, 164, 22, () -> {
            studioOpen = false;
            rebuildWidgets();
        });
    }

    private void rebuildRetainedUi() {
        if (runtime != null) runtime.dispose();
        runtime = new VantaUiRuntime(buildRetainedRoot());
        runtime.setScale((float) preferences.uiScale);
        runtime.layout(font, width, height);
    }

    private VantaUiNode buildRetainedRoot() {
        boolean compact = width < 760;
        VantaUiNode root = VantaUiNode.column(12, 14)
                .background(VantaUiTheme.CANVAS)
                .clipToBounds();

        VantaUiNode header = VantaUiNode.row(10, 0)
                .height(52)
                .background(VantaUiTheme.PANEL)
                .padding(10)
                .align(VantaUiNode.Alignment.CENTER);
        VantaUiNode identity = VantaUiNode.column(2, 0);
        identity.add(VantaUiNode.eyebrow("VANTA CLIENT / MOD LIBRARY"));
        identity.add(VantaUiNode.title(selection == Selection.INSTALLED_MOD
                ? selectedModTitle() : "Installed mods"));
        header.add(identity.flex(1));
        if (!compact) {
            header.add(VantaUiNode.button("Modrinth", VantaUiNode.Icon.SEARCH,
                    () -> minecraft.setScreen(new VantaModrinthBrowserScreen(this)))
                    .size(112, 32));
            header.add(VantaUiNode.button("HUD editor", VantaUiNode.Icon.HUD,
                    () -> minecraft.setScreen(new name.vantaclient.client.hudeditor.HudEditorScreen(this)))
                    .size(104, 32));
            header.add(VantaUiNode.button("Settings", VantaUiNode.Icon.DISPLAY,
                    () -> minecraft.setScreen(new VantaSettingsScreen(this)))
                    .size(92, 32));
        } else {
            header.add(VantaUiNode.button("Modrinth", VantaUiNode.Icon.SEARCH,
                    () -> minecraft.setScreen(new VantaModrinthBrowserScreen(this)))
                    .size(90, 30));
            header.add(VantaUiNode.button("HUD", VantaUiNode.Icon.HUD,
                    () -> minecraft.setScreen(new name.vantaclient.client.hudeditor.HudEditorScreen(this)))
                    .size(54, 30));
        }
        header.add(VantaUiNode.button("Close", this::onClose).size(compact ? 48 : 62, 32));
        root.add(header);

        if (selection == Selection.INSTALLED_MOD) {
            root.add(buildRetainedModDetails());
            return root;
        }

        VantaUiNode toolbar = VantaUiNode.row(7, 0)
                .height(38)
                .align(VantaUiNode.Alignment.CENTER);
        toolbar.add(VantaUiNode.textField("Search installed mods…",
                () -> modSearchQuery, value -> modSearchQuery = value, 64)
                .flex(1).minSize(70, 32));
        toolbar.add(VantaUiNode.button("Search", VantaUiNode.Icon.SEARCH,
                this::rebuildRetainedUi).size(66, 32));
        toolbar.add(VantaUiNode.button("All", () -> {
            favoritesOnly = false;
            sidebarScroll = 0;
            rebuildRetainedUi();
        }).size(46, 32).selected(!favoritesOnly));
        toolbar.add(VantaUiNode.button("★", () -> {
            favoritesOnly = true;
            sidebarScroll = 0;
            rebuildRetainedUi();
        }).size(42, 32).selected(favoritesOnly));
        toolbar.add(VantaUiNode.button("Scan", this::scanAllMods).size(54, 32));
        root.add(toolbar);

        VantaUiNode content = VantaUiNode.column(8, 10)
                .flex(1)
                .background(VantaUiTheme.PANEL)
                .padding(10);
        VantaUiNode heading = VantaUiNode.row(8, 0)
                .height(24)
                .align(VantaUiNode.Alignment.CENTER);
        List<ExperimentalModHotLoader.AvailableMod> mods = filteredMods();
        heading.add(VantaUiNode.eyebrow(favoritesOnly ? "FAVORITE MODS" : "MODS IN THIS INSTANCE"));
        heading.add(VantaUiNode.spacer(1).flex(1));
        heading.add(VantaUiNode.text(mods.size() + " / " + availableMods.size(),
                VantaUiTheme.TEXT_MUTED));
        content.add(heading);

        VantaUiNode cards = VantaUiNode.column(8, 8)
                .flex(1)
                .background(VantaUiTheme.INPUT_SURFACE)
                .padding(8)
                .scrollable();
        if (mods.isEmpty()) {
            VantaUiNode empty = VantaUiNode.column(8, 12)
                    .height(140)
                    .background(VantaUiTheme.PANEL_RAISED)
                    .align(VantaUiNode.Alignment.CENTER);
            empty.add(VantaUiNode.icon(VantaUiNode.Icon.OVERVIEW).iconColor(VantaUiTheme.TEXT_MUTED));
            empty.add(VantaUiNode.title(favoritesOnly ? "No favorite mods yet" : "Your mod library is empty"));
            empty.add(VantaUiNode.paragraph(favoritesOnly
                            ? "Mark a mod with the star to keep it in this list."
                            : "Add Fabric mod JARs to this instance, then scan the mods folder.",
                    VantaUiTheme.TEXT_MUTED, 2));
            cards.add(empty);
        } else {
            addRetainedModCards(cards, mods, compact ? 1 : 3);
        }
        content.add(cards);
        content.add(VantaUiNode.dynamicText(
                () -> trim(hotLoadStatus, compact ? 66 : 108),
                VantaUiTheme.TEXT_MUTED).height(26));
        root.add(content);
        return root;
    }

    private void addRetainedModCards(VantaUiNode cards,
                                     List<ExperimentalModHotLoader.AvailableMod> mods,
                                     int columns) {
        Set<String> hotLoadedIds = new HashSet<>();
        ExperimentalModHotLoader.getHotLoadedMods().forEach(mod -> hotLoadedIds.add(mod.id()));
        for (int index = 0; index < mods.size(); index += columns) {
            VantaUiNode row = VantaUiNode.row(8, 0)
                    .height(132)
                    .align(VantaUiNode.Alignment.STRETCH);
            for (int column = 0; column < columns && index + column < mods.size(); column++) {
                row.add(buildRetainedModCard(mods.get(index + column), hotLoadedIds));
            }
            if (columns > 1 && mods.size() - index < columns) {
                for (int missing = mods.size() - index; missing < columns; missing++) {
                    row.add(VantaUiNode.spacer(1).flex(1));
                }
            }
            cards.add(row);
        }
    }

    private VantaUiNode buildRetainedModCard(ExperimentalModHotLoader.AvailableMod mod,
                                              Set<String> hotLoadedIds) {
        boolean fabricLoaded = FabricLoader.getInstance().isModLoaded(mod.id());
        boolean sessionLoaded = hotLoadedIds.contains(mod.id());
        VantaUiNode card = VantaUiNode.column(6, 9)
                .flex(1)
                .height(132)
                .background(VantaUiTheme.PANEL_RAISED)
                .padding(9);
        VantaUiNode top = VantaUiNode.row(8, 0)
                .height(38)
                .align(VantaUiNode.Alignment.CENTER);
        String initial = mod.name().isBlank() ? "?"
                : mod.name().substring(0, 1).toUpperCase(Locale.ROOT);
        top.add(VantaUiNode.dynamicImage(() -> modIconTextures.get(mod.id()), initial)
                .size(34, 34)
                .background(VantaUiTheme.SURFACE_SELECTED)
                .align(VantaUiNode.Alignment.CENTER));
        VantaUiNode labels = VantaUiNode.column(3, 0).flex(1);
        labels.add(VantaUiNode.text(trim(mod.name(), 30), VantaUiTheme.TEXT_PRIMARY));
        labels.add(VantaUiNode.text(trim(mod.id() + "  ·  " + mod.version(), 34),
                VantaUiTheme.TEXT_MUTED));
        top.add(labels);
        card.add(top);
        card.add(VantaUiNode.text(fabricLoaded ? "Enabled by Fabric"
                        : sessionLoaded ? "Session-loaded · experimental"
                        : "Installed · restart to load",
                fabricLoaded ? 0xFF78D6A0
                        : sessionLoaded ? 0xFFFFC978 : VantaUiTheme.TEXT_SECONDARY));
        VantaUiNode actions = VantaUiNode.row(6, 0)
                .height(28)
                .align(VantaUiNode.Alignment.CENTER);
        actions.add(VantaUiNode.button(preferences.favoriteMods.contains(mod.id()) ? "★" : "☆",
                () -> toggleFavorite(mod.id())).size(30, 28));
        actions.add(VantaUiNode.button("Details", () -> selectInstalledMod(mod.id()))
                .size(62, 28));
        if (!fabricLoaded && !sessionLoaded) {
            actions.add(VantaUiNode.button("Hotload", () -> hotLoadMod(mod.id()))
                    .size(68, 28));
        } else {
            actions.add(VantaUiNode.text("ACTIVE", VantaUiTheme.TEXT_MUTED).flex(1));
        }
        card.add(actions);
        return card;
    }

    private VantaUiNode buildRetainedModDetails() {
        ExperimentalModHotLoader.AvailableMod mod = availableMods.stream()
                .filter(candidate -> candidate.id().equals(selectedModId))
                .findFirst().orElse(null);
        VantaUiNode details = VantaUiNode.column(10, 14)
                .flex(1)
                .background(VantaUiTheme.PANEL)
                .padding(14)
                .scrollable();
        if (mod == null) {
            details.add(VantaUiNode.title("Mod not found"));
            details.add(VantaUiNode.text(
                    "The mod JAR may have been removed or the folder was rescanned.",
                    VantaUiTheme.TEXT_MUTED));
        } else {
            String initial = mod.name().isBlank() ? "?"
                    : mod.name().substring(0, 1).toUpperCase(Locale.ROOT);
            VantaUiNode identity = VantaUiNode.row(12, 0)
                    .height(64)
                    .align(VantaUiNode.Alignment.CENTER);
            identity.add(VantaUiNode.dynamicImage(() -> modIconTextures.get(mod.id()), initial)
                    .size(56, 56)
                    .background(VantaUiTheme.SURFACE_SELECTED)
                    .align(VantaUiNode.Alignment.CENTER));
            VantaUiNode labels = VantaUiNode.column(4, 0).flex(1);
            labels.add(VantaUiNode.title(mod.name()));
            labels.add(VantaUiNode.text(mod.id() + "  ·  " + mod.version(),
                    VantaUiTheme.TEXT_MUTED));
            identity.add(labels);
            details.add(identity);

            ModDescriptor descriptor = integrations.stream()
                    .filter(candidate -> candidate.id().equals(mod.id()))
                    .findFirst().orElse(null);
            Object configFactory = discoveredConfigFactories.get(mod.id());
            if (descriptor != null) {
                details.add(VantaUiNode.button("Open Vanta integration settings",
                        VantaUiNode.Icon.DISPLAY,
                        () -> minecraft.setScreen(new VantaIntegrationSettingsScreen(this, descriptor)))
                        .height(34));
            } else if (configFactory != null) {
                details.add(VantaUiNode.button("Open mod settings",
                        VantaUiNode.Icon.DISPLAY, () -> {
                            Screen configScreen = ModConfigDiscovery.open(configFactory, this);
                            if (configScreen != null) minecraft.setScreen(configScreen);
                        }).height(34));
            } else if (FabricLoader.getInstance().isModLoaded("modmenu")) {
                details.add(VantaUiNode.button("Open Mod Menu",
                        VantaUiNode.Icon.DISPLAY, this::openModMenu).height(34));
            } else {
                details.add(VantaUiNode.paragraph(
                        "This mod does not expose a settings screen that Vanta can safely open. "
                                + "Install Mod Menu to discover supported configuration screens.",
                        VantaUiTheme.TEXT_MUTED, 3));
            }
            details.add(VantaUiNode.paragraph(
                    "JAR: " + mod.path().getFileName()
                            + ". Mods loaded by Fabric cannot be safely unloaded at runtime. "
                            + "Session hotloading is experimental and restart is the recovery path.",
                    VantaUiTheme.TEXT_MUTED, 3));
        }
        details.add(VantaUiNode.button("Back to mod library", () -> {
            selection = Selection.NONE;
            selectedModId = null;
            rebuildWidgets();
        }).height(34));
        return details;
    }

    private String selectedModTitle() {
        return availableMods.stream().filter(mod -> mod.id().equals(selectedModId))
                .map(ExperimentalModHotLoader.AvailableMod::name)
                .findFirst().orElse("Mod details");
    }

    private static String trim(String value, int maximumLength) {
        if (value == null || maximumLength <= 0) return "";
        if (value.length() <= maximumLength) return value;
        if (maximumLength == 1) return "…";
        return value.substring(0, maximumLength - 1) + "…";
    }

    private void toggleFavorite(String id) {
        if (!preferences.favoriteMods.add(id)) preferences.favoriteMods.remove(id);
        VantaClientPreferences.save();
        rebuildRetainedUi();
    }

    private void hotLoadMod(String id) {
        ExperimentalModHotLoader.Result result = ExperimentalModHotLoader.scanAndLoad(id);
        hotLoadStatus = result.summary();
        notifyHotLoadResult(result);
        refreshModList();
        rebuildRetainedUi();
    }

    private void scanAllMods() {
        ExperimentalModHotLoader.Result result = ExperimentalModHotLoader.scanAndLoad();
        hotLoadStatus = result.summary();
        notifyHotLoadResult(result);
        List<ExperimentalModHotLoader.HotLoadedMod> hotLoaded =
                ExperimentalModHotLoader.getHotLoadedMods();
        if (!hotLoaded.isEmpty()) {
            hotLoadStatus += " Session mods: " + hotLoaded.stream()
                    .map(mod -> mod.name() + (mod.version().isBlank() ? "" : " " + mod.version()))
                    .limit(5).collect(java.util.stream.Collectors.joining(", ")) + ".";
        }
        refreshModList();
        rebuildRetainedUi();
    }

    private List<ExperimentalModHotLoader.AvailableMod> filteredMods() {
        String query = modSearchQuery == null ? "" : modSearchQuery.trim().toLowerCase(Locale.ROOT);
        return availableMods.stream().filter(mod -> {
            if (favoritesOnly && !preferences.favoriteMods.contains(mod.id())) return false;
            if (query.isEmpty()) return true;
            return mod.name().toLowerCase(Locale.ROOT).contains(query)
                    || mod.id().toLowerCase(Locale.ROOT).contains(query);
        }).toList();
    }

    private void refreshModList() {
        integrations = VantaClientApi.getRegisteredMods();
        discoveredConfigFactories = ModConfigDiscovery.discover();
        installedMods = FabricLoader.getInstance().getAllMods().stream()
                .filter(mod -> {
                    String id = mod.getMetadata().getId();
                    return !id.equals("minecraft")
                            && !id.equals("fabricloader")
                            && !id.equals("dropperutils")
                            && !id.equals("fabric-api")
                            && !id.startsWith("fabric-");
                })
                .sorted(Comparator.comparing(mod -> mod.getMetadata().getName(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
        availableMods = ExperimentalModHotLoader.getAvailableMods();
        for (Identifier texture : modIconTextures.values()) {
            minecraft.getTextureManager().release(texture);
        }
        modIconTextures.clear();
        for (ExperimentalModHotLoader.AvailableMod mod : availableMods) {
            loadModIcon(mod.id(), mod.path(), mod.iconPath());
        }
    }

    private void loadModIcon(String modId, Path jarPath, String iconPath) {
        if (iconPath == null || iconPath.isBlank() || iconPath.startsWith("/")
                || iconPath.contains("..") || iconPath.contains("\\")) return;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getJarEntry(iconPath);
            if (entry == null || entry.getSize() <= 0 || entry.getSize() > 1024 * 1024) return;
            byte[] bytes;
            try (InputStream input = jar.getInputStream(entry)) {
                bytes = input.readNBytes(1024 * 1024 + 1);
            }
            if (bytes.length < 24 || bytes.length > 1024 * 1024
                    || bytes[0] != (byte) 0x89 || bytes[1] != 0x50
                    || bytes[2] != 0x4E || bytes[3] != 0x47
                    || bytes[4] != 0x0D || bytes[5] != 0x0A
                    || bytes[6] != 0x1A || bytes[7] != 0x0A) return;
            int imageWidth = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16)
                    | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
            int imageHeight = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16)
                    | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);
            if (imageWidth <= 0 || imageHeight <= 0 || imageWidth > 512 || imageHeight > 512) return;
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            if (image.getWidth() > 512 || image.getHeight() > 512
                    || image.getWidth() <= 0 || image.getHeight() <= 0) {
                image.close();
                return;
            }
            Identifier texture = Identifier.fromNamespaceAndPath("vantaclient", "mod_icons/" + modId);
            minecraft.getTextureManager().register(texture,
                    new DynamicTexture(() -> "Vanta mod icon " + modId, image));
            modIconTextures.put(modId, texture);
        } catch (Exception | LinkageError ignored) {
            // Missing, malformed or oversized optional icons fall back to the mod initial.
        }
    }

    private void addModNavigation() {
        addPanelButton("Browse Modrinth", 34, 184, SIDEBAR_WIDTH - 24, 25, () -> {
            minecraft.setScreen(new VantaModrinthBrowserScreen(this));
        });
        addPanelButton("HUD Editor", 34, 222, SIDEBAR_WIDTH - 24, 25, () -> {
            minecraft.setScreen(new name.vantaclient.client.hudeditor.HudEditorScreen(this));
        });
    }

    private void addInstalledModGrid() {
        List<ExperimentalModHotLoader.AvailableMod> mods = filteredMods();
        int columns = 4;
        int gap = 10;
        int cardWidth = Math.max(112, (contentWidth() - gap * (columns - 1)) / columns);
        int cardHeight = 120;
        int startY = CONTENT_TOP + 88;
        int visibleRows = Math.max(1, (504 - startY - 24) / (cardHeight + gap));
        int start = Math.min(sidebarScroll, Math.max(0, mods.size() - visibleRows * columns));
        int end = Math.min(mods.size(), start + visibleRows * columns);
        Set<String> hotLoadedIds = new HashSet<>();
        ExperimentalModHotLoader.getHotLoadedMods().forEach(mod -> hotLoadedIds.add(mod.id()));
        for (int index = start; index < end; index++) {
            ExperimentalModHotLoader.AvailableMod mod = mods.get(index);
            String id = mod.id();
            int column = (index - start) % columns;
            int row = (index - start) / columns;
            int x = contentX() + column * (cardWidth + gap);
            int y = startY + row * (cardHeight + gap);
            boolean fabricLoaded = FabricLoader.getInstance().isModLoaded(id);
            boolean hotLoaded = hotLoadedIds.contains(id);
            addPanelButton(preferences.favoriteMods.contains(id) ? "★" : "☆",
                    x + 7, y + 7, 20, 20, () -> {
                        if (!preferences.favoriteMods.add(id)) preferences.favoriteMods.remove(id);
                        VantaClientPreferences.save();
                        rebuildWidgets();
                    });
            addAccentPanelButton(mod.name(), x + 2, y + cardHeight - 30,
                    cardWidth - 4, 28, () -> selectInstalledMod(id));
            if (fabricLoaded || hotLoaded) {
                addPanelButton(fabricLoaded ? "Enabled" : "Hotloaded", x + cardWidth - 69, y + 7, 62, 19,
                        () -> hotLoadStatus = fabricLoaded
                                ? id + " is enabled by Fabric. It cannot be safely unloaded at runtime; restart after changing the mod JAR."
                                : id + " is session-loaded experimentally. It cannot be safely unloaded; restart Minecraft to clear it.");
            } else {
                addPanelButton("Hotload", x + cardWidth - 61, y + 7, 54, 19, () -> {
                    ExperimentalModHotLoader.Result result = ExperimentalModHotLoader.scanAndLoad(id);
                    hotLoadStatus = result.summary();
                    notifyHotLoadResult(result);
                    refreshModList();
                    rebuildWidgets();
                });
            }
        }
    }

    private void selectCategory(Category category) {
        section = Section.MODS;
        selection = Selection.BUILTIN_CATEGORY;
        selectedCategory = category;
        selectedFeature = null;
        selectedModId = null;
        firstSetting = 0;
        rebuildWidgets();
    }

    private void selectFeatureSettings(Feature feature) {
        section = Section.MODS;
        selection = Selection.BUILTIN_SETTINGS;
        selectedFeature = feature;
        selectedModId = null;
        firstSetting = 0;
        rebuildWidgets();
    }

    private void selectInstalledMod(String modId) {
        section = Section.MODS;
        selection = Selection.INSTALLED_MOD;
        selectedModId = modId;
        firstSetting = 0;
        rebuildWidgets();
    }

    private void addClientSettings() {
        int x = contentX() + contentWidth() - 196;
        int y = CONTENT_TOP + 8;
        addRenderableWidget(VantaPanelButton.builder(
                Component.literal(preferences.accent.name()),
                button -> {
                    VantaClientPreferences.Accent[] accents = VantaClientPreferences.Accent.values();
                    preferences.accent = accents[(preferences.accent.ordinal() + 1) % accents.length];
                    VantaClientPreferences.save();
                    button.setMessage(Component.literal(preferences.accent.name()));
                }).bounds(x, y, 170, 22).build());

        y += 48;
        addPreferenceToggle("Interface animations", preferences.animations, value -> {
            preferences.animations = value;
            VantaClientPreferences.save();
        }, x, y);
        y += 42;
        addPreferenceToggle("Compact layout", preferences.compactMode, value -> {
            preferences.compactMode = value;
            VantaClientPreferences.save();
        }, x, y);
        y += 42;
        addPreferenceToggle("Setting descriptions", preferences.showDescriptions, value -> {
            preferences.showDescriptions = value;
            VantaClientPreferences.save();
        }, x, y);
        y += 48;

        addRenderableWidget(VantaPanelButton.builder(Component.literal("−"), button -> {
            preferences.uiScale = Math.max(0.8, Math.round((preferences.uiScale - 0.05) * 100.0) / 100.0);
            VantaClientPreferences.save();
            button.setMessage(Component.literal("−"));
            rebuildWidgets();
        }).bounds(x, y, 28, 22).build());
        addRenderableWidget(VantaPanelButton.builder(Component.literal(String.format(Locale.ROOT, "%.2fx", preferences.uiScale)),
                button -> {}).bounds(x + 34, y, 102, 22).build());
        addRenderableWidget(VantaPanelButton.builder(Component.literal("+"), button -> {
            preferences.uiScale = Math.min(1.2, Math.round((preferences.uiScale + 0.05) * 100.0) / 100.0);
            VantaClientPreferences.save();
            rebuildWidgets();
        }).bounds(x + 142, y, 28, 22).build());

    }

    private void addPreferenceToggle(String name, boolean current, java.util.function.Consumer<Boolean> setter,
                                     int x, int y) {
        addRenderableWidget(new VantaToggleSwitch(x + 122, y, () -> switch (name) {
            case "Interface animations" -> preferences.animations;
            case "Compact layout" -> preferences.compactMode;
            case "Setting descriptions" -> preferences.showDescriptions;
            default -> current;
        }, setter));
    }

    private int accentColor() {
        return preferences == null ? 0xFF3B82FF : preferences.accent.color();
    }

    private VantaPanelButton addAccentPanelButton(String label, int x, int y,
                                                   int buttonWidth, int buttonHeight,
                                                   Runnable action) {
        return addRenderableWidget(new VantaPanelButton(x, y, buttonWidth, buttonHeight,
                label, action, () -> false, this::accentColor, true));
    }

    private VantaPanelButton addPanelButton(String label, int x, int y, int buttonWidth, int buttonHeight,
                                            Runnable action) {
        return addPanelButton(label, x, y, buttonWidth, buttonHeight, action, () -> false);
    }

    private VantaPanelButton addPanelButton(String label, int x, int y, int buttonWidth, int buttonHeight,
                                            Runnable action, java.util.function.BooleanSupplier selected) {
        VantaPanelButton button = new VantaPanelButton(x, y, buttonWidth, buttonHeight, label,
                action, selected, this::accentColor, false, true);
        return addRenderableWidget(button);
    }

    private int rowHeight() {
        return preferences != null && preferences.compactMode ? 30 : ROW_HEIGHT;
    }

    private int contentX() {
        return 34 + SIDEBAR_WIDTH + 30;
    }

    private int contentWidth() {
        return Math.max(160, 940 - contentX() - 44);
    }

    private static int visibleRows(int rowHeight) {
        return Math.max(1, (504 - CONTENT_TOP - 48) / rowHeight);
    }

    private void addBuiltInFeatures() {
        List<Feature> features = FeatureRegistry.FEATURES.stream()
                .filter(feature -> feature.getCategory() == selectedCategory)
                .toList();
        int rows = visibleRows(rowHeight());
        int maxFirst = Math.max(0, features.size() - rows);
        firstSetting = Math.min(firstSetting, maxFirst);
        int end = Math.min(features.size(), firstSetting + rows);
        int y = CONTENT_TOP + 8;
        for (int i = firstSetting; i < end; i++) {
            Feature feature = features.get(i);
            int controlX = contentX() + contentWidth() - 190;
            int rowY = y;
            addRenderableWidget(new VantaToggleSwitch(controlX, rowY,
                    feature::isEnabled, enabled -> {
                        if (feature.isEnabled() != enabled) feature.toggle();
                        feature.saveConfig();
                        DropperUtilsConfig.save();
                    }));
            if (!name.vantaclient.client.gui.components.settings.SettingRegistry
                    .getSettings(feature).isEmpty()) {
                addPanelButton("Settings", controlX + 54, rowY, 90, 22,
                        () -> selectFeatureSettings(feature));
            }
            y += rowHeight();
        }
    }

    private void addBuiltInFeatureSettings() {
        if (selectedFeature == null) return;
        List<name.vantaclient.client.gui.components.settings.Setting> settings =
                name.vantaclient.client.gui.components.settings.SettingRegistry.getSettings(selectedFeature);
        int rows = visibleRows(34);
        int maxFirst = Math.max(0, settings.size() - rows);
        firstSetting = Math.max(0, Math.min(firstSetting, maxFirst));
        int end = Math.min(settings.size(), firstSetting + rows);
        int y = CONTENT_TOP + 8;
        for (int i = firstSetting; i < end; i++) {
            if (settings.get(i) instanceof name.vantaclient.client.gui.components.settings.SliderSetting setting) {
                addFeatureSlider(setting, y);
            } else if (settings.get(i) instanceof name.vantaclient.client.gui.components.settings.BooleanSetting setting) {
                addFeatureToggle(setting, y);
            } else if (settings.get(i) instanceof name.vantaclient.client.gui.components.settings.ChoiceSetting setting) {
                addFeatureChoice(setting, y);
            }
            y += 34;
        }
        addPanelButton("Back to " + selectedCategory.name(), contentX(), 486, 180, 22, () -> {
            selection = Selection.BUILTIN_CATEGORY;
            selectedFeature = null;
            firstSetting = 0;
            rebuildWidgets();
        });
    }

    private void addFeatureSlider(
            name.vantaclient.client.gui.components.settings.SliderSetting setting, int y) {
        double minimum = setting.getMin();
        double maximum = setting.getMax();
        double current;
        try {
            current = setting.getGetter().get();
        } catch (RuntimeException exception) {
            return;
        }
        double range = maximum - minimum;
        double initial = range <= 0.0 ? 0.0 : Math.max(0.0, Math.min(1.0, (current - minimum) / range));
        addRenderableWidget(new name.vantaclient.client.gui.components.FeatureSliderWidget(
                VantaPanelButton.layoutX(contentX()), VantaPanelButton.layoutY(y),
                VantaPanelButton.layoutSize(Math.min(300, contentWidth())),
                VantaPanelButton.layoutSize(26), setting.getName(), initial) {
            @Override
            protected String getValueText() {
                double value = minimum + this.value * (maximum - minimum);
                if (setting.getUnit().equals("%")) return String.format(Locale.ROOT, "%.0f%%", value);
                if (setting.getUnit().isEmpty()) return String.format(Locale.ROOT, "%.2f", value);
                return String.format(Locale.ROOT, "%.1f %s", value, setting.getUnit());
            }

            @Override
            protected void applyValue() {
                setting.getSetter().accept(minimum + this.value * (maximum - minimum));
            }
        });
    }


    private void addFeatureChoice(
            name.vantaclient.client.gui.components.settings.ChoiceSetting setting, int y) {
        List<String> options = setting.getOptions();
        if (options.isEmpty()) return;

        String current;
        try {
            current = setting.getGetter().get();
        } catch (RuntimeException exception) {
            return;
        }
        if (current == null || !options.contains(current)) current = options.get(0);
        String displayedValue = current;
        int buttonWidth = Math.min(320, contentWidth());
        addPanelButton(setting.getName() + ": " + displayedValue,
                contentX(), y, buttonWidth, 26, () -> {
                    String value;
                    try {
                        value = setting.getGetter().get();
                    } catch (RuntimeException exception) {
                        return;
                    }
                    int index = options.indexOf(value);
                    int next = (index + 1 + options.size()) % options.size();
                    setting.getSetter().accept(options.get(next));
                    rebuildWidgets();
                });
    }

    private void addFeatureToggle(
            name.vantaclient.client.gui.components.settings.BooleanSetting setting, int y) {
        boolean current;
        try {
            current = setting.getGetter().get();
        } catch (RuntimeException exception) {
            return;
        }
        int controlX = contentX() + Math.min(300, contentWidth()) - 46;
        addRenderableWidget(new VantaToggleSwitch(controlX, y + 2,
                () -> setting.getGetter().get(), setting.getSetter()::accept));
    }

    private void addInstalledModContent() {
        ModContainer container = installedMods.stream()
                .filter(mod -> mod.getMetadata().getId().equals(selectedModId))
                .findFirst().orElse(null);
        if (container == null) return;

        ModDescriptor descriptor = integrations.stream()
                .filter(mod -> mod.id().equals(selectedModId))
                .findFirst().orElse(null);
        if (descriptor != null) {
            addPanelButton("Configure " + descriptor.displayName(),
                    contentX(), CONTENT_TOP + 8, 220, 28,
                    () -> minecraft.setScreen(new VantaIntegrationSettingsScreen(this, descriptor)));
            addPanelButton("Back to mod library", contentX(), 486, 180, 22, () -> {
                selection = Selection.NONE;
                selectedModId = null;
                firstSetting = 0;
                rebuildWidgets();
            });
            return;
        }

        int x = contentX() + contentWidth() - 190;
        Object configFactory = discoveredConfigFactories.get(selectedModId);
        if (configFactory != null) {
            addRenderableWidget(VantaPanelButton.builder(Component.literal("Open Mod Settings"),
                    button -> {
                        Screen configScreen = ModConfigDiscovery.open(configFactory, this);
                        if (configScreen != null) minecraft.setScreen(configScreen);
                    }).bounds(x, CONTENT_TOP + 8, 180, 24).build());
        } else if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            addRenderableWidget(VantaPanelButton.builder(Component.literal("Open Mod Menu"),
                    button -> openModMenu())
                    .bounds(x, CONTENT_TOP + 8, 180, 24).build());
        }
        addPanelButton("Back to mod library", contentX(), 486, 180, 22, () -> {
            selection = Selection.NONE;
            selectedModId = null;
            firstSetting = 0;
            rebuildWidgets();
        });
    }

    private void notifyHotLoadResult(ExperimentalModHotLoader.Result result) {
        if (result.loaded() > 0 && result.failed() == 0) {
            VantaNotificationManager.success("Session load complete",
                    "Loaded " + result.loaded() + " mod(s). Runtime loading remains experimental.");
        } else if (result.failed() > 0) {
            VantaNotificationManager.error("Session load failed",
                    "Some mod entrypoints failed. Review the scan details and restart Minecraft.");
        } else if (result.skipped() > 0) {
            VantaNotificationManager.warning("No mods loaded",
                    "These JARs are not eligible for session loading. Restart Minecraft to load normal mods.");
        }
    }

    private void openModMenu() {
        try {
            Class<?> integration = Class.forName(
                    "name.vantaclient.client.integration.VantaModMenuIntegration");
            Screen modsScreen = (Screen) integration
                    .getMethod("openModsScreen", Screen.class)
                    .invoke(null, this);
            minecraft.setScreen(modsScreen);
        } catch (ReflectiveOperationException | LinkageError exception) {
            System.err.println("[Vanta Client] Could not open Mod Menu: " + exception.getMessage());
            VantaNotificationManager.warning("Mod Menu unavailable",
                    "Could not open this mod's settings screen. Check the installed Mod Menu version.");
        }
    }

    private void addIntegrationSettings(ModDescriptor descriptor) {
        List<SettingDefinition<?>> settings = descriptor.settings();
        int visibleRows = visibleRows(rowHeight());
        int maxFirst = Math.max(0, settings.size() - visibleRows);
        firstSetting = Math.max(0, Math.min(firstSetting, maxFirst));
        int end = Math.min(settings.size(), firstSetting + visibleRows);
        int y = CONTENT_TOP + 8;
        for (int i = firstSetting; i < end; i++) {
            addSettingControl(settings.get(i), y);
            y += rowHeight();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void addSettingControl(SettingDefinition setting, int y) {
        int controlX = contentX() + contentWidth() - 190;
        int controlWidth = 180;
        Object value;
        try {
            value = setting.value();
        } catch (RuntimeException exception) {
            addRenderableWidget(VantaPanelButton.builder(Component.literal("Unavailable"), button -> {})
                    .bounds(controlX, y, controlWidth, 22).build());
            return;
        }

        switch (setting.type()) {
            case BOOLEAN -> addRenderableWidget(new VantaToggleSwitch(controlX + controlWidth - 46, y,
                    () -> {
                        try { return Boolean.TRUE.equals(setting.value()); }
                        catch (RuntimeException ignored) { return false; }
                    }, next -> {
                        try { setting.set(next); } catch (RuntimeException ignored) {}
                    }));
            case INTEGER, DECIMAL -> {
                double minimum = setting.minimum() == null ? 0 : setting.minimum();
                double maximum = setting.maximum() == null ? minimum : setting.maximum();
                double step = setting.type() == SettingDefinition.Type.INTEGER ? 1
                        : Math.max(0.01, (maximum - minimum) / 100.0);
                VantaPanelButton valueButton = VantaPanelButton.builder(Component.literal(formatNumber(
                        value instanceof Number number ? number.doubleValue() : minimum)), button -> {})
                        .bounds(controlX + 30, y, 120, 22).build();
                addRenderableWidget(VantaPanelButton.builder(Component.literal("−"), button -> {
                    updateNumeric(setting, minimum, maximum, -step, valueButton);
                }).bounds(controlX, y, 26, 22).build());
                addRenderableWidget(valueButton);
                addRenderableWidget(VantaPanelButton.builder(Component.literal("+"), button -> {
                    updateNumeric(setting, minimum, maximum, step, valueButton);
                }).bounds(controlX + 154, y, 26, 22).build());
            }
            case ENUM -> {
                Object[] constants = setting.valueClass().getEnumConstants();
                addRenderableWidget(VantaPanelButton.builder(Component.literal(String.valueOf(value)), button -> {
                    if (constants == null || constants.length == 0) return;
                    try {
                        Object current = setting.value();
                        int index = 0;
                        for (int i = 0; i < constants.length; i++) if (constants[i].equals(current)) index = i;
                        setting.set(constants[(index + 1) % constants.length]);
                        button.setMessage(Component.literal(String.valueOf(setting.value())));
                    } catch (RuntimeException ignored) {
                        button.setMessage(Component.literal("Error"));
                    }
                }).bounds(controlX, y, controlWidth, 22).build());
            }
            case STRING -> {
                EditBox input = new EditBox(font, VantaPanelButton.layoutX(controlX),
                        VantaPanelButton.layoutY(y), VantaPanelButton.layoutSize(controlWidth),
                        VantaPanelButton.layoutSize(22), Component.literal(setting.name()));
                input.setMaxLength(setting.maximum() == null ? 256
                        : Math.max(0, Math.min(32767, setting.maximum().intValue())));
                input.setValue(value instanceof String string ? string : "");
                input.setResponder(text -> {
                    try { setting.set(text); } catch (RuntimeException ignored) {}
                });
                addRenderableWidget(input);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void updateNumeric(SettingDefinition setting, double minimum, double maximum,
                                      double delta, VantaPanelButton label) {
        try {
            Object current = setting.value();
            double next = Math.max(minimum, Math.min(maximum,
                    (current instanceof Number number ? number.doubleValue() : minimum) + delta));
            if (setting.type() == SettingDefinition.Type.INTEGER) setting.set((int) Math.round(next));
            else setting.set(next);
            label.setMessage(Component.literal(formatNumber(next)));
        } catch (RuntimeException ignored) {
            label.setMessage(Component.literal("Error"));
        }
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value)
                : String.format(Locale.ROOT, "%.2f", value);
    }

    @Override
    public void tick() {
        super.tick();
        if (runtime != null) runtime.tick();
        if (preferences == null || !preferences.animations) {
            openProgress = 1.0f;
        } else {
            openProgress = Math.min(1.0f, openProgress + 0.16f);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (runtime != null) {
            graphics.fill(0, 0, width, height, 0x90070D16);
            runtime.setScale((float) VantaClientPreferences.get().uiScale);
            runtime.render(graphics, font, width, height, mouseX, mouseY);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        int accent = preferences == null ? 0xFF718BFF : preferences.accent.color();
        float rawProgress = Math.min(1.0f, openProgress + 0.16f * partialTick);
        float easedProgress = 1.0f - (float) Math.pow(1.0f - rawProgress, 3.0);
        int alpha = (int) (0xD9 * easedProgress);
        graphics.fill(0, 0, width, height, (alpha << 24));

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) VantaPanelButton.layoutOffsetX(),
                (float) VantaPanelButton.layoutOffsetY());
        graphics.pose().scale(VantaPanelButton.layoutScale(), VantaPanelButton.layoutScale());
        if (preferences != null && preferences.animations) {
            graphics.pose().translate(0.0f, 7.0f * (1.0f - easedProgress));
        }
        int left = 20, top = 18, right = 940, bottom = 522;
        drawRoundedCard(graphics, left - 3, top - 3, right - left + 6,
                bottom - top + 6, 0x44000000, 19);
        float panelOpacity = preferences == null ? 0.88f : (float) preferences.panelOpacity;
        drawRoundedCard(graphics, left, top, right - left, bottom - top,
                withAlpha(VantaUiTheme.PANEL, panelOpacity), 16);
        drawRoundedCard(graphics, left + 1, top + 4, right - left - 2, 49,
                withAlpha(VantaUiTheme.PANEL_RAISED, panelOpacity), 14);
        drawRoundedCard(graphics, left + 12, top + 12, SIDEBAR_WIDTH + 12,
                bottom - top - 24, withAlpha(VantaUiTheme.PANEL_RAISED, panelOpacity), 15);
        graphics.fill(left + 12 + SIDEBAR_WIDTH + 14, top + 20,
                left + 13 + SIDEBAR_WIDTH + 14, bottom - 20, VantaUiTheme.BORDER_SUBTLE);
        drawRoundedCard(graphics, left + 12 + SIDEBAR_WIDTH + 20, top + 62,
                right - (left + 12 + SIDEBAR_WIDTH + 20) - 12,
                bottom - top - 74, withAlpha(VantaUiTheme.PANEL, panelOpacity), 15);
        graphics.fill(left + 12, top + 61, right - 12, top + 62, VantaUiTheme.BORDER_SUBTLE);

        drawVantaBrandMark(graphics, 34, 28, accent);
        graphics.drawString(font, "VANTA CLIENT", 72, 31, accent);
        graphics.drawString(font, "FABRIC CLIENT", 72, 46, 0xFF7F8CA1);
        graphics.drawString(font, "MODS & OPTIONS", 34, 63, VantaUiTheme.TEXT_MUTED);
        graphics.drawString(font, "PERSONALIZATION", 34, 117, VantaUiTheme.TEXT_MUTED);
        if (section == Section.SETTINGS) {
            graphics.drawString(font, "LOCAL CONFIGURATION", 760, 34, 0xFF778296);
        }
        if (section == Section.MODS) {
            graphics.drawString(font, "MOD LIBRARY", 34, 166, VantaUiTheme.TEXT_MUTED);
        }
        graphics.drawCenteredString(font, "V", 56, 471, accent);
        graphics.drawString(font, "Vanta Client", 78, 462, 0xFFE8EDF6);
        graphics.drawString(font, "Fabric Edition", 78, 476, 0xFF7F8CA1);

        graphics.drawString(font, section == Section.SETTINGS ? "Settings" : "Mods",
                contentX(), 27, 0xFFF2F4F9);
        graphics.drawString(font, section == Section.SETTINGS
                ? "Appearance and interface preferences"
                : "Built-in features and installed integrations",
                contentX(), 43, VantaUiTheme.TEXT_SECONDARY);
        if (section == Section.MODS && selection == Selection.NONE) {
            drawRoundedCard(graphics, 648, 20, 196, 28, VantaUiTheme.INPUT_SURFACE, 10);
            graphics.drawString(font, "⌕", 658, 29, 0xFF7F8DA2);
        }
        graphics.fill(contentX() - 8, 98, right - 14, 99, VantaUiTheme.BORDER_SUBTLE);

        if (section == Section.SETTINGS) {
            int cardX = contentX() - 8;
            int cardWidth = contentWidth() + 8;
            drawRoundedCard(graphics, cardX, CONTENT_TOP, cardWidth, 42, VantaUiTheme.PANEL_RAISED, 12);
            drawRoundedCard(graphics, cardX, CONTENT_TOP + 44, cardWidth, 42, VantaUiTheme.PANEL_RAISED, 12);
            drawRoundedCard(graphics, cardX, CONTENT_TOP + 88, cardWidth, 42, VantaUiTheme.PANEL_RAISED, 12);
            drawRoundedCard(graphics, cardX, CONTENT_TOP + 132, cardWidth, 42, VantaUiTheme.PANEL_RAISED, 12);
            drawRoundedCard(graphics, cardX, CONTENT_TOP + 178, cardWidth, 42, VantaUiTheme.PANEL_RAISED, 12);
            graphics.drawString(font, "Accent colour", contentX(), CONTENT_TOP + 9, VantaUiTheme.TEXT_PRIMARY);
            graphics.drawString(font, "Choose the highlight colour used across Vanta.", contentX(), CONTENT_TOP + 23, VantaUiTheme.TEXT_SECONDARY);
            graphics.drawString(font, "Interface animations", contentX(), CONTENT_TOP + 53, VantaUiTheme.TEXT_PRIMARY);
            graphics.drawString(font, "Animate panel, navigation and control feedback.", contentX(), CONTENT_TOP + 67, VantaUiTheme.TEXT_SECONDARY);
            graphics.drawString(font, "Compact layout", contentX(), CONTENT_TOP + 97, VantaUiTheme.TEXT_PRIMARY);
            graphics.drawString(font, "Reduce vertical spacing in feature lists.", contentX(), CONTENT_TOP + 111, VantaUiTheme.TEXT_SECONDARY);
            graphics.drawString(font, "Setting descriptions", contentX(), CONTENT_TOP + 141, VantaUiTheme.TEXT_PRIMARY);
            graphics.drawString(font, "Show explanatory text where integrations provide it.", contentX(), CONTENT_TOP + 155, VantaUiTheme.TEXT_SECONDARY);
            graphics.drawString(font, "UI scale / spacing", contentX(), CONTENT_TOP + 187, VantaUiTheme.TEXT_PRIMARY);
            graphics.drawString(font, "Adjust the overall interface size.", contentX(), CONTENT_TOP + 201, VantaUiTheme.TEXT_SECONDARY);
        } else if (selection == Selection.BUILTIN_CATEGORY) {
            graphics.drawString(font, selectedCategory.name(), contentX(), 66, 0xFFE8EAF0);
            graphics.drawString(font, "Built-in Vanta Client features", contentX(), 86, VantaUiTheme.TEXT_SECONDARY);
            List<Feature> features = FeatureRegistry.FEATURES.stream()
                    .filter(feature -> feature.getCategory() == selectedCategory).toList();
            int visibleRows = visibleRows(rowHeight());
            int end = Math.min(features.size(), firstSetting + visibleRows);
            int y = CONTENT_TOP + 14;
            for (int i = firstSetting; i < end; i++) {
                Feature feature = features.get(i);
                drawRoundedCard(graphics, contentX() - 8, y - 10, contentWidth() + 8,
                        rowHeight() - 4,
                        feature.isEnabled() ? VantaUiTheme.SURFACE_SELECTED : VantaUiTheme.PANEL_RAISED, 12);
                graphics.drawString(font, feature.getName(), contentX(), y, VantaUiTheme.TEXT_PRIMARY);
                if (preferences.showDescriptions && !feature.getDescription().isBlank()) {
                    drawWrappedText(graphics, feature.getDescription(), contentX(), y + 13,
                            contentWidth() - 210, VantaUiTheme.TEXT_MUTED, 2);
                }
                y += rowHeight();
            }
        } else if (selection == Selection.BUILTIN_SETTINGS && selectedFeature != null) {
            graphics.drawString(font, selectedFeature.getName() + " settings", contentX(), 66, VantaUiTheme.TEXT_PRIMARY);
            drawWrappedText(graphics, selectedFeature.getDescription(), contentX(), 86,
                    contentWidth() - 12, VantaUiTheme.TEXT_SECONDARY, 2);
            List<name.vantaclient.client.gui.components.settings.Setting> featureSettings =
                    name.vantaclient.client.gui.components.settings.SettingRegistry.getSettings(selectedFeature);
            int y = CONTENT_TOP + 8;
            for (int i = firstSetting; i < Math.min(featureSettings.size(), firstSetting + visibleRows(34)); i++) {
                drawRoundedCard(graphics, contentX() - 8, y - 3, Math.min(340, contentWidth() + 8),
                        31, VantaUiTheme.PANEL_RAISED, 11);
                y += 34;
            }
        } else if (selection == Selection.INSTALLED_MOD) {
            ModContainer mod = installedMods.stream()
                    .filter(entry -> entry.getMetadata().getId().equals(selectedModId))
                    .findFirst().orElse(null);
            if (mod != null) {
                drawRoundedCard(graphics, contentX() - 8, 58, contentWidth() + 8, 62,
                        VantaUiTheme.PANEL_RAISED, 12);
                drawRoundedCard(graphics, contentX() + 2, 68, 42, 42, 0xFF252F40, 10);
                Identifier detailIcon = modIconTextures.get(selectedModId);
                if (detailIcon != null) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, detailIcon, contentX() + 4, 70,
                            0.0f, 0.0f, 38, 38, 38, 38);
                } else {
                    String initial = mod.getMetadata().getName().isBlank() ? "?"
                            : mod.getMetadata().getName().substring(0, 1).toUpperCase(Locale.ROOT);
                    graphics.drawCenteredString(font, initial, contentX() + 23, 84, accent);
                }
                graphics.drawString(font, font.plainSubstrByWidth(
                        mod.getMetadata().getName(), contentWidth() - 72),
                        contentX() + 54, 68, 0xFFF0F3F9);
                drawWrappedText(graphics, mod.getMetadata().getId() + "  •  " +
                        mod.getMetadata().getVersion().getFriendlyString(), contentX() + 54, 84,
                        contentWidth() - 72, VantaUiTheme.TEXT_SECONDARY, 1);
                ModDescriptor descriptor = integrations.stream()
                        .filter(entry -> entry.id().equals(selectedModId)).findFirst().orElse(null);
                String description = descriptor != null ? descriptor.description() : mod.getMetadata().getDescription();
                if (preferences.showDescriptions && description != null && !description.isBlank()) {
                    drawWrappedText(graphics, description, contentX() + 54, 99,
                            contentWidth() - 72, VantaUiTheme.TEXT_MUTED, 1);
                }
                if (descriptor == null) {
                    boolean hasDiscoveredSettings = discoveredConfigFactories.containsKey(selectedModId);
                    String settingsMessage = hasDiscoveredSettings
                            ? "Vanta found this mod's existing Mod Menu config screen. Use Open Mod Settings to edit it."
                            : FabricLoader.getInstance().isModLoaded("modmenu")
                            ? "No config screen was exposed by this mod. Vanta cannot safely infer private settings without a supported config API."
                            : "No config screen was exposed. Install Mod Menu to discover settings screens provided by compatible mods.";
                    drawWrappedText(graphics, settingsMessage,
                            contentX(), CONTENT_TOP + 20, contentWidth() - 12, 0xFFB3B6C0, 3);
                } else {
                    List<SettingDefinition<?>> settings = descriptor.settings();
                    int visibleRows = visibleRows(rowHeight());
                    int end = Math.min(settings.size(), firstSetting + visibleRows);
                    int y = CONTENT_TOP + 14;
                    for (int i = firstSetting; i < end; i++) {
                        SettingDefinition<?> setting = settings.get(i);
                        graphics.drawString(font, setting.name(), contentX(), y, 0xFFE8EAF0);
                        if (preferences.showDescriptions && !setting.description().isBlank()) {
                            drawWrappedText(graphics, setting.description(), contentX(), y + 13,
                            contentWidth() - 210, VantaUiTheme.TEXT_MUTED, 2);
                        }
                        y += rowHeight();
                    }
                }
            }
        } else {
            List<ExperimentalModHotLoader.AvailableMod> mods = filteredMods();
            graphics.drawString(font, "Installed mods", contentX(), 66, 0xFFE8EAF0);
            graphics.drawString(font, mods.size() + " matching mods · " + availableMods.size() + " total",
                    contentX(), 86, VantaUiTheme.TEXT_SECONDARY);
            graphics.drawString(font, "Four-column library · settings per mod · hot-loading is experimental",
                    contentX(), CONTENT_TOP + 22, 0xFFB3B6C0);
            int columns = 4;
            int gap = 10;
            int cardWidth = Math.max(112, (contentWidth() - gap * (columns - 1)) / columns);
            int cardHeight = 120;
            int startY = CONTENT_TOP + 88;
            int visibleRows = Math.max(1, (504 - startY - 24) / (cardHeight + gap));
            int start = Math.min(sidebarScroll, Math.max(0, mods.size() - visibleRows * columns));
            int end = Math.min(mods.size(), start + visibleRows * columns);
            Set<String> hotLoadedIds = new HashSet<>();
            ExperimentalModHotLoader.getHotLoadedMods().forEach(mod -> hotLoadedIds.add(mod.id()));
            if (mods.isEmpty()) {
                drawRoundedCard(graphics, contentX(), startY + 8, contentWidth(), 116,
                        VantaUiTheme.PANEL_RAISED, 14);
                drawRoundedCard(graphics, contentX() + 16, startY + 25, 38, 38,
                        0xFF27364A, 11);
                graphics.drawCenteredString(font, "＋", contentX() + 35, startY + 37, accent);
                graphics.drawString(font, "Your mod library is empty", contentX() + 66,
                        startY + 28, 0xFFE8EDF6);
                graphics.drawString(font, "Add Fabric mod JARs to this instance, then reopen Vanta Client.",
                        contentX() + 66, startY + 45, 0xFF8E9AAF);
                graphics.drawString(font, "Installed mods appear here with their original icons and settings links.",
                        contentX() + 66, startY + 61, 0xFF748197);
            }
            int logicalMouseX = VantaPanelButton.unlayoutX(mouseX);
            int logicalMouseY = VantaPanelButton.unlayoutY(mouseY);
            for (int index = start; index < end; index++) {
                ExperimentalModHotLoader.AvailableMod mod = mods.get(index);
                int column = (index - start) % columns;
                int row = (index - start) / columns;
                int x = contentX() + column * (cardWidth + gap);
                int y = startY + row * (cardHeight + gap);
                boolean hovered = logicalMouseX >= x && logicalMouseX < x + cardWidth
                        && logicalMouseY >= y && logicalMouseY < y + cardHeight;
                float hover = modCardHover.getOrDefault(mod.id(), 0.0f);
                hover += ((hovered ? 1.0f : 0.0f) - hover) * 0.22f;
                if (Math.abs((hovered ? 1.0f : 0.0f) - hover) < 0.015f) hover = hovered ? 1.0f : 0.0f;
                modCardHover.put(mod.id(), hover);
                drawRoundedCard(graphics, x, y, cardWidth, cardHeight,
                        withAlpha(mixColor(VantaUiTheme.PANEL_RAISED, accent, 0.05f + hover * 0.08f),
                                (float) preferences.panelOpacity), 13);
                Identifier iconTexture = modIconTextures.get(mod.id());
                int iconSize = 42;
                int iconX = x + (cardWidth - iconSize) / 2;
                int iconY = y + 15;
                if (iconTexture != null) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, iconTexture, iconX, iconY,
                            0.0f, 0.0f, iconSize, iconSize, iconSize, iconSize);
                } else {
                    String iconLetter = mod.name().isBlank() ? "?" :
                            mod.name().substring(0, 1).toUpperCase(Locale.ROOT);
                    graphics.drawCenteredString(font, iconLetter, x + cardWidth / 2,
                            iconY + 13, accent);
                }
                graphics.drawString(font, font.plainSubstrByWidth(mod.version(),
                        cardWidth - 20), x + 10, y + 66, 0xFF8994A7);
                boolean fabricLoaded = FabricLoader.getInstance().isModLoaded(mod.id());
                graphics.drawString(font, fabricLoaded ? "Enabled · Fabric"
                        : hotLoadedIds.contains(mod.id()) ? "Session loaded*"
                        : "Not loaded", x + 10, y + 78,
                        fabricLoaded ? 0xFF79B89A
                                : hotLoadedIds.contains(mod.id()) ? 0xFFE5BD78 : 0xFF9BA7BA);
            }
            drawWrappedText(graphics, hotLoadStatus, contentX(), 486,
                    contentWidth() - 12, 0xFFB3B6C0, 2);
        }

        if (studioOpen && section == Section.MODS) {
            int panelAlpha = (int) (Math.max(0.85, Math.min(0.98, preferences.panelOpacity)) * 255.0);
            drawRoundedCard(graphics, 736, 72, 192, 164,
                    withAlpha(VantaUiTheme.PANEL_RAISED, panelAlpha / 255.0f), 13);
            graphics.drawString(font, "STUDIO", 750, 84, 0xFFF1F4FA);
            graphics.drawString(font, "Live interface controls", 750, 98, 0xFF8E9AAF);
            graphics.drawString(font, "Panel opacity", 750, 130, 0xFFB8C1D0);
            graphics.drawString(font, String.format(Locale.ROOT, "%.0f%%", preferences.panelOpacity * 100),
                    866, 130, accent);
            graphics.drawString(font, "UI scale", 750, 160, 0xFFB8C1D0);
            graphics.drawString(font, String.format(Locale.ROOT, "%.2fx", preferences.uiScale),
                    866, 160, accent);
            graphics.drawString(font, "Animation", 750, 190, 0xFFB8C1D0);
            graphics.drawString(font, preferences.animations ? "Enabled" : "Disabled",
                    866, 190, accent);
        }
        graphics.pose().popMatrix();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawVantaBrandMark(GuiGraphics graphics, int x, int y, int accent) {
        drawRoundedCard(graphics, x, y, 30, 30,
                mixColor(VantaUiTheme.PANEL_RAISED, accent, 0.18f), 10);
        String[] mark = {"10001", "10001", "01010", "01010", "00100"};
        int cell = 3;
        int gap = 1;
        int startX = x + 6;
        int startY = y + 6;
        for (int row = 0; row < mark.length; row++) {
            for (int column = 0; column < mark[row].length(); column++) {
                if (mark[row].charAt(column) == '1') {
                    drawRoundedCard(graphics, startX + column * (cell + gap),
                            startY + row * (cell + gap), cell, cell, accent, 1);
                }
            }
        }
    }

    private int mixColor(int from, int to, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int a = Math.round(((from >>> 24) & 255) * (1.0f - t) + ((to >>> 24) & 255) * t);
        int r = Math.round(((from >>> 16) & 255) * (1.0f - t) + ((to >>> 16) & 255) * t);
        int g = Math.round(((from >>> 8) & 255) * (1.0f - t) + ((to >>> 8) & 255) * t);
        int b = Math.round((from & 255) * (1.0f - t) + (to & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int withAlpha(int color, float opacity) {
        int alpha = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 255) * opacity)));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private void drawRoundedCard(GuiGraphics graphics, int x, int y, int width,
                                  int height, int color, int radius) {
        int safeRadius = Math.max(1, Math.min(radius, Math.min(width, height) / 2));
        for (int offset = 0; offset < height; offset++) {
            int inset = 0;
            if (offset < safeRadius) {
                double dy = safeRadius - offset - 0.5;
                inset = safeRadius - (int) Math.floor(Math.sqrt(
                        Math.max(0.0, safeRadius * safeRadius - dy * dy)));
            } else if (offset >= height - safeRadius) {
                double dy = offset - (height - safeRadius) + 0.5;
                inset = safeRadius - (int) Math.floor(Math.sqrt(
                        Math.max(0.0, safeRadius * safeRadius - dy * dy)));
            }
            graphics.fill(x + inset, y + offset, x + width - inset, y + offset + 1, color);
        }
        graphics.fill(x + safeRadius, y, x + width - safeRadius, y + 1, 0x223C4657);
    }

    private void drawCard(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        drawRoundedCard(graphics, x, y, width, height, 0xFF2A3443, 10);
        drawRoundedCard(graphics, x + 1, y + 1, width - 2, height - 2, color, 9);
    }

    private void drawWrappedText(GuiGraphics graphics, String value, int x, int y,
                                 int maxWidth, int color, int maxLines) {
        if (value == null || value.isBlank() || maxWidth <= 0) return;
        StringBuilder line = new StringBuilder();
        int lineIndex = 0;
        for (String word : value.trim().split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) > maxWidth && !line.isEmpty()) {
                graphics.drawString(font, line.toString(), x, y + lineIndex * 11, color);
                line.setLength(0);
                if (++lineIndex >= maxLines) return;
                if (font.width(word) > maxWidth) word = font.plainSubstrByWidth(word, maxWidth);
                line.append(word);
            } else {
                if (font.width(word) > maxWidth) word = font.plainSubstrByWidth(word, maxWidth);
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty() && lineIndex < maxLines) {
            graphics.drawString(font, line.toString(), x, y + lineIndex * 11, color);
        }
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
    public boolean keyPressed(KeyEvent event) {
        boolean shiftDown = (event.modifiers() & 1) != 0;
        if (runtime != null && runtime.keyPressed(event.key(), shiftDown)) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (runtime != null && runtime.charTyped(event.codepoint())) return true;
        return super.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (runtime != null && runtime.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        double rawMouseX = mouseX;
        double rawMouseY = mouseY;
        mouseX = VantaPanelButton.unlayoutX(mouseX);
        mouseY = VantaPanelButton.unlayoutY(mouseY);
        if (section == Section.MODS && selection == Selection.NONE
                && mouseX >= contentX() && mouseY >= CONTENT_TOP + 88) {
            int columns = 4;
            int cardHeight = 120;
            int gap = 10;
            int visibleRows = Math.max(1, (504 - (CONTENT_TOP + 88) - 24) / (cardHeight + gap));
            int maxScroll = Math.max(0, filteredMods().size() - visibleRows * columns);
            sidebarScroll = Math.max(0, Math.min(maxScroll,
                    sidebarScroll - (int) Math.signum(scrollY) * columns));
            rebuildWidgets();
            return true;
        }
        if (section == Section.MODS && (selection == Selection.BUILTIN_CATEGORY
                || selection == Selection.BUILTIN_SETTINGS)) {
            int count = selection == Selection.BUILTIN_SETTINGS && selectedFeature != null
                    ? name.vantaclient.client.gui.components.settings.SettingRegistry.getSettings(selectedFeature).size()
                    : (int) FeatureRegistry.FEATURES.stream()
                    .filter(feature -> feature.getCategory() == selectedCategory).count();
            int rows = selection == Selection.BUILTIN_SETTINGS ? visibleRows(34) : visibleRows(rowHeight());
            int maxFirst = Math.max(0, count - rows);
            firstSetting = Math.max(0, Math.min(maxFirst, firstSetting - (int) Math.signum(scrollY)));
            rebuildWidgets();
            return true;
        }
        if (section == Section.MODS && selection == Selection.INSTALLED_MOD) {
            ModDescriptor descriptor = integrations.stream()
                    .filter(entry -> entry.id().equals(selectedModId)).findFirst().orElse(null);
            if (descriptor != null) {
                int maxFirst = Math.max(0, descriptor.settings().size()
                        - visibleRows(rowHeight()));
                firstSetting = Math.max(0, Math.min(maxFirst,
                        firstSetting - (int) Math.signum(scrollY)));
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseScrolled(rawMouseX, rawMouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (runtime != null) {
            runtime.dispose();
            runtime = null;
        }
        releaseModIcons();
        super.removed();
    }

    private void releaseModIcons() {
        for (Identifier texture : modIconTextures.values()) {
            minecraft.getTextureManager().release(texture);
        }
        modIconTextures.clear();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
