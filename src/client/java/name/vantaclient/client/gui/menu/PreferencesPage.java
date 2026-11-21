package name.vantaclient.client.gui.menu;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.VantaIntegrationsScreen;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.menu.MenuIcons.Icon;
import name.vantaclient.client.init.Keybinds;
import net.minecraft.client.gui.Font;

import java.util.List;

/** Behaviour options for the menu and the HUD editor. */
public final class PreferencesPage {
    private PreferencesPage() {}

    public static VantaUiNode build(MenuHost host) {
        Font font = host.font();
        VantaClientPreferences preferences = VantaClientPreferences.get();
        VantaUiNode scroll = VantaUiNode.column(12, 24).flex(1).scrollable();
        int toggleDesc = ModSettingsPage.descriptionWidth(host, ModSettingsPage.TOGGLE_WIDTH);
        int dropdownDesc = ModSettingsPage.descriptionWidth(host, ModSettingsPage.DROPDOWN_WIDTH);
        int sliderDesc = ModSettingsPage.descriptionWidth(host, ModSettingsPage.SLIDER_WIDTH);

        scroll.add(MenuComponents.sectionTitle("GUI"));
        if (Keybinds.guiKey != null) {
            scroll.add(MenuComponents.settingRow(font, "Open Vanta menu", "Key that opens this menu in game.",
                    KeybindsPage.keyButton(host, Keybinds.guiKey), ModSettingsPage.KEY_WIDTH,
                    ModSettingsPage.descriptionWidth(host, ModSettingsPage.KEY_WIDTH)));
        }
        scroll.add(MenuComponents.settingRow(font, "Setting descriptions", "Show a short explanation under each setting.",
                ModSettingsPage.toggleControl(() -> preferences.showDescriptions, value -> {
                    preferences.showDescriptions = value;
                    VantaClientPreferences.save();
                    host.rebuild();
                }), ModSettingsPage.TOGGLE_WIDTH, toggleDesc));
        scroll.add(MenuComponents.settingRow(font, "Mod library",
                "Installed mods, with experimental loading for the current session.",
                MenuComponents.button(font, "Open", Icon.ARROW_RIGHT, false, () ->
                        host.minecraft().setScreen(new VantaIntegrationsScreen(host.screen()))),
                120, descriptionWidthFor(host, 120)));

        scroll.add(MenuComponents.sectionTitle("HUD EDITOR"));
        scroll.add(MenuComponents.settingRow(font, "Snap to grid", "Align elements to the grid while dragging.",
                ModSettingsPage.toggleControl(() -> preferences.hudSnapToGrid, value -> {
                    preferences.hudSnapToGrid = value;
                    VantaClientPreferences.save();
                }), ModSettingsPage.TOGGLE_WIDTH, toggleDesc));
        scroll.add(MenuComponents.settingRow(font, "Show grid", "Draw the grid behind the editor.",
                ModSettingsPage.toggleControl(() -> preferences.hudShowGrid, value -> {
                    preferences.hudShowGrid = value;
                    VantaClientPreferences.save();
                }), ModSettingsPage.TOGGLE_WIDTH, toggleDesc));
        scroll.add(MenuComponents.settingRow(font, "Grid size", "Spacing of the editor grid in pixels.",
                VantaUiNode.dropdown("", List.of("8", "16", "24", "32"),
                        () -> String.valueOf(preferences.hudGridSize), value -> {
                            try {
                                preferences.hudGridSize = Integer.parseInt(value);
                                VantaClientPreferences.save();
                            } catch (NumberFormatException ignored) {
                                // Options are fixed numeric strings; nothing to apply otherwise.
                            }
                        }).menuStyle().height(30), ModSettingsPage.DROPDOWN_WIDTH, dropdownDesc));
        scroll.add(MenuComponents.settingRow(font, "Connect backgrounds",
                "Merge HUD backgrounds that touch or sit close together.",
                ModSettingsPage.toggleControl(() -> preferences.connectHudBackgrounds, value -> {
                    preferences.connectHudBackgrounds = value;
                    VantaClientPreferences.save();
                }), ModSettingsPage.TOGGLE_WIDTH, toggleDesc));
        scroll.add(MenuComponents.settingRow(font, "Connection gap", "Largest gap bridged between backgrounds, in pixels.",
                VantaUiNode.slider("", 0, 24, 1, () -> preferences.hudBackgroundConnectionGap, value -> {
                    preferences.hudBackgroundConnectionGap = (int) Math.round(value);
                    VantaClientPreferences.save();
                }).menuStyle().height(30), ModSettingsPage.SLIDER_WIDTH, sliderDesc));
        return scroll;
    }

    private static int descriptionWidthFor(MenuHost host, int controlWidth) {
        return ModSettingsPage.descriptionWidth(host, controlWidth);
    }
}
