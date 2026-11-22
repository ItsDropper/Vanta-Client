package name.vantaclient.client.gui.compose;

import name.vantaclient.client.gui.compose.render.VantaGlassRenderer;
import name.vantaclient.client.gui.compose.theme.VantaTheme;
import name.vantaclient.client.gui.compose.theme.VantaThemeFactory;
import name.vantaclient.client.gui.compose.components.VantaComponents;
import name.vantaclient.client.gui.framework.VantaUiNode;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
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

    /** Material-backed container factory used by screen composition. */
    public static VantaUiNode surface(Material material, int gap, int padding) {
        VantaComponents.Surface surface = switch (material) {
            case PANEL -> VantaComponents.Surface.PANEL;
            case ELEVATED, CARD -> VantaComponents.Surface.ELEVATED;
            case FIELD -> VantaComponents.Surface.FIELD;
            case POPOVER -> VantaComponents.Surface.POPOVER;
        };
        return VantaComponents.surface(surface, gap, padding);
    }

    public static VantaUiNode moduleCard(String title, String description, VantaUiNode.Icon icon,
                                          Supplier<String> status, int accent, int cardHeight, boolean compact, boolean configurable,
                                          Runnable configure, BooleanSupplier enabled,
                                          Consumer<Boolean> toggle) {
        return VantaComponents.moduleCard(title, description, icon, status, accent, cardHeight, compact,
                configurable, configure, enabled, toggle);
    }

    public static VantaUiNode sectionHeader(String eyebrow, String title, String detail) {
        return VantaComponents.sectionHeader(eyebrow, title, detail);
    }

    public static VantaUiNode panel(VantaComponents.Surface surface, int gap, int padding) {
        return VantaComponents.panel(surface, gap, padding);
    }

    public static VantaUiNode divider() {
        return VantaComponents.divider();
    }

    public static VantaUiNode badge(String label, VantaComponents.Tone tone) {
        return VantaComponents.badge(label, tone);
    }

    public static VantaUiNode actionButton(String label, VantaUiNode.Icon icon,
                                           VantaComponents.ButtonVariant variant, Runnable action) {
        return VantaComponents.actionButton(label, icon, variant, action);
    }

    public static VantaUiNode settingRow(String title, String description,
                                         VantaUiNode control, int controlWidth) {
        return VantaComponents.settingRow(title, description, control, controlWidth);
    }

    public static VantaUiNode emptyState(VantaUiNode.Icon icon, String title,
                                         String description, String actionLabel, Runnable action) {
        return VantaComponents.emptyState(icon, title, description, actionLabel, action);
    }

    public static VantaUiNode statusRow(String title, String detail, String status,
                                        VantaComponents.Tone tone) {
        return VantaComponents.statusRow(title, detail, status, tone);
    }

    public static VantaUiNode navigationItem(String label, VantaUiNode.Icon icon,
                                              boolean selected, Runnable action) {
        return VantaComponents.navigationItem(label, icon, selected, action);
    }

    public static VantaUiNode workspaceRow(String title, String detail,
                                            VantaUiNode.Icon icon, Runnable action) {
        return VantaComponents.workspaceRow(title, detail, icon, action);
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
