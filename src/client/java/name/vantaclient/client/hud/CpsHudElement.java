package name.vantaclient.client.hud;

import name.vantaclient.client.feature.CpsHudFeature;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.hudeditor.HudElement;
import name.vantaclient.client.feature.PerformanceOptimizerFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class CpsHudElement extends HudElement {
    public CpsHudElement() {
        super(name.vantaclient.client.config.DropperUtilsConfig.get().cpsHudX,
                name.vantaclient.client.config.DropperUtilsConfig.get().cpsHudY);
        scale = name.vantaclient.client.config.DropperUtilsConfig.get().cpsHudScale;
    }

    @Override public boolean shouldRender() {
        return CpsHudFeature.INSTANCE.isEnabled() || HudEditorFeature.INSTANCE.isEnabled();
    }

    @Override protected void renderElement(GuiGraphics graphics) {
        if (!CpsHudFeature.INSTANCE.isEnabled() && !HudEditorFeature.INSTANCE.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        graphics.drawString(mc.font, getDisplayText(), 0, 0, 0xFFF0F3FA);
    }

    @Override public void savePosition() {
        var config = name.vantaclient.client.config.DropperUtilsConfig.get();
        config.cpsHudX = xPercent;
        config.cpsHudY = yPercent;
        name.vantaclient.client.config.DropperUtilsConfig.save();
    }

    @Override public void setScale(float value) {
        super.setScale(value);
        name.vantaclient.client.config.DropperUtilsConfig.get().cpsHudScale = scale;
        name.vantaclient.client.config.DropperUtilsConfig.save();
    }

    private String getDisplayText() {
        return PerformanceOptimizerFeature.INSTANCE.isEnabled()
                ? HudPerformanceCache.getCpsText()
                : "CPS  " + ClickRateTracker.getSnapshotLeftCps()
                + " | " + ClickRateTracker.getSnapshotRightCps();
    }

    @Override
    public int getWidth() {
        return Math.max(1, Minecraft.getInstance().font.width(getDisplayText()));
    }

    @Override public int getHeight() { return Minecraft.getInstance().font.lineHeight; }
}
