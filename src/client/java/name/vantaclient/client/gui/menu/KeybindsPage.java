package name.vantaclient.client.gui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.menu.MenuIcons.Icon;
import name.vantaclient.client.init.FeatureKeybinds;
import name.vantaclient.client.init.Keybinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keyboard and mouse map showing which keys are in use or clashing, plus every rebindable action. */
public final class KeybindsPage {
    private KeybindsPage() {}

    private record Key(int code, String label, float x, float y, float w) {}

    private static final int MOUSE_BASE = -100;
    private static final List<Key> KEYS = buildLayout();
    private static final float LAYOUT_WIDTH = 15.0f;
    private static final float LAYOUT_HEIGHT = 6.6f;

    private static List<Key> buildLayout() {
        List<Key> keys = new ArrayList<>();
        keys.add(new Key(GLFW.GLFW_KEY_ESCAPE, "Esc", 0, 0, 1));
        for (int i = 0; i < 12; i++) {
            float x = 2.0f + i + (i / 4) * 0.5f - 0.0f;
            keys.add(new Key(GLFW.GLFW_KEY_F1 + i, "F" + (i + 1), x - 0.5f + 0.5f, 0, 1));
        }
        float y = 1.6f;
        keys.add(new Key(GLFW.GLFW_KEY_GRAVE_ACCENT, "`", 0, y, 1));
        String digits = "1234567890";
        for (int i = 0; i < digits.length(); i++) {
            int code = digits.charAt(i) == '0' ? GLFW.GLFW_KEY_0 : GLFW.GLFW_KEY_1 + i;
            keys.add(new Key(code, String.valueOf(digits.charAt(i)), 1 + i, y, 1));
        }
        keys.add(new Key(GLFW.GLFW_KEY_MINUS, "-", 11, y, 1));
        keys.add(new Key(GLFW.GLFW_KEY_EQUAL, "=", 12, y, 1));
        keys.add(new Key(GLFW.GLFW_KEY_BACKSPACE, "Back", 13, y, 2));

        y = 2.6f;
        keys.add(new Key(GLFW.GLFW_KEY_TAB, "Tab", 0, y, 1.5f));
        String top = "QWERTYUIOP";
        for (int i = 0; i < top.length(); i++) {
            keys.add(new Key(top.charAt(i), String.valueOf(top.charAt(i)), 1.5f + i, y, 1));
        }
        keys.add(new Key(GLFW.GLFW_KEY_LEFT_BRACKET, "[", 11.5f, y, 1));
        keys.add(new Key(GLFW.GLFW_KEY_RIGHT_BRACKET, "]", 12.5f, y, 1));
        keys.add(new Key(GLFW.GLFW_KEY_BACKSLASH, "\\", 13.5f, y, 1.5f));

        y = 3.6f;
        keys.add(new Key(GLFW.GLFW_KEY_CAPS_LOCK, "Caps", 0, y, 1.75f));
        String home = "ASDFGHJKL";
        for (int i = 0; i < home.length(); i++) {
            keys.add(new Key(home.charAt(i), String.valueOf(home.charAt(i)), 1.75f + i, y, 1));
        }
        keys.add(new Key(GLFW.GLFW_KEY_SEMICOLON, ";", 10.75f, y, 1));
        keys.add(new Key(GLFW.GLFW_KEY_APOSTROPHE, "'", 11.75f, y, 1));
        keys.add(new Key(GLFW.GLFW_KEY_ENTER, "Enter", 12.75f, y, 2.25f));

        y = 4.6f;
        keys.add(new Key(GLFW.GLFW_KEY_LEFT_SHIFT, "Shift", 0, y, 2.25f));
        String bottom = "ZXCVBNM";
        for (int i = 0; i < bottom.length(); i++) {
            keys.add(new Key(bottom.charAt(i), String.valueOf(bottom.charAt(i)), 2.25f + i, y, 1));
        }
        keys.add(new Key(GLFW.GLFW_KEY_COMMA, ",", 9.25f, y, 1));
        keys.add(new Key(GLFW.GLFW_KEY_PERIOD, ".", 10.25f, y, 1));
        keys.add(new Key(GLFW.GLFW_KEY_SLASH, "/", 11.25f, y, 1));
        keys.add(new Key(GLFW.GLFW_KEY_RIGHT_SHIFT, "Shift", 12.25f, y, 2.75f));

        y = 5.6f;
        keys.add(new Key(GLFW.GLFW_KEY_LEFT_CONTROL, "Ctrl", 0, y, 1.25f));
        keys.add(new Key(GLFW.GLFW_KEY_LEFT_SUPER, "Win", 1.25f, y, 1.25f));
        keys.add(new Key(GLFW.GLFW_KEY_LEFT_ALT, "Alt", 2.5f, y, 1.25f));
        keys.add(new Key(GLFW.GLFW_KEY_SPACE, "Space", 3.75f, y, 6.25f));
        keys.add(new Key(GLFW.GLFW_KEY_RIGHT_ALT, "Alt", 10.0f, y, 1.25f));
        keys.add(new Key(GLFW.GLFW_KEY_RIGHT_SUPER, "Win", 11.25f, y, 1.25f));
        keys.add(new Key(GLFW.GLFW_KEY_MENU, "Menu", 12.5f, y, 1.25f));
        keys.add(new Key(GLFW.GLFW_KEY_RIGHT_CONTROL, "Ctrl", 13.75f, y, 1.25f));
        return List.copyOf(keys);
    }

    private static InputConstants.Key keyFor(int code) {
        return code <= MOUSE_BASE
                ? InputConstants.Type.MOUSE.getOrCreate(MOUSE_BASE - code)
                : InputConstants.Type.KEYSYM.getOrCreate(code);
    }

    /** All mappings currently bound to {@code code}. */
    private static List<KeyMapping> usersOf(Map<String, List<KeyMapping>> byBinding, int code) {
        List<KeyMapping> users = byBinding.get(keyFor(code).getName());
        return users == null ? List.of() : users;
    }

    private static Map<String, List<KeyMapping>> bindingIndex(MenuHost host) {
        Map<String, List<KeyMapping>> index = new HashMap<>();
        for (KeyMapping mapping : host.minecraft().options.keyMappings) {
            if (mapping.isUnbound()) continue;
            index.computeIfAbsent(mapping.saveString(), key -> new ArrayList<>()).add(mapping);
        }
        return index;
    }

    public static VantaUiNode keyButton(MenuHost host, KeyMapping mapping) {
        return MenuComponents.valueButton(
                () -> host.pendingKey() == mapping ? "Press a key…" : mapping.getTranslatedKeyMessage().getString(),
                () -> host.pendingKey() == mapping,
                () -> host.hasConflict(mapping),
                () -> host.beginKeyCapture(mapping));
    }

    private static String actionName(KeyMapping mapping) {
        return Component.translatable(mapping.getName()).getString();
    }

    public static VantaUiNode build(MenuHost host) {
        Font font = host.font();
        Map<String, List<KeyMapping>> index = bindingIndex(host);
        VantaUiNode scroll = VantaUiNode.column(14, 24).flex(1).scrollable();

        int available = Math.max(300, host.contentWidth());
        float unit = Math.max(24.0f, Math.min(58.0f, (available - 150) / (LAYOUT_WIDTH + 0.2f)));
        int keyboardHeight = Math.round(LAYOUT_HEIGHT * unit) + 8;
        int keyboardWidth = Math.round(LAYOUT_WIDTH * unit);
        int selected = host.selectedKeyCode();

        VantaUiNode visual = VantaUiNode.canvas((g, f, x, y, w, h, st) ->
                        paintKeyboard(g, f, x, y, unit, index, selected, keyboardWidth))
                .height(keyboardHeight)
                .onClickAt((lx, ly, nodeWidth, nodeHeight) -> {
                    int code = hitTest(lx, ly, unit, keyboardWidth);
                    if (code != GLFW.GLFW_KEY_UNKNOWN) host.selectKeyCode(code);
                });
        scroll.add(visual);
        scroll.add(legend());

        if (selected != GLFW.GLFW_KEY_UNKNOWN) {
            addSelection(scroll, host, font, index, selected);
        }
        addSection(scroll, host, font, "VANTA", true);
        addSection(scroll, host, font, "MINECRAFT", false);
        return scroll;
    }

    // ---- keyboard drawing --------------------------------------------------------------------

    private static int stateColor(List<KeyMapping> users) {
        if (users.isEmpty()) return 0;
        return users.size() > 1 ? MenuTheme.DANGER : MenuTheme.accent();
    }

    private static void paintKeyboard(GuiGraphics g, Font font, int x, int y, float unit,
                                      Map<String, List<KeyMapping>> index, int selected, int keyboardWidth) {
        int gap = 4;
        int panelHeight = Math.round(LAYOUT_HEIGHT * unit) + 16;
        MenuTheme.fill(g, x - 14, y - 6, keyboardWidth + 24, panelHeight, 16, 0x5C0B0F18);
        MenuTheme.outline(g, x - 14, y - 6, keyboardWidth + 24, panelHeight, 16, 0x26FFFFFF);
        for (Key key : KEYS) {
            int kx = x + Math.round(key.x() * unit);
            int ky = y + 4 + Math.round(key.y() * unit);
            int kw = Math.round(key.w() * unit) - gap;
            int kh = Math.round(unit) - gap;
            paintKey(g, font, kx, ky, kw, kh, key.label(), usersOf(index, key.code()), selected == key.code(), 9);
        }
        int mx = x + keyboardWidth + 34;
        int my = y + 12;
        int bodyWidth = 84;
        int bodyHeight = Math.min(Math.round(unit * 3.2f), 150);
        MenuTheme.fill(g, mx - 14, y - 6, bodyWidth + 28, panelHeight, 16, 0x5C0B0F18);
        MenuTheme.outline(g, mx - 14, y - 6, bodyWidth + 28, panelHeight, 16, 0x26FFFFFF);
        MenuTheme.fill(g, mx, my, bodyWidth, bodyHeight, 26, 0x660F131C);
        MenuTheme.outline(g, mx, my, bodyWidth, bodyHeight, 26, 0x40FFFFFF);
        int buttonHeight = bodyHeight / 2 - 6;
        int half = bodyWidth / 2;
        paintKey(g, font, mx + 5, my + 5, half - 7, buttonHeight, "L",
                usersOf(index, MOUSE_BASE), selected == MOUSE_BASE, 14);
        paintKey(g, font, mx + half + 2, my + 5, half - 7, buttonHeight, "R",
                usersOf(index, MOUSE_BASE - 1), selected == MOUSE_BASE - 1, 14);
        paintKey(g, font, mx + half - 8, my + 5 + buttonHeight / 4, 16, buttonHeight / 2, "",
                usersOf(index, MOUSE_BASE - 2), selected == MOUSE_BASE - 2, 6);
    }

    private static void paintKey(GuiGraphics g, Font font, int x, int y, int w, int h, String label,
                                 List<KeyMapping> users, boolean selected, int radius) {
        int state = stateColor(users);
        if (state != 0) {
            MenuTheme.fill(g, x, y, w, h, radius, MenuTheme.alpha(state, 64));
            MenuTheme.outline(g, x, y, w, h, radius, MenuTheme.alpha(state, 235));
        } else {
            MenuTheme.fill(g, x, y, w, h, radius, 0x7A141926);
            MenuTheme.outline(g, x, y, w, h, radius, 0x30FFFFFF);
        }
        if (selected) {
            MenuTheme.outline(g, x - 2, y - 2, w + 4, h + 4, radius + 2, 0xFFFFFFFF);
        }
        if (!label.isEmpty()) {
            net.minecraft.resources.Identifier labelFont =
                    label.length() == 1 ? MenuTheme.FONT_MEDIUM : MenuTheme.FONT_SMALL;
            MenuTheme.textCentered(g, font, MenuTheme.fit(font, label, w - 4, labelFont),
                    x + w / 2, y + h / 2, state != 0 ? MenuTheme.TEXT : MenuTheme.TEXT_DIM, labelFont);
        }
    }

    private static int hitTest(int lx, int ly, float unit, int keyboardWidth) {
        int gap = 4;
        for (Key key : KEYS) {
            int kx = Math.round(key.x() * unit);
            int ky = 4 + Math.round(key.y() * unit);
            int kw = Math.round(key.w() * unit) - gap;
            int kh = Math.round(unit) - gap;
            if (lx >= kx && lx < kx + kw && ly >= ky && ly < ky + kh) return key.code();
        }
        int mx = keyboardWidth + 34;
        int my = 12;
        int bodyWidth = 84;
        int bodyHeight = Math.min(Math.round(unit * 3.2f), 150);
        int buttonHeight = bodyHeight / 2 - 6;
        int half = bodyWidth / 2;
        if (lx >= mx + half - 8 && lx < mx + half + 8 && ly >= my + 5 + buttonHeight / 4
                && ly < my + 5 + buttonHeight / 4 + buttonHeight / 2) return MOUSE_BASE - 2;
        if (ly >= my + 5 && ly < my + 5 + buttonHeight) {
            if (lx >= mx + 5 && lx < mx + half - 2) return MOUSE_BASE;
            if (lx >= mx + half + 2 && lx < mx + bodyWidth - 5) return MOUSE_BASE - 1;
        }
        return GLFW.GLFW_KEY_UNKNOWN;
    }

    private static VantaUiNode legend() {
        return VantaUiNode.canvas((g, font, x, y, w, h, st) -> {
            int cx = x + 4;
            int cy = y + h / 2;
            String[] labels = {"In use", "Clashing", "Free"};
            int[] colors = {MenuTheme.accent(), MenuTheme.DANGER, MenuTheme.CARD};
            for (int i = 0; i < labels.length; i++) {
                MenuTheme.fill(g, cx, cy - 7, 14, 14, 4, colors[i]);
                if (i == 2) MenuTheme.outline(g, cx, cy - 7, 14, 14, 4, 0x30FFFFFF);
                MenuTheme.text(g, font, labels[i], cx + 22, cy, MenuTheme.TEXT_DIM, MenuTheme.FONT_SMALL);
                cx += 22 + MenuTheme.width(font, labels[i], MenuTheme.FONT_SMALL) + 26;
            }
            MenuTheme.textRight(g, font, "Click a key to see what it does", x + w - 4, cy,
                    MenuTheme.TEXT_MUTED, MenuTheme.FONT_SMALL);
        }).height(30);
    }

    // ---- lists -------------------------------------------------------------------------------

    private static void addSelection(VantaUiNode page, MenuHost host, Font font,
                                     Map<String, List<KeyMapping>> index, int selected) {
        List<KeyMapping> users = usersOf(index, selected);
        String keyName = keyFor(selected).getDisplayName().getString();
        page.add(MenuComponents.sectionTitle(("BOUND TO " + keyName).toUpperCase(java.util.Locale.ROOT)));
        if (users.isEmpty()) {
            page.add(MenuComponents.settingRow(font, "Nothing is bound to " + keyName,
                    "Pick an action below and press this key to use it.", VantaUiNode.spacer(1), 1,
                    ModSettingsPage.descriptionWidth(host, 1)));
            return;
        }
        for (KeyMapping mapping : users) page.add(row(host, font, mapping));
    }

    private static void addSection(VantaUiNode page, MenuHost host, Font font, String title, boolean vanta) {
        Set<KeyMapping> vantaMappings = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (KeyMapping mapping : new KeyMapping[] {Keybinds.guiKey, Keybinds.fullbrightKey, Keybinds.zoomKey,
                Keybinds.sprintToggleKey, Keybinds.sneakToggleKey}) {
            if (mapping != null) vantaMappings.add(mapping);
        }
        for (FeatureKeybinds.Binding binding : FeatureKeybinds.bindings()) vantaMappings.add(binding.mapping());

        List<VantaUiNode> rows = new ArrayList<>();
        for (KeyMapping mapping : host.minecraft().options.keyMappings) {
            if (vantaMappings.contains(mapping) == vanta) rows.add(row(host, font, mapping));
        }
        if (rows.isEmpty()) return;
        page.add(MenuComponents.sectionTitle(title));
        for (VantaUiNode row : rows) page.add(row);
    }

    private static VantaUiNode row(MenuHost host, Font font, KeyMapping mapping) {
        VantaUiNode control = VantaUiNode.row(8, 0).height(32);
        control.add(keyButton(host, mapping).flex(1));
        control.add(MenuComponents.iconButton(Icon.RESET, "Reset to default", host.pendingKey() == null,
                () -> host.resetKey(mapping)).size(32, 32));
        String detail = null;
        if (host.hasConflict(mapping)) {
            for (KeyMapping other : host.minecraft().options.keyMappings) {
                if (other != mapping && !other.isUnbound() && other.saveString().equals(mapping.saveString())) {
                    detail = "Also used by " + actionName(other);
                    break;
                }
            }
        }
        return MenuComponents.settingRow(font, actionName(mapping), detail, control, 196,
                ModSettingsPage.descriptionWidth(host, 196));
    }
}
