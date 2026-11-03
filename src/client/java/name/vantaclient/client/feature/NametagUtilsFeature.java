package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

/** Client-side scale and visibility controls for vanilla entity nameplates. */
public final class NametagUtilsFeature extends Feature {
    public static final NametagUtilsFeature INSTANCE = new NametagUtilsFeature();

    private NametagUtilsFeature() {
        super("Nametag Utils",
                "Adjusts vanilla nameplate size and optionally hides player nameplates.",
                Category.VISUAL);
    }

    public static float getScale() {
        float value = DropperUtilsConfig.get().nametagScale;
        return Float.isFinite(value) ? Math.max(0.5f, Math.min(2.0f, value)) : 1.0f;
    }

    public static void setScale(float value) {
        DropperUtilsConfig.get().nametagScale = Math.max(0.5f,
                Math.min(2.0f, Float.isFinite(value) ? value : 1.0f));
        DropperUtilsConfig.save();
    }

    public static boolean isHidePlayers() {
        return DropperUtilsConfig.get().nametagHidePlayers;
    }

    public static void setHidePlayers(boolean value) {
        DropperUtilsConfig.get().nametagHidePlayers = value;
        DropperUtilsConfig.save();
    }

    @Override
    public void saveConfig() {
        DropperUtilsConfig.save();
    }

    @Override
    public void loadConfig() {
        DropperUtilsConfig config = DropperUtilsConfig.get();
        if (!Float.isFinite(config.nametagScale)) config.nametagScale = 1.0f;
        config.nametagScale = Math.max(0.5f, Math.min(2.0f, config.nametagScale));
    }
}
