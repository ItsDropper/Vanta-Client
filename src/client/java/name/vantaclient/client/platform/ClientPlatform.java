package name.vantaclient.client.platform;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Small loader boundary for paths and optional mod-presence checks.
 * Fabric remains the default distribution; VantaLoader installs its context
 * before any client configuration or feature classes are initialized.
 */
public final class ClientPlatform {
    private static volatile Path configDirectory;
    private static volatile boolean fabric;

    private ClientPlatform() {}

    public static void configure(Path configDirectory, boolean fabric) {
        ClientPlatform.configDirectory = Objects.requireNonNull(configDirectory, "configDirectory")
                .toAbsolutePath().normalize();
        ClientPlatform.fabric = fabric;
    }

    public static Path configDirectory() {
        Path configured = configDirectory;
        if (configured == null) {
            throw new IllegalStateException("Client platform has not been initialized");
        }
        return configured;
    }

    public static boolean isFabric() {
        return fabric;
    }

    /** Optional integrations must fail closed when the active loader cannot enumerate mods. */
    public static boolean isModLoaded(String modId) {
        if (!fabric) return false;
        try {
            Class<?> loaderType = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderType.getMethod("getInstance").invoke(null);
            Method isModLoaded = loaderType.getMethod("isModLoaded", String.class);
            return Boolean.TRUE.equals(isModLoaded.invoke(loader, modId));
        } catch (ReflectiveOperationException | LinkageError exception) {
            System.err.println("[Vanta Client] Could not query optional Fabric mod '" + modId
                    + "': " + exception.getMessage());
            return false;
        }
    }
}
