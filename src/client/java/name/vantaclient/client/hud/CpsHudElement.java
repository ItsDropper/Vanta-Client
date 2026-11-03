package name.vantaclient.client.hud;

import name.vantaclient.client.feature.CpsHudFeature;
import name.vantaclient.client.hudeditor.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class CpsHudElement extends HudElement {
    public CpsHudElement() { super(0.02f, 0.42f); }

    @Override protected void renderElement(GuiGraphics graphics) {
        if (!CpsHudFeature.INSTANCE.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        String value = "CPS  " + ClickRateTracker.getLeftCps()
                + " | " + ClickRateTracker.getRightCps();
        graphics.drawString(mc.font, value, 0, 0, 0xFFF0F3FA);
    }

    @Override public int getWidth() { return 86; }
    @Override public int getHeight() { return 10; }
}
