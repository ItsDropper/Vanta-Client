package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.util.MotionBlurRenderer;

public class MotionBlurFeature extends Feature {
    public static final MotionBlurFeature INSTANCE = new MotionBlurFeature();

    private MotionBlurFeature() {
        super("Motion Blur", "Temporal frame-blending motion blur with adjustable persistence.", Category.VISUAL);
    }

    public static float getStrength() { return DropperUtilsConfig.get().motionBlurStrength; }

    public static void setStrength(float value) {
        DropperUtilsConfig.get().motionBlurStrength = Math.max(0.0f, Math.min(value, 0.95f));
        DropperUtilsConfig.save();
        MotionBlurRenderer.reset();
    }

    @Override
    public void onEnable() {
        DropperUtilsConfig.get().motionBlur = true;
        DropperUtilsConfig.save();
        MotionBlurRenderer.reset();
    }

    @Override
    public void onDisable() {
        DropperUtilsConfig.get().motionBlur = false;
        DropperUtilsConfig.save();
        MotionBlurRenderer.reset();
    }

    @Override
    public void loadConfig() {
        setEnabled(DropperUtilsConfig.get().motionBlur);
    }
}
