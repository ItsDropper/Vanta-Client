package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

public class NoExplosionEffectsFeature extends Feature {

    public static final NoExplosionEffectsFeature INSTANCE =
            new NoExplosionEffectsFeature();

    private NoExplosionEffectsFeature() {
        super(
                "No CPVP Effects",
                "Removes visual explosion- and end crystal effects.",
                Category.VISUAL
        );
    }


    @Override
    public void onEnable() {
        DropperUtilsConfig.get().noExplosionEffects = true;
        DropperUtilsConfig.save();
    }


    @Override
    public void onDisable() {
        DropperUtilsConfig.get().noExplosionEffects = false;
        DropperUtilsConfig.save();
    }


    @Override
    public void loadConfig() {
        setEnabled(
                DropperUtilsConfig.get().noExplosionEffects
        );
    }
}