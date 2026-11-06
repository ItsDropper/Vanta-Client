package name.vantaclient.client.integration;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Fabric-only adapter for Mod Menu's optional configuration entrypoints. */
final class FabricModConfigDiscovery {
    private static final String ENTRYPOINT = "modmenu";
    private static final String MOD_MENU_API_CLASS =
            "com.terraformersmc.modmenu.api.ModMenuApi";

    private FabricModConfigDiscovery() {}

    static Map<String, Object> discover() {
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
}
