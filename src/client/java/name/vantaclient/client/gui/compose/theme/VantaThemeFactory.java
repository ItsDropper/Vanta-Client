package name.vantaclient.client.gui.compose.theme;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiTheme;

/** Resolves immutable Vanta design-system snapshots from existing user preferences. */
public final class VantaThemeFactory {
    private static final float DEFAULT_PANEL_OPACITY = 0.85f;
    private static final float MIN_PANEL_OPACITY = 0.72f;
    private static final float MAX_PANEL_OPACITY = 0.98f;

    private static volatile VantaTheme cached;
    private static int cachedAccent;
    private static int cachedOpacityBits;
    private static boolean cachedAnimations;

    private VantaThemeFactory() {}

    public static VantaTheme create() {
        VantaClientPreferences preferences = VantaClientPreferences.get();
        double configuredOpacity = preferences.panelOpacity;
        if (!Double.isFinite(configuredOpacity)) configuredOpacity = DEFAULT_PANEL_OPACITY;
        float opacity = (float) Math.max(MIN_PANEL_OPACITY,
                Math.min(MAX_PANEL_OPACITY, configuredOpacity));
        int accent = preferences.accent.color();
        boolean animations = preferences.animations;
        int opacityBits = Float.floatToIntBits(opacity);

        VantaTheme snapshot = cached;
        if (snapshot != null && cachedAccent == accent
                && cachedOpacityBits == opacityBits && cachedAnimations == animations) {
            return snapshot;
        }

        synchronized (VantaThemeFactory.class) {
            snapshot = cached;
            if (snapshot != null && cachedAccent == accent
                    && cachedOpacityBits == opacityBits && cachedAnimations == animations) {
                return snapshot;
            }

            VantaTheme.Palette palette = new VantaTheme.Palette(
                    VantaUiTheme.CANVAS, VantaUiTheme.TEXT_PRIMARY,
                    VantaUiTheme.TEXT_SECONDARY, VantaUiTheme.TEXT_MUTED,
                    accent, contrastingForeground(accent), VantaUiTheme.BORDER,
                    VantaUiTheme.BORDER_SUBTLE, accent, VantaUiTheme.SURFACE_SELECTED,
                    VantaUiTheme.STATUS_INFO, VantaUiTheme.STATUS_SUCCESS,
                    VantaUiTheme.STATUS_WARNING, VantaUiTheme.STATUS_DANGER
            );
            VantaTheme.Spacing spacing = new VantaTheme.Spacing(
                    VantaUiTheme.SPACE_1, VantaUiTheme.SPACE_2, VantaUiTheme.SPACE_3,
                    VantaUiTheme.SPACE_4, VantaUiTheme.SPACE_5, VantaUiTheme.SPACE_6,
                    VantaUiTheme.RADIUS_SMALL, VantaUiTheme.RADIUS_MEDIUM,
                    VantaUiTheme.RADIUS_LARGE, VantaUiTheme.RADIUS_PANEL,
                    VantaUiTheme.CONTROL_HEIGHT, VantaUiTheme.ROW_HEIGHT
            );
            VantaTheme.Typography typography = new VantaTheme.Typography(
                    28, 26, 15, 13, 13, 12, 34, 30, 22, 14, 12
            );
            VantaTheme.Glass glass = new VantaTheme.Glass(
                    0xFF101B2A, 0xFF17263A, 0xFF111F31, 0xFF0B1522, 0xFF0A1420,
                    Math.round(0xB0 * opacity), Math.round(0xC2 * opacity),
                    Math.round(0xA8 * opacity), Math.round(0xC0 * opacity),
                    Math.round(0xD0 * opacity),
                    0xFF91A9C6, 0xFFFFFFFF, 0xFF030811,
                    0x68050B15, 0x78070D17,
                    0x25, 0x22, 0x42, 0x0C, 0x16, 0xF0, 0x68
            );
            VantaTheme.Motion motion = new VantaTheme.Motion(
                    16.0f, 22.0f, 14.0f, animations
            );
            cachedAccent = accent;
            cachedOpacityBits = opacityBits;
            cachedAnimations = animations;
            snapshot = new VantaTheme(palette, spacing, typography, glass, motion,
                    VantaTheme.BackdropBlurStrategy.MINECRAFT_GUI_RENDER_GRAPH);
            cached = snapshot;
            return snapshot;
        }
    }

    /** Picks whichever monochrome foreground has the stronger contrast against the accent. */
    static int contrastingForeground(int color) {
        double luminance = relativeLuminance(color);
        double whiteContrast = 1.05 / (luminance + 0.05);
        double darkContrast = (luminance + 0.05) / 0.05;
        return darkContrast >= whiteContrast ? 0xFF07111F : 0xFFFFFFFF;
    }

    private static double relativeLuminance(int color) {
        double red = linear(((color >>> 16) & 0xFF) / 255.0);
        double green = linear(((color >>> 8) & 0xFF) / 255.0);
        double blue = linear((color & 0xFF) / 255.0);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92
                : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
