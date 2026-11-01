package name.vantaclient.client.hudeditor;

import name.vantaclient.client.feature.HudEditorFeature;
import net.minecraft.client.gui.GuiGraphics;

public class HudEditor {

    public static void render(GuiGraphics graphics) {

        if (!HudEditorFeature.INSTANCE.isEnabled()) {
            return;
        }


        HudManager.render(graphics);
    }
}