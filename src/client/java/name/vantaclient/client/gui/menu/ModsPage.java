package name.vantaclient.client.gui.menu;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.VantaNotificationManager;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.menu.MenuIcons.Icon;
import name.vantaclient.client.integration.ModConfigDiscovery;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** The Mods page: filter chips, grid/list toggle and one tile per feature or installed mod. */
public final class ModsPage {
    private ModsPage() {}

    /** Filter selection that survives page rebuilds. */
    public static final class State {
        private ModEntry.Group group;
        private boolean favoritesOnly;

        public void reset() {
            group = null;
            favoritesOnly = false;
        }

        public void selectGroup(ModEntry.Group value) {
            group = value;
            favoritesOnly = false;
        }
    }

    /** Number of card columns for a page body of the given width. */
    public static int columnsFor(int contentWidth) {
        if (contentWidth >= 760) return 4;
        if (contentWidth >= 520) return 3;
        return 2;
    }

    public static VantaUiNode build(MenuHost host) {
        State state = host.modsState();
        VantaClientPreferences preferences = VantaClientPreferences.get();
        Set<String> favorites = preferences.favoriteMods;
        ModCatalog catalog = host.catalog();
        Font font = host.font();

        VantaUiNode page = VantaUiNode.column(0, 0).flex(1);
        addFilterRows(page, host, state, preferences);

        String query = host.search();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        List<ModEntry> visible = new ArrayList<>();
        for (ModEntry entry : catalog.entries()) {
            boolean inFilter = state.favoritesOnly
                    ? favorites.contains(entry.id())
                    : state.group == null || entry.group() == state.group;
            if (inFilter && entry.matchesNormalized(normalizedQuery)) visible.add(entry);
        }

        VantaUiNode scroll = VantaUiNode.column(16, 24).flex(1).scrollable();
        if (visible.isEmpty()) {
            scroll.add(VantaUiNode.spacer(40));
            String message = state.favoritesOnly && host.search().isBlank()
                    ? "No favorites yet. Open a mod and use the star to pin it here."
                    : catalog.isScanning() ? "Scanning your mods folder…" : "No mods match your filters.";
            scroll.add(VantaUiNode.canvas((g, f, x, y, w, h, st) -> MenuTheme.textCentered(g, f, message,
                    x + w / 2, y + h / 2, MenuTheme.TEXT_MUTED, MenuTheme.FONT_UI)).height(30));
            page.add(scroll);
            return page;
        }

        if (preferences.modsListView) {
            for (ModEntry entry : visible) {
                scroll.add(MenuComponents.modRow(entry, () -> catalog.icon(entry.id()),
                        () -> favorites.contains(entry.id()), () -> open(host, entry)));
            }
        } else {
            int columns = columnsFor(host.contentWidth());
            for (int start = 0; start < visible.size(); start += columns) {
                VantaUiNode row = VantaUiNode.row(16, 0).height(176);
                int end = Math.min(visible.size(), start + columns);
                for (int index = start; index < end; index++) {
                    ModEntry entry = visible.get(index);
                    row.add(MenuComponents.modCard(entry, () -> catalog.icon(entry.id()),
                            () -> favorites.contains(entry.id()), () -> open(host, entry)).flex(1));
                }
                for (int fill = end - start; fill < columns; fill++) {
                    row.add(VantaUiNode.spacer(1).flex(1));
                }
                scroll.add(row);
            }
        }
        page.add(scroll);
        return page;
    }

    private static void addFilterRows(VantaUiNode page, MenuHost host, State state,
                                      VantaClientPreferences preferences) {
        Font font = host.font();
        List<VantaUiNode> chips = new ArrayList<>();
        List<Integer> widths = new ArrayList<>();
        widths.add(MenuComponents.chipWidth(font, Icon.ALL, "All"));
        chips.add(MenuComponents.chip(font, Icon.ALL, "All", state.group == null && !state.favoritesOnly, () -> {
            state.reset();
            host.rebuild();
        }));
        widths.add(MenuComponents.chipWidth(font, Icon.STAR, "Favorites"));
        chips.add(MenuComponents.chip(font, Icon.STAR, "Favorites", state.favoritesOnly, () -> {
            state.reset();
            state.favoritesOnly = true;
            host.rebuild();
        }));
        for (ModEntry.Group group : ModEntry.Group.values()) {
            Icon icon = MenuComponents.iconFor(group);
            if (group == ModEntry.Group.OTHER) icon = Icon.DOTS;
            widths.add(MenuComponents.chipWidth(font, icon, group.label()));
            chips.add(MenuComponents.chip(font, icon, group.label(), state.group == group, () -> {
                state.selectGroup(group);
                host.rebuild();
            }));
        }

        int available = Math.max(200, host.contentWidth() - 90);
        List<VantaUiNode> line = new ArrayList<>();
        int used = 0;
        boolean first = true;
        for (int chipIndex = 0; chipIndex < chips.size(); chipIndex++) {
            VantaUiNode chip = chips.get(chipIndex);
            int chipWidth = widths.get(chipIndex);
            if (!line.isEmpty() && used + chipWidth + 8 > available) {
                page.add(chipRow(line, host, preferences, first, false));
                first = false;
                line = new ArrayList<>();
                used = 0;
            }
            line.add(chip);
            used += chipWidth + 8;
        }
        page.add(chipRow(line, host, preferences, first, true));
    }

    private static VantaUiNode chipRow(List<VantaUiNode> chips, MenuHost host,
                                       VantaClientPreferences preferences, boolean first, boolean last) {
        VantaUiNode row = VantaUiNode.row(8, 0).height(first ? 46 : 46).align(VantaUiNode.Alignment.CENTER);
        row.add(VantaUiNode.spacer(1).width(16));
        for (VantaUiNode chip : chips) row.add(chip);
        row.add(VantaUiNode.spacer(1).flex(1));
        if (last) {
            if (host.catalog().isScanning()) {
                row.add(VantaUiNode.text("Scanning…", MenuTheme.TEXT_MUTED).font(MenuTheme.FONT_SMALL).width(70));
            }
            boolean list = preferences.modsListView;
            row.add(MenuComponents.iconButton(list ? Icon.GRID : Icon.LIST,
                    list ? "Grid view" : "List view", true, () -> {
                        preferences.modsListView = !preferences.modsListView;
                        VantaClientPreferences.save();
                        host.rebuild();
                    }));
        }
        row.add(VantaUiNode.spacer(1).width(16));
        return row;
    }

    /** Opens a mod's settings page, or the mod's own config screen when it has no Vanta settings. */
    static void open(MenuHost host, ModEntry entry) {
        if (!entry.configurableInVanta() && entry.hasOwnConfigScreen()) {
            Screen screen = ModConfigDiscovery.open(entry.configFactory(), host.screen());
            if (screen != null) {
                host.minecraft().setScreen(screen);
                return;
            }
            VantaNotificationManager.warning("Settings unavailable",
                    entry.name() + " did not provide a settings screen.");
        }
        host.navigate(new MenuHost.Route(MenuHost.Page.MOD, entry.id()));
    }
}
