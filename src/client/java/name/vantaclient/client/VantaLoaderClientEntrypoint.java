package name.vantaclient.client;

import dev.itsdropper.vantaloader.api.ModContext;
import dev.itsdropper.vantaloader.api.VantaMod;
import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.config.VantaClientPreferences;
import name.vantaclient.client.feature.FeatureRegistry;
import name.vantaclient.client.integration.BuiltInIntegration;
import name.vantaclient.client.platform.ClientPlatform;
import name.vantaclient.client.init.VantaLoaderEvents;
import name.vantaclient.client.init.Keybinds;
import name.vantaclient.client.init.FeatureKeybinds;

/**
 * Native VantaLoader entrypoint. Fabric's entrypoint remains separate so this
 * class can be loaded without Fabric Loader classes being present.
 */
public final class VantaLoaderClientEntrypoint implements VantaMod {
    @Override
    public void onInitialize(ModContext context) {
        ClientPlatform.configure(context.gameDirectory().resolve("config"), false);
        DropperUtilsConfig.load();
        VantaClientPreferences.load();
        FeatureRegistry.loadConfigs();
        BuiltInIntegration.register();
        VantaLoaderEvents.register(context.events());
        context.logger().info("Vanta Client native bootstrap initialized for Minecraft "
                + context.minecraftVersion());
    }

    @Override
    public void onShutdown() {
        name.vantaclient.client.integration.net.VantaRemoteSessionManager.shutdown();
    }
}
