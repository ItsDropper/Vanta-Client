package name.vantaclient.client.init;

import dev.itsdropper.vantaloader.api.ClientTickEvent;
import dev.itsdropper.vantaloader.api.EventBus;
import name.vantaclient.client.integration.net.VantaRemoteSessionManager;
import name.vantaclient.client.util.ClientPerformanceMetrics;
import net.minecraft.client.Minecraft;

/** VantaLoader-native event integration; intentionally has no Fabric API linkage. */
public final class VantaLoaderEvents {
    private VantaLoaderEvents() {}

    public static void register(EventBus eventBus) {
        ClientBootstrap.initializeFeatures();
        eventBus.subscribe(ClientTickEvent.class, 0, event -> {
            if (event.phase() == ClientTickEvent.Phase.START) {
                ClientPerformanceMetrics.beginClientTick();
                return;
            }
            Minecraft client = (Minecraft) event.client();
            ClientTickHandler.tick(client);
            VantaRemoteSessionManager.tick(client);
            ClientPerformanceMetrics.endClientTick();
        });
    }
}
