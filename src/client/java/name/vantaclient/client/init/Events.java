package name.vantaclient.client.init;

import name.vantaclient.client.feature.*;
import dev.itsdropper.vantaloader.api.ClientTickEvent;
import dev.itsdropper.vantaloader.api.EventBus;
import net.minecraft.client.Minecraft;
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

        initializeFeatures();

        ClientTickEvents.END_CLIENT_TICK.register(Events::onEndClientTick);

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

    private static void initializeFeatures() {
        FullbrightFeature.INSTANCE.setEnabled(
                DropperUtilsConfig.get().fullbright
        );

        ZoomFeature.INSTANCE.setEnabled(
                DropperUtilsConfig.get().zoom
        );

        MotionBlurFeature.INSTANCE.setEnabled(
                DropperUtilsConfig.get().motionBlur
        );

        TotemCounterFeature.INSTANCE.setEnabled(
                DropperUtilsConfig.get().totemCounter
        );

        ArmorHudFeature.INSTANCE.setEnabled(
                DropperUtilsConfig.get().armorHud
        );

        SaturationFeature.INSTANCE.setEnabled(
                DropperUtilsConfig.get().saturation
        );

        DebugHudFeature.INSTANCE.setEnabled(
                DropperUtilsConfig.get().debugHud
        );

        PotionEffectsFeature.INSTANCE.setEnabled(
                DropperUtilsConfig.get().potionEffects
        );
        FpsHudFeature.INSTANCE.loadConfig();
        PingHudFeature.INSTANCE.loadConfig();
        KeystrokesFeature.INSTANCE.loadConfig();
        CpsHudFeature.INSTANCE.loadConfig();
        SprintSneakToggles.load();
    }

    private static void onEndClientTick(Minecraft client) {

        if (CpsHudFeature.INSTANCE.isEnabled() || HudEditorFeature.INSTANCE.isEnabled()
                || PerformanceOptimizerFeature.INSTANCE.isEnabled()) {
            ClickRateTracker.refreshSnapshot();
        }

        if (PerformanceOptimizerFeature.INSTANCE.isEnabled()) {
            HudPerformanceCache.tick(client);
        }

        FarViewController.tick(client);
        FarTerrainRenderer.tick(client);
        VantaNotificationManager.tick();
        FlyBoostFeature.tick(client);
        DisableDoubleTapSprintFeature.tick(client);

        boolean captureActive = Keybinds.isCaptureActive();
        boolean gameplayInputAllowed = client.screen == null && !captureActive;

        if (Keybinds.fullbrightKey.consumeClick() && gameplayInputAllowed) {
            FullbrightFeature.INSTANCE.toggle();
        }
        if (Keybinds.guiKey.consumeClick() && gameplayInputAllowed) {
            client.setScreen(new VantaSettingsScreen(null));
        }

        ZoomFeature.INSTANCE.tick(Keybinds.zoomKey, !gameplayInputAllowed);
        if (Keybinds.sprintToggleKey.consumeClick() && gameplayInputAllowed) {
            SprintSneakToggles.toggleSprint();
        }
        if (Keybinds.sneakToggleKey.consumeClick() && gameplayInputAllowed) {
            SprintSneakToggles.toggleSneak();
        }
        SprintSneakToggles.tick(client);
        AdaptiveEntityDistanceController.tick(client);
        FeatureKeybinds.tick(gameplayInputAllowed);

        if (client.player != null) {

            ItemStack main = client.player.getMainHandItem();
            ItemStack off = client.player.getOffhandItem();

            if (main.is(Items.RESPAWN_ANCHOR)
                    || off.is(Items.RESPAWN_ANCHOR)
                    || main.is(Items.GLOWSTONE)
                    || off.is(Items.GLOWSTONE)) {

                AnchorOptimizer.wakeUp();

            }

        }

        AnchorOptimizer.tick();
        AnchorPrediction.tick();
        ExplosionEffects.tick();
        HudManager.refreshVisibleElements();
    }
}
