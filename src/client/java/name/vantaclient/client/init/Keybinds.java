package name.vantaclient.client.init;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class Keybinds {

    public static KeyMapping fullbrightKey;
    public static KeyMapping zoomKey;
    public static KeyMapping guiKey;
    public static KeyMapping sprintToggleKey;
    public static KeyMapping sneakToggleKey;

    private static boolean captureActive;

    public static boolean isCaptureActive() {
        return captureActive;
    }

    public static void setCaptureActive(boolean active) {
        captureActive = active;
    }

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath(
                            "dropperutils",
                            "category"
                    )
            );


    public static void register() {

        fullbrightKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.dropperutils.fullbright",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_K,
                        CATEGORY
                )
        );

        zoomKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.dropperutils.zoom",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_C,
                        CATEGORY
                )
        );

        sprintToggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.dropperutils.sprint_toggle", InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN, CATEGORY)
        );
        sneakToggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.dropperutils.sneak_toggle", InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_UNKNOWN, CATEGORY)
        );
        guiKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping(
                        "key.dropperutils.gui",
                        InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_M,
                        CATEGORY
                )
        );


    }
}