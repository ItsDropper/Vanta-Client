package name.vantaclient.client.hud;

import name.vantaclient.client.feature.FpsHudFeature;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.hudeditor.HudElement;
import name.vantaclient.client.feature.PerformanceOptimizerFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class FpsHudElement extends HudElement {
    public FpsHudElement() {
        super(name.vantaclient.client.config.DropperUtilsConfig.get().fpsHudX,
                name.vantaclient.client.config.DropperUtilsConfig.get().fpsHudY);
        scale = name.vantaclient.client.config.DropperUtilsConfig.get().fpsHudScale;
    }

    @Override public boolean shouldRender() {
        return FpsHudFeature.INSTANCE.isEnabled() || HudEditorFeature.INSTANCE.isEnabled();
    }

    @Override protected void renderElement(GuiGraphics graphics) {
        if (!FpsHudFeature.INSTANCE.isEnabled() && !HudEditorFeature.INSTANCE.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        graphics.drawString(mc.font, getDisplayText(mc), 0, 0, 0xFFF0F3FA);
    }

    @Override public void savePosition() {
        var config = name.vantaclient.client.config.DropperUtilsConfig.get();
        config.fpsHudX = xPercent;
        config.fpsHudY = yPercent;
        name.vantaclient.client.config.DropperUtilsConfig.save();
    }

    @Override public void setScale(float value) {
        super.setScale(value);
        name.vantaclient.client.config.DropperUtilsConfig.get().fpsHudScale = scale;
        name.vantaclient.client.config.DropperUtilsConfig.save();
    }

    private String getDisplayText(Minecraft mc) {
        return PerformanceOptimizerFeature.INSTANCE.isEnabled()
                ? HudPerformanceCache.getFpsText() : "FPS  " + mc.getFps();
    }

    @Override
    public int getWidth() {
        Minecraft mc = Minecraft.getInstance();
        return Math.max(1, mc.font.width(getDisplayText(mc)));
    }

    @Override public int getHeight() { return Minecraft.getInstance().font.lineHeight; }
}
