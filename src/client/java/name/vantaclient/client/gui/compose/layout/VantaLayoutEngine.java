package name.vantaclient.client.gui.compose.layout;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Independent, renderer-agnostic layout engine for VantaCompose.
 *
 * The engine owns measurement and placement. Components describe their intrinsic
 * size and layout policy; renderers consume immutable placed results. No Minecraft
 * rendering types or legacy VantaUiNode references cross this boundary.
 */
public final class VantaLayoutEngine {
    public enum Axis { ROW, COLUMN, OVERLAY }
    public enum Alignment { START, CENTER, END, STRETCH }

    public record Constraints(int minWidth, int minHeight, int maxWidth, int maxHeight) {
        public static final Constraints UNBOUNDED =
                new Constraints(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);

        public Constraints {
            minWidth = Math.max(0, minWidth);
            minHeight = Math.max(0, minHeight);
            maxWidth = Math.max(minWidth, maxWidth);
            maxHeight = Math.max(minHeight, maxHeight);
        }

        public Constraints inset(int horizontal, int vertical) {
            return new Constraints(Math.max(0, minWidth - horizontal),
                    Math.max(0, minHeight - vertical),
                    Math.max(0, maxWidth - horizontal),
                    Math.max(0, maxHeight - vertical));
        }

        int width(int value) { return clamp(value, minWidth, maxWidth); }
        int height(int value) { return clamp(value, minHeight, maxHeight); }
    }

    /** Immutable component layout description. Flex is meaningful on row/column children. */
    public record Element(
            String key, Axis axis, List<Element> children,
            int intrinsicWidth, int intrinsicHeight,
            int padding, int gap, float flex,
            Alignment crossAlignment, Constraints constraints
    ) {
        public Element {
            key = Objects.requireNonNull(key, "key");
            axis = Objects.requireNonNull(axis, "axis");
            children = List.copyOf(children == null ? List.of() : children);
            crossAlignment = Objects.requireNonNull(crossAlignment, "crossAlignment");
            constraints = Objects.requireNonNull(constraints, "constraints");
            if (key.isBlank()) throw new IllegalArgumentException("Layout keys cannot be blank.");
            if (intrinsicWidth < 0 || intrinsicHeight < 0 || padding < 0 || gap < 0
                    || !Float.isFinite(flex) || flex < 0) {
                throw new IllegalArgumentException("Layout dimensions and flex must be non-negative.");
            }
            if (axis != Axis.OVERLAY && children.size() > 1 && gap < 0) {
                throw new IllegalArgumentException("Container gap must be non-negative.");
            }
        }

        public static Element leaf(String key, int width, int height) {
            return new Element(key, Axis.OVERLAY, List.of(), width, height,
                    0, 0, 0, Alignment.START, Constraints.UNBOUNDED);
        }

        public static Element container(String key, Axis axis, List<Element> children,
                                        int padding, int gap, Alignment alignment) {
            if (axis == Axis.OVERLAY && gap != 0) {
                throw new IllegalArgumentException("Overlay containers do not have a gap.");
            }
            return new Element(key, axis, children, 0, 0, padding, gap,
                    0, alignment, Constraints.UNBOUNDED);
        }

        public Element withFlex(float weight) {
            return new Element(key, axis, children, intrinsicWidth, intrinsicHeight,
                    padding, gap, weight, crossAlignment, constraints);
        }

        public Element withConstraints(Constraints value) {
            return new Element(key, axis, children, intrinsicWidth, intrinsicHeight,
                    padding, gap, flex, crossAlignment, value);
        }
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 0 || height < 0) throw new IllegalArgumentException("Negative layout bounds.");
        }
    }

    public record Placement(Element element, Rect bounds, int depth) {}

    public record Result(int measuredWidth, int measuredHeight, List<Placement> placements) {
        public Result { placements = List.copyOf(placements); }

        public Rect bounds(String key) {
            for (Placement placement : placements) {
                if (placement.element().key().equals(key)) return placement.bounds();
            }
            throw new IllegalArgumentException("No layout element with key: " + key);
        }
    }

    private record Size(int width, int height) {}
    private final Map<Element, Size> measurements = new IdentityHashMap<>();
    private final List<Placement> placements = new ArrayList<>();

    public Result layout(Element root, Rect viewport) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(viewport, "viewport");
        measurements.clear();
        placements.clear();
        Size measured = measure(root, new Constraints(0, 0, viewport.width(), viewport.height()));
        place(root, new Rect(viewport.x(), viewport.y(),
                viewport.width(), viewport.height()), 0);
        return new Result(measured.width(), measured.height(), placements);
    }

    private Size measure(Element element, Constraints available) {
        Size cached = measurements.get(element);
        if (cached != null) return cached;
        int contentWidth = element.intrinsicWidth();
        int contentHeight = element.intrinsicHeight();
        if (!element.children().isEmpty()) {
            int main = 0;
            int cross = 0;
            for (Element child : element.children()) {
                Size childSize = measure(child, available.inset(element.padding() * 2,
                        element.padding() * 2));
                if (element.axis() == Axis.ROW) {
                    main = saturatingAdd(main, childSize.width());
                    cross = Math.max(cross, childSize.height());
                } else if (element.axis() == Axis.COLUMN) {
                    main = saturatingAdd(main, childSize.height());
                    cross = Math.max(cross, childSize.width());
                } else {
                    main = Math.max(main, childSize.width());
                    cross = Math.max(cross, childSize.height());
                }
            }
            if (element.axis() == Axis.ROW) {
                contentWidth = saturatingAdd(main, element.gap() * Math.max(0, element.children().size() - 1));
                contentHeight = cross;
            } else if (element.axis() == Axis.COLUMN) {
                contentWidth = cross;
                contentHeight = saturatingAdd(main, element.gap() * Math.max(0, element.children().size() - 1));
            } else {
                contentWidth = main;
                contentHeight = cross;
            }
        }
        int width = element.constraints().width(saturatingAdd(contentWidth, element.padding() * 2));
        int height = element.constraints().height(saturatingAdd(contentHeight, element.padding() * 2));
        Size result = new Size(available.width(width), available.height(height));
        measurements.put(element, result);
        return result;
    }

    private void place(Element element, Rect bounds, int depth) {
        placements.add(new Placement(element, bounds, depth));
        if (element.children().isEmpty()) return;
        int inset = element.padding();
        int left = bounds.x() + inset;
        int top = bounds.y() + inset;
        int innerWidth = Math.max(0, bounds.width() - inset * 2);
        int innerHeight = Math.max(0, bounds.height() - inset * 2);
        List<Element> children = element.children();
        if (element.axis() == Axis.OVERLAY) {
            for (Element child : children) {
                Size preferred = measurements.get(child);
                int width = element.crossAlignment() == Alignment.STRETCH
                        ? innerWidth : Math.min(innerWidth, preferred.width());
                int height = element.crossAlignment() == Alignment.STRETCH
                        ? innerHeight : Math.min(innerHeight, preferred.height());
                place(child, new Rect(left + offset(innerWidth, width, element.crossAlignment()),
                        top + offset(innerHeight, height, element.crossAlignment()), width, height), depth + 1);
            }
            return;
        }
        boolean row = element.axis() == Axis.ROW;
        int mainAvailable = row ? innerWidth : innerHeight;
        int crossAvailable = row ? innerHeight : innerWidth;
        int gaps = element.gap() * Math.max(0, children.size() - 1);
        float totalFlex = 0;
        int fixed = gaps;
        for (Element child : children) {
            Size size = measurements.get(child);
            fixed = saturatingAdd(fixed, row ? size.width() : size.height());
            totalFlex += child.flex();
        }
        int remaining = Math.max(0, mainAvailable - fixed);
        int cursor = row ? left : top;
        float allocatedFlex = 0;
        for (int i = 0; i < children.size(); i++) {
            Element child = children.get(i);
            Size preferred = measurements.get(child);
            int mainSize = row ? preferred.width() : preferred.height();
            if (child.flex() > 0 && totalFlex > 0) {
                allocatedFlex += child.flex();
                mainSize = i == children.size() - 1 || allocatedFlex >= totalFlex
                        ? remaining : Math.round(remaining * child.flex() / totalFlex);
                remaining = Math.max(0, remaining - mainSize);
                totalFlex -= child.flex();
            }
            int crossSize = row ? preferred.height() : preferred.width();
            Alignment alignment = child.crossAlignment() == Alignment.STRETCH
                    ? element.crossAlignment() : child.crossAlignment();
            if (alignment == Alignment.STRETCH) crossSize = crossAvailable;
            int crossOffset = offset(crossAvailable, crossSize, alignment);
            Rect childBounds = row
                    ? new Rect(cursor, top + crossOffset, Math.max(0, mainSize), Math.max(0, crossSize))
                    : new Rect(left + crossOffset, cursor, Math.max(0, crossSize), Math.max(0, mainSize));
            place(child, childBounds, depth + 1);
            cursor += mainSize + element.gap();
        }
    }

    private static int offset(int available, int size, Alignment alignment) {
        return switch (alignment) {
            case CENTER -> Math.max(0, (available - size) / 2);
            case END -> Math.max(0, available - size);
            default -> 0;
        };
    }

    private static int saturatingAdd(int left, int right) {
        long sum = (long) left + right;
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
