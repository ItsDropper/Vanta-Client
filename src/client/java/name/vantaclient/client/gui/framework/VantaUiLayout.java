package name.vantaclient.client.gui.framework;

import net.minecraft.client.gui.Font;
import name.vantaclient.client.gui.compose.typography.VantaTypography;

import java.util.Arrays;

/** Owns intrinsic measurement and retained-tree placement, independent of rendering and input. */
final class VantaUiLayout {
    private VantaUiLayout() {}

    /** Shrinks a flow's primary-axis allocations proportionally when fixed content cannot fit. */
    static int[] fitPrimarySizes(int[] requested, int available) {
        int[] fitted = Arrays.copyOf(requested, requested.length);
        long total = 0L;
        int lastPositive = -1;
        for (int i = 0; i < fitted.length; i++) {
            fitted[i] = Math.max(0, fitted[i]);
            total += fitted[i];
            if (fitted[i] > 0) lastPositive = i;
        }
        int budget = Math.max(0, available);
        if (total <= budget || total == 0L) return fitted;
        float ratio = budget / (float) total;
        int assigned = 0;
        for (int i = 0; i < fitted.length; i++) {
            if (fitted[i] == 0) continue;
            int size = i == lastPositive ? budget - assigned
                    : Math.min(budget - assigned, Math.round(fitted[i] * ratio));
            fitted[i] = Math.max(0, size);
            assigned += fitted[i];
        }
        return fitted;
    }

    static int intrinsicWidth(VantaUiNode node, Font font) {
        if (node.preferredWidth >= 0) return node.constraints.constrainWidth(node.preferredWidth);
        int measured = switch (node.kind) {
            case TEXT -> VantaUiNode.textWidth(font, node.currentText(), node.textFont);
            case ICON, IMAGE -> 16;
            case CANVAS -> 16;
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
            case CANVAS -> 16;
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
        arrange(node, x, y, width, height, font, false);
    }

    private static void arrange(VantaUiNode node, int x, int y, int width, int height,
                                Font font, boolean boundedByParent) {
        node.x = x;
        node.y = y;
        node.width = boundedByParent ? Math.max(0, width) : node.constraints.constrainWidth(width);
        node.height = boundedByParent ? Math.max(0, height) : node.constraints.constrainHeight(height);

        if (node.kind == VantaUiNode.Kind.TEXT && node.textMaximumLines > 1) {
            String value = node.currentText();
            if (!value.equals(node.cachedTextLayoutValue) || node.width != node.cachedTextLayoutWidth) {
                node.cachedTextLayoutValue = value;
                node.cachedTextLayoutWidth = node.width;
                node.cachedTextLines = VantaTypography.wrapText(value, font, Math.max(1, node.width), node.textMaximumLines, node.textFont);
            }
            return;
        }

        if (node.kind != VantaUiNode.Kind.CONTAINER) return;
        int innerX = node.x + node.padding;
        int innerY = node.y + node.padding;
        int innerWidth = Math.max(0, node.width - node.padding * 2);
        int innerHeight = Math.max(0, node.height - node.padding * 2);

        if (node.direction == VantaUiNode.Direction.STACK) {
            // Stack containers do not participate in flow scrolling. Clear values left
            // by a previous layout if this node changed direction or its children changed.
            node.contentHeight = 0;
            node.scrollOffset = 0;
            for (VantaUiNode child : node.children) {
                if (!child.visible) continue;
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
                arrange(child, childX, childY, childWidth, childHeight, font, true);
            }
            return;
        }

        int visibleCount = 0;
        float totalFlex = 0.0f;
        int fixedPrimary = 0;
        for (VantaUiNode child : node.children) {
            if (!child.visible) continue;
            visibleCount++;
            if (child.flex > 0.0f) totalFlex += child.flex;
            else fixedPrimary += node.direction == VantaUiNode.Direction.COLUMN
                    ? intrinsicHeight(child, font) : intrinsicWidth(child, font);
        }
        if (visibleCount == 0) {
            node.contentHeight = 0;
            node.scrollOffset = 0;
            return;
        }

        int childGap = node.gap * (visibleCount - 1);

        int availablePrimary = Math.max(0,
                (node.direction == VantaUiNode.Direction.COLUMN ? innerHeight : innerWidth) - childGap - fixedPrimary);
        int[] primarySizes = node.layoutPrimarySizes(node.children.size());
        int measuredContent = childGap;
        for (int i = 0; i < node.children.size(); i++) {
            VantaUiNode child = node.children.get(i);
            if (!child.visible) continue;
            int primary = child.flex > 0.0f && totalFlex > 0.0f
                    ? Math.round(availablePrimary * child.flex / totalFlex)
                    : node.direction == VantaUiNode.Direction.COLUMN ? intrinsicHeight(child, font) : intrinsicWidth(child, font);
            primarySizes[i] = primary;
            measuredContent += primary;
        }

        int availableSize = node.direction == VantaUiNode.Direction.COLUMN ? innerHeight : innerWidth;
        // Scrollable columns intentionally keep their full content height. All other
        // flows compress children before placement so controls cannot overlap siblings.
        if (measuredContent > availableSize
                && !(node.direction == VantaUiNode.Direction.COLUMN && node.scrollable)) {
            int[] fitted = fitPrimarySizes(primarySizes, Math.max(0, availableSize - childGap));
            measuredContent = childGap;
            for (int i = 0; i < primarySizes.length; i++) {
                primarySizes[i] = fitted[i];
                measuredContent += fitted[i];
            }
        }
        int freeSpace = Math.max(0, availableSize - measuredContent);
        int leadingSpace = 0;
        int distributedGap = 0;
        if (totalFlex == 0.0f && !(node.direction == VantaUiNode.Direction.COLUMN && node.scrollable)) {
            switch (node.justification) {
                case CENTER -> leadingSpace = freeSpace / 2;
                case END -> leadingSpace = freeSpace;
                case SPACE_BETWEEN -> {
                    if (visibleCount > 1) distributedGap = freeSpace / (visibleCount - 1);
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
        for (int i = 0; i < node.children.size(); i++) {
            VantaUiNode child = node.children.get(i);
            if (!child.visible) continue;
            int primary = primarySizes[i];

            if (node.direction == VantaUiNode.Direction.COLUMN) {
                VantaUiNode.Alignment effectiveAlignment = effectiveAlignment(node, child);
                int requestedWidth = child.preferredWidth >= 0
                        ? child.preferredWidth
                        : effectiveAlignment == VantaUiNode.Alignment.STRETCH ? innerWidth
                        : intrinsicWidth(child, font);
                int childWidth = child.constraints.constrainWidth(requestedWidth, innerWidth);
                int childX = alignedOffset(innerX, innerWidth, childWidth, effectiveAlignment);
                int childY = cursor;
                int childHeight = node.scrollable
                        ? child.constraints.constrainHeight(primary)
                        : child.constraints.constrainHeight(primary,
                                Math.max(0, innerY + innerHeight - childY));
                if (node.scrollable && (childY + childHeight <= innerY || childY >= innerY + innerHeight)) {
                    // Keep the direct child's bounds ordered for binary-search culling,
                    // but do not recursively measure/arrange its descendants until it enters the viewport.
                    child.x = childX;
                    child.y = childY;
                    child.width = childWidth;
                    child.height = childHeight;
                } else {
                    arrange(child, childX, childY, childWidth, childHeight, font, true);
                }
                cursor += childHeight + node.gap + distributedGap;
            } else {
                VantaUiNode.Alignment effectiveAlignment = effectiveAlignment(node, child);
                int requestedHeight = child.preferredHeight >= 0 ? child.preferredHeight
                        : effectiveAlignment == VantaUiNode.Alignment.STRETCH ? innerHeight
                        : intrinsicHeight(child, font);
                int childHeight = child.constraints.constrainHeight(requestedHeight, innerHeight);
                int childY = alignedOffset(innerY, innerHeight, childHeight, effectiveAlignment);
                int childWidth = child.constraints.constrainWidth(primary,
                        Math.max(0, innerX + innerWidth - cursor));
                arrange(child, cursor, childY, childWidth, childHeight, font, true);
                cursor += childWidth + node.gap + distributedGap;
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
