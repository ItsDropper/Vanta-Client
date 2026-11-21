package name.vantaclient.client.gui.lobby;

import net.minecraft.client.gui.GuiGraphics;

/** Atmospheric layers drawn over Minecraft's live 3D title panorama. */
public final class VantaLobbyChrome {
    private VantaLobbyChrome() {}

    public static void renderSceneAtmosphere(GuiGraphics graphics, int width, int height) {
        if (graphics == null || width <= 0 || height <= 0) return;

        // Preserve the panorama's geometry and motion while creating readable
        // contrast zones for the menu chrome.
        graphics.fill(0, 0, width, height, 0x50070A11);
        graphics.fillGradient(0, 0, width, Math.max(1, height * 48 / 100),
                0xA8070B14, 0x00070B14);
        graphics.fillGradient(0, height * 54 / 100, width, height,
                0x00070B14, 0xD9070B14);

        int sideWidth = Math.max(1, width / 14);
        graphics.fillGradient(0, 0, sideWidth, height, 0x4D070A11, 0x00070A11);
        graphics.fillGradient(width - sideWidth, 0, width, height, 0x00070A11, 0x4D070A11);
    }
}
