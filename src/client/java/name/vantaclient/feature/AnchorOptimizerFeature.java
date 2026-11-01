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
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public void saveConfig() {
        DropperUtilsConfig.get().anchorOptimizer = isEnabled();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().anchorOptimizer);
    }

    public static int getScanInterval() {
        return DropperUtilsConfig.get().anchorScanInterval;
    }

    public static void setScanInterval(int value) {
        DropperUtilsConfig.get().anchorScanInterval =
                Math.max(1, Math.min(value, 100));

        DropperUtilsConfig.save();
    }

    public static int getScanRange() {
        return DropperUtilsConfig.get().anchorScanRange;
    }

    public static void setScanRange(int value) {
        DropperUtilsConfig.get().anchorScanRange =
                Math.max(1, Math.min(value, 8));

        DropperUtilsConfig.save();
    }
}