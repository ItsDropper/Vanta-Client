package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

public final class KeystrokesFeature extends Feature {
    public static final KeystrokesFeature INSTANCE = new KeystrokesFeature();

    private KeystrokesFeature() {
        super("Keystrokes", "Shows live movement and jump key states.", Category.HUD);
    }

    @Override public void saveConfig() {
        DropperUtilsConfig.get().keystrokesHud = isEnabled();
        DropperUtilsConfig.save();
    }

    @Override public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().keystrokesHud);
    }
}
