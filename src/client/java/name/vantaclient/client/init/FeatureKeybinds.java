package name.vantaclient.client.init;

import name.vantaclient.client.feature.Feature;
import name.vantaclient.client.feature.FeatureRegistry;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Optional per-feature toggle shortcuts.
 *
 * Bindings are registered with Fabric so Minecraft persists them in its normal
 * options key-mapping store and exposes them in the Controls screen.
 */
public final class FeatureKeybinds {
    public record Binding(Feature feature, KeyMapping mapping) {}

    private static final List<Binding> BINDINGS = new ArrayList<>();
    private static boolean registered;

    private FeatureKeybinds() {}

    public static void register() {
        if (registered) return;
        registered = true;

        for (Feature feature : FeatureRegistry.FEATURES) {
            String id = feature.getClass().getSimpleName()
                    .replaceFirst("Feature$", "")
                    .toLowerCase(Locale.ROOT);
            String translationKey = "key.dropperutils.feature." + id;
            KeyMapping mapping = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    translationKey,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UNKNOWN,
                    Keybinds.CATEGORY));
            BINDINGS.add(new Binding(feature, mapping));
        }

        KeyMapping.resetMapping();
    }

    public static List<Binding> bindings() {
        return List.copyOf(BINDINGS);
    }

    public static KeyMapping mappingFor(Feature feature) {
        if (feature == null) return null;
        for (Binding binding : BINDINGS) {
            if (binding.feature() == feature) return binding.mapping();
        }
        return null;
    }

    /**
     * Consume every queued press even while a screen is open so opening and
     * closing a menu cannot replay a shortcut after returning to gameplay.
     */
    public static void tick(boolean gameplayInputAllowed) {
        for (Binding binding : BINDINGS) {
            while (binding.mapping().consumeClick()) {
                if (gameplayInputAllowed) binding.feature().toggle();
            }
        }
    }
}
