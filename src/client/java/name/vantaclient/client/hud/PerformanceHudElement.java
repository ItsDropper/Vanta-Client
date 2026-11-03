package name.vantaclient.client.hud;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.feature.PerformanceOptimizerFeature;
import name.vantaclient.client.hudeditor.HudElement;
import name.vantaclient.client.util.ClientPerformanceMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class PerformanceHudElement extends HudElement {
    private long lastUpdateTick = Long.MIN_VALUE;
    private String fpsText = "FPS  --";
    private String worldText = "WORLD  -- ms";
    private String tickText = "TICK  -- ms";

    public PerformanceHudElement() {
        super(DropperUtilsConfig.get().performanceHudX, DropperUtilsConfig.get().performanceHudY);
        scale = DropperUtilsConfig.get().performanceHudScale;
    }

    @Override
    public boolean shouldRender() {
        return PerformanceOptimizerFeature.INSTANCE.isEnabled() || HudEditorFeature.INSTANCE.isEnabled();
    }

    @Override
    protected void renderElement(GuiGraphics graphics) {
        if (!PerformanceOptimizerFeature.INSTANCE.isEnabled() && !HudEditorFeature.INSTANCE.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        refreshSnapshot(mc);
        graphics.drawString(mc.font, "VANTA PERFORMANCE", 0, 0, 0xFF8CB8FF);
        graphics.drawString(mc.font, fpsText, 0, 11, 0xFFF0F3FA);
        graphics.drawString(mc.font, worldText, 0, 21, 0xFFD6DCE8);
        graphics.drawString(mc.font, tickText, 0, 31, 0xFFD6DCE8);
    }

    private void refreshSnapshot(Minecraft mc) {
        long gameTime = mc.level == null ? 0L : mc.level.getGameTime();
        if (lastUpdateTick != Long.MIN_VALUE && gameTime >= lastUpdateTick
                && gameTime - lastUpdateTick < 5L) return;
        lastUpdateTick = gameTime;
        if (mc.level == null) {
            fpsText = "FPS  " + mc.getFps();
            worldText = "WORLD  -- ms";
            tickText = "TICK  -- ms";
            return;
        }
        fpsText = "FPS  " + mc.getFps();
        worldText = "WORLD  " + format(ClientPerformanceMetrics.getAverageWorldRenderMs()) + " ms";
        tickText = "TICK  " + format(ClientPerformanceMetrics.getAverageClientTickMs()) + " ms";
    }

    private static String format(double value) {
        if (!Double.isFinite(value) || value <= 0.0) return "--";
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }

    @Override public void savePosition() {
        DropperUtilsConfig.get().performanceHudX = xPercent;
        DropperUtilsConfig.get().performanceHudY = yPercent;
        DropperUtilsConfig.save();
    }

    @Override
    public void setScale(float value) {
        super.setScale(value);
        DropperUtilsConfig.get().performanceHudScale = scale;
        DropperUtilsConfig.save();
    }

    @Override public int getWidth() { return 130; }
    @Override public int getHeight() { return 42; }
}
