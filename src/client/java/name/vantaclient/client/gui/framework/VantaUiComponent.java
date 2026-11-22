package name.vantaclient.client.gui.framework;

import java.util.List;

/**
 * Stable inspection contract for retained UI components.
 *
 * Layout and rendering remain owned by the Minecraft-backed runtime; this interface
 * exposes semantic identity and geometry without leaking mutable node internals.
 */
public interface VantaUiComponent {
    enum Role {
        GROUP, LABEL, IMAGE, PRESENTATION, BUTTON, SWITCH, SLIDER,
        COMBO_BOX, TEXT_FIELD, PROGRESS, CUSTOM
    }

    record Bounds(int x, int y, int width, int height) {
        public Bounds {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Component bounds cannot have negative dimensions.");
            }
        }
    }

    record Semantics(Role role, String label, String value, String description,
                     boolean enabled, boolean selected, boolean focused) {
        public Semantics {
            if (role == null) throw new IllegalArgumentException("Component role cannot be null.");
            label = label == null ? "" : label;
            value = value == null ? "" : value;
            description = description == null ? "" : description;
        }
    }

    Bounds bounds();

    Semantics semantics();

    List<? extends VantaUiComponent> children();
}
