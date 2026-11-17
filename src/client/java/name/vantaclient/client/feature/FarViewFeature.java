package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.util.FarTerrainRenderer;
import name.vantaclient.client.util.FarViewController;

public final class FarViewFeature extends Feature {
    public static final FarViewFeature INSTANCE = new FarViewFeature();

    private FarViewFeature() {
        super("FarView", "Singleplayer distant terrain with adaptive quadtree LOD, real surface snapshots from explored chunks, procedural terrain fallback with biome-blended materials and directional ridge shadows, captured voxel ambient occlusion, adjustable sampling, water, shading, tree silhouettes, adaptive background mesh pacing and cache budgets. It does not extend multiplayer chunk delivery.", Category.VISUAL);
    }

    @Override
    public void onEnable() {
        DropperUtilsConfig.get().farViewEnabled = true;
        DropperUtilsConfig.save();
    }

    @Override
    public void onDisable() {
        DropperUtilsConfig.get().farViewEnabled = false;
        DropperUtilsConfig.save();
        FarViewController.disable();
    }

    @Override
    public void saveConfig() {
        DropperUtilsConfig.get().farViewEnabled = isEnabled();
        DropperUtilsConfig.save();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().farViewEnabled);
    }

    /** Native Minecraft detail distance; remains capped at the vanilla maximum. */
    public static int getTargetRenderDistance() {
        int value = DropperUtilsConfig.get().farViewRenderDistance;
        return value <= 0 ? 24 : Math.max(2, Math.min(32, value));
    }

    public static void setTargetRenderDistance(int value) {
        DropperUtilsConfig.get().farViewRenderDistance = Math.max(2, Math.min(32, value));
        DropperUtilsConfig.save();
        FarViewController.resetAdaptiveTarget();
    }

    public static int getFarViewDistance() {
        return Math.max(32, Math.min(2048, DropperUtilsConfig.get().farViewDistance));
    }

    public static void setFarViewDistance(int value) {
        int rounded = Math.round(value / 16.0f) * 16;
        DropperUtilsConfig.get().farViewDistance = Math.max(32, Math.min(2048, rounded));
        DropperUtilsConfig.save();
    }

    public static String getQuality() {
        String value = DropperUtilsConfig.get().farViewQuality;
        String quality = value == null ? "BALANCED" : value;
        return switch (quality) {
            case "PERFORMANCE", "BALANCED", "HIGH", "ULTRA" -> quality;
            default -> "BALANCED";
        };
    }

    public static void setQuality(String value) {
        if (!"PERFORMANCE".equals(value) && !"BALANCED".equals(value)
                && !"HIGH".equals(value) && !"ULTRA".equals(value)) return;
        DropperUtilsConfig.get().farViewQuality = value;
        DropperUtilsConfig.get().farViewSampleSpacingChunks = switch (value) {
            case "PERFORMANCE" -> 64;
            case "HIGH" -> 16;
            case "ULTRA" -> 4;
            default -> 32;
        };
        DropperUtilsConfig.save();
        FarViewController.applyCurrentProfile();
    }

    /** Target grid-cell spacing for distant terrain, expressed in chunks. */
    public static int getSampleSpacingChunks() {
        return Math.max(2, Math.min(64,
                DropperUtilsConfig.get().farViewSampleSpacingChunks));
    }

    public static void setSampleSpacingChunks(int value) {
        DropperUtilsConfig.get().farViewSampleSpacingChunks = Math.max(2, Math.min(64, value));
        DropperUtilsConfig.save();
    }

    public static boolean isTerrainShadingEnabled() {
        return DropperUtilsConfig.get().farViewTerrainShading;
    }

    public static void setTerrainShadingEnabled(boolean value) {
        DropperUtilsConfig.get().farViewTerrainShading = value;
        DropperUtilsConfig.save();
    }

    public static boolean isWaterEnabled() {
        return DropperUtilsConfig.get().farViewWater;
    }

    public static void setWaterEnabled(boolean value) {
        DropperUtilsConfig.get().farViewWater = value;
        DropperUtilsConfig.save();
    }

    public static boolean isTreesEnabled() {
        return DropperUtilsConfig.get().farViewTrees;
    }

    public static void setTreesEnabled(boolean value) {
        DropperUtilsConfig.get().farViewTrees = value;
        DropperUtilsConfig.save();
    }

    public static int getCacheMegabytes() {
        return Math.max(64, Math.min(1024, DropperUtilsConfig.get().farViewCacheMegabytes));
    }

    public static void setCacheMegabytes(int value) {
        DropperUtilsConfig.get().farViewCacheMegabytes = Math.max(64, Math.min(1024, value));
        DropperUtilsConfig.save();
    }

    public static boolean isAdaptiveEnabled() {
        return DropperUtilsConfig.get().farViewAdaptive;
    }

    public static void setAdaptiveEnabled(boolean value) {
        DropperUtilsConfig.get().farViewAdaptive = value;
        DropperUtilsConfig.save();
        FarViewController.resetAdaptiveTarget();
    }

    /** Whether background FarView mesh work should yield under foreground frame pressure. */
    public static boolean isWorkerAdaptiveEnabled() {
        return DropperUtilsConfig.get().farViewWorkerAdaptive;
    }

    public static void setWorkerAdaptiveEnabled(boolean value) {
        DropperUtilsConfig.get().farViewWorkerAdaptive = value;
        DropperUtilsConfig.save();
    }
}
