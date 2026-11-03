package name.vantaclient.client.util;

import name.vantaclient.client.feature.FarViewFeature;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;

/**
 * Applies FarView's client-side view-distance and chunk-builder profile.
 *
 * This first implementation deliberately stays on Minecraft's supported
 * client options. It does not change simulation distance, server settings,
 * chunk packet handling, or renderer internals.
 */
public final class FarViewController {
    private static final int MIN_ADAPTIVE_DISTANCE = 8;
    private static final int ADAPTIVE_INTERVAL_TICKS = 40;

    private static boolean capturedOriginalOptions;
    private static int originalRenderDistance;
    private static PrioritizeChunkUpdates originalChunkPriority;
    private static double originalChunkFade;
    private static String appliedQuality;

    private static int adaptiveDistance = -1;
    private static int ticksUntilAdaptiveUpdate;

    private FarViewController() {}

    public static void tick(Minecraft client) {
        if (!FarViewFeature.INSTANCE.isEnabled() || client.player == null || client.level == null) {
            // Restore the user's options when leaving a world, not only when manually disabling FarView.
            disable();
            return;
        }

        captureOriginalOptions(client);
        updateAdaptiveDistance(client);

        int desiredDistance = adaptiveDistance >= 0
                ? adaptiveDistance : FarViewFeature.getTargetRenderDistance();
        desiredDistance = Math.max(2, Math.min(32, desiredDistance));

        if (client.options.renderDistance().get() != desiredDistance) {
            client.options.renderDistance().set(desiredDistance);
        }
        applyProfile(client);
    }

    private static void captureOriginalOptions(Minecraft client) {
        if (capturedOriginalOptions) return;
        originalRenderDistance = client.options.renderDistance().get();
        originalChunkPriority = client.options.prioritizeChunkUpdates().get();
        originalChunkFade = client.options.chunkSectionFadeInTime().get();
        capturedOriginalOptions = true;
        adaptiveDistance = FarViewFeature.getTargetRenderDistance();
    }

    private static void updateAdaptiveDistance(Minecraft client) {
        if (!FarViewFeature.isAdaptiveEnabled()) {
            adaptiveDistance = FarViewFeature.getTargetRenderDistance();
            ticksUntilAdaptiveUpdate = 0;
            return;
        }

        if (ticksUntilAdaptiveUpdate-- > 0) return;
        ticksUntilAdaptiveUpdate = ADAPTIVE_INTERVAL_TICKS;

        int target = FarViewFeature.getTargetRenderDistance();
        if (adaptiveDistance < 0) adaptiveDistance = target;
        adaptiveDistance = Math.min(adaptiveDistance, target);

        double renderMs = ClientPerformanceMetrics.getAverageWorldRenderMs();
        double tickMs = ClientPerformanceMetrics.getAverageClientTickMs();
        if (renderMs <= 0.0 && tickMs <= 0.0) return;

        boolean overloaded = renderMs > 22.0 || tickMs > 28.0 || client.getFps() < 45;
        boolean hasHeadroom = renderMs > 0.0 && renderMs < 12.0
                && (tickMs <= 0.0 || tickMs < 12.0) && client.getFps() >= 100;

        if (overloaded) {
            adaptiveDistance = Math.max(Math.min(MIN_ADAPTIVE_DISTANCE, target), adaptiveDistance - 2);
        } else if (hasHeadroom && adaptiveDistance < target) {
            adaptiveDistance = Math.min(target, adaptiveDistance + 1);
        }
    }

    private static void applyProfile(Minecraft client) {
        String quality = FarViewFeature.getQuality();
        if (quality.equals(appliedQuality)) return;

        switch (quality) {
            case "PERFORMANCE" -> {
                client.options.prioritizeChunkUpdates().set(PrioritizeChunkUpdates.PLAYER_AFFECTED);
                client.options.chunkSectionFadeInTime().set(0.0);
            }
            case "HIGH" -> {
                client.options.prioritizeChunkUpdates().set(PrioritizeChunkUpdates.NEARBY);
                client.options.chunkSectionFadeInTime().set(0.22);
            }
            case "ULTRA" -> {
                client.options.prioritizeChunkUpdates().set(PrioritizeChunkUpdates.NEARBY);
                client.options.chunkSectionFadeInTime().set(0.32);
            }
            default -> {
                client.options.prioritizeChunkUpdates().set(PrioritizeChunkUpdates.NEARBY);
                client.options.chunkSectionFadeInTime().set(0.12);
            }
        }
        appliedQuality = quality;
    }

    public static void applyCurrentProfile() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && FarViewFeature.INSTANCE.isEnabled()) {
            captureOriginalOptions(client);
            appliedQuality = null;
            applyProfile(client);
        }
    }

    public static void resetAdaptiveTarget() {
        adaptiveDistance = FarViewFeature.getTargetRenderDistance();
        ticksUntilAdaptiveUpdate = 0;
    }

    public static void disable() {
        Minecraft client = Minecraft.getInstance();
        if (capturedOriginalOptions && client.options != null) {
            client.options.renderDistance().set(originalRenderDistance);
            client.options.prioritizeChunkUpdates().set(originalChunkPriority);
            client.options.chunkSectionFadeInTime().set(originalChunkFade);
        }
        capturedOriginalOptions = false;
        appliedQuality = null;
        adaptiveDistance = -1;
        ticksUntilAdaptiveUpdate = 0;
    }

    public static boolean isNvidiumLoaded() {
        return FabricLoader.getInstance().isModLoaded("nvidium");
    }

    public static int getEffectiveRenderDistance() {
        Minecraft client = Minecraft.getInstance();
        return client.options == null ? 0 : client.options.getEffectiveRenderDistance();
    }

    public static String getRendererStatus() {
        return isNvidiumLoaded()
                ? "Nvidium detected (optional; native integration pending)"
                : "Vanilla renderer (Nvidium optional)";
    }
}
