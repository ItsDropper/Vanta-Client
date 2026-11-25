package name.vantaclient.client.gui.compose.render;

import name.vantaclient.client.gui.compose.VantaCompose;
import name.vantaclient.client.gui.compose.theme.VantaTheme;
import name.vantaclient.client.gui.framework.VantaUiTheme;
import net.minecraft.client.gui.GuiGraphics;

/** Minecraft-native renderer for VantaCompose's layered glass materials. */
public final class VantaGlassRenderer {
    private VantaGlassRenderer() {}

    public static int resolveTintAlpha(VantaCompose.Material material, float panelOpacity) {
        if (material == null) throw new IllegalArgumentException("Material cannot be null.");
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

    public static void renderScrim(GuiGraphics graphics, int width, int height, VantaTheme theme) {
        if (graphics == null || theme == null || width <= 0 || height <= 0) return;
        // Screen background rendering owns Minecraft's once-per-frame blur pass.
        // Requesting it again here throws when a screen has already added its blur stratum.
        // Keep this layer to the scrim; surfaces remain translucent and composited by GuiGraphics.
        VantaTheme.Glass glass = theme.glass();
        graphics.fillGradient(0, 0, width, height, glass.scrimTop(), glass.scrimBottom());
    }

    public static void drawSurface(
            GuiGraphics graphics, VantaTheme theme, VantaCompose.Material material,
            VantaCompose.State state, int x, int y, int width, int height, int radius
    ) {
        if (graphics == null || theme == null || material == null || state == null
                || width <= 0 || height <= 0) return;

        VantaTheme.Glass glass = theme.glass();
        VantaTheme.Palette palette = theme.palette();
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
        if (state == VantaCompose.State.DISABLED) {
            alpha = Math.round(alpha * glass.disabledSurfaceFactor());
        }

        int safeRadius = Math.max(0, Math.min(radius, Math.min(width / 2, height / 2)));
        if (material == VantaCompose.Material.PANEL || material == VantaCompose.Material.ELEVATED
                || material == VantaCompose.Material.POPOVER) {
            VantaUiTheme.fillSquircle(graphics, x, y + 2, width, height,
                    safeRadius, VantaUiTheme.withAlpha(glass.shadow(), glass.shadowAlpha()));
        }
        VantaUiTheme.fillSquircle(graphics, x, y, width, height, safeRadius,
                VantaUiTheme.withAlpha(tint, alpha));

        // A single restrained top-edge reflection gives the material depth without
        // layering multiple decorative gradients over every surface.
        if (height > 3 && width > safeRadius * 2) {
            graphics.fill(x + safeRadius, y + 1, x + width - safeRadius, y + 2,
                    VantaUiTheme.withAlpha(glass.highlight(), glass.highlightAlpha()));
        }

        int edgeAlpha = state == VantaCompose.State.DISABLED
                ? glass.disabledAlpha() : glass.borderAlpha();
        VantaUiTheme.outlineSquircle(graphics, x, y, width, height, safeRadius,
                VantaUiTheme.withAlpha(glass.edge(), edgeAlpha));

        if (state == VantaCompose.State.HOVERED) {
            VantaUiTheme.fillSquircle(graphics, x + 1, y + 1, width - 2, height - 2,
                    Math.max(0, safeRadius - 1), VantaUiTheme.withAlpha(palette.accent(), glass.hoverAlpha()));
            VantaUiTheme.outlineSquircle(graphics, x, y, width, height, safeRadius,
                    VantaUiTheme.withAlpha(palette.accent(), glass.hoverOutlineAlpha()));
        } else if (state == VantaCompose.State.SELECTED) {
            VantaUiTheme.fillSquircle(graphics, x + 1, y + 1, width - 2, height - 2,
                    Math.max(0, safeRadius - 1), VantaUiTheme.withAlpha(palette.selection(), glass.selectedAlpha()));
            VantaUiTheme.outlineSquircle(graphics, x, y, width, height, safeRadius,
                    VantaUiTheme.withAlpha(palette.accent(), glass.focusAlpha()));
            graphics.fill(x + 1, y + Math.min(5, height / 3),
                    x + 3, y + height - Math.min(5, height / 3),
                    VantaUiTheme.withAlpha(palette.accent(), glass.focusAlpha()));
        } else if (state == VantaCompose.State.FOCUSED) {
            VantaUiTheme.outlineSquircle(graphics, x, y, width, height, safeRadius,
                    VantaUiTheme.withAlpha(palette.focus(), glass.focusAlpha()), VantaUiTheme.FOCUS_RING_WIDTH);
        }
    }
}
