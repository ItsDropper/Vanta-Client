package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

public final class FpsHudFeature extends Feature {
    public static final FpsHudFeature INSTANCE = new FpsHudFeature();

    private FpsHudFeature() {
        super("FPS HUD", "Displays your current frames per second.", Category.HUD);
    }

    @Override public void saveConfig() {
        DropperUtilsConfig.get().fpsHud = isEnabled();
        DropperUtilsConfig.save();
    }

    @Override public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().fpsHud);
    }
}
