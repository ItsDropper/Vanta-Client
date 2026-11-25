package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

public final class PingHudFeature extends Feature {
    public static final PingHudFeature INSTANCE = new PingHudFeature();

    private PingHudFeature() {
        super("Ping HUD", "Displays your current server latency.", Category.HUD);
    }

    @Override public void saveConfig() {
        DropperUtilsConfig.get().pingHud = isEnabled();
        DropperUtilsConfig.save();
    }

    @Override public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().pingHud);
    }
}
