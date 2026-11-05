package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.util.FarTerrainRenderer;
import name.vantaclient.client.util.FarViewController;

public final class FarViewFeature extends Feature {
    public static final FarViewFeature INSTANCE = new FarViewFeature();

    private FarViewFeature() {
        super("FarView", "Singleplayer distant-terrain LOD up to 2,048 chunks, with adjustable vanilla detail distance, biome-aware materials, blocky distant tree impostors, quality profiles and adaptive budgets. Structures, caves and post-generation edits are not represented.", Category.VISUAL);
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
        DropperUtilsConfig.save();
        FarViewController.applyCurrentProfile();
    }

    /** Target grid-cell spacing for distant terrain, expressed in chunks. */
    public static int getSampleSpacingChunks() {
        return switch (getQuality()) {
            case "PERFORMANCE" -> 64;
            case "HIGH" -> 16;
            case "ULTRA" -> 8;
            default -> 32;
        };
    }

    public static boolean isAdaptiveEnabled() {
        return DropperUtilsConfig.get().farViewAdaptive;
    }

    public static void setAdaptiveEnabled(boolean value) {
        DropperUtilsConfig.get().farViewAdaptive = value;
        DropperUtilsConfig.save();
        FarViewController.resetAdaptiveTarget();
    }
}
