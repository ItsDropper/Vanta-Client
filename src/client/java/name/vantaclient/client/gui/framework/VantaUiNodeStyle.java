package name.vantaclient.client.gui.framework;

import name.vantaclient.client.gui.compose.VantaCompose;
import net.minecraft.client.gui.GuiGraphics;

/** Resolves and paints node surface styling, keeping visual policy out of the node model. */
final class VantaUiNodeStyle {
    private VantaUiNodeStyle() {}

    static void draw(VantaUiNode node, GuiGraphics graphics, VantaUiTheme theme) {
        int background = node.backgroundColor();
        int radius = node.cornerRadius() >= 0 ? node.cornerRadius()
                : background == VantaUiTheme.PANEL ? VantaUiTheme.RADIUS_PANEL
                : background == VantaUiTheme.PANEL_RAISED ? VantaUiTheme.RADIUS_LARGE
                : background == VantaUiTheme.TOOLTIP_SURFACE ? VantaUiTheme.RADIUS_MEDIUM
                : VantaUiTheme.RADIUS_MEDIUM;

        if (background != 0) {
            VantaCompose.Material material = materialFor(background);
            if (material == null) {
                VantaUiTheme.fillSquircle(graphics, node.x(), node.y(),
                        node.width(), node.height(), radius,
                        VantaUiTheme.applyPanelOpacity(background));
            } else {
                VantaCompose.drawSurface(graphics, theme.composeTheme(), material,
                        stateFor(node, background), node.x(), node.y(),
                        node.width(), node.height(), radius);
            }
        }

        int border = node.borderColor();
        if (border != 0) {
            VantaUiTheme.outlineSquircle(graphics, node.x(), node.y(),
                    node.width(), node.height(),
                    node.cornerRadius() >= 0 ? node.cornerRadius() : VantaUiTheme.RADIUS_MEDIUM,
                    border);
        }
    }

    private static VantaCompose.Material materialFor(int background) {
        return switch (background) {
            case VantaUiTheme.PANEL -> VantaCompose.Material.PANEL;
            case VantaUiTheme.PANEL_RAISED -> VantaCompose.Material.ELEVATED;
            case VantaUiTheme.SURFACE, VantaUiTheme.SURFACE_HOVER,
                    VantaUiTheme.SURFACE_SELECTED -> VantaCompose.Material.CARD;
            case VantaUiTheme.INPUT_SURFACE, VantaUiTheme.INPUT_FOCUS_SURFACE ->
                    VantaCompose.Material.FIELD;
            case VantaUiTheme.TOOLTIP_SURFACE -> VantaCompose.Material.POPOVER;
            default -> null;
        };
    }

    private static VantaCompose.State stateFor(VantaUiNode node, int background) {
        if (!node.isEnabled()) return VantaCompose.State.DISABLED;
        if (node.isSelected() || background == VantaUiTheme.SURFACE_SELECTED) {
            return VantaCompose.State.SELECTED;
        }
        if (node.isFocused() || background == VantaUiTheme.INPUT_FOCUS_SURFACE) {
            return VantaCompose.State.FOCUSED;
        }
        if (node.isHovered() || background == VantaUiTheme.SURFACE_HOVER) {
            return VantaCompose.State.HOVERED;
        }
        return VantaCompose.State.NORMAL;
    }
}
