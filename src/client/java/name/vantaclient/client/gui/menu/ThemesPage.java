package name.vantaclient.client.gui.menu;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.menu.MenuIcons.Icon;
import net.minecraft.client.gui.Font;

import java.util.Locale;

/** Accent themes with live previews plus the general appearance options. */
public final class ThemesPage {
    private ThemesPage() {}

    public static VantaUiNode build(MenuHost host) {
        Font font = host.font();
        VantaClientPreferences preferences = VantaClientPreferences.get();
        VantaUiNode scroll = VantaUiNode.column(12, 24).flex(1).scrollable();

        scroll.add(MenuComponents.sectionTitle("THEMES"));
        VantaClientPreferences.Accent[] accents = VantaClientPreferences.Accent.values();
        int columns = ModsPage.columnsFor(host.contentWidth());
        for (int start = 0; start < accents.length; start += columns) {
            VantaUiNode row = VantaUiNode.row(16, 0).height(166);
            int end = Math.min(accents.length, start + columns);
            for (int index = start; index < end; index++) {
                row.add(themeCard(host, accents[index]).flex(1));
            }
            for (int fill = end - start; fill < columns; fill++) row.add(VantaUiNode.spacer(1).flex(1));
            scroll.add(row);
        }

        scroll.add(MenuComponents.sectionTitle("GENERAL"));
        int toggleDesc = ModSettingsPage.descriptionWidth(host, ModSettingsPage.TOGGLE_WIDTH);
        int sliderDesc = ModSettingsPage.descriptionWidth(host, ModSettingsPage.SLIDER_WIDTH);
        scroll.add(MenuComponents.settingRow(font, "Animations", "Smooth hover and toggle transitions.",
                ModSettingsPage.toggleControl(() -> preferences.animations, value -> {
                    preferences.animations = value;
                    VantaClientPreferences.save();
                }), ModSettingsPage.TOGGLE_WIDTH, toggleDesc));
        scroll.add(MenuComponents.settingRow(font, "Panel opacity", "How see-through the menu panel is.",
                VantaUiNode.slider("", 0.72, 0.98, 0.02, () -> preferences.panelOpacity, value -> {
                    preferences.panelOpacity = value;
                    VantaClientPreferences.save();
                }).menuStyle().height(30), ModSettingsPage.SLIDER_WIDTH, sliderDesc));
        scroll.add(MenuComponents.settingRow(font, "Interface scale", "Size of the Vanta menu and HUD editor.",
                VantaUiNode.slider("", 0.8, 1.2, 0.05, () -> preferences.uiScale, value -> {
                    preferences.uiScale = value;
                    VantaClientPreferences.save();
                }).menuStyle().height(30), ModSettingsPage.SLIDER_WIDTH, sliderDesc));
        return scroll;
    }

    private static VantaUiNode themeCard(MenuHost host, VantaClientPreferences.Accent accent) {
        String name = accent.name().charAt(0) + accent.name().substring(1).toLowerCase(Locale.ROOT);
        return VantaUiNode.canvas((g, font, x, y, w, h, st) -> {
            boolean selected = VantaClientPreferences.get().accent == accent;
            int color = accent.color();
            MenuTheme.fill(g, x, y, w, h, 14, st.hovered() ? MenuTheme.CARD_HOVER : MenuTheme.CARD);
            MenuTheme.outline(g, x, y, w, h, 14, selected ? color : st.hovered() ? 0xFF3A3F4B : 0xFF23262E);
            if (selected) MenuTheme.outline(g, x + 1, y + 1, w - 2, h - 2, 13, MenuTheme.alpha(color, 120));

            // Miniature of the menu using this accent.
            int px = x + 14;
            int py = y + 14;
            int pw = w - 28;
            int ph = h - 14 - 14 - 34;
            MenuTheme.fill(g, px, py, pw, ph, 9, 0xFF0C0E12);
            MenuTheme.outline(g, px, py, pw, ph, 9, 0xFF22252C);
            MenuTheme.fill(g, px + 8, py + 8, 18, 5, 2, 0xFF2A2D35);
            for (int i = 0; i < 3; i++) {
                MenuTheme.fill(g, px + 8, py + 20 + i * 11, 22, 7, 3, i == 0 ? MenuTheme.alpha(color, 90) : 0xFF1B1E25);
            }
            int gx = px + 40;
            int gw = Math.max(10, (pw - 40 - 8 - 6) / 2);
            for (int i = 0; i < 2; i++) {
                int cx = gx + i * (gw + 6);
                MenuTheme.fill(g, cx, py + 8, gw, ph - 16, 5, 0xFF16181D);
                MenuTheme.fill(g, cx + 4, py + ph - 8 - 11, gw - 8, 7, 3, color);
            }
            MenuTheme.text(g, font, name, x + 14, y + h - 14 - 10, MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
            if (selected) {
                MenuIcons.draw(g, Icon.CHECK, x + w - 14 - 18, y + h - 14 - 18, 18, color);
            }
            if (st.focused()) MenuTheme.outline(g, x - 1, y - 1, w + 2, h + 2, 15, MenuTheme.accent());
        }).height(166).tooltip("Use " + name.toLowerCase(Locale.ROOT) + " as the interface accent").onClick(() -> {
            VantaClientPreferences.get().accent = accent;
            VantaClientPreferences.save();
            host.rebuild();
        });
    }
}
