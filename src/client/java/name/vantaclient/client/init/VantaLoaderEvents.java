package name.vantaclient.client.init;

import dev.itsdropper.vantaloader.api.ClientTickEvent;
import dev.itsdropper.vantaloader.api.EventBus;
import name.vantaclient.client.integration.net.VantaRemoteSessionManager;
import name.vantaclient.client.util.ClientPerformanceMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import name.vantaclient.client.gui.lobby.VantaLobbyWorld;
import name.vantaclient.client.init.Keybinds;
import name.vantaclient.client.init.FeatureKeybinds;

/** VantaLoader-native event integration; intentionally has no Fabric API linkage. */
public final class VantaLoaderEvents {
    private VantaLoaderEvents() {}

    public static void register(EventBus eventBus) {
        ClientBootstrap.initializeFeatures();
        eventBus.subscribe(ClientTickEvent.class, 0, event -> {
            if (event.phase() == ClientTickEvent.Phase.START) {
                if (!Keybinds.isNativeRegistered()) {
                    Keybinds.registerNative();
                    FeatureKeybinds.registerNative();
                }
                ClientPerformanceMetrics.beginClientTick();
                return;
            }
            Minecraft client = (Minecraft) event.client();
            if (client.screen instanceof TitleScreen && client.level == null) {
                VantaLobbyWorld.start();
                VantaLobbyWorld.tick();
            }
            ClientTickHandler.tick(client);
            VantaRemoteSessionManager.tick(client);
            ClientPerformanceMetrics.endClientTick();
        });
    }
}
