package name.vantaclient.client.gui.framework;

import net.minecraft.client.gui.Font;
import java.util.ArrayList;
import java.util.List;

/** Owns intrinsic measurement and retained-tree placement, independent of rendering and input. */
final class VantaUiLayout {
    private VantaUiLayout() {}

    static int intrinsicWidth(VantaUiNode node, Font font) {
        if (node.preferredWidth >= 0) return node.constraints.constrainWidth(node.preferredWidth);
        int measured = switch (node.kind) {
            case TEXT -> VantaUiNode.textWidth(font, node.currentText(), node.textFont);
            case ICON, IMAGE -> 16;
            case SPACER -> 1;
            case BUTTON, TOGGLE, SLIDER, DROPDOWN, TEXT_FIELD, PROGRESS -> 160;
            case CONTAINER -> {
                int size = 0;
                int visibleChildren = 0;
                for (VantaUiNode child : node.children) {
                    if (!child.visible) continue;
                    int childWidth = intrinsicWidth(child, font);
                    size = node.direction == VantaUiNode.Direction.ROW
                            ? size + childWidth
                            : Math.max(size, childWidth);
                    visibleChildren++;
                }
                if (node.direction == VantaUiNode.Direction.ROW && visibleChildren > 1) {
                    size += node.gap * (visibleChildren - 1);
                }
                yield size + node.padding * 2;
            }
        };
        return node.constraints.constrainWidth(measured);
    }

    static int intrinsicHeight(VantaUiNode node, Font font) {
        if (node.preferredHeight >= 0) return node.constraints.constrainHeight(node.preferredHeight);
        int measured = switch (node.kind) {
            case TEXT -> node.textMaximumLines > 1 ? node.textMaximumLines * 14 : 14;
            case ICON, IMAGE -> 16;
            case SPACER -> 0;
            case BUTTON, TOGGLE, DROPDOWN, TEXT_FIELD -> VantaUiTheme.CONTROL_HEIGHT;
            case PROGRESS -> 30;
            case SLIDER -> VantaUiTheme.SLIDER_HEIGHT;
            case CONTAINER -> {
                int size = 0;
                int visibleChildren = 0;
                for (VantaUiNode child : node.children) {
                    if (!child.visible) continue;
                    int childHeight = intrinsicHeight(child, font);
                    size = node.direction == VantaUiNode.Direction.COLUMN
                            ? size + childHeight
                            : Math.max(size, childHeight);
                    visibleChildren++;
                }
                if (node.direction == VantaUiNode.Direction.COLUMN && visibleChildren > 1) {
                    size += node.gap * (visibleChildren - 1);
                }
                yield size + node.padding * 2;
            }
        };
        return node.constraints.constrainHeight(measured);
    }

    static void arrange(VantaUiNode node, int x, int y, int width, int height, Font font) {
        this.node.x = node.x;
        this.node.y = node.y;
        this.node.width = node.constraints.constrainWidth(node.width);
        this.node.height = node.constraints.constrainHeight(node.height);

        if (node.kind == Kind.TEXT && node.textMaximumLines > 1) {
            String value = node.currentText();
            if (!value.equals(node.cachedTextLayoutValue) || this.node.width != node.cachedTextLayoutWidth) {
                node.cachedTextLayoutValue = value;
                node.cachedTextLayoutWidth = this.node.width;
                node.cachedTextLines = VantaTypography.wrapText(value, font, Math.max(1, this.node.width), node.textMaximumLines, node.textFont);
            }
            return;
        }

        if (node.kind != Kind.CONTAINER) return;
        List<VantaUiNode> layoutChildren = new ArrayList<>();
        for (VantaUiNode child : node.children) {
            if (child.visible) layoutChildren.add(child);
        }
        if (layoutChildren.isEmpty()) {
            node.contentHeight = 0;
            node.scrollOffset = 0;
            return;
        }

        int innerX = this.node.x + node.padding;
        int innerY = this.node.y + node.padding;
        int innerWidth = Math.max(0, this.node.width - node.padding * 2);
        int innerHeight = Math.max(0, this.node.height - node.padding * 2);

        if (node.direction == VantaUiNode.Direction.STACK) {
            for (VantaUiNode child : layoutChildren) {
                VantaUiNode.Alignment effectiveAlignment = effectiveAlignment(node, child);
                int requestedWidth = child.preferredWidth >= 0
                        ? child.preferredWidth
                        : effectiveAlignment == VantaUiNode.Alignment.STRETCH ? innerWidth
                        : intrinsicWidth(child, font);
                int childWidth = child.constraints.constrainWidth(requestedWidth, innerWidth);
                int requestedHeight = child.preferredHeight >= 0 ? child.preferredHeight
                        : effectiveAlignment == VantaUiNode.Alignment.STRETCH ? innerHeight
                        : intrinsicHeight(child, font);
                int childHeight = child.constraints.constrainHeight(requestedHeight, innerHeight);
                int childX = alignedOffset(innerX, innerWidth, childWidth, effectiveAlignment);
                int childY = alignedOffset(innerY, innerHeight, childHeight, effectiveAlignment);
                arrange(child, childX, childY, childWidth, childHeight, font);
            }
            return;
        }

        int childGap = node.gap * Math.max(0, layoutChildren.size() - 1);
        float totalFlex = 0.0f;
        int fixedPrimary = 0;
        for (VantaUiNode child : layoutChildren) {
            if (child.flex > 0.0f) totalFlex += child.flex;
            else fixedPrimary += node.direction == VantaUiNode.Direction.COLUMN
                    ? intrinsicHeight(child, font) : intrinsicWidth(child, font);
        }

        int availablePrimary = Math.max(0,
                (node.direction == VantaUiNode.Direction.COLUMN ? innerHeight : innerWidth) - childGap - fixedPrimary);
        int[] primarySizes = new int[layoutChildren.size()];
        int measuredContent = childGap;
        for (int i = 0; i < layoutChildren.size(); i++) {
            VantaUiNode child = layoutChildren.get(i);
            int primary = child.flex > 0.0f && totalFlex > 0.0f
                    ? Math.round(availablePrimary * child.flex / totalFlex)
                    : node.direction == VantaUiNode.Direction.COLUMN ? intrinsicHeight(child, font) : intrinsicWidth(child, font);
            primarySizes[i] = primary;
            measuredContent += primary;
        }

        int availableSize = node.direction == VantaUiNode.Direction.COLUMN ? innerHeight : innerWidth;
        int freeSpace = Math.max(0, availableSize - measuredContent);
        int leadingSpace = 0;
        int distributedGap = 0;
        if (totalFlex == 0.0f && !(node.direction == VantaUiNode.Direction.COLUMN && node.scrollable)) {
            switch (justification) {
                case CENTER -> leadingSpace = freeSpace / 2;
                case END -> leadingSpace = freeSpace;
                case SPACE_BETWEEN -> {
                    if (layoutChildren.size() > 1) distributedGap = freeSpace / (layoutChildren.size() - 1);
                }
                case START -> { }
            }
        }

        if (node.direction == VantaUiNode.Direction.COLUMN) {
            node.contentHeight = measuredContent;
            int maxScroll = Math.max(0, node.contentHeight - innerHeight);
            node.scrollOffset = node.scrollable ? VantaUiNode.clamp(node.scrollOffset, 0, maxScroll) : 0;
        } else {
            node.contentHeight = 0;
        }

        int cursor = node.direction == VantaUiNode.Direction.COLUMN
                ? innerY + leadingSpace - (node.scrollable ? node.scrollOffset : 0)
                : innerX + leadingSpace;
        for (int i = 0; i < layoutChildren.size(); i++) {
            VantaUiNode child = layoutChildren.get(i);
            int primary = primarySizes[i];

            if (node.direction == VantaUiNode.Direction.COLUMN) {
                VantaUiNode.Alignment effectiveAlignment = effectiveAlignment(node, child);
                int requestedWidth = child.preferredWidth >= 0
                        ? child.preferredWidth
                        : effectiveAlignment == VantaUiNode.Alignment.STRETCH ? innerWidth
                        : intrinsicWidth(child, font);
                int childWidth = child.constraints.constrainWidth(requestedWidth, innerWidth);
                int childX = alignedOffset(innerX, innerWidth, childWidth, effectiveAlignment);
                arrange(child, childX, cursor, childWidth, primary, font);
                cursor += primary + node.gap + distributedGap;
            } else {
                VantaUiNode.Alignment effectiveAlignment = effectiveAlignment(node, child);
                int requestedHeight = child.preferredHeight >= 0 ? child.preferredHeight
                        : effectiveAlignment == VantaUiNode.Alignment.STRETCH ? innerHeight
                        : intrinsicHeight(child, font);
                int childHeight = child.constraints.constrainHeight(requestedHeight, innerHeight);
                int childY = alignedOffset(innerY, innerHeight, childHeight, effectiveAlignment);
                arrange(child, cursor, childY, primary, childHeight, font);
                cursor += primary + node.gap + distributedGap;
            }
        }
    }

    private static VantaUiNode.Alignment effectiveAlignment(VantaUiNode node, VantaUiNode child) {
        return child.alignmentExplicit ? child.alignment : node.childAlignment;
    }

    private static int alignedOffset(int start, int available, int size, VantaUiNode.Alignment alignment) {
        return switch (alignment) {
            case CENTER -> start + (available - size) / 2;
            case END -> start + available - size;
            case START, STRETCH -> start;
        };
    }


}
