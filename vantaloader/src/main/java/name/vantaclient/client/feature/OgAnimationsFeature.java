package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;

/** Classic first-person swing animation profiles. */
public final class OgAnimationsFeature extends Feature {
    public static final OgAnimationsFeature INSTANCE = new OgAnimationsFeature();

    public enum Preset {
        CLASSIC_1_8("Classic 1.8.9"),
        SOFT_LEGACY("Soft Legacy");

        private final String label;
        Preset(String label) { this.label = label; }
        public String label() { return label; }
    }

    private static Preset preset = Preset.CLASSIC_1_8;

    private OgAnimationsFeature() {
        super("OG Animations", "Restores a classic first-person swing animation with adjustable legacy profiles.", Category.VISUAL);
    }

    public static Preset getPreset() {
        return preset;
    }

    public static String getPresetLabel() {
        return preset.label();
    }

    public static void setPreset(String value) {
        if (value == null) return;
        for (Preset candidate : Preset.values()) {
            if (candidate.name().equalsIgnoreCase(value)
                    || candidate.label().equalsIgnoreCase(value)) {
                setPreset(candidate);
                return;
            }
        }
    }

    public static void setPreset(Preset value) {
        if (value == null) return;
        preset = value;
        DropperUtilsConfig.get().ogAnimationPreset = value.name();
        DropperUtilsConfig.save();
    }

    @Override
    public void saveConfig() {
        DropperUtilsConfig.get().ogAnimations = isEnabled();
        DropperUtilsConfig.get().ogAnimationPreset = preset.name();
        DropperUtilsConfig.save();
    }

    @Override
    public void loadConfig() {
        DropperUtilsConfig config = DropperUtilsConfig.get();
        try {
            preset = Preset.valueOf(config.ogAnimationPreset);
        } catch (RuntimeException ignored) {
            preset = Preset.CLASSIC_1_8;
            config.ogAnimationPreset = preset.name();
            DropperUtilsConfig.save();
        }
        setEnabled(config.ogAnimations);
    }
}
