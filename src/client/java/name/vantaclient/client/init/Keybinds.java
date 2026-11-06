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

    public static boolean isNativeRegistered() {
        return fullbrightKey != null;
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


    private static boolean containsIdentity(KeyMapping[] mappings, KeyMapping expected) {
        return Arrays.stream(mappings).anyMatch(candidate -> candidate == expected);
    }

    static KeyMapping nativeRegister(KeyMapping mapping) {
        try {
            Options options = Minecraft.getInstance().options;
            Field[] mappingFields = Arrays.stream(Options.class.getDeclaredFields())
                    .filter(candidate -> candidate.getType() == KeyMapping[].class)
                    .filter(candidate -> {
                        try {
                            candidate.setAccessible(true);
                            KeyMapping[] values = (KeyMapping[]) candidate.get(options);
                            return containsIdentity(values, options.keyUp)
                                    && containsIdentity(values, options.keyAttack);
                        } catch (ReflectiveOperationException | RuntimeException ignored) {
                            return false;
                        }
                    })
                    .toArray(Field[]::new);
            if (mappingFields.length != 1) {
                throw new IllegalStateException("Expected exactly one all-key mapping array in Minecraft Options, found "
                        + mappingFields.length);
            }
            Field field = mappingFields[0];
            field.setAccessible(true);
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
