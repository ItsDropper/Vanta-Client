package name.vantaclient.client.init;

import name.vantaclient.client.integration.net.VantaRemoteSessionManager;
import name.vantaclient.client.util.ClientPerformanceMetrics;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/** Fabric-specific lifecycle adapter around the shared client runtime. */
public final class Events {
    private Events() {}

    public static void register() {
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
