package name.vantaclient.client.gui.menu;

import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.menu.MenuIcons.Icon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

/** Hand-drawn building blocks shared by the menu pages. */
public final class MenuComponents {
    private MenuComponents() {}

    public static final Identifier LOGO =
            Identifier.fromNamespaceAndPath("vanta", "textures/gui/title/mark_small.png");

    private static final int CARD_BORDER = 0x26FFFFFF;
    private static final int CARD_BORDER_HOVER = 0x47FFFFFF;

    private static void focusRing(GuiGraphics g, VantaUiNode.PaintState st, int x, int y, int w, int h, int r) {
        if (st.focused()) MenuTheme.outline(g, x - 1, y - 1, w + 2, h + 2, r + 1, MenuTheme.alpha(MenuTheme.accent(), 235));
    }

    // ---- chrome ------------------------------------------------------------------------------

    public static VantaUiNode iconButton(Icon icon, String tooltip, boolean enabled, Runnable action) {
        VantaUiNode node = VantaUiNode.canvas((g, font, x, y, w, h, st) -> {
            if (enabled && st.hover() > 0.01f) MenuTheme.fill(g, x, y, w, h, 10, MenuTheme.alpha(0xFFFFFFFF, Math.round(26 * st.hover())));
            int color = !enabled ? 0xFF3C4049 : st.hovered() ? MenuTheme.TEXT : MenuTheme.TEXT_DIM;
            MenuIcons.draw(g, icon, x + (w - 18) / 2, y + (h - 18) / 2, 18, color);
            if (enabled) focusRing(g, st, x, y, w, h, 10);
        }).size(36, 36).tooltip(tooltip == null ? "" : tooltip).enabled(enabled);
        if (enabled && action != null) node.onClick(action);
        return node;
    }

    public static VantaUiNode navItem(Icon icon, String label, boolean selected, Runnable action) {
        return VantaUiNode.canvas((g, font, x, y, w, h, st) -> {
            if (selected) {
                MenuTheme.fill(g, x, y, w, h, 13, 0x14FFFFFF);
                MenuTheme.outline(g, x, y, w, h, 13, 0x55FFFFFF);
            } else if (st.hover() > 0.01f) {
                MenuTheme.fill(g, x, y, w, h, 13, MenuTheme.alpha(0xFFFFFFFF, Math.round(16 * st.hover())));
            }
            int color = selected || st.hovered() ? MenuTheme.TEXT : MenuTheme.TEXT_DIM;
            MenuIcons.draw(g, icon, x + 14, y + (h - 18) / 2, 18, color);
            MenuTheme.text(g, font, label, x + 44, y + h / 2, color, MenuTheme.FONT_MEDIUM);
            focusRing(g, st, x, y, w, h, 13);
        }).height(42).onClick(action);
    }

    public static VantaUiNode groupHeader(String label, boolean expanded, Runnable action) {
        return VantaUiNode.canvas((g, font, x, y, w, h, st) -> {
            MenuTheme.text(g, font, label, x + 8, y + h / 2,
                    st.hovered() ? MenuTheme.TEXT_DIM : MenuTheme.TEXT_MUTED, MenuTheme.FONT_SMALL);
            MenuIcons.draw(g, expanded ? Icon.CHEVRON_DOWN : Icon.CHEVRON_UP, x + w - 24, y + (h - 16) / 2, 16,
                    MenuTheme.TEXT_MUTED);
            focusRing(g, st, x, y, w, h, 8);
        }).height(30).onClick(action);
    }

    public static VantaUiNode brand() {
        return VantaUiNode.canvas((g, font, x, y, w, h, st) -> {
            int logoW = 28;
            int logoH = 28;
            int vantaWidth = MenuTheme.width(font, "VANTA", MenuTheme.FONT_HEADING);
            int clientWidth = MenuTheme.width(font, "CLIENT", MenuTheme.FONT_SMALL);
            int total = logoW + 10 + vantaWidth + 6 + clientWidth;
            int left = x + Math.max(0, (w - total) / 2);
            int cy = y + h / 2;
            g.blit(RenderPipelines.GUI_TEXTURED, LOGO, left, cy - logoH / 2, 0.0f, 0.0f, logoW, logoH,
                    96, 96, 96, 96);
            MenuTheme.text(g, font, "VANTA", left + logoW + 10, cy, MenuTheme.TEXT, MenuTheme.FONT_HEADING);
            // Same baseline as VANTA: cap centre of the 11px label sits 5 units below the heading's.
            MenuTheme.text(g, font, "CLIENT", left + logoW + 16 + vantaWidth, cy + 5,
                    MenuTheme.accent(), MenuTheme.FONT_SMALL);
        }).height(60);
    }

    public static VantaUiNode footer(String name, String detail, Runnable infoAction) {
        VantaUiNode root = VantaUiNode.row(8, 0).height(52).align(VantaUiNode.Alignment.CENTER);
        root.add(VantaUiNode.canvas((g, font, x, y, w, h, st) -> {
            int size = 38;
            int top = y + (h - size) / 2;
            MenuTheme.fill(g, x + 6, top, size, size, 11, MenuTheme.alpha(MenuTheme.accent(), 60));
            String initial = name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
            MenuTheme.textCentered(g, font, initial, x + 6 + size / 2, top + size / 2,
                    MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
            String shownName = MenuTheme.fit(font, name, w - size - 20, MenuTheme.FONT_MEDIUM);
            MenuTheme.text(g, font, shownName, x + size + 16, y + h / 2 - 8, MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
            MenuTheme.text(g, font, MenuTheme.fit(font, detail, w - size - 20, MenuTheme.FONT_SMALL),
                    x + size + 16, y + h / 2 + 8, MenuTheme.TEXT_MUTED, MenuTheme.FONT_SMALL);
        }).height(52).flex(1));
        root.add(iconButton(Icon.INFO, "About this client", true, infoAction));
        return root;
    }

    // ---- buttons -----------------------------------------------------------------------------

    public static int chipWidth(Font font, Icon icon, String label) {
        return 14 + (icon == null ? 0 : 22) + MenuTheme.width(font, label, MenuTheme.FONT_MEDIUM) + 14;
    }

    public static VantaUiNode chip(Font font, Icon icon, String label, boolean selected, Runnable action) {
        int width = chipWidth(font, icon, label);
        return VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            int accent = MenuTheme.accent();
            if (selected) {
                MenuTheme.accentFill(g, x, y, w, h, 12, st.hover());
            } else {
                MenuTheme.fill(g, x, y, w, h, 12, MenuTheme.mix(MenuTheme.CARD, MenuTheme.CARD_HOVER, st.hover()));
                MenuTheme.outline(g, x, y, w, h, 12, MenuTheme.mix(CARD_BORDER, CARD_BORDER_HOVER, st.hover()));
            }
            int color = selected ? 0xFFFFFFFF : st.hovered() ? MenuTheme.TEXT : 0xFFD0D5DE;
            int textX = x + 14;
            if (icon != null) {
                MenuIcons.draw(g, icon, x + 12, y + (h - 16) / 2, 16, color);
                textX = x + 34;
            }
            MenuTheme.text(g, f, label, textX, y + h / 2, color, MenuTheme.FONT_MEDIUM);
            focusRing(g, st, x, y, w, h, 12);
        }).size(width, 34).onClick(action);
    }

    /** Text button; {@code filled} uses the accent, otherwise a dark outlined style. */
    public static VantaUiNode button(Font font, String label, Icon icon, boolean filled, Runnable action) {
        int width = 28 + (icon == null ? 0 : 24) + MenuTheme.width(font, label, MenuTheme.FONT_MEDIUM);
        return VantaUiNode.canvas((g, f, x, y, w, h, st) -> {
            int accent = MenuTheme.accent();
            if (filled) {
                MenuTheme.accentFill(g, x, y, w, h, 12, st.hover());
            } else {
                MenuTheme.fill(g, x, y, w, h, 12, MenuTheme.mix(MenuTheme.CARD, MenuTheme.CARD_HOVER, st.hover()));
                MenuTheme.outline(g, x, y, w, h, 12, MenuTheme.mix(CARD_BORDER, CARD_BORDER_HOVER, st.hover()));
            }
            int color = filled || st.hovered() ? 0xFFFFFFFF : MenuTheme.TEXT_DIM;
            int labelWidth = MenuTheme.width(f, label, MenuTheme.FONT_MEDIUM);
            int contentWidth = labelWidth + (icon == null ? 0 : 24);
            int left = x + (w - contentWidth) / 2;
            if (icon != null) {
                MenuIcons.draw(g, icon, left, y + (h - 16) / 2, 16, color);
                left += 24;
            }
            MenuTheme.text(g, f, label, left, y + h / 2, color, MenuTheme.FONT_MEDIUM);
            focusRing(g, st, x, y, w, h, 12);
        }).size(width, 38).onClick(action);
    }

    /** A value button such as the key currently bound to an action. */
    public static VantaUiNode valueButton(Supplier<String> text, Supplier<Boolean> active,
                                          Supplier<Boolean> danger, Runnable action) {
        return VantaUiNode.canvas((g, font, x, y, w, h, st) -> {
            boolean isActive = Boolean.TRUE.equals(active.get());
            boolean isDanger = Boolean.TRUE.equals(danger.get());
            int accent = MenuTheme.accent();
            MenuTheme.fill(g, x, y, w, h, 10, st.hovered() ? MenuTheme.CARD_HOVER : MenuTheme.FIELD);
            MenuTheme.outline(g, x, y, w, h, 10,
                    isActive ? accent : isDanger ? MenuTheme.DANGER : st.hovered() ? CARD_BORDER_HOVER : 0x33FFFFFF);
            String shown = MenuTheme.fit(font, text.get(), w - 20, MenuTheme.FONT_MEDIUM);
            MenuTheme.textCentered(g, font, shown, x + w / 2, y + h / 2,
                    isActive ? accent : MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
            focusRing(g, st, x, y, w, h, 10);
        }).height(32).onClick(action);
    }

    // ---- rows and sections -------------------------------------------------------------------

    public static VantaUiNode sectionTitle(String label) {
        return VantaUiNode.text(label, MenuTheme.TEXT_MUTED).font(MenuTheme.FONT_SMALL).height(30);
    }

    public static VantaUiNode settingRow(Font font, String title, String description,
                                         VantaUiNode control, int controlWidth, int descriptionWidth) {
        boolean hasDescription = description != null && !description.isBlank();
        VantaUiNode row = VantaUiNode.row(16, 16).height(hasDescription ? 64 : 56)
                .background(MenuTheme.ROW).border(CARD_BORDER).radius(12)
                .align(VantaUiNode.Alignment.CENTER);
        VantaUiNode labels = VantaUiNode.column(2, 0).flex(1);
        labels.add(VantaUiNode.text(MenuTheme.fit(font, title, Math.max(40, descriptionWidth), MenuTheme.FONT_MEDIUM),
                MenuTheme.TEXT).font(MenuTheme.FONT_MEDIUM).height(18));
        if (hasDescription) {
            labels.add(VantaUiNode.text(MenuTheme.fit(font, description, Math.max(40, descriptionWidth),
                    MenuTheme.FONT_SMALL), MenuTheme.TEXT_MUTED).font(MenuTheme.FONT_SMALL).height(16));
        }
        row.add(labels);
        row.add(control.width(controlWidth));
        return row;
    }

    // ---- cards -------------------------------------------------------------------------------

    public static Icon iconFor(ModEntry.Group group) {
        return switch (group) {
            case PERFORMANCE -> Icon.GAUGE;
            case HUD -> Icon.HUD;
            case UTILITY -> Icon.WRENCH;
            case QUALITY_OF_LIFE -> Icon.SPARKLE;
            case OTHER -> Icon.MODS;
        };
    }

    /** Draws a mod's artwork (jar icon, or a tinted tile with the group icon / initial) centred in a square. */
    public static void drawModArt(GuiGraphics g, Font font, ModEntry entry, ModCatalog.IconTexture texture,
                                  int x, int y, int size) {
        if (texture != null) {
            int drawW = size;
            int drawH = size;
            if (texture.width() > texture.height()) drawH = Math.max(1, size * texture.height() / texture.width());
            else if (texture.height() > texture.width()) drawW = Math.max(1, size * texture.width() / texture.height());
            g.blit(RenderPipelines.GUI_TEXTURED, texture.id(), x + (size - drawW) / 2, y + (size - drawH) / 2,
                    0.0f, 0.0f, drawW, drawH, texture.width(), texture.height(), texture.width(), texture.height());
            return;
        }
        int accent = MenuTheme.accent();
        int tile = Math.max(6, size / 4);
        MenuTheme.fill(g, x, y, size, size, tile, MenuTheme.alpha(accent, 38));
        MenuTheme.outline(g, x, y, size, size, tile, MenuTheme.alpha(accent, 84));
        if (entry.isBuiltIn()) {
            int iconSize = size / 2;
            MenuIcons.draw(g, iconFor(entry.group()), x + (size - iconSize) / 2, y + (size - iconSize) / 2,
                    iconSize, accent);
        } else {
            String initial = entry.name().isBlank() ? "?" : entry.name().substring(0, 1).toUpperCase();
            MenuTheme.textCentered(g, font, initial, x + size / 2, y + size / 2, MenuTheme.TEXT, MenuTheme.FONT_HEADING);
        }
    }

    public static VantaUiNode modCard(ModEntry entry, Supplier<ModCatalog.IconTexture> icon,
                                      Supplier<Boolean> favorite, Runnable open) {
        return VantaUiNode.canvas((g, font, x, y, w, h, st) -> {
            int accent = MenuTheme.accent();
            float hover = st.hover();
            MenuTheme.fill(g, x, y, w, h, 16, MenuTheme.mix(0x8C141926, 0xB41B2231, hover));
            MenuTheme.outline(g, x, y, w, h, 16, MenuTheme.mix(0x22FFFFFF, MenuTheme.alpha(accent, 200), hover));
            if (Boolean.TRUE.equals(favorite.get())) {
                MenuIcons.draw(g, Icon.STAR_FILLED, x + w - 28, y + 12, 16, 0xFFF2C14E);
            }
            if (entry.isBuiltIn()) {
                boolean on = entry.feature().isEnabled();
                int stateColor = on ? 0xFF3DD68C : 0xFF6F7581;
                String label = on ? "On" : "Off";
                int pillW = MenuTheme.width(font, label, MenuTheme.FONT_SMALL) + 28;
                MenuTheme.fill(g, x + 12, y + 12, pillW, 22, 11, MenuTheme.alpha(stateColor, on ? 34 : 24));
                MenuTheme.dot(g, x + 12 + 12, y + 23, 7, stateColor);
                MenuTheme.text(g, font, label, x + 12 + 21, y + 23,
                        on ? MenuTheme.mix(stateColor, 0xFFFFFFFF, 0.4f) : MenuTheme.TEXT_MUTED, MenuTheme.FONT_SMALL);
            }
            int art = 60;
            drawModArt(g, font, entry, icon.get(), x + (w - art) / 2, y + 22, art);
            String name = MenuTheme.fit(font, entry.name(), w - 28, MenuTheme.FONT_MEDIUM);
            MenuTheme.textCentered(g, font, name, x + w / 2, y + 22 + art + 22, MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
            // Status chip: tells at a glance whether Vanta can configure this mod.
            boolean configurable = entry.configurableInVanta();
            String status = configurable ? "Configure" : entry.hasOwnConfigScreen() ? "Own settings" : "No options";
            int chipW = MenuTheme.width(font, status, MenuTheme.FONT_SMALL) + 22;
            int chipH = 22;
            int chipX = x + (w - chipW) / 2;
            int chipY = y + h - 14 - chipH;
            if (configurable) {
                MenuTheme.fill(g, chipX, chipY, chipW, chipH, 11, MenuTheme.alpha(accent, 40 + Math.round(60 * hover)));
            } else {
                MenuTheme.fill(g, chipX, chipY, chipW, chipH, 11, 0x1FFFFFFF);
            }
            MenuTheme.textCentered(g, font, status, x + w / 2, chipY + chipH / 2,
                    configurable ? MenuTheme.mix(accent, 0xFFFFFFFF, 0.35f) : MenuTheme.TEXT_DIM, MenuTheme.FONT_SMALL);
            focusRing(g, st, x, y, w, h, 16);
        }).height(176).tooltip(entry.configurableInVanta()
                ? "Settings available in Vanta"
                : entry.hasOwnConfigScreen() ? "Opens the mod's own settings" : "No settings Vanta can edit")
                .onClick(open);
    }

    public static VantaUiNode modRow(ModEntry entry, Supplier<ModCatalog.IconTexture> icon,
                                     Supplier<Boolean> favorite, Runnable open) {
        return VantaUiNode.canvas((g, font, x, y, w, h, st) -> {
            int accent = MenuTheme.accent();
            MenuTheme.fill(g, x, y, w, h, 14, MenuTheme.mix(0x8C141926, 0xB41B2231, st.hover()));
            MenuTheme.outline(g, x, y, w, h, 14, MenuTheme.mix(0x22FFFFFF, MenuTheme.alpha(accent, 200), st.hover()));
            drawModArt(g, font, entry, icon.get(), x + 12, y + (h - 36) / 2, 36);
            int nameX = x + 62;
            MenuTheme.text(g, font, MenuTheme.fit(font, entry.name(), w - 300, MenuTheme.FONT_MEDIUM),
                    nameX, y + h / 2 - 8, MenuTheme.TEXT, MenuTheme.FONT_MEDIUM);
            String detail = entry.isBuiltIn() ? entry.group().label()
                    : entry.version().isBlank() ? "Installed mod" : "Installed mod · " + entry.version();
            MenuTheme.text(g, font, MenuTheme.fit(font, detail, w - 300, MenuTheme.FONT_SMALL),
                    nameX, y + h / 2 + 9, MenuTheme.TEXT_MUTED, MenuTheme.FONT_SMALL);
            if (Boolean.TRUE.equals(favorite.get())) {
                MenuIcons.draw(g, Icon.STAR_FILLED, x + w - 188, y + (h - 16) / 2, 16, 0xFFF2C14E);
            }
            MenuTheme.dot(g, x + w - 150, y + h / 2, 8, entry.configurableInVanta() ? accent : MenuTheme.NEUTRAL_DOT);
            int bw = 112;
            int bh = 34;
            int bx = x + w - bw - 14;
            int by = y + (h - bh) / 2;
            MenuTheme.accentFill(g, bx, by, bw, bh, 11, st.hover());
            MenuTheme.textCentered(g, font, entry.configurableInVanta() ? "Configure" : "Open",
                    bx + bw / 2, by + bh / 2, 0xFFFFFFFF, MenuTheme.FONT_MEDIUM);
            focusRing(g, st, x, y, w, h, 12);
        }).height(60).onClick(open);
    }
}
