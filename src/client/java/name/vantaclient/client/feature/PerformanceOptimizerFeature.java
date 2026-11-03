package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.util.ClientPerformanceMetrics;

public final class PerformanceOptimizerFeature extends Feature {
    public static final PerformanceOptimizerFeature INSTANCE = new PerformanceOptimizerFeature();

    private PerformanceOptimizerFeature() {
        super(
                "Performance Optimizer",
                "Skips Vanta motion blur and caches potion HUD data while monitoring client timings.",
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
        ClientPerformanceMetrics.reset();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().performanceOptimizer);
    }
}
