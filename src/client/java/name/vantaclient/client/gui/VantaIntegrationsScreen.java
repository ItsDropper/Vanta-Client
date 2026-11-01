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
import name.vantaclient.client.gui.components.VantaPanelButton;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Unified Vanta Client settings and installed-mod browser. */
public class VantaIntegrationsScreen extends Screen {
    private static final int SIDEBAR_WIDTH = 190;
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
    private Map<String, Object> discoveredConfigFactories = Map.of();
    private List<ModDescriptor> integrations = List.of();
    private int sidebarScroll;
    private int firstSetting;
    private float openProgress;
    private VantaClientPreferences preferences;

    public VantaIntegrationsScreen(Screen parent) {
        super(Component.literal("Vanta Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        preferences = VantaClientPreferences.get();
        openProgress = preferences.animations ? 0.0f : 1.0f;
        refreshModList();

        addPanelButton("Settings", 34, 68, SIDEBAR_WIDTH - 24, 25,
                () -> {
                    section = Section.SETTINGS;
                    selection = Selection.NONE;
                    rebuildWidgets();
                }, () -> section == Section.SETTINGS);
        addPanelButton("Mods", 34, 99, SIDEBAR_WIDTH - 24, 25,
                () -> {
                    section = Section.MODS;
                    selection = Selection.NONE;
                    sidebarScroll = 0;
                    rebuildWidgets();
                }, () -> section == Section.MODS);

        if (section == Section.MODS) {
            addModNavigation();
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
    }

    private void addModNavigation() {
        int y = 143;
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

        y += 54;
        int available = Math.max(0, (504 - y - 38) / 25);
        int startIndex = Math.min(sidebarScroll, Math.max(0, installedMods.size() - available));
        int endIndex = Math.min(installedMods.size(), startIndex + available);
        for (int index = startIndex; index < endIndex; index++) {
            ModContainer mod = installedMods.get(index);
            String id = mod.getMetadata().getId();
            addPanelButton(mod.getMetadata().getName(), 34, y, SIDEBAR_WIDTH - 24, 22,
                    () -> selectInstalledMod(id),
                    () -> selection == Selection.INSTALLED_MOD && id.equals(selectedModId));
            y += 25;
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
        addRenderableWidget(VantaPanelButton.builder(Component.literal(current ? "On" : "Off"), button -> {
            boolean next = !current;
            setter.accept(next);
            button.setMessage(Component.literal(next ? "On" : "Off"));
            rebuildWidgets();
        }).bounds(x, y, 170, 22).build());
    }

    private int accentColor() {
        return preferences == null ? 0xFF718BFF : preferences.accent.color();
    }

    private VantaPanelButton addPanelButton(String label, int x, int y, int buttonWidth, int buttonHeight,
                                            Runnable action) {
        return addPanelButton(label, x, y, buttonWidth, buttonHeight, action, () -> false);
    }

    private VantaPanelButton addPanelButton(String label, int x, int y, int buttonWidth, int buttonHeight,
                                            Runnable action, java.util.function.BooleanSupplier selected) {
        VantaPanelButton button = new VantaPanelButton(x, y, buttonWidth, buttonHeight, label,
                action, selected, this::accentColor);
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
            addRenderableWidget(VantaPanelButton.builder(
                    Component.literal(feature.isEnabled() ? "Enabled" : "Disabled"),
                    button -> {
                        feature.toggle();
                        feature.saveConfig();
                        DropperUtilsConfig.save();
                        button.setMessage(Component.literal(feature.isEnabled() ? "Enabled" : "Disabled"));
                    }).bounds(controlX, rowY, 88, 22).build());
            if (!name.vantaclient.client.gui.components.settings.SettingRegistry
                    .getSettings(feature).isEmpty()) {
                addRenderableWidget(VantaPanelButton.builder(Component.literal("Settings"),
                        button -> selectFeatureSettings(feature))
                        .bounds(controlX + 94, rowY, 90, 22).build());
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
        int controlWidth = Math.min(300, contentWidth());
        addRenderableWidget(VantaPanelButton.builder(
                Component.literal(setting.getName() + ": " + (current ? "On" : "Off")),
                button -> {
                    boolean next = !setting.getGetter().get();
                    setting.getSetter().accept(next);
                    button.setMessage(Component.literal(setting.getName() + ": " + (next ? "On" : "Off")));
                }).bounds(contentX(), y, controlWidth, 26).build());
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
            case BOOLEAN -> addRenderableWidget(VantaPanelButton.builder(
                    Component.literal(Boolean.TRUE.equals(value) ? "On" : "Off"), button -> {
                        try {
                            setting.set(!Boolean.TRUE.equals(setting.value()));
                            button.setMessage(Component.literal(Boolean.TRUE.equals(setting.value()) ? "On" : "Off"));
                        } catch (RuntimeException ignored) {
                            button.setMessage(Component.literal("Error"));
                        }
                    }).bounds(controlX, y, controlWidth, 22).build());
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
        int alpha = (int) (0xD9 * Math.min(1.0f, openProgress + 0.16f * partialTick));
        graphics.fill(0, 0, width, height, (alpha << 24));

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) VantaPanelButton.layoutOffsetX(),
                (float) VantaPanelButton.layoutOffsetY());
        graphics.pose().scale(VantaPanelButton.layoutScale(), VantaPanelButton.layoutScale());
        int left = 20, top = 18, right = 940, bottom = 522;
        graphics.fill(left - 6, top - 6, right + 6, bottom + 6, 0x55000000);
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0x99000000);
        graphics.fill(left, top, right, bottom, 0xFF0D1016);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0xFF10141C);
        graphics.fill(left, top, right, top + 3, accent);
        graphics.fill(left, top + 3, right, top + 53, 0xFF141923);
        graphics.fill(left + 12, top + 12, left + 12 + SIDEBAR_WIDTH + 12, bottom - 12, 0xFF151A23);
        graphics.fill(left + 12 + SIDEBAR_WIDTH + 14, top + 12,
                left + 13 + SIDEBAR_WIDTH + 14, bottom - 12, 0xFF292F3A);
        graphics.fill(left + 12 + SIDEBAR_WIDTH + 20, top + 62,
                right - 12, bottom - 12, 0xFF11151D);
        graphics.fill(left + 12, top + 61, right - 12, top + 62, 0xFF252B36);

        graphics.fill(34, 31, 37, 44, accent);
        graphics.drawString(font, "VANTA", 45, 28, 0xFFF4F6FC);
        graphics.drawString(font, "CLIENT", 90, 28, 0xFF9AA4B6);
        graphics.drawString(font, "MINECRAFT MOD", 34, 47, 0xFF727D90);
        graphics.drawString(font, "LOCAL CONFIGURATION", 810, 34, 0xFF778296);
        if (section == Section.MODS) {
            graphics.drawString(font, "BUILT-IN", 34, 132, 0xFF858A98);
            graphics.drawString(font, "INSTALLED MODS", 34, 266, 0xFF858A98);
        }
        graphics.drawString(font, section == Section.SETTINGS ? "SETTINGS" : "MODS",
                contentX(), 27, 0xFFF2F4F9);
        graphics.drawString(font, section == Section.SETTINGS
                ? "Appearance and interface preferences"
                : "Built-in features and installed integrations",
                contentX(), 43, 0xFF8D98AA);
        graphics.fill(contentX() - 8, 98, right - 14, 99, 0xFF252B36);

        if (section == Section.SETTINGS) {
            int cardX = contentX() - 8;
            int cardWidth = contentWidth() + 8;
            drawCard(graphics, cardX, CONTENT_TOP, cardWidth, 38, 0xFF171D27);
            drawCard(graphics, cardX, CONTENT_TOP + 44, cardWidth, 38, 0xFF151A23);
            drawCard(graphics, cardX, CONTENT_TOP + 86, cardWidth, 38, 0xFF171D27);
            drawCard(graphics, cardX, CONTENT_TOP + 128, cardWidth, 38, 0xFF151A23);
            drawCard(graphics, cardX, CONTENT_TOP + 174, cardWidth, 38, 0xFF171D27);
            graphics.drawString(font, "Accent colour", contentX(), CONTENT_TOP + 14, 0xFFE8ECF4);
            graphics.drawString(font, "Interface animations", contentX(), CONTENT_TOP + 58, 0xFFE8ECF4);
            graphics.drawString(font, "Compact layout", contentX(), CONTENT_TOP + 100, 0xFFE8ECF4);
            graphics.drawString(font, "Setting descriptions", contentX(), CONTENT_TOP + 142, 0xFFE8ECF4);
            graphics.drawString(font, "UI scale / spacing", contentX(), CONTENT_TOP + 188, 0xFFE8ECF4);
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
                drawCard(graphics, contentX() - 8, y - 10, contentWidth() + 8,
                        rowHeight() - 4, 0xFF171D27);
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
                drawCard(graphics, contentX() - 8, y - 3, Math.min(340, contentWidth() + 8),
                        31, 0xFF171D27);
                y += 34;
            }
        } else if (selection == Selection.INSTALLED_MOD) {
            ModContainer mod = installedMods.stream()
                    .filter(entry -> entry.getMetadata().getId().equals(selectedModId))
                    .findFirst().orElse(null);
            if (mod != null) {
                graphics.drawString(font, mod.getMetadata().getName(), contentX(), 66, 0xFFE8EAF0);
                drawWrappedText(graphics, mod.getMetadata().getId() + "  •  " +
                        mod.getMetadata().getVersion().getFriendlyString(), contentX(), 86,
                        contentWidth() - 12, 0xFF9296A3, 1);
                ModDescriptor descriptor = integrations.stream()
                        .filter(entry -> entry.id().equals(selectedModId)).findFirst().orElse(null);
                String description = descriptor != null ? descriptor.description() : mod.getMetadata().getDescription();
                if (preferences.showDescriptions && description != null && !description.isBlank()) {
                    drawWrappedText(graphics, description, contentX(), 105,
                            contentWidth() - 12, 0xFF858A98, 2);
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
            graphics.drawString(font, "Installed mods", contentX(), 66, 0xFFE8EAF0);
            graphics.drawString(font, installedMods.size() + " loaded mods detected by Fabric Loader",
                    contentX(), 86, 0xFF9296A3);
            graphics.drawString(font, "Select a mod in the sidebar to view its details and available settings.",
                    contentX(), CONTENT_TOP + 20, 0xFFB3B6C0);
        }

        graphics.pose().popMatrix();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawCard(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
        graphics.fill(x, y, x + width, y + 1, 0xFF252D39);
        graphics.fill(x, y, x + 1, y + height, 0xFF252D39);
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
        if (section == Section.MODS && mouseX < 34 + SIDEBAR_WIDTH + 12) {
            int available = Math.max(0, (504 - 143 - 4 * 27 - 54 - 38) / 25);
            int maxScroll = Math.max(0, installedMods.size() - available);
            sidebarScroll = Math.max(0, Math.min(maxScroll,
                    sidebarScroll - (int) Math.signum(scrollY)));
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
        minecraft.setScreen(parent);
    }
}
