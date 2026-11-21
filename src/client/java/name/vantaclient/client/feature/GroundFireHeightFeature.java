package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

/** Scales the geometry of vanilla ground-fire models without affecting the first-person overlay. */
public final class GroundFireHeightFeature extends Feature {
    public static final GroundFireHeightFeature INSTANCE = new GroundFireHeightFeature();

    private GroundFireHeightFeature() {
        super("Ground Fire Height", "Adjusts the height of fire rendered on blocks.", Category.VISUAL);
    }

    public static float getHeightScale() {
        float value = DropperUtilsConfig.get().groundFireHeight;
        return Float.isFinite(value) ? Math.max(0.25f, Math.min(2.0f, value)) : 1.0f;
    }

    public static void setHeightScale(float value) {
        DropperUtilsConfig.get().groundFireHeight = Float.isFinite(value)
                ? Math.max(0.25f, Math.min(2.0f, value))
                : 1.0f;
        DropperUtilsConfig.save();
    }

    @Override
    public void onEnable() {
        DropperUtilsConfig.get().groundFireHeightEnabled = true;
        DropperUtilsConfig.save();
    }

    @Override
    public void onDisable() {
        DropperUtilsConfig.get().groundFireHeightEnabled = false;
        DropperUtilsConfig.save();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().groundFireHeightEnabled);
    }
}
