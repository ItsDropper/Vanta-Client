package name.vantaclient.client.init;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import java.lang.reflect.Field;
import java.util.Arrays;

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

    /** Registers mappings directly with Minecraft when Fabric's helper is absent. */
    public static void registerNative() {
        if (fullbrightKey != null) return;
        fullbrightKey = nativeRegister(new KeyMapping("key.dropperutils.fullbright",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
        zoomKey = nativeRegister(new KeyMapping("key.dropperutils.zoom",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));
        sprintToggleKey = nativeRegister(new KeyMapping("key.dropperutils.sprint_toggle",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        sneakToggleKey = nativeRegister(new KeyMapping("key.dropperutils.sneak_toggle",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        guiKey = nativeRegister(new KeyMapping("key.dropperutils.gui",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY));
    }

    static KeyMapping nativeRegister(KeyMapping mapping) {
        try {
            Field field = Options.class.getDeclaredField("keyMappings");
            field.setAccessible(true);
            Options options = Minecraft.getInstance().options;
            KeyMapping[] current = (KeyMapping[]) field.get(options);
            KeyMapping[] updated = Arrays.copyOf(current, current.length + 1);
            updated[current.length] = mapping;
            field.set(options, updated);
            KeyMapping.resetMapping();
            return mapping;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException("Cannot register native Vanta Client key mapping "
                    + mapping.getName(), failure);
        }
    }
}
