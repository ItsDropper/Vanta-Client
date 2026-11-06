package name.vantaclient.client.init;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.*;
import name.vantaclient.client.util.SprintSneakToggles;

/** Shared feature/config initialization used by every supported loader. */
public final class ClientBootstrap {
    private ClientBootstrap() {}

    public static void initializeFeatures() {
        FullbrightFeature.INSTANCE.setEnabled(DropperUtilsConfig.get().fullbright);
        ZoomFeature.INSTANCE.setEnabled(DropperUtilsConfig.get().zoom);
        MotionBlurFeature.INSTANCE.setEnabled(DropperUtilsConfig.get().motionBlur);
        TotemCounterFeature.INSTANCE.setEnabled(DropperUtilsConfig.get().totemCounter);
        ArmorHudFeature.INSTANCE.setEnabled(DropperUtilsConfig.get().armorHud);
        SaturationFeature.INSTANCE.setEnabled(DropperUtilsConfig.get().saturation);
        DebugHudFeature.INSTANCE.setEnabled(DropperUtilsConfig.get().debugHud);
        PotionEffectsFeature.INSTANCE.setEnabled(DropperUtilsConfig.get().potionEffects);
        FpsHudFeature.INSTANCE.loadConfig();
        PingHudFeature.INSTANCE.loadConfig();
        KeystrokesFeature.INSTANCE.loadConfig();
        CpsHudFeature.INSTANCE.loadConfig();
        SprintSneakToggles.load();
    }
}
