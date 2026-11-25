package name.vantaclient.client.hud;

import name.vantaclient.client.hudeditor.HudManager;
import net.minecraft.client.gui.GuiGraphics;

public class HudRenderer {

    public static void render(GuiGraphics graphics) {
        HudManager.render(graphics);
    }
}