package name.vantaclient.client.init;

import name.vantaclient.client.feature.*;
import name.vantaclient.client.integration.net.VantaRemoteSessionManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import name.vantaclient.client.util.AnchorPrediction;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.gui.VantaSettingsScreen;
import name.vantaclient.client.gui.VantaNotificationManager;
import name.vantaclient.client.util.AnchorOptimizer;
import name.vantaclient.client.util.ClientPerformanceMetrics;
import name.vantaclient.client.hud.HudPerformanceCache;
import name.vantaclient.client.hud.ClickRateTracker;
import name.vantaclient.client.hudeditor.HudManager;
import name.vantaclient.client.util.ExplosionEffects;
import name.vantaclient.client.util.FarViewController;
import name.vantaclient.client.util.FarTerrainRenderer;
import name.vantaclient.client.util.SprintSneakToggles;
import name.vantaclient.client.util.AdaptiveEntityDistanceController;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

public class Events {

    public static void register() {

        ClientTickEvents.START_CLIENT_TICK.register(client -> ClientPerformanceMetrics.beginClientTick());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> VantaRemoteSessionManager.shutdown());
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> VantaRemoteSessionManager.onHostPlayerJoined(handler, server));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> VantaRemoteSessionManager.onGuestJoined());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> VantaRemoteSessionManager.onGuestDisconnected());

        ClientBootstrap.initializeFeatures();

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickHandler::tick);

        ClientTickEvents.END_CLIENT_TICK.register(VantaRemoteSessionManager::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientPerformanceMetrics.endClientTick());
    }


    public static void registerVantaLoader(EventBus eventBus) {
        initializeFeatures();
        eventBus.subscribe(ClientTickEvent.class, 0, event -> {
            if (event.phase() == ClientTickEvent.Phase.START) {
                ClientPerformanceMetrics.beginClientTick();
            } else {
                onEndClientTick((Minecraft) event.client());
                VantaRemoteSessionManager.tick((Minecraft) event.client());
                ClientPerformanceMetrics.endClientTick();
            }
        });
    }




}
