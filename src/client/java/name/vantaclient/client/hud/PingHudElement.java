package name.vantaclient.client.hud;

import name.vantaclient.client.feature.PingHudFeature;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.hudeditor.HudElement;
import name.vantaclient.client.feature.PerformanceOptimizerFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class PingHudElement extends HudElement {
    public PingHudElement() {
        super(name.vantaclient.client.config.DropperUtilsConfig.get().pingHudX,
                name.vantaclient.client.config.DropperUtilsConfig.get().pingHudY);
        scale = name.vantaclient.client.config.DropperUtilsConfig.get().pingHudScale;
    }

    @Override public boolean shouldRender() {
        return PingHudFeature.INSTANCE.isEnabled() || HudEditorFeature.INSTANCE.isEnabled();
    }

    @Override protected void renderElement(GuiGraphics graphics) {
        if (!PingHudFeature.INSTANCE.isEnabled() && !HudEditorFeature.INSTANCE.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        graphics.drawString(mc.font, getDisplayText(mc), 0, 0, 0xFFF0F3FA);
    }

    @Override public void savePosition() {
        var config = name.vantaclient.client.config.DropperUtilsConfig.get();
        config.pingHudX = xPercent;
        config.pingHudY = yPercent;
        name.vantaclient.client.config.DropperUtilsConfig.save();
    }

    @Override public void setScale(float value) {
        super.setScale(value);
        name.vantaclient.client.config.DropperUtilsConfig.get().pingHudScale = scale;
        name.vantaclient.client.config.DropperUtilsConfig.save();
    }

    private String getDisplayText(Minecraft mc) {
        if (PerformanceOptimizerFeature.INSTANCE.isEnabled()) {
            return HudPerformanceCache.getPingText();
        }
        if (mc.player != null && mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) return "PING  " + info.getLatency() + " ms";
        }
        return "PING  --";
    }

    @Override
    public int getWidth() {
        Minecraft mc = Minecraft.getInstance();
        return Math.max(1, mc.font.width(getDisplayText(mc)));
    }

    @Override public int getHeight() { return Minecraft.getInstance().font.lineHeight; }
}
