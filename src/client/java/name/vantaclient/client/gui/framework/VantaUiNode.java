package name.vantaclient.client.gui.framework;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

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
    public enum Kind { CONTAINER, TEXT, ICON, IMAGE, SPACER, BUTTON, TOGGLE, SLIDER, DROPDOWN, TEXT_FIELD }
    public enum Icon { BRAND, OVERVIEW, VISUALS, CONTROLS, DISPLAY, KEYBOARD, SEARCH, CROSSHAIR, HUD, SPARK, RESET }
    public enum Direction { ROW, COLUMN, STACK }
    public enum Alignment { START, CENTER, END, STRETCH }
    public enum Justification { START, CENTER, END, SPACE_BETWEEN }

    private final Kind kind;
    private final Direction direction;
    private final List<VantaUiNode> children = new ArrayList<>();
    static final Identifier UI_FONT = Identifier.fromNamespaceAndPath("vanta", "ui");
    private static final Identifier TITLE_FONT = Identifier.fromNamespaceAndPath("vanta", "title");
    private static final Identifier CAPTION_FONT = Identifier.fromNamespaceAndPath("vanta", "caption");

    private String label = "";
    private Icon icon;
    private Identifier texture;
    private Identifier textFont = UI_FONT;
    private Supplier<String> textSupplier;
    private String tooltip = "";
    private String placeholder = "";
    private String cachedDynamicText;
    private String cachedTextLayoutValue;
    private List<String> cachedTextLines = List.of();
    private int textMaximumLines = 1;
    private int cachedTextLayoutWidth = -1;
    private String cachedInputValue;
    private String cachedInputVisibleText = "";
    private int cachedInputAvailableWidth = -1;
    private int cachedInputCursor = -1;
    private int cachedInputCursorWidth;
    private double cachedSliderValue = Double.NaN;
    private String cachedSliderValueText = "";
    private Supplier<String> inputValue;
    private Consumer<String> inputSetter;
    private int inputMaximumLength = 128;
    private int inputCursor;
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
    private boolean clipChildren;
    private Alignment alignment = Alignment.STRETCH;
    private Alignment childAlignment = Alignment.STRETCH;
    private boolean alignmentExplicit;
    private Justification justification = Justification.START;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean selected;
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
    private int caretTicks;
    private boolean caretVisible = true;

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
    private boolean dropdownOpen;
    private int dropdownHighlight;

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

    public static VantaUiNode box(int padding) {
        return stack(padding);
    }

    public static VantaUiNode overlay(int padding) {
        return stack(padding);
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

    public static VantaUiNode title(String value) {
        VantaUiNode node = text(value, VantaUiTheme.TEXT_PRIMARY);
        node.textFont = TITLE_FONT; node.preferredHeight = 22; return node;
    }
    public static VantaUiNode eyebrow(String value) {
        VantaUiNode node = text(value, VantaUiTheme.TEXT_MUTED);
        node.textFont = CAPTION_FONT; node.preferredHeight = 12; return node;
    }
    public static VantaUiNode icon(Icon icon) {
        if (icon == null) throw new IllegalArgumentException("Icon cannot be null.");
        VantaUiNode node = new VantaUiNode(Kind.ICON, null);
        node.icon = icon; node.textColor = VantaUiTheme.TEXT_SECONDARY; node.size(16, 16); return node;
    }
    public static VantaUiNode image(Identifier texture) {
        if (texture == null) throw new IllegalArgumentException("Image texture cannot be null.");
        VantaUiNode node = new VantaUiNode(Kind.IMAGE, null);
        node.texture = texture;
        node.size(24, 24);
        return node;
    }
    public static VantaUiNode paragraph(String value, int color, int maximumLines) {
        VantaUiNode node = text(value, color);
        node.textMaximumLines = Math.max(1, maximumLines);
        node.preferredHeight = node.textMaximumLines * 14;
        return node;
    }

    public static VantaUiNode dynamicText(Supplier<String> value, int color) {
        if (value == null) throw new IllegalArgumentException("Dynamic text requires a value supplier.");
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

    public static VantaUiNode button(String label, Runnable action) { return button(label, null, action); }
    public static VantaUiNode button(String label, Icon icon, Runnable action) {
        VantaUiNode node = new VantaUiNode(Kind.BUTTON, null);
        node.label = label == null ? "" : label;
        node.icon = icon;
        node.action = action;
        node.preferredHeight = VantaUiTheme.CONTROL_HEIGHT;
        return node;
    }

    public static VantaUiNode toggle(String label, BooleanSupplier value, Consumer<Boolean> setter) {
        if (value == null || setter == null) {
            throw new IllegalArgumentException("Toggle requires value and setter bindings.");
        }
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
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || maximum < minimum
                || !Double.isFinite(maximum - minimum)
                || !Double.isFinite(step) || step < 0.0 || value == null || setter == null) {
            throw new IllegalArgumentException("Slider bounds, step and bindings must be valid.");
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

    public static VantaUiNode textField(String placeholder, Supplier<String> value,
                                        Consumer<String> setter, int maximumLength) {
        if (value == null || setter == null) {
            throw new IllegalArgumentException("Text field requires value and setter bindings.");
        }
        VantaUiNode node = new VantaUiNode(Kind.TEXT_FIELD, null);
        node.placeholder = placeholder == null ? "" : placeholder;
        node.inputValue = value;
        node.inputSetter = setter;
        node.inputMaximumLength = Math.max(1, maximumLength);
        node.preferredHeight = VantaUiTheme.CONTROL_HEIGHT;
        return node;
    }

    public static VantaUiNode dropdown(String label, List<String> options,
                                       Supplier<String> value, Consumer<String> setter) {
        if (options == null || options.isEmpty() || value == null || setter == null) {
            throw new IllegalArgumentException("Dropdown requires options and value/setter bindings.");
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
    public VantaUiNode iconColor(int color) { textColor = color; return this; }
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
    public VantaUiNode align(Alignment value) {
        Alignment resolved = value == null ? Alignment.STRETCH : value;
        if (kind == Kind.CONTAINER) childAlignment = resolved;
        else {
            alignment = resolved;
            alignmentExplicit = true;
        }
        return this;
    }
    public VantaUiNode justify(Justification value) {
        justification = value == null ? Justification.START : value;
        return this;
    }
    public VantaUiNode clipToBounds() { clipChildren = true; return this; }
    public VantaUiNode scrollable() {
        scrollable = true;
        clipChildren = true;
        return this;
    }
    public VantaUiNode tooltip(String value) { tooltip = value == null ? "" : value; return this; }
    public VantaUiNode textColor(int color) { textColor = color; return this; }
    public VantaUiNode enabled(boolean value) { enabled = value; return this; }
    public VantaUiNode selected(boolean value) { selected = value; return this; }
    public VantaUiNode visible(boolean value) { visible = value; return this; }

    public Kind kind() { return kind; }
    public String tooltip() { return tooltip; }
    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }
    public boolean isInteractive() {
        return kind == Kind.BUTTON || kind == Kind.TOGGLE
                || kind == Kind.SLIDER || kind == Kind.DROPDOWN || kind == Kind.TEXT_FIELD;
    }
    public boolean isVisible() { return visible; }
    boolean isDropdownOpen() { return kind == Kind.DROPDOWN && dropdownOpen; }

    int intrinsicWidth(Font font) {
        if (preferredWidth >= 0) return clamp(preferredWidth, minWidth, maxWidth);
        int measured = switch (kind) {
            case TEXT -> textWidth(font, currentText(), textFont);
            case ICON, IMAGE -> 16;
            case SPACER -> 1;
            case BUTTON, TOGGLE, SLIDER, DROPDOWN, TEXT_FIELD -> 160;
            case CONTAINER -> {
                int size = 0;
                int visibleChildren = 0;
                for (VantaUiNode child : children) {
                    if (!child.visible) continue;
                    int childWidth = child.intrinsicWidth(font);
                    size = direction == Direction.ROW
                            ? size + childWidth
                            : Math.max(size, childWidth);
                    visibleChildren++;
                }
                if (direction == Direction.ROW && visibleChildren > 1) {
                    size += gap * (visibleChildren - 1);
                }
                yield size + padding * 2;
            }
        };
        return clamp(measured, minWidth, maxWidth);
    }

    int intrinsicHeight(Font font) {
        if (kind == Kind.DROPDOWN && dropdownOpen) {
            int expanded = VantaUiTheme.CONTROL_HEIGHT
                    + dropdownOptions.size() * 22;
            return clamp(expanded, minHeight, maxHeight);
        }
        if (preferredHeight >= 0) return clamp(preferredHeight, minHeight, maxHeight);
        int measured = switch (kind) {
            case TEXT -> textMaximumLines > 1 ? textMaximumLines * 14 : 14;
            case ICON, IMAGE -> 16;
            case SPACER -> 0;
            case BUTTON, TOGGLE, DROPDOWN, TEXT_FIELD -> VantaUiTheme.CONTROL_HEIGHT;
            case SLIDER -> VantaUiTheme.SLIDER_HEIGHT;
            case CONTAINER -> {
                int size = 0;
                int visibleChildren = 0;
                for (VantaUiNode child : children) {
                    if (!child.visible) continue;
                    int childHeight = child.intrinsicHeight(font);
                    size = direction == Direction.COLUMN
                            ? size + childHeight
                            : Math.max(size, childHeight);
                    visibleChildren++;
                }
                if (direction == Direction.COLUMN && visibleChildren > 1) {
                    size += gap * (visibleChildren - 1);
                }
                yield size + padding * 2;
            }
        };
        return clamp(measured, minHeight, maxHeight);
    }

    void arrange(int x, int y, int width, int height, Font font) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, Math.min(width, maxWidth));
        this.height = Math.max(0, Math.min(height, maxHeight));

        if (kind == Kind.TEXT && textMaximumLines > 1) {
            String value = currentText();
            if (!value.equals(cachedTextLayoutValue) || this.width != cachedTextLayoutWidth) {
                cachedTextLayoutValue = value;
                cachedTextLayoutWidth = this.width;
                cachedTextLines = wrapText(value, font, Math.max(1, this.width), textMaximumLines, textFont);
            }
            return;
        }

        if (kind != Kind.CONTAINER) return;
        List<VantaUiNode> layoutChildren = new ArrayList<>();
        for (VantaUiNode child : children) {
            if (child.visible) layoutChildren.add(child);
        }
        if (layoutChildren.isEmpty()) {
            contentHeight = 0;
            scrollOffset = 0;
            return;
        }

        int innerX = this.x + padding;
        int innerY = this.y + padding;
        int innerWidth = Math.max(0, this.width - padding * 2);
        int innerHeight = Math.max(0, this.height - padding * 2);

        if (direction == Direction.STACK) {
            for (VantaUiNode child : layoutChildren) {
                Alignment effectiveAlignment = effectiveAlignment(child);
                int requestedWidth = child.preferredWidth >= 0
                        ? child.preferredWidth
                        : effectiveAlignment == Alignment.STRETCH ? innerWidth
                        : child.intrinsicWidth(font);
                int childWidth = constrain(requestedWidth, innerWidth,
                        child.minWidth, child.maxWidth);
                Alignment effectiveAlignment = effectiveAlignment(child);
                int requestedHeight = child.kind == Kind.DROPDOWN && child.dropdownOpen
                        ? child.intrinsicHeight(font)
                        : child.preferredHeight >= 0 ? child.preferredHeight
                        : effectiveAlignment == Alignment.STRETCH ? innerHeight
                        : child.intrinsicHeight(font);
                int childHeight = constrain(requestedHeight, innerHeight,
                        child.minHeight, child.maxHeight);
                int childX = alignedOffset(innerX, innerWidth, childWidth, effectiveAlignment);
                int childY = alignedOffset(innerY, innerHeight, childHeight, effectiveAlignment);
                child.arrange(childX, childY, childWidth, childHeight, font);
            }
            return;
        }

        int childGap = gap * Math.max(0, layoutChildren.size() - 1);
        float totalFlex = 0.0f;
        int fixedPrimary = 0;
        for (VantaUiNode child : layoutChildren) {
            if (child.flex > 0.0f) totalFlex += child.flex;
            else fixedPrimary += direction == Direction.COLUMN
                    ? child.intrinsicHeight(font) : child.intrinsicWidth(font);
        }

        int availablePrimary = Math.max(0,
                (direction == Direction.COLUMN ? innerHeight : innerWidth) - childGap - fixedPrimary);
        int[] primarySizes = new int[layoutChildren.size()];
        int measuredContent = childGap;
        for (int i = 0; i < layoutChildren.size(); i++) {
            VantaUiNode child = layoutChildren.get(i);
            int primary = child.flex > 0.0f && totalFlex > 0.0f
                    ? Math.round(availablePrimary * child.flex / totalFlex)
                    : direction == Direction.COLUMN ? child.intrinsicHeight(font) : child.intrinsicWidth(font);
            primarySizes[i] = primary;
            measuredContent += primary;
        }

        int availableSize = direction == Direction.COLUMN ? innerHeight : innerWidth;
        int freeSpace = Math.max(0, availableSize - measuredContent);
        int leadingSpace = 0;
        int distributedGap = 0;
        if (totalFlex == 0.0f && !(direction == Direction.COLUMN && scrollable)) {
            switch (justification) {
                case CENTER -> leadingSpace = freeSpace / 2;
                case END -> leadingSpace = freeSpace;
                case SPACE_BETWEEN -> {
                    if (layoutChildren.size() > 1) distributedGap = freeSpace / (layoutChildren.size() - 1);
                }
                case START -> { }
            }
        }

        if (direction == Direction.COLUMN) {
            contentHeight = measuredContent;
            int maxScroll = Math.max(0, contentHeight - innerHeight);
            scrollOffset = scrollable ? clamp(scrollOffset, 0, maxScroll) : 0;
        } else {
            contentHeight = 0;
        }

        int cursor = direction == Direction.COLUMN
                ? innerY + leadingSpace - (scrollable ? scrollOffset : 0)
                : innerX + leadingSpace;
        for (int i = 0; i < layoutChildren.size(); i++) {
            VantaUiNode child = layoutChildren.get(i);
            int primary = primarySizes[i];

            if (direction == Direction.COLUMN) {
                Alignment effectiveAlignment = effectiveAlignment(child);
                int requestedWidth = child.preferredWidth >= 0
                        ? child.preferredWidth
                        : effectiveAlignment == Alignment.STRETCH ? innerWidth
                        : child.intrinsicWidth(font);
                int childWidth = constrain(requestedWidth, innerWidth,
                        child.minWidth, child.maxWidth);
                int childX = alignedOffset(innerX, innerWidth, childWidth, effectiveAlignment);
                child.arrange(childX, cursor, childWidth, primary, font);
                cursor += primary + gap + distributedGap;
            } else {
                Alignment effectiveAlignment = effectiveAlignment(child);
                int requestedHeight = child.kind == Kind.DROPDOWN && child.dropdownOpen
                        ? child.intrinsicHeight(font)
                        : child.preferredHeight >= 0 ? child.preferredHeight
                        : effectiveAlignment == Alignment.STRETCH ? innerHeight
                        : child.intrinsicHeight(font);
                int childHeight = constrain(requestedHeight, innerHeight,
                        child.minHeight, child.maxHeight);
                int childY = alignedOffset(innerY, innerHeight, childHeight, effectiveAlignment);
                child.arrange(cursor, childY, primary, childHeight, font);
                cursor += primary + gap + distributedGap;
            }
        }
    }

    private Alignment effectiveAlignment(VantaUiNode child) {
        return child.alignmentExplicit ? child.alignment : childAlignment;
    }

    private static int constrain(int desired, int available, int minimum, int maximum) {
        int upper = Math.max(0, Math.min(available, maximum));
        int lower = Math.min(upper, Math.max(0, minimum));
        return Math.max(lower, Math.min(upper, desired));
    }

    private static int alignedOffset(int start, int available, int size, Alignment alignment) {
        return switch (alignment) {
            case CENTER -> start + (available - size) / 2;
            case END -> start + available - size;
            case START, STRETCH -> start;
        };
    }

    private boolean containsClip(double mouseX, double mouseY) {
        if (!clipChildren && !scrollable) return true;
        int clipX = x + (scrollable ? padding : 0);
        int clipY = y + (scrollable ? padding : 0);
        int clipWidth = width - (scrollable ? padding * 2 : 0);
        int clipHeight = height - (scrollable ? padding * 2 : 0);
        return mouseX >= clipX && mouseX < clipX + clipWidth
                && mouseY >= clipY && mouseY < clipY + clipHeight;
    }

    void render(GuiGraphics graphics, Font font, VantaUiTheme theme,
                int mouseX, int mouseY) {
        if (!visible || width <= 0 || height <= 0) return;

        if (background != 0) fillRounded(graphics, x, y, width, height, VantaUiTheme.RADIUS_LARGE, VantaUiTheme.applyPanelOpacity(background));

        if (kind == Kind.CONTAINER) {
            if (clipChildren || scrollable) {
                int clipX = x + (scrollable ? padding : 0);
                int clipY = y + (scrollable ? padding : 0);
                int clipRight = x + width - (scrollable ? padding : 0);
                int clipBottom = y + height - (scrollable ? padding : 0);
                graphics.enableScissor(clipX, clipY, clipRight, clipBottom);
            }
            try {
                for (VantaUiNode child : children) {
                    child.render(graphics, font, theme, mouseX, mouseY);
                }
            } finally {
                if (clipChildren || scrollable) graphics.disableScissor();
            }
            if (scrollable) renderScrollbar(graphics);
            return;
        }

        boolean over = contains(mouseX, mouseY);
        int accent = theme.accent();
        switch (kind) {
            case TEXT -> {
                if (textMaximumLines > 1 && !cachedTextLines.isEmpty()) {
                    for (int i = 0; i < cachedTextLines.size(); i++)
                        graphics.drawString(font, styledText(cachedTextLines.get(i), textFont), x, y + i * 14, textColor);
                } else {
                    graphics.drawString(font, styledText(currentText(), textFont), x,
                            y + Math.max(0, (height - VantaUiTheme.TEXT_LINE_HEIGHT) / 2), textColor);
                }
            }
            case ICON -> drawIcon(graphics, icon, x, y, width, height, textColor);
            case IMAGE -> graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
                    0.0f, 0.0f, width, height, 962, 702, 962, 702);
            case SPACER, CONTAINER -> { }
            case BUTTON -> {
                int selectedSurface = mixColor(VantaUiTheme.PANEL_RAISED, accent, 0.18f);
                int surface = selected ? selectedSurface : mixColor(VantaUiTheme.SURFACE, VantaUiTheme.SURFACE_HOVER, hoverAmount);
                if (selected || over || focused) fillRounded(graphics, x, y, width, height, VantaUiTheme.RADIUS_MEDIUM, surface);
                if (selected || focused) fillRounded(graphics, x, y + 5, 2, Math.max(0, height - 10), 1, accent);
                int labelX = x, labelWidth = width;
                if (icon != null) {
                    drawIcon(graphics, icon, x + 10, y + (height - 16) / 2, 16, 16,
                            selected ? accent : VantaUiTheme.TEXT_SECONDARY);
                    labelX = x + 34; labelWidth = width - 42;
                }
                String shownLabel = fitText(font, label, Math.max(0, labelWidth - 8), UI_FONT);
                int textX = icon == null ? x + Math.max(8, (width - textWidth(font, shownLabel, UI_FONT)) / 2) : labelX;
                graphics.drawString(font, styledText(shownLabel, UI_FONT), textX,
                        y + (height - VantaUiTheme.TEXT_LINE_HEIGHT) / 2,
                        enabled ? VantaUiTheme.TEXT_PRIMARY : VantaUiTheme.TEXT_MUTED);
            }
            case TOGGLE -> {
                String shownLabel = fitText(font, label, Math.max(0, width - 52), UI_FONT);
                graphics.drawString(font, styledText(shownLabel, UI_FONT), x,
                        y + (height - VantaUiTheme.TEXT_LINE_HEIGHT) / 2,
                        enabled ? VantaUiTheme.TEXT_PRIMARY : VantaUiTheme.TEXT_MUTED);
                boolean value = toggleValue != null && toggleValue.getAsBoolean();
                int trackColor = !enabled ? VantaUiTheme.BORDER_SUBTLE
                        : value ? accent : VantaUiTheme.TRACK;
                int trackX = x + width - 42;
                int trackY = y + (height - 18) / 2;
                fillRounded(graphics, trackX, trackY, 38, 18, 9, trackColor);
                int knobX = trackX + 3 + Math.round(19.0f * animatedToggle);
                fillRounded(graphics, knobX, trackY + 3, 13, 12, 6, VantaUiTheme.WHITE);
                if (focused) graphics.fill(trackX - 2, trackY - 2, trackX + 40, trackY + 20, VantaUiTheme.withAlpha(accent, 70));
            }
            case SLIDER -> {
                double value = sliderValue == null ? sliderMinimum : sliderValue.getAsDouble();
                if (!Double.isFinite(value)) value = sliderMinimum;
                value = clamp(value, sliderMinimum, sliderMaximum);
                double range = sliderMaximum - sliderMinimum;
                double normalized = range <= 0.0 ? 0.0 : clamp((value - sliderMinimum) / range, 0.0, 1.0);
                if (Double.compare(value, cachedSliderValue) != 0) {
                    cachedSliderValue = value;
                    cachedSliderValueText = formatValue(value);
                }
                String valueText = cachedSliderValueText;
                int valueWidth = textWidth(font, valueText, UI_FONT);
                String sliderLabel = fitText(font, label, Math.max(0, width - valueWidth - 12), UI_FONT);
                int labelColor = enabled ? VantaUiTheme.TEXT_PRIMARY : VantaUiTheme.TEXT_MUTED;
                int valueColor = enabled ? VantaUiTheme.TEXT_SECONDARY : VantaUiTheme.TEXT_MUTED;
                graphics.drawString(font, styledText(sliderLabel, UI_FONT), x, y + 2, labelColor);
                graphics.drawString(font, styledText(valueText, UI_FONT), x + width - valueWidth, y + 2, valueColor);
                int trackY = y + 28;
                int knobX = x + (int) Math.round((width - 8) * normalized);
                fillRounded(graphics, x, trackY, width, 4, 2, VantaUiTheme.TRACK);
                fillRounded(graphics, x, trackY, Math.max(4, knobX + 4 - x), 4, 2,
                        enabled ? accent : VantaUiTheme.TEXT_MUTED);
                fillRounded(graphics, knobX - 2, trackY - 4, 12, 12, 6,
                        !enabled ? VantaUiTheme.TEXT_MUTED
                                : focused || over ? VantaUiTheme.WHITE : 0xFFD5DFEC);
                if (focused) graphics.fill(x, y + height - 1, x + width, y + height, accent);
            }
            case DROPDOWN -> {
                int controlHeight = VantaUiTheme.CONTROL_HEIGHT;
                int boxWidth = Math.min(width, Math.min(150, Math.max(72, width / 2)));
                int boxX = x + width - boxWidth;
                String dropdownLabel = fitText(font, label, Math.max(0, boxX - x - 12), UI_FONT);
                graphics.drawString(font, styledText(dropdownLabel, UI_FONT), x,
                        y + (controlHeight - VantaUiTheme.TEXT_LINE_HEIGHT) / 2,
                        enabled ? VantaUiTheme.TEXT_PRIMARY : VantaUiTheme.TEXT_MUTED);
                fillRounded(graphics, boxX, y + 2, x + width - boxX, controlHeight - 4,
                        VantaUiTheme.RADIUS_SMALL,
                        enabled ? mixColor(VantaUiTheme.SURFACE, VantaUiTheme.SURFACE_HOVER, hoverAmount)
                                : VantaUiTheme.INPUT_SURFACE);
                String selected = dropdownValue == null ? "" : dropdownValue.get();
                String shown = fitText(font, selected == null ? "" : selected, Math.max(0, boxWidth - 24), UI_FONT);
                graphics.drawString(font, styledText(shown, UI_FONT), boxX + 8,
                        y + (controlHeight - VantaUiTheme.TEXT_LINE_HEIGHT) / 2,
                        enabled ? VantaUiTheme.TEXT_PRIMARY : VantaUiTheme.TEXT_MUTED);
                int arrowX = x + width - 14, arrowY = y + 13;
                line(graphics, arrowX - 3, arrowY - 1, arrowX, arrowY + 2, VantaUiTheme.TEXT_SECONDARY, 1, 1);
                line(graphics, arrowX, arrowY + 2, arrowX + 3, arrowY - 1, VantaUiTheme.TEXT_SECONDARY, 1, 1);
                if (focused && enabled) graphics.fill(boxX, y + controlHeight - 2, x + width,
                        y + controlHeight, accent);

                if (dropdownOpen) {
                    int visibleOptions = dropdownOptions.size();
                    int menuTop = y + controlHeight;
                    fillRounded(graphics, x, menuTop, width, visibleOptions * 22,
                            VantaUiTheme.RADIUS_MEDIUM, VantaUiTheme.PANEL_RAISED);
                    graphics.fill(x, menuTop, x + width, menuTop + 1, VantaUiTheme.BORDER);
                    int hoveredOption = -1;
                    if (over && mouseY >= menuTop) {
                        int candidate = (mouseY - menuTop) / 22;
                        if (candidate < visibleOptions) hoveredOption = candidate;
                    }
                    for (int i = 0; i < visibleOptions; i++) {
                        int optionY = menuTop + i * 22;
                        String option = dropdownOptions.get(i);
                        boolean highlighted = hoveredOption >= 0
                                ? i == hoveredOption : i == dropdownHighlight;
                        if (highlighted) {
                            fillRounded(graphics, x + 2, optionY + 1, width - 4, 20,
                                    VantaUiTheme.RADIUS_SMALL, VantaUiTheme.withAlpha(accent, 58));
                        }
                        if (option.equals(selected)) {
                            graphics.fill(x + 1, optionY, x + 3, optionY + 22, accent);
                        }
                        String optionText = fitText(font, option, Math.max(0, width - 18), UI_FONT);
                        graphics.drawString(font, styledText(optionText, UI_FONT), x + 9, optionY + 7,
                                highlighted ? VantaUiTheme.TEXT_PRIMARY : VantaUiTheme.TEXT_SECONDARY);
                    }
                }
            }
            case TEXT_FIELD -> {
                int fieldBackground = focused ? VantaUiTheme.INPUT_FOCUS_SURFACE : VantaUiTheme.INPUT_SURFACE;
                fillRounded(graphics, x, y, width, height, VantaUiTheme.RADIUS_SMALL, fieldBackground);
                fillRounded(graphics, x, y, width, 2, VantaUiTheme.RADIUS_SMALL,
                        focused ? accent : VantaUiTheme.BORDER);
                fillRounded(graphics, x, y + height - 2, width, 2, VantaUiTheme.RADIUS_SMALL,
                        focused ? accent : VantaUiTheme.BORDER_SUBTLE);

                String value = currentInput();
                int availableWidth = Math.max(0, width - 18);
                int cursor = clamp(inputCursor, 0, value.length());
                if (!value.equals(cachedInputValue)
                        || availableWidth != cachedInputAvailableWidth
                        || cursor != cachedInputCursor) {
                    cachedInputValue = value;
                    cachedInputAvailableWidth = availableWidth;
                    cachedInputCursor = cursor;

                    int visibleStart = 0;
                    while (visibleStart < cursor
                            && textWidth(font, value.substring(visibleStart, cursor), UI_FONT) > availableWidth) {
                        visibleStart++;
                    }
                    int visibleEnd = value.length();
                    while (visibleEnd > cursor
                            && textWidth(font, value.substring(visibleStart, visibleEnd), UI_FONT) > availableWidth) {
                        visibleEnd--;
                    }
                    cachedInputVisibleText = value.substring(visibleStart, visibleEnd);
                    cachedInputCursorWidth = textWidth(font, value.substring(visibleStart, cursor), UI_FONT);
                }

                int textColor = !enabled || value.isEmpty()
                        ? VantaUiTheme.TEXT_MUTED : VantaUiTheme.TEXT_PRIMARY;
                String display = value.isEmpty()
                        ? fitText(font, placeholder, availableWidth, UI_FONT)
                        : cachedInputVisibleText;
                graphics.drawString(font, styledText(display, UI_FONT), x + 8,
                        y + (height - VantaUiTheme.TEXT_LINE_HEIGHT) / 2, textColor);

                if (focused && caretVisible && width > 12) {
                    int caretX = Math.min(x + width - 5, x + 8 + cachedInputCursorWidth);
                    int caretY = y + (height - 12) / 2;
                    graphics.fill(caretX, caretY, caretX + 1, caretY + 12, accent);
                }
            }
        }

        if (focused && kind != Kind.SLIDER && kind != Kind.TOGGLE
                && kind != Kind.DROPDOWN && kind != Kind.TEXT_FIELD) {
            graphics.fill(x, y + height - 1, x + width, y + height, accent);
        }
    }

    private static void drawIcon(GuiGraphics g, Icon icon, int x, int y, int w, int h, int color) {
        if(icon==null||w<=0||h<=0)return; float sx=w/16.0f,sy=h/16.0f;
        switch(icon){
            case BRAND -> {
                // A compact framed monogram: the open lower edge keeps the mark light,
                // while the inset stroke gives the V a distinct silhouette.
                line(g, x + 3 * sx, y + 2 * sy, x + 12 * sx, y + 2 * sy, color, sx, sy);
                line(g, x + 12 * sx, y + 2 * sy, x + 15 * sx, y + 5 * sy, color, sx, sy);
                line(g, x + 15 * sx, y + 5 * sy, x + 15 * sx, y + 10 * sy, color, sx, sy);
                line(g, x + 15 * sx, y + 10 * sy, x + 12 * sx, y + 13 * sy, color, sx, sy);
                line(g, x + 3 * sx, y + 2 * sy, x + 1 * sx, y + 5 * sy, color, sx, sy);
                line(g, x + 1 * sx, y + 5 * sy, x + 1 * sx, y + 10 * sy, color, sx, sy);
                line(g, x + 1 * sx, y + 10 * sy, x + 4 * sx, y + 13 * sy, color, sx, sy);
                line(g, x + 4 * sx, y + 13 * sy, x + 12 * sx, y + 13 * sy, color, sx, sy);
                line(g, x + 4 * sx, y + 5 * sy, x + 8 * sx, y + 12 * sy, color, 1.5f * sx, 1.5f * sy);
                line(g, x + 8 * sx, y + 12 * sy, x + 12 * sx, y + 5 * sy, color, 1.5f * sx, 1.5f * sy);
            }
            case OVERVIEW->{rect(g,x+2*sx,y+2*sy,5*sx,5*sy,color,sx,sy);rect(g,x+9*sx,y+2*sy,5*sx,5*sy,color,sx,sy);rect(g,x+2*sx,y+9*sy,5*sx,5*sy,color,sx,sy);rect(g,x+9*sx,y+9*sy,5*sx,5*sy,color,sx,sy);}
            case VISUALS->{line(g,x+1*sx,y+8*sy,x+4*sx,y+4*sy,color,sx,sy);line(g,x+4*sx,y+4*sy,x+8*sx,y+2*sy,color,sx,sy);line(g,x+8*sx,y+2*sy,x+12*sx,y+4*sy,color,sx,sy);line(g,x+12*sx,y+4*sy,x+15*sx,y+8*sy,color,sx,sy);line(g,x+15*sx,y+8*sy,x+12*sx,y+12*sy,color,sx,sy);line(g,x+12*sx,y+12*sy,x+8*sx,y+14*sy,color,sx,sy);line(g,x+8*sx,y+14*sy,x+4*sx,y+12*sy,color,sx,sy);line(g,x+4*sx,y+12*sy,x+1*sx,y+8*sy,color,sx,sy);ring(g,x+8*sx,y+8*sy,2*sx,color);}
            case CONTROLS->{for(int i=0;i<3;i++)line(g,x+2*sx,y+(4+i*4)*sy,x+14*sx,y+(4+i*4)*sy,color,sx,sy);ring(g,x+6*sx,y+4*sy,1.8f*sx,color);ring(g,x+11*sx,y+8*sy,1.8f*sx,color);ring(g,x+5*sx,y+12*sy,1.8f*sx,color);}
            case DISPLAY->{rect(g,x+1.5f*sx,y+2*sy,13*sx,9*sy,color,sx,sy);line(g,x+8*sx,y+11*sy,x+8*sx,y+14*sy,color,sx,sy);line(g,x+4*sx,y+14*sy,x+12*sx,y+14*sy,color,sx,sy);}
            case KEYBOARD->{rect(g,x+1*sx,y+3*sy,14*sx,10*sy,color,sx,sy);for(int row=0;row<2;row++)for(int col=0;col<5;col++){float px=x+(3+col*2.5f)*sx,py=y+(5+row*3)*sy;g.fill(Math.round(px),Math.round(py),Math.round(px+sx),Math.round(py+sy),color);}}
            case SEARCH->{ring(g,x+6.5f*sx,y+6.5f*sy,4.3f*sx,color);line(g,x+9.7f*sx,y+9.7f*sy,x+14*sx,y+14*sy,color,sx,sy);}
            case CROSSHAIR->{line(g,x+8*sx,y+1*sy,x+8*sx,y+5*sy,color,sx,sy);line(g,x+8*sx,y+11*sy,x+8*sx,y+15*sy,color,sx,sy);line(g,x+1*sx,y+8*sy,x+5*sx,y+8*sy,color,sx,sy);line(g,x+11*sx,y+8*sy,x+15*sx,y+8*sy,color,sx,sy);ring(g,x+8*sx,y+8*sy,2*sx,color);}
            case HUD->{line(g,x+2*sx,y+6*sy,x+2*sx,y+2*sy,color,sx,sy);line(g,x+2*sx,y+2*sy,x+6*sx,y+2*sy,color,sx,sy);line(g,x+10*sx,y+2*sy,x+14*sx,y+2*sy,color,sx,sy);line(g,x+14*sx,y+2*sy,x+14*sx,y+6*sy,color,sx,sy);line(g,x+2*sx,y+10*sy,x+2*sx,y+14*sy,color,sx,sy);line(g,x+2*sx,y+14*sy,x+6*sx,y+14*sy,color,sx,sy);line(g,x+10*sx,y+14*sy,x+14*sx,y+14*sy,color,sx,sy);line(g,x+14*sx,y+10*sy,x+14*sx,y+14*sy,color,sx,sy);}
            case SPARK->{line(g,x+8*sx,y+1*sy,x+9.5f*sx,y+6.5f*sy,color,sx,sy);line(g,x+9.5f*sx,y+6.5f*sy,x+15*sx,y+8*sy,color,sx,sy);line(g,x+15*sx,y+8*sy,x+9.5f*sx,y+9.5f*sy,color,sx,sy);line(g,x+9.5f*sx,y+9.5f*sy,x+8*sx,y+15*sy,color,sx,sy);line(g,x+8*sx,y+15*sy,x+6.5f*sx,y+9.5f*sy,color,sx,sy);line(g,x+6.5f*sx,y+9.5f*sy,x+1*sx,y+8*sy,color,sx,sy);line(g,x+1*sx,y+8*sy,x+6.5f*sx,y+6.5f*sy,color,sx,sy);line(g,x+6.5f*sx,y+6.5f*sy,x+8*sx,y+1*sy,color,sx,sy);}
            case RESET->{line(g,x+13*sx,y+2*sy,x+10*sx,y+4*sy,color,sx,sy);line(g,x+13*sx,y+2*sy,x+15*sx,y+5*sy,color,sx,sy);line(g,x+12*sx,y+3*sy,x+6*sx,y+2*sy,color,sx,sy);line(g,x+6*sx,y+2*sy,x+2*sx,y+6*sy,color,sx,sy);line(g,x+2*sx,y+6*sy,x+3*sx,y+12*sy,color,sx,sy);line(g,x+3*sx,y+12*sy,x+8*sx,y+14*sy,color,sx,sy);line(g,x+8*sx,y+14*sy,x+12*sx,y+11*sy,color,sx,sy);}
        }
    }
    private static void line(GuiGraphics g,float x1,float y1,float x2,float y2,int color,float sx,float sy){
        int steps=Math.max(1,Math.round(Math.max(Math.abs(x2-x1),Math.abs(y2-y1))*2));
        for(int i=0;i<=steps;i++){float t=i/(float)steps;int px=Math.round(x1+(x2-x1)*t),py=Math.round(y1+(y2-y1)*t);g.fill(px,py,px+Math.max(1,Math.round(sx)),py+Math.max(1,Math.round(sy)),color);}
    }
    private static void rect(GuiGraphics g,float x,float y,float w,float h,int color,float sx,float sy){
        line(g,x,y,x+w,y,color,sx,sy);line(g,x,y+h,x+w,y+h,color,sx,sy);line(g,x,y,x,y+h,color,sx,sy);line(g,x+w,y,x+w,y+h,color,sx,sy);
    }
    private static void ring(GuiGraphics g,float cx,float cy,float radius,int color){
        float px=cx+radius,py=cy;for(int a=15;a<=360;a+=15){double rad=Math.toRadians(a);float nx=cx+(float)Math.cos(rad)*radius,ny=cy+(float)Math.sin(rad)*radius;line(g,px,py,nx,ny,color,1,1);px=nx;py=ny;}
    }
    /**
     * Draws a rounded rectangle using a small set of horizontal spans.
     * This avoids a custom GPU pipeline while keeping panel/control corners consistent.
     */
    private static void fillRounded(GuiGraphics graphics, int x, int y, int width, int height,
                                    int radius, int color) {
        if (width <= 0 || height <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(width / 2, height / 2)));
        if (r == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + r, y, x + width - r, y + height, color);
        for (int row = 0; row < r; row++) {
            int dy = r - row;
            int inset = r - (int) Math.floor(Math.sqrt((double) r * r - (double) dy * dy));
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            graphics.fill(x + inset, y + height - row - 1,
                    x + width - inset, y + height - row, color);
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

    void tick() {
        if (!visible) return;
        if (kind == Kind.TEXT_FIELD && focused) {
            caretTicks++;
            if (caretTicks >= VantaUiTheme.CARET_BLINK_TICKS) {
                caretTicks = 0;
                caretVisible = !caretVisible;
            }
        } else {
            caretTicks = 0;
            caretVisible = true;
        }
        for (VantaUiNode child : children) child.tick();
    }

    void animate(boolean animationsEnabled, float hoverFactor, float toggleFactor) {
        if (!visible) return;
        float hoverTarget = hovered ? 1.0f : 0.0f;
        if (!animationsEnabled) {
            hoverAmount = hoverTarget;
        } else if (Math.abs(hoverTarget - hoverAmount) >= 0.002f) {
            hoverAmount += (hoverTarget - hoverAmount) * hoverFactor;
            if (Math.abs(hoverTarget - hoverAmount) < 0.002f) hoverAmount = hoverTarget;
        }
        if (kind == Kind.TOGGLE && toggleValue != null) {
            float target = toggleValue.getAsBoolean() ? 1.0f : 0.0f;
            if (!animationsEnabled) {
                animatedToggle = target;
            } else if (Math.abs(target - animatedToggle) >= 0.002f) {
                animatedToggle += (target - animatedToggle) * toggleFactor;
                if (Math.abs(target - animatedToggle) < 0.002f) animatedToggle = target;
            }
        }
        for (VantaUiNode child : children) {
            child.animate(animationsEnabled, hoverFactor, toggleFactor);
        }
    }

    void clearHover() {
        hovered = false;
        for (VantaUiNode child : children) child.clearHover();
    }

    VantaUiNode hitTest(double mouseX, double mouseY) {
        if (!visible || !contains(mouseX, mouseY)) return null;
        if (!enabled) return null;
        if (kind == Kind.CONTAINER && !containsClip(mouseX, mouseY)) {
            return isInteractive() && enabled ? this : null;
        }
        for (int i = children.size() - 1; i >= 0; i--) {
            VantaUiNode hit = children.get(i).hitTest(mouseX, mouseY);
            if (hit != null) return hit;
        }
        if (isInteractive() && enabled) return this;
        return null;
    }

    void setHovered(boolean value) { hovered = value; }
    void setFocused(boolean value) {
        focused = value;
        if (focused && kind == Kind.TEXT_FIELD) {
            inputCursor = currentInput().length();
            caretVisible = true;
            caretTicks = 0;
        }
    }

    void click(double mouseX, double mouseY, int button, Font font) {
        if (!enabled || button != 0) return;
        switch (kind) {
            case BUTTON -> { if (action != null) action.run(); }
            case TOGGLE -> {
                if (toggleSetter != null && toggleValue != null) {
                    toggleSetter.accept(!toggleValue.getAsBoolean());
                }
            }
            case SLIDER -> updateSlider(mouseX);
            case DROPDOWN -> {
                int controlBottom = y + VantaUiTheme.CONTROL_HEIGHT;
                if (mouseY < controlBottom || !dropdownOpen) {
                    if (dropdownOpen) {
                        dropdownOpen = false;
                    } else {
                        dropdownHighlight = Math.max(0, selectedDropdownIndex());
                        dropdownOpen = true;
                    }
                } else {
                    int optionIndex = (int) ((mouseY - controlBottom) / 22.0);
                    if (optionIndex >= 0 && optionIndex < dropdownOptions.size()) {
                        dropdownHighlight = optionIndex;
                        dropdownSetter.accept(dropdownOptions.get(optionIndex));
                    }
                    dropdownOpen = false;
                }
            }
            case TEXT_FIELD -> positionInputCursor(mouseX, font);
            default -> { }
        }
    }

    private void positionInputCursor(double mouseX, Font font) {
        String value = currentInput();
        int availableWidth = Math.max(0, width - 18);
        int cursor = clamp(inputCursor, 0, value.length());

        int visibleStart = 0;
        while (visibleStart < cursor
                && textWidth(font, value.substring(visibleStart, cursor), UI_FONT) > availableWidth) {
            visibleStart = value.offsetByCodePoints(visibleStart, 1);
        }
        int visibleEnd = value.length();
        while (visibleEnd > cursor
                && textWidth(font, value.substring(visibleStart, visibleEnd), UI_FONT) > availableWidth) {
            visibleEnd = value.offsetByCodePoints(visibleEnd, -1);
        }

        double targetX = mouseX - (x + 8);
        int previousWidth = 0;
        int selectedCursor = visibleStart;
        int offset = visibleStart;
        while (offset < visibleEnd) {
            int next = value.offsetByCodePoints(offset, 1);
            int nextWidth = textWidth(font, value.substring(visibleStart, next), UI_FONT);
            if (targetX < (previousWidth + nextWidth) / 2.0) break;
            selectedCursor = next;
            previousWidth = nextWidth;
            offset = next;
        }

        inputCursor = selectedCursor;
        caretVisible = true;
        caretTicks = 0;
    }

    boolean charTyped(int codepoint) {
        if (kind != Kind.TEXT_FIELD || !focused || !enabled || !Character.isValidCodePoint(codepoint)
                || Character.isISOControl(codepoint)) return false;
        String current = currentInput();
        inputCursor = clamp(inputCursor, 0, current.length());
        String insertion = new String(Character.toChars(codepoint));
        if (current.length() + insertion.length() > inputMaximumLength) return true;
        String next = current.substring(0, inputCursor) + insertion + current.substring(inputCursor);
        inputCursor += insertion.length();
        inputSetter.accept(next);
        caretVisible = true;
        caretTicks = 0;
        return true;
    }

    boolean editKey(int keyCode) {
        if (kind != Kind.TEXT_FIELD || !focused || !enabled) return false;
        String current = currentInput();
        inputCursor = clamp(inputCursor, 0, current.length());
        switch (keyCode) {
            case 259 -> {
                if (inputCursor > 0) {
                    int start = current.offsetByCodePoints(inputCursor, -1);
                    inputSetter.accept(current.substring(0, start) + current.substring(inputCursor));
                    inputCursor = start;
                }
            }
            case 261 -> {
                if (inputCursor < current.length()) {
                    int end = current.offsetByCodePoints(inputCursor, 1);
                    inputSetter.accept(current.substring(0, inputCursor) + current.substring(end));
                }
            }
            case 263 -> {
                if (inputCursor > 0) inputCursor = current.offsetByCodePoints(inputCursor, -1);
            }
            case 262 -> {
                if (inputCursor < current.length()) inputCursor = current.offsetByCodePoints(inputCursor, 1);
            }
            case 268 -> inputCursor = 0;
            case 269 -> inputCursor = current.length();
            default -> { return false; }
        }
        caretVisible = true;
        caretTicks = 0;
        return true;
    }

    void drag(double mouseX) {
        if (kind == Kind.SLIDER && enabled) updateSlider(mouseX);
    }

    void activate() {
        if (!enabled) return;
        switch (kind) {
            case BUTTON -> { if (action != null) action.run(); }
            case TOGGLE -> {
                if (toggleSetter != null && toggleValue != null) {
                    toggleSetter.accept(!toggleValue.getAsBoolean());
                }
            }
            case DROPDOWN -> {
                if (dropdownOpen) {
                    if (dropdownHighlight >= 0 && dropdownHighlight < dropdownOptions.size()) {
                        dropdownSetter.accept(dropdownOptions.get(dropdownHighlight));
                    }
                    dropdownOpen = false;
                } else {
                    dropdownHighlight = Math.max(0, selectedDropdownIndex());
                    dropdownOpen = true;
                }
            }
            case TEXT_FIELD -> { }
            default -> { }
        }
    }

    void adjust(int direction) {
        if (direction == 0 || !enabled) return;
        if (kind == Kind.SLIDER && sliderSetter != null && sliderValue != null) {
            double step = sliderStep > 0.0 ? sliderStep : Math.max(0.01, (sliderMaximum - sliderMinimum) / 100.0);
            setSliderValue(sliderValue.getAsDouble() + step * direction);
        } else if (kind == Kind.DROPDOWN) {
            if (dropdownOpen) {
                int maxIndex = dropdownOptions.size() - 1;
                dropdownHighlight = Math.max(0, Math.min(maxIndex, dropdownHighlight + direction));
            } else {
                cycleDropdown(direction);
            }
        }
    }

    boolean revealDescendant(VantaUiNode target, int requiredHeight) {
        if (this == target) return true;
        boolean found = false;
        for (VantaUiNode child : children) {
            if (child.revealDescendant(target, requiredHeight)) {
                found = true;
                break;
            }
        }
        if (found && scrollable) {
            int viewportTop = y + padding;
            int viewportBottom = y + height - padding;
            int targetTop = target.y;
            int targetBottom = target.y + requiredHeight;

            if (targetBottom > viewportBottom) {
                scrollOffset += targetBottom - viewportBottom;
            }
            if (targetTop < viewportTop) {
                scrollOffset -= viewportTop - targetTop;
            }

            int viewportHeight = Math.max(1, height - padding * 2);
            int maxScroll = Math.max(0, contentHeight - viewportHeight);
            scrollOffset = clamp(scrollOffset, 0, maxScroll);
        }
        return found;
    }

    boolean scrollAt(double mouseX, double mouseY, double amount) {
        if (!visible || !enabled || !contains(mouseX, mouseY) || !containsClip(mouseX, mouseY)) return false;
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
        if (!Double.isFinite(value)) return;
        double clamped = clamp(value, sliderMinimum, sliderMaximum);
        if (sliderStep > 0.0) {
            clamped = sliderMinimum
                    + Math.round((clamped - sliderMinimum) / sliderStep) * sliderStep;
            clamped = clamp(clamped, sliderMinimum, sliderMaximum);
        }
        sliderSetter.accept(clamped);
    }

    private int selectedDropdownIndex() {
        String current = dropdownValue == null ? null : dropdownValue.get();
        return dropdownOptions.indexOf(current);
    }

    private void cycleDropdown(int direction) {
        if (dropdownSetter == null || dropdownOptions.isEmpty()) return;
        int index = selectedDropdownIndex();
        int next = index < 0
                ? direction > 0 ? 0 : dropdownOptions.size() - 1
                : Math.floorMod(index + direction, dropdownOptions.size());
        dropdownSetter.accept(dropdownOptions.get(next));
    }

    boolean closeDropdown() {
        if (kind != Kind.DROPDOWN || !dropdownOpen) return false;
        dropdownOpen = false;
        return true;
    }

    boolean closeDropdownsExcept(VantaUiNode keepOpen) {
        boolean changed = false;
        if (kind == Kind.DROPDOWN && this != keepOpen && dropdownOpen) {
            dropdownOpen = false;
            changed = true;
        }
        for (VantaUiNode child : children) {
            changed |= child.closeDropdownsExcept(keepOpen);
        }
        return changed;
    }

    void collectFocusable(List<VantaUiNode> result) {
        if (!visible || !enabled) return;
        if (isInteractive()) result.add(this);
        for (VantaUiNode child : children) child.collectFocusable(result);
    }

    private String currentInput() {
        String value = inputValue == null ? "" : inputValue.get();
        return value == null ? "" : value;
    }

    static Component styledText(String value, Identifier fontId) {
        return Component.literal(value == null ? "" : value).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(fontId == null ? UI_FONT : fontId)));
    }
    static int textWidth(Font font, String value, Identifier fontId) { return font.width(styledText(value, fontId)); }
    static String fitText(Font font, String value, int maximumWidth, Identifier fontId) {
        if (value == null || value.isEmpty() || maximumWidth <= 0) return "";
        int end = value.length();
        while (end > 0 && textWidth(font, value.substring(0, end), fontId) > maximumWidth)
            end = value.offsetByCodePoints(end, -1);
        return value.substring(0, end);
    }
    private static List<String> wrapText(String value, Font font, int maximumWidth, int maximumLines, Identifier fontId) {
        if (value == null || value.isBlank()) return List.of("");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String[] words = value.trim().split("\\s+");
        boolean truncated = false;

        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (textWidth(font, candidate, fontId) > maximumWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
                if (lines.size() >= maximumLines) {
                    truncated = true;
                    break;
                }
            }

            if (textWidth(font, word, fontId) > maximumWidth) {
                word = fitText(font, word, maximumWidth, fontId);
                truncated = true;
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
            if (truncated) break;
        }

        if (!line.isEmpty() && lines.size() < maximumLines) lines.add(line.toString());
        if (truncated && !lines.isEmpty()) {
            int lastIndex = lines.size() - 1;
            String last = lines.get(lastIndex);
            String ellipsis = "…";
            while (!last.isEmpty() && textWidth(font, last + ellipsis, fontId) > maximumWidth) {
                last = last.substring(0, last.length() - 1);
            }
            lines.set(lastIndex, last + ellipsis);
        }
        if (lines.isEmpty()) lines.add(fitText(font, value, maximumWidth, fontId));
        return List.copyOf(lines);
    }

    private String currentText() {
        if (textSupplier == null) return label;
        String value = textSupplier.get();
        if (value == null) value = "";
        if (!value.equals(cachedDynamicText)) cachedDynamicText = value;
        return cachedDynamicText;
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
