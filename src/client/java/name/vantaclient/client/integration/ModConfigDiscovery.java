package name.vantaclient.client.integration;

import name.vantaclient.client.platform.ClientPlatform;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Discovers config screens already exposed by installed mods through Mod Menu's
 * standard entrypoint. No Vanta-specific registration is required.
 */
public final class ModConfigDiscovery {
    private static final String FACTORY_CLASS =
            "com.terraformersmc.modmenu.api.ConfigScreenFactory";

    private ModConfigDiscovery() {}

    public static Map<String, Object> discover() {
        if (!ClientPlatform.isFabric()) return Map.of();
        return FabricModConfigDiscovery.discover();
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
