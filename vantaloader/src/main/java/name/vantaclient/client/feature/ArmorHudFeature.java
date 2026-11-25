package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

public class ArmorHudFeature extends Feature {

    public static final ArmorHudFeature INSTANCE = new ArmorHudFeature();

    private ArmorHudFeature() {
        super(
                "Armor HUD",
                "Displays your armor and durability.",
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
        DropperUtilsConfig.get().armorHud = isEnabled();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().armorHud);
    }
}