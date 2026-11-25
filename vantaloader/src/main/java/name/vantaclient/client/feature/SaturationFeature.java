package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

public class SaturationFeature extends Feature {

    public static final SaturationFeature INSTANCE = new SaturationFeature();

    private SaturationFeature() {
        super(
                "Saturation",
                "Displays your current saturation level.",
                Category.HUD
        );
    }

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }
    @Override
    public void saveConfig() {
        DropperUtilsConfig.get().saturation = isEnabled();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().saturation);
    }
}