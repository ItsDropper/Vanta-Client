package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.util.ClientPerformanceMetrics;
import name.vantaclient.client.util.AdaptiveEntityDistanceController;

public final class PerformanceOptimizerFeature extends Feature {
    public static final PerformanceOptimizerFeature INSTANCE = new PerformanceOptimizerFeature();

    private PerformanceOptimizerFeature() {
        super(
                "Performance Optimizer",
                "Enabled by default: reuses tick-refreshed HUD snapshots and automatically reduces only client-side entity draw distance under sustained frame pressure, then restores the user-selected distance when headroom returns.",
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
