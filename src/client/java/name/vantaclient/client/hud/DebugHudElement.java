package name.vantaclient.client.hud;

import name.vantaclient.client.config.DropperUtilsConfig;
import name.vantaclient.client.feature.DebugHudFeature;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.hudeditor.HudElement;
import name.vantaclient.client.util.AnchorStats;
import name.vantaclient.client.util.AnchorTracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class DebugHudElement extends HudElement {
    private long lastSnapshotTick = Long.MIN_VALUE;
    private String trackedText = "Tracked: 0";
    private String averageScanText = "Avg Scan: 0.000 ms";
    private String lastScanText = "Last Scan: 0.000 ms";

    public DebugHudElement() {
        super(DropperUtilsConfig.get().debugHudX, DropperUtilsConfig.get().debugHudY);
        scale = DropperUtilsConfig.get().debugHudScale;
    }

    @Override
    public boolean shouldRender() {
        return DebugHudFeature.INSTANCE.isEnabled() || HudEditorFeature.INSTANCE.isEnabled();
    }

    @Override
    protected void renderElement(GuiGraphics graphics) {
        if (!DebugHudFeature.INSTANCE.isEnabled() && !HudEditorFeature.INSTANCE.isEnabled()) return;
        refreshSnapshot();
        Minecraft mc = Minecraft.getInstance();
        graphics.drawString(mc.font, trackedText, 0, 0, 0xFFFFFFFF);
        graphics.drawString(mc.font, averageScanText, 0, 11, 0xFF00FF00);
        graphics.drawString(mc.font, lastScanText, 0, 22, 0xFFFFFF00);
    }

    private void refreshSnapshot() {
        Minecraft mc = Minecraft.getInstance();
        long gameTime = mc.level == null ? 0L : mc.level.getGameTime();
        if (lastSnapshotTick == gameTime) return;
        lastSnapshotTick = gameTime;
        trackedText = "Tracked: " + AnchorTracker.getTrackedAnchors().size();
        averageScanText = String.format("Avg Scan: %.3f ms", AnchorStats.getAverageScanTimeMs());
        lastScanText = String.format("Last Scan: %.3f ms", AnchorStats.getLastScanTimeMs());
    }

    @Override public int getWidth() { return 120; }
    @Override public int getHeight() { return 35; }

    @Override
    public void savePosition() {
        DropperUtilsConfig.get().debugHudX = xPercent;
        DropperUtilsConfig.get().debugHudY = yPercent;
        DropperUtilsConfig.save();
    }

    @Override
    public void setScale(float value) {
        scale = Math.max(0.25f, Math.min(value, 3.0f));
        DropperUtilsConfig.get().debugHudScale = scale;
        DropperUtilsConfig.save();
    }
}
