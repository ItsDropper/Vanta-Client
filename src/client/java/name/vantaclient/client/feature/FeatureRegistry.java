package name.vantaclient.client.feature;

import java.util.List;

public class FeatureRegistry {

    public static final List<Feature> FEATURES = List.of(
            FullbrightFeature.INSTANCE,
            ZoomFeature.INSTANCE,
            MotionBlurFeature.INSTANCE,
            TotemCounterFeature.INSTANCE,
            ArmorHudFeature.INSTANCE,
            SaturationFeature.INSTANCE,
            AnchorOptimizerFeature.INSTANCE,
            DebugHudFeature.INSTANCE,
            NoExplosionEffectsFeature.INSTANCE,
            LowFireFeature.INSTANCE,
            PotionEffectsFeature.INSTANCE
    );

    public static void loadConfigs() {
        for (Feature feature : FEATURES) {
            feature.loadConfig();
        }
    }
}
