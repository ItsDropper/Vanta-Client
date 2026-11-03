package name.vantaclient.client.hud;

import name.vantaclient.client.feature.PingHudFeature;
import name.vantaclient.client.hudeditor.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class PingHudElement extends HudElement {
    public PingHudElement() { super(0.02f, 0.38f); }

    @Override protected void renderElement(GuiGraphics graphics) {
        if (!PingHudFeature.INSTANCE.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        String value = "PING  --";
        if (mc.player != null && mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) value = "PING  " + info.getLatency() + " ms";
        }
        graphics.drawString(mc.font, value, 0, 0, 0xFFF0F3FA);
    }

    @Override public int getWidth() { return 92; }
    @Override public int getHeight() { return 10; }
}
