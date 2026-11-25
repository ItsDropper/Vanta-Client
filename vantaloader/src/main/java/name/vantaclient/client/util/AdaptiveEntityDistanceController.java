package name.vantaclient.client.util;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.PerformanceOptimizerFeature;
import net.minecraft.client.Minecraft;

/**
 * Dynamically reduces only client-side entity draw distance when world rendering
 * is consistently over budget. It never changes entity ticks, AI, collision or
 * server simulation. The user's original option is restored on every exit path.
 */
public final class AdaptiveEntityDistanceController {
    private static final int UPDATE_INTERVAL_TICKS = 40;
    private static final double REDUCE_STEP = 0.25;
    private static final double RECOVER_STEP = 0.125;
    private static final double MINIMUM_VANILLA_SCALE = 0.5;

    private static boolean captured;
    private static double originalScale;
    private static double lastAppliedScale;
    private static int ticksUntilUpdate;
    private static long lastObservedWorldRenderSamples;

    private AdaptiveEntityDistanceController() {}

    public static void tick(Minecraft client) {
        if (!PerformanceOptimizerFeature.INSTANCE.isEnabled()
                || !DropperUtilsConfig.get().adaptiveEntityDistance
                || client.player == null
                || client.level == null) {
            restore(client);
            return;
        }

        double actualScale = client.options.entityDistanceScaling().get();
        if (!captured) {
            originalScale = actualScale;
            lastAppliedScale = actualScale;
            captured = true;
            lastObservedWorldRenderSamples = ClientPerformanceMetrics.getWorldRenderSamples();
            ticksUntilUpdate = 0;
        } else if (Math.abs(actualScale - lastAppliedScale) > 0.0001) {
            // Treat an external options change as the new user-selected ceiling.
            originalScale = actualScale;
            lastAppliedScale = actualScale;
            lastObservedWorldRenderSamples = ClientPerformanceMetrics.getWorldRenderSamples();
            ticksUntilUpdate = 0;
        }

        // Do not tune settings while the user is navigating menus. External
        // slider changes are still detected above and become the new baseline.
        if (client.screen != null) return;
        if (ticksUntilUpdate-- > 0) return;
        ticksUntilUpdate = UPDATE_INTERVAL_TICKS;
        long sampleCount = ClientPerformanceMetrics.getWorldRenderSamples();
        if (sampleCount == lastObservedWorldRenderSamples) return;
        lastObservedWorldRenderSamples = sampleCount;

        double next = calculateNextScale(
                lastAppliedScale,
                originalScale,
                DropperUtilsConfig.get().minimumEntityDistanceRatio,
                ClientPerformanceMetrics.getAverageWorldRenderMs(),
                client.getFps());
        if (Math.abs(next - lastAppliedScale) < 0.0001) return;

        client.options.entityDistanceScaling().set(next);
        lastAppliedScale = next;
    }

    /**
     * Pure policy function, kept separate from Minecraft state for deterministic
     * regression tests.
     */
    public static double calculateNextScale(double currentScale,
                                            double userMaximumScale,
                                            double minimumRatio,
                                            double averageWorldRenderMs,
                                            int fps) {
        double maximum = clamp(userMaximumScale, MINIMUM_VANILLA_SCALE, 5.0);
        double current = clamp(currentScale, MINIMUM_VANILLA_SCALE, maximum);
        double ratio = clamp(minimumRatio, 0.5, 1.0);
        double floor = Math.max(MINIMUM_VANILLA_SCALE, maximum * ratio);

        boolean overloaded = averageWorldRenderMs >= 22.0 || (fps > 0 && fps < 50);
        boolean hasHeadroom = averageWorldRenderMs > 0.0
                && averageWorldRenderMs < 12.0
                && fps >= 90;

        if (overloaded) {
            return Math.max(floor, current - REDUCE_STEP);
        }
        if (hasHeadroom) {
            return Math.min(maximum, current + RECOVER_STEP);
        }
        return current;
    }

    public static void restore() {
        restore(Minecraft.getInstance());
    }

    private static void restore(Minecraft client) {
        if (captured && client != null && client.options != null) {
            double current = client.options.entityDistanceScaling().get();
            // If another option screen/mod changed it since our last write,
            // preserve that newer value rather than overwriting it.
            if (Math.abs(current - lastAppliedScale) <= 0.0001) {
                client.options.entityDistanceScaling().set(originalScale);
            }
        }
        captured = false;
        originalScale = 1.0;
        lastAppliedScale = 1.0;
        ticksUntilUpdate = 0;
        lastObservedWorldRenderSamples = 0L;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
