package name.vantaclient.client.hud;

import name.vantaclient.client.feature.FpsHudFeature;
import name.vantaclient.client.hudeditor.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class FpsHudElement extends HudElement {
    public FpsHudElement() { super(0.02f, 0.34f); }

    @Override protected void renderElement(GuiGraphics graphics) {
        if (!FpsHudFeature.INSTANCE.isEnabled()) return;
        int fps = Minecraft.getInstance().getFps();
        graphics.drawString(Minecraft.getInstance().font, "FPS  " + fps, 0, 0, 0xFFF0F3FA);
    }

    @Override public int getWidth() { return 76; }
    @Override public int getHeight() { return 10; }
}
