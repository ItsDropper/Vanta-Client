package name.vantaclient.client.integration;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Discovers config screens already exposed by installed mods through Mod Menu's
 * standard entrypoint. No Vanta-specific registration is required.
 */
public final class ModConfigDiscovery {
    private static final String ENTRYPOINT = "modmenu";
    private static final String MOD_MENU_API_CLASS =
            "com.terraformersmc.modmenu.api.ModMenuApi";
    private static final String FACTORY_CLASS =
            "com.terraformersmc.modmenu.api.ConfigScreenFactory";

    private ModConfigDiscovery() {}

    public static Map<String, Object> discover() {
        FabricLoader loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("modmenu")) return Map.of();

        Map<String, Object> discovered = new HashMap<>();
        for (var container : loader.getEntrypointContainers(ENTRYPOINT, Object.class)) {
            String modId = container.getProvider().getMetadata().getId();
            try {
                Object entrypoint = container.getEntrypoint();
                Class<?> apiType = Class.forName(MOD_MENU_API_CLASS, false,
                        entrypoint.getClass().getClassLoader());
                if (!apiType.isInstance(entrypoint)) continue;
                Method getter = apiType.getMethod("getModConfigScreenFactory");
                Object factory = getter.invoke(entrypoint);
                if (factory != null) discovered.putIfAbsent(modId, factory);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                System.err.println("[Vanta Client] Could not inspect config entrypoint for "
                        + modId + ": " + exception.getMessage());
            }
        }
        return Map.copyOf(discovered);
    }

    public static Screen open(Object factory, Screen parent) {
        if (factory == null) return null;
        try {
            Class<?> factoryType = Class.forName(FACTORY_CLASS, false,
                    factory.getClass().getClassLoader());
            Method create = factoryType.getMethod("create", Screen.class);
            Object result = create.invoke(factory, parent);
            return result instanceof Screen screen ? screen : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            System.err.println("[Vanta Client] Could not open discovered config screen: "
                    + exception.getMessage());
            return null;
        }
    }
}
