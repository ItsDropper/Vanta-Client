package name.vantaclient.client.gui.compose.components;

import name.vantaclient.client.gui.framework.VantaState;
import name.vantaclient.client.gui.framework.VantaUiComponent;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiTheme;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * Reusable, theme-aware components for every retained Vanta screen.
 *
 * These factories compose the same retained nodes used by screen layouts;
 * they do not own domain state, persistence, screen navigation or rendering.
 */
public final class VantaComponents {
    public enum Surface { PANEL, ELEVATED, FIELD, POPOVER }
    public enum Tone { NEUTRAL, ACCENT, SUCCESS, WARNING, DANGER }
    public enum ButtonVariant { PRIMARY, SECONDARY, QUIET, DANGER }

    private VantaComponents() {}

    public static VantaUiNode surface(Surface surface, int gap, int padding) {
        Objects.requireNonNull(surface, "surface");
        int background = switch (surface) {
            case PANEL -> VantaUiTheme.PANEL;
            case ELEVATED -> VantaUiTheme.PANEL_RAISED;
            case FIELD -> VantaUiTheme.INPUT_SURFACE;
            case POPOVER -> VantaUiTheme.TOOLTIP_SURFACE;
        };
        return VantaUiNode.column(gap, padding).background(background);
    }

    /** Standard bordered panel used for content groups and inspector sections. */
    public static VantaUiNode panel(Surface surface, int gap, int padding) {
        return panel(surface, VantaUiNode.Direction.COLUMN, gap, padding);
    }

    /** Bordered panel with an explicit layout direction for toolbars and inline groups. */
    public static VantaUiNode panel(Surface surface, VantaUiNode.Direction direction,
                                    int gap, int padding) {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(direction, "direction");
        int background = switch (surface) {
            case PANEL -> VantaUiTheme.PANEL;
            case ELEVATED -> VantaUiTheme.PANEL_RAISED;
            case FIELD -> VantaUiTheme.INPUT_SURFACE;
            case POPOVER -> VantaUiTheme.TOOLTIP_SURFACE;
        };
        VantaUiNode node = switch (direction) {
            case ROW -> VantaUiNode.row(gap, padding);
            case COLUMN -> VantaUiNode.column(gap, padding);
            case STACK -> VantaUiNode.stack(padding).gap(gap);
        };
        return node.background(background).radius(VantaUiTheme.RADIUS_PANEL)
                .border(VantaUiTheme.BORDER_SUBTLE);
    }

    /** One-pixel separator that participates in normal layout. */
    public static VantaUiNode divider() {
        return VantaUiNode.spacer(1).height(1).background(VantaUiTheme.BORDER_SUBTLE);
    }

    /** Compact semantic status label with a consistent tone palette. */
    public static VantaUiNode badge(String label, Tone tone) {
        Objects.requireNonNull(tone, "tone");
        int foreground = switch (tone) {
            case NEUTRAL -> VantaUiTheme.TEXT_SECONDARY;
            case ACCENT -> VantaClientAccent.current();
            case SUCCESS -> VantaUiTheme.STATUS_SUCCESS;
            case WARNING -> VantaUiTheme.STATUS_WARNING;
            case DANGER -> VantaUiTheme.STATUS_DANGER;
        };
        int background = switch (tone) {
            case NEUTRAL -> VantaUiTheme.TRACK;
            case ACCENT -> VantaUiTheme.withAlpha(VantaClientAccent.current(), 38);
            case SUCCESS -> VantaUiTheme.withAlpha(VantaUiTheme.STATUS_SUCCESS, 34);
            case WARNING -> VantaUiTheme.withAlpha(VantaUiTheme.STATUS_WARNING, 34);
            case DANGER -> VantaUiTheme.withAlpha(VantaUiTheme.STATUS_DANGER, 34);
        };
        return VantaUiNode.row(0, 7).height(22)
                .align(VantaUiNode.Alignment.CENTER)
                .background(background).radius(VantaUiTheme.RADIUS_PILL)
                .add(VantaUiNode.text(label == null ? "" : label, foreground));
    }

    /** Standard action button variants; action ownership remains with the caller. */
    public static VantaUiNode actionButton(String label, VantaUiNode.Icon icon,
                                           ButtonVariant variant, Runnable action) {
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(action, "action");
        VantaUiNode node = VantaUiNode.button(label, icon, action)
                .height(VantaUiTheme.CONTROL_HEIGHT)
                .radius(VantaUiTheme.RADIUS_MEDIUM);
        switch (variant) {
            case PRIMARY -> node.background(VantaClientAccent.current())
                    .buttonForeground(name.vantaclient.client.gui.compose.theme.VantaThemeFactory
                            .create().palette().accentForeground());
            case SECONDARY -> node.background(VantaUiTheme.SURFACE)
                    .border(VantaUiTheme.BUTTON_OUTLINE);
            case QUIET -> node.background(0);
            case DANGER -> node.background(VantaUiTheme.withAlpha(VantaUiTheme.STATUS_DANGER, 46))
                    .border(VantaUiTheme.STATUS_DANGER)
                    .buttonForeground(VantaUiTheme.STATUS_DANGER);
        }
        return node;
    }

    /** Consistent page/section heading with optional eyebrow and supporting copy. */
    public static VantaUiNode sectionHeader(String eyebrow, String title, String detail) {
        VantaUiNode header = VantaUiNode.column(4, 0);
        if (eyebrow != null && !eyebrow.isBlank()) {
            header.add(VantaUiNode.eyebrow(eyebrow));
        }
        header.add(VantaUiNode.title(Objects.requireNonNull(title, "title")));
        if (detail != null && !detail.isBlank()) {
            header.add(VantaUiNode.paragraph(detail, VantaUiTheme.TEXT_SECONDARY, 2));
        }
        return header;
    }

    /** Label/control row used by settings, inspectors and integration forms. */
    public static VantaUiNode settingRow(String title, String description,
                                         VantaUiNode control, int controlWidth) {
        Objects.requireNonNull(control, "control");
        boolean hasDescription = description != null && !description.isBlank();
        VantaUiNode row = VantaUiNode.row(12, 10)
                .height(hasDescription ? 58 : 42)
                .align(VantaUiNode.Alignment.CENTER);
        VantaUiNode labels = VantaUiNode.column(3, 0).flex(1);
        String resolvedTitle = Objects.requireNonNull(title, "title");
        if (!containsVisibleLabel(control, resolvedTitle)) {
            labels.add(VantaUiNode.text(resolvedTitle));
        }
        if (hasDescription) {
            labels.add(VantaUiNode.paragraph(description, VantaUiTheme.TEXT_MUTED, 2));
        }
        if (!labels.children().isEmpty()) row.add(labels);
        row.add(controlWidth > 0 ? control.width(controlWidth) : control);
        return row;
    }

    private static boolean containsVisibleLabel(VantaUiComponent component, String label) {
        if (label.equals(component.semantics().label())) return true;
        for (VantaUiComponent child : component.children()) {
            if (containsVisibleLabel(child, label)) return true;
        }
        return false;
    }

    /** Informational placeholder for genuinely empty collections or unavailable content. */
    public static VantaUiNode emptyState(VantaUiNode.Icon icon, String title,
                                         String description, String actionLabel,
                                         Runnable action) {
        VantaUiNode card = panel(Surface.ELEVATED, 8, 16)
                .align(VantaUiNode.Alignment.CENTER)
                .width(320);
        if (icon != null) {
            card.add(VantaUiNode.icon(icon).size(24, 24)
                    .iconColor(VantaUiTheme.TEXT_MUTED));
        }
        card.add(VantaUiNode.title(Objects.requireNonNull(title, "title")));
        if (description != null && !description.isBlank()) {
            card.add(VantaUiNode.paragraph(description, VantaUiTheme.TEXT_SECONDARY, 3)
                    .align(VantaUiNode.Alignment.CENTER));
        }
        if (actionLabel != null && !actionLabel.isBlank() && action != null) {
            card.add(actionButton(actionLabel, null, ButtonVariant.SECONDARY, action));
        }
        return card;
    }

    /** Two-line information row with a right-aligned semantic status badge. */
    public static VantaUiNode statusRow(String title, String detail, String status, Tone tone) {
        VantaUiNode row = VantaUiNode.row(10, 10)
                .height(48).align(VantaUiNode.Alignment.CENTER);
        VantaUiNode labels = VantaUiNode.column(3, 0).flex(1);
        labels.add(VantaUiNode.text(Objects.requireNonNull(title, "title")));
        if (detail != null && !detail.isBlank()) {
            labels.add(VantaUiNode.text(detail, VantaUiTheme.TEXT_MUTED));
        }
        row.add(labels);
        row.add(badge(status, tone));
        return row;
    }

    public static VantaUiNode navigationItem(String label, VantaUiNode.Icon icon,
                                              boolean selected, Runnable action) {
        return VantaUiNode.button(label, icon, Objects.requireNonNull(action, "action"))
                .height(36).selected(selected);
    }

    public static VantaUiNode workspaceRow(String title, String detail, VantaUiNode.Icon icon,
                                            Runnable action) {
        VantaUiNode row = VantaUiNode.row(12, 10).height(54)
                .align(VantaUiNode.Alignment.CENTER);
        row.add(VantaUiNode.icon(Objects.requireNonNull(icon, "icon")).size(18, 18)
                .iconColor(VantaClientAccent.current()));
        VantaUiNode labels = VantaUiNode.column(3, 0).flex(1);
        labels.add(VantaUiNode.text(Objects.requireNonNull(title, "title")));
        labels.add(VantaUiNode.text(detail == null ? "" : detail, VantaUiTheme.TEXT_MUTED));
        row.add(labels);
        row.add(actionButton("Open", null, ButtonVariant.SECONDARY,
                Objects.requireNonNull(action, "action")).height(28));
        return row;
    }

    private static final class VantaClientAccent {
        private static int current() {
            return name.vantaclient.client.config.VantaClientPreferences.get().accent.color();
        }
    }

    /** Reusable module tile; feature state and configuration remain caller-owned. */
    public static VantaUiNode moduleCard(String title, String description, VantaUiNode.Icon icon,
                                         Supplier<String> status, int accent, int cardHeight,
                                         boolean compact, boolean configurable, Runnable configure,
                                         BooleanSupplier enabled, Consumer<Boolean> toggle) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(toggle, "toggle");

        VantaUiNode card = panel(Surface.ELEVATED, compact ? 3 : 4, compact ? 7 : 8)
                .height(cardHeight);
        VantaUiNode identity = VantaUiNode.row(8, 0)
                .height(20).align(VantaUiNode.Alignment.CENTER);
        identity.add(VantaUiNode.icon(icon).size(16, 16).iconColor(accent));
        identity.add(VantaUiNode.spacer(1).flex(1));
        identity.add(VantaUiNode.toggle("", enabled, toggle).width(36).height(22)
                .tooltip("Enable or disable " + title));
        card.add(identity);
        card.add(VantaUiNode.title(title));
        if (description != null && !description.isBlank()) {
            card.add(VantaUiNode.paragraph(description, VantaUiTheme.TEXT_MUTED,
                    compact ? 1 : 2));
        }
        card.add(VantaUiNode.dynamicText(() -> {
            String current = status.get();
            return current == null ? "" : current;
        }, VantaUiTheme.TEXT_MUTED).height(14));
        card.add(VantaUiNode.spacer(1).flex(1));
        if (configurable) {
            card.add(actionButton("Settings", null, ButtonVariant.SECONDARY,
                    Objects.requireNonNull(configure, "configure")).height(25)
                    .tooltip("Configure " + title));
        }
        return card;
    }

    public static VantaUiNode button(String label, Runnable action) {
        return actionButton(label, null, ButtonVariant.SECONDARY, action);
    }

    public static VantaUiNode toggle(String label, VantaState<Boolean> state) {
        Objects.requireNonNull(state, "state");
        return VantaUiNode.toggle(label, () -> Boolean.TRUE.equals(state.get()), state::set)
                .observeState(state, false);
    }

    public static VantaUiNode slider(String label, double minimum, double maximum, double step,
                                    VantaState<Double> state) {
        Objects.requireNonNull(state, "state");
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || maximum < minimum) {
            throw new IllegalArgumentException("Slider range must be finite and ordered.");
        }
        state.update(current -> current == null ? minimum : Math.max(minimum, Math.min(maximum, current)));
        DoubleConsumer setter = value -> state.set(Math.max(minimum, Math.min(maximum, value)));
        return VantaUiNode.slider(label, minimum, maximum, step,
                () -> { Double current = state.get(); return current == null ? minimum : current; }, setter)
                .observeState(state, false);
    }

    public static VantaUiNode textField(String placeholder, VantaState<String> state,
                                        int maximumLength) {
        Objects.requireNonNull(state, "state");
        return VantaUiNode.textField(placeholder,
                () -> state.get() == null ? "" : state.get(), state::set, maximumLength)
                .observeState(state, false);
    }

    public static VantaUiNode dropdown(String label, List<String> options, VantaState<String> state) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(options, "options");
        if (options.isEmpty()) throw new IllegalArgumentException("Dropdown options cannot be empty.");
        if (!options.contains(state.get())) state.set(options.get(0));
        return VantaUiNode.dropdown(label, options, state::get, state::set)
                .observeState(state, false);
    }
}
