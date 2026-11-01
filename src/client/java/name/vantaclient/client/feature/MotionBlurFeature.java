package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.util.MotionBlurRenderer;

public class MotionBlurFeature extends Feature {
    public static final MotionBlurFeature INSTANCE = new MotionBlurFeature();

    private MotionBlurFeature() {
        super("Motion Blur", "Natural camera-motion blur with adjustable strength, sensitivity and sample quality.", Category.VISUAL);
    }

    public static float getStrength() { return DropperUtilsConfig.get().motionBlurStrength; }
    public static void setStrength(float value) {
        DropperUtilsConfig.get().motionBlurStrength = Math.max(0.05f, Math.min(value, 0.85f));
        DropperUtilsConfig.save();
    }

    public static int getSamples() { return DropperUtilsConfig.get().motionBlurSamples; }
    public static void setSamples(int value) {
        DropperUtilsConfig.get().motionBlurSamples = Math.max(2, Math.min(value, 12));
        DropperUtilsConfig.save();
    }

    public static float getSensitivity() { return DropperUtilsConfig.get().motionBlurSensitivity; }
    public static void setSensitivity(float value) {
        DropperUtilsConfig.get().motionBlurSensitivity = Math.max(0.25f, Math.min(value, 2.0f));
        DropperUtilsConfig.save();
    }

    @Override public void onEnable() {
        DropperUtilsConfig.get().motionBlur = true;
        DropperUtilsConfig.save();
        MotionBlurRenderer.reset();
    }

    @Override public void onDisable() {
        DropperUtilsConfig.get().motionBlur = false;
        DropperUtilsConfig.save();
        MotionBlurRenderer.reset();
    }

    @Override public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().motionBlur);
    }
}
