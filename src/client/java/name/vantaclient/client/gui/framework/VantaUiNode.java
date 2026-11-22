package name.vantaclient.client.gui.framework;

import net.minecraft.client.gui.Font;
import name.vantaclient.client.gui.compose.VantaCompose;
import name.vantaclient.client.gui.compose.theme.VantaTheme;
import name.vantaclient.client.gui.compose.typography.VantaTypography;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
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
public final class VantaUiNode implements VantaUiComponent {
    public enum Kind { CONTAINER, TEXT, ICON, IMAGE, SPACER, BUTTON, TOGGLE, SLIDER, DROPDOWN, TEXT_FIELD, PROGRESS, CANVAS }
    public enum Icon { BRAND, OVERVIEW, VISUALS, CONTROLS, DISPLAY, KEYBOARD, SEARCH, CROSSHAIR, HUD, SPARK, RESET, COMBAT, MISC }
    public enum Direction { ROW, COLUMN, STACK }
    public enum Alignment { START, CENTER, END, STRETCH }
    public enum Justification { START, CENTER, END, SPACE_BETWEEN }

    /** Logical-screen bounds for a floating dropdown popup. */
    public record DropdownPopupBounds(int x, int y, int width, int height,
                                      int firstOption, int visibleOptions) {}

    /** Per-frame state handed to a {@link Painter}. */
    public record PaintState(boolean hovered, boolean focused, float hover,
                             int mouseX, int mouseY) {}

    /** Custom drawing callback for a {@link Kind#CANVAS} node; coordinates are logical GUI units. */
    @FunctionalInterface
    public interface Painter {
        void paint(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                   PaintState state);
    }

    /** Pointer callback with coordinates relative to the node's top-left corner and the node's size. */
    @FunctionalInterface
    public interface PointerAction {
        void click(int localX, int localY, int width, int height);
    }

    final Kind kind;
    final Direction direction;
    final List<VantaUiNode> children = new ArrayList<>();
    static final Identifier UI_FONT = Identifier.fromNamespaceAndPath("vanta", "ui");
    static final Identifier MENU_FONT = Identifier.fromNamespaceAndPath("vanta", "medium");

    String label = "";
    private Icon icon;
    private Identifier texture;
    private Supplier<Identifier> textureSupplier;
    Identifier textFont = UI_FONT;
    private Supplier<String> textSupplier;
    private String tooltip = "";
    private String placeholder = "";
    private String cachedDynamicText;
    String cachedTextLayoutValue;
    List<String> cachedTextLines = List.of();
    int textMaximumLines = 1;
    int cachedTextLayoutWidth = -1;
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
    private boolean boldText;
    private int buttonForeground;
    private int background;
    int preferredWidth = -1;
    int preferredHeight = -1;
    VantaLayoutConstraints constraints = VantaLayoutConstraints.UNBOUNDED;
    int padding;
    int gap;
    float flex;
    boolean scrollable;
    private boolean clipChildren;
    Alignment alignment = Alignment.STRETCH;
    Alignment childAlignment = Alignment.STRETCH;
    boolean alignmentExplicit;
    Justification justification = Justification.START;
    boolean visible = true;
    private boolean enabled = true;
    private boolean selected;
    private boolean hovered;
    private boolean focused;
    int scrollOffset;
    int contentHeight;
    private int[] layoutPrimarySizes = new int[0];

    int[] layoutPrimarySizes(int requiredSize) {
        if (layoutPrimarySizes.length < requiredSize) {
            layoutPrimarySizes = new int[requiredSize];
        }
        return layoutPrimarySizes;
    }
    private int scrollDragPointerOffset;

    int x;
    int y;
    int width;
    int height;
    private float hoverAmount;
    private float animatedToggle;
    private float progressPhase;
    private int caretTicks;
    private boolean caretVisible = true;

    private Runnable action;
    private Painter painter;
    private PointerAction pointerAction;
    private int cornerRadius = -1;
    private int borderColor;
    private boolean menuStyle;
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
    private List<StateBinding> observedStates;
    private boolean dropdownOpen;
    private int dropdownHighlight;
    private int dropdownScrollOffset;

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

    public static VantaUiNode label(String value, int color) {
        VantaUiNode node = text(value, color);
        node.textFont = VantaTypography.font(VantaTypography.Role.LABEL);
        node.preferredHeight = VantaTypography.lineHeight(VantaTypography.Role.LABEL);
        node.boldText = true;
        return node;
    }

    public static VantaUiNode title(String value) {
        VantaUiNode node = text(value, VantaUiTheme.TEXT_PRIMARY);
        node.textFont = VantaTypography.font(VantaTypography.Role.TITLE);
        node.preferredHeight = VantaTypography.lineHeight(VantaTypography.Role.TITLE);
        node.boldText = true; return node;
    }

    public static VantaUiNode heading(String value) {
        VantaUiNode node = text(value, VantaUiTheme.TEXT_PRIMARY);
        node.textFont = VantaTypography.font(VantaTypography.Role.HEADING);
        node.preferredHeight = VantaTypography.lineHeight(VantaTypography.Role.HEADING);
        node.boldText = true;
        return node;
    }

    public static VantaUiNode display(String value) {
        VantaUiNode node = text(value, VantaUiTheme.TEXT_PRIMARY);
        node.textFont = VantaTypography.font(VantaTypography.Role.DISPLAY);
        node.preferredHeight = VantaTypography.lineHeight(VantaTypography.Role.DISPLAY);
        node.boldText = true;
        return node;
    }
    public static VantaUiNode eyebrow(String value) {
        VantaUiNode node = text(sentenceCaseCaption(value), VantaUiTheme.TEXT_MUTED);
        node.textFont = VantaTypography.font(VantaTypography.Role.CAPTION);
        node.preferredHeight = VantaTypography.lineHeight(VantaTypography.Role.CAPTION);
        return node;
    }

    private static String sentenceCaseCaption(String value) {
        if (value == null || value.isBlank()) return "";
        String[] words = value.trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder result = new StringBuilder(value.length());
        for (String word : words) {
            if (result.length() > 0) result.append(' ');
            String[] segments = word.split("/", -1);
            for (int index = 0; index < segments.length; index++) {
                if (index > 0) result.append('/');
                String segment = segments[index];
                if (segment.isEmpty()) continue;
                String formatted = switch (segment) {
                    case "hud", "fps", "cps", "ui", "api", "jar", "cpu", "gpu",
                            "lan", "http", "https", "p2p", "rgb", "url" -> segment.toUpperCase(Locale.ROOT);
                    default -> Character.toUpperCase(segment.charAt(0)) + segment.substring(1);
                };
                result.append(formatted);
            }
        }
        return result.toString();
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

    /**
     * Image node whose texture can become available after layout without rebuilding the UI tree.
     * The fallback is rendered in the same bounds until the supplier returns a texture.
     */
    public static VantaUiNode dynamicImage(Supplier<Identifier> texture, String fallback) {
        if (texture == null) throw new IllegalArgumentException("Dynamic image requires a texture supplier.");
        VantaUiNode node = new VantaUiNode(Kind.IMAGE, null);
        node.textureSupplier = texture;
        node.label = fallback == null ? "" : fallback;
        node.size(24, 24);
        return node;
    }
    public static VantaUiNode paragraph(String value, int color, int maximumLines) {
        VantaUiNode node = text(value, color);
        node.textMaximumLines = Math.max(1, maximumLines);
        node.preferredHeight = node.textMaximumLines * VantaTypography.lineHeight(VantaTypography.Role.BODY);
        return node;
    }

    /**
     * Creates an indeterminate progress row. It intentionally does not claim a
     * percentage because callers may not have access to byte-level progress.
     */
    public static VantaUiNode progressIndicator(String label) {
        VantaUiNode node = new VantaUiNode(Kind.PROGRESS, null);
        node.label = label == null ? "" : label;
        node.textColor = VantaUiTheme.TEXT_MUTED;
        node.preferredHeight = 30;
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

    /** Creates a node whose pixels are produced by {@code painter}. Add {@link #onClick} to make it interactive. */
    public static VantaUiNode canvas(Painter painter) {
        if (painter == null) throw new IllegalArgumentException("Canvas requires a painter.");
        VantaUiNode node = new VantaUiNode(Kind.CANVAS, null);
        node.painter = painter;
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
    /** Corner radius used by this node's own background and border. */
    public VantaUiNode radius(int value) { cornerRadius = Math.max(0, value); return this; }
    /** One-pixel outline drawn over the background with the node's corner radius. */
    public VantaUiNode border(int color) { borderColor = color; return this; }
    /** Switches toggles, dropdowns, sliders and text fields to the flat menu skin. */
    public VantaUiNode menuStyle() { menuStyle = true; return this; }
    /** Selects the font used by text, button and field labels. */
    public VantaUiNode font(Identifier fontId) { if (fontId != null) textFont = fontId; return this; }
    /** Runs {@code callback} when a canvas or button node is clicked or activated by keyboard. */
    public VantaUiNode onClick(Runnable callback) { action = callback; return this; }
    /** Receives the click position relative to this node. Used by canvas nodes. */
    public VantaUiNode onClickAt(PointerAction callback) { pointerAction = callback; return this; }
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
        constraints = constraints.withMinimum(width, height);
        return this;
    }
    public VantaUiNode maxSize(int width, int height) {
        constraints = constraints.withMaximum(width, height);
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
    public VantaUiNode buttonForeground(int color) { buttonForeground = color; return this; }
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
                || kind == Kind.SLIDER || kind == Kind.DROPDOWN || kind == Kind.TEXT_FIELD
                || (kind == Kind.CANVAS && (action != null || pointerAction != null));
    }
    public boolean isVisible() { return visible; }
    public boolean isEnabled() { return enabled; }
    boolean isDropdownOpen() { return kind == Kind.DROPDOWN && dropdownOpen; }

    int backgroundColor() { return background; }
    int borderColor() { return borderColor; }
    int cornerRadius() { return cornerRadius; }
    boolean isSelected() { return selected; }
    boolean isHovered() { return hovered; }
    boolean isFocused() { return focused; }

    @Override
    public VantaUiComponent.Bounds bounds() {
        return new VantaUiComponent.Bounds(x, y, width, height);
    }

    @Override
    public VantaUiComponent.Semantics semantics() {
        VantaUiComponent.Role role = switch (kind) {
            case CONTAINER -> VantaUiComponent.Role.GROUP;
            case TEXT -> VantaUiComponent.Role.LABEL;
            case ICON, IMAGE -> VantaUiComponent.Role.IMAGE;
            case SPACER -> VantaUiComponent.Role.PRESENTATION;
            case BUTTON -> VantaUiComponent.Role.BUTTON;
            case TOGGLE -> VantaUiComponent.Role.SWITCH;
            case SLIDER -> VantaUiComponent.Role.SLIDER;
            case DROPDOWN -> VantaUiComponent.Role.COMBO_BOX;
            case TEXT_FIELD -> VantaUiComponent.Role.TEXT_FIELD;
            case PROGRESS -> VantaUiComponent.Role.PROGRESS;
            case CANVAS -> VantaUiComponent.Role.CUSTOM;
        };
        String accessibleLabel = label;
        if (kind == Kind.TEXT) accessibleLabel = currentText();
        if (kind == Kind.TEXT_FIELD && accessibleLabel.isBlank()) accessibleLabel = placeholder;
        String value = switch (kind) {
            case TOGGLE -> Boolean.toString(toggleValue != null && toggleValue.getAsBoolean());
            case SLIDER -> {
                double slider = sliderValue == null ? sliderMinimum : sliderValue.getAsDouble();
                if (!Double.isFinite(slider)) slider = sliderMinimum;
                yield formatValue(clamp(slider, sliderMinimum, sliderMaximum));
            }
            case DROPDOWN -> dropdownValue == null ? "" : safeValue(dropdownValue.get());
            case TEXT_FIELD -> currentInput();
            case PROGRESS -> label;
            default -> "";
        };
        return new VantaUiComponent.Semantics(role, accessibleLabel, value, tooltip,
                enabled, selected, focused);
    }

    @Override
    public List<? extends VantaUiComponent> children() {
        return List.copyOf(children);
    }

    /** Registers externally-owned state so the owning runtime can invalidate on writes. */
    public VantaUiNode observeState(VantaState<?> state, boolean affectsLayout) {
        if (state == null) throw new IllegalArgumentException("Observed UI state cannot be null.");
        if (observedStates == null) observedStates = new ArrayList<>();
        for (StateBinding binding : observedStates) {
            if (binding.state() == state) {
                if (affectsLayout && !binding.affectsLayout()) {
                    observedStates.set(observedStates.indexOf(binding), new StateBinding(state, true));
                }
                return this;
            }
        }
        observedStates.add(new StateBinding(state, affectsLayout));
        return this;
    }

    void collectStateBindings(List<StateBinding> destination) {
        if (observedStates != null) destination.addAll(observedStates);
        for (VantaUiNode child : children) child.collectStateBindings(destination);
    }

    private static String safeValue(String value) {
        return value == null ? "" : value;
    }

    record StateBinding(VantaState<?> state, boolean affectsLayout) {}

    int intrinsicWidth(Font font) { return VantaUiLayout.intrinsicWidth(this, font); }

    int intrinsicHeight(Font font) { return VantaUiLayout.intrinsicHeight(this, font); }

    void arrange(int x, int y, int width, int height, Font font) {
        VantaUiLayout.arrange(this, x, y, width, height, font);
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

        VantaUiNodeStyle.draw(this, graphics, theme);

        if (kind == Kind.CONTAINER) {
            if (clipChildren || scrollable) {
                int clipX = x + (scrollable ? padding : 0);
                int clipY = y + (scrollable ? padding : 0);
                int clipRight = x + width - (scrollable ? padding : 0);
                int clipBottom = y + height - (scrollable ? padding : 0);
                graphics.enableScissor(clipX, clipY, clipRight, clipBottom);
            }
            try {
                if (scrollable) {
                    int start = visibleChildStart();
                    int end = visibleChildEnd(start);
                    int left = x + padding;
                    int right = x + width - padding;
                    for (int i = start; i < end; i++) {
                        VantaUiNode child = children.get(i);
                        if (child.x + child.width > left && child.x < right) {
                            child.render(graphics, font, theme, mouseX, mouseY);
                        }
                    }
                } else {
                    for (VantaUiNode child : children) {
                        child.render(graphics, font, theme, mouseX, mouseY);
                    }
                }
            } finally {
                if (clipChildren || scrollable) graphics.disableScissor();
            }
            if (scrollable) renderScrollbar(graphics);
            return;
        }

        boolean over = contains(mouseX, mouseY);
        int accent = theme.accent();
        VantaTheme.Glass stateTokens = theme.composeTheme().glass();
        switch (kind) {
            case TEXT -> {
                if (textMaximumLines > 1 && !cachedTextLines.isEmpty()) {
                    for (int i = 0; i < cachedTextLines.size(); i++)
                        graphics.drawString(font, styledText(cachedTextLines.get(i), textFont), x,
                                y + i * VantaTypography.lineHeight(VantaTypography.Role.BODY), textColor);
                } else {
                    int menuSize = menuFontSize(textFont);
                    int textY = menuSize > 0
                            ? y + height / 2 - 7 + Math.round(menuSize * 0.35f)
                            : y + Math.max(0, (height - VantaUiTheme.TEXT_LINE_HEIGHT) / 2);
                    graphics.drawString(font, styledText(currentText(), textFont), x, textY, textColor);
                }
            }
            case ICON -> drawIcon(graphics, icon, x, y, width, height, textColor);
            case IMAGE -> {
                Identifier resolvedTexture = textureSupplier == null ? texture : textureSupplier.get();
                if (resolvedTexture != null) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, resolvedTexture, x, y,
                            0.0f, 0.0f, width, height, 962, 702, 962, 702);
                } else if (!label.isBlank()) {
                    String fallback = fitText(font, label, Math.max(0, width - 4), UI_FONT);
                    int textWidth = textWidth(font, fallback, UI_FONT);
                    graphics.drawString(font, styledText(fallback, UI_FONT),
                            x + (width - textWidth) / 2,
                            y + Math.max(0, (height - VantaUiTheme.TEXT_LINE_HEIGHT) / 2),
                            textColor);
                }
            }
            case SPACER, CONTAINER -> { }
            case CANVAS -> painter.paint(graphics, font, x, y, width, height,
                    new PaintState(hovered || over && interactiveCanvas(), focused, hoverAmount, mouseX, mouseY));
            case PROGRESS -> {
                if (width > 0 && height >= 8) {
                    if (!label.isBlank()) {
                        String shownLabel = fitText(font, label, Math.max(0, width - 2), UI_FONT);
                        graphics.drawString(font, styledText(shownLabel, UI_FONT),
                                x, y + 1, textColor);
                    }
                    int trackY = y + height - 7;
                    int trackWidth = Math.max(0, width);
                    int segmentWidth = Math.min(trackWidth,
                            Math.max(8, Math.round(trackWidth * 0.28f)));
                    VantaUiTheme.fillSquircle(graphics, x, trackY, trackWidth, 4, 2,
                            VantaUiTheme.withAlpha(VantaUiTheme.TRACK, 180));
                    float phase = progressPhase <= 1.0f ? progressPhase : 2.0f - progressPhase;
                    int segmentTravel = Math.max(0, trackWidth - segmentWidth);
                    int segmentX = x + Math.round(segmentTravel * phase);
                    VantaUiTheme.fillSquircle(graphics, segmentX, trackY, segmentWidth, 4, 2, accent);
                }
            }
            case BUTTON -> {
                if (selected) {
                    VantaUiTheme.fillSquircle(graphics, x, y, width, height, VantaUiTheme.RADIUS_MEDIUM,
                            VantaUiTheme.SURFACE_SELECTED);
                    graphics.fill(x, y + 5, x + 2, y + height - 5, accent);
                } else if (over || focused) {
                    int hoverSurface = buttonForeground != 0
                            ? VantaUiTheme.withAlpha(VantaUiTheme.WHITE,
                                    Math.round(focused ? stateTokens.controlFocusAlpha()
                                    : stateTokens.controlHoverAlpha() + 35.0f * hoverAmount))
                            : VantaUiTheme.withAlpha(VantaUiTheme.SURFACE_HOVER,
                                    Math.round(focused ? stateTokens.controlFocusAlpha()
                                    : stateTokens.controlHoverAlpha() + 35.0f * hoverAmount));
                    VantaUiTheme.fillSquircle(graphics, x, y, width, height,
                            VantaUiTheme.RADIUS_MEDIUM, hoverSurface);
                }
                if (focused) {
                    VantaUiTheme.outlineSquircle(graphics, x, y, width, height,
                            VantaUiTheme.RADIUS_MEDIUM, VantaUiTheme.withAlpha(accent, stateTokens.focusAlpha()));
                }
                int labelX = x, labelWidth = width;
                if (icon != null) {
                    drawIcon(graphics, icon, x + 10, y + (height - 16) / 2, 16, 16,
                            selected ? accent : over || focused
                                    ? VantaUiTheme.TEXT_PRIMARY : VantaUiTheme.TEXT_SECONDARY);
                    labelX = x + 34;
                    labelWidth = width - 42;
                }
                String shownLabel = fitText(font, label, Math.max(0, labelWidth - 8), UI_FONT);
                int textX = icon == null
                        ? x + Math.max(8, (width - textWidth(font, shownLabel, UI_FONT)) / 2) : labelX;
                int color = !enabled ? VantaUiTheme.TEXT_MUTED
                        : buttonForeground != 0 ? buttonForeground
                        : selected || over || focused ? VantaUiTheme.TEXT_PRIMARY
                        : VantaUiTheme.TEXT_SECONDARY;
                graphics.drawString(font, styledText(shownLabel, UI_FONT, true), textX,
                        y + (height - VantaUiTheme.TEXT_LINE_HEIGHT) / 2, color);
            }
            case TOGGLE -> {
                if (menuStyle) {
                    renderMenuToggle(graphics, font, accent);
                    break;
                }
                String shownLabel = fitText(font, label, Math.max(0, width - 52), UI_FONT);
                graphics.drawString(font, styledText(shownLabel, UI_FONT), x,
                        y + (height - VantaUiTheme.TEXT_LINE_HEIGHT) / 2,
                        enabled ? VantaUiTheme.TEXT_PRIMARY : VantaUiTheme.TEXT_MUTED);
                boolean value = toggleValue != null && toggleValue.getAsBoolean();
                int trackColor = !enabled ? VantaUiTheme.BORDER_SUBTLE
                        : value ? accent : VantaUiTheme.TRACK;
                int trackX = x + width - 42;
                int trackY = y + (height - 18) / 2;
                VantaUiTheme.fillSquircle(graphics, trackX, trackY, 38, 18, 9, trackColor);
                int knobX = trackX + 3 + Math.round(19.0f * animatedToggle);
                VantaUiTheme.fillSquircle(graphics, knobX, trackY + 3, 13, 12, 6, VantaUiTheme.WHITE);
                if (focused) VantaUiTheme.fillSquircle(graphics, trackX, y + height - 2, 38, 2, 1,
                        VantaUiTheme.withAlpha(accent, stateTokens.controlFocusIndicatorAlpha()));
            }
            case SLIDER -> {
                if (menuStyle) {
                    renderMenuSlider(graphics, font, accent, over);
                    break;
                }
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
                VantaUiTheme.fillSquircle(graphics, x, trackY, width, 4, 2, VantaUiTheme.TRACK);
                VantaUiTheme.fillSquircle(graphics, x, trackY, Math.max(4, knobX + 4 - x), 4, 2,
                        enabled ? accent : VantaUiTheme.TEXT_MUTED);
                VantaUiTheme.fillSquircle(graphics, knobX - 2, trackY - 4, 12, 12, 6,
                        !enabled ? VantaUiTheme.TEXT_MUTED
                                : focused || over ? VantaUiTheme.WHITE : 0xFFD5DFEC);
                if (focused) graphics.fill(x, y + height - 1, x + width, y + height, accent);
            }
            case DROPDOWN -> {
                if (menuStyle) {
                    renderMenuDropdown(graphics, font, accent);
                    break;
                }
                int controlHeight = VantaUiTheme.CONTROL_HEIGHT;
                int boxWidth = Math.min(width, Math.min(150, Math.max(72, width / 2)));
                int boxX = x + width - boxWidth;
                String dropdownLabel = fitText(font, label, Math.max(0, boxX - x - 12), UI_FONT);
                graphics.drawString(font, styledText(dropdownLabel, UI_FONT), x,
                        y + (controlHeight - VantaUiTheme.TEXT_LINE_HEIGHT) / 2,
                        enabled ? VantaUiTheme.TEXT_PRIMARY : VantaUiTheme.TEXT_MUTED);
                VantaUiTheme.fillSquircle(graphics, boxX, y + 2, x + width - boxX, controlHeight - 4,
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

                // Floating options render after the complete tree to escape parent scissors.
            }
            case TEXT_FIELD -> {
                if (!menuStyle) {
                    int fieldBackground = focused ? VantaUiTheme.INPUT_FOCUS_SURFACE : VantaUiTheme.INPUT_SURFACE;
                    VantaUiTheme.fillSquircle(graphics, x, y, width, height,
                            VantaUiTheme.RADIUS_MEDIUM, fieldBackground);
                    if (focused) VantaUiTheme.fillSquircle(graphics, x + 7, y + height - 2,
                            Math.max(0, width - 14), 2, 1, accent);
                }

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
                        menuStyle ? menuTextTop() : y + (height - VantaUiTheme.TEXT_LINE_HEIGHT) / 2, textColor);

                if (focused && caretVisible && width > 12) {
                    int caretX = Math.min(x + width - 5, x + 8 + cachedInputCursorWidth);
                    int caretH = menuStyle ? 16 : 12;
                    int caretY = y + (height - caretH) / 2;
                    graphics.fill(caretX, caretY, caretX + 1, caretY + caretH, accent);
                }
            }
        }

        if (focused && kind != Kind.SLIDER && kind != Kind.TOGGLE && kind != Kind.CANVAS
                && kind != Kind.DROPDOWN && kind != Kind.TEXT_FIELD) {
            graphics.fill(x, y + height - 1, x + width, y + height, accent);
        }
    }

    private boolean interactiveCanvas() {
        return action != null || pointerAction != null;
    }

    private void renderMenuToggle(GuiGraphics graphics, Font font, int accent) {
        boolean value = toggleValue != null && toggleValue.getAsBoolean();
        int trackWidth = 44;
        int trackHeight = 24;
        int trackX = x + width - trackWidth;
        int trackY = y + (height - trackHeight) / 2;
        String state = value ? "On" : "Off";
        int stateWidth = textWidth(font, state, MENU_FONT);
        graphics.drawString(font, styledText(state, MENU_FONT),
                trackX - 12 - stateWidth, menuTextTop(),
                !enabled ? VantaUiTheme.TEXT_MUTED : value ? 0xFFF2F4F8 : 0xFF8A909B);
        if (!label.isBlank()) {
            String shown = fitText(font, label, Math.max(0, width - trackWidth - stateWidth - 24), textFont);
            graphics.drawString(font, styledText(shown, textFont), x,
                    menuTextTop(),
                    enabled ? VantaUiTheme.TEXT_PRIMARY : VantaUiTheme.TEXT_MUTED);
        }
        int track = !enabled ? 0xFF1B1E24 : mixColor(0xFF2A2E36, accent, animatedToggle);
        int trackTop = enabled ? mixColor(0xFF343943, mixColor(accent, 0xFFFFFFFF, 0.14f), animatedToggle) : track;
        VantaUiTheme.fillSquircleGradient(graphics, trackX, trackY, trackWidth, trackHeight, 12, trackTop, track);
        int knobSize = 18;
        int knobX = trackX + 3 + Math.round((trackWidth - 6 - knobSize) * animatedToggle);
        VantaUiTheme.fillSquircle(graphics, knobX, trackY + 4, knobSize, knobSize, knobSize / 2, 0x44000000);
        VantaUiTheme.fillSquircle(graphics, knobX, trackY + 3, knobSize, knobSize, knobSize / 2, VantaUiTheme.WHITE);
        if (focused) {
            VantaUiTheme.outlineSquircle(graphics, trackX - 2, trackY - 2,
                    trackWidth + 4, trackHeight + 4, 14, VantaUiTheme.withAlpha(accent, 235));
        }
    }

    private int menuSliderTrackWidth() {
        return Math.max(8, width - 58);
    }

    private void renderMenuSlider(GuiGraphics graphics, Font font, int accent, boolean over) {
        double value = sliderValue == null ? sliderMinimum : sliderValue.getAsDouble();
        if (!Double.isFinite(value)) value = sliderMinimum;
        value = clamp(value, sliderMinimum, sliderMaximum);
        double range = sliderMaximum - sliderMinimum;
        double normalized = range <= 0.0 ? 0.0 : clamp((value - sliderMinimum) / range, 0.0, 1.0);
        if (Double.compare(value, cachedSliderValue) != 0) {
            cachedSliderValue = value;
            cachedSliderValueText = formatValue(value);
        }
        int trackWidth = menuSliderTrackWidth();
        int trackY = y + height / 2 - 3;
        int fill = (int) Math.round(trackWidth * normalized);
        VantaUiTheme.fillSquircle(graphics, x, trackY, trackWidth, 6, 3, 0xFF2A2E36);
        if (fill > 0) {
            VantaUiTheme.fillSquircle(graphics, x, trackY, Math.max(6, fill), 6, 3,
                    enabled ? accent : VantaUiTheme.TEXT_MUTED);
        }
        int knobSize = over || focused ? 18 : 16;
        VantaUiTheme.fillSquircle(graphics, x + fill - knobSize / 2, y + height / 2 - knobSize / 2 + 1,
                knobSize, knobSize, knobSize / 2, 0x44000000);
        VantaUiTheme.fillSquircle(graphics, x + fill - knobSize / 2, y + height / 2 - knobSize / 2,
                knobSize, knobSize, knobSize / 2, VantaUiTheme.WHITE);
        int valueWidth = textWidth(font, cachedSliderValueText, MENU_FONT);
        graphics.drawString(font, styledText(cachedSliderValueText, MENU_FONT),
                x + width - valueWidth, menuTextTop(),
                enabled ? 0xFFF2F4F8 : VantaUiTheme.TEXT_MUTED);
    }

    /** Size of the menu fonts (vanta:medium, :small, :heading); -1 for every other font. */
    private static int menuFontSize(Identifier fontId) {
        if (fontId == null || !"vanta".equals(fontId.getNamespace())) return -1;
        return switch (fontId.getPath()) {
            case "medium" -> 13;
            case "small" -> 11;
            case "heading" -> 26;
            case "brand" -> 28;
            default -> -1;
        };
    }

    /** Top edge that vertically centres 13px menu text (baseline sits 7 units below the top). */
    private int menuTextTop() {
        return y + height / 2 - 2;
    }

    private void renderMenuDropdown(GuiGraphics graphics, Font font, int accent) {
        boolean open = dropdownOpen;
        int fill = open || hovered ? 0xFF22262E : 0xFF1B1E25;
        VantaUiTheme.fillSquircle(graphics, x, y, width, VantaUiTheme.CONTROL_HEIGHT, 10, fill);
        VantaUiTheme.outlineSquircle(graphics, x, y, width, VantaUiTheme.CONTROL_HEIGHT, 10,
                focused || open ? VantaUiTheme.withAlpha(accent, 235) : 0xFF2E333D);
        String selectedText = dropdownValue == null ? "" : dropdownValue.get();
        String shown = fitText(font, selectedText == null ? "" : selectedText,
                Math.max(0, width - 38), MENU_FONT);
        graphics.drawString(font, styledText(shown, MENU_FONT), x + 14,
                y + VantaUiTheme.CONTROL_HEIGHT / 2 - 2,
                enabled ? 0xFFF2F4F8 : VantaUiTheme.TEXT_MUTED);
        int arrowX = x + width - 18;
        int arrowY = y + VantaUiTheme.CONTROL_HEIGHT / 2;
        int direction = open ? -1 : 1;
        line(graphics, arrowX - 3, arrowY - direction * 1, arrowX, arrowY + direction * 2,
                VantaUiTheme.TEXT_SECONDARY, 1, 1);
        line(graphics, arrowX, arrowY + direction * 2, arrowX + 3, arrowY - direction * 1,
                VantaUiTheme.TEXT_SECONDARY, 1, 1);
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
            case VISUALS->{
                // Eye silhouette and pupil: a clear visual-category metaphor.
                boldLine(g,x+1*sx,y+8*sy,x+4*sx,y+4*sy,color,2*sx,2*sy);
                boldLine(g,x+4*sx,y+4*sy,x+8*sx,y+2*sy,color,2*sx,2*sy);
                boldLine(g,x+8*sx,y+2*sy,x+12*sx,y+4*sy,color,2*sx,2*sy);
                boldLine(g,x+12*sx,y+4*sy,x+15*sx,y+8*sy,color,2*sx,2*sy);
                boldLine(g,x+15*sx,y+8*sy,x+12*sx,y+12*sy,color,2*sx,2*sy);
                boldLine(g,x+12*sx,y+12*sy,x+8*sx,y+14*sy,color,2*sx,2*sy);
                boldLine(g,x+8*sx,y+14*sy,x+4*sx,y+12*sy,color,2*sx,2*sy);
                boldLine(g,x+4*sx,y+12*sy,x+1*sx,y+8*sy,color,2*sx,2*sy);
                boldRing(g,x+8*sx,y+8*sy,2.7f*sx,color,1.8f*sx);
                g.fill(Math.round(x+7*sx),Math.round(y+7*sy),Math.round(x+9*sx),Math.round(y+9*sy),color);
            }
            case CONTROLS->{for(int i=0;i<3;i++)line(g,x+2*sx,y+(4+i*4)*sy,x+14*sx,y+(4+i*4)*sy,color,sx,sy);ring(g,x+6*sx,y+4*sy,1.8f*sx,color);ring(g,x+11*sx,y+8*sy,1.8f*sx,color);ring(g,x+5*sx,y+12*sy,1.8f*sx,color);}
            case DISPLAY->{rect(g,x+1.5f*sx,y+2*sy,13*sx,9*sy,color,sx,sy);line(g,x+8*sx,y+11*sy,x+8*sx,y+14*sy,color,sx,sy);line(g,x+4*sx,y+14*sy,x+12*sx,y+14*sy,color,sx,sy);}
            case KEYBOARD->{rect(g,x+1*sx,y+3*sy,14*sx,10*sy,color,sx,sy);for(int row=0;row<2;row++)for(int col=0;col<5;col++){float px=x+(3+col*2.5f)*sx,py=y+(5+row*3)*sy;g.fill(Math.round(px),Math.round(py),Math.round(px+sx),Math.round(py+sy),color);}}
            case SEARCH->{ring(g,x+6.5f*sx,y+6.5f*sy,4.3f*sx,color);line(g,x+9.7f*sx,y+9.7f*sy,x+14*sx,y+14*sy,color,sx,sy);}
            case CROSSHAIR->{line(g,x+8*sx,y+1*sy,x+8*sx,y+5*sy,color,sx,sy);line(g,x+8*sx,y+11*sy,x+8*sx,y+15*sy,color,sx,sy);line(g,x+1*sx,y+8*sy,x+5*sx,y+8*sy,color,sx,sy);line(g,x+11*sx,y+8*sy,x+15*sx,y+8*sy,color,sx,sy);ring(g,x+8*sx,y+8*sy,2*sx,color);}
            case HUD->{
                // Four heavier screen-corner brackets with a clean central opening.
                boldLine(g,x+2*sx,y+6*sy,x+2*sx,y+2*sy,color,2*sx,2*sy);
                boldLine(g,x+2*sx,y+2*sy,x+6*sx,y+2*sy,color,2*sx,2*sy);
                boldLine(g,x+10*sx,y+2*sy,x+14*sx,y+2*sy,color,2*sx,2*sy);
                boldLine(g,x+14*sx,y+2*sy,x+14*sx,y+6*sy,color,2*sx,2*sy);
                boldLine(g,x+2*sx,y+10*sy,x+2*sx,y+14*sy,color,2*sx,2*sy);
                boldLine(g,x+2*sx,y+14*sy,x+6*sx,y+14*sy,color,2*sx,2*sy);
                boldLine(g,x+10*sx,y+14*sy,x+14*sx,y+14*sy,color,2*sx,2*sy);
                boldLine(g,x+14*sx,y+10*sy,x+14*sx,y+14*sy,color,2*sx,2*sy);
            }
            case SPARK->{line(g,x+8*sx,y+1*sy,x+9.5f*sx,y+6.5f*sy,color,sx,sy);line(g,x+9.5f*sx,y+6.5f*sy,x+15*sx,y+8*sy,color,sx,sy);line(g,x+15*sx,y+8*sy,x+9.5f*sx,y+9.5f*sy,color,sx,sy);line(g,x+9.5f*sx,y+9.5f*sy,x+8*sx,y+15*sy,color,sx,sy);line(g,x+8*sx,y+15*sy,x+6.5f*sx,y+9.5f*sy,color,sx,sy);line(g,x+6.5f*sx,y+9.5f*sy,x+1*sx,y+8*sy,color,sx,sy);line(g,x+1*sx,y+8*sy,x+6.5f*sx,y+6.5f*sy,color,sx,sy);line(g,x+6.5f*sx,y+6.5f*sy,x+8*sx,y+1*sy,color,sx,sy);}
            case RESET->{line(g,x+13*sx,y+2*sy,x+10*sx,y+4*sy,color,sx,sy);line(g,x+13*sx,y+2*sy,x+15*sx,y+5*sy,color,sx,sy);line(g,x+12*sx,y+3*sy,x+6*sx,y+2*sy,color,sx,sy);line(g,x+6*sx,y+2*sy,x+2*sx,y+6*sy,color,sx,sy);line(g,x+2*sx,y+6*sy,x+3*sx,y+12*sy,color,sx,sy);line(g,x+3*sx,y+12*sy,x+8*sx,y+14*sy,color,sx,sy);line(g,x+8*sx,y+14*sy,x+12*sx,y+11*sy,color,sx,sy);}
            case COMBAT -> {
                // Bold target reticle with a solid center: unmistakable at small sizes.
                boldLine(g,x+8*sx,y+1*sy,x+8*sx,y+4*sy,color,2*sx,2*sy);
                boldLine(g,x+8*sx,y+12*sy,x+8*sx,y+15*sy,color,2*sx,2*sy);
                boldLine(g,x+1*sx,y+8*sy,x+4*sx,y+8*sy,color,2*sx,2*sy);
                boldLine(g,x+12*sx,y+8*sy,x+15*sx,y+8*sy,color,2*sx,2*sy);
                boldRing(g,x+8*sx,y+8*sy,4.1f*sx,color,1.8f*sx);
                g.fill(Math.round(x+6*sx),Math.round(y+6*sy),Math.round(x+10*sx),Math.round(y+10*sy),color);
            }
            case MISC -> {
                // Three weighted adjustment tracks with offset circular handles.
                boldLine(g,x+2*sx,y+4*sy,x+14*sx,y+4*sy,color,1.8f*sx,1.8f*sy);
                boldLine(g,x+2*sx,y+8*sy,x+14*sx,y+8*sy,color,1.8f*sx,1.8f*sy);
                boldLine(g,x+2*sx,y+12*sy,x+14*sx,y+12*sy,color,1.8f*sx,1.8f*sy);
                g.fill(Math.round(x+5*sx),Math.round(y+2*sy),Math.round(x+9*sx),Math.round(y+6*sy),color);
                g.fill(Math.round(x+10*sx),Math.round(y+6*sy),Math.round(x+14*sx),Math.round(y+10*sy),color);
                g.fill(Math.round(x+3*sx),Math.round(y+10*sy),Math.round(x+7*sx),Math.round(y+14*sy),color);
            }
        }
    }
    private static void boldLine(GuiGraphics g,float x1,float y1,float x2,float y2,int color,float thicknessX,float thicknessY) {
        int steps=Math.max(1,Math.round(Math.max(Math.abs(x2-x1),Math.abs(y2-y1))*3));
        int halfX=Math.max(1,Math.round(thicknessX*0.5f)),halfY=Math.max(1,Math.round(thicknessY*0.5f));
        for(int i=0;i<=steps;i++){float t=i/(float)steps;int px=Math.round(x1+(x2-x1)*t),py=Math.round(y1+(y2-y1)*t);g.fill(px-halfX,py-halfY,px+halfX+1,py+halfY+1,color);}
    }
    private static void boldRing(GuiGraphics g,float cx,float cy,float radius,int color,float thickness) {
        float px=cx+radius,py=cy;for(int a=10;a<=360;a+=10){double rad=Math.toRadians(a);float nx=cx+(float)Math.cos(rad)*radius,ny=cy+(float)Math.sin(rad)*radius;boldLine(g,px,py,nx,ny,color,thickness,thickness);px=nx;py=ny;}
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
    /** Finds the open dropdown popup in this subtree, if any. */
    DropdownPopupBounds dropdownPopupBounds(int viewportWidth, int viewportHeight) {
        if (!visible || width <= 0 || height <= 0) return null;
        if (kind == Kind.DROPDOWN && dropdownOpen) {
            int boxWidth = menuStyle ? width
                    : Math.min(width, Math.min(150, Math.max(72, width / 2)));
            int popupWidth = Math.min(boxWidth, Math.max(1, viewportWidth - 16));
            int visibleOptions = Math.max(1, Math.min(
                    Math.min(8, dropdownOptions.size()), Math.max(1, (viewportHeight - 16) / 22)));
            int popupHeight = visibleOptions * 22;
            int firstOption = clamp(dropdownScrollOffset, 0,
                    Math.max(0, dropdownOptions.size() - visibleOptions));
            int popupX = Math.max(8, Math.min(
                    Math.max(8, viewportWidth - popupWidth - 8),
                    x + width - popupWidth));
            int belowTop = y + VantaUiTheme.CONTROL_HEIGHT;
            int belowSpace = viewportHeight - belowTop - 8;
            int aboveSpace = y - 8;
            int desiredTop = belowSpace >= popupHeight || belowSpace >= aboveSpace
                    ? belowTop : y - popupHeight;
            int maximumTop = Math.max(8, viewportHeight - popupHeight - 8);
            int popupY = Math.max(8, Math.min(maximumTop, desiredTop));
            return new DropdownPopupBounds(
                    popupX, popupY, popupWidth, popupHeight, firstOption, visibleOptions);
        }
        for (int index = children.size() - 1; index >= 0; index--) {
            DropdownPopupBounds bounds = children.get(index)
                    .dropdownPopupBounds(viewportWidth, viewportHeight);
            if (bounds != null) return bounds;
        }
        return null;
    }

    VantaUiNode dropdownPopupAt(double mouseX, double mouseY,
                                int viewportWidth, int viewportHeight) {
        if (!visible) return null;
        if (kind == Kind.DROPDOWN && dropdownOpen) {
            DropdownPopupBounds bounds = dropdownPopupBounds(viewportWidth, viewportHeight);
            if (bounds != null && mouseX >= bounds.x() && mouseX < bounds.x() + bounds.width()
                    && mouseY >= bounds.y() && mouseY < bounds.y() + bounds.height()) {
                return this;
            }
        }
        for (int index = children.size() - 1; index >= 0; index--) {
            VantaUiNode target = children.get(index)
                    .dropdownPopupAt(mouseX, mouseY, viewportWidth, viewportHeight);
            if (target != null) return target;
        }
        return null;
    }

    int dropdownOptionAt(double mouseY, int viewportWidth, int viewportHeight) {
        DropdownPopupBounds bounds = dropdownPopupBounds(viewportWidth, viewportHeight);
        if (bounds == null || mouseY < bounds.y() || mouseY >= bounds.y() + bounds.height()) return -1;
        int visibleIndex = (int) ((mouseY - bounds.y()) / 22.0);
        int optionIndex = bounds.firstOption() + visibleIndex;
        return optionIndex >= 0 && optionIndex < dropdownOptions.size() ? optionIndex : -1;
    }

    void selectDropdownOption(int optionIndex) {
        if (kind != Kind.DROPDOWN || !dropdownOpen) return;
        if (optionIndex >= 0 && optionIndex < dropdownOptions.size()) {
            dropdownHighlight = optionIndex;
            if (dropdownSetter != null) dropdownSetter.accept(dropdownOptions.get(optionIndex));
        }
        dropdownOpen = false;
        dropdownScrollOffset = 0;
    }

    boolean scrollDropdownAt(double mouseX, double mouseY, double amount,
                             int viewportWidth, int viewportHeight) {
        if (!visible) return false;
        if (kind == Kind.DROPDOWN && dropdownOpen) {
            DropdownPopupBounds bounds = dropdownPopupBounds(viewportWidth, viewportHeight);
            if (bounds != null && mouseX >= bounds.x() && mouseX < bounds.x() + bounds.width()
                    && mouseY >= bounds.y() && mouseY < bounds.y() + bounds.height()) {
                int maxOffset = Math.max(0, dropdownOptions.size() - bounds.visibleOptions());
                dropdownScrollOffset = clamp(
                        dropdownScrollOffset - (int) Math.round(amount), 0, maxOffset);
                dropdownHighlight = clamp(dropdownHighlight, dropdownScrollOffset,
                        Math.max(dropdownScrollOffset,
                                dropdownScrollOffset + bounds.visibleOptions() - 1));
                return true;
            }
        }
        for (int index = children.size() - 1; index >= 0; index--) {
            if (children.get(index).scrollDropdownAt(
                    mouseX, mouseY, amount, viewportWidth, viewportHeight)) return true;
        }
        return false;
    }

    boolean renderDropdownPopup(GuiGraphics graphics, Font font, VantaUiTheme theme,
                                int mouseX, int mouseY, int viewportWidth, int viewportHeight) {
        if (!visible) return false;
        if (kind == Kind.DROPDOWN && dropdownOpen) {
            DropdownPopupBounds bounds = dropdownPopupBounds(viewportWidth, viewportHeight);
            if (bounds == null) return false;
            int accent = theme.accent();
            VantaUiTheme.fillSquircle(graphics, bounds.x() + 2, bounds.y() + 3,
                    bounds.width(), bounds.height(), VantaUiTheme.RADIUS_MEDIUM, 0x55000000);
            VantaUiTheme.fillSquircle(graphics, bounds.x(), bounds.y(),
                    bounds.width(), bounds.height(), VantaUiTheme.RADIUS_MEDIUM,
                    menuStyle ? 0xFF181B21 : VantaUiTheme.applyPanelOpacity(VantaUiTheme.PANEL_RAISED));
            VantaUiTheme.outlineSquircle(graphics, bounds.x(), bounds.y(),
                    bounds.width(), bounds.height(), VantaUiTheme.RADIUS_MEDIUM,
                    menuStyle ? 0xFF2E333D : VantaUiTheme.BORDER);
            String selected = dropdownValue == null ? "" : dropdownValue.get();
            for (int visibleIndex = 0; visibleIndex < bounds.visibleOptions(); visibleIndex++) {
                int optionIndex = bounds.firstOption() + visibleIndex;
                int optionY = bounds.y() + visibleIndex * 22;
                String option = dropdownOptions.get(optionIndex);
                boolean hoveredOption = mouseX >= bounds.x() && mouseX < bounds.x() + bounds.width()
                        && mouseY >= optionY && mouseY < optionY + 22;
                boolean highlighted = hoveredOption || optionIndex == dropdownHighlight;
                if (highlighted) {
                    VantaUiTheme.fillSquircle(graphics,
                            bounds.x() + 2, optionY + 1, bounds.width() - 4, 20,
                            VantaUiTheme.RADIUS_SMALL, VantaUiTheme.withAlpha(accent, 58));
                }
                if (option.equals(selected)) {
                    graphics.fill(bounds.x() + 1, optionY + 3,
                            bounds.x() + 3, optionY + 19, accent);
                }
                String optionText = fitText(font, option,
                        Math.max(0, bounds.width() - 18), UI_FONT);
                graphics.drawString(font, styledText(optionText, UI_FONT),
                        bounds.x() + 9, menuStyle ? optionY + 9 : optionY + 7,
                        highlighted ? VantaUiTheme.TEXT_PRIMARY : VantaUiTheme.TEXT_SECONDARY);
            }
            if (dropdownOptions.size() > bounds.visibleOptions()) {
                int trackX = bounds.x() + bounds.width() - 4;
                int trackY = bounds.y() + 4;
                int trackHeight = Math.max(1, bounds.height() - 8);
                int maximumOffset = dropdownOptions.size() - bounds.visibleOptions();
                int thumbHeight = Math.max(12,
                        trackHeight * bounds.visibleOptions() / dropdownOptions.size());
                int thumbTravel = Math.max(0, trackHeight - thumbHeight);
                int thumbTop = trackY + (maximumOffset == 0 ? 0
                        : thumbTravel * bounds.firstOption() / maximumOffset);
                graphics.fill(trackX, trackY, trackX + 2, trackY + trackHeight,
                        VantaUiTheme.BORDER_SUBTLE);
                graphics.fill(trackX, thumbTop, trackX + 2, thumbTop + thumbHeight,
                        VantaUiTheme.TEXT_MUTED);
            }
            return true;
        }
        for (int index = children.size() - 1; index >= 0; index--) {
            if (children.get(index).renderDropdownPopup(
                    graphics, font, theme, mouseX, mouseY, viewportWidth, viewportHeight)) return true;
        }
        return false;
    }

    private void revealDropdownHighlight(int visibleOptions) {
        int shown = Math.max(1, visibleOptions);
        if (dropdownHighlight < dropdownScrollOffset) {
            dropdownScrollOffset = dropdownHighlight;
        } else if (dropdownHighlight >= dropdownScrollOffset + shown) {
            dropdownScrollOffset = dropdownHighlight - shown + 1;
        }
        dropdownScrollOffset = clamp(dropdownScrollOffset, 0,
                Math.max(0, dropdownOptions.size() - shown));
    }

    private int scrollViewportHeight() {
        return Math.max(1, height - padding * 2);
    }

    private int scrollMaxOffset() {
        return Math.max(0, contentHeight - scrollViewportHeight());
    }

    private int scrollThumbHeight() {
        int viewportHeight = scrollViewportHeight();
        if (contentHeight <= viewportHeight) return viewportHeight;
        return Math.min(viewportHeight,
                Math.max(12, viewportHeight * viewportHeight / Math.max(1, contentHeight)));
    }

    private int scrollThumbTop() {
        int maxScroll = scrollMaxOffset();
        int trackTravel = Math.max(0, scrollViewportHeight() - scrollThumbHeight());
        return y + padding + (maxScroll == 0 ? 0 : trackTravel * scrollOffset / maxScroll);
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int innerHeight = scrollViewportHeight();
        if (scrollMaxOffset() <= 0 || innerHeight <= 8) return;
        int trackX = x + width - 3;
        int trackTop = y + padding;
        int thumbTop = scrollThumbTop();
        int thumbHeight = scrollThumbHeight();
        graphics.fill(trackX, trackTop, trackX + 2, trackTop + innerHeight, VantaUiTheme.BORDER_SUBTLE);
        graphics.fill(trackX, thumbTop, trackX + 2, thumbTop + thumbHeight, VantaUiTheme.TEXT_MUTED);
    }

    /** Starts pointer capture only when the visible scrollbar thumb is grabbed. */
    VantaUiNode beginScrollbarDrag(double mouseX, double mouseY) {
        if (!visible || !enabled || !contains(mouseX, mouseY)) return null;
        // Children are clipped to the content viewport, but this node's own scrollbar
        // is intentionally drawn on the viewport's right edge outside that clip.
        if (containsClip(mouseX, mouseY)) {
            for (int index = children.size() - 1; index >= 0; index--) {
                VantaUiNode target = children.get(index).beginScrollbarDrag(mouseX, mouseY);
                if (target != null) return target;
            }
        }
        int maxScroll = scrollMaxOffset();
        int viewportHeight = scrollViewportHeight();
        if (!scrollable || maxScroll <= 0 || viewportHeight <= 8
                || viewportHeight - scrollThumbHeight() <= 0) return null;

        int trackX = x + width - 3;
        if (mouseX < trackX - 5 || mouseX > trackX + 7
                || mouseY < y + padding || mouseY >= y + height - padding) {
            return null;
        }
        int thumbTop = scrollThumbTop();
        int thumbHeight = scrollThumbHeight();
        if (mouseY < thumbTop || mouseY >= thumbTop + thumbHeight) return null;

        scrollDragPointerOffset = (int) Math.round(mouseY - thumbTop);
        return this;
    }

    public void dragScrollbar(double mouseY) {
        int maxScroll = scrollMaxOffset();
        int trackTravel = scrollViewportHeight() - scrollThumbHeight();
        if (!scrollable || maxScroll <= 0 || trackTravel <= 0) return;
        double normalized = (mouseY - (y + padding) - scrollDragPointerOffset) / trackTravel;
        scrollOffset = clamp((int) Math.round(normalized * maxScroll), 0, maxScroll);
    }

    public void endScrollbarDrag() {
        scrollDragPointerOffset = 0;
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
        if (scrollable) {
            int start = visibleChildStart();
            int end = visibleChildEnd(start);
            int left = x + padding;
            int right = x + width - padding;
            for (int i = start; i < end; i++) {
                VantaUiNode child = children.get(i);
                if (child.x + child.width > left && child.x < right) child.tick();
            }
        } else {
            for (VantaUiNode child : children) child.tick();
        }
    }

    /**
     * Scrollable page children are laid out in vertical order. Find the contiguous
     * vertical range intersecting the viewport with binary search instead of testing
     * every row during render, animation, ticking and pointer hit-testing.
     */
    private int firstVisibleChildIndex(int viewportTop) {
        int low = 0;
        int high = children.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            VantaUiNode child = children.get(middle);
            if (child.y + child.height <= viewportTop) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private int visibleChildEndIndex(int viewportBottom, int start) {
        int low = start;
        int high = children.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (children.get(middle).y < viewportBottom) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private int visibleChildStart() {
        return firstVisibleChildIndex(y + padding);
    }

    private int visibleChildEnd(int start) {
        return visibleChildEndIndex(y + height - padding, start);
    }

    void animate(boolean animationsEnabled, float hoverFactor, float toggleFactor,
                 float deltaSeconds) {
        if (!visible) return;
        float hoverTarget = hovered ? 1.0f : 0.0f;
        if (!animationsEnabled) {
            hoverAmount = hoverTarget;
        } else if (Math.abs(hoverTarget - hoverAmount) >= 0.002f) {
            hoverAmount += (hoverTarget - hoverAmount) * hoverFactor;
            if (Math.abs(hoverTarget - hoverAmount) < 0.002f) hoverAmount = hoverTarget;
        }
        if (kind == Kind.PROGRESS) {
            progressPhase = VantaUiMotion.advancePingPongPhase(
                    progressPhase, deltaSeconds, 0.9f, animationsEnabled);
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
        if (scrollable) {
            int start = visibleChildStart();
            int end = visibleChildEnd(start);
            int left = x + padding;
            int right = x + width - padding;
            for (int i = start; i < end; i++) {
                VantaUiNode child = children.get(i);
                if (child.x + child.width > left && child.x < right) {
                    child.animate(animationsEnabled, hoverFactor, toggleFactor, deltaSeconds);
                }
            }
        } else {
            for (VantaUiNode child : children) {
                child.animate(animationsEnabled, hoverFactor, toggleFactor, deltaSeconds);
            }
        }
    }

    /**
     * Scroll containers may hold large retained lists. Nodes fully outside the viewport
     * need no draw submission or per-frame animation until they scroll into view.
     */
    private boolean intersectsScrollViewport(VantaUiNode child) {
        int left = x + padding;
        int top = y + padding;
        int right = x + width - padding;
        int bottom = y + height - padding;
        return child.x + child.width > left && child.x < right
                && child.y + child.height > top && child.y < bottom;
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
        if (scrollable) {
            int start = visibleChildStart();
            int end = visibleChildEnd(start);
            for (int i = end - 1; i >= start; i--) {
                VantaUiNode child = children.get(i);
                if (child.x + child.width <= x + padding || child.x >= x + width - padding) continue;
                VantaUiNode hit = child.hitTest(mouseX, mouseY);
                if (hit != null) return hit;
            }
        } else {
            for (int i = children.size() - 1; i >= 0; i--) {
                VantaUiNode hit = children.get(i).hitTest(mouseX, mouseY);
                if (hit != null) return hit;
            }
        }
        if (isInteractive() && enabled) return this;
        return null;
    }

    void setHovered(boolean value) { hovered = value; }
    public void setFocused(boolean value) {
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
            case CANVAS -> {
                if (pointerAction != null) {
                    pointerAction.click((int) Math.floor(mouseX - x), (int) Math.floor(mouseY - y), width, height);
                } else if (action != null) {
                    action.run();
                }
            }
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
                        dropdownScrollOffset = 0;
                    } else {
                        dropdownHighlight = Math.max(0, selectedDropdownIndex());
                        dropdownOpen = true;
                        dropdownScrollOffset = Math.max(0, dropdownHighlight - 7);
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

    public void drag(double mouseX) {
        if (kind == Kind.SLIDER && enabled) updateSlider(mouseX);
    }

    void activate() {
        if (!enabled) return;
        switch (kind) {
            case BUTTON -> { if (action != null) action.run(); }
            case CANVAS -> { if (action != null) action.run(); }
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
                    dropdownScrollOffset = 0;
                } else {
                    dropdownHighlight = Math.max(0, selectedDropdownIndex());
                    dropdownOpen = true;
                    dropdownScrollOffset = Math.max(0, dropdownHighlight - 7);
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
                revealDropdownHighlight(8);
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
        double span = menuStyle ? menuSliderTrackWidth() : width;
        double normalized = clamp((mouseX - x) / span, 0.0, 1.0);
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
        dropdownScrollOffset = 0;
        return true;
    }

    boolean closeDropdownsExcept(VantaUiNode keepOpen) {
        boolean changed = false;
        if (kind == Kind.DROPDOWN && this != keepOpen && dropdownOpen) {
            dropdownOpen = false;
            dropdownScrollOffset = 0;
            changed = true;
        }
        for (VantaUiNode child : children) {
            changed |= child.closeDropdownsExcept(keepOpen);
        }
        return changed;
    }

    public void collectFocusable(List<VantaUiNode> result) {
        if (!visible || !enabled) return;
        if (isInteractive()) result.add(this);
        for (VantaUiNode child : children) child.collectFocusable(result);
    }

    private String currentInput() {
        String value = inputValue == null ? "" : inputValue.get();
        return value == null ? "" : value;
    }

    static Component styledText(String value, Identifier fontId) {
        return VantaTypography.styledText(value, fontId, false);
    }
    static Component styledText(String value, Identifier fontId, boolean bold) {
        return VantaTypography.styledText(value, fontId, bold);
    }
    static int textWidth(Font font, String value, Identifier fontId) {
        return VantaTypography.textWidth(font, value, fontId);
    }
    static String fitText(Font font, String value, int maximumWidth, Identifier fontId) {
        return VantaTypography.fitText(font, value, maximumWidth, fontId);
    }

    String currentText() {
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

    static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String formatValue(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.00001) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
