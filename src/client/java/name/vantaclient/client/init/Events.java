package name.vantaclient.client.init;

import name.vantaclient.client.integration.net.VantaRemoteSessionManager;
import name.vantaclient.client.util.ClientPerformanceMetrics;
import name.vantaclient.client.gui.lobby.VantaLobbyWorld;
import net.minecraft.client.gui.screens.TitleScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** Fabric-specific lifecycle adapter around the shared client runtime. */
public final class Events {
    private Events() {}

    public static void register() {
        // PolyPlus drives lobby lifecycle at Minecraft.tick RETURN. END_CLIENT_TICK
        // gives the title screen, renderers, registries and resource managers a complete
        // client tick to settle before the synthetic ClientLevel is constructed.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.screen instanceof TitleScreen && client.level == null) {
                VantaLobbyWorld.start();
                VantaLobbyWorld.tick();
            }
        });
        ClientTickEvents.START_CLIENT_TICK.register(
                client -> ClientPerformanceMetrics.beginClientTick());
        ClientLifecycleEvents.CLIENT_STOPPING.register(
                client -> VantaRemoteSessionManager.shutdown());
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) ->
                        VantaRemoteSessionManager.onHostPlayerJoined(handler, server));
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> VantaRemoteSessionManager.onGuestJoined());
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> VantaRemoteSessionManager.onGuestDisconnected());

        ClientBootstrap.initializeFeatures();
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickHandler::tick);
        ClientTickEvents.END_CLIENT_TICK.register(VantaRemoteSessionManager::tick);
        ClientTickEvents.END_CLIENT_TICK.register(
                client -> ClientPerformanceMetrics.endClientTick());
    }
}
