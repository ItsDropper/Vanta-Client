package name.vantaclient.client.gui.menu;

import name.vantaclient.client.config.ProfileManager;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.VantaNotificationManager;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.menu.MenuIcons.Icon;
import net.minecraft.client.gui.Font;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Saved snapshots of Vanta's feature settings and interface preferences. */
public final class ProfilesPage {
    private ProfilesPage() {}

    /** Page state that survives rebuilds. */
    public static final class State {
        private boolean favoritesOnly;
        private boolean naming;
        private String draftName = "";
        private String confirmDeleteId = "";
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    public static VantaUiNode build(MenuHost host) {
        State state = host.profilesState();
        Font font = host.font();
        VantaUiNode page = VantaUiNode.column(0, 0).flex(1);

        VantaUiNode top = VantaUiNode.row(8, 0).height(46).align(VantaUiNode.Alignment.CENTER);
        top.add(VantaUiNode.spacer(1).width(16));
        top.add(MenuComponents.chip(font, Icon.PROFILES, "All profiles", !state.favoritesOnly, () -> {
            state.favoritesOnly = false;
            host.rebuild();
        }));
        top.add(MenuComponents.chip(font, Icon.STAR, "Favorites", state.favoritesOnly, () -> {
            state.favoritesOnly = true;
            host.rebuild();
        }));
        top.add(VantaUiNode.spacer(1).flex(1));
        top.add(MenuComponents.button(font, "Open folder", Icon.FOLDER, false, () -> openFolder()));
        top.add(VantaUiNode.spacer(1).width(16));
        page.add(top);

        VantaUiNode scroll = VantaUiNode.column(16, 24).flex(1).scrollable();
        if (state.naming) scroll.add(namingRow(host, state, font));

        List<ProfileManager.Profile> profiles = new ArrayList<>();
        for (ProfileManager.Profile profile : ProfileManager.list()) {
            if (!state.favoritesOnly || profile.favorite()) profiles.add(profile);
        }

        int columns = ModsPage.columnsFor(host.contentWidth());
        List<VantaUiNode> cards = new ArrayList<>();
        if (!state.favoritesOnly) cards.add(newCard(state, host));
        for (ProfileManager.Profile profile : profiles) cards.add(profileCard(host, state, profile));
        for (int start = 0; start < cards.size(); start += columns) {
            VantaUiNode row = VantaUiNode.row(16, 0).height(150);
            int end = Math.min(cards.size(), start + columns);
            for (int index = start; index < end; index++) row.add(cards.get(index).flex(1));
            for (int fill = end - start; fill < columns; fill++) row.add(VantaUiNode.spacer(1).flex(1));
            scroll.add(row);
        }
        if (profiles.isEmpty()) {
            scroll.add(VantaUiNode.text(state.favoritesOnly
                            ? "No favorite profiles yet."
                            : "A profile stores your feature settings and appearance so you can switch setups in one click.",
                    MenuTheme.TEXT_MUTED).font(MenuTheme.FONT_SMALL).height(24));
        }
        page.add(scroll);
        return page;
    }

    private static void openFolder() {
        try {
            Files.createDirectories(ProfileManager.directory());
            net.minecraft.util.Util.getPlatform().openPath(ProfileManager.directory());
        } catch (IOException | RuntimeException exception) {
            VantaNotificationManager.warning("Could not open folder", exception.getMessage() == null
                    ? "The profiles folder is unavailable." : exception.getMessage());
        }
    }

    private static VantaUiNode namingRow(MenuHost host, State state, Font font) {
        VantaUiNode row = VantaUiNode.row(10, 0).height(46).align(VantaUiNode.Alignment.CENTER);
        VantaUiNode box = VantaUiNode.row(0, 0).height(40).background(MenuTheme.FIELD).radius(12)
                .border(MenuTheme.alpha(MenuTheme.accent(), 215)).flex(1);
        VantaUiNode field = VantaUiNode.textField("Profile name", () -> state.draftName,
                value -> state.draftName = value, 32).menuStyle().height(40).flex(1);
        box.add(field);
        host.focusAfterBuild(field);
        row.add(box);
        row.add(MenuComponents.button(font, "Create", null, true, () -> {
            ProfileManager.Profile created = ProfileManager.create(state.draftName);
            if (created == null) {
                VantaNotificationManager.warning("Name needed", "Enter a name to save this profile.");
                return;
            }
            state.naming = false;
            state.draftName = "";
            VantaNotificationManager.success("Profile saved", created.name() + " now holds your current settings.");
            host.rebuild();
        }));
        row.add(MenuComponents.button(font, "Cancel", null, false, () -> {
            state.naming = false;
            state.draftName = "";
            host.rebuild();
        }));
        return row;
    }

    private static VantaUiNode newCard(State state, MenuHost host) {
        return VantaUiNode.canvas((g, font, x, y, w, h, st) -> {
            MenuTheme.fill(g, x, y, w, h, 14, st.hovered() ? MenuTheme.CARD_HOVER : MenuTheme.CARD);
            MenuTheme.outline(g, x, y, w, h, 14, st.hovered() ? 0xFF3A3F4B : 0xFF23262E);
            MenuTheme.fill(g, x + w / 2 - 22, y + h / 2 - 36, 44, 44, 13, MenuTheme.alpha(MenuTheme.accent(), 46));
            MenuIcons.draw(g, Icon.PLUS, x + w / 2 - 10, y + h / 2 - 24, 20, MenuTheme.accent());
            MenuTheme.textCentered(g, font, "New profile", x + w / 2, y + h / 2 + 28,
                    MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
            MenuTheme.textCentered(g, font, "Save your current settings", x + w / 2, y + h / 2 + 48,
                    MenuTheme.TEXT_MUTED, MenuTheme.FONT_SMALL);
            if (st.focused()) MenuTheme.outline(g, x - 1, y - 1, w + 2, h + 2, 15, MenuTheme.accent());
        }).height(150).onClick(() -> {
            state.naming = true;
            host.rebuild();
        });
    }

    private static VantaUiNode profileCard(MenuHost host, State state, ProfileManager.Profile profile) {
        boolean active = profile.id().equals(VantaClientPreferences.get().activeProfile);
        boolean confirming = profile.id().equals(state.confirmDeleteId);
        String updated = profile.updatedAt() <= 0 ? "" : DATE.format(
                Instant.ofEpochMilli(profile.updatedAt()).atZone(ZoneId.systemDefault()).toLocalDate());
        return VantaUiNode.canvas((g, font, x, y, w, h, st) -> {
            int accent = MenuTheme.accent();
            MenuTheme.fill(g, x, y, w, h, 14, st.hovered() ? MenuTheme.CARD_HOVER : MenuTheme.CARD);
            MenuTheme.outline(g, x, y, w, h, 14, active ? accent : st.hovered() ? 0xFF3A3F4B : 0xFF23262E);
            if (active) MenuTheme.outline(g, x + 1, y + 1, w - 2, h - 2, 13, MenuTheme.alpha(accent, 140));
            MenuTheme.fill(g, x + 14, y + 14, 38, 38, 11, MenuTheme.alpha(accent, 46));
            MenuIcons.draw(g, Icon.PROFILES, x + 23, y + 23, 20, accent);
            MenuIcons.draw(g, profile.favorite() ? Icon.STAR_FILLED : Icon.STAR, x + w - 36, y + 18, 18,
                    profile.favorite() ? 0xFFF2C14E : MenuTheme.TEXT_MUTED);
            MenuTheme.text(g, font, MenuTheme.fit(font, profile.name(), w - 28, MenuTheme.FONT_MEDIUM),
                    x + 14, y + 72, MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
            MenuTheme.text(g, font, active ? "Active" : updated, x + 14, y + 92,
                    active ? accent : MenuTheme.TEXT_MUTED, MenuTheme.FONT_SMALL);
            int by = y + h - 14 - 30;
            MenuTheme.fill(g, x + 14, by, 70, 30, 10, accent);
            MenuTheme.textCentered(g, font, "Apply", x + 14 + 35, by + 15, 0xFFFFFFFF, MenuTheme.FONT_MEDIUM);
            MenuTheme.fill(g, x + 92, by, 76, 30, 10, MenuTheme.FIELD);
            MenuTheme.outline(g, x + 92, by, 76, 30, 10, 0xFF2E333D);
            MenuTheme.textCentered(g, font, "Update", x + 92 + 38, by + 15, MenuTheme.TEXT_DIM, MenuTheme.FONT_MEDIUM);
            MenuIcons.draw(g, Icon.TRASH, x + w - 14 - 22, by + 4, 22,
                    confirming ? MenuTheme.DANGER : MenuTheme.TEXT_MUTED);
            if (confirming) {
                MenuTheme.textRight(g, font, "Delete?", x + w - 44, by + 15, MenuTheme.DANGER, MenuTheme.FONT_SMALL);
            }
            if (st.focused()) MenuTheme.outline(g, x - 1, y - 1, w + 2, h + 2, 15, accent);
        }).height(150).tooltip("Apply this profile").onClickAt((lx, ly, cardWidth, cardHeight) -> {
            int buttonTop = cardHeight - 14 - 30;
            boolean bottomRow = ly >= buttonTop && ly < buttonTop + 30;
            if (lx >= cardWidth - 44 && lx < cardWidth - 8 && ly >= 8 && ly < 44) {
                state.confirmDeleteId = "";
                ProfileManager.toggleFavorite(profile.id());
                host.rebuild();
            } else if (bottomRow && lx >= cardWidth - 40 && lx < cardWidth - 8) {
                if (confirming) {
                    ProfileManager.delete(profile.id());
                    state.confirmDeleteId = "";
                    VantaNotificationManager.info("Profile deleted", profile.name() + " was removed.");
                } else {
                    state.confirmDeleteId = profile.id();
                }
                host.rebuild();
            } else if (bottomRow && lx >= 92 && lx < 168) {
                state.confirmDeleteId = "";
                if (ProfileManager.update(profile.id())) {
                    VantaNotificationManager.success("Profile updated",
                            profile.name() + " now holds your current settings.");
                } else {
                    VantaNotificationManager.error("Update failed", "Could not write " + profile.name() + ".");
                }
                host.rebuild();
            } else {
                state.confirmDeleteId = "";
                if (ProfileManager.apply(profile.id())) {
                    VantaNotificationManager.success("Profile applied", profile.name() + " is now active.");
                } else {
                    VantaNotificationManager.error("Apply failed", "Could not read " + profile.name() + ".");
                }
                host.rebuild();
            }
        });
    }
}
