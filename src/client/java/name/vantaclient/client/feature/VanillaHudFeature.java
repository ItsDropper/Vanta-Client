package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

/** Applies the positions configured for individual vanilla HUD components. */
public final class VanillaHudFeature extends Feature {
    public static final VanillaHudFeature INSTANCE = new VanillaHudFeature();

    private VanillaHudFeature() {
        super("VanillaHUD", "Move vanilla HUD components independently in the HUD editor.", Category.HUD);
    }

    @Override
    public void saveConfig() {
        DropperUtilsConfig.get().vanillaHud = isEnabled();
        DropperUtilsConfig.save();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().vanillaHud);
    }
}
