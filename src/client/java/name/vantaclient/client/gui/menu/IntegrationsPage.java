package name.vantaclient.client.gui.menu;

import name.vantaclient.api.ModDescriptor;
import name.vantaclient.api.VantaClientApi;
import name.vantaclient.api.setting.SettingDefinition;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.menu.MenuIcons.Icon;

import java.util.Arrays;
import java.util.List;

/** Vanta API integrations rendered as native pages inside the main Vanta settings shell. */
public final class IntegrationsPage {
    private IntegrationsPage() {}

    public static VantaUiNode build(MenuHost host, String selectedId) {
        VantaUiNode page = VantaUiNode.column(12, 18).flex(1);
        List<ModDescriptor> integrations = VantaClientApi.getRegisteredMods();
        if (selectedId == null) {
            page.add(VantaUiNode.text("MOD INTEGRATIONS", MenuTheme.TEXT_MUTED)
                    .font(MenuTheme.FONT_SMALL).height(16));
            page.add(VantaUiNode.text("Settings contributed by mods through the Vanta Client API.",
                    MenuTheme.TEXT_DIM).font(MenuTheme.FONT_UI).height(22));
            VantaUiNode scroll = VantaUiNode.column(10, 12).flex(1).scrollable();
            if (integrations.isEmpty()) {
                scroll.add(VantaUiNode.spacer(28));
                scroll.add(VantaUiNode.canvas((g, f, x, y, w, h, st) ->
                        MenuTheme.textCentered(g, f, "No Vanta integrations are registered.",
                                x + w / 2, y + h / 2, MenuTheme.TEXT_MUTED, MenuTheme.FONT_UI))
                        .height(32));
            } else {
                scroll.add(VantaUiNode.text(integrations.size() + " registered integration"
                        + (integrations.size() == 1 ? "" : "s"), MenuTheme.TEXT_MUTED)
                        .font(MenuTheme.FONT_SMALL).height(18));
                for (ModDescriptor descriptor : integrations) {
                    VantaUiNode row = VantaUiNode.row(16, 16).height(72)
                            .background(MenuTheme.ROW).border(0x26FFFFFF).radius(12)
                            .align(VantaUiNode.Alignment.CENTER);
                    VantaUiNode labels = VantaUiNode.column(3, 0).flex(1);
                    labels.add(VantaUiNode.text(descriptor.displayName(), MenuTheme.TEXT)
                            .font(MenuTheme.FONT_MEDIUM).height(18));
                    String detail = descriptor.id()
                            + (descriptor.version().isBlank() ? "" : "  ·  " + descriptor.version())
                            + "  ·  " + descriptor.settings().size() + " settings";
                    labels.add(VantaUiNode.text(detail, MenuTheme.TEXT_MUTED)
                            .font(MenuTheme.FONT_SMALL).height(16));
                    if (!descriptor.description().isBlank()) {
                        labels.add(VantaUiNode.text(descriptor.description(), MenuTheme.TEXT_DIM)
                                .font(MenuTheme.FONT_SMALL).height(16));
                    }
                    row.add(labels);
                    row.add(MenuComponents.button(host.font(), "Configure", Icon.PREFERENCES, true,
                            () -> host.navigate(new MenuHost.Route(MenuHost.Page.INTEGRATION, descriptor.id())))
                            .size(116, 36));
                    scroll.add(row);
                }
            }
            page.add(scroll);
            return page;
        }

        ModDescriptor descriptor = integrations.stream()
                .filter(candidate -> candidate.id().equals(selectedId)).findFirst().orElse(null);
        if (descriptor == null) {
            page.add(VantaUiNode.text("This integration is no longer registered.",
                    MenuTheme.TEXT_MUTED).font(MenuTheme.FONT_UI));
            page.add(MenuComponents.button(host.font(), "Back to integrations", Icon.ARROW_LEFT,
                    false, () -> host.navigate(MenuHost.Route.of(MenuHost.Page.INTEGRATIONS))));
            return page;
        }

        page.add(VantaUiNode.text(descriptor.id()
                + (descriptor.version().isBlank() ? "" : "  ·  " + descriptor.version()),
                MenuTheme.TEXT_MUTED).font(MenuTheme.FONT_SMALL).height(18));
        if (!descriptor.description().isBlank()) {
            page.add(VantaUiNode.text(descriptor.description(), MenuTheme.TEXT_DIM)
                    .font(MenuTheme.FONT_UI).height(34));
        }
        VantaUiNode scroll = VantaUiNode.column(10, 12).flex(1).scrollable();
        if (descriptor.settings().isEmpty()) {
            scroll.add(VantaUiNode.text("This integration does not expose configurable settings.",
                    MenuTheme.TEXT_MUTED).font(MenuTheme.FONT_UI));
        } else {
            for (SettingDefinition<?> definition : descriptor.settings()) {
                addSetting(scroll, host, definition);
            }
            scroll.add(MenuComponents.button(host.font(), "Restore defaults", Icon.RESET, false, () -> {
                for (SettingDefinition<?> setting : descriptor.settings()) {
                    writeValue(setting, setting.defaultValue());
                }
                host.rebuild();
            }));
        }
        page.add(scroll);
        return page;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addSetting(VantaUiNode page, MenuHost host, SettingDefinition setting) {
        String description = VantaClientPreferences.get().showDescriptions
                ? setting.description() : null;
        VantaUiNode control = null;
        int controlWidth = ModSettingsPage.TOGGLE_WIDTH;
        switch (setting.type()) {
            case BOOLEAN -> control = VantaUiNode.toggle("", () -> Boolean.TRUE.equals(readValue(setting)),
                    value -> writeValue(setting, value)).menuStyle().height(30);
            case INTEGER, DECIMAL -> {
                double minimum = setting.minimum() == null ? 0.0 : setting.minimum();
                double maximum = setting.maximum() == null ? minimum : setting.maximum();
                double step = setting.type() == SettingDefinition.Type.INTEGER
                        ? 1.0 : Math.max(0.01, (maximum - minimum) / 100.0);
                controlWidth = ModSettingsPage.SLIDER_WIDTH;
                control = VantaUiNode.slider("", minimum, maximum, step, () -> {
                    Object value = readValue(setting);
                    return value instanceof Number number ? number.doubleValue() : minimum;
                }, value -> writeValue(setting, setting.type() == SettingDefinition.Type.INTEGER
                        ? Integer.valueOf((int) Math.round(value)) : Double.valueOf(value)))
                        .menuStyle().height(30);
            }
            case ENUM -> {
                List<String> options = setting.options();
                if (!options.isEmpty()) {
                    controlWidth = ModSettingsPage.DROPDOWN_WIDTH;
                    control = VantaUiNode.dropdown("", options, () -> {
                        Object value = readValue(setting);
                        return value instanceof Enum<?> enumeration ? enumeration.name() : options.get(0);
                    }, value -> {
                        Object[] constants = setting.valueClass().getEnumConstants();
                        if (constants == null) return;
                        Arrays.stream(constants).filter(option -> ((Enum<?>) option).name().equals(value))
                                .findFirst().ifPresent(option -> writeValue(setting, option));
                    }).menuStyle().height(30);
                }
            }
            case STRING -> {
                int maxLength = setting.maximum() == null ? 128
                        : Math.max(1, (int) Math.floor(setting.maximum()));
                controlWidth = ModSettingsPage.DROPDOWN_WIDTH;
                VantaUiNode field = VantaUiNode.row(0, 0).height(30)
                        .background(MenuTheme.FIELD).radius(8).border(0x33FFFFFF);
                field.add(VantaUiNode.textField(setting.name(), () -> {
                    Object value = readValue(setting);
                    return value instanceof String string ? string : "";
                }, value -> writeValue(setting, value), maxLength).menuStyle().flex(1));
                control = field;
            }
        }
        if (control == null) return;
        page.add(MenuComponents.settingRow(host.font(), setting.name(), description, control,
                controlWidth, ModSettingsPage.descriptionWidth(host, controlWidth)));
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
}
