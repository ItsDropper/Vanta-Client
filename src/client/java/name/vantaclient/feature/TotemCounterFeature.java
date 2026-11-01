package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

public class TotemCounterFeature extends Feature {

    public static final TotemCounterFeature INSTANCE = new TotemCounterFeature();

    private TotemCounterFeature() {
        super(
                "Totem Counter",
                "Shows how many totems you have available.",
                Category.HUD
        );
    }

    @Override
    public void onEnable() {
        // if you have any enable code put it here
    }

    @Override
    public void onDisable() {
        // if you have any disable code put it here
    }

    @Override
    public void saveConfig() {
        DropperUtilsConfig.get().totemCounter = isEnabled();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().totemCounter);
    }
}