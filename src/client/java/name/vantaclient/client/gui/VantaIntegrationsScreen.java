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
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
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
    private static final int SIDEBAR_WIDTH = 220;
    private static final int ROW_HEIGHT = 38;
    private static final int CONTENT_TOP = 118;

    private enum Section { SETTINGS, MODS }
    private enum Selection { NONE, BUILTIN_CATEGORY, BUILTIN_SETTINGS, INSTALLED_MOD }

    private final Screen parent;
    private Section section = Section.SETTINGS;
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
        super(Component.literal("Vanta Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        preferences = VantaClientPreferences.get();
        openProgress = preferences.animations ? 0.0f : 1.0f;
        if (!modListInitialized) {
            refreshModList();
            modListInitialized = true;
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
                () -> {
                    section = Section.SETTINGS;
                    selection = Selection.NONE;
                    rebuildWidgets();
                }, () -> section == Section.SETTINGS);
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
                addPanelButton("HUD", contentX() + 138, filterY, 48, 26,
                        () -> selectCategory(Category.HUD),
                        () -> selection == Selection.BUILTIN_CATEGORY && selectedCategory == Category.HUD);
                addPanelButton("Visual", contentX() + 192, filterY, 58, 26,
                        () -> selectCategory(Category.VISUAL),
                        () -> selection == Selection.BUILTIN_CATEGORY && selectedCategory == Category.VISUAL);
                addPanelButton("Combat", contentX() + 256, filterY, 64, 26,
                        () -> selectCategory(Category.COMBAT),
                        () -> selection == Selection.BUILTIN_CATEGORY && selectedCategory == Category.COMBAT);
                addPanelButton("Misc", contentX() + 326, filterY, 52, 26,
                        () -> selectCategory(Category.MISC),
                        () -> selection == Selection.BUILTIN_CATEGORY && selectedCategory == Category.MISC);
                addPanelButton("Scan mods", contentX() + contentWidth() - 154,
                        filterY, 154, 26, () -> {
                    ExperimentalModHotLoader.Result result = ExperimentalModHotLoader.scanAndLoad();
                    hotLoadStatus = result.summary();
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
            preferences.panelOpacity = Math.max(0.72, Math.round((preferences.panelOpacity - 0.04) * 100.0) / 100.0);
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
                    new DynamicTexture("Vanta mod icon " + modId, image));
            modIconTextures.put(modId, texture);
        } catch (Exception | LinkageError ignored) {
            // Missing, malformed or oversized optional icons fall back to the mod initial.
        }
    }

    private void addModNavigation() {
        int y = 177;
        addPanelButton("HUD", 34, y, SIDEBAR_WIDTH - 24, 22,
                () -> selectCategory(Category.HUD),
                () -> selection == Selection.BUILTIN_CATEGORY && selectedCategory == Category.HUD);
        y += 27;
        addPanelButton("Visual", 34, y, SIDEBAR_WIDTH - 24, 22,
                () -> selectCategory(Category.VISUAL),
                () -> selection == Selection.BUILTIN_CATEGORY && selectedCategory == Category.VISUAL);
        y += 27;
        addPanelButton("Combat", 34, y, SIDEBAR_WIDTH - 24, 22,
                () -> selectCategory(Category.COMBAT),
                () -> selection == Selection.BUILTIN_CATEGORY && selectedCategory == Category.COMBAT);
        y += 27;
        addPanelButton("Misc", 34, y, SIDEBAR_WIDTH - 24, 22,
                () -> selectCategory(Category.MISC),
                () -> selection == Selection.BUILTIN_CATEGORY && selectedCategory == Category.MISC);

        // Installed mods use a four-column tile grid in the main content area.
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
                addPanelButton("Loaded", x + cardWidth - 57, y + 7, 50, 19,
                        () -> hotLoadStatus = id + " is already active. Arbitrary Fabric mods cannot be safely disabled at runtime.");
            } else {
                addPanelButton("Enable*", x + cardWidth - 61, y + 7, 54, 19, () -> {
                    ExperimentalModHotLoader.Result result = ExperimentalModHotLoader.scanAndLoad(id);
                    hotLoadStatus = result.summary();
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
            addIntegrationSettings(descriptor);
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
        if (preferences == null || !preferences.animations) {
            openProgress = 1.0f;
        } else {
            openProgress = Math.min(1.0f, openProgress + 0.16f);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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
        drawRoundedCard(graphics, left - 6, top - 6, right - left + 12,
                bottom - top + 12, 0x55000000, 18);
        drawRoundedCard(graphics, left - 2, top - 2, right - left + 4,
                bottom - top + 4, 0x99000000, 16);
        float panelOpacity = preferences == null ? 0.92f : (float) preferences.panelOpacity;
        drawRoundedCard(graphics, left, top, right - left, bottom - top,
                withAlpha(0xFF10141C, panelOpacity), 15);
        drawRoundedCard(graphics, left + 1, top + 1, right - left - 2,
                bottom - top - 2, withAlpha(0xFF10141C, panelOpacity), 14);
        drawRoundedCard(graphics, left + 1, top + 1, right - left - 2, 3, accent, 2);
        drawRoundedCard(graphics, left + 1, top + 4, right - left - 2, 49, 0xFF151B26, 13);
        drawRoundedCard(graphics, left + 12, top + 12, SIDEBAR_WIDTH + 12,
                bottom - top - 24, 0xFF171D27, 11);
        graphics.fill(left + 12 + SIDEBAR_WIDTH + 14, top + 20,
                left + 13 + SIDEBAR_WIDTH + 14, bottom - 20, 0xFF2A303C);
        drawRoundedCard(graphics, left + 12 + SIDEBAR_WIDTH + 20, top + 62,
                right - (left + 12 + SIDEBAR_WIDTH + 20) - 12,
                bottom - top - 74, 0xFF121720, 11);
        graphics.fill(left + 12, top + 61, right - 12, top + 62, 0xFF252B36);

        graphics.fill(34, 31, 37, 44, accent);
        graphics.drawString(font, "VANTA", 45, 28, 0xFFF4F6FC);
        graphics.drawString(font, "CLIENT", 90, 28, 0xFF9AA4B6);
        graphics.drawString(font, "MINECRAFT MOD", 34, 47, 0xFF727D90);
        graphics.drawString(font, "MODS & OPTIONS", 34, 63, 0xFF858A98);
        graphics.drawString(font, "PERSONALIZATION", 34, 117, 0xFF858A98);
        if (section == Section.SETTINGS) {
            graphics.drawString(font, "LOCAL CONFIGURATION", 760, 34, 0xFF778296);
        }
        if (section == Section.MODS) {
            graphics.drawString(font, "BUILT-IN", 34, 166, 0xFF858A98);
            graphics.drawString(font, "INSTALLED MODS", 34, 302, 0xFF858A98);
        }
        graphics.drawString(font, section == Section.SETTINGS ? "Settings" : "Mods",
                contentX(), 27, 0xFFF2F4F9);
        graphics.drawString(font, section == Section.SETTINGS
                ? "Appearance and interface preferences"
                : "Built-in features and installed integrations",
                contentX(), 43, 0xFF8D98AA);
        if (section == Section.MODS && selection == Selection.NONE) {
            drawRoundedCard(graphics, 648, 20, 196, 28, 0xFF303744, 9);
            drawRoundedCard(graphics, 649, 21, 194, 26, 0xFF1B222B, 8);
            graphics.drawString(font, "⌕", 658, 29, 0xFF7F8DA2);
        }
        graphics.fill(contentX() - 8, 98, right - 14, 99, 0xFF252B36);

        if (section == Section.SETTINGS) {
            int cardX = contentX() - 8;
            int cardWidth = contentWidth() + 8;
            drawRoundedCard(graphics, cardX, CONTENT_TOP, cardWidth, 40, 0xFF171D27, 12);
            drawRoundedCard(graphics, cardX, CONTENT_TOP + 44, cardWidth, 40, 0xFF151A23, 12);
            drawRoundedCard(graphics, cardX, CONTENT_TOP + 88, cardWidth, 40, 0xFF171D27, 12);
            drawRoundedCard(graphics, cardX, CONTENT_TOP + 132, cardWidth, 40, 0xFF151A23, 12);
            drawRoundedCard(graphics, cardX, CONTENT_TOP + 178, cardWidth, 40, 0xFF171D27, 12);
            graphics.drawString(font, "Accent colour", contentX(), CONTENT_TOP + 9, 0xFFE8ECF4);
            graphics.drawString(font, "Choose the highlight colour used across Vanta.", contentX(), CONTENT_TOP + 23, 0xFF8792A4);
            graphics.drawString(font, "Interface animations", contentX(), CONTENT_TOP + 53, 0xFFE8ECF4);
            graphics.drawString(font, "Animate panel, navigation and control feedback.", contentX(), CONTENT_TOP + 67, 0xFF8792A4);
            graphics.drawString(font, "Compact layout", contentX(), CONTENT_TOP + 97, 0xFFE8ECF4);
            graphics.drawString(font, "Reduce vertical spacing in feature lists.", contentX(), CONTENT_TOP + 111, 0xFF8792A4);
            graphics.drawString(font, "Setting descriptions", contentX(), CONTENT_TOP + 141, 0xFFE8ECF4);
            graphics.drawString(font, "Show explanatory text where integrations provide it.", contentX(), CONTENT_TOP + 155, 0xFF8792A4);
            graphics.drawString(font, "UI scale / spacing", contentX(), CONTENT_TOP + 187, 0xFFE8ECF4);
            graphics.drawString(font, "Adjust the overall interface size.", contentX(), CONTENT_TOP + 201, 0xFF8792A4);
        } else if (selection == Selection.BUILTIN_CATEGORY) {
            graphics.drawString(font, selectedCategory.name(), contentX(), 66, 0xFFE8EAF0);
            graphics.drawString(font, "Built-in Vanta Client features", contentX(), 86, 0xFF9296A3);
            List<Feature> features = FeatureRegistry.FEATURES.stream()
                    .filter(feature -> feature.getCategory() == selectedCategory).toList();
            int visibleRows = visibleRows(rowHeight());
            int end = Math.min(features.size(), firstSetting + visibleRows);
            int y = CONTENT_TOP + 14;
            for (int i = firstSetting; i < end; i++) {
                Feature feature = features.get(i);
                drawRoundedCard(graphics, contentX() - 8, y - 10, contentWidth() + 8,
                        rowHeight() - 4, 0xFF171D27, 12);
                if (feature.isEnabled()) {
                    graphics.fill(contentX() - 8, y - 7, contentX() - 5,
                            y + rowHeight() - 13, accent);
                }
                graphics.drawString(font, feature.getName(), contentX(), y, 0xFFE8ECF4);
                if (preferences.showDescriptions && !feature.getDescription().isBlank()) {
                    drawWrappedText(graphics, feature.getDescription(), contentX(), y + 13,
                            contentWidth() - 210, 0xFF858A98, 2);
                }
                y += rowHeight();
            }
        } else if (selection == Selection.BUILTIN_SETTINGS && selectedFeature != null) {
            graphics.drawString(font, selectedFeature.getName() + " settings", contentX(), 66, 0xFFE8ECF4);
            drawWrappedText(graphics, selectedFeature.getDescription(), contentX(), 86,
                    contentWidth() - 12, 0xFF929CAF, 2);
            List<name.vantaclient.client.gui.components.settings.Setting> featureSettings =
                    name.vantaclient.client.gui.components.settings.SettingRegistry.getSettings(selectedFeature);
            int y = CONTENT_TOP + 8;
            for (int i = firstSetting; i < Math.min(featureSettings.size(), firstSetting + visibleRows(34)); i++) {
                drawRoundedCard(graphics, contentX() - 8, y - 3, Math.min(340, contentWidth() + 8),
                        31, 0xFF171D27, 11);
                y += 34;
            }
        } else if (selection == Selection.INSTALLED_MOD) {
            ModContainer mod = installedMods.stream()
                    .filter(entry -> entry.getMetadata().getId().equals(selectedModId))
                    .findFirst().orElse(null);
            if (mod != null) {
                drawRoundedCard(graphics, contentX() - 8, 58, contentWidth() + 8, 62,
                        0xFF1A202B, 12);
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
                        contentWidth() - 72, 0xFF9296A3, 1);
                ModDescriptor descriptor = integrations.stream()
                        .filter(entry -> entry.id().equals(selectedModId)).findFirst().orElse(null);
                String description = descriptor != null ? descriptor.description() : mod.getMetadata().getDescription();
                if (preferences.showDescriptions && description != null && !description.isBlank()) {
                    drawWrappedText(graphics, description, contentX() + 54, 99,
                            contentWidth() - 72, 0xFF858A98, 1);
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
                            contentWidth() - 210, 0xFF858A98, 2);
                        }
                        y += rowHeight();
                    }
                }
            }
        } else {
            List<ExperimentalModHotLoader.AvailableMod> mods = filteredMods();
            graphics.drawString(font, "Installed mods", contentX(), 66, 0xFFE8EAF0);
            graphics.drawString(font, mods.size() + " matching mods · " + availableMods.size() + " total",
                    contentX(), 86, 0xFF9296A3);
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
                graphics.drawString(font, "No third-party mod JARs found in the active instance mods folder.",
                        contentX() + 8, startY + 18, 0xFF9296A3);
                graphics.drawString(font, "Add Fabric mods, then reopen this screen to refresh the list.",
                        contentX() + 8, startY + 34, 0xFF727D90);
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
                        withAlpha(accent, 0.22f + hover * 0.34f), 12);
                drawRoundedCard(graphics, x + 1, y + 1, cardWidth - 2, cardHeight - 2,
                        mixColor(0xFF1B222E, 0xFF263449, hover), 11);
                drawRoundedCard(graphics, x + 8, y + 7, cardWidth - 16, cardHeight - 44,
                        0xFF202A38, 10);
                Identifier iconTexture = modIconTextures.get(mod.id());
                int iconSize = 38;
                int iconX = x + (cardWidth - iconSize) / 2;
                int iconY = y + 18;
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
                graphics.drawString(font, fabricLoaded ? "Fabric loaded"
                        : hotLoadedIds.contains(mod.id()) ? "Session loaded"
                        : "Available", x + 10, y + 78,
                        fabricLoaded || hotLoadedIds.contains(mod.id()) ? 0xFF79B89A : 0xFF9BA7BA);
            }
            drawWrappedText(graphics, hotLoadStatus, contentX(), 486,
                    contentWidth() - 12, 0xFFB3B6C0, 2);
        }

        if (studioOpen && section == Section.MODS) {
            int panelAlpha = (int) (Math.max(0.72, Math.min(0.98, preferences.panelOpacity)) * 255.0);
            drawRoundedCard(graphics, 736, 72, 192, 164,
                    (panelAlpha << 24) | 0x00151B27, 12);
            drawRoundedCard(graphics, 736, 72, 192, 2, accent, 1);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
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
    public void onClose() {
        for (Identifier texture : modIconTextures.values()) {
            minecraft.getTextureManager().release(texture);
        }
        modIconTextures.clear();
        minecraft.setScreen(parent);
    }
}
