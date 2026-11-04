package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.util.ClientPerformanceMetrics;
import name.vantaclient.client.util.AdaptiveEntityDistanceController;

public final class PerformanceOptimizerFeature extends Feature {
    public static final PerformanceOptimizerFeature INSTANCE = new PerformanceOptimizerFeature();

    private PerformanceOptimizerFeature() {
        super(
                "Performance Optimizer",
                "Caches frequently rendered HUD data and can adapt entity draw distance to measured frame performance.",
                Category.MISC
        );
    }

    @Override
    public void onEnable() {
        DropperUtilsConfig.get().performanceOptimizer = true;
        DropperUtilsConfig.save();
    }

    @Override
    public void onDisable() {
        DropperUtilsConfig.get().performanceOptimizer = false;
        DropperUtilsConfig.save();
        AdaptiveEntityDistanceController.restore();
        if (!FarViewFeature.INSTANCE.isEnabled()) {
            ClientPerformanceMetrics.reset();
        }
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().performanceOptimizer);
    }
}
