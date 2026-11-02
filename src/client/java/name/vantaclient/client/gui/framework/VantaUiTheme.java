package name.vantaclient.client.gui.framework;

import name.vantaclient.client.config.VantaClientPreferences;

/**
 * Central visual tokens for every Vanta-native UI component.
 *
 * Tokens are resolved at render time so Appearance changes are reflected
 * without rebuilding the screen tree.
 */
public final class VantaUiTheme {
    public static final int CANVAS = 0xFF0B0F16;
    public static final int PANEL = 0xFF111823;
    public static final int PANEL_RAISED = 0xFF151E2B;
    public static final int SURFACE = 0xFF192332;
    public static final int SURFACE_HOVER = 0xFF202E40;
    public static final int BORDER = 0xFF2A394C;
    public static final int BORDER_SUBTLE = 0xFF202B3A;
    public static final int TEXT_PRIMARY = 0xFFEAF0F8;
    public static final int TEXT_SECONDARY = 0xFF9AA8BA;
    public static final int TEXT_MUTED = 0xFF68788D;
    public static final int TRACK = 0xFF303D50;
    public static final int INPUT_SURFACE = 0xFF0E1621;
    public static final int INPUT_FOCUS_SURFACE = 0xFF111B29;
    public static final int TOOLTIP_SURFACE = 0xF00A1019;
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

    public static final int RADIUS_SMALL = 3;
    public static final int RADIUS_MEDIUM = 6;
    public static final int RADIUS_LARGE = 10;

    public static final int TEXT_LINE_HEIGHT = 9;
    public static final int CARET_BLINK_TICKS = 10;
    public static final float HOVER_EASING = 0.30f;
    public static final float TOGGLE_EASING = 0.28f;

    public int accent() {
        return VantaClientPreferences.get().accent.color();
    }

    public boolean animationsEnabled() {
        return VantaClientPreferences.get().animations;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }
}
