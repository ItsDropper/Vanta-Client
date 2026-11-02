package name.vantaclient.client.gui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Retained UI node used by Vanta's small declarative component tree.
 *
 * This is intentionally not a recreation of Compose. It provides the layout,
 * clipping and interaction primitives currently needed by Vanta screens.
 */
public final class VantaUiNode {
    public enum Kind { CONTAINER, TEXT, SPACER, BUTTON, TOGGLE, SLIDER, DROPDOWN }
    public enum Direction { ROW, COLUMN, STACK }

    private final Kind kind;
    private final Direction direction;
    private final List<VantaUiNode> children = new ArrayList<>();

    private String label = "";
    private Supplier<String> textSupplier;
    private String tooltip = "";
    private int textColor = VantaUiTheme.TEXT_PRIMARY;
    private int background;
    private int preferredWidth = -1;
    private int preferredHeight = -1;
    private int minWidth;
    private int minHeight;
    private int maxWidth = Integer.MAX_VALUE;
    private int maxHeight = Integer.MAX_VALUE;
    private int padding;
    private int gap;
    private float flex;
    private boolean scrollable;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean hovered;
    private boolean focused;
    private int scrollOffset;
    private int contentHeight;

    private int x;
    private int y;
    private int width;
    private int height;
    private float hoverAmount;
    private float animatedToggle;

    private Runnable action;
    private BooleanSupplier toggleValue;
    private Consumer<Boolean> toggleSetter;
    private DoubleSupplier sliderValue;
    private DoubleConsumer sliderSetter;
    private double sliderMinimum;
    private double sliderMaximum = 1.0;
    private double sliderStep;
    private Supplier<String> dropdownValue;
    private Consumer<String> dropdownSetter;
    private List<String> dropdownOptions = List.of();

    private VantaUiNode(Kind kind, Direction direction) {
        this.kind = kind;
        this.direction = direction;
    }

    public static VantaUiNode column(int gap, int padding) {
        return new VantaUiNode(Kind.CONTAINER, Direction.COLUMN)
                .gap(gap).padding(padding);
    }

    public static VantaUiNode row(int gap, int padding) {
        return new VantaUiNode(Kind.CONTAINER, Direction.ROW)
                .gap(gap).padding(padding);
    }

    public static VantaUiNode stack(int padding) {
        return new VantaUiNode(Kind.CONTAINER, Direction.STACK)
                .padding(padding);
    }

    public static VantaUiNode text(String value) {
        return text(value, VantaUiTheme.TEXT_PRIMARY);
    }

    public static VantaUiNode text(String value, int color) {
        VantaUiNode node = new VantaUiNode(Kind.TEXT, null);
        node.label = value == null ? "" : value;
        node.textColor = color;
        return node;
    }

    public static VantaUiNode dynamicText(Supplier<String> value, int color) {
        VantaUiNode node = new VantaUiNode(Kind.TEXT, null);
        node.textSupplier = value;
        node.textColor = color;
        return node;
    }

    public static VantaUiNode spacer(int height) {
        VantaUiNode node = new VantaUiNode(Kind.SPACER, null);
        node.preferredHeight = Math.max(0, height);
        return node;
    }

    public static VantaUiNode button(String label, Runnable action) {
        VantaUiNode node = new VantaUiNode(Kind.BUTTON, null);
        node.label = label == null ? "" : label;
        node.action = action;
        node.preferredHeight = VantaUiTheme.CONTROL_HEIGHT;
        return node;
    }

    public static VantaUiNode toggle(String label, BooleanSupplier value, Consumer<Boolean> setter) {
        VantaUiNode node = new VantaUiNode(Kind.TOGGLE, null);
        node.label = label == null ? "" : label;
        node.toggleValue = value;
        node.toggleSetter = setter;
        node.preferredHeight = VantaUiTheme.CONTROL_HEIGHT;
        node.animatedToggle = value != null && value.getAsBoolean() ? 1.0f : 0.0f;
        return node;
    }

    public static VantaUiNode slider(String label, double minimum, double maximum,
                                     double step, DoubleSupplier value, DoubleConsumer setter) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || maximum < minimum) {
            throw new IllegalArgumentException("Slider bounds must be finite and ordered.");
        }
        VantaUiNode node = new VantaUiNode(Kind.SLIDER, null);
        node.label = label == null ? "" : label;
        node.sliderMinimum = minimum;
        node.sliderMaximum = maximum;
        node.sliderStep = Math.max(0.0, step);
        node.sliderValue = value;
        node.sliderSetter = setter;
        node.preferredHeight = VantaUiTheme.SLIDER_HEIGHT;
        return node;
    }

    public static VantaUiNode dropdown(String label, List<String> options,
                                       Supplier<String> value, Consumer<String> setter) {
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("Dropdown requires at least one option.");
        }
        VantaUiNode node = new VantaUiNode(Kind.DROPDOWN, null);
        node.label = label == null ? "" : label;
        node.dropdownOptions = List.copyOf(options);
        node.dropdownValue = value;
        node.dropdownSetter = setter;
        node.preferredHeight = VantaUiTheme.CONTROL_HEIGHT;
        return node;
    }

    public VantaUiNode add(VantaUiNode child) {
        if (kind != Kind.CONTAINER) {
            throw new IllegalStateException("Only containers can own child nodes.");
        }
        if (child == null) throw new IllegalArgumentException("Child cannot be null.");
        children.add(child);
        return this;
    }

    public VantaUiNode background(int color) { background = color; return this; }
    public VantaUiNode padding(int value) { padding = Math.max(0, value); return this; }
    public VantaUiNode gap(int value) { gap = Math.max(0, value); return this; }
    public VantaUiNode width(int value) { preferredWidth = Math.max(0, value); return this; }
    public VantaUiNode height(int value) { preferredHeight = Math.max(0, value); return this; }
    public VantaUiNode size(int width, int height) {
        this.preferredWidth = Math.max(0, width);
        this.preferredHeight = Math.max(0, height);
        return this;
    }
    public VantaUiNode minSize(int width, int height) {
        minWidth = Math.max(0, width);
        minHeight = Math.max(0, height);
        return this;
    }
    public VantaUiNode maxSize(int width, int height) {
        maxWidth = Math.max(0, width);
        maxHeight = Math.max(0, height);
        return this;
    }
    public VantaUiNode flex(float value) { flex = Math.max(0.0f, value); return this; }
    public VantaUiNode scrollable() { scrollable = true; return this; }
    public VantaUiNode tooltip(String value) { tooltip = value == null ? "" : value; return this; }
    public VantaUiNode textColor(int color) { textColor = color; return this; }
    public VantaUiNode enabled(boolean value) { enabled = value; return this; }
    public VantaUiNode visible(boolean value) { visible = value; return this; }

    public Kind kind() { return kind; }
    public String tooltip() { return tooltip; }
    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
    public boolean isInteractive() {
        return kind == Kind.BUTTON || kind == Kind.TOGGLE
                || kind == Kind.SLIDER || kind == Kind.DROPDOWN;
    }
    public boolean isVisible() { return visible; }

    int intrinsicWidth(Font font) {
        if (preferredWidth >= 0) return clamp(preferredWidth, minWidth, maxWidth);
        return switch (kind) {
            case TEXT -> font.width(currentText());
            case SPACER -> 1;
            case BUTTON, TOGGLE, SLIDER, DROPDOWN -> 160;
            case CONTAINER -> {
                int measured = 0;
                for (VantaUiNode child : children) {
                    int childWidth = child.intrinsicWidth(font);
                    measured = direction == Direction.ROW
                            ? measured + childWidth
                            : Math.max(measured, childWidth);
                }
                if (direction == Direction.ROW && children.size() > 1) {
                    measured += gap * (children.size() - 1);
                }
                yield measured + padding * 2;
            }
        };
    }

    int intrinsicHeight(Font font) {
        if (preferredHeight >= 0) return clamp(preferredHeight, minHeight, maxHeight);
        return switch (kind) {
            case TEXT -> 14;
            case SPACER -> 0;
            case BUTTON, TOGGLE, DROPDOWN -> VantaUiTheme.CONTROL_HEIGHT;
            case SLIDER -> VantaUiTheme.SLIDER_HEIGHT;
            case CONTAINER -> {
                int measured = 0;
                for (VantaUiNode child : children) {
                    int childHeight = child.intrinsicHeight(font);
                    measured = direction == Direction.COLUMN
                            ? measured + childHeight
                            : Math.max(measured, childHeight);
                }
                if (direction == Direction.COLUMN && children.size() > 1) {
                    measured += gap * (children.size() - 1);
                }
                yield measured + padding * 2;
            }
        };
    }

    void arrange(int x, int y, int width, int height, Font font) {
        this.x = x;
        this.y = y;
        this.width = clamp(width, minWidth, maxWidth);
        this.height = clamp(height, minHeight, maxHeight);

        if (kind != Kind.CONTAINER || children.isEmpty()) return;

        int innerX = this.x + padding;
        int innerY = this.y + padding;
        int innerWidth = Math.max(0, this.width - padding * 2);
        int innerHeight = Math.max(0, this.height - padding * 2);

        if (direction == Direction.STACK) {
            for (VantaUiNode child : children) {
                int childWidth = child.preferredWidth >= 0
                        ? Math.min(innerWidth, child.preferredWidth) : innerWidth;
                int childHeight = child.preferredHeight >= 0
                        ? Math.min(innerHeight, child.preferredHeight) : innerHeight;
                int childX = innerX + (innerWidth - childWidth) / 2;
                int childY = innerY + (innerHeight - childHeight) / 2;
                child.arrange(childX, childY, childWidth, childHeight, font);
            }
            return;
        }

        int childGap = gap * Math.max(0, children.size() - 1);
        float totalFlex = 0.0f;
        int fixedPrimary = 0;
        for (VantaUiNode child : children) {
            if (child.flex > 0.0f) totalFlex += child.flex;
            else fixedPrimary += direction == Direction.COLUMN
                    ? child.intrinsicHeight(font) : child.intrinsicWidth(font);
        }

        int availablePrimary = Math.max(0,
                (direction == Direction.COLUMN ? innerHeight : innerWidth) - childGap - fixedPrimary);
        int cursor = direction == Direction.COLUMN ? innerY - (scrollable ? scrollOffset : 0) : innerX;
        int measuredContent = childGap;

        for (VantaUiNode child : children) {
            int primary;
            if (child.flex > 0.0f && totalFlex > 0.0f) {
                primary = Math.round(availablePrimary * child.flex / totalFlex);
            } else {
                primary = direction == Direction.COLUMN
                        ? child.intrinsicHeight(font) : child.intrinsicWidth(font);
            }

            if (direction == Direction.COLUMN) {
                int childWidth = child.preferredWidth >= 0
                        ? Math.min(innerWidth, child.preferredWidth) : innerWidth;
                child.arrange(innerX, cursor, childWidth, primary, font);
                cursor += primary + gap;
                measuredContent += primary;
            } else {
                int childHeight = child.preferredHeight >= 0
                        ? Math.min(innerHeight, child.preferredHeight) : innerHeight;
                child.arrange(cursor, innerY, primary, childHeight, font);
                cursor += primary + gap;
                measuredContent += primary;
            }
        }

        if (direction == Direction.COLUMN) {
            contentHeight = measuredContent;
            int maxScroll = Math.max(0, contentHeight - innerHeight);
            scrollOffset = clamp(scrollOffset, 0, maxScroll);
        } else {
            contentHeight = 0;
        }
    }

    void render(GuiGraphics graphics, Font font, VantaUiTheme theme,
                int mouseX, int mouseY) {
        if (!visible || width <= 0 || height <= 0) return;

        if (background != 0) {
            graphics.fill(x, y, x + width, y + height, background);
            graphics.fill(x, y, x + width, y + 1, VantaUiTheme.withAlpha(VantaUiTheme.WHITE, 12));
        }

        if (kind == Kind.CONTAINER) {
            if (scrollable) {
                graphics.enableScissor(x + padding, y + padding,
                        x + width - padding, y + height - padding);
            }
            for (VantaUiNode child : children) {
                child.render(graphics, font, theme, mouseX, mouseY);
            }
            if (scrollable) {
                graphics.disableScissor();
                renderScrollbar(graphics);
            }
            return;
        }

        boolean over = contains(mouseX, mouseY);
        int accent = theme.accent();
        switch (kind) {
            case TEXT -> graphics.drawString(font, currentText(), x,
                    y + Math.max(0, (height - 9) / 2), textColor);
            case SPACER, CONTAINER -> { }
            case BUTTON -> {
                int surface = mixColor(VantaUiTheme.SURFACE, VantaUiTheme.SURFACE_HOVER, hoverAmount);
                graphics.fill(x, y, x + width, y + height, surface);
                graphics.fill(x, y, x + width, y + 1,
                        focused ? accent : VantaUiTheme.BORDER);
                if (focused) graphics.fill(x, y + 1, x + 2, y + height - 1, accent);
                graphics.drawString(font, label,
                        x + Math.max(8, (width - font.width(label)) / 2),
                        y + (height - 9) / 2, enabled ? VantaUiTheme.TEXT_PRIMARY : VantaUiTheme.TEXT_MUTED);
            }
            case TOGGLE -> {
                graphics.drawString(font, label, x, y + (height - 9) / 2,
                        enabled ? VantaUiTheme.TEXT_PRIMARY : VantaUiTheme.TEXT_MUTED);
                boolean value = toggleValue != null && toggleValue.getAsBoolean();
                int trackColor = value ? accent : VantaUiTheme.TRACK;
                int trackX = x + width - 42;
                int trackY = y + (height - 18) / 2;
                graphics.fill(trackX, trackY, trackX + 38, trackY + 18, trackColor);
                int knobX = trackX + 3 + Math.round(19.0f * animatedToggle);
                graphics.fill(knobX, trackY + 3, knobX + 13, trackY + 15, VantaUiTheme.WHITE);
                if (focused) graphics.fill(trackX - 2, trackY - 2, trackX + 40, trackY + 20, VantaUiTheme.withAlpha(accent, 70));
            }
            case SLIDER -> {
                double value = sliderValue == null ? sliderMinimum : sliderValue.getAsDouble();
                double range = sliderMaximum - sliderMinimum;
                double normalized = range <= 0.0 ? 0.0 : clamp((value - sliderMinimum) / range, 0.0, 1.0);
                graphics.drawString(font, label, x, y + 2, VantaUiTheme.TEXT_PRIMARY);
                String valueText = formatValue(value);
                graphics.drawString(font, valueText, x + width - font.width(valueText), y + 2,
                        VantaUiTheme.TEXT_SECONDARY);
                int trackY = y + 28;
                int knobX = x + (int) Math.round((width - 8) * normalized);
                graphics.fill(x, trackY, x + width, trackY + 4, VantaUiTheme.TRACK);
                graphics.fill(x, trackY, knobX + 4, trackY + 4, accent);
                graphics.fill(knobX, trackY - 3, knobX + 8, trackY + 11,
                        focused || over ? VantaUiTheme.WHITE : 0xFFD5DFEC);
                if (focused) graphics.fill(x, y + height - 1, x + width, y + height, accent);
            }
            case DROPDOWN -> {
                graphics.drawString(font, label, x, y + (height - 9) / 2,
                        VantaUiTheme.TEXT_PRIMARY);
                int boxWidth = Math.min(150, Math.max(92, width / 2));
                int boxX = x + width - boxWidth;
                graphics.fill(boxX, y + 2, x + width, y + height - 2,
                        mixColor(VantaUiTheme.SURFACE, VantaUiTheme.SURFACE_HOVER, hoverAmount));
                String selected = dropdownValue == null ? "" : dropdownValue.get();
                String shown = font.plainSubstrByWidth(selected == null ? "" : selected, boxWidth - 24);
                graphics.drawString(font, shown, boxX + 8, y + (height - 9) / 2,
                        VantaUiTheme.TEXT_PRIMARY);
                graphics.drawString(font, "v", x + width - 15, y + (height - 9) / 2,
                        VantaUiTheme.TEXT_SECONDARY);
                if (focused) graphics.fill(boxX, y + height - 2, x + width, y + height, accent);
            }
        }

        if (focused && kind != Kind.SLIDER && kind != Kind.TOGGLE && kind != Kind.DROPDOWN) {
            graphics.fill(x, y + height - 1, x + width, y + height, accent);
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int innerHeight = Math.max(1, height - padding * 2);
        if (contentHeight <= innerHeight || innerHeight <= 8) return;
        int trackX = x + width - 3;
        int trackTop = y + padding;
        int thumbHeight = Math.max(12, innerHeight * innerHeight / contentHeight);
        int maxScroll = contentHeight - innerHeight;
        int thumbY = trackTop + (innerHeight - thumbHeight) * scrollOffset / maxScroll;
        graphics.fill(trackX, trackTop, trackX + 2, trackTop + innerHeight, VantaUiTheme.BORDER_SUBTLE);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, VantaUiTheme.TEXT_MUTED);
    }

    void tick(VantaUiTheme theme) {
        float hoverTarget = hovered ? 1.0f : 0.0f;
        if (!theme.animationsEnabled()) {
            hoverAmount = hoverTarget;
        } else {
            hoverAmount += (hoverTarget - hoverAmount) * 0.30f;
            if (Math.abs(hoverTarget - hoverAmount) < 0.01f) hoverAmount = hoverTarget;
        }
        if (kind == Kind.TOGGLE && toggleValue != null) {
            float target = toggleValue.getAsBoolean() ? 1.0f : 0.0f;
            if (!theme.animationsEnabled()) {
                animatedToggle = target;
            } else {
                animatedToggle += (target - animatedToggle) * 0.28f;
                if (Math.abs(target - animatedToggle) < 0.01f) animatedToggle = target;
            }
        }
        for (VantaUiNode child : children) child.tick(theme);
    }

    void clearHover() {
        hovered = false;
        for (VantaUiNode child : children) child.clearHover();
    }

    VantaUiNode hitTest(double mouseX, double mouseY) {
        if (!visible || !contains(mouseX, mouseY)) return null;
        for (int i = children.size() - 1; i >= 0; i--) {
            VantaUiNode hit = children.get(i).hitTest(mouseX, mouseY);
            if (hit != null) return hit;
        }
        if (isInteractive() && enabled) return this;
        return null;
    }

    void setHovered(boolean value) { hovered = value; }
    void setFocused(boolean value) { focused = value; }

    void click(double mouseX, int button) {
        if (!enabled || button != 0) return;
        switch (kind) {
            case BUTTON -> { if (action != null) action.run(); }
            case TOGGLE -> {
                if (toggleSetter != null && toggleValue != null) {
                    toggleSetter.accept(!toggleValue.getAsBoolean());
                }
            }
            case SLIDER -> updateSlider(mouseX);
            case DROPDOWN -> cycleDropdown(1);
            default -> { }
        }
    }

    void drag(double mouseX) {
        if (kind == Kind.SLIDER && enabled) updateSlider(mouseX);
    }

    void activate() {
        switch (kind) {
            case BUTTON -> { if (action != null) action.run(); }
            case TOGGLE -> {
                if (toggleSetter != null && toggleValue != null) {
                    toggleSetter.accept(!toggleValue.getAsBoolean());
                }
            }
            case DROPDOWN -> cycleDropdown(1);
            default -> { }
        }
    }

    void adjust(int direction) {
        if (direction == 0 || !enabled) return;
        if (kind == Kind.SLIDER && sliderSetter != null && sliderValue != null) {
            double step = sliderStep > 0.0 ? sliderStep : Math.max(0.01, (sliderMaximum - sliderMinimum) / 100.0);
            setSliderValue(sliderValue.getAsDouble() + step * direction);
        } else if (kind == Kind.DROPDOWN) {
            cycleDropdown(direction);
        }
    }

    boolean scrollAt(double mouseX, double mouseY, double amount) {
        if (!visible || !contains(mouseX, mouseY)) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).scrollAt(mouseX, mouseY, amount)) return true;
        }
        if (scrollable && contentHeight > height - padding * 2) {
            int maxScroll = Math.max(0, contentHeight - (height - padding * 2));
            scrollOffset = clamp(scrollOffset - (int) Math.round(amount * 22.0), 0, maxScroll);
            return true;
        }
        return false;
    }

    private void updateSlider(double mouseX) {
        if (sliderSetter == null || width <= 0) return;
        double normalized = clamp((mouseX - x) / width, 0.0, 1.0);
        setSliderValue(sliderMinimum + normalized * (sliderMaximum - sliderMinimum));
    }

    private void setSliderValue(double value) {
        double clamped = clamp(value, sliderMinimum, sliderMaximum);
        if (sliderStep > 0.0) {
            clamped = sliderMinimum
                    + Math.round((clamped - sliderMinimum) / sliderStep) * sliderStep;
            clamped = clamp(clamped, sliderMinimum, sliderMaximum);
        }
        sliderSetter.accept(clamped);
    }

    private void cycleDropdown(int direction) {
        if (dropdownSetter == null || dropdownOptions.isEmpty()) return;
        String current = dropdownValue == null ? null : dropdownValue.get();
        int index = dropdownOptions.indexOf(current);
        if (index < 0) index = 0;
        int next = Math.floorMod(index + direction, dropdownOptions.size());
        dropdownSetter.accept(dropdownOptions.get(next));
    }

    void collectFocusable(List<VantaUiNode> result) {
        if (!visible || !enabled) return;
        if (isInteractive()) result.add(this);
        for (VantaUiNode child : children) child.collectFocusable(result);
    }

    private String currentText() {
        if (textSupplier == null) return label;
        String value = textSupplier.get();
        return value == null ? "" : value;
    }

    private static int mixColor(int from, int to, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int a = Math.round(((from >>> 24) & 255) * (1.0f - t) + ((to >>> 24) & 255) * t);
        int r = Math.round(((from >>> 16) & 255) * (1.0f - t) + ((to >>> 16) & 255) * t);
        int g = Math.round(((from >>> 8) & 255) * (1.0f - t) + ((to >>> 8) & 255) * t);
        int b = Math.round((from & 255) * (1.0f - t) + (to & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String formatValue(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.00001) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
