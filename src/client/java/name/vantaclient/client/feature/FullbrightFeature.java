package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

public class FullbrightFeature extends Feature {

    public static final FullbrightFeature INSTANCE =
            new FullbrightFeature();

    private FullbrightFeature() {
        super(
                "Fullbright",
                "Makes dark areas fully visible.",
                Category.VISUAL
        );
    }

    @Override
    public void onEnable() {
        DropperUtilsConfig.get().fullbright = true;
        DropperUtilsConfig.save();
    }

    @Override
    public void onDisable() {
        DropperUtilsConfig.get().fullbright = false;
        DropperUtilsConfig.save();
    }

    public static float getBrightness() {
        return DropperUtilsConfig.get().fullbrightBrightness;
    }

    public static float getMinimumBrightness() {
        return DropperUtilsConfig.get().fullbrightMinimum;
    }

    public static void setMinimumBrightness(float value) {
        DropperUtilsConfig.get().fullbrightMinimum = Math.max(0.0f, Math.min(value, 100.0f));
        DropperUtilsConfig.save();
    }

    public static void setBrightness(float value) {
        DropperUtilsConfig.get().fullbrightBrightness =
                Math.max(0.0f, Math.min(value, 100.0f));

        DropperUtilsConfig.save();
    }
}