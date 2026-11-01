package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

public class AnchorOptimizerFeature extends Feature {

    public static final AnchorOptimizerFeature INSTANCE =
            new AnchorOptimizerFeature();

    private AnchorOptimizerFeature() {
        super(
                "Anchor Optimizer",
                "Improves visual feedback" +
                        " and prediction for respawn anchors.",
                Category.COMBAT
        );
    }

    @Override
    public void onEnable() {
        DropperUtilsConfig.get().anchorOptimizer = true;
        DropperUtilsConfig.save();
    }

    @Override
    public void onDisable() {
        DropperUtilsConfig.get().anchorOptimizer = false;
        DropperUtilsConfig.save();
    }

    @Override
    public void saveConfig() {
        DropperUtilsConfig.get().anchorOptimizer = isEnabled();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().anchorOptimizer);
    }

    public static int getPredictionTimeout() {
        return DropperUtilsConfig.get().anchorPredictionTimeoutTicks;
    }

    public static void setPredictionTimeout(int value) {
        DropperUtilsConfig.get().anchorPredictionTimeoutTicks = Math.max(5, Math.min(value, 100));
        DropperUtilsConfig.save();
    }

    public static float getPredictionRange() {
        return DropperUtilsConfig.get().anchorPredictionRange;
    }

    public static void setPredictionRange(float value) {
        DropperUtilsConfig.get().anchorPredictionRange = Math.max(4.0f, Math.min(value, 16.0f));
        DropperUtilsConfig.save();
    }

    public static boolean isExplosionPreviewEnabled() {
        return DropperUtilsConfig.get().anchorExplosionPreview;
    }

    public static void setExplosionPreviewEnabled(boolean value) {
        DropperUtilsConfig.get().anchorExplosionPreview = value;
        DropperUtilsConfig.save();
    }
}