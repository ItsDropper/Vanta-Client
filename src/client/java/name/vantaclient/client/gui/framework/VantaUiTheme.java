package name.vantaclient.client.gui.framework;

import name.vantaclient.client.config.VantaClientPreferences;

/**
 * Central visual tokens for every Vanta-native UI component.
 *
 * Tokens are resolved at render time so Appearance changes are reflected
 * without rebuilding the screen tree.
 */
public final class VantaUiTheme {
    public static final int CANVAS = 0xFF101113;
    public static final int PANEL = 0xFF181A1D;
    public static final int PANEL_RAISED = 0xFF202328;
    public static final int SURFACE = 0xFF292D33;
    public static final int SURFACE_HOVER = 0xFF343941;
    public static final int SURFACE_SELECTED = 0xFF303A48;
    public static final int BORDER = 0xFF3A3F46;
    public static final int BORDER_SUBTLE = 0xFF2B2F35;
    public static final int TEXT_PRIMARY = 0xFFF3F4F5;
    public static final int TEXT_SECONDARY = 0xFFB9BEC5;
    public static final int TEXT_MUTED = 0xFF858C96;
    public static final int TRACK = 0xFF424850;
    public static final int INPUT_SURFACE = 0xFF17191C;
    public static final int INPUT_FOCUS_SURFACE = 0xFF24282E;
    public static final int TOOLTIP_SURFACE = 0xF0141619;
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
}
