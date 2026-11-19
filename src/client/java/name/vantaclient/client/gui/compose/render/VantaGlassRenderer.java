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
        // Minecraft 1.21.11 resolves this request in its GUI render graph. Unlike
        // a translucent tint, it blurs the already-rendered world behind this UI.
        graphics.blurBeforeThisStratum();
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
        if (state == VantaCompose.State.DISABLED) alpha = Math.round(alpha * 0.68f);

        int safeRadius = Math.max(0, Math.min(radius, Math.min(width / 2, height / 2)));
        if (material == VantaCompose.Material.PANEL || material == VantaCompose.Material.ELEVATED
                || material == VantaCompose.Material.POPOVER) {
            VantaUiTheme.fillSquircle(graphics, x, y + 2, width, height,
                    safeRadius, VantaUiTheme.withAlpha(glass.shadow(), 0x25));
        }
        VantaUiTheme.fillSquircle(graphics, x, y, width, height, safeRadius,
                VantaUiTheme.withAlpha(tint, alpha));

        // A single restrained top-edge reflection gives the material depth without
        // layering multiple decorative gradients over every surface.
        if (height > 3 && width > safeRadius * 2) {
            graphics.fill(x + safeRadius, y + 1, x + width - safeRadius, y + 2,
                    VantaUiTheme.withAlpha(glass.highlight(), 0x22));
        }

        int edgeAlpha = state == VantaCompose.State.DISABLED ? 0x24 : 0x42;
        VantaUiTheme.outlineSquircle(graphics, x, y, width, height, safeRadius,
                VantaUiTheme.withAlpha(glass.edge(), edgeAlpha));

        if (state == VantaCompose.State.HOVERED) {
            VantaUiTheme.fillSquircle(graphics, x + 1, y + 1, width - 2, height - 2,
                    Math.max(0, safeRadius - 1), VantaUiTheme.withAlpha(palette.accent(), 0x0C));
            VantaUiTheme.outlineSquircle(graphics, x, y, width, height, safeRadius,
                    VantaUiTheme.withAlpha(palette.accent(), 0x78));
        } else if (state == VantaCompose.State.SELECTED) {
            VantaUiTheme.fillSquircle(graphics, x + 1, y + 1, width - 2, height - 2,
                    Math.max(0, safeRadius - 1), VantaUiTheme.withAlpha(palette.accent(), 0x16));
            VantaUiTheme.outlineSquircle(graphics, x, y, width, height, safeRadius,
                    VantaUiTheme.withAlpha(palette.accent(), 0xD8));
            graphics.fill(x + 1, y + Math.min(5, height / 3),
                    x + 3, y + height - Math.min(5, height / 3),
                    VantaUiTheme.withAlpha(palette.accent(), 0xE8));
        } else if (state == VantaCompose.State.FOCUSED) {
            VantaUiTheme.outlineSquircle(graphics, x, y, width, height, safeRadius,
                    VantaUiTheme.withAlpha(palette.accent(), 0xF0), 2);
        }
    }
}
