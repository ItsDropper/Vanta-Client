package name.vantaclient.client.gui.compose.theme;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.compose.typography.VantaTypography;

/** Guards the design-token hierarchy, semantic palette and accent contrast. */
@SuppressWarnings("UnstableApiUsage")
public final class VantaThemeDesignSystemGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        verifyTokenHierarchy();
        verifyAccentContrast();
        verifyMaterialStateTokens();
    }

    private static void verifyTokenHierarchy() {
        VantaTheme theme = VantaThemeFactory.create();
        VantaTheme.Spacing spacing = theme.spacing();
        require(spacing.xs() <= spacing.sm() && spacing.sm() <= spacing.md()
                        && spacing.md() <= spacing.lg() && spacing.lg() <= spacing.xl()
                        && spacing.xl() <= spacing.xxl(),
                "Spacing tokens must remain ordered.");
        require(spacing.radiusPanel() >= spacing.radiusLarge(),
                "Panel radius must not be smaller than the standard component radius.");

        VantaTheme.Typography type = theme.typography();
        require(type.displaySize() >= type.headingSize()
                        && type.headingSize() >= type.titleSize()
                        && type.titleSize() >= type.bodySize()
                        && type.bodySize() >= type.labelSize()
                        && type.labelSize() >= type.captionSize(),
                "Typography sizes must follow the display-to-caption hierarchy.");
        require(type.displayLineHeight() >= type.displaySize()
                        && type.headingLineHeight() >= type.headingSize()
                        && type.titleLineHeight() >= type.titleSize(),
                "Large typography roles must not clip vertically.");
        require(VantaTypography.font(VantaTypography.Role.HEADING).getPath().equals("heading")
                        && VantaTypography.font(VantaTypography.Role.CAPTION).getPath().equals("caption"),
                "Semantic roles must use the bundled Vanta font providers.");
        require(theme.palette().success() != theme.palette().warning()
                        && theme.palette().warning() != theme.palette().danger(),
                "Status roles must remain visually distinct.");
    }

    private static void verifyAccentContrast() {
        for (VantaClientPreferences.Accent accent : VantaClientPreferences.Accent.values()) {
            int foreground = VantaThemeFactory.contrastingForeground(accent.color());
            double ratio = contrastRatio(relativeLuminance(accent.color()),
                    relativeLuminance(foreground));
            require(ratio >= 4.5,
                    "Accent " + accent.name() + " has insufficient foreground contrast: " + ratio);
        }
        VantaTheme theme = VantaThemeFactory.create();
        require(theme.palette().accentForeground() == VantaThemeFactory.contrastingForeground(
                        theme.palette().accent()),
                "The active theme must expose its computed accessible accent foreground.");
    }

    private static void verifyMaterialStateTokens() {
        VantaTheme.Glass glass = VantaThemeFactory.create().glass();
        require(glass.shadowAlpha() > 0 && glass.highlightAlpha() > 0
                        && glass.borderAlpha() > 0 && glass.hoverAlpha() > 0
                        && glass.selectedAlpha() > glass.hoverAlpha()
                        && glass.focusAlpha() >= glass.selectedAlpha()
                        && glass.disabledAlpha() < glass.borderAlpha(),
                "Glass materials must define distinct shadow, hover, selection, focus and disabled states.");
    }

    private static double contrastRatio(double first, double second) {
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double relativeLuminance(int color) {
        double red = linear((color >>> 16 & 0xFF) / 255.0);
        double green = linear((color >>> 8 & 0xFF) / 255.0);
        double blue = linear((color & 0xFF) / 255.0);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linear(double channel) {
        return channel <= 0.04045 ? channel / 12.92
                : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
