package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

public class PotionEffectsFeature extends Feature {

    public static final PotionEffectsFeature INSTANCE =
            new PotionEffectsFeature();

    private PotionEffectsFeature() {
        super(
                "Potion Effects",
                "Displays your active potion effects.",
                Category.HUD
        );
    }

    @Override
    public void onEnable() {
        DropperUtilsConfig.get().potionEffects = true;
        DropperUtilsConfig.save();
    }

    @Override
    public void onDisable() {
        DropperUtilsConfig.get().potionEffects = false;
        DropperUtilsConfig.save();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().potionEffects);
    }
}