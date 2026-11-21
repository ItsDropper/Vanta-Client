package name.vantaclient.client.gui.menu;

import name.vantaclient.api.setting.SettingDefinition;
import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.feature.Feature;
import name.vantaclient.client.gui.VantaNotificationManager;
import name.vantaclient.client.gui.components.settings.BooleanSetting;
import name.vantaclient.client.gui.components.settings.ChoiceSetting;
import name.vantaclient.client.gui.components.settings.Setting;
import name.vantaclient.client.gui.components.settings.SettingRegistry;
import name.vantaclient.client.gui.components.settings.SliderSetting;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.menu.MenuIcons.Icon;
import name.vantaclient.client.init.FeatureKeybinds;
import name.vantaclient.client.integration.ModConfigDiscovery;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;

import java.util.Arrays;
import java.util.List;

/** Settings for one mod: stacked rows with the control on the right. */
public final class ModSettingsPage {
    private ModSettingsPage() {}

    static final int TOGGLE_WIDTH = 116;
    static final int SLIDER_WIDTH = 290;
    static final int DROPDOWN_WIDTH = 210;
    static final int KEY_WIDTH = 150;

    public static VantaUiNode build(MenuHost host, ModEntry entry) {
        VantaUiNode scroll = VantaUiNode.column(12, 24).flex(1).scrollable();
        Font font = host.font();
        if (entry.isBuiltIn()) {
            addFeature(scroll, host, font, entry.feature());
        } else if (entry.descriptor() != null && !entry.descriptor().settings().isEmpty()) {
            addDescriptor(scroll, host, font, entry);
        } else {
            addInstalledInfo(scroll, host, font, entry);
        }
        return scroll;
    }

    static int descriptionWidth(MenuHost host, int controlWidth) {
        return Math.max(120, host.contentWidth() - controlWidth - 32 - 16 - 24);
    }

    static VantaUiNode toggleControl(java.util.function.BooleanSupplier value, java.util.function.Consumer<Boolean> setter) {
        return VantaUiNode.toggle("", value, setter).menuStyle().height(30);
    }

    private static void addFeature(VantaUiNode page, MenuHost host, Font font, Feature feature) {
        boolean descriptions = VantaClientPreferences.get().showDescriptions;
        int toggleDesc = descriptionWidth(host, TOGGLE_WIDTH);
        page.add(MenuComponents.settingRow(font, "Enabled",
                descriptions ? feature.getDescription() : null,
                toggleControl(feature::isEnabled, enabled -> {
                    if (feature.isEnabled() != enabled) feature.toggle();
                    feature.saveConfig();
                    DropperUtilsConfig.save();
                }), TOGGLE_WIDTH, toggleDesc));

        KeyMapping mapping = FeatureKeybinds.mappingFor(feature);
        if (mapping != null) {
            page.add(MenuComponents.settingRow(font, "Keybind",
                    descriptions ? "Press this key to toggle " + feature.getName() + "." : null,
                    KeybindsPage.keyButton(host, mapping), KEY_WIDTH, descriptionWidth(host, KEY_WIDTH)));
        }

        for (Setting setting : SettingRegistry.getSettings(feature)) {
            if (setting instanceof SliderSetting slider) {
                String unit = slider.getUnit() == null || slider.getUnit().isBlank() ? "" : " (" + slider.getUnit() + ")";
                double step = Math.max(0.0, (slider.getMax() - slider.getMin()) / 100.0);
                page.add(MenuComponents.settingRow(font, slider.getName() + unit, null,
                        VantaUiNode.slider("", slider.getMin(), slider.getMax(), step,
                                () -> safeNumber(slider), slider.getSetter()::accept).menuStyle().height(30),
                        SLIDER_WIDTH, descriptionWidth(host, SLIDER_WIDTH)));
            } else if (setting instanceof BooleanSetting toggle) {
                page.add(MenuComponents.settingRow(font, toggle.getName(), null,
                        toggleControl(() -> safeBoolean(toggle), toggle.getSetter()::accept),
                        TOGGLE_WIDTH, toggleDesc));
            } else if (setting instanceof ChoiceSetting choice) {
                page.add(MenuComponents.settingRow(font, choice.getName(), null,
                        VantaUiNode.dropdown("", choice.getOptions(), choice.getGetter(),
                                choice.getSetter()::accept).menuStyle().height(30),
                        DROPDOWN_WIDTH, descriptionWidth(host, DROPDOWN_WIDTH)));
            }
        }
    }

    private static double safeNumber(SliderSetting setting) {
        try {
            Double value = setting.getGetter().get();
            return value == null || !Double.isFinite(value) ? setting.getMin() : value;
        } catch (RuntimeException exception) {
            return setting.getMin();
        }
    }

    private static boolean safeBoolean(BooleanSetting setting) {
        try {
            return Boolean.TRUE.equals(setting.getGetter().get());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    // ---- settings contributed through the Vanta API ------------------------------------------

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addDescriptor(VantaUiNode page, MenuHost host, Font font, ModEntry entry) {
        boolean descriptions = VantaClientPreferences.get().showDescriptions;
        for (SettingDefinition setting : entry.descriptor().settings()) {
            String description = descriptions ? setting.description() : null;
            VantaUiNode control = null;
            int controlWidth = TOGGLE_WIDTH;
            switch (setting.type()) {
                case BOOLEAN -> control = toggleControl(() -> Boolean.TRUE.equals(readValue(setting)),
                        value -> writeValue(setting, value));
                case INTEGER, DECIMAL -> {
                    double minimum = setting.minimum() == null ? 0.0 : setting.minimum();
                    double maximum = setting.maximum() == null ? minimum : setting.maximum();
                    double step = setting.type() == SettingDefinition.Type.INTEGER
                            ? 1.0 : Math.max(0.01, (maximum - minimum) / 100.0);
                    controlWidth = SLIDER_WIDTH;
                    control = VantaUiNode.slider("", minimum, maximum, step, () -> {
                        Object value = readValue(setting);
                        return value instanceof Number number ? number.doubleValue() : minimum;
                    }, value -> {
                        if (setting.type() == SettingDefinition.Type.INTEGER) {
                            writeValue(setting, Integer.valueOf((int) Math.round(value)));
                        } else {
                            writeValue(setting, Double.valueOf(value));
                        }
                    }).menuStyle().height(30);
                }
                case ENUM -> {
                    List<String> options = setting.options();
                    if (!options.isEmpty()) {
                        controlWidth = DROPDOWN_WIDTH;
                        control = VantaUiNode.dropdown("", options, () -> {
                            Object value = readValue(setting);
                            return value instanceof Enum<?> enumeration ? enumeration.name() : options.get(0);
                        }, value -> {
                            Object[] constants = setting.valueClass().getEnumConstants();
                            if (constants == null) return;
                            Arrays.stream(constants)
                                    .filter(option -> ((Enum<?>) option).name().equals(value))
                                    .findFirst().ifPresent(option -> writeValue(setting, option));
                        }).menuStyle().height(30);
                    }
                }
                case STRING -> {
                    int maximumLength = setting.maximum() == null ? 128
                            : Math.max(1, (int) Math.floor(setting.maximum()));
                    controlWidth = DROPDOWN_WIDTH;
                    VantaUiNode box = VantaUiNode.row(0, 0).height(30).background(MenuTheme.FIELD)
                            .radius(8).border(0xFF2E333D);
                    box.add(VantaUiNode.textField(setting.name(), () -> {
                        Object value = readValue(setting);
                        return value instanceof String string ? string : "";
                    }, value -> writeValue(setting, value), maximumLength).menuStyle().flex(1));
                    control = box;
                }
            }
            if (control == null) continue;
            page.add(MenuComponents.settingRow(font, setting.name(), description, control,
                    controlWidth, descriptionWidth(host, controlWidth)));
        }
        page.add(MenuComponents.button(font, "Restore defaults", Icon.RESET, false, () -> {
            for (SettingDefinition setting : entry.descriptor().settings()) {
                writeValue(setting, setting.defaultValue());
            }
            host.rebuild();
        }));
    }

    private static Object readValue(SettingDefinition<?> setting) {
        try {
            return setting.value();
        } catch (RuntimeException exception) {
            return setting.defaultValue();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void writeValue(SettingDefinition setting, Object value) {
        try {
            setting.set(value);
        } catch (RuntimeException exception) {
            System.err.println("[Vanta Client] Could not update integration setting "
                    + setting.id() + ": " + exception.getMessage());
        }
    }

    // ---- installed mods without settings Vanta can edit --------------------------------------

    private static void addInstalledInfo(VantaUiNode page, MenuHost host, Font font, ModEntry entry) {
        int plain = descriptionWidth(host, 160);
        page.add(MenuComponents.settingRow(font, "Mod ID", entry.id(),
                VantaUiNode.spacer(1), 1, plain));
        if (!entry.version().isBlank()) {
            page.add(MenuComponents.settingRow(font, "Version", entry.version(),
                    VantaUiNode.spacer(1), 1, plain));
        }
        if (entry.hasOwnConfigScreen()) {
            page.add(MenuComponents.settingRow(font, "Settings",
                    "This mod has its own settings screen.",
                    MenuComponents.button(font, "Open", Icon.ARROW_RIGHT, true, () -> {
                        Screen screen = ModConfigDiscovery.open(entry.configFactory(), host.screen());
                        if (screen != null) host.minecraft().setScreen(screen);
                        else VantaNotificationManager.warning("Settings unavailable",
                                entry.name() + " did not provide a settings screen.");
                    }), 110, plain));
        } else {
            page.add(MenuComponents.settingRow(font, "Settings",
                    "Vanta cannot edit this mod's options. Configure it with the mod's own config file.",
                    VantaUiNode.spacer(1), 1, plain));
        }
    }
}
