package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

public final class CpsHudFeature extends Feature {
    public static final CpsHudFeature INSTANCE = new CpsHudFeature();

    private CpsHudFeature() {
        super("CPS HUD", "Displays recent left and right mouse clicks per second.", Category.HUD);
    }

    @Override public void saveConfig() {
        DropperUtilsConfig.get().cpsHud = isEnabled();
        DropperUtilsConfig.save();
    }

    @Override public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().cpsHud);
    }
}
