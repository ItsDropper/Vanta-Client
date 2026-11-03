package name.vantaclient.client.gui.framework;

import name.vantaclient.client.config.VantaClientPreferences;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Central visual tokens for every Vanta-native UI component.
 *
 * Tokens are resolved at render time so Appearance changes are reflected
 * without rebuilding the screen tree.
 */
public final class VantaUiTheme {
    public static final int CANVAS = 0xD90A1019;
    public static final int PANEL = 0xD91A2331;
    public static final int PANEL_RAISED = 0xCA202D40;
    public static final int SURFACE = 0x382E3B50;
    public static final int SURFACE_HOVER = 0x693A4B62;
    public static final int SURFACE_SELECTED = 0x6B3B76AC;
    public static final int BORDER = 0x643A4D65;
    public static final int BORDER_SUBTLE = 0x382E3C50;
    public static final int BUTTON_OUTLINE = 0x8C9AA8B8;
    public static final int BUTTON_OUTLINE_HOVER = 0xC0C9D3DF;
    public static final int TEXT_PRIMARY = 0xFFF1F5FC;
    public static final int TEXT_SECONDARY = 0xFFB5C0D0;
    public static final int TEXT_MUTED = 0xFF8290A4;
    public static final int TRACK = 0xA02A3749;
    public static final int INPUT_SURFACE = 0xA20B1320;
    public static final int INPUT_FOCUS_SURFACE = 0xBE17263A;
    public static final int TOOLTIP_SURFACE = 0xEE101925;
    public static final int WHITE = 0xFFFFFFFF;

    public static final int SPACE_1 = 4;
    public static final int SPACE_2 = 8;
    public static final int SPACE_3 = 12;
    public static final int SPACE_4 = 16;
    public static final int SPACE_5 = 20;
    public static final int SPACE_6 = 24;

    public static final int CONTROL_HEIGHT = 32;
    public static final int ROW_HEIGHT = 42;
    public static final int SLIDER_HEIGHT = 46;

    public static final int RADIUS_SMALL = 4;
    public static final int RADIUS_MEDIUM = 8;
    public static final int RADIUS_LARGE = 12;

    public static final int TEXT_LINE_HEIGHT = 11;
    public static final int CARET_BLINK_TICKS = 10;
    public static final float HOVER_RATE = 16.0f;
    public static final float TOGGLE_RATE = 14.0f;

    public int accent() {
        return VantaClientPreferences.get().accent.color();
    }

    public boolean animationsEnabled() {
        return VantaClientPreferences.get().animations;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    public static int applyPanelOpacity(int color) {
        if (color != PANEL && color != PANEL_RAISED) return color;
        double opacity = VantaClientPreferences.get().panelOpacity;
        if (!Double.isFinite(opacity)) opacity = 0.85;
        opacity = Math.max(0.0, Math.min(1.0, opacity));
        int baseAlpha = (color >>> 24) & 0xFF;
        return withAlpha(color, (int) Math.round(baseAlpha * opacity));
    }

    /** Crisp one-pixel rounded outline; no dark offset plate or side box. */
    public static void outlineRounded(GuiGraphics graphics, int x, int y, int width,
                                      int height, int radius, int color) {
        if (graphics == null || width <= 1 || height <= 1 || color == 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(width / 2, height / 2)));
        if (r == 0) {
            graphics.fill(x, y, x + width, y + 1, color);
            graphics.fill(x, y + height - 1, x + width, y + height, color);
            graphics.fill(x, y + 1, x + 1, y + height - 1, color);
            graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
            return;
        }
        for (int row = 0; row < height; row++) {
            int distanceFromCorner = row < r ? r - row
                    : row >= height - r ? row - (height - r - 1) : 0;
            int inset = distanceFromCorner == 0 ? 0
                    : r - (int) Math.floor(Math.sqrt((double) r * r
                    - (double) distanceFromCorner * distanceFromCorner));
            if (row == 0 || row == height - 1) {
                graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            } else {
                graphics.fill(x + inset, y + row, x + inset + 1, y + row + 1, color);
                graphics.fill(x + width - inset - 1, y + row,
                        x + width - inset, y + row + 1, color);
            }
        }
    }

    /** Shared rounded fill used by Vanta-native controls and floating surfaces. */
    public static void fillRounded(GuiGraphics graphics, int x, int y, int width,
                                   int height, int radius, int color) {
        if (graphics == null || width <= 0 || height <= 0 || color == 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(width / 2, height / 2)));
        if (r == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + r, y, x + width - r, y + height, color);
        for (int row = 0; row < r; row++) {
            int dy = r - row;
            int inset = r - (int) Math.floor(Math.sqrt((double) r * r - (double) dy * dy));
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            graphics.fill(x + inset, y + height - row - 1,
                    x + width - inset, y + height - row, color);
        }
    }
}
