package name.vantaclient.client.gui.compose;

import name.vantaclient.client.gui.compose.render.VantaGlassRenderer;
import name.vantaclient.client.gui.compose.theme.VantaTheme;
import name.vantaclient.client.gui.compose.theme.VantaThemeFactory;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Stable entry point for VantaCompose.
 *
 * Theme resolution and Minecraft drawing live in dedicated subsystem packages;
 * this class intentionally contains only the public screen/material vocabulary
 * and compatibility-facing delegation methods.
 */
public final class VantaCompose {
    public interface Screen {}

    public enum Material {
        PANEL, ELEVATED, CARD, FIELD, POPOVER
    }

    public enum State {
        NORMAL, HOVERED, FOCUSED, SELECTED, DISABLED
    }

    private VantaCompose() {}

    /** Immutable token snapshot, refreshed once per retained UI frame. */
    public static VantaTheme theme() {
        return VantaThemeFactory.create();
    }

    public static int resolveTintAlpha(Material material, float panelOpacity) {
        return VantaGlassRenderer.resolveTintAlpha(material, panelOpacity);
    }

    public static void renderScrim(GuiGraphics graphics, int width, int height) {
        VantaGlassRenderer.renderScrim(graphics, width, height, theme());
    }

    public static void drawSurface(
            GuiGraphics graphics, VantaTheme theme, Material material, State state,
            int x, int y, int width, int height, int radius
    ) {
        VantaGlassRenderer.drawSurface(
                graphics, theme, material, state, x, y, width, height, radius
        );
    }
}
