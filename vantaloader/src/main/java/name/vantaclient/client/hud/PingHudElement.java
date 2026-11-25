package name.vantaclient.client.hud;

import name.vantaclient.client.feature.PingHudFeature;
import name.vantaclient.client.feature.HudEditorFeature;
import name.vantaclient.client.hudeditor.HudElement;
import name.vantaclient.client.feature.PerformanceOptimizerFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class PingHudElement extends HudElement {
    private String snapshotText = "PING  --";
    private long lastSnapshotTick = Long.MIN_VALUE;
    private Object snapshotWorld;
    private Object snapshotPlayer;
    private Object snapshotConnection;
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
        refreshPingSnapshot(mc);
        return snapshotText;
    }

    private void refreshPingSnapshot(Minecraft mc) {
        var connection = mc.getConnection();
        if (mc.player == null || mc.level == null || connection == null) {
            snapshotText = "PING  --";
            lastSnapshotTick = Long.MIN_VALUE;
            snapshotWorld = null;
            snapshotPlayer = null;
            snapshotConnection = null;
            return;
        }

        long gameTime = mc.level.getGameTime();
        if (!shouldRefreshPingSnapshot(lastSnapshotTick, gameTime,
                snapshotWorld, mc.level, snapshotPlayer, mc.player,
                snapshotConnection, connection)) return;

        var info = connection.getPlayerInfo(mc.player.getUUID());
        snapshotText = info == null ? "PING  --" : "PING  " + info.getLatency() + " ms";
        lastSnapshotTick = gameTime;
        snapshotWorld = mc.level;
        snapshotPlayer = mc.player;
        snapshotConnection = connection;
    }

    public static boolean shouldRefreshPingSnapshot(long lastGameTime, long currentGameTime,
                                                     Object previousWorld, Object currentWorld,
                                                     Object previousPlayer, Object currentPlayer,
                                                     Object previousConnection, Object currentConnection) {
        return lastGameTime != currentGameTime
                || previousWorld != currentWorld
                || previousPlayer != currentPlayer
                || previousConnection != currentConnection;
    }

    @Override
    public int getWidth() {
        Minecraft mc = Minecraft.getInstance();
        return Math.max(1, mc.font.width(getDisplayText(mc)));
    }

    @Override public int getHeight() { return Minecraft.getInstance().font.lineHeight; }
}
