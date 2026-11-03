package name.vantaclient.client.util;

import name.vantaclient.client.feature.FarViewFeature;
import name.vantaclient.client.feature.PerformanceOptimizerFeature;

/**
 * Low-overhead timings for the client tick and world-render phases.
 * Measurements are collected only while a feature consumes them.
 */
public final class ClientPerformanceMetrics {
    private static long clientTickStartNanos;
    private static long worldRenderStartNanos;

    private static volatile double lastClientTickMs;
    private static volatile double averageClientTickMs;
    private static volatile double lastWorldRenderMs;
    private static volatile double averageWorldRenderMs;

    private ClientPerformanceMetrics() {}

    private static boolean isCollectionEnabled() {
        return PerformanceOptimizerFeature.INSTANCE.isEnabled()
                || FarViewFeature.INSTANCE.isEnabled();
    }

    public static void beginClientTick() {
        if (!isCollectionEnabled()) {
            clientTickStartNanos = 0L;
            return;
        }
        clientTickStartNanos = System.nanoTime();
    }

    public static void endClientTick() {
        if (clientTickStartNanos == 0L) return;
        long started = clientTickStartNanos;
        clientTickStartNanos = 0L;
        if (!isCollectionEnabled()) return;
        recordClientTick((System.nanoTime() - started) / 1_000_000.0);
    }

    public static void beginWorldRender() {
        if (!isCollectionEnabled()) {
            worldRenderStartNanos = 0L;
            return;
        }
        worldRenderStartNanos = System.nanoTime();
    }

    public static void endWorldRender() {
        if (worldRenderStartNanos == 0L) return;
        long started = worldRenderStartNanos;
        worldRenderStartNanos = 0L;
        if (!isCollectionEnabled()) return;
        recordWorldRender((System.nanoTime() - started) / 1_000_000.0);
    }

    public static double getLastClientTickMs() { return lastClientTickMs; }
    public static double getAverageClientTickMs() { return averageClientTickMs; }
    public static double getLastWorldRenderMs() { return lastWorldRenderMs; }
    public static double getAverageWorldRenderMs() { return averageWorldRenderMs; }

    public static void reset() {
        clientTickStartNanos = 0L;
        worldRenderStartNanos = 0L;
        lastClientTickMs = 0.0;
        averageClientTickMs = 0.0;
        lastWorldRenderMs = 0.0;
        averageWorldRenderMs = 0.0;
    }

    private static void recordClientTick(double elapsedMs) {
        lastClientTickMs = elapsedMs;
        averageClientTickMs = smooth(averageClientTickMs, elapsedMs);
    }

    private static void recordWorldRender(double elapsedMs) {
        lastWorldRenderMs = elapsedMs;
        averageWorldRenderMs = smooth(averageWorldRenderMs, elapsedMs);
    }

    private static double smooth(double previous, double current) {
        return previous <= 0.0 ? current : previous + (current - previous) * 0.12;
    }
}
