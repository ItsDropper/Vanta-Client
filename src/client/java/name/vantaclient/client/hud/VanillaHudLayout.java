package name.vantaclient.client.hud;

import name.vantaclient.client.feature.VanillaHudFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Applies editor positions around the corresponding vanilla GUI render methods. */
public final class VanillaHudLayout {
    private VanillaHudLayout() {}

    public static void push(GuiGraphics graphics, VanillaHudElement.Part part) {
        graphics.pose().pushMatrix();
        if (!VanillaHudFeature.INSTANCE.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        var position = VanillaHudElement.getPosition(part);
        int x = Math.round(position.x * screenWidth);
        int y = Math.round(position.y * screenHeight);
        int defaultX = part.defaultX(screenWidth);
        int defaultY = part.defaultY(screenHeight);

        graphics.pose().translate(x, y);
        graphics.pose().scale(position.scale, position.scale);
        graphics.pose().translate(-defaultX, -defaultY);
    }

    public static void pop(GuiGraphics graphics) {
        graphics.pose().popMatrix();
    }
}
