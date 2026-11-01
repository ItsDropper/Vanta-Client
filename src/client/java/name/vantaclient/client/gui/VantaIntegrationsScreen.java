package name.vantaclient.client.gui;

import name.vantaclient.api.ModDescriptor;
import name.vantaclient.api.VantaClientApi;
import name.vantaclient.api.setting.SettingDefinition;
import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.feature.Category;
import name.vantaclient.client.feature.Feature;
import name.vantaclient.client.feature.FeatureRegistry;
import name.vantaclient.client.integration.VantaModMenuIntegration;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Unified Vanta Client settings and installed-mod browser. */
public final class VantaIntegrationsScreen extends Screen {
    private static final int SIDEBAR_WIDTH = 190;
    private static final int ROW_HEIGHT = 38;
    private static final int CONTENT_TOP = 118;

    private enum Section { SETTINGS, MODS }
    private enum Selection { NONE, BUILTIN_CATEGORY, INSTALLED_MOD }

    private final Screen parent;
    private Section section = Section.SETTINGS;
    private Selection selection = Selection.NONE;
    private Category selectedCategory = Category.HUD;
    private String selectedModId;
    private List<ModContainer> installedMods = List.of();
    private List<ModDescriptor> integrations = List.of();
    private int sidebarScroll;
    private int firstSetting;
    private VantaClientPreferences preferences;

    public VantaIntegrationsScreen(Screen parent) {
        super(Component.literal("Vanta Client"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        preferences = VantaClientPreferences.get();
        refreshModList();

        addRenderableWidget(Button.builder(Component.literal("Settings"), button -> {
            section = Section.SETTINGS;
            selection = Selection.NONE;
            rebuildWidgets();
        }).bounds(34, 68, SIDEBAR_WIDTH - 24, 25).build());

        addRenderableWidget(Button.builder(Component.literal("Mods"), button -> {
            section = Section.MODS;
            selection = Selection.NONE;
            sidebarScroll = 0;
            rebuildWidgets();
        }).bounds(34, 99, SIDEBAR_WIDTH - 24, 25).build());

        if (section == Section.MODS) {
            addModNavigation();
        } else {
            addClientSettings();
        }
    }

    private void refreshModList() {
        integrations = VantaClientApi.getRegisteredMods();
        installedMods = FabricLoader.getInstance().getAllMods().stream()
                .filter(mod -> !mod.getMetadata().getId().equals("minecraft"))
                .filter(mod -> !mod.getMetadata().getId().equals("fabricloader"))
                .filter(mod -> !mod.getMetadata().getId().equals("dropperutils"))
                .sorted(Comparator.comparing(mod -> mod.getMetadata().getName(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void addModNavigation() {
        int y = 143;
        addRenderableWidget(Button.builder(Component.literal("HUD"), button -> selectCategory(Category.HUD))
                .bounds(34, y, SIDEBAR_WIDTH - 24, 22).build());
        y += 27;
        addRenderableWidget(Button.builder(Component.literal("Visual"), button -> selectCategory(Category.VISUAL))
                .bounds(34, y, SIDEBAR_WIDTH - 24, 22).build());
        y += 27;
        addRenderableWidget(Button.builder(Component.literal("Combat"), button -> selectCategory(Category.COMBAT))
                .bounds(34, y, SIDEBAR_WIDTH - 24, 22).build());
        y += 27;
        addRenderableWidget(Button.builder(Component.literal("Misc"), button -> selectCategory(Category.MISC))
                .bounds(34, y, SIDEBAR_WIDTH - 24, 22).build());

        y += 34;
        int available = Math.max(0, (height - y - 38) / 25);
        int start = Math.min(sidebarScroll, Math.max(0, installedMods.size() - available));
        int end = Math.min(installedMods.size(), start + available);
        for (int index = start; index < end; index++) {
            ModContainer mod = installedMods.get(index);
            String id = mod.getMetadata().getId();
            addRenderableWidget(Button.builder(
                    Component.literal(mod.getMetadata().getName()),
                    button -> selectInstalledMod(id))
                    .bounds(34, y, SIDEBAR_WIDTH - 24, 22).build());
            y += 25;
        }
    }

    private void selectCategory(Category category) {
        section = Section.MODS;
        selection = Selection.BUILTIN_CATEGORY;
        selectedCategory = category;
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
        addRenderableWidget(Button.builder(
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

        addRenderableWidget(Button.builder(Component.literal("−"), button -> {
            preferences.uiScale = Math.max(0.8, Math.round((preferences.uiScale - 0.05) * 100.0) / 100.0);
            VantaClientPreferences.save();
            button.setMessage(Component.literal("−"));
            rebuildWidgets();
        }).bounds(x, y, 28, 22).build());
        addRenderableWidget(Button.builder(Component.literal(String.format(Locale.ROOT, "%.2fx", preferences.uiScale)),
                button -> {}).bounds(x + 34, y, 102, 22).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            preferences.uiScale = Math.min(1.2, Math.round((preferences.uiScale + 0.05) * 100.0) / 100.0);
            VantaClientPreferences.save();
            rebuildWidgets();
        }).bounds(x + 142, y, 28, 22).build());
    }

    private void addPreferenceToggle(String name, boolean current, java.util.function.Consumer<Boolean> setter,
                                     int x, int y) {
        addRenderableWidget(Button.builder(Component.literal(current ? "On" : "Off"), button -> {
            boolean next = !current;
            setter.accept(next);
            button.setMessage(Component.literal(next ? "On" : "Off"));
            rebuildWidgets();
        }).bounds(x, y, 170, 22).build());
    }

    private int contentX() {
        return 34 + SIDEBAR_WIDTH + 30;
    }

    private int contentWidth() {
        return Math.max(160, width - contentX() - 44);
    }

    private void addBuiltInFeatures() {
        List<Feature> features = FeatureRegistry.FEATURES.stream()
                .filter(feature -> feature.getCategory() == selectedCategory)
                .toList();
        int visibleRows = Math.max(1, (height - CONTENT_TOP - 48) / ROW_HEIGHT);
        int maxFirst = Math.max(0, features.size() - visibleRows);
        firstSetting = Math.min(firstSetting, maxFirst);
        int end = Math.min(features.size(), firstSetting + visibleRows);
        int y = CONTENT_TOP + 8;
        for (int i = firstSetting; i < end; i++) {
            Feature feature = features.get(i);
            int controlX = contentX() + contentWidth() - 190;
            addRenderableWidget(Button.builder(Component.literal(feature.isEnabled() ? "Enabled" : "Disabled"),
                    button -> {
                        feature.toggle();
                        feature.saveConfig();
                        DropperUtilsConfig.save();
                        button.setMessage(Component.literal(feature.isEnabled() ? "Enabled" : "Disabled"));
                    }).bounds(controlX, y, 92, 22).build());

            if (parent instanceof ClickGuiScreen clickGui) {
                addRenderableWidget(Button.builder(Component.literal("Configure"), button -> {
                    minecraft.setScreen(parent);
                    clickGui.openSettings(feature, controlX, y);
                }).bounds(controlX + 98, y, 86, 22).build());
            }
            y += ROW_HEIGHT;
        }
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

        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            int x = contentX() + contentWidth() - 190;
            addRenderableWidget(Button.builder(Component.literal("Open Mod Menu"),
                    button -> minecraft.setScreen(VantaModMenuIntegration.openModsScreen(this)))
                    .bounds(x, CONTENT_TOP + 8, 180, 24).build());
        }
    }

    private void addIntegrationSettings(ModDescriptor descriptor) {
        List<SettingDefinition<?>> settings = descriptor.settings();
        int visibleRows = Math.max(1, (height - CONTENT_TOP - 48) / ROW_HEIGHT);
        int maxFirst = Math.max(0, settings.size() - visibleRows);
        firstSetting = Math.max(0, Math.min(firstSetting, maxFirst));
        int end = Math.min(settings.size(), firstSetting + visibleRows);
        int y = CONTENT_TOP + 8;
        for (int i = firstSetting; i < end; i++) {
            addSettingControl(settings.get(i), y);
            y += ROW_HEIGHT;
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
            addRenderableWidget(Button.builder(Component.literal("Unavailable"), button -> {})
                    .bounds(controlX, y, controlWidth, 22).build());
            return;
        }

        switch (setting.type()) {
            case BOOLEAN -> addRenderableWidget(Button.builder(
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
                Button valueButton = Button.builder(Component.literal(formatNumber(
                        value instanceof Number number ? number.doubleValue() : minimum)), button -> {})
                        .bounds(controlX + 30, y, 120, 22).build();
                addRenderableWidget(Button.builder(Component.literal("−"), button -> {
                    updateNumeric(setting, minimum, maximum, -step, valueButton);
                }).bounds(controlX, y, 26, 22).build());
                addRenderableWidget(valueButton);
                addRenderableWidget(Button.builder(Component.literal("+"), button -> {
                    updateNumeric(setting, minimum, maximum, step, valueButton);
                }).bounds(controlX + 154, y, 26, 22).build());
            }
            case ENUM -> {
                Object[] constants = setting.valueClass().getEnumConstants();
                addRenderableWidget(Button.builder(Component.literal(String.valueOf(value)), button -> {
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
                EditBox input = new EditBox(font, controlX, y, controlWidth, 22,
                        Component.literal(setting.name()));
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
                                      double delta, Button label) {
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int accent = preferences == null ? 0xFF718BFF : preferences.accent.color();
        graphics.fill(0, 0, width, height, 0xD9000000);

        int left = 20, top = 18, right = width - 20, bottom = height - 18;
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0x99000000);
        graphics.fill(left, top, right, bottom, 0xFF101116);
        graphics.fill(left, top, right, top + 3, accent);
        graphics.fill(left + 12, top + 12, left + 12 + SIDEBAR_WIDTH + 12, bottom - 12, 0xFF171920);
        graphics.fill(left + 12 + SIDEBAR_WIDTH + 12, top + 12,
                left + 13 + SIDEBAR_WIDTH + 12, bottom - 12, 0xFF30333D);

        graphics.drawString(font, "VANTA", 34, 34, accent);
        graphics.drawString(font, "CLIENT", 79, 34, 0xFFF0F1F5);
        graphics.drawString(font, section == Section.SETTINGS ? "SETTINGS" : "MODS",
                contentX(), 40, 0xFFF2F3F8);

        if (section == Section.SETTINGS) {
            graphics.drawString(font, "Personalize the Vanta Client interface.",
                    contentX(), 66, 0xFF9296A3);
            graphics.drawString(font, "Accent colour", contentX(), CONTENT_TOP + 14, 0xFFE8EAF0);
            graphics.drawString(font, "Interface animations", contentX(), CONTENT_TOP + 62, 0xFFE8EAF0);
            graphics.drawString(font, "Compact layout", contentX(), CONTENT_TOP + 104, 0xFFE8EAF0);
            graphics.drawString(font, "Setting descriptions", contentX(), CONTENT_TOP + 146, 0xFFE8EAF0);
            graphics.drawString(font, "UI scale", contentX(), CONTENT_TOP + 194, 0xFFE8EAF0);
        } else if (selection == Selection.BUILTIN_CATEGORY) {
            graphics.drawString(font, selectedCategory.name(), contentX(), 66, 0xFFE8EAF0);
            graphics.drawString(font, "Built-in Vanta Client features", contentX(), 86, 0xFF9296A3);
            List<Feature> features = FeatureRegistry.FEATURES.stream()
                    .filter(feature -> feature.getCategory() == selectedCategory).toList();
            int visibleRows = Math.max(1, (height - CONTENT_TOP - 48) / ROW_HEIGHT);
            int end = Math.min(features.size(), firstSetting + visibleRows);
            int y = CONTENT_TOP + 14;
            for (int i = firstSetting; i < end; i++) {
                Feature feature = features.get(i);
                graphics.drawString(font, feature.getName(), contentX(), y, 0xFFE8EAF0);
                if (preferences.showDescriptions && !feature.getDescription().isBlank()) {
                    graphics.drawString(font, feature.getDescription(), contentX(), y + 13, 0xFF858A98);
                }
                y += ROW_HEIGHT;
            }
        } else if (selection == Selection.INSTALLED_MOD) {
            ModContainer mod = installedMods.stream()
                    .filter(entry -> entry.getMetadata().getId().equals(selectedModId))
                    .findFirst().orElse(null);
            if (mod != null) {
                graphics.drawString(font, mod.getMetadata().getName(), contentX(), 66, 0xFFE8EAF0);
                graphics.drawString(font, mod.getMetadata().getId() + "  •  " +
                        mod.getMetadata().getVersion().getFriendlyString(), contentX(), 86, 0xFF9296A3);
                ModDescriptor descriptor = integrations.stream()
                        .filter(entry -> entry.id().equals(selectedModId)).findFirst().orElse(null);
                String description = descriptor != null ? descriptor.description() : mod.getMetadata().getDescription();
                if (preferences.showDescriptions && description != null && !description.isBlank()) {
                    graphics.drawString(font, description, contentX(), 105, 0xFF858A98);
                }
                if (descriptor == null) {
                    graphics.drawString(font, FabricLoader.getInstance().isModLoaded("modmenu")
                            ? "This mod has not registered Vanta settings. Open Mod Menu to access its config, if provided."
                            : "This mod has not registered Vanta settings. Install Mod Menu to access its config, if provided.",
                            contentX(), CONTENT_TOP + 20, 0xFFB3B6C0);
                } else {
                    List<SettingDefinition<?>> settings = descriptor.settings();
                    int visibleRows = Math.max(1, (height - CONTENT_TOP - 48) / ROW_HEIGHT);
                    int end = Math.min(settings.size(), firstSetting + visibleRows);
                    int y = CONTENT_TOP + 14;
                    for (int i = firstSetting; i < end; i++) {
                        SettingDefinition<?> setting = settings.get(i);
                        graphics.drawString(font, setting.name(), contentX(), y, 0xFFE8EAF0);
                        if (preferences.showDescriptions && !setting.description().isBlank()) {
                            graphics.drawString(font, setting.description(), contentX(), y + 13, 0xFF858A98);
                        }
                        y += ROW_HEIGHT;
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

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (section == Section.MODS && mouseX < 34 + SIDEBAR_WIDTH + 12) {
            int available = Math.max(1, (height - 340) / 25);
            int maxScroll = Math.max(0, installedMods.size() - available);
            sidebarScroll = Math.max(0, Math.min(maxScroll,
                    sidebarScroll - (int) Math.signum(scrollY)));
            rebuildWidgets();
            return true;
        }
        if (section == Section.MODS && selection == Selection.BUILTIN_CATEGORY) {
            int count = (int) FeatureRegistry.FEATURES.stream()
                    .filter(feature -> feature.getCategory() == selectedCategory).count();
            int maxFirst = Math.max(0, count - Math.max(1, (height - CONTENT_TOP - 48) / ROW_HEIGHT));
            firstSetting = Math.max(0, Math.min(maxFirst, firstSetting - (int) Math.signum(scrollY)));
            rebuildWidgets();
            return true;
        }
        if (section == Section.MODS && selection == Selection.INSTALLED_MOD) {
            ModDescriptor descriptor = integrations.stream()
                    .filter(entry -> entry.id().equals(selectedModId)).findFirst().orElse(null);
            if (descriptor != null) {
                int maxFirst = Math.max(0, descriptor.settings().size()
                        - Math.max(1, (height - CONTENT_TOP - 48) / ROW_HEIGHT));
                firstSetting = Math.max(0, Math.min(maxFirst,
                        firstSetting - (int) Math.signum(scrollY)));
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
