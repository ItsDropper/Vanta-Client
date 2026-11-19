package name.vantaclient.client.gui.compose;

import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import net.minecraft.client.gui.GuiGraphics;

/**
 * VantaCompose's native rendering foundation.
 *
 * The glass material is backed by Minecraft's GUI render-graph blur marker,
 * then composed from translucent tint, restrained specular response, inner
 * edge and state-aware borders. A tinted rectangle by itself is not treated
 * as a blur implementation.
 */
public final class VantaCompose {
    public interface Screen {}

    public enum BackdropBlurStrategy {
        MINECRAFT_GUI_RENDER_GRAPH
    }

    public enum Material {
        PANEL, ELEVATED, CARD, FIELD, POPOVER
    }

    public enum State {
        NORMAL, HOVERED, FOCUSED, SELECTED, DISABLED
    }

    public record Palette(
            int canvas, int textPrimary, int textSecondary, int textMuted,
            int accent, int border, int borderSubtle
    ) {}

    public record Spacing(
            int xs, int sm, int md, int lg, int xl, int xxl,
            int radiusSmall, int radiusMedium, int radiusLarge,
            int radiusPanel, int controlHeight, int rowHeight
    ) {}

    public record Glass(
            int panelTint, int elevatedTint, int cardTint, int fieldTint, int popoverTint,
            int panelAlpha, int elevatedAlpha, int cardAlpha, int fieldAlpha, int popoverAlpha,
            int edge, int highlight, int shadow, int scrimTop, int scrimBottom
    ) {}

    public record Motion(float hoverRate, float pressRate, float selectionRate, boolean enabled) {}

    public record Theme(
            Palette palette,
            Spacing spacing,
            Glass glass,
            Motion motion,
            BackdropBlurStrategy blurStrategy
    ) {}

    private VantaCompose() {}

    /** One immutable token snapshot per UI frame, refreshed by VantaUiRuntime. */
    public static Theme theme() {
        VantaClientPreferences preferences = VantaClientPreferences.get();
        double configuredOpacity = preferences.panelOpacity;
        if (!Double.isFinite(configuredOpacity)) configuredOpacity = 0.85;
        float opacity = (float) Math.max(0.72, Math.min(0.98, configuredOpacity));

        Palette palette = new Palette(
                VantaUiTheme.CANVAS, VantaUiTheme.TEXT_PRIMARY,
                VantaUiTheme.TEXT_SECONDARY, VantaUiTheme.TEXT_MUTED,
                preferences.accent.color(), VantaUiTheme.BORDER, VantaUiTheme.BORDER_SUBTLE
        );
        Spacing spacing = new Spacing(4, 8, 12, 16, 20, 24,
                5, 8, 12, 16, 36, 48);
        Glass glass = new Glass(
                0xFF101B2A, 0xFF17263A, 0xFF111F31, 0xFF0B1522, 0xFF0A1420,
                Math.round(0xB0 * opacity), Math.round(0xC2 * opacity),
                Math.round(0xA8 * opacity), Math.round(0xC0 * opacity),
                Math.round(0xD0 * opacity),
                0xFF91A9C6, 0xFFFFFFFF, 0xFF030811,
                0x68050B15, 0x78070D17
        );
        Motion motion = new Motion(16.0f, 22.0f, 14.0f, preferences.animations);
        return new Theme(palette, spacing, glass, motion,
                BackdropBlurStrategy.MINECRAFT_GUI_RENDER_GRAPH);
    }

    public static int resolveTintAlpha(Material material, float panelOpacity) {
        float opacity = Float.isFinite(panelOpacity)
                ? Math.max(0.72f, Math.min(0.98f, panelOpacity)) : 0.85f;
        int base = switch (material) {
            case PANEL -> 0xB0;
            case ELEVATED -> 0xC2;
            case CARD -> 0xA8;
            case FIELD -> 0xC0;
            case POPOVER -> 0xD0;
        };
        return Math.max(1, Math.min(255, Math.round(base * opacity)));
    }

    /** Draws the shared screen scrim after the real GUI backdrop blur is queued. */
    public static void renderScrim(GuiGraphics graphics, int width, int height) {
        if (graphics == null || width <= 0 || height <= 0) return;
        Glass glass = theme().glass();
        graphics.fillGradient(0, 0, width, height, glass.scrimTop(), glass.scrimBottom());
    }

    /** Material renderer shared by retained nodes and future Compose controls. */
    public static void drawSurface(
            GuiGraphics graphics, Theme theme, Material material, State state,
            int x, int y, int width, int height, int radius
    ) {
        if (graphics == null || theme == null || material == null || state == null
                || width <= 0 || height <= 0) return;

        Glass glass = theme.glass();
        Palette palette = theme.palette();
        int tint = switch (material) {
            case PANEL -> glass.panelTint();
            case ELEVATED -> glass.elevatedTint();
            case CARD -> glass.cardTint();
            case FIELD -> glass.fieldTint();
            case POPOVER -> glass.popoverTint();
        };
        int alpha = switch (material) {
            case PANEL -> glass.panelAlpha();
            case ELEVATED -> glass.elevatedAlpha();
            case CARD -> glass.cardAlpha();
            case FIELD -> glass.fieldAlpha();
            case POPOVER -> glass.popoverAlpha();
        };
        if (state == State.DISABLED) alpha = Math.round(alpha * 0.68f);

        int safeRadius = Math.max(0, Math.min(radius, Math.min(width / 2, height / 2)));
        if (material == Material.PANEL || material == Material.ELEVATED || material == Material.POPOVER) {
            VantaUiTheme.fillSquircle(graphics, x, y + 2, width, height,
                    safeRadius, VantaUiTheme.withAlpha(glass.shadow(), 0x25));
        }
        VantaUiTheme.fillSquircle(graphics, x, y, width, height, safeRadius,
                VantaUiTheme.withAlpha(tint, alpha));

        // Restrained specular edge: a crisp reflection line plus a short,
        // fading wash. The surface never relies on a fake blur texture.
        int inset = Math.min(safeRadius, Math.max(2, width / 4));
        int left = x + inset;
        int right = x + width - inset;
        if (right > left && height > 4) {
            graphics.fill(left, y + 1, right, y + 2,
                    VantaUiTheme.withAlpha(glass.highlight(), 0x30));
            int washBottom = y + Math.min(height - 2, Math.max(5, height / 4));
            if (washBottom > y + 2) {
                graphics.fillGradient(left + 1, y + 2, right - 1, washBottom,
                        VantaUiTheme.withAlpha(glass.highlight(), 0x0D),
                        VantaUiTheme.withAlpha(glass.highlight(), 0x00));
            }
        }

        int edgeAlpha = state == State.DISABLED ? 0x28 : 0x58;
        VantaUiTheme.outlineSquircle(graphics, x, y, width, height, safeRadius,
                VantaUiTheme.withAlpha(glass.edge(), edgeAlpha));

        if (state == State.HOVERED) {
            VantaUiTheme.fillSquircle(graphics, x + 1, y + 1, width - 2, height - 2,
                    Math.max(0, safeRadius - 1), VantaUiTheme.withAlpha(palette.accent(), 0x0C));
            VantaUiTheme.outlineSquircle(graphics, x, y, width, height, safeRadius,
                    VantaUiTheme.withAlpha(palette.accent(), 0x78));
        } else if (state == State.SELECTED) {
            VantaUiTheme.fillSquircle(graphics, x + 1, y + 1, width - 2, height - 2,
                    Math.max(0, safeRadius - 1), VantaUiTheme.withAlpha(palette.accent(), 0x16));
            VantaUiTheme.outlineSquircle(graphics, x, y, width, height, safeRadius,
                    VantaUiTheme.withAlpha(palette.accent(), 0xD8));
            graphics.fill(x + 1, y + Math.min(5, height / 3),
                    x + 3, y + height - Math.min(5, height / 3),
                    VantaUiTheme.withAlpha(palette.accent(), 0xE8));
        } else if (state == State.FOCUSED) {
            VantaUiTheme.outlineSquircle(graphics, x, y, width, height, safeRadius,
                    VantaUiTheme.withAlpha(palette.accent(), 0xF0), 2);
        }
    }
}
