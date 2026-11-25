package name.vantaclient.client.init;

import name.vantaclient.client.feature.*;
import name.vantaclient.client.platform.ClientPlatform;
import name.vantaclient.client.integration.net.VantaRemoteSessionManager;
import name.vantaclient.client.init.Keybinds;
import name.vantaclient.client.init.FeatureKeybinds;
import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.gui.VantaSettingsScreen;
import name.vantaclient.client.gui.VantaNotificationManager;
import name.vantaclient.client.util.AnchorPrediction;
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
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/** Loader-neutral per-tick gameplay and presentation work. */
public final class ClientTickHandler {
    private ClientTickHandler() {}

    public static void tick(Minecraft client) {
        if (CpsHudFeature.INSTANCE.isEnabled() || HudEditorFeature.INSTANCE.isEnabled()
                        || PerformanceOptimizerFeature.INSTANCE.isEnabled()) {
                    ClickRateTracker.refreshSnapshot();
                }
        
                if (PerformanceOptimizerFeature.INSTANCE.isEnabled()) {
                    HudPerformanceCache.tick(client);
                }
        
                if (ClientPlatform.isFabric()) {
                    FarViewController.tick(client);
                    FarTerrainRenderer.tick(client);
                }
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
