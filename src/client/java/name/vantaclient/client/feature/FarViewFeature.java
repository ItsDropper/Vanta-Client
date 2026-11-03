package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.util.FarViewController;

public final class FarViewFeature extends Feature {
    public static final FarViewFeature INSTANCE = new FarViewFeature();

    private FarViewFeature() {
        super("FarView", "Extended vanilla view-distance control with chunk-update prioritization, quality profiles and optional adaptive scaling. Nvidium is detected optionally; it is never required.", Category.VISUAL);
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

    public static int getTargetRenderDistance() {
        int value = DropperUtilsConfig.get().farViewRenderDistance;
        return value <= 0 ? 24 : Math.max(2, Math.min(32, value));
    }

    public static void setTargetRenderDistance(int value) {
        DropperUtilsConfig.get().farViewRenderDistance = Math.max(2, Math.min(32, value));
        DropperUtilsConfig.save();
        FarViewController.resetAdaptiveTarget();
    }

    public static String getQuality() {
        String value = DropperUtilsConfig.get().farViewQuality;
        return switch (value == null ? "BALANCED" : value) {
            case "PERFORMANCE", "BALANCED", "HIGH", "ULTRA" -> value;
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

    public static boolean isAdaptiveEnabled() {
        return DropperUtilsConfig.get().farViewAdaptive;
    }

    public static void setAdaptiveEnabled(boolean value) {
        DropperUtilsConfig.get().farViewAdaptive = value;
        DropperUtilsConfig.save();
        FarViewController.resetAdaptiveTarget();
    }
}
