package name.vantaclient.client.gui.compose.components;

import name.vantaclient.client.gui.framework.VantaState;
import name.vantaclient.client.gui.framework.VantaUiNode;

import java.util.Objects;
import java.util.function.DoubleConsumer;

/**
 * State-bound component factories for the retained UI.
 *
 * Component bindings use VantaState directly so callers can share state across
 * controls and screen logic without re-creating node trees or hand-writing
 * supplier/setter pairs.
 */
public final class VantaComponents {
    private VantaComponents() {}

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
