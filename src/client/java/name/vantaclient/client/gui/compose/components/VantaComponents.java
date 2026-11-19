package name.vantaclient.client.gui.compose.components;

import name.vantaclient.client.gui.framework.VantaState;
import name.vantaclient.client.gui.framework.VantaUiNode;
import name.vantaclient.client.gui.framework.VantaUiTheme;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * State-bound component factories for the retained UI.
 *
 * Component bindings use VantaState directly so callers can share state across
 * controls and screen logic without re-creating node trees or hand-writing
 * supplier/setter pairs.
 */
public final class VantaComponents {
    public enum Surface { PANEL, ELEVATED, FIELD, POPOVER }

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

    public static VantaUiNode sectionHeader(String eyebrow, String title, String detail) {
        VantaUiNode header = VantaUiNode.column(4, 0);
        if (eyebrow != null && !eyebrow.isBlank()) {
            header.add(VantaUiNode.eyebrow(eyebrow));
        }
        header.add(VantaUiNode.title(Objects.requireNonNull(title, "title")));
        if (detail != null && !detail.isBlank()) {
            header.add(VantaUiNode.text(detail, VantaUiTheme.TEXT_SECONDARY));
        }
        return header;
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
        row.add(VantaUiNode.button("Open", Objects.requireNonNull(action, "action")).height(28));
        return row;
    }

    private static final class VantaClientAccent {
        private static int current() {
            return name.vantaclient.client.config.VantaClientPreferences.get().accent.color();
        }
    }

    /**
     * Reusable module tile used by the feature browser. Domain state remains owned
     * by the caller; this component only composes the visual identity and controls.
     */
    public static VantaUiNode moduleCard(String title, String description, VantaUiNode.Icon icon,
                                         Supplier<String> status, int accent, boolean configurable,
                                         Runnable configure, BooleanSupplier enabled,
                                         Consumer<Boolean> toggle) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(enabled, "enabled");
        Objects.requireNonNull(toggle, "toggle");

        VantaUiNode card = surface(Surface.ELEVATED, 8, 12).height(166);
        VantaUiNode identity = VantaUiNode.row(8, 0)
                .height(22).align(VantaUiNode.Alignment.CENTER);
        identity.add(VantaUiNode.icon(icon).size(18, 18).iconColor(accent));
        identity.add(VantaUiNode.spacer(1).flex(1));
        identity.add(VantaUiNode.dynamicText(status, accent));
        card.add(identity);
        card.add(VantaUiNode.title(title));
        card.add(VantaUiNode.paragraph(
                description == null || description.isBlank()
                        ? (configurable ? "Configurable module" : "No additional options")
                        : description,
                VantaUiTheme.TEXT_MUTED, 2));
        card.add(VantaUiNode.spacer(1).flex(1));

        VantaUiNode actions = VantaUiNode.row(7, 0)
                .height(30).align(VantaUiNode.Alignment.CENTER);
        if (configurable) {
            actions.add(VantaUiNode.button("Configure", Objects.requireNonNull(configure, "configure"))
                    .height(28).tooltip("Open " + title + " settings"));
        } else {
            actions.add(VantaUiNode.text("Module", VantaUiTheme.TEXT_MUTED));
        }
        actions.add(VantaUiNode.spacer(1).flex(1));
        actions.add(VantaUiNode.toggle("", enabled, toggle).width(46).height(28)
                .tooltip("Enable or disable " + title));
        card.add(actions);
        return card;
    }

    public static VantaUiNode button(String label, Runnable action) {
        return VantaUiNode.button(label, Objects.requireNonNull(action, "action"));
    }

    public static VantaUiNode toggle(String label, VantaState<Boolean> state) {
        Objects.requireNonNull(state, "state");
        return VantaUiNode.toggle(label, () -> Boolean.TRUE.equals(state.get()), state::set);
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
                () -> { Double current = state.get(); return current == null ? minimum : current; }, setter);
    }

    public static VantaUiNode textField(String placeholder, VantaState<String> state,
                                        int maximumLength) {
        Objects.requireNonNull(state, "state");
        return VantaUiNode.textField(placeholder,
                () -> state.get() == null ? "" : state.get(), state::set, maximumLength);
    }
}
