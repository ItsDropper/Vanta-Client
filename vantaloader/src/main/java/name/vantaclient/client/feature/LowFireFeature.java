package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

public class LowFireFeature extends Feature {

    public static final LowFireFeature INSTANCE =
            new LowFireFeature();

    private LowFireFeature() {
        super(
                "Low Fire",
                "Reduces the size of the fire overlay.",
                Category.VISUAL
        );
    }

    public static boolean isOverlayHidden() {
        return name.vantaclient.client.config.DropperUtilsConfig.get().lowFireHideOverlay;
    }

    public static void setOverlayHidden(boolean hidden) {
        name.vantaclient.client.config.DropperUtilsConfig.get().lowFireHideOverlay = hidden;
        name.vantaclient.client.config.DropperUtilsConfig.save();
    }

    @Override
    public void onEnable() {
        DropperUtilsConfig.get().lowFire = true;
        DropperUtilsConfig.save();
    }

    @Override
    public void onDisable() {
        DropperUtilsConfig.get().lowFire = false;
        DropperUtilsConfig.save();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().lowFire);
    }
}