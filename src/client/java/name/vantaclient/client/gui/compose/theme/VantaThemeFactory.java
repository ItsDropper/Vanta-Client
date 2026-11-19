package name.vantaclient.client.gui.compose.theme;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiTheme;

/** Resolves immutable Vanta theme snapshots from the existing user preferences. */
public final class VantaThemeFactory {
    private static final float DEFAULT_PANEL_OPACITY = 0.85f;
    private static final float MIN_PANEL_OPACITY = 0.72f;
    private static final float MAX_PANEL_OPACITY = 0.98f;

    private VantaThemeFactory() {}

    public static VantaTheme create() {
        VantaClientPreferences preferences = VantaClientPreferences.get();
        double configuredOpacity = preferences.panelOpacity;
        if (!Double.isFinite(configuredOpacity)) configuredOpacity = DEFAULT_PANEL_OPACITY;
        float opacity = (float) Math.max(MIN_PANEL_OPACITY,
                Math.min(MAX_PANEL_OPACITY, configuredOpacity));

        VantaTheme.Palette palette = new VantaTheme.Palette(
                VantaUiTheme.CANVAS, VantaUiTheme.TEXT_PRIMARY,
                VantaUiTheme.TEXT_SECONDARY, VantaUiTheme.TEXT_MUTED,
                preferences.accent.color(), VantaUiTheme.BORDER, VantaUiTheme.BORDER_SUBTLE
        );
        VantaTheme.Spacing spacing = new VantaTheme.Spacing(
                4, 8, 12, 16, 20, 24, 5, 8, 12, 16, 36, 48
        );
        VantaTheme.Glass glass = new VantaTheme.Glass(
                0xFF101B2A, 0xFF17263A, 0xFF111F31, 0xFF0B1522, 0xFF0A1420,
                Math.round(0xB0 * opacity), Math.round(0xC2 * opacity),
                Math.round(0xA8 * opacity), Math.round(0xC0 * opacity),
                Math.round(0xD0 * opacity),
                0xFF91A9C6, 0xFFFFFFFF, 0xFF030811,
                0x68050B15, 0x78070D17
        );
        VantaTheme.Motion motion = new VantaTheme.Motion(
                16.0f, 22.0f, 14.0f, preferences.animations
        );
        return new VantaTheme(palette, spacing, glass, motion,
                VantaTheme.BackdropBlurStrategy.MINECRAFT_GUI_RENDER_GRAPH);
    }
}
