package name.vantaclient.client.gui.lobby;

import net.minecraft.client.gui.GuiGraphics;
import name.vantaclient.client.gui.VantaMenuStyle;
import name.vantaclient.client.gui.framework.VantaUiTheme;

/** Atmospheric layers drawn over Minecraft's live 3D title panorama. */
public final class VantaLobbyChrome {
    private VantaLobbyChrome() {}


    public static void renderPlayPanel(GuiGraphics graphics, int width, int height) {
        if (graphics == null || width < 220 || height < 180) return;

        boolean compact = height < 420 || width < 420;
        int left = VantaMenuStyle.menuLeft(width);
        int top = VantaMenuStyle.menuTop(height);
        int buttonWidth = VantaMenuStyle.menuButtonWidth(width);
        int buttonHeight = VantaMenuStyle.menuButtonHeight(height);
        int gap = VantaMenuStyle.menuButtonGap(height);
        int secondaryHeight = compact ? 20 : 27;
        int settingsHeight = compact ? 18 : 24;
        int stackHeight = 3 * (buttonHeight + gap) + (compact ? 4 : 8)
                + secondaryHeight + (compact ? 3 : 6) + settingsHeight;

        int panelX = Math.max(6, left - (compact ? 12 : 20));
        int panelY = Math.max(4, top - (compact ? 23 : 34));
        int panelWidth = Math.min(width - panelX - 6, buttonWidth + (compact ? 24 : 40));
        int panelHeight = Math.min(height - panelY - 28, stackHeight + (compact ? 34 : 50));
        if (panelWidth <= 0 || panelHeight <= 0) return;

        VantaUiTheme.fillSquircle(graphics, panelX + 2, panelY + 5,
                panelWidth, panelHeight, compact ? 10 : 16, 0x30000000);
        VantaUiTheme.fillSquircle(graphics, panelX, panelY,
                panelWidth, panelHeight, compact ? 10 : 16, 0xA8101722);
        VantaUiTheme.outlineSquircle(graphics, panelX, panelY,
                panelWidth, panelHeight, compact ? 10 : 16, 0x4CFFFFFF);

        int accent = new VantaUiTheme().accent();
        int headerY = panelY + (compact ? 8 : 13);
        graphics.fill(panelX + (compact ? 10 : 18), headerY + 1,
                panelX + (compact ? 12 : 20), headerY + (compact ? 10 : 13),
                VantaUiTheme.withAlpha(accent, 0xE8));
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                "PLAY", panelX + (compact ? 17 : 26), headerY,
                0xFFF1F5FA, false);
        if (!compact) {
            graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,
                    "Choose a destination", panelX + 18, headerY + 15,
                    0xFFAAB6C7, false);
            graphics.fill(panelX + 18, top - 8, panelX + panelWidth - 18,
                    top - 7, 0x315E7189);
        }
    }

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
        graphics.fill(0, 0, sideWidth, height, 0x18070A11);
        graphics.fill(width - sideWidth, 0, width, height, 0x18070A11);
    }
}
