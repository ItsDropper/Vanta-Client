package name.vantaclient.client.feature;

import name.vantaclient.client.config.DropperUtilsConfig;
import net.minecraft.client.KeyMapping;

public class ZoomFeature extends Feature {

    public static final ZoomFeature INSTANCE = new ZoomFeature();

    private static boolean zooming = false;
    private static int scrollSteps = 0;

    private static int zoomFov;
    private static float zoomSpeed;

    private ZoomFeature() {
        super("Zoom", "Hold the zoom key to zoom the camera. Scroll while held for extra zoom.", Category.VISUAL);
    }

    public void tick(KeyMapping zoomKey) {
        boolean wasZooming = zooming;
        zooming = isEnabled() && zoomKey.isDown();

        if (wasZooming && !zooming) {
            scrollSteps = 0;
        }
    }

    public static boolean isZooming() {
        return zooming;
    }

    public static void scrollZoom(double amount) {
        if (!zooming || amount == 0.0) {
            return;
        }

        int direction = amount > 0.0 ? 1 : -1;
        scrollSteps = Math.max(0, Math.min(30, scrollSteps + direction));
    }

    public static float getZoomMultiplier() {
        return (Math.max(1, Math.min(zoomFov, 70)) / 70.0f)
                * (float) Math.pow(DropperUtilsConfig.get().zoomScrollStrength, scrollSteps);
    }

    public static int getZoomFov() {
        return zoomFov;
    }

    public static void setZoomFov(int value) {
        zoomFov = Math.max(1, Math.min(value, 70));
        DropperUtilsConfig.get().zoomFov = zoomFov;
        DropperUtilsConfig.save();
    }

    public static float getZoomSpeed() {
        return DropperUtilsConfig.get().smoothZoom ? zoomSpeed : 1.0f;
    }

    public static float getConfiguredZoomSpeed() {
        return zoomSpeed;
    }

    public static void setZoomSpeed(float value) {
        zoomSpeed = Math.max(0.01f, Math.min(value, 1.0f));
        DropperUtilsConfig.get().zoomSpeed = zoomSpeed;
        DropperUtilsConfig.save();
    }

    public static float getScrollStrength() {
        return DropperUtilsConfig.get().zoomScrollStrength;
    }

    public static void setScrollStrength(float value) {
        DropperUtilsConfig.get().zoomScrollStrength = Math.max(0.35f, Math.min(value, 0.90f));
        DropperUtilsConfig.save();
    }

    public static boolean isSmoothZoom() {
        return DropperUtilsConfig.get().smoothZoom;
    }

    public static void setSmoothZoom(boolean value) {
        DropperUtilsConfig.get().smoothZoom = value;
        DropperUtilsConfig.save();
    }

    @Override
    public void loadConfig() {
        DropperUtilsConfig config = DropperUtilsConfig.get();
        zoomFov = config.zoomFov;
        zoomSpeed = config.zoomSpeed;
    }
}