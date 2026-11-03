package name.vantaclient.client.init;

import name.vantaclient.client.feature.*;
import name.vantaclient.client.util.AnchorPrediction;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.gui.VantaSettingsScreen;
import name.vantaclient.client.util.AnchorOptimizer;
import name.vantaclient.client.util.ClientPerformanceMetrics;
import name.vantaclient.client.util.ExplosionEffects;
import name.vantaclient.client.util.SprintSneakToggles;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

public class Events {

    public static void register() {

        ClientTickEvents.START_CLIENT_TICK.register(client -> ClientPerformanceMetrics.beginClientTick());

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


        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (Keybinds.fullbrightKey.consumeClick()) {
                FullbrightFeature.INSTANCE.toggle();
            }



            if (Keybinds.guiKey.consumeClick()) {
                client.setScreen(new VantaSettingsScreen(null));
            }


            ZoomFeature.INSTANCE.tick(Keybinds.zoomKey);
            if (Keybinds.sprintToggleKey.consumeClick()) SprintSneakToggles.toggleSprint();
            if (Keybinds.sneakToggleKey.consumeClick()) SprintSneakToggles.toggleSneak();
            SprintSneakToggles.tick(client);

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
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientPerformanceMetrics.endClientTick());
    }
}