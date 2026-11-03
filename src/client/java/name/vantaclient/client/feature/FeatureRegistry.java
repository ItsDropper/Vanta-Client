package name.vantaclient.client.feature;

import java.util.List;

public class FeatureRegistry {

    public static final List<Feature> FEATURES = List.of(
            FullbrightFeature.INSTANCE,
            ZoomFeature.INSTANCE,
            MotionBlurFeature.INSTANCE,
            NametagUtilsFeature.INSTANCE,
            FarViewFeature.INSTANCE,
            PerformanceOptimizerFeature.INSTANCE,
            TotemCounterFeature.INSTANCE,
            ArmorHudFeature.INSTANCE,
            SaturationFeature.INSTANCE,
            AnchorOptimizerFeature.INSTANCE,
            DebugHudFeature.INSTANCE,
            NoExplosionEffectsFeature.INSTANCE,
            LowFireFeature.INSTANCE,
            PotionEffectsFeature.INSTANCE,
            FpsHudFeature.INSTANCE,
            PingHudFeature.INSTANCE,
            KeystrokesFeature.INSTANCE,
            CpsHudFeature.INSTANCE,
            ChatTimestampsFeature.INSTANCE,
            VanillaHudFeature.INSTANCE,
            FlyBoostFeature.INSTANCE,
            OgAnimationsFeature.INSTANCE,
            DisableDoubleTapSprintFeature.INSTANCE
    );

    public static void loadConfigs() {
        for (Feature feature : FEATURES) {
            feature.loadConfig();
        }
    }
}
