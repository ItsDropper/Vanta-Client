package name.vantaclient.client.gui.components.settings;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.*;

import java.util.ArrayList;
import java.util.List;

public final class SettingRegistry {
    private SettingRegistry() {}

    public static List<Setting> getSettings(Feature feature) {
        List<Setting> settings = new ArrayList<>();

        if (feature == FlyBoostFeature.INSTANCE) {
            settings.add(new SliderSetting("Singleplayer flight multiplier", 1.0, 3.0, "x",
                    () -> (double) FlyBoostFeature.getMultiplier(),
                    value -> FlyBoostFeature.setMultiplier(value.floatValue())));
        }

        if (feature == ZoomFeature.INSTANCE) {
            settings.add(new SliderSetting("Zoom FOV", 1, 50, "",
                    () -> (double) ZoomFeature.getZoomFov(),
                    value -> ZoomFeature.setZoomFov(value.intValue())));
            settings.add(new SliderSetting("Zoom Speed", 0.01, 0.5, "",
                    () -> (double) ZoomFeature.getConfiguredZoomSpeed(),
                    value -> ZoomFeature.setZoomSpeed(value.floatValue())));
            settings.add(new SliderSetting("Scroll Strength", 0.35, 0.90, "x",
                    () -> (double) ZoomFeature.getScrollStrength(),
                    value -> ZoomFeature.setScrollStrength(value.floatValue())));
            settings.add(new BooleanSetting("Smooth Zoom",
                    ZoomFeature::isSmoothZoom, ZoomFeature::setSmoothZoom));
        }

        if (feature == FarViewFeature.INSTANCE) {
            settings.add(new SliderSetting("Vanilla Detail Distance", 2, 32, " chunks",
                    () -> (double) FarViewFeature.getTargetRenderDistance(),
                    value -> FarViewFeature.setTargetRenderDistance(value.intValue())));
            settings.add(new SliderSetting("Far Terrain Distance", 32, 2048, " chunks",
                    () -> (double) FarViewFeature.getFarViewDistance(),
                    value -> FarViewFeature.setFarViewDistance(value.intValue())));
            settings.add(new ChoiceSetting("Quality Profile",
                    List.of("PERFORMANCE", "BALANCED", "HIGH", "ULTRA"),
                    FarViewFeature::getQuality, FarViewFeature::setQuality));
            settings.add(new BooleanSetting("Adaptive Distance",
                    FarViewFeature::isAdaptiveEnabled, FarViewFeature::setAdaptiveEnabled));
        }

        if (feature == MotionBlurFeature.INSTANCE) {
            settings.add(new SliderSetting("Frame Persistence", 0.0, 0.95, "",
                    () -> (double) MotionBlurFeature.getStrength(),
                    value -> MotionBlurFeature.setStrength(value.floatValue())));
            settings.add(new SliderSetting("Blur Intensity", 0.0, 1.0, "%",
                    () -> (double) (DropperUtilsConfig.get().motionBlurIntensity * 100.0f),
                    value -> {
                        DropperUtilsConfig.get().motionBlurIntensity =
                                Math.max(0.0f, Math.min(1.0f, value.floatValue() / 100.0f));
                        DropperUtilsConfig.save();
                        name.vantaclient.client.util.MotionBlurRenderer.reset();
                    }));
        }

        if (feature == LowFireFeature.INSTANCE) {
            settings.add(new SliderSetting("Fire Height", 0, 100, "%",
                    () -> 100.0 - DropperUtilsConfig.get().lowFireHeight * 100.0,
                    value -> {
                        DropperUtilsConfig.get().lowFireHeight = 1.0f - value.floatValue() / 100.0f;
                        DropperUtilsConfig.save();
                    }));
            settings.add(new BooleanSetting("Hide Fire Overlay",
                    LowFireFeature::isOverlayHidden, LowFireFeature::setOverlayHidden));
        }

        if (feature == FullbrightFeature.INSTANCE) {
            settings.add(new SliderSetting("Brightness", 0, 100, "%",
                    () -> (double) FullbrightFeature.getBrightness(),
                    value -> FullbrightFeature.setBrightness(value.floatValue())));
            settings.add(new SliderSetting("Minimum Light", 0, 100, "%",
                    () -> (double) FullbrightFeature.getMinimumBrightness(),
                    value -> FullbrightFeature.setMinimumBrightness(value.floatValue())));
        }

        if (feature == AnchorOptimizerFeature.INSTANCE) {
            settings.add(new SliderSetting("Prediction Timeout", 5, 100, "ticks",
                    () -> (double) AnchorOptimizerFeature.getPredictionTimeout(),
                    value -> AnchorOptimizerFeature.setPredictionTimeout(value.intValue())));
            settings.add(new SliderSetting("Prediction Range", 4, 16, "blocks",
                    () -> (double) AnchorOptimizerFeature.getPredictionRange(),
                    value -> AnchorOptimizerFeature.setPredictionRange(value.floatValue())));
            settings.add(new BooleanSetting("Explosion Preview",
                    AnchorOptimizerFeature::isExplosionPreviewEnabled,
                    AnchorOptimizerFeature::setExplosionPreviewEnabled));
        }

        if (feature == NoExplosionEffectsFeature.INSTANCE) {
            settings.add(new BooleanSetting("Explosion Particles",
                    NoExplosionEffectsFeature::removeExplosionParticles,
                    NoExplosionEffectsFeature::setRemoveExplosionParticles));
            settings.add(new BooleanSetting("Explosion Smoke",
                    NoExplosionEffectsFeature::removeExplosionSmoke,
                    NoExplosionEffectsFeature::setRemoveExplosionSmoke));
        }

        return settings;
    }
}
